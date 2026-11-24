package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.EngineAudioSynthesizer
import com.example.audio.UiSoundType
import com.example.data.ActiveCarBuild
import com.example.data.BodyKitStyle
import com.example.data.CameraMode
import com.example.data.CareerEventSpec
import com.example.data.CareerProgressDoc
import com.example.data.CloudProgressRepository
import com.example.data.DeviceSettings
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.GarageCarDoc
import com.example.data.GraphicsQuality
import com.example.data.HeadlightStyle
import com.example.data.LocalProgressRepository
import com.example.data.LocalProgressSnapshot
import com.example.data.MedalTier
import com.example.data.PlayerProfileDoc
import com.example.data.PlayerProgression
import com.example.data.RaceResult
import com.example.data.RimStyle
import com.example.data.SettingsRepository
import com.example.data.SpoilerStyle
import com.example.data.TelemetrySnapshot
import com.example.data.TimeOfDayPreset
import com.example.data.WeatherPreset
import com.example.data.WindowTint
import com.example.engine.ControlInput
import com.example.engine.GameRenderer
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameUiState(
    val screen: GameScreen = GameScreen.MAIN_MENU,
    val profile: PlayerProfileDoc = PlayerProfileDoc(),
    val garageCars: Map<String, GarageCarDoc> = emptyMap(),
    val careerProgress: Map<String, CareerProgressDoc> = emptyMap(),
    val previewCarId: String = "vipera_gtx",
    val activeCarBuild: ActiveCarBuild = defaultCarBuild(),
    val settings: DeviceSettings = DeviceSettings(),
    val paused: Boolean = false,
    val lastRaceResult: RaceResult? = null,
    val statusBanner: String? = null,
    val cloudSyncError: String? = null
)

private fun defaultCarBuild(): ActiveCarBuild {
    val spec = GameCatalog.cars.first()
    return ActiveCarBuild(
        spec = spec,
        paintHex = spec.defaultPaintHex,
        rimStyle = RimStyle.CHROME,
        underglowHex = spec.defaultUnderglowHex,
        engineLevel = 1,
        nitroLevel = 1,
        handlingLevel = 1,
        brakesLevel = 1,
        turboLevel = 1,
        tiresLevel = 1,
        suspensionLevel = 1,
        spoilerStyle = SpoilerStyle.STOCK,
        bodyKitStyle = BodyKitStyle.STREET,
        windowTint = WindowTint.SMOKE,
        headlightStyle = HeadlightStyle.XENON_WHITE
    )
}

class GameViewModel(
    private val uid: String,
    private val localRepository: LocalProgressRepository,
    private val cloudRepository: CloudProgressRepository?,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val controlInput = ControlInput()
    val audioSynthesizer = EngineAudioSynthesizer()

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private val _screen = MutableStateFlow(GameScreen.MAIN_MENU)
    private val _previewCarId = MutableStateFlow("vipera_gtx")
    private val _paused = MutableStateFlow(false)
    private val _lastRaceResult = MutableStateFlow<RaceResult?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _cloudSyncError = MutableStateFlow<String?>(null)

    private var lastLaunchedMode: GameMode = GameMode.FREE_ROAM
    private var lastLaunchedEvent: CareerEventSpec? = null

    val renderer: GameRenderer = GameRenderer(
        controlInput = controlInput,
        audioSynthesizer = audioSynthesizer,
        onTelemetryUpdate = { snapshot ->
            _telemetry.value = snapshot
        },
        onRaceFinished = { result ->
            handleRaceFinished(result)
        }
    )

    private val localSnapshotFlow = localRepository.observeSnapshot(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val cloudProfileFlow = (cloudRepository?.observeProfile(uid) ?: flowOf(null))
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            _cloudSyncError.value = (firestoreError ?: error).message ?: "Local save active; cloud sync paused"
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val cloudGarageFlow = (cloudRepository?.observeGarageCars(uid) ?: flowOf(emptyList()))
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            _cloudSyncError.value = (firestoreError ?: error).message ?: "Local save active; garage sync paused"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val cloudCareerFlow = (cloudRepository?.observeCareerProgress(uid) ?: flowOf(emptyList()))
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            _cloudSyncError.value = (firestoreError ?: error).message ?: "Local save active; career sync paused"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val settingsFlow = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DeviceSettings())

    private val mergedProgressFlow = combine(
        localSnapshotFlow,
        cloudProfileFlow,
        cloudGarageFlow,
        cloudCareerFlow
    ) { localSnap, cloudProf, cloudCars, cloudCareer ->
        val starter = GameCatalog.carById("vipera_gtx")
        val fallbackCars = mapOf(
            starter.id to GarageCarDoc(
                docId = starter.id,
                userId = uid,
                carId = starter.id,
                paintHex = starter.defaultPaintHex,
                rimStyle = RimStyle.CHROME.name,
                underglowHex = starter.defaultUnderglowHex
            )
        )

        // Local-first snapshot takes immediate precedence; merge richer cloud records when present
        val baseProfile = localSnap?.profile ?: cloudProf ?: PlayerProfileDoc(userId = uid)
        val mergedProfile = if (cloudProf != null && localSnap != null) {
            val bestXp = maxOf(localSnap.profile.xp, cloudProf.xp)
            val bestLvl = maxOf(
                localSnap.profile.level,
                cloudProf.level,
                PlayerProgression.levelForXp(bestXp).toLong()
            )
            localSnap.profile.copy(
                displayName = cloudProf.displayName.ifBlank { localSnap.profile.displayName },
                xp = bestXp,
                level = bestLvl,
                unlockedAchievements = (localSnap.profile.unlockedAchievements + cloudProf.unlockedAchievements).distinct(),
                totalDistanceKm = maxOf(localSnap.profile.totalDistanceKm, cloudProf.totalDistanceKm),
                highestDriftCombo = maxOf(localSnap.profile.highestDriftCombo, cloudProf.highestDriftCombo)
            )
        } else {
            val derivedLvl = maxOf(baseProfile.level, PlayerProgression.levelForXp(baseProfile.xp).toLong())
            baseProfile.copy(level = derivedLvl)
        }

        val mergedCars = LinkedHashMap<String, GarageCarDoc>()
        cloudCars.forEach { mergedCars[it.carId] = it }
        localSnap?.garageCars?.forEach { (id, doc) -> mergedCars[id] = doc }
        if (mergedCars.isEmpty()) mergedCars.putAll(fallbackCars)

        val mergedCareer = LinkedHashMap<String, CareerProgressDoc>()
        cloudCareer.forEach { mergedCareer[it.eventId] = it }
        localSnap?.careerProgress?.forEach { (id, doc) -> mergedCareer[id] = doc }

        LocalProgressSnapshot(mergedProfile, mergedCars, mergedCareer)
    }

    val uiState: StateFlow<GameUiState> = combine(
        combine(_screen, _previewCarId, _paused, _lastRaceResult, _statusBanner) { s, p, pause, res, ban ->
            LocalUiShell(s, p, pause, res, ban)
        },
        mergedProgressFlow,
        combine(settingsFlow, _cloudSyncError) { set, err -> set to err }
    ) { shell, progress, (deviceSettings, syncError) ->
        val profile = progress.profile
        val carsMap = progress.garageCars
        val careerMap = progress.careerProgress

        val targetCarId = if (shell.screen == GameScreen.GARAGE) {
            shell.previewCarId
        } else {
            profile.selectedCarId
        }
        val carSpec = GameCatalog.carById(targetCarId)
        val ownedDoc = carsMap[carSpec.id]
        val legacyHandling = (ownedDoc?.handlingLevel ?: 1L).toInt().coerceIn(1, 5)
        val build = ActiveCarBuild(
            spec = carSpec,
            paintHex = ownedDoc?.paintHex ?: carSpec.defaultPaintHex,
            rimStyle = RimStyle.fromRaw(ownedDoc?.rimStyle ?: RimStyle.CHROME.name),
            underglowHex = ownedDoc?.underglowHex ?: carSpec.defaultUnderglowHex,
            engineLevel = (ownedDoc?.engineLevel ?: 1L).toInt().coerceIn(1, 5),
            nitroLevel = (ownedDoc?.nitroLevel ?: 1L).toInt().coerceIn(1, 5),
            handlingLevel = legacyHandling,
            brakesLevel = (ownedDoc?.brakesLevel ?: 1L).toInt().coerceIn(1, 5),
            turboLevel = (ownedDoc?.turboLevel ?: 1L).toInt().coerceIn(1, 5),
            tiresLevel = (ownedDoc?.tiresLevel ?: legacyHandling.toLong()).toInt().coerceIn(1, 5),
            suspensionLevel = (ownedDoc?.suspensionLevel ?: legacyHandling.toLong()).toInt().coerceIn(1, 5),
            spoilerStyle = SpoilerStyle.fromRaw(ownedDoc?.spoilerStyle ?: SpoilerStyle.STOCK.name),
            bodyKitStyle = BodyKitStyle.fromRaw(ownedDoc?.bodyKitStyle ?: BodyKitStyle.STREET.name),
            windowTint = WindowTint.fromRaw(ownedDoc?.windowTint ?: WindowTint.SMOKE.name),
            headlightStyle = HeadlightStyle.fromRaw(ownedDoc?.headlightStyle ?: HeadlightStyle.XENON_WHITE.name)
        )

        // Push live car build (chassis, body kit, spoiler, tint, headlights, upgrades) & settings to OpenGL
        renderer.activeCarBuild = build
        renderer.settings = deviceSettings
        audioSynthesizer.setMusicEnabled(deviceSettings.musicEnabled)
        audioSynthesizer.setSfxEnabled(deviceSettings.sfxEnabled)
        audioSynthesizer.setEnabled(deviceSettings.sfxEnabled)

        GameUiState(
            screen = shell.screen,
            profile = profile,
            garageCars = carsMap,
            careerProgress = careerMap,
            previewCarId = shell.previewCarId,
            activeCarBuild = build,
            settings = deviceSettings,
            paused = shell.paused,
            lastRaceResult = shell.lastRaceResult,
            statusBanner = shell.statusBanner,
            cloudSyncError = syncError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), GameUiState())

    init {
        audioSynthesizer.start()
        viewModelScope.launch {
            val displayName = if (cloudRepository != null) {
                runCatching { Firebase.auth.currentUser?.displayName?.trim().orEmpty() }.getOrDefault("")
            } else {
                ""
            }.ifBlank { "Street Racer" }
            localRepository.ensureInitialized(uid, displayName)
            val initResult = cloudRepository?.ensurePlayerInitialized()
            if (initResult != null && initResult.isFailure) {
                _cloudSyncError.value = "Local save active • Cloud sync pending"
            }
        }
    }

    fun triggerLaunchFlyIn() {
        renderer.triggerLaunchFlyIn()
        audioSynthesizer.triggerUiSound(UiSoundType.CONFIRM)
    }

    fun playUiSound(type: UiSoundType = UiSoundType.CLICK) {
        audioSynthesizer.triggerUiSound(type)
    }

    fun onAppPaused() {
        controlInput.clear()
        audioSynthesizer.setLifecyclePaused(true)
        if (_screen.value == GameScreen.RACING) {
            setPaused(true)
        }
    }

    fun onAppResumed() {
        audioSynthesizer.setLifecyclePaused(false)
    }

    fun navigateTo(screen: GameScreen) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        if (screen == GameScreen.GARAGE) {
            _previewCarId.value = uiState.value.profile.selectedCarId
            renderer.physics.garageUserOrbitRad = 0f
        }
        if (screen != GameScreen.RACING) {
            _paused.value = false
            _lastRaceResult.value = null
            controlInput.clear()
        }
        _screen.value = screen
        renderer.setScreenMode(screen)
    }

    fun openCarInGarage(carId: String) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        _previewCarId.value = carId
        renderer.physics.garageUserOrbitRad = 0f
        _screen.value = GameScreen.GARAGE
        renderer.setScreenMode(GameScreen.GARAGE)
    }

    fun launchMode(mode: GameMode, event: CareerEventSpec? = null) {
        audioSynthesizer.triggerUiSound(UiSoundType.CONFIRM)
        val resolvedEvent = event ?: GameCatalog.defaultQuickEvent(mode)
        lastLaunchedMode = mode
        lastLaunchedEvent = resolvedEvent
        _paused.value = false
        _lastRaceResult.value = null
        _screen.value = GameScreen.RACING
        renderer.startRaceSession(mode, resolvedEvent)
    }

    fun restartCurrentSession() {
        launchMode(lastLaunchedMode, lastLaunchedEvent)
    }

    fun togglePause() {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        val next = !_paused.value
        _paused.value = next
        renderer.setPaused(next)
    }

    fun setPaused(paused: Boolean) {
        _paused.value = paused
        renderer.setPaused(paused)
    }

    fun cycleCameraMode() {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        val modes = CameraMode.entries
        val current = uiState.value.settings.cameraMode
        val next = modes[(modes.indexOf(current) + 1) % modes.size]
        viewModelScope.launch {
            settingsRepository.setCameraMode(next)
        }
    }

    fun recoverCar() {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        renderer.recoverCar()
    }

    fun previewCarInGarage(carId: String) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        _previewCarId.value = carId
    }

    fun equipCar(carId: String) {
        val state = uiState.value
        if (!state.garageCars.containsKey(carId)) return
        audioSynthesizer.triggerUiSound(UiSoundType.CONFIRM)
        _previewCarId.value = carId
        val updatedProfile = state.profile.copy(selectedCarId = carId)
        val snapshot = LocalProgressSnapshot(updatedProfile, state.garageCars, state.careerProgress)
        viewModelScope.launch {
            localRepository.saveSnapshot(uid, snapshot)
            showBanner("EQUIPPED ${GameCatalog.carById(carId).name}")
            cloudRepository?.selectCar(carId)
        }
    }

    /**
     * Dual-path unlock:
     * - Free claim when Player Level >= car.unlockLevel
     * - Early Cash purchase anytime when credits >= car.price
     */
    fun unlockCar(carId: String) {
        val state = uiState.value
        val spec = GameCatalog.carById(carId)
        if (state.garageCars.containsKey(carId)) {
            equipCar(carId)
            return
        }
        val levelUnlocked = state.profile.level >= spec.unlockLevel.toLong()
        val effectiveCost = if (levelUnlocked) 0L else spec.price
        if (state.profile.credits < effectiveCost) {
            showBanner("REACH LVL ${spec.unlockLevel} OR NEED ${effectiveCost - state.profile.credits} CR")
            return
        }

        val newCarDoc = GarageCarDoc(
            docId = spec.id,
            userId = uid,
            carId = spec.id,
            paintHex = spec.defaultPaintHex,
            rimStyle = RimStyle.CHROME.name,
            underglowHex = spec.defaultUnderglowHex,
            spoilerStyle = SpoilerStyle.STOCK.name,
            bodyKitStyle = BodyKitStyle.STREET.name,
            windowTint = WindowTint.SMOKE.name,
            headlightStyle = HeadlightStyle.XENON_WHITE.name
        )
        val updatedCars = state.garageCars + (spec.id to newCarDoc)
        val draftProfile = state.profile.copy(
            credits = (state.profile.credits - effectiveCost).coerceAtLeast(0L),
            selectedCarId = spec.id
        )
        val (finalProfile, newAchs) = applyNewAchievements(draftProfile, updatedCars, state.careerProgress)
        _previewCarId.value = spec.id
        audioSynthesizer.triggerUiSound(UiSoundType.UNLOCK)

        viewModelScope.launch {
            localRepository.saveSnapshot(uid, LocalProgressSnapshot(finalProfile, updatedCars, state.careerProgress))
            val achSuffix = if (newAchs.isNotEmpty()) " • +${newAchs.size} ACHIEVEMENT!" else ""
            val unlockVerb = if (levelUnlocked) "CLAIMED" else "PURCHASED"
            showBanner("$unlockVerb ${spec.name}!$achSuffix")
            cloudRepository?.unlockAndSelectCar(
                spec = spec,
                newCredits = finalProfile.credits,
                newXp = finalProfile.xp,
                newLevel = finalProfile.level.toInt(),
                unlockedAchievements = finalProfile.unlockedAchievements
            )
        }
    }

    fun updateVisuals(
        paintHex: String? = null,
        rimStyle: RimStyle? = null,
        underglowHex: String? = null,
        spoilerStyle: SpoilerStyle? = null,
        bodyKitStyle: BodyKitStyle? = null,
        windowTint: WindowTint? = null,
        headlightStyle: HeadlightStyle? = null
    ) {
        val state = uiState.value
        val carId = state.previewCarId
        val owned = state.garageCars[carId] ?: return

        val updatedCar = owned.copy(
            paintHex = paintHex ?: owned.paintHex,
            rimStyle = (rimStyle ?: RimStyle.fromRaw(owned.rimStyle)).name,
            underglowHex = underglowHex ?: owned.underglowHex,
            spoilerStyle = (spoilerStyle ?: SpoilerStyle.fromRaw(owned.spoilerStyle)).name,
            bodyKitStyle = (bodyKitStyle ?: BodyKitStyle.fromRaw(owned.bodyKitStyle)).name,
            windowTint = (windowTint ?: WindowTint.fromRaw(owned.windowTint)).name,
            headlightStyle = (headlightStyle ?: HeadlightStyle.fromRaw(owned.headlightStyle)).name
        )
        val updatedCars = state.garageCars + (carId to updatedCar)
        val (finalProfile, newAchs) = applyNewAchievements(state.profile, updatedCars, state.careerProgress)
        audioSynthesizer.triggerUiSound(if (newAchs.isNotEmpty()) UiSoundType.UNLOCK else UiSoundType.CLICK)

        viewModelScope.launch {
            localRepository.saveSnapshot(uid, LocalProgressSnapshot(finalProfile, updatedCars, state.careerProgress))
            if (newAchs.isNotEmpty()) {
                showBanner("ACHIEVEMENT UNLOCKED: ${newAchs.first().title}!")
            }
            cloudRepository?.updateCarVisuals(
                carId = carId,
                paintHex = updatedCar.paintHex,
                rimStyle = RimStyle.fromRaw(updatedCar.rimStyle),
                underglowHex = updatedCar.underglowHex,
                spoilerStyle = SpoilerStyle.fromRaw(updatedCar.spoilerStyle),
                bodyKitStyle = BodyKitStyle.fromRaw(updatedCar.bodyKitStyle),
                windowTint = WindowTint.fromRaw(updatedCar.windowTint),
                headlightStyle = HeadlightStyle.fromRaw(updatedCar.headlightStyle),
                updatedProfileRewards = if (newAchs.isNotEmpty()) finalProfile else null
            )
        }
    }

    fun upgradeStat(statType: String) {
        val state = uiState.value
        val carId = state.previewCarId
        val owned = state.garageCars[carId] ?: return
        var eng = owned.engineLevel.toInt()
        var trb = owned.turboLevel.toInt()
        var brk = owned.brakesLevel.toInt()
        var tir = owned.tiresLevel.toInt()
        var sus = owned.suspensionLevel.toInt()
        var nit = owned.nitroLevel.toInt()

        val currentLevel = when (statType.uppercase()) {
            "ENGINE" -> eng
            "TURBO" -> trb
            "BRAKES" -> brk
            "TIRES", "HANDLING" -> tir
            "SUSPENSION" -> sus
            "NITRO" -> nit
            else -> 5
        }
        if (currentLevel >= 5) return
        val cost = GameCatalog.upgradeCost(currentLevel)
        if (state.profile.credits < cost) {
            showBanner("NEED ${cost - state.profile.credits} MORE CR")
            return
        }
        when (statType.uppercase()) {
            "ENGINE" -> eng++
            "TURBO" -> trb++
            "BRAKES" -> brk++
            "TIRES", "HANDLING" -> tir++
            "SUSPENSION" -> sus++
            "NITRO" -> nit++
        }

        val updatedCar = owned.copy(
            engineLevel = eng.toLong(),
            turboLevel = trb.toLong(),
            brakesLevel = brk.toLong(),
            tiresLevel = tir.toLong(),
            suspensionLevel = sus.toLong(),
            handlingLevel = maxOf(tir, sus).toLong(),
            nitroLevel = nit.toLong()
        )
        val updatedCars = state.garageCars + (carId to updatedCar)
        val draftProfile = state.profile.copy(credits = (state.profile.credits - cost).coerceAtLeast(0L))
        val (finalProfile, newAchs) = applyNewAchievements(draftProfile, updatedCars, state.careerProgress)
        audioSynthesizer.triggerUiSound(UiSoundType.UNLOCK)

        viewModelScope.launch {
            localRepository.saveSnapshot(uid, LocalProgressSnapshot(finalProfile, updatedCars, state.careerProgress))
            val achNote = if (newAchs.isNotEmpty()) " • ${newAchs.first().title}!" else ""
            showBanner("${statType.uppercase()} LVL ${currentLevel + 1}$achNote")
            cloudRepository?.upgradeCar(
                carId = carId,
                engineLevel = eng,
                turboLevel = trb,
                nitroLevel = nit,
                tiresLevel = tir,
                suspensionLevel = sus,
                brakesLevel = brk,
                updatedProfile = finalProfile
            )
        }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setGraphicsQuality(quality) }
    }

    fun setCameraMode(mode: CameraMode) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setCameraMode(mode) }
    }

    fun setTimeOfDayPreset(preset: TimeOfDayPreset) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setTimeOfDayPreset(preset) }
    }

    fun setWeatherPreset(preset: WeatherPreset) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setWeatherPreset(preset) }
    }

    fun setSteeringSensitivity(value: Float) {
        viewModelScope.launch { settingsRepository.setSteeringSensitivity(value) }
    }

    fun setEngineAudioEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEngineAudioEnabled(enabled) }
    }

    fun setMusicEnabled(enabled: Boolean) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setMusicEnabled(enabled) }
    }

    fun setSfxEnabled(enabled: Boolean) {
        if (enabled) audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setSfxEnabled(enabled) }
    }

    fun setShowFpsCounter(show: Boolean) {
        audioSynthesizer.triggerUiSound(UiSoundType.CLICK)
        viewModelScope.launch { settingsRepository.setShowFpsCounter(show) }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun showBanner(message: String) {
        _statusBanner.value = message
    }

    private fun applyNewAchievements(
        profile: PlayerProfileDoc,
        cars: Map<String, GarageCarDoc>,
        career: Map<String, CareerProgressDoc>
    ): Pair<PlayerProfileDoc, List<com.example.data.AchievementSpec>> {
        val newlyUnlocked = PlayerProgression.evaluateNewAchievements(profile, cars, career)
        if (newlyUnlocked.isEmpty()) return profile to emptyList()
        val bonusCr = newlyUnlocked.sumOf { it.rewardCredits }
        val bonusXp = newlyUnlocked.sumOf { it.rewardXp }
        val nextXp = (profile.xp + bonusXp).coerceAtMost(100_000_000L)
        val nextLevel = PlayerProgression.levelForXp(nextXp).toLong()
        val updated = profile.copy(
            credits = (profile.credits + bonusCr).coerceAtMost(100_000_000L),
            xp = nextXp,
            level = maxOf(profile.level, nextLevel),
            unlockedAchievements = (profile.unlockedAchievements + newlyUnlocked.map { it.id }).distinct()
        )
        return updated to newlyUnlocked
    }

    private fun handleRaceFinished(result: RaceResult) {
        controlInput.clear()
        val state = uiState.value
        val existingEvent = result.eventId?.let { state.careerProgress[it] }

        val baseXp = PlayerProgression.calculateRaceXp(result)
        val prevLevel = state.profile.level.toInt()
        val wonRace = result.medal == MedalTier.GOLD || (result.mode == GameMode.STREET_RACE && result.finishPosition == 1)
        val newXp = (state.profile.xp + baseXp).coerceAtMost(100_000_000L)
        val newLevel = PlayerProgression.levelForXp(newXp)

        val draftProfile = state.profile.copy(
            credits = (state.profile.credits + result.creditsEarned).coerceIn(0L, 100_000_000L),
            xp = newXp,
            level = maxOf(state.profile.level, newLevel.toLong()),
            racesCompleted = state.profile.racesCompleted + 1L,
            racesWon = state.profile.racesWon + if (wonRace) 1L else 0L,
            totalDistanceKm = (state.profile.totalDistanceKm + result.distanceKm).coerceIn(0.0, 1_000_000.0),
            highestDriftCombo = maxOf(state.profile.highestDriftCombo, result.highestCombo).coerceIn(0L, 10_000_000L)
        )

        val updatedCareer = LinkedHashMap(state.careerProgress)
        val eventId = result.eventId
        if (!eventId.isNullOrBlank()) {
            val prevMedal = MedalTier.fromRaw(existingEvent?.medalTier ?: "NONE")
            val bestMedal = if (result.medal.rank >= prevMedal.rank) result.medal else prevMedal
            val bestTime = when {
                result.mode == GameMode.DRIFT_CHALLENGE -> existingEvent?.bestTimeMs ?: 0L
                existingEvent == null || existingEvent.bestTimeMs == 0L -> result.elapsedMs
                else -> minOf(existingEvent.bestTimeMs, result.elapsedMs)
            }
            updatedCareer[eventId] = CareerProgressDoc(
                docId = eventId,
                userId = uid,
                eventId = eventId,
                bestTimeMs = bestTime,
                bestDriftScore = maxOf(existingEvent?.bestDriftScore ?: 0L, result.driftScore),
                medalTier = bestMedal.name,
                completedCount = (existingEvent?.completedCount ?: 0L) + 1L
            )
        }

        val (finalProfile, newAchs) = applyNewAchievements(draftProfile, state.garageCars, updatedCareer)
        val totalXpEarned = baseXp + newAchs.sumOf { it.rewardXp }
        val totalCreditsEarned = result.creditsEarned + newAchs.sumOf { it.rewardCredits }
        val leveledUpTo = if (finalProfile.level.toInt() > prevLevel) finalProfile.level.toInt() else null

        val enrichedResult = result.copy(
            creditsEarned = totalCreditsEarned,
            xpEarned = totalXpEarned,
            leveledUpTo = leveledUpTo,
            newlyUnlockedAchievements = newAchs.map { it.title }
        )
        _lastRaceResult.value = enrichedResult

        viewModelScope.launch {
            localRepository.saveSnapshot(uid, LocalProgressSnapshot(finalProfile, state.garageCars, updatedCareer))
            val res = cloudRepository?.recordSessionResult(enrichedResult, finalProfile, existingEvent)
            if (res != null && res.isFailure) {
                _cloudSyncError.value = "Progress saved locally • Cloud sync retry pending"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioSynthesizer.stop()
        renderer.releaseGlResources()
    }

    private data class LocalUiShell(
        val screen: GameScreen,
        val previewCarId: String,
        val paused: Boolean,
        val lastRaceResult: RaceResult?,
        val statusBanner: String?
    )

    class Factory(
        private val context: Context,
        private val uid: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val cloudRepo = if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                runCatching { CloudProgressRepository(appContext) }.getOrNull()
            } else {
                null
            }
            return GameViewModel(
                uid = uid,
                localRepository = LocalProgressRepository(appContext),
                cloudRepository = cloudRepo,
                settingsRepository = SettingsRepository(appContext)
            ) as T
        }
    }
}

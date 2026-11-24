package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GameScreen {
    MAIN_MENU,
    PLAY_SELECTOR,
    GARAGE,
    CAREER,
    PROFILE,
    SETTINGS,
    RACING
}

enum class GameMode(val title: String, val subtitle: String) {
    FREE_ROAM("FREE DRIVE", "Roam all 7 city districts with live traffic, signals & dynamic weather"),
    STREET_RACE("STREET RACING", "Grid start against 3 overtaking AI rivals to claim 1st place"),
    TIME_TRIAL("TIME TRIAL", "Beat gold sector targets across highway, bridge & tunnel"),
    CHECKPOINT_RACE("CHECKPOINT CHALLENGE", "Blast through high-speed neon checkpoint gates across the city"),
    DRIFT_CHALLENGE("DRIFT CHALLENGE", "Chain wide-angle wet & dry drifts for 60 seconds to max your combo")
}

enum class CameraMode(val label: String) {
    CHASE("CHASE"),
    CLOSE("CLOSE"),
    HOOD("HOOD"),
    CINEMATIC("CINEMATIC")
}

enum class TimeOfDayPreset(val label: String) {
    AUTO("AUTO"),
    DAWN("DAWN"),
    DAY("DAY"),
    SUNSET("SUNSET"),
    NIGHT("NIGHT")
}

enum class WeatherPreset(val label: String) {
    AUTO("AUTO"),
    CLEAR("CLEAR"),
    FOG("FOG"),
    RAIN("RAIN")
}

enum class GraphicsQuality(
    val label: String,
    val drawDistance: Float,
    val propDistance: Float,
    val shadowEnabled: Boolean,
    val maxTrafficVehicles: Int,
    val reflectionStrength: Float,
    val maxParticles: Int,
    val lodDetailDistance: Float,
    val rainDensityScale: Float,
    val summary: String
) {
    LOW(
        label = "LOW",
        drawDistance = 260f,
        propDistance = 120f,
        shadowEnabled = false,
        maxTrafficVehicles = 14,
        reflectionStrength = 0.35f,
        maxParticles = 48,
        lodDetailDistance = 65f,
        rainDensityScale = 0.50f,
        summary = "Max battery & stable 60 FPS • Aggressive LOD"
    ),
    MEDIUM(
        label = "MEDIUM",
        drawDistance = 390f,
        propDistance = 200f,
        shadowEnabled = true,
        maxTrafficVehicles = 20,
        reflectionStrength = 0.72f,
        maxParticles = 96,
        lodDetailDistance = 110f,
        rainDensityScale = 0.78f,
        summary = "Balanced 60 FPS • Shadows, reflections & LOD"
    ),
    HIGH(
        label = "HIGH",
        drawDistance = 540f,
        propDistance = 300f,
        shadowEnabled = true,
        maxTrafficVehicles = 26,
        reflectionStrength = 1.0f,
        maxParticles = 144,
        lodDetailDistance = 155f,
        rainDensityScale = 1.0f,
        summary = "Full reflections, shadows, VFX & 6–8 GB RAM tuning"
    )
}

enum class VehicleClass(val label: String) {
    SPORTS("SPORTS CAR"),
    DRIFT("DRIFT CAR"),
    MUSCLE("MUSCLE CAR"),
    SUPERCAR("SUPERCAR"),
    HYPERCAR("HYPERCAR")
}

enum class RimStyle(val label: String, val r: Float, val g: Float, val b: Float) {
    CHROME("CHROME", 0.85f, 0.88f, 0.94f),
    CARBON("CARBON", 0.22f, 0.24f, 0.30f),
    GOLD("GOLD", 0.92f, 0.72f, 0.22f),
    CRIMSON("CRIMSON", 0.88f, 0.12f, 0.16f),
    BRONZE("BRONZE", 0.72f, 0.48f, 0.26f);

    companion object {
        fun fromRaw(raw: String): RimStyle = entries.firstOrNull { it.name == raw } ?: CHROME
    }
}

enum class SpoilerStyle(val label: String, val downforceBonus: Float) {
    STOCK("STOCK", 0.00f),
    LIP("DUCKTAIL", 0.02f),
    GT_WING("GT WING", 0.04f),
    HYPER_AERO("HYPER AERO", 0.06f);

    companion object {
        fun fromRaw(raw: String): SpoilerStyle = entries.firstOrNull { it.name == raw } ?: STOCK
    }
}

enum class BodyKitStyle(val label: String, val weightReductionKg: Float) {
    STREET("STREET", 0f),
    WIDEBODY("WIDEBODY", 15f),
    TRACK_CARBON("CARBON AERO", 35f),
    APEX_PRO("APEX PRO", 50f);

    companion object {
        fun fromRaw(raw: String): BodyKitStyle = entries.firstOrNull { it.name == raw } ?: STREET
    }
}

enum class WindowTint(
    val label: String,
    val r: Float,
    val g: Float,
    val b: Float,
    val metallic: Float,
    val roughness: Float
) {
    CLEAR("CLEAR", 0.46f, 0.56f, 0.66f, 0.50f, 0.14f),
    SMOKE("SMOKE", 0.16f, 0.21f, 0.28f, 0.65f, 0.12f),
    MIDNIGHT("LIMO", 0.04f, 0.05f, 0.08f, 0.78f, 0.08f),
    MIRROR_CYAN("CYBER", 0.08f, 0.46f, 0.58f, 0.92f, 0.06f);

    companion object {
        fun fromRaw(raw: String): WindowTint = entries.firstOrNull { it.name == raw } ?: SMOKE
    }
}

enum class HeadlightStyle(
    val label: String,
    val hex: String,
    val r: Float,
    val g: Float,
    val b: Float
) {
    XENON_WHITE("XENON", "#F0F8FF", 0.94f, 0.97f, 1.00f),
    ICE_BLUE("ICE BLUE", "#00E5FF", 0.12f, 0.90f, 1.00f),
    SOLAR_AMBER("AMBER", "#FFB800", 1.00f, 0.72f, 0.08f),
    CRIMSON_RED("CRIMSON", "#FF1E32", 1.00f, 0.14f, 0.20f),
    NEON_VIOLET("VIOLET", "#C084FC", 0.75f, 0.44f, 1.00f);

    companion object {
        fun fromRaw(raw: String): HeadlightStyle = entries.firstOrNull { it.name == raw } ?: XENON_WHITE
    }
}

enum class MedalTier(val label: String, val rank: Int) {
    NONE("UNRANKED", 0),
    BRONZE("BRONZE", 1),
    SILVER("SILVER", 2),
    GOLD("GOLD", 3);

    companion object {
        fun fromRaw(raw: String): MedalTier = entries.firstOrNull { it.name == raw } ?: NONE
    }
}

data class PaintSwatch(val name: String, val hex: String)

data class CarSpec(
    val id: String,
    val name: String,
    val category: String,
    val vehicleClass: VehicleClass,
    val price: Long,
    val unlockLevel: Int,
    val baseTopSpeedKmh: Float,
    val baseAcceleration: Float,
    val baseHandling: Float,
    val baseBraking: Float,
    val weightKg: Float,
    val baseNitroCapacity: Float, // 1.0 = baseline burn tank; higher = longer boost duration
    val baseNitroPower: Float,
    val defaultPaintHex: String,
    val defaultUnderglowHex: String,
    val bodyLength: Float,
    val bodyWidth: Float,
    val spoilerHeight: Float
)

data class AchievementSpec(
    val id: String,
    val title: String,
    val description: String,
    val rewardCredits: Long,
    val rewardXp: Long
)

data class CheckpointNode(
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float = 14f
)

data class CareerEventSpec(
    val id: String,
    val title: String,
    val district: String,
    val mode: GameMode,
    val description: String,
    val spawnX: Float,
    val spawnY: Float,
    val spawnZ: Float,
    val spawnHeadingRad: Float,
    val checkpoints: List<CheckpointNode>,
    val goldTarget: Long,   // ms for race/time-trial (lower is better), score for drift (higher is better)
    val silverTarget: Long,
    val bronzeTarget: Long,
    val rewardCredits: Long
)

data class RadarBlip(
    val x: Float,
    val z: Float,
    val isRival: Boolean
)

@Keep
@IgnoreExtraProperties
data class PlayerProfileDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "Street Racer",
    val credits: Long = 15000L,
    val xp: Long = 0L,
    val level: Long = 1L,
    val racesCompleted: Long = 0L,
    val racesWon: Long = 0L,
    val unlockedAchievements: List<String> = emptyList(),
    val selectedCarId: String = "vipera_gtx",
    val totalDistanceKm: Double = 0.0,
    val highestDriftCombo: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GarageCarDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val carId: String = "vipera_gtx",
    val paintHex: String = "#E50914",
    val rimStyle: String = "CHROME",
    val underglowHex: String = "#00E5FF",
    val spoilerStyle: String = "STOCK",
    val bodyKitStyle: String = "STREET",
    val windowTint: String = "SMOKE",
    val headlightStyle: String = "XENON_WHITE",
    val engineLevel: Long = 1L,
    val turboLevel: Long = 1L,
    val nitroLevel: Long = 1L,
    val handlingLevel: Long = 1L,
    val tiresLevel: Long = 1L,
    val suspensionLevel: Long = 1L,
    val brakesLevel: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CareerProgressDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val eventId: String = "",
    val bestTimeMs: Long = 0L,
    val bestDriftScore: Long = 0L,
    val medalTier: String = "NONE",
    val completedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DeviceSettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val cameraMode: CameraMode = CameraMode.CHASE,
    val timeOfDayPreset: TimeOfDayPreset = TimeOfDayPreset.AUTO,
    val weatherPreset: WeatherPreset = WeatherPreset.AUTO,
    val steeringSensitivity: Float = 1.0f,
    val engineAudioEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = engineAudioEnabled,
    val showFpsCounter: Boolean = true
)

data class ActiveCarBuild(
    val spec: CarSpec,
    val paintHex: String,
    val rimStyle: RimStyle,
    val underglowHex: String,
    val engineLevel: Int,
    val nitroLevel: Int,
    val handlingLevel: Int,
    val brakesLevel: Int,
    val turboLevel: Int = 1,
    val tiresLevel: Int = handlingLevel,
    val suspensionLevel: Int = handlingLevel,
    val spoilerStyle: SpoilerStyle = SpoilerStyle.STOCK,
    val bodyKitStyle: BodyKitStyle = BodyKitStyle.STREET,
    val windowTint: WindowTint = WindowTint.SMOKE,
    val headlightStyle: HeadlightStyle = HeadlightStyle.XENON_WHITE
) {
    val effectiveTopSpeedKmh: Float
        get() = spec.baseTopSpeedKmh + (engineLevel - 1) * 13f + (turboLevel - 1) * 6f

    val effectiveAcceleration: Float
        get() = spec.baseAcceleration + (engineLevel - 1) * 3.0f + (turboLevel - 1) * 3.6f

    val effectiveTurboSpoolBoost: Float
        get() = 1.0f + (turboLevel - 1) * 0.08f

    val effectiveTireGrip: Float
        get() = 1.0f + (tiresLevel - 1) * 0.11f

    val effectiveSuspensionStiffness: Float
        get() = 1.0f + (suspensionLevel - 1) * 0.14f

    val effectiveHandling: Float
        get() = spec.baseHandling + (tiresLevel - 1) * 0.10f + (suspensionLevel - 1) * 0.08f + spoilerStyle.downforceBonus

    val effectiveBraking: Float
        get() = spec.baseBraking + (brakesLevel - 1) * 0.15f

    val effectiveWeightKg: Float
        get() = (spec.weightKg - (suspensionLevel - 1) * 16f - bodyKitStyle.weightReductionKg).coerceAtLeast(1080f)

    val effectiveNitroBoost: Float
        get() = spec.baseNitroPower + (nitroLevel - 1) * 0.11f

    val effectiveNitroCapacity: Float
        get() = spec.baseNitroCapacity + (nitroLevel - 1) * 0.22f
}

data class TelemetrySnapshot(
    val speedKmh: Int = 0,
    val engineRpm: Int = 950,
    val rpmNormalized: Float = 0.15f,
    val gearLabel: String = "1",
    val gearShiftFlash: Boolean = false,
    val handbrakeActive: Boolean = false,
    val nitroNormalized: Float = 1.0f,
    val drifting: Boolean = false,
    val currentDriftPoints: Long = 0L,
    val driftMultiplier: Int = 1,
    val totalSessionDriftScore: Long = 0L,
    val mode: GameMode = GameMode.FREE_ROAM,
    val activeEventTitle: String = "FREE DRIVE",
    val checkpointIndex: Int = 0,
    val totalCheckpoints: Int = 0,
    val elapsedMs: Long = 0L,
    val remainingMs: Long = 0L,
    val countdownLabel: String? = null,
    val racePosition: Int = 1,
    val totalRacers: Int = 1,
    val carX: Float = 0f,
    val carY: Float = 0f,
    val carZ: Float = 0f,
    val carHeadingRad: Float = 0f,
    val nextCheckpointX: Float? = null,
    val nextCheckpointZ: Float? = null,
    val radarBlips: List<RadarBlip> = emptyList(),
    val districtName: String = "DOWNTOWN",
    val timeOfDayLabel: String = "NIGHT",
    val weatherLabel: String = "CLEAR",
    val roadWetness: Float = 0f,
    val collisionIntensity: Float = 0f,
    val avgSuspensionCompression: Float = 0.35f,
    val fps: Int = 60
)

data class RaceResult(
    val eventId: String?,
    val eventTitle: String,
    val mode: GameMode,
    val elapsedMs: Long,
    val driftScore: Long,
    val highestCombo: Long,
    val medal: MedalTier,
    val creditsEarned: Long,
    val distanceKm: Float,
    val finishPosition: Int = 1,
    val totalRacers: Int = 1,
    val xpEarned: Long = 0L,
    val leveledUpTo: Int? = null,
    val newlyUnlockedAchievements: List<String> = emptyList()
)

object PlayerProgression {
    const val MAX_LEVEL = 50

    /** Cumulative XP required to reach [level] (Level 1 = 0 XP, Level 2 = 400 XP, Level 3 = 950 XP, etc.). */
    fun xpRequiredForLevel(level: Int): Long {
        val clamped = level.coerceIn(1, MAX_LEVEL)
        if (clamped <= 1) return 0L
        var total = 0L
        for (l in 1 until clamped) {
            total += 400L + (l - 1) * 150L
        }
        return total
    }

    fun levelForXp(xp: Long): Int {
        val safeXp = xp.coerceAtLeast(0L)
        var lvl = 1
        while (lvl < MAX_LEVEL && safeXp >= xpRequiredForLevel(lvl + 1)) {
            lvl++
        }
        return lvl
    }

    /** Normalized 0..1 progress within the current driver level. */
    fun xpProgressInCurrentLevel(xp: Long): Float {
        val lvl = levelForXp(xp)
        if (lvl >= MAX_LEVEL) return 1f
        val start = xpRequiredForLevel(lvl)
        val next = xpRequiredForLevel(lvl + 1)
        val span = (next - start).coerceAtLeast(1L)
        return ((xp - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    fun calculateRaceXp(result: RaceResult): Long {
        val modeBase = when (result.mode) {
            GameMode.STREET_RACE -> 260L
            GameMode.TIME_TRIAL -> 220L
            GameMode.CHECKPOINT_RACE -> 210L
            GameMode.DRIFT_CHALLENGE -> 230L
            GameMode.FREE_ROAM -> 90L
        }
        val medalBonus = when (result.medal) {
            MedalTier.GOLD -> 260L
            MedalTier.SILVER -> 160L
            MedalTier.BRONZE -> 95L
            MedalTier.NONE -> 35L
        }
        val podiumBonus = if (result.mode == GameMode.STREET_RACE && result.finishPosition == 1) 120L else 0L
        val driftBonus = (result.driftScore / 80L).coerceAtMost(180L)
        return modeBase + medalBonus + podiumBonus + driftBonus
    }

    fun evaluateNewAchievements(
        profile: PlayerProfileDoc,
        garageCars: Map<String, GarageCarDoc>,
        careerProgress: Map<String, CareerProgressDoc>
    ): List<AchievementSpec> {
        val owned = profile.unlockedAchievements.toSet()
        val goldCount = careerProgress.values.count { MedalTier.fromRaw(it.medalTier) == MedalTier.GOLD }
        val maxUpgradedAnyCar = garageCars.values.any { car ->
            car.engineLevel >= 5L || car.turboLevel >= 5L || car.brakesLevel >= 5L ||
                car.tiresLevel >= 5L || car.suspensionLevel >= 5L || car.nitroLevel >= 5L
        }
        val customizedAnyCar = garageCars.values.any { car ->
            car.spoilerStyle != SpoilerStyle.STOCK.name ||
                car.bodyKitStyle != BodyKitStyle.STREET.name ||
                car.windowTint != WindowTint.SMOKE.name ||
                car.headlightStyle != HeadlightStyle.XENON_WHITE.name
        }

        val unlocked = mutableListOf<AchievementSpec>()
        for (ach in GameCatalog.achievements) {
            if (ach.id in owned) continue
            val earned = when (ach.id) {
                "first_victory" -> profile.racesWon >= 1L || goldCount >= 1
                "street_veteran" -> profile.racesCompleted >= 5L
                "drift_legend" -> profile.highestDriftCombo >= 5000L
                "road_warrior" -> profile.totalDistanceKm >= 10.0
                "garage_collector" -> garageCars.size >= 3
                "full_fleet" -> garageCars.size >= GameCatalog.cars.size
                "style_icon" -> customizedAnyCar
                "tuner_pro" -> maxUpgradedAnyCar
                "hypercar_club" -> garageCars.containsKey("spectre_rs")
                "rising_star" -> profile.level >= 5L
                else -> false
            }
            if (earned) unlocked.add(ach)
        }
        return unlocked
    }
}

object GameCatalog {
    val cars: List<CarSpec> = listOf(
        CarSpec(
            id = "vipera_gtx",
            name = "VIPERA GT-X",
            category = "SPORTS CAR",
            vehicleClass = VehicleClass.SPORTS,
            price = 0L,
            unlockLevel = 1,
            baseTopSpeedKmh = 282f,
            baseAcceleration = 35f,
            baseHandling = 1.04f,
            baseBraking = 1.00f,
            weightKg = 1380f,
            baseNitroCapacity = 1.00f,
            baseNitroPower = 1.32f,
            defaultPaintHex = "#E50914",
            defaultUnderglowHex = "#00E5FF",
            bodyLength = 4.65f,
            bodyWidth = 2.06f,
            spoilerHeight = 0.30f
        ),
        CarSpec(
            id = "apex_kuro",
            name = "APEX KURO R",
            category = "DRIFT CAR",
            vehicleClass = VehicleClass.DRIFT,
            price = 18000L,
            unlockLevel = 2,
            baseTopSpeedKmh = 294f,
            baseAcceleration = 39f,
            baseHandling = 1.18f,
            baseBraking = 1.06f,
            weightKg = 1240f,
            baseNitroCapacity = 1.10f,
            baseNitroPower = 1.38f,
            defaultPaintHex = "#1565C0",
            defaultUnderglowHex = "#FF2A36",
            bodyLength = 4.54f,
            bodyWidth = 2.04f,
            spoilerHeight = 0.44f
        ),
        CarSpec(
            id = "titan_v8",
            name = "TITAN HELLFIRE V8",
            category = "MUSCLE CAR",
            vehicleClass = VehicleClass.MUSCLE,
            price = 28000L,
            unlockLevel = 3,
            baseTopSpeedKmh = 308f,
            baseAcceleration = 45f,
            baseHandling = 0.96f,
            baseBraking = 0.95f,
            weightKg = 1680f,
            baseNitroCapacity = 1.24f,
            baseNitroPower = 1.46f,
            defaultPaintHex = "#FF6D00",
            defaultUnderglowHex = "#FFB800",
            bodyLength = 4.92f,
            bodyWidth = 2.18f,
            spoilerHeight = 0.22f
        ),
        CarSpec(
            id = "solstice_v10",
            name = "SOLSTICE V10",
            category = "SUPERCAR",
            vehicleClass = VehicleClass.SUPERCAR,
            price = 42000L,
            unlockLevel = 5,
            baseTopSpeedKmh = 326f,
            baseAcceleration = 47f,
            baseHandling = 1.16f,
            baseBraking = 1.16f,
            weightKg = 1410f,
            baseNitroCapacity = 1.18f,
            baseNitroPower = 1.48f,
            defaultPaintHex = "#FFB800",
            defaultUnderglowHex = "#FFB800",
            bodyLength = 4.76f,
            bodyWidth = 2.16f,
            spoilerHeight = 0.32f
        ),
        CarSpec(
            id = "spectre_rs",
            name = "SPECTRE RS-X",
            category = "HYPERCAR",
            vehicleClass = VehicleClass.HYPERCAR,
            price = 68000L,
            unlockLevel = 7,
            baseTopSpeedKmh = 354f,
            baseAcceleration = 53f,
            baseHandling = 1.28f,
            baseBraking = 1.28f,
            weightKg = 1285f,
            baseNitroCapacity = 1.36f,
            baseNitroPower = 1.56f,
            defaultPaintHex = "#ECEFF1",
            defaultUnderglowHex = "#00E5FF",
            bodyLength = 4.88f,
            bodyWidth = 2.22f,
            spoilerHeight = 0.50f
        )
    )

    val paints: List<PaintSwatch> = listOf(
        PaintSwatch("CRIMSON RED", "#E50914"),
        PaintSwatch("OBSIDIAN CARBON", "#1B1F29"),
        PaintSwatch("MIDNIGHT COBALT", "#1565C0"),
        PaintSwatch("GLACIER PEARL", "#ECEFF1"),
        PaintSwatch("SOLAR GOLD", "#FFB800"),
        PaintSwatch("HELLFIRE ORANGE", "#FF6D00"),
        PaintSwatch("EMERALD MATTE", "#00A86B"),
        PaintSwatch("VENOM VIOLET", "#7B1FA2")
    )

    val underglows: List<PaintSwatch> = listOf(
        PaintSwatch("NITRO CYAN", "#00E5FF"),
        PaintSwatch("CRIMSON PULSE", "#E50914"),
        PaintSwatch("AMBER NEON", "#FFB800"),
        PaintSwatch("HYPER VIOLET", "#B388FF"),
        PaintSwatch("ACID EMERALD", "#00E676"),
        PaintSwatch("OFF", "NONE")
    )

    val achievements: List<AchievementSpec> = listOf(
        AchievementSpec("first_victory", "FIRST VICTORY", "Win a street race or earn your first Gold medal", 3000L, 250L),
        AchievementSpec("street_veteran", "STREET VETERAN", "Complete 5 career races or challenges", 4500L, 350L),
        AchievementSpec("drift_legend", "DRIFT LEGEND", "Bank a single drift combo of 5,000+ points", 4000L, 320L),
        AchievementSpec("road_warrior", "ROAD WARRIOR", "Drive 10+ total kilometers across the city", 3500L, 300L),
        AchievementSpec("style_icon", "STYLE ICON", "Install a custom spoiler, body kit, tint, or headlights", 2500L, 200L),
        AchievementSpec("tuner_pro", "TUNER PRO", "Max any mechanical upgrade track to Level 5", 5500L, 450L),
        AchievementSpec("garage_collector", "GARAGE COLLECTOR", "Own at least 3 vehicles in your garage collection", 6000L, 500L),
        AchievementSpec("hypercar_club", "HYPERCAR CLUB", "Unlock the Spectre RS-X Hypercar", 8000L, 650L),
        AchievementSpec("rising_star", "RISING STAR", "Reach Driver Level 5", 5000L, 400L),
        AchievementSpec("full_fleet", "COMPLETE FLEET", "Collect all 5 vehicle classes in the Garage", 15000L, 1200L)
    )

    fun carById(id: String): CarSpec = cars.firstOrNull { it.id == id } ?: cars.first()

    fun upgradeCost(currentLevel: Int): Long = when (currentLevel) {
        1 -> 3500L
        2 -> 6500L
        3 -> 11000L
        4 -> 18000L
        else -> 0L
    }

    val careerEvents: List<CareerEventSpec> = listOf(
        CareerEventSpec(
            id = "downtown_street_clash",
            title = "DOWNTOWN STREET CLASH",
            district = "DOWNTOWN",
            mode = GameMode.STREET_RACE,
            description = "Battle 3 aggressive AI street racers through Downtown avenues and live traffic.",
            spawnX = 0f, spawnY = 0f, spawnZ = 220f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(0f, 0f, 120f),
                CheckpointNode(0f, 0f, 0f),
                CheckpointNode(120f, 0f, 0f),
                CheckpointNode(120f, 0f, -120f),
                CheckpointNode(0f, 0f, -120f),
                CheckpointNode(-120f, 0f, -120f),
                CheckpointNode(-120f, 0f, 0f),
                CheckpointNode(0f, 0f, 120f)
            ),
            goldTarget = 42000L, silverTarget = 52000L, bronzeTarget = 66000L,
            rewardCredits = 7500L
        ),
        CareerEventSpec(
            id = "racing_district_gp",
            title = "RACING DISTRICT GRAND PRIX",
            district = "RACING DISTRICT",
            mode = GameMode.STREET_RACE,
            description = "High-speed pack battle across illuminated grandstands and apex kerbs.",
            spawnX = -240f, spawnY = 0f, spawnZ = -120f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(-240f, 0f, -240f),
                CheckpointNode(-360f, 0f, -240f),
                CheckpointNode(-360f, 0f, -360f),
                CheckpointNode(-240f, 0f, -360f),
                CheckpointNode(-120f, 0f, -240f),
                CheckpointNode(-120f, 0f, -120f),
                CheckpointNode(-240f, 0f, -120f)
            ),
            goldTarget = 38000L, silverTarget = 48000L, bronzeTarget = 60000L,
            rewardCredits = 9000L
        ),
        CareerEventSpec(
            id = "downtown_sprint",
            title = "MIDNIGHT AVENUE SPRINT",
            district = "DOWNTOWN",
            mode = GameMode.CHECKPOINT_RACE,
            description = "Thread through neon skyscrapers and busy signalized central intersections.",
            spawnX = 0f, spawnY = 0f, spawnZ = 220f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(0f, 0f, 120f),
                CheckpointNode(0f, 0f, 0f),
                CheckpointNode(120f, 0f, 0f),
                CheckpointNode(120f, 0f, -120f),
                CheckpointNode(0f, 0f, -120f),
                CheckpointNode(-120f, 0f, -120f),
                CheckpointNode(-120f, 0f, 0f),
                CheckpointNode(0f, 0f, 0f)
            ),
            goldTarget = 38000L, silverTarget = 48000L, bronzeTarget = 62000L,
            rewardCredits = 5500L
        ),
        CareerEventSpec(
            id = "residential_rush",
            title = "RESIDENTIAL BOULEVARD RUSH",
            district = "RESIDENTIAL DISTRICT",
            mode = GameMode.CHECKPOINT_RACE,
            description = "Weave through tree-lined residential avenues and quiet neighborhood junctions.",
            spawnX = -360f, spawnY = 0f, spawnZ = 320f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(-360f, 0f, 240f),
                CheckpointNode(-360f, 0f, 120f),
                CheckpointNode(-240f, 0f, 120f),
                CheckpointNode(-240f, 0f, 240f),
                CheckpointNode(-120f, 0f, 240f),
                CheckpointNode(-120f, 0f, 360f),
                CheckpointNode(-240f, 0f, 360f)
            ),
            goldTarget = 36000L, silverTarget = 46000L, bronzeTarget = 58000L,
            rewardCredits = 6200L
        ),
        CareerEventSpec(
            id = "tunnel_blitz",
            title = "NEON TUNNEL BLITZ",
            district = "TUNNEL",
            mode = GameMode.TIME_TRIAL,
            description = "Full-throttle run diving into the glowing West Boulevard tunnel.",
            spawnX = -240f, spawnY = 0f, spawnZ = 240f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(-240f, 0f, 120f),
                CheckpointNode(-240f, 0f, 0f),
                CheckpointNode(-240f, 0f, -120f),
                CheckpointNode(-240f, 0f, -240f),
                CheckpointNode(-120f, 0f, -240f),
                CheckpointNode(0f, 0f, -240f)
            ),
            goldTarget = 29000L, silverTarget = 37000L, bronzeTarget = 48000L,
            rewardCredits = 6500L
        ),
        CareerEventSpec(
            id = "bay_bridge_run",
            title = "BAY SUSPENSION CROSSING",
            district = "BRIDGE",
            mode = GameMode.STREET_RACE,
            description = "4-car showdown across the cable-stayed suspension bridge over the bay.",
            spawnX = -220f, spawnY = 0f, spawnZ = -360f, spawnHeadingRad = (-Math.PI / 2.0).toFloat(),
            checkpoints = listOf(
                CheckpointNode(-100f, 3.8f, -360f),
                CheckpointNode(0f, 7.5f, -360f),
                CheckpointNode(100f, 3.8f, -360f),
                CheckpointNode(240f, 0f, -360f),
                CheckpointNode(240f, 0f, -240f),
                CheckpointNode(240f, 0f, -120f),
                CheckpointNode(120f, 0f, -120f)
            ),
            goldTarget = 37000L, silverTarget = 46000L, bronzeTarget = 58000L,
            rewardCredits = 8500L
        ),
        CareerEventSpec(
            id = "industrial_drift_yard",
            title = "INDUSTRIAL DOCK DRIFT",
            district = "INDUSTRIAL DISTRICT",
            mode = GameMode.DRIFT_CHALLENGE,
            description = "60-second slide fest between cargo warehouses, silos, and amber floodlights.",
            spawnX = 240f, spawnY = 0f, spawnZ = 240f, spawnHeadingRad = 0f,
            checkpoints = emptyList(),
            goldTarget = 16000L, silverTarget = 9500L, bronzeTarget = 4500L,
            rewardCredits = 7500L
        ),
        CareerEventSpec(
            id = "plaza_drift_king",
            title = "CENTRAL PLAZA DRIFT",
            district = "DOWNTOWN",
            mode = GameMode.DRIFT_CHALLENGE,
            description = "60-second open drift session around wide Downtown avenues. Chain 5x combos!",
            spawnX = 0f, spawnY = 0f, spawnZ = 100f, spawnHeadingRad = 0f,
            checkpoints = emptyList(),
            goldTarget = 14000L, silverTarget = 8500L, bronzeTarget = 4000L,
            rewardCredits = 7000L
        ),
        CareerEventSpec(
            id = "skyway_express",
            title = "ELEVATED SKYWAY LOOP",
            district = "HIGHWAY",
            mode = GameMode.TIME_TRIAL,
            description = "Climb the East ramp onto the elevated highway deck at redline speeds.",
            spawnX = 360f, spawnY = 0f, spawnZ = 340f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(360f, 4.7f, 220f),
                CheckpointNode(360f, 9.5f, 100f),
                CheckpointNode(360f, 9.5f, -40f),
                CheckpointNode(360f, 9.5f, -180f),
                CheckpointNode(360f, 4.7f, -280f),
                CheckpointNode(240f, 0f, -360f)
            ),
            goldTarget = 31000L, silverTarget = 40000L, bronzeTarget = 52000L,
            rewardCredits = 8500L
        ),
        CareerEventSpec(
            id = "grand_metropolis_gp",
            title = "GRAND METROPOLIS CIRCUIT",
            district = "ALL DISTRICTS",
            mode = GameMode.STREET_RACE,
            description = "Ultimate 4-car championship uniting Downtown, Tunnel, Bridge, Racing District & Highway.",
            spawnX = 0f, spawnY = 0f, spawnZ = 240f, spawnHeadingRad = 0f,
            checkpoints = listOf(
                CheckpointNode(0f, 0f, 0f),
                CheckpointNode(-240f, 0f, 0f),
                CheckpointNode(-240f, 0f, -240f),
                CheckpointNode(-240f, 0f, -360f),
                CheckpointNode(0f, 7.5f, -360f),
                CheckpointNode(240f, 0f, -360f),
                CheckpointNode(240f, 0f, 0f),
                CheckpointNode(240f, 0f, 240f),
                CheckpointNode(0f, 0f, 240f)
            ),
            goldTarget = 64000L, silverTarget = 80000L, bronzeTarget = 100000L,
            rewardCredits = 16000L
        )
    )

    fun eventById(id: String): CareerEventSpec =
        careerEvents.firstOrNull { it.id == id } ?: careerEvents.first()

    fun defaultQuickEvent(mode: GameMode): CareerEventSpec? = when (mode) {
        GameMode.FREE_ROAM -> null
        GameMode.STREET_RACE -> careerEvents.first { it.id == "downtown_street_clash" }
        GameMode.CHECKPOINT_RACE -> careerEvents.first { it.id == "downtown_sprint" }
        GameMode.TIME_TRIAL -> careerEvents.first { it.id == "skyway_express" }
        GameMode.DRIFT_CHALLENGE -> careerEvents.first { it.id == "industrial_drift_yard" }
    }
}

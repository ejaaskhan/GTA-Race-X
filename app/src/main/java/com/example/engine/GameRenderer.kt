package com.example.engine

import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.audio.EngineAudioSynthesizer
import com.example.audio.UiSoundType
import com.example.data.ActiveCarBuild
import com.example.data.CareerEventSpec
import com.example.data.DeviceSettings
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.BodyKitStyle
import com.example.data.RaceResult
import com.example.data.RadarBlip
import com.example.data.RimStyle
import com.example.data.SpoilerStyle
import com.example.data.TelemetrySnapshot
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GameRenderer(
    private val controlInput: ControlInput,
    private val audioSynthesizer: EngineAudioSynthesizer,
    private val onTelemetryUpdate: (TelemetrySnapshot) -> Unit,
    private val onRaceFinished: (RaceResult) -> Unit
) : GLSurfaceView.Renderer {

    val physics = VehiclePhysics()
    val environment = EnvironmentSystem()
    val trafficSystem = TrafficSystem()
    val aiRacerSystem = AiRacerSystem()

    @Volatile var activeCarBuild: ActiveCarBuild = ActiveCarBuild(
        spec = GameCatalog.cars.first(),
        paintHex = GameCatalog.cars.first().defaultPaintHex,
        rimStyle = RimStyle.CHROME,
        underglowHex = GameCatalog.cars.first().defaultUnderglowHex,
        engineLevel = 1,
        nitroLevel = 1,
        handlingLevel = 1,
        brakesLevel = 1
    )
    @Volatile var settings: DeviceSettings = DeviceSettings()

    private var litShader: LitShaderProgram? = null
    private var unlitShader: UnlitOverlayShader? = null
    private var textures: ProceduralTextures? = null
    private var cityWorld: CityWorld? = null
    private var showroomStage: ShowroomStageMeshes? = null
    private val vfxSystem = DrivingVfxSystem()
    private val carMeshesCache = mutableMapOf<String, CarMeshes>()
    private var glInitStage: Int = 0
    private var nextCarWarmupIndex: Int = 0

    private val vfxListener = object : DrivingVfxListener {
        override fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {
            vfxSystem.emitTireSmoke(wx, wy, wz, carVx, carVz)
        }

        override fun onSkidMark(
            prevX: Float, prevY: Float, prevZ: Float,
            currX: Float, currY: Float, currZ: Float,
            rightX: Float, rightZ: Float
        ) {
            vfxSystem.emitSkidMark(prevX, prevY, prevZ, currX, currY, currZ, rightX, rightZ)
        }

        override fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
            vfxSystem.emitCollisionSparks(wx, wy, wz, nx, nz, intensity)
            audioSynthesizer.triggerCollision(intensity)
        }

        override fun onCrashDebris(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
            vfxSystem.emitCrashDebris(wx, wy, wz, nx, nz, intensity)
        }

        override fun onEngineSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float, damageSeverity: Float) {
            vfxSystem.emitEngineSmoke(wx, wy, wz, carVx, carVz, damageSeverity)
        }

        override fun onRepairCompleted() {
            audioSynthesizer.triggerUiSound(UiSoundType.UNLOCK)
        }
    }

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val wheelMatrix = FloatArray(16)
    private val chassisMatrix = FloatArray(16)

    // Pre-allocated zero-GC scratch containers for per-frame culling & telemetry (expanded world)
    private val visibleChunksScratch = ArrayList<CityChunk>(96)
    private val visibleChunkDistsScratch = FloatArray(96)
    private val radarBlipsScratch = ArrayList<RadarBlip>(48)
    private val rgbScratch = FloatArray(3)
    private val wheelSignX = floatArrayOf(-1f, 1f, -1f, 1f)
    private val wheelSignZ = floatArrayOf(-1f, -1f, 1f, 1f)

    private var aspectRatio: Float = 16f / 9f
    private var lastFrameNanos: Long = 0L
    private var lastTelemetryNanos: Long = 0L
    private var frameCounter: Int = 0
    private var fpsAccumNanos: Long = 0L
    private var currentFps: Int = 60
    private var animTimeSec: Float = 0f
    private var nitroParticleAccum: Float = 0f
    private var rainSprayAccum: Float = 0f
    private var lastCountdownLabel: String? = null

    // Pending actions queued from UI thread -> GL thread
    private val pendingActions = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    fun enqueue(action: () -> Unit) {
        pendingActions.add(action)
    }

    fun setScreenMode(screen: GameScreen) {
        enqueue {
            physics.screenMode = screen
            if (screen != GameScreen.RACING) {
                vfxSystem.clear()
                aiRacerSystem.setupForSession(GameMode.FREE_ROAM, null)
            }
            audioSynthesizer.activeRacing = (screen == GameScreen.RACING && !physics.paused)
        }
    }

    fun startRaceSession(mode: GameMode, event: CareerEventSpec?) {
        enqueue {
            controlInput.clear()
            vfxSystem.clear()
            physics.startSession(mode, event)
            aiRacerSystem.setupForSession(mode, event)
            trafficSystem.resetTraffic(
                activeCount = settings.graphicsQuality.maxTrafficVehicles,
                avoidX = physics.x,
                avoidZ = physics.z
            )
            physics.screenMode = GameScreen.RACING
            audioSynthesizer.activeRacing = true
        }
    }

    fun setPaused(paused: Boolean) {
        enqueue {
            physics.paused = paused
            if (paused) controlInput.clear()
            audioSynthesizer.activeRacing = (physics.screenMode == GameScreen.RACING && !paused)
        }
    }

    fun recoverCar() {
        enqueue {
            physics.recoverCarOnRoad()
        }
    }

    fun repairCar() {
        enqueue {
            physics.repairCar(vfxListener = vfxListener, showNotice = true)
        }
    }

    fun triggerLaunchFlyIn() {
        enqueue {
            physics.triggerLaunchFlyIn()
        }
    }

    fun releaseGlResources() {
        enqueue {
            litShader?.release()
            unlitShader?.release()
            textures?.release()
            vfxSystem.release()
            litShader = null
            unlitShader = null
            textures = null
            cityWorld = null
            showroomStage = null
            carMeshesCache.clear()
            glInitStage = 0
            nextCarWarmupIndex = 0
        }
    }

    private fun getOrBuildCarMesh(carId: String): CarMeshes {
        carMeshesCache[carId]?.let { return it }
        val spec = GameCatalog.carById(carId)
        val built = CarMeshFactory.buildCar(spec)
        carMeshesCache[spec.id] = built
        return built
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Keep onSurfaceCreated ultra-fast (<5ms) so Android's SurfaceSyncGroup receives
        // the first SurfaceView frame transaction well within its 1000ms budget.
        GLES30.glClearColor(environment.skyR, environment.skyG, environment.skyB, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        litShader = null
        unlitShader = null
        textures = null
        cityWorld = null
        showroomStage = null
        carMeshesCache.clear()
        glInitStage = 0
        nextCarWarmupIndex = 0
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val rawDt = if (lastFrameNanos == 0L) 0.016f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        val dt = rawDt.coerceIn(0.004f, 0.05f)
        animTimeSec += dt

        // FPS tracker
        frameCounter++
        fpsAccumNanos += (dt * 1_000_000_000f).toLong()
        if (fpsAccumNanos >= 500_000_000L) {
            currentFps = ((frameCounter * 1_000_000_000L) / fpsAccumNanos).toInt().coerceIn(1, 120)
            frameCounter = 0
            fpsAccumNanos = 0L
        }

        // Drain pending UI commands
        while (true) {
            val action = pendingActions.poll() ?: break
            action()
        }

        // Staged progressive GL resource initialization so Frame 0 completes immediately (<15ms)
        // and each subsequent warmup frame stays short behind the launch splash screen.
        if (glInitStage < 5) {
            GLES30.glClearColor(0.04f, 0.05f, 0.08f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            when (glInitStage) {
                0 -> {
                    // Frame 0: Commit first clean swap immediately for SurfaceSyncGroup!
                    glInitStage = 1
                }
                1 -> {
                    litShader = LitShaderProgram()
                    unlitShader = UnlitOverlayShader()
                    vfxSystem.initGl()
                    glInitStage = 2
                }
                2 -> {
                    textures = ProceduralTextures()
                    glInitStage = 3
                }
                3 -> {
                    showroomStage = CarMeshFactory.buildShowroomStage()
                    getOrBuildCarMesh(activeCarBuild.spec.id)
                    glInitStage = 4
                }
                4 -> {
                    cityWorld = CityWorld()
                    glInitStage = 5
                }
            }
            lastFrameNanos = System.nanoTime()
            return
        }

        // Warm up remaining car classes 1 per frame without stalling the main scene
        if (nextCarWarmupIndex < GameCatalog.cars.size) {
            val spec = GameCatalog.cars[nextCarWarmupIndex++]
            if (!carMeshesCache.containsKey(spec.id)) {
                carMeshesCache[spec.id] = CarMeshFactory.buildCar(spec)
            }
        }

        val world = cityWorld ?: return
        val shader = litShader ?: return
        val unlit = unlitShader ?: return
        val tex = textures ?: return
        val carBuild = activeCarBuild
        val curSettings = settings
        val quality = curSettings.graphicsQuality

        // 1. Update dynamic Day/Night cycle, VFX pool budget & Weather (Rain / Fog / Wet Grip)
        vfxSystem.particleBudget = quality.maxParticles
        environment.update(dt, curSettings.timeOfDayPreset, curSettings.weatherPreset)

        // 2. Fixed-substep 60Hz+ vehicle, traffic & AI racer simulation (capped to avoid frame spikes)
        val steps = if (dt > 0.020f) 2 else 1
        val subDt = dt / steps
        var playerPosition = aiRacerSystem.computePlayerPosition(physics)
        val totalRacers = aiRacerSystem.totalParticipants()

        for (i in 0 until steps) {
            if (!physics.paused) {
                trafficSystem.step(
                    dt = subDt,
                    activeCount = quality.maxTrafficVehicles,
                    playerPhysics = physics,
                    vfxListener = vfxListener
                )
                if (physics.screenMode == GameScreen.RACING && !physics.eventFinished) {
                    aiRacerSystem.step(
                        dt = subDt,
                        countdownActive = physics.isCountdownActive,
                        elapsedMs = physics.elapsedMs,
                        playerPhysics = physics,
                        trafficSystem = trafficSystem,
                        roadWetness = environment.roadWetness,
                        vfxListener = vfxListener
                    )
                    playerPosition = aiRacerSystem.computePlayerPosition(physics)
                }
            }
            physics.step(
                dt = subDt,
                input = controlInput,
                carBuild = carBuild,
                steeringSensitivity = curSettings.steeringSensitivity,
                cameraMode = curSettings.cameraMode,
                colliders = world.colliders,
                vfxListener = vfxListener,
                tireGripMultiplier = environment.tireGripMultiplier,
                currentRacePosition = playerPosition,
                totalRacers = totalRacers,
                onRaceFinished = onRaceFinished
            )
        }

        if (physics.screenMode == GameScreen.RACING && !physics.paused) {
            // Emit pooled Nitro exhaust flame trail particles when boosting
            if (physics.nitroFiring) {
                nitroParticleAccum += dt
                if (nitroParticleAccum >= 0.024f) {
                    nitroParticleAccum = 0f
                    val fX = -sin(physics.headingRad)
                    val fZ = -cos(physics.headingRad)
                    val rX = cos(physics.headingRad)
                    val rZ = -sin(physics.headingRad)
                    val rearOff = carBuild.spec.bodyLength * 0.52f
                    for (side in -1..1 step 2) {
                        val px = physics.x - fX * rearOff + rX * (side * 0.34f)
                        val pz = physics.z - fZ * rearOff + rZ * (side * 0.34f)
                        vfxSystem.emitNitroFlame(px, physics.y + 0.34f, pz, -fX, -fZ, physics.vx, physics.vz)
                    }
                }
            }
            // Emit subtle wet tire mist spray when speeding in rain
            if (environment.rainIntensity > 0.25f && physics.speedMs > 22f) {
                rainSprayAccum += dt
                if (rainSprayAccum >= 0.055f) {
                    rainSprayAccum = 0f
                    vfxSystem.emitTireSmoke(physics.wheels[2].worldContactX, physics.y, physics.wheels[2].worldContactZ, physics.vx, physics.vz)
                    vfxSystem.emitTireSmoke(physics.wheels[3].worldContactX, physics.y, physics.wheels[3].worldContactZ, physics.vx, physics.vz)
                }
            }
            vfxSystem.update(dt)
        }

        // 3. Update multi-channel procedural audio synthesizer (Engine, Accel, Brake, Screech, Nitro, Countdown)
        val curCountdown = physics.countdownLabel
        if (curCountdown != null && curCountdown != lastCountdownLabel) {
            audioSynthesizer.triggerUiSound(
                if (curCountdown == "GO!") UiSoundType.CONFIRM else UiSoundType.COUNTDOWN
            )
        }
        lastCountdownLabel = curCountdown

        audioSynthesizer.rpmNormalized = physics.rpmNormalized
        audioSynthesizer.speedNormalized = (physics.speedMs / 95f).coerceIn(0f, 1f)
        audioSynthesizer.throttleActive = controlInput.throttle
        audioSynthesizer.brakeActive = controlInput.brake
        audioSynthesizer.nitroActive = physics.nitroFiring
        audioSynthesizer.drifting = physics.drifting || (physics.handbrakeActive && physics.speedMs > 5f)
        audioSynthesizer.activeRacing = (physics.screenMode == GameScreen.RACING && !physics.paused && !physics.eventFinished)

        // 4. Publish live telemetry + radar blips (rivals, traffic, and 4 glowing repair bays) at ~20 Hz
        if (now - lastTelemetryNanos >= 48_000_000L) {
            lastTelemetryNanos = now
            radarBlipsScratch.clear()
            for (bay in CityWorld.REPAIR_BAYS) {
                radarBlipsScratch.add(RadarBlip(bay.x, bay.z, isRival = false, isRepairBay = true))
            }
            aiRacerSystem.collectRivalBlips(radarBlipsScratch)
            trafficSystem.collectNearbyBlips(physics.x, physics.z, 280f, radarBlipsScratch)
            onTelemetryUpdate(
                physics.buildTelemetry(
                    fps = currentFps,
                    racePosition = playerPosition,
                    totalRacers = totalRacers,
                    timeOfDayLabel = environment.timeOfDayLabel,
                    weatherLabel = environment.weatherLabel,
                    roadWetness = environment.roadWetness,
                    radarBlips = radarBlipsScratch.toList()
                )
            )
        }

        val inGarageShowroom = (physics.screenMode == GameScreen.GARAGE)

        // 5. Render Frame — Dedicated Lit Neon Showroom Stage in Garage, Open-World City elsewhere
        if (inGarageShowroom) {
            GLES30.glClearColor(0.03f, 0.035f, 0.055f, 1.0f)
        } else {
            GLES30.glClearColor(environment.skyR, environment.skyG, environment.skyB, 1.0f)
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val farPlane = if (inGarageShowroom) 65f else quality.drawDistance + 80f
        Matrix.perspectiveM(projMatrix, 0, physics.camFovDeg, aspectRatio, 0.45f, farPlane)
        Matrix.setLookAtM(
            viewMatrix, 0,
            physics.camX + physics.shakeOffsetX,
            physics.camY + physics.shakeOffsetY,
            physics.camZ,
            physics.camLookX + physics.shakeOffsetX * 0.5f,
            physics.camLookY + physics.shakeOffsetY * 0.5f,
            physics.camLookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        if (inGarageShowroom) {
            renderGarageShowroomScene(shader, unlit, tex, carBuild)
            return
        }

        // Bind lit shader + dynamic atmosphere & lighting uniforms
        shader.use()
        GLES30.glUniform1f(shader.uDamageFactor, 0f)
        GLES30.glUniform3f(shader.uCameraPos, physics.camX, physics.camY, physics.camZ)
        GLES30.glUniform3f(shader.uFogColor, environment.fogR, environment.fogG, environment.fogB)
        val baseFogDensity = when (quality) {
            com.example.data.GraphicsQuality.LOW -> 0.0044f
            com.example.data.GraphicsQuality.MEDIUM -> 0.0031f
            com.example.data.GraphicsQuality.HIGH -> 0.0023f
        }
        GLES30.glUniform1f(shader.uFogDensity, baseFogDensity * environment.fogDensityMultiplier)
        GLES30.glUniform3f(shader.uSunDir, environment.sunDirX, environment.sunDirY, environment.sunDirZ)
        GLES30.glUniform3f(shader.uSunColor, environment.sunColorR, environment.sunColorG, environment.sunColorB)
        GLES30.glUniform1f(shader.uSunIntensity, environment.sunIntensity)
        GLES30.glUniform3f(shader.uSkyAmb, environment.skyAmbR, environment.skyAmbG, environment.skyAmbB)
        GLES30.glUniform3f(shader.uGroundAmb, environment.groundAmbR, environment.groundAmbG, environment.groundAmbB)
        GLES30.glUniform1f(shader.uNightFactor, environment.nightFactor)
        GLES30.glUniform1f(shader.uReflectionStrength, quality.reflectionStrength)

        // Dynamic car headlights position, forward aim & electrical flicker on heavy front crash
        val fwdX = -sin(physics.headingRad)
        val fwdZ = -cos(physics.headingRad)
        val headlightFlicker = if (physics.screenMode == GameScreen.RACING && physics.frontDamage > 0.50f) {
            if (sin(animTimeSec * 37f) > -0.35f) 0.55f else 0.12f
        } else {
            1.0f
        }
        GLES30.glUniform3f(shader.uHeadlightPos, physics.x + fwdX * 1.9f, physics.y + 0.72f, physics.z + fwdZ * 1.9f)
        GLES30.glUniform3f(shader.uHeadlightDir, fwdX, -0.10f, fwdZ)
        GLES30.glUniform1f(shader.uHeadlightPower, environment.headlightPower * headlightFlicker)

        // Identity world matrix for static city meshes
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uEmissive, 0f)

        // A. Ground base & bay water
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform1f(shader.uMetallic, 0.05f)
        GLES30.glUniform1f(shader.uRoughness, 0.9f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0f)
        world.groundBaseMesh.draw()

        GLES30.glUniform1f(shader.uMetallic, 0.85f)
        GLES30.glUniform1f(shader.uRoughness, 0.14f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 1.2f)
        world.waterMesh.draw()

        // B. Asphalt Roads with Dynamic Rain Wetness Reflections
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.asphaltTex)
        GLES30.glUniform1i(shader.uTexture, 0)
        GLES30.glUniform1i(shader.uUseTexture, 1)
        GLES30.glUniform1f(shader.uMetallic, 0.25f)
        GLES30.glUniform1f(shader.uRoughness, (0.34f - environment.roadWetness * 0.20f).coerceIn(0.10f, 0.45f))
        GLES30.glUniform1f(shader.uWetRoadSpec, environment.wetRoadSpec)
        world.roadMesh.draw()

        // C. Elevated Highway & Suspension Bridge Decks
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.bridgeTex)
        world.bridgeDeckMesh.draw()

        // D. West Neon Tunnel Walls & Ceiling
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.tunnelTex)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0f)
        GLES30.glUniform1f(shader.uRoughness, 0.55f)
        world.tunnelMesh.draw()

        // E. Bridge Pylons, Skyway Piers & Highway Gantries
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform1f(shader.uMetallic, 0.35f)
        GLES30.glUniform1f(shader.uRoughness, 0.45f)
        world.structuresMesh.draw()

        // Emissive landmark neon (brightens smoothly as dusk/night falls)
        val neonStrength = 0.35f + 1.05f * environment.nightFactor
        GLES30.glUniform1f(shader.uEmissive, neonStrength)
        world.landmarksNeonMesh.draw()
        GLES30.glUniform1f(shader.uEmissive, 0f)

        // F. Spatial Chunked City (7 Districts with Distance LOD & Frustum Culling)
        val maxDrawDist = quality.drawDistance
        val propDrawDist = quality.propDistance
        val lodSwitchDist = maxDrawDist * 0.62f

        val lookDx = physics.camLookX - physics.camX
        val lookDz = physics.camLookZ - physics.camZ
        val lookLen = hypot(lookDx, lookDz).coerceAtLeast(0.001f)
        val camFwdX = lookDx / lookLen
        val camFwdZ = lookDz / lookLen

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.buildingTex)
        GLES30.glUniform1i(shader.uUseTexture, 1)
        GLES30.glUniform1f(shader.uMetallic, 0.42f)
        GLES30.glUniform1f(shader.uRoughness, 0.30f)

        visibleChunksScratch.clear()
        for (chunk in world.chunks) {
            val dx = chunk.centerX - physics.camX
            val dz = chunk.centerZ - physics.camZ
            val dist = hypot(dx, dz)
            if (dist > maxDrawDist) continue
            if (dist > 85f) {
                val dot = (dx * camFwdX + dz * camFwdZ) / dist
                if (dot < -0.15f) continue
            }
            val idx = visibleChunksScratch.size
            if (idx < visibleChunkDistsScratch.size) {
                visibleChunksScratch.add(chunk)
                visibleChunkDistsScratch[idx] = dist
                if (dist <= lodSwitchDist) {
                    chunk.buildingsMesh?.draw()
                }
            }
        }

        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform1f(shader.uMetallic, 0.2f)
        GLES30.glUniform1f(shader.uRoughness, 0.65f)
        for (i in 0 until visibleChunksScratch.size) {
            val chunk = visibleChunksScratch[i]
            val dist = visibleChunkDistsScratch[i]
            if (dist > lodSwitchDist) chunk.lodMesh?.draw()
            if (dist <= propDrawDist) chunk.propsMesh?.draw()
        }

        GLES30.glUniform1f(shader.uEmissive, neonStrength)
        for (i in 0 until visibleChunksScratch.size) {
            if (visibleChunkDistsScratch[i] <= maxDrawDist * 0.85f) {
                visibleChunksScratch[i].neonMesh?.draw()
            }
        }
        GLES30.glUniform1f(shader.uEmissive, 0f)

        // G. Live Intersection Traffic Signal Bulbs (Red / Yellow / Green)
        renderLiveTrafficSignals(shader, world, propDrawDist)

        // H. Render Distance-Culled & LOD-Scaled AI Traffic Vehicles
        renderTrafficVehicles(shader, world, maxDrawDist * 0.78f, quality.lodDetailDistance)

        // I. Render 3 Competitive AI Street Racers (in Street Racing Mode, with Distance LOD)
        if (physics.screenMode == GameScreen.RACING && aiRacerSystem.activeRace) {
            renderAiRacers(shader, unlit, tex, quality.shadowEnabled, quality.lodDetailDistance)
        }

        // J. Render Active Player Sports Car + 4 Independently Suspended, Rotating & Steering Wheels
        val carMeshes = getOrBuildCarMesh(carBuild.spec.id)
        renderPlayerCar(shader, unlit, tex, carMeshes, carBuild, quality.shadowEnabled)

        // K. Render 3D Start Line Gantry, Checkered Finish Line Gantry & Active Checkpoint Beacon
        val event = physics.activeEvent
        if (physics.screenMode == GameScreen.RACING && event != null) {
            renderEventGantries(shader, unlit, world, event)
        }

        // L. Render Pooled Driving VFX (Skid Marks, Tire Smoke, Nitro Flames, Sparks) & Quality-Scaled 3D Rain
        if (physics.screenMode == GameScreen.RACING) {
            vfxSystem.render(vpMatrix, physics.camX, physics.camZ, physics.x, physics.z)
        }
        if (environment.rainIntensity > 0.05f) {
            vfxSystem.renderRain(
                vpMatrix,
                physics.camX, physics.camZ,
                physics.x, physics.y, physics.z,
                environment.rainIntensity,
                animTimeSec,
                quality.rainDensityScale
            )
        }
    }

    private fun renderLiveTrafficSignals(shader: LitShaderProgram, world: CityWorld, maxDist: Float) {
        shader.use()
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform1f(shader.uEmissive, 2.2f)

        val (r, g, b) = when (trafficSystem.signalState) {
            TrafficSignalState.NS_GREEN -> Triple(0.05f, 0.96f, 0.35f)
            TrafficSignalState.NS_YELLOW -> Triple(1.0f, 0.76f, 0.08f)
            TrafficSignalState.EW_GREEN -> Triple(1.0f, 0.12f, 0.16f)
            TrafficSignalState.EW_YELLOW -> Triple(1.0f, 0.76f, 0.08f)
        }
        GLES30.glUniform3f(shader.uTintColor, r, g, b)

        for ((sx, sz) in world.signalPositions) {
            val dist = hypot(sx - physics.camX, sz - physics.camZ)
            if (dist > maxDist) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, sx, 6.45f, sz + 0.38f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
            world.signalBulbMesh.draw()
        }
        GLES30.glUniform1f(shader.uEmissive, 0f)
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
    }

    private fun renderTrafficVehicles(
        shader: LitShaderProgram,
        world: CityWorld,
        maxDist: Float,
        lodDetailDist: Float
    ) {
        shader.use()
        GLES30.glUniform1f(shader.uDamageFactor, 0f)
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0f)

        for (v in trafficSystem.vehicles) {
            if (!v.active) continue
            val dist = hypot(v.x - physics.camX, v.z - physics.camZ)
            if (dist > maxDist) continue

            val headingDeg = Math.toDegrees(v.headingRad.toDouble()).toFloat()
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, v.x, v.y, v.z)
            Matrix.rotateM(modelMatrix, 0, headingDeg, 0f, 1f, 0f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
            GLES30.glUniform3f(shader.uTintColor, v.colorR, v.colorG, v.colorB)
            GLES30.glUniform1f(shader.uMetallic, 0.58f)
            GLES30.glUniform1f(shader.uRoughness, 0.28f)
            GLES30.glUniform1f(shader.uEmissive, if (v.brakeLightsOn) 0.14f else 0.04f)

            if (v.type == TrafficVehicleType.VAN) {
                world.trafficVanMesh.draw()
            } else {
                world.trafficSedanMesh.draw()
            }

            // Distance-LOD wheels: render 4 wheels only within quality LOD threshold (zero heap allocations!)
            if (dist < lodDetailDist) {
                GLES30.glUniform3f(shader.uTintColor, 0.2f, 0.22f, 0.26f)
                GLES30.glUniform1f(shader.uMetallic, 0.3f)
                GLES30.glUniform1f(shader.uEmissive, 0f)
                val wxOff = v.type.width * 0.48f
                val wzOff = v.type.length * 0.31f
                for (cIdx in 0 until 4) {
                    System.arraycopy(modelMatrix, 0, wheelMatrix, 0, 16)
                    Matrix.translateM(wheelMatrix, 0, wheelSignX[cIdx] * wxOff, 0.36f, wheelSignZ[cIdx] * wzOff)
                    Matrix.rotateM(wheelMatrix, 0, 90f, 0f, 0f, 1f)
                    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, wheelMatrix, 0)
                    GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, wheelMatrix, 0)
                    GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
                    world.trafficWheelMesh.draw()
                }
            }
        }
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uEmissive, 0f)
    }

    private fun renderAiRacers(
        shader: LitShaderProgram,
        unlit: UnlitOverlayShader,
        tex: ProceduralTextures,
        shadowEnabled: Boolean,
        lodDetailDist: Float
    ) {
        for (r in aiRacerSystem.racers) {
            if (!r.active) continue
            val dist = hypot(r.x - physics.camX, r.z - physics.camZ)
            val car = getOrBuildCarMesh(r.carId)
            val headingDeg = Math.toDegrees(r.headingRad.toDouble()).toFloat()

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, r.x, r.y, r.z)
            Matrix.rotateM(modelMatrix, 0, headingDeg, 0f, 1f, 0f)

            if (shadowEnabled && dist < lodDetailDist * 1.4f) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
                unlit.use()
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(unlit.uMvpMatrix, 1, false, mvpMatrix, 0)
                GLES30.glUniform4f(unlit.uColor, 0f, 0f, 0f, 0.65f)
                car.shadowMesh.draw()
                GLES30.glDepthMask(true)
                GLES30.glDisable(GLES30.GL_BLEND)
            }

            shader.use()
            GLES30.glUniform1f(shader.uDamageFactor, 0f)
            GLES30.glUniform1i(shader.uUseTexture, 0)
            GLES30.glUniform1f(shader.uWetRoadSpec, 0f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)

            val rgb = parseHexRgb(r.paintHex)
            GLES30.glUniform3f(shader.uTintColor, rgb[0], rgb[1], rgb[2])
            GLES30.glUniform1f(shader.uMetallic, 0.86f)
            GLES30.glUniform1f(shader.uRoughness, 0.16f)
            GLES30.glUniform1f(shader.uEmissive, 0.05f)
            car.bodyMesh.draw()

            GLES30.glUniform3f(shader.uTintColor, 0.14f, 0.18f, 0.25f)
            GLES30.glUniform1f(shader.uMetallic, 0.72f)
            GLES30.glUniform1f(shader.uRoughness, 0.12f)
            GLES30.glUniform1f(shader.uEmissive, 0f)
            car.windowsMesh.draw()

            // LOD optimization: draw detailed trim, bodykit, spoiler, and spinning wheels within LOD range
            if (dist < lodDetailDist * 1.45f) {
                GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
                GLES30.glUniform1f(shader.uMetallic, 0.65f)
                GLES30.glUniform1f(shader.uRoughness, 0.22f)
                car.trimMesh.draw()
                car.bodyKitMeshes[BodyKitStyle.TRACK_CARBON]?.draw()
                car.spoilerMeshes[SpoilerStyle.GT_WING]?.draw()
            }

            GLES30.glUniform1f(shader.uEmissive, 2.0f)
            car.headlightsMesh.draw()
            GLES30.glUniform1f(shader.uEmissive, if (r.brakeLightsOn) 3.2f else 1.4f)
            car.taillightsMesh.draw()
            GLES30.glUniform1f(shader.uEmissive, 0f)

            if (dist < lodDetailDist * 1.3f) {
                val steerDeg = Math.toDegrees(r.steerVisualRad.toDouble()).toFloat()
                val spinDeg = Math.toDegrees(r.wheelSpinRad.toDouble()).toFloat()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.wheelTex)
                GLES30.glUniform3f(shader.uTintColor, r.rimStyle.r, r.rimStyle.g, r.rimStyle.b)
                for (mount in car.wheelMounts) {
                    System.arraycopy(modelMatrix, 0, wheelMatrix, 0, 16)
                    Matrix.translateM(wheelMatrix, 0, mount.x, mount.y, mount.z)
                    if (mount.front) Matrix.rotateM(wheelMatrix, 0, steerDeg, 0f, 1f, 0f)
                    Matrix.rotateM(wheelMatrix, 0, spinDeg, 1f, 0f, 0f)
                    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, wheelMatrix, 0)
                    GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, wheelMatrix, 0)
                    GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
                    car.wheelMesh.draw()
                }
            }

            if (r.nitroActive) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
                GLES30.glDepthMask(false)
                unlit.use()
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(unlit.uMvpMatrix, 1, false, mvpMatrix, 0)
                GLES30.glUniform4f(unlit.uColor, 0.25f, 0.90f, 1.0f, 0.85f)
                car.nitroFlameMesh.draw()
                GLES30.glDepthMask(true)
                GLES30.glDisable(GLES30.GL_BLEND)
            }
        }
    }

    private fun renderEventGantries(
        shader: LitShaderProgram,
        unlit: UnlitOverlayShader,
        world: CityWorld,
        event: CareerEventSpec
    ) {
        // 1. 3D Start Line Gantry positioned right over the starting grid
        shader.use()
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uMetallic, 0.4f)
        GLES30.glUniform1f(shader.uRoughness, 0.35f)
        GLES30.glUniform1f(shader.uEmissive, if (physics.isCountdownActive) 1.5f else 0.45f)

        val startYawDeg = Math.toDegrees(event.spawnHeadingRad.toDouble()).toFloat()
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, event.spawnX, event.spawnY, event.spawnZ)
        Matrix.rotateM(modelMatrix, 0, startYawDeg, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
        world.startLineGantryMesh.draw()

        // 2. Checkpoints & 3D Checkered Finish Line Gantry on Final Gate
        if (event.checkpoints.isNotEmpty()) {
            val finCp = event.checkpoints.last()
            val prevCp = if (event.checkpoints.size >= 2) event.checkpoints[event.checkpoints.size - 2] else null
            val finYawDeg = if (prevCp != null) {
                Math.toDegrees(atan2(-(finCp.x - prevCp.x).toDouble(), -(finCp.z - prevCp.z).toDouble())).toFloat()
            } else {
                startYawDeg
            }
            GLES30.glUniform1f(shader.uEmissive, 0.85f)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, finCp.x, finCp.y, finCp.z)
            Matrix.rotateM(modelMatrix, 0, finYawDeg, 0f, 1f, 0f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
            world.finishLineGantryMesh.draw()
            GLES30.glUniform1f(shader.uEmissive, 0f)

            if (physics.checkpointIndex < event.checkpoints.size) {
                val cp = event.checkpoints[physics.checkpointIndex]
                val isFinalGate = physics.checkpointIndex == event.checkpoints.lastIndex
                renderCheckpointBeacon(unlit, world.checkpointRingMesh, cp.x, cp.y, cp.z, isFinalGate)
            }
        }
    }

    private fun renderPlayerCar(
        shader: LitShaderProgram,
        unlit: UnlitOverlayShader,
        tex: ProceduralTextures,
        car: CarMeshes,
        build: ActiveCarBuild,
        shadowEnabled: Boolean
    ) {
        val headingDeg = Math.toDegrees(physics.headingRad.toDouble()).toFloat()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, physics.x, physics.y, physics.z)
        Matrix.rotateM(modelMatrix, 0, headingDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, physics.groundSlopeDeg, 1f, 0f, 0f)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        unlit.use()
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(unlit.uMvpMatrix, 1, false, mvpMatrix, 0)
        if (shadowEnabled) {
            GLES30.glUniform4f(unlit.uColor, 0.0f, 0.0f, 0.0f, 0.72f)
            car.shadowMesh.draw()
        }
        if (build.underglowHex != "NONE") {
            val glowRgb = parseHexRgb(build.underglowHex)
            val pulse = (0.24f + 0.20f * environment.nightFactor) + 0.08f * sin(animTimeSec * 4.5f)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            GLES30.glUniform4f(unlit.uColor, glowRgb[0], glowRgb[1], glowRgb[2], pulse)
            car.underglowMesh.draw()
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        shader.use()
        GLES30.glUniform1f(shader.uHeadlightPower, 0.0f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0f)

        System.arraycopy(modelMatrix, 0, chassisMatrix, 0, 16)
        Matrix.rotateM(chassisMatrix, 0, physics.bodyPitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(chassisMatrix, 0, physics.bodyRollDeg, 0f, 0f, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, chassisMatrix, 0)

        GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, chassisMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES30.glUniform1i(shader.uUseTexture, 0)

        // Progressive chassis damage wear (scuffs, carbon soot, clearcoat dulling) — pristine in Garage showroom
        val damageFactor = if (physics.screenMode == GameScreen.GARAGE) {
            0f
        } else {
            (1f - physics.healthNormalized).coerceIn(0f, 1f)
        }

        // 1. Metallic Car Paint Body (with live progressive wear shader)
        val paintRgb = parseHexRgb(build.paintHex)
        GLES30.glUniform1f(shader.uDamageFactor, damageFactor)
        GLES30.glUniform3f(shader.uTintColor, paintRgb[0], paintRgb[1], paintRgb[2])
        GLES30.glUniform1f(shader.uMetallic, 0.88f)
        GLES30.glUniform1f(shader.uRoughness, 0.14f)
        GLES30.glUniform1f(shader.uEmissive, 0.04f)
        car.bodyMesh.draw()

        // 2. Live Customized Window Tint Greenhouse Glass
        val tint = build.windowTint
        GLES30.glUniform1f(shader.uDamageFactor, 0f)
        GLES30.glUniform3f(shader.uTintColor, tint.r, tint.g, tint.b)
        GLES30.glUniform1f(shader.uMetallic, tint.metallic)
        GLES30.glUniform1f(shader.uRoughness, tint.roughness)
        GLES30.glUniform1f(shader.uEmissive, if (tint == com.example.data.WindowTint.MIRROR_CYAN) 0.10f else 0.0f)
        car.windowsMesh.draw()

        // 3. Carbon Trim + Selected 3D Body Kit + Selected 3D Spoiler
        GLES30.glUniform1f(shader.uDamageFactor, damageFactor * 0.85f)
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uMetallic, 0.65f)
        GLES30.glUniform1f(shader.uRoughness, 0.22f)
        GLES30.glUniform1f(shader.uEmissive, 0.0f)
        car.trimMesh.draw()
        car.bodyKitMeshes[build.bodyKitStyle]?.draw()
        car.spoilerMeshes[build.spoilerStyle]?.draw()

        // 4. Live Customized Emissive Headlights (flickers after severe frontal collision)
        GLES30.glUniform1f(shader.uDamageFactor, 0f)
        val hl = build.headlightStyle
        val frontFlicker = if (physics.screenMode == GameScreen.RACING && physics.frontDamage > 0.50f) {
            if (sin(animTimeSec * 37f) > -0.35f) 0.55f else 0.12f
        } else {
            1.0f
        }
        GLES30.glUniform3f(shader.uTintColor, hl.r, hl.g, hl.b)
        val baseHeadlightEmissive = if (physics.screenMode == GameScreen.GARAGE) 2.2f else (1.4f + 1.1f * environment.nightFactor)
        GLES30.glUniform1f(shader.uEmissive, baseHeadlightEmissive * frontFlicker)
        car.headlightsMesh.draw()

        // 5. Emissive Crimson Taillights
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uEmissive, if (physics.brakeLightsOn) 3.4f else 1.4f)
        car.taillightsMesh.draw()
        GLES30.glUniform1f(shader.uEmissive, 0f)

        val steerDeg = Math.toDegrees(physics.steerVisualRad.toDouble()).toFloat()
        val rim = build.rimStyle

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.wheelTex)
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform3f(shader.uTintColor, rim.r, rim.g, rim.b)
        GLES30.glUniform1f(shader.uMetallic, 0.72f)
        GLES30.glUniform1f(shader.uRoughness, 0.28f)

        car.wheelMounts.forEachIndexed { idx, mount ->
            val wheelState = physics.wheels[idx.coerceIn(0, 3)]
            val spinDeg = Math.toDegrees(wheelState.spinRad.toDouble()).toFloat()
            System.arraycopy(modelMatrix, 0, wheelMatrix, 0, 16)
            Matrix.translateM(wheelMatrix, 0, mount.x, mount.y + wheelState.visualOffsetY, mount.z)
            if (mount.front) {
                Matrix.rotateM(wheelMatrix, 0, steerDeg, 0f, 1f, 0f)
            }
            Matrix.rotateM(wheelMatrix, 0, spinDeg, 1f, 0f, 0f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, wheelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, wheelMatrix, 0)
            GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
            car.wheelMesh.draw()
        }

        if (physics.nitroFiring) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            GLES30.glDepthMask(false)
            unlit.use()
            val flicker = 0.85f + 0.35f * sin(animTimeSec * 42f)
            System.arraycopy(chassisMatrix, 0, wheelMatrix, 0, 16)
            Matrix.scaleM(wheelMatrix, 0, 1f, 1f, flicker)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, wheelMatrix, 0)
            GLES30.glUniformMatrix4fv(unlit.uMvpMatrix, 1, false, mvpMatrix, 0)
            GLES30.glUniform4f(unlit.uColor, 0.2f, 0.92f, 1.0f, 0.92f)
            car.nitroFlameMesh.draw()
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private fun renderCheckpointBeacon(
        unlit: UnlitOverlayShader,
        mesh: GlMesh,
        x: Float,
        y: Float,
        z: Float,
        isFinishGate: Boolean
    ) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        unlit.use()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, animTimeSec * 45f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(unlit.uMvpMatrix, 1, false, mvpMatrix, 0)
        val alpha = 0.55f + 0.25f * sin(animTimeSec * 5f)
        if (isFinishGate) {
            GLES30.glUniform4f(unlit.uColor, 1.0f, 0.84f, 0.18f, alpha)
        } else {
            GLES30.glUniform4f(unlit.uColor, 1.0f, 0.92f, 0.95f, alpha)
        }
        mesh.draw()

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun renderGarageShowroomScene(
        shader: LitShaderProgram,
        unlit: UnlitOverlayShader,
        tex: ProceduralTextures,
        carBuild: ActiveCarBuild
    ) {
        val stage = showroomStage ?: return
        val carMeshes = getOrBuildCarMesh(carBuild.spec.id)

        shader.use()
        GLES30.glUniform1f(shader.uDamageFactor, 0f)
        GLES30.glUniform3f(shader.uCameraPos, physics.camX, physics.camY, physics.camZ)
        GLES30.glUniform3f(shader.uFogColor, 0.03f, 0.035f, 0.055f)
        GLES30.glUniform1f(shader.uFogDensity, 0.014f)
        // Bright overhead studio key light + cool showroom fill light
        GLES30.glUniform3f(shader.uSunDir, 0.35f, 0.88f, 0.32f)
        GLES30.glUniform3f(shader.uSunColor, 0.96f, 0.98f, 1.0f)
        GLES30.glUniform1f(shader.uSunIntensity, 1.25f)
        GLES30.glUniform3f(shader.uSkyAmb, 0.24f, 0.28f, 0.36f)
        GLES30.glUniform3f(shader.uGroundAmb, 0.14f, 0.10f, 0.14f)
        GLES30.glUniform1f(shader.uNightFactor, 0.72f)
        GLES30.glUniform1f(shader.uReflectionStrength, 1.15f)
        GLES30.glUniform1f(shader.uHeadlightPower, 0.0f)

        // Draw Showroom Floor, Walls & Lighting Gantry
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uModelMatrix, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES30.glUniform1i(shader.uUseTexture, 0)
        GLES30.glUniform3f(shader.uTintColor, 1f, 1f, 1f)
        GLES30.glUniform1f(shader.uMetallic, 0.52f)
        GLES30.glUniform1f(shader.uRoughness, 0.24f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0.55f)
        GLES30.glUniform1f(shader.uEmissive, 0f)
        stage.floorAndWallsMesh.draw()

        // Draw Metallic Turntable Pedestal
        GLES30.glUniform1f(shader.uMetallic, 0.78f)
        GLES30.glUniform1f(shader.uRoughness, 0.16f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0.25f)
        stage.turntableMesh.draw()

        // Draw Pulsing Crimson & Cyan Neon Stage Rings + Overhead Studio Light Bars
        val crimsonPulse = 1.6f + 0.35f * sin(animTimeSec * 3.0f)
        GLES30.glUniform1f(shader.uWetRoadSpec, 0f)
        GLES30.glUniform1f(shader.uEmissive, crimsonPulse)
        stage.neonRingCrimsonMesh.draw()

        val cyanPulse = 1.4f + 0.30f * cos(animTimeSec * 2.4f)
        GLES30.glUniform1f(shader.uEmissive, cyanPulse)
        stage.neonRingCyanMesh.draw()

        GLES30.glUniform1f(shader.uEmissive, 1.8f)
        stage.studioLightsMesh.draw()
        GLES30.glUniform1f(shader.uEmissive, 0f)

        // Draw Active Previewed 3D Vehicle Centered on the Turntable
        renderPlayerCar(shader, unlit, tex, carMeshes, carBuild, shadowEnabled = true)
    }

    private fun parseHexRgb(hex: String): FloatArray {
        return try {
            val c = Color.parseColor(hex)
            rgbScratch[0] = Color.red(c) / 255f
            rgbScratch[1] = Color.green(c) / 255f
            rgbScratch[2] = Color.blue(c) / 255f
            rgbScratch
        } catch (_: Exception) {
            rgbScratch[0] = 0.90f
            rgbScratch[1] = 0.06f
            rgbScratch[2] = 0.10f
            rgbScratch
        }
    }
}

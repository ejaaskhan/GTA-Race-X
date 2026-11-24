package com.example

import com.example.data.ActiveCarBuild
import com.example.data.CameraMode
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.MedalTier
import com.example.data.RaceResult
import com.example.data.RimStyle
import com.example.data.TimeOfDayPreset
import com.example.data.WeatherPreset
import com.example.engine.AiRacerSystem
import com.example.engine.CityWorld
import com.example.engine.ControlInput
import com.example.engine.DrivingVfxListener
import com.example.engine.EnvironmentSystem
import com.example.engine.TrafficSignalState
import com.example.engine.TrafficSystem
import com.example.engine.TrafficVehicleType
import com.example.engine.VehiclePhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class OpenWorldSystemsTest {

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
            brakesLevel = 1
        )
    }

    @Test
    fun `city world maps all 7 required districts accurately`() {
        assertEquals("DOWNTOWN", CityWorld.getDistrictName(0f, 0f))
        assertEquals("RESIDENTIAL DISTRICT", CityWorld.getDistrictName(-260f, 260f))
        assertEquals("INDUSTRIAL DISTRICT", CityWorld.getDistrictName(240f, 240f))
        assertEquals("HIGHWAY", CityWorld.getDistrictName(CityWorld.SKYWAY_X, 0f))
        assertEquals("BRIDGE", CityWorld.getDistrictName(0f, CityWorld.BRIDGE_Z))
        assertEquals("TUNNEL", CityWorld.getDistrictName(CityWorld.TUNNEL_X, 0f))
        assertEquals("RACING DISTRICT", CityWorld.getDistrictName(-260f, -260f))
    }

    @Test
    fun `traffic system moves diverse vehicles along roads, cycles traffic signals, and collides with player`() {
        val traffic = TrafficSystem()
        val player = VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.FREE_ROAM, null)
        }

        // Verify vehicle speed variety across archetypes
        assertTrue(TrafficVehicleType.COUPE.baseSpeedMs > TrafficVehicleType.SEDAN.baseSpeedMs)
        assertTrue(TrafficVehicleType.SEDAN.baseSpeedMs > TrafficVehicleType.VAN.baseSpeedMs)

        // Verify traffic signal phases cycle NS_GREEN -> NS_YELLOW -> EW_GREEN -> EW_YELLOW
        val seenStates = mutableSetOf<TrafficSignalState>()
        val initialPositions = traffic.vehicles.take(14).map { it.x to it.z }

        repeat(1400) { // ~23.3 seconds covers a full 22s signal cycle
            traffic.step(1f / 60f, activeCount = 14, playerPhysics = player, vfxListener = null)
            seenStates.add(traffic.signalState)
        }
        assertEquals(
            "Traffic signals must cycle through all 4 phases",
            TrafficSignalState.entries.toSet(),
            seenStates
        )

        val movedCount = traffic.vehicles.take(14).indices.count { i ->
            val (ox, oz) = initialPositions[i]
            hypot(traffic.vehicles[i].x - ox, traffic.vehicles[i].z - oz) > 10f
        }
        assertTrue("AI traffic vehicles must actively travel along city roads ($movedCount/14 moved)", movedCount >= 10)

        // Verify traffic-to-player rigid-body collision triggers knockback, shake & sparks
        var sparkCount = 0
        val vfx = object : DrivingVfxListener {
            override fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {}
            override fun onSkidMark(prevX: Float, prevY: Float, prevZ: Float, currX: Float, currY: Float, currZ: Float, rightX: Float, rightZ: Float) {}
            override fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
                sparkCount++
            }
        }
        val targetCar = traffic.vehicles[0]
        targetCar.active = true
        targetCar.axisIsNS = true
        targetCar.avenueCoord = 0f
        targetCar.directionSign = -1
        targetCar.x = 4.6f
        targetCar.z = 176f
        targetCar.y = 0f
        targetCar.vx = 0f
        targetCar.vz = 0f
        targetCar.speedMs = 0f
        targetCar.knockbackTimer = 0f

        player.x = 4.6f
        player.z = 178.1f
        player.y = 0f
        player.vx = 0f
        player.vz = -24f
        player.speedMs = 24f

        traffic.step(1f / 60f, activeCount = 14, playerPhysics = player, vfxListener = vfx)

        assertTrue("Collision with traffic must trigger knockback timer on traffic car", targetCar.knockbackTimer > 0f)
        assertTrue("Collision with traffic must apply camera shake on player", player.impactShakeIntensity > 0.2f)
        assertTrue("Collision with traffic must emit spark burst", sparkCount > 0)
    }

    @Test
    fun `ai racers spawn 3 opponents, navigate checkpoints, overtake traffic, and rank player position`() {
        val streetEvent = GameCatalog.defaultQuickEvent(GameMode.STREET_RACE)!!
        val player = VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.STREET_RACE, streetEvent)
        }
        val ai = AiRacerSystem()
        ai.setupForSession(GameMode.STREET_RACE, streetEvent)
        val traffic = TrafficSystem()

        assertTrue("Street race must activate AI racer pack", ai.activeRace)
        assertEquals("Street race must have 4 total participants (Player + 3 AI)", 4, ai.totalParticipants())
        assertTrue("All 3 AI opponents must be active", ai.racers.all { it.active })

        // During countdown, AI rivals must hold on the starting grid
        ai.step(1f / 60f, countdownActive = true, elapsedMs = 0L, playerPhysics = player, trafficSystem = traffic, roadWetness = 0f, vfxListener = null)
        assertTrue("AI opponents must stay stationary during 3-2-1 countdown", ai.racers.all { it.speedMs == 0f })

        // Place a slow traffic car directly ahead of AI Racer 0 to verify lateral overtaking
        val r0 = ai.racers[0]
        val blocker = traffic.vehicles[0]
        blocker.active = true
        blocker.x = r0.x
        blocker.z = r0.z - 16f
        blocker.y = r0.y
        blocker.speedMs = 6f

        // Simulate 8 seconds of live racing after GO!
        var observedOvertakeShift = false
        repeat(480) { tick ->
            ai.step(
                dt = 1f / 60f,
                countdownActive = false,
                elapsedMs = (tick * 16L),
                playerPhysics = player,
                trafficSystem = traffic,
                roadWetness = 0f,
                vfxListener = null
            )
            if (abs(r0.overtakeOffset) > 1.5f) observedOvertakeShift = true
        }

        assertTrue("AI racers must navigate through checkpoints", ai.racers.any { it.checkpointIndex >= 1 })
        assertTrue("AI racers must shift laterally to overtake blocking vehicles", observedOvertakeShift)
        val pos = ai.computePlayerPosition(player)
        assertTrue("Player standing stationary at start must drop behind advancing AI rivals (got P$pos)", pos == 4)
    }

    @Test
    fun `environment system updates day night lighting, fog density, rain streaks, and lowers wet tire grip`() {
        val env = EnvironmentSystem()

        // Daytime Clear
        repeat(120) { env.update(1f / 30f, TimeOfDayPreset.DAY, WeatherPreset.CLEAR) }
        assertEquals("DAY", env.timeOfDayLabel)
        assertEquals("CLEAR", env.weatherLabel)
        assertTrue("Daytime sky must be bright", env.skyB > 0.65f)
        assertTrue("Daytime nightFactor must be near 0", env.nightFactor < 0.1f)
        val dryGrip = env.tireGripMultiplier
        val clearFog = env.fogDensityMultiplier

        // Midnight Fog
        repeat(120) { env.update(1f / 30f, TimeOfDayPreset.NIGHT, WeatherPreset.FOG) }
        assertEquals("NIGHT", env.timeOfDayLabel)
        assertEquals("FOG", env.weatherLabel)
        assertTrue("Fog must substantially increase fog density multiplier", env.fogDensityMultiplier > clearFog + 1.2f)
        assertTrue("Nighttime nightFactor must be high", env.nightFactor > 0.85f)

        // Heavy Rain lowers wet-asphalt grip for longer drifts
        repeat(150) { env.update(1f / 30f, TimeOfDayPreset.NIGHT, WeatherPreset.RAIN) }
        assertEquals("RAIN", env.weatherLabel)
        assertTrue("Rain intensity must ramp up", env.rainIntensity > 0.8f)
        assertTrue("Rain must soak road surface", env.roadWetness > 0.8f)
        assertTrue("Rain must reduce tire grip for wet drifts (wet=${env.tireGripMultiplier}, dry=$dryGrip)", env.tireGripMultiplier < dryGrip - 0.15f)
    }

    @Test
    fun `race countdown locks launch until GO and all 5 game modes and 10 career missions are valid and completable`() {
        val car = defaultCarBuild()
        val streetEvent = GameCatalog.defaultQuickEvent(GameMode.STREET_RACE)!!
        val physics = VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.STREET_RACE, streetEvent)
        }
        val input = ControlInput().apply { throttle = true }

        // Countdown starts at 3 and holds chassis on the grid while allowing engine revs
        assertTrue(physics.isCountdownActive)
        assertEquals("3", physics.countdownLabel)
        val startZ = physics.z

        repeat(60) { // 1 second into countdown
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }
        assertEquals("Chassis must remain locked on starting line during countdown", startZ, physics.z, 0.01f)
        assertTrue("Engine must rev while holding throttle during countdown", physics.engineRpm > 3000f)

        // Advance past remaining 2 seconds -> GO! flash -> car launches forward (~0.67s under throttle)
        repeat(160) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }
        assertFalse("Countdown must expire after 3 seconds", physics.isCountdownActive)
        assertTrue("Car must launch forward after GO!", physics.z < startZ - 1f)

        // Verify finish line completion awards Gold medal and credits for P1 in Street Racing
        var result: RaceResult? = null
        physics.checkpointIndex = streetEvent.checkpoints.lastIndex
        val fin = streetEvent.checkpoints.last()
        physics.x = fin.x
        physics.y = fin.y
        physics.z = fin.z
        physics.step(
            dt = 1f / 60f,
            input = input,
            carBuild = car,
            steeringSensitivity = 1.0f,
            cameraMode = CameraMode.CHASE,
            colliders = emptyList(),
            currentRacePosition = 1,
            totalRacers = 4,
            onRaceFinished = { result = it }
        )
        assertNotNull("Crossing final finish line must emit RaceResult", result)
        val finalResult = requireNotNull(result)
        assertEquals(MedalTier.GOLD, finalResult.medal)
        assertEquals(1, finalResult.finishPosition)
        assertTrue(finalResult.creditsEarned > 0L)

        // Verify all 5 Game Modes exist and every Career mission has Firestore-safe IDs & valid setup
        assertEquals(5, GameMode.entries.size)
        assertTrue("Career must contain at least 10 playable missions", GameCatalog.careerEvents.size >= 10)
        val idRegex = Regex("^[a-zA-Z0-9_\\-]+$")
        for (ev in GameCatalog.careerEvents) {
            assertTrue("Event ID '${ev.id}' must satisfy Firestore rules", idRegex.matches(ev.id) && ev.id.length <= 64)
            assertTrue("Event '${ev.id}' must award positive credits", ev.rewardCredits > 0L)
            if (ev.mode != GameMode.DRIFT_CHALLENGE) {
                assertTrue("Non-drift mission '${ev.id}' must define checkpoints", ev.checkpoints.size >= 5)
            }
        }
    }
}

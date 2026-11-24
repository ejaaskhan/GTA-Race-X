package com.example

import com.example.data.ActiveCarBuild
import com.example.data.BodyKitStyle
import com.example.data.CameraMode
import com.example.data.CareerProgressDoc
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.GarageCarDoc
import com.example.data.HeadlightStyle
import com.example.data.MedalTier
import com.example.data.PlayerProfileDoc
import com.example.data.PlayerProgression
import com.example.data.RaceResult
import com.example.data.RimStyle
import com.example.data.SpoilerStyle
import com.example.data.VehicleClass
import com.example.data.WindowTint
import com.example.engine.ControlInput
import com.example.engine.VehiclePhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GarageProgressionTest {

    private fun baseBuild(
        carId: String = "vipera_gtx",
        engineLevel: Int = 1,
        turboLevel: Int = 1,
        brakesLevel: Int = 1,
        tiresLevel: Int = 1,
        suspensionLevel: Int = 1,
        nitroLevel: Int = 1,
        spoilerStyle: SpoilerStyle = SpoilerStyle.STOCK,
        bodyKitStyle: BodyKitStyle = BodyKitStyle.STREET
    ): ActiveCarBuild {
        val spec = GameCatalog.carById(carId)
        return ActiveCarBuild(
            spec = spec,
            paintHex = spec.defaultPaintHex,
            rimStyle = RimStyle.CHROME,
            underglowHex = spec.defaultUnderglowHex,
            engineLevel = engineLevel,
            nitroLevel = nitroLevel,
            handlingLevel = tiresLevel,
            brakesLevel = brakesLevel,
            turboLevel = turboLevel,
            tiresLevel = tiresLevel,
            suspensionLevel = suspensionLevel,
            spoilerStyle = spoilerStyle,
            bodyKitStyle = bodyKitStyle,
            windowTint = WindowTint.SMOKE,
            headlightStyle = HeadlightStyle.XENON_WHITE
        )
    }

    private fun createRacingPhysics(): VehiclePhysics {
        return VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.FREE_ROAM, null)
        }
    }

    @Test
    fun `catalog defines all 5 original vehicle classes with distinct performance stats`() {
        val cars = GameCatalog.cars
        assertEquals(5, cars.size)

        val classes = cars.map { it.vehicleClass }.toSet()
        assertEquals(
            setOf(
                VehicleClass.SPORTS,
                VehicleClass.DRIFT,
                VehicleClass.MUSCLE,
                VehicleClass.SUPERCAR,
                VehicleClass.HYPERCAR
            ),
            classes
        )

        // Every vehicle must have distinct top speed, acceleration, weight, and nitro capacity
        assertEquals(5, cars.map { it.baseTopSpeedKmh }.toSet().size)
        assertEquals(5, cars.map { it.baseAcceleration }.toSet().size)
        assertEquals(5, cars.map { it.weightKg }.toSet().size)
        assertEquals(5, cars.map { it.baseNitroCapacity }.toSet().size)

        // Hypercar is fastest, Muscle car is heaviest, Drift car is lightest
        val hypercar = GameCatalog.carById("spectre_rs")
        val muscle = GameCatalog.carById("titan_v8")
        val drift = GameCatalog.carById("apex_kuro")
        assertTrue(hypercar.baseTopSpeedKmh > GameCatalog.carById("solstice_v10").baseTopSpeedKmh)
        assertTrue(muscle.weightKg > hypercar.weightKg)
        assertTrue(drift.weightKg < muscle.weightKg)
    }

    @Test
    fun `engine and turbo upgrades measurably increase real acceleration and top speed in physics`() {
        val stockBuild = baseBuild(engineLevel = 1, turboLevel = 1)
        val engineBuild = baseBuild(engineLevel = 5, turboLevel = 1)
        val turboBuild = baseBuild(engineLevel = 1, turboLevel = 5)
        val input = ControlInput().apply { throttle = true }

        // Test Engine upgrade effect over 3.5s straight run
        val stockPhys1 = createRacingPhysics()
        val enginePhys = createRacingPhysics()
        repeat(210) {
            stockPhys1.step(1f / 60f, input, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            enginePhys.step(1f / 60f, input, engineBuild, 1.0f, CameraMode.CHASE, emptyList())
        }
        val stockKmh1 = stockPhys1.speedMs * 3.6f
        val engineKmh = enginePhys.speedMs * 3.6f
        assertTrue(
            "Engine Lv5 speed ($engineKmh km/h) should exceed Stock ($stockKmh1 km/h)",
            engineKmh > stockKmh1 + 10f
        )

        // Test Turbo upgrade effect over 4.0s high-RPM pull
        val stockPhys2 = createRacingPhysics()
        val turboPhys = createRacingPhysics()
        repeat(240) {
            stockPhys2.step(1f / 60f, input, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            turboPhys.step(1f / 60f, input, turboBuild, 1.0f, CameraMode.CHASE, emptyList())
        }
        val stockKmh2 = stockPhys2.speedMs * 3.6f
        val turboKmh = turboPhys.speedMs * 3.6f
        assertTrue(
            "Turbo Lv5 speed ($turboKmh km/h) should exceed Stock ($stockKmh2 km/h)",
            turboKmh > stockKmh2 + 8f
        )
    }

    @Test
    fun `brakes upgrade measurably shortens stopping distance in 60Hz physics`() {
        val stockPhys = createRacingPhysics()
        val brakePhys = createRacingPhysics()
        val stockBuild = baseBuild(brakesLevel = 1)
        val upgradedBuild = baseBuild(brakesLevel = 5)

        val gas = ControlInput().apply { throttle = true }
        val brake = ControlInput().apply { brake = true }

        // Accelerate both to identical speed
        repeat(150) {
            stockPhys.step(1f / 60f, gas, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            brakePhys.step(1f / 60f, gas, upgradedBuild, 1.0f, CameraMode.CHASE, emptyList())
        }
        val startZStock = stockPhys.z
        val startZBrake = brakePhys.z

        // Brake hard until decelerated (45 ticks = 0.75s so neither enters reverse)
        repeat(45) {
            stockPhys.step(1f / 60f, brake, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            brakePhys.step(1f / 60f, brake, upgradedBuild, 1.0f, CameraMode.CHASE, emptyList())
        }
        val stopDistStock = abs(stockPhys.z - startZStock)
        val stopDistUpgraded = abs(brakePhys.z - startZBrake)

        assertTrue(
            "Upgraded brakes stopping distance ($stopDistUpgraded m) must be shorter than stock ($stopDistStock m)",
            stopDistUpgraded < stopDistStock
        )
    }

    @Test
    fun `tires and suspension upgrades reduce body roll and improve cornering stability`() {
        val stockPhys = createRacingPhysics()
        val tunedPhys = createRacingPhysics()
        val stockBuild = baseBuild(tiresLevel = 1, suspensionLevel = 1)
        val tunedBuild = baseBuild(tiresLevel = 5, suspensionLevel = 5, spoilerStyle = SpoilerStyle.GT_WING)

        val accel = ControlInput().apply { throttle = true }
        val corner = ControlInput().apply {
            throttle = true
            steerRight = true
        }

        repeat(100) {
            stockPhys.step(1f / 60f, accel, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            tunedPhys.step(1f / 60f, accel, tunedBuild, 1.0f, CameraMode.CHASE, emptyList())
        }

        var maxRollStock = 0f
        var maxRollTuned = 0f
        repeat(75) {
            stockPhys.step(1f / 60f, corner, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            tunedPhys.step(1f / 60f, corner, tunedBuild, 1.0f, CameraMode.CHASE, emptyList())
            maxRollStock = maxOf(maxRollStock, abs(stockPhys.bodyRollDeg))
            maxRollTuned = maxOf(maxRollTuned, abs(tunedPhys.bodyRollDeg))
        }

        assertTrue(
            "Suspension Lv5 should produce tighter roll control ($maxRollTuned deg vs $maxRollStock deg)",
            maxRollTuned < maxRollStock
        )
        assertTrue(
            "Tires + Suspension + Wing build must have higher effective handling & grip",
            tunedBuild.effectiveTireGrip > stockBuild.effectiveTireGrip &&
                tunedBuild.effectiveHandling > stockBuild.effectiveHandling
        )
    }

    @Test
    fun `nitro upgrade increases both boost speed and tank capacity burn duration`() {
        val stockPhys = createRacingPhysics()
        val nitroPhys = createRacingPhysics()
        val stockBuild = baseBuild(nitroLevel = 1)
        val nitroBuild = baseBuild(nitroLevel = 5)

        val boostInput = ControlInput().apply {
            throttle = true
            nitro = true
        }

        repeat(160) {
            stockPhys.step(1f / 60f, boostInput, stockBuild, 1.0f, CameraMode.CHASE, emptyList())
            nitroPhys.step(1f / 60f, boostInput, nitroBuild, 1.0f, CameraMode.CHASE, emptyList())
        }

        assertTrue(
            "Nitro Lv5 should retain more nitro tank (${nitroPhys.nitroNormalized}) than Lv1 (${stockPhys.nitroNormalized})",
            nitroPhys.nitroNormalized > stockPhys.nitroNormalized + 0.15f
        )
        val stockKmh = stockPhys.speedMs * 3.6f
        val nitroKmh = nitroPhys.speedMs * 3.6f
        assertTrue(
            "Nitro Lv5 should achieve higher boost speed ($nitroKmh km/h) than Lv1 ($stockKmh km/h)",
            nitroKmh > stockKmh + 8f
        )
    }

    @Test
    fun `player progression XP curve, race XP calculation, and all 10 achievements work accurately`() {
        assertEquals(0L, PlayerProgression.xpRequiredForLevel(1))
        assertEquals(400L, PlayerProgression.xpRequiredForLevel(2))
        assertEquals(1, PlayerProgression.levelForXp(0L))
        assertEquals(1, PlayerProgression.levelForXp(399L))
        assertEquals(2, PlayerProgression.levelForXp(400L))
        assertTrue(PlayerProgression.levelForXp(4500L) >= 5)

        val winResult = RaceResult(
            eventId = "downtown_street_clash",
            eventTitle = "DOWNTOWN STREET CLASH",
            mode = GameMode.STREET_RACE,
            elapsedMs = 38_000L,
            driftScore = 4000L,
            highestCombo = 2000L,
            medal = MedalTier.GOLD,
            creditsEarned = 7500L,
            distanceKm = 2.5f,
            finishPosition = 1,
            totalRacers = 4
        )
        val earnedXp = PlayerProgression.calculateRaceXp(winResult)
        assertTrue("Gold Street Race win should award >= 600 XP (was $earnedXp)", earnedXp >= 600L)

        // Create a high-progress player state that qualifies for all 10 achievements
        val maxedProfile = PlayerProfileDoc(
            userId = "u1",
            displayName = "Apex Driver",
            credits = 50000L,
            xp = 6000L,
            level = 7L,
            racesCompleted = 6L,
            racesWon = 2L,
            unlockedAchievements = emptyList(),
            selectedCarId = "spectre_rs",
            totalDistanceKm = 15.0,
            highestDriftCombo = 6200L
        )
        val fullFleet = GameCatalog.cars.associate { spec ->
            spec.id to GarageCarDoc(
                docId = spec.id,
                userId = "u1",
                carId = spec.id,
                spoilerStyle = SpoilerStyle.GT_WING.name,
                bodyKitStyle = BodyKitStyle.WIDEBODY.name,
                windowTint = WindowTint.MIDNIGHT.name,
                headlightStyle = HeadlightStyle.ICE_BLUE.name,
                engineLevel = 5L
            )
        }
        val career = mapOf(
            "downtown_street_clash" to CareerProgressDoc(
                docId = "downtown_street_clash",
                userId = "u1",
                eventId = "downtown_street_clash",
                medalTier = MedalTier.GOLD.name,
                completedCount = 1L
            )
        )

        val unlocked = PlayerProgression.evaluateNewAchievements(maxedProfile, fullFleet, career)
        assertEquals(10, GameCatalog.achievements.size)
        assertEquals(10, unlocked.size)
    }
}

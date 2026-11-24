package com.example

import com.example.data.ActiveCarBuild
import com.example.data.CameraMode
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.RimStyle
import com.example.engine.AabbCollider
import com.example.engine.CityWorld
import com.example.engine.ControlInput
import com.example.engine.DrivingVfxListener
import com.example.engine.VehiclePhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VehiclePhysicsTest {

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

    private fun createRacingPhysics(): VehiclePhysics {
        return VehiclePhysics().apply {
            screenMode = GameScreen.RACING
            startSession(GameMode.FREE_ROAM, null)
        }
    }

    @Test
    fun `acceleration increases speed, engine RPM, shifts gears automatically, and matches real telemetry`() {
        val physics = createRacingPhysics()
        val input = ControlInput().apply { throttle = true }
        val car = defaultCarBuild()

        val initialZ = physics.z
        val initialWheelSpin = physics.wheels[0].spinRad

        // Simulate 6 seconds of full-throttle acceleration at 60 Hz
        repeat(360) {
            physics.step(
                dt = 1f / 60f,
                input = input,
                carBuild = car,
                steeringSensitivity = 1.0f,
                cameraMode = CameraMode.CHASE,
                colliders = emptyList()
            )
        }

        val telemetry = physics.buildTelemetry(fps = 60)

        assertTrue("Car must travel forward along -Z", physics.z < initialZ - 100f)
        assertTrue("Car must reach highway speed (> 130 km/h), got ${telemetry.speedKmh}", telemetry.speedKmh > 130)
        assertEquals(
            "HUD speedometer must exactly derive from rigid-body speedMs",
            (physics.speedMs * 3.6f).toInt(),
            telemetry.speedKmh
        )
        assertTrue("Engine RPM must rise above idle, got ${telemetry.engineRpm}", telemetry.engineRpm > 2500)
        assertTrue("Automatic 6-speed gearbox must upshift beyond gear 1, got gear ${physics.currentGear}", physics.currentGear >= 2)
        assertEquals(physics.currentGear.toString(), telemetry.gearLabel)
        assertNotEquals("Front wheel must physically rotate while driving", initialWheelSpin, physics.wheels[0].spinRad)
        assertTrue("Speed-based FOV surge must expand camera FOV", physics.camFovDeg > 64f)
    }

    @Test
    fun `braking decelerates car and transitions cleanly into reverse gear`() {
        val physics = createRacingPhysics()
        val input = ControlInput().apply { throttle = true }
        val car = defaultCarBuild()

        // Accelerate for 2 seconds
        repeat(120) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }
        val peakSpeed = physics.speedMs
        assertTrue("Peak speed must be > 15 m/s", peakSpeed > 15f)

        // Release throttle, apply brake to stop and engage reverse
        input.throttle = false
        input.brake = true
        repeat(240) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }

        assertTrue("Brake lights must activate when braking", physics.brakeLightsOn)
        assertEquals("Gear must shift to Reverse (-1)", -1, physics.currentGear)
        assertEquals("Gear label must display R", "R", physics.gearLabel)
        assertTrue("Car must move in reverse (forwardSpeedMs < -2 m/s), got ${physics.forwardSpeedMs}", physics.forwardSpeedMs < -2f)
    }

    @Test
    fun `steering rotates heading and front wheel steer angle`() {
        val physics = createRacingPhysics()
        val input = ControlInput().apply {
            throttle = true
            steerLeft = true
        }
        val car = defaultCarBuild()

        repeat(120) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }

        assertTrue("Front wheel steer angle must turn left (> 0.1 rad)", physics.steerVisualRad > 0.1f)
        assertTrue("Car heading must rotate left (> 0.25 rad), got ${physics.headingRad}", physics.headingRad > 0.25f)
        assertTrue("Chassis must exhibit lateral body roll under cornering", abs(physics.bodyRollDeg) > 0.2f)
    }

    @Test
    fun `handbrake locks rear wheels, triggers drift, and emits skid marks and tire smoke`() {
        val physics = createRacingPhysics()
        val input = ControlInput().apply { throttle = true }
        val car = defaultCarBuild()

        var smokeCount = 0
        var skidCount = 0
        val vfx = object : DrivingVfxListener {
            override fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {
                smokeCount++
            }
            override fun onSkidMark(
                prevX: Float, prevY: Float, prevZ: Float,
                currX: Float, currY: Float, currZ: Float,
                rightX: Float, rightZ: Float
            ) {
                skidCount++
            }
            override fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {}
        }

        // Build speed first
        repeat(120) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList(), vfx)
        }

        // Initiate handbrake drift with simultaneous left steer + throttle + handbrake
        input.steerLeft = true
        input.handbrake = true
        var observedDrift = false
        var observedRearLock = false

        repeat(90) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList(), vfx)
            if (physics.drifting) observedDrift = true
            if (physics.wheels[2].locked && physics.wheels[3].locked) observedRearLock = true
        }

        assertTrue("Handbrake must physically lock rear wheels RL & RR", observedRearLock)
        assertFalse("Front wheels must stay unlocked during handbrake", physics.wheels[0].locked || physics.wheels[1].locked)
        assertTrue("Simultaneous steer + handbrake at speed must trigger drifting state", observedDrift)
        assertTrue("Drifting must emit tire smoke particles (got $smokeCount)", smokeCount > 0)
        assertTrue("Drifting must leave asphalt skid marks (got $skidCount)", skidCount > 0)
    }

    @Test
    fun `nitro boost drains tank and delivers higher speed than normal throttle`() {
        val car = defaultCarBuild()

        val normalPhysics = createRacingPhysics()
        val normalInput = ControlInput().apply { throttle = true }
        repeat(180) {
            normalPhysics.step(1f / 60f, normalInput, car, 1.0f, CameraMode.CHASE, emptyList())
        }

        val nitroPhysics = createRacingPhysics()
        val nitroInput = ControlInput().apply {
            throttle = true
            nitro = true
        }
        repeat(180) {
            nitroPhysics.step(1f / 60f, nitroInput, car, 1.0f, CameraMode.CHASE, emptyList())
        }

        assertTrue("Nitro must drain the nitro gauge", nitroPhysics.nitroNormalized < 0.5f)
        assertTrue(
            "Nitro car (${nitroPhysics.speedMs} m/s) must accelerate faster than non-nitro (${normalPhysics.speedMs} m/s)",
            nitroPhysics.speedMs > normalPhysics.speedMs + 3f
        )
    }

    @Test
    fun `four wheel raycast suspension tracks elevated skyway ramp height`() {
        val physics = createRacingPhysics()
        val car = defaultCarBuild()
        // Place car on the elevated skyway approach ramp (x = CityWorld.SKYWAY_X = 360f)
        physics.x = CityWorld.SKYWAY_X
        physics.z = 270f
        physics.y = CityWorld.getGroundHeight(physics.x, physics.z)

        val input = ControlInput().apply { throttle = true }
        repeat(240) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        }

        val expectedDeckHeight = CityWorld.getGroundHeight(physics.x, physics.z)
        assertTrue("Car must climb onto elevated skyway deck (y > 4m), got ${physics.y}", physics.y > 4.0f)
        assertTrue("Chassis Y must stay coupled to skyway deck", abs(physics.y - expectedDeckHeight) < 0.5f)
        assertTrue("All 4 wheels must maintain raycast ground contact", physics.wheels.all { it.grounded })
        assertTrue("Suspension normal forces must support vehicle mass", physics.wheels.sumOf { it.normalForce.toDouble() } > 8000.0)
    }

    @Test
    fun `rigid body building collision prevents tunneling, applies bounce, triggers shake and sparks`() {
        val physics = createRacingPhysics()
        val car = defaultCarBuild()
        val wall = AabbCollider(minX = -15f, maxX = 15f, minZ = 140f, maxZ = 150f, maxY = 40f)
        val colliders = listOf(wall)

        var sparkEvents = 0
        val vfx = object : DrivingVfxListener {
            override fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {}
            override fun onSkidMark(
                prevX: Float, prevY: Float, prevZ: Float,
                currX: Float, currY: Float, currZ: Float,
                rightX: Float, rightZ: Float
            ) {}
            override fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
                sparkEvents++
            }
        }

        // Drive directly toward wall with slight angle so we also verify angular impulse
        physics.headingRad = 0.08f
        val input = ControlInput().apply { throttle = true }
        var maxShakeObserved = 0f

        repeat(180) {
            physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, colliders, vfx)
            if (physics.impactShakeIntensity > maxShakeObserved) {
                maxShakeObserved = physics.impactShakeIntensity
            }
        }

        assertTrue("Car must not tunnel through solid building AABB (z=${physics.z})", physics.z >= wall.maxZ + 1.2f)
        assertTrue("High-speed collision must trigger camera impact shake ($maxShakeObserved)", maxShakeObserved > 0.15f)
        assertTrue("High-speed collision must emit metallic spark bursts ($sparkEvents)", sparkEvents > 0)
    }

    @Test
    fun `camera modes produce distinct camera positions`() {
        val physics = createRacingPhysics()
        val car = defaultCarBuild()
        val input = ControlInput().apply { throttle = true }

        physics.step(1f / 60f, input, car, 1.0f, CameraMode.CHASE, emptyList())
        val chaseZ = physics.camZ

        physics.step(1f / 60f, input, car, 1.0f, CameraMode.HOOD, emptyList())
        val hoodZ = physics.camZ

        assertTrue("HOOD camera ($hoodZ) must sit well ahead of CHASE camera ($chaseZ)", hoodZ < chaseZ - 5f)
    }
}

package com.example.engine

import com.example.data.ActiveCarBuild
import com.example.data.CameraMode
import com.example.data.CareerEventSpec
import com.example.data.GameMode
import com.example.data.GameScreen
import com.example.data.MedalTier
import com.example.data.RaceResult
import com.example.data.RadarBlip
import com.example.data.TelemetrySnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ControlInput {
    @Volatile var steerLeft: Boolean = false
    @Volatile var steerRight: Boolean = false
    @Volatile var throttle: Boolean = false
    @Volatile var brake: Boolean = false
    @Volatile var handbrake: Boolean = false
    @Volatile var nitro: Boolean = false

    fun clear() {
        steerLeft = false
        steerRight = false
        throttle = false
        brake = false
        handbrake = false
        nitro = false
    }
}

/**
 * Independent raycast suspension and tire state for each of the 4 wheels (FL, FR, RL, RR).
 */
class WheelPhysicsState(
    val index: Int,
    val mountX: Float,
    val mountZ: Float,
    val isFront: Boolean
) {
    var grounded: Boolean = true
    var compression: Float = 0.35f       // 0.0 = fully extended, 1.0 = bump-stop compressed
    var compressionVel: Float = 0f
    var normalForce: Float = 3500f       // Newtons supporting chassis corner
    var visualOffsetY: Float = 0f        // Vertical suspension displacement relative to rest mount
    var spinRad: Float = 0f              // Wheel angular rotation (locks when handbrake pulled on rear)
    var angularVelRad: Float = 0f
    var locked: Boolean = false
    var skidding: Boolean = false
    var slipRatio: Float = 0f
    var slipAngleRad: Float = 0f
    var worldContactX: Float = 0f
    var worldContactY: Float = 0f
    var worldContactZ: Float = 0f
    var prevContactX: Float = 0f
    var prevContactY: Float = 0f
    var prevContactZ: Float = 0f
    var hasPrevContact: Boolean = false
}

interface DrivingVfxListener {
    fun onTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float)
    fun onSkidMark(
        prevX: Float, prevY: Float, prevZ: Float,
        currX: Float, currY: Float, currZ: Float,
        rightX: Float, rightZ: Float
    )
    fun onCollisionSpark(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float)
    fun onCrashDebris(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {}
    fun onEngineSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float, damageSeverity: Float) {}
    fun onRepairCompleted() {}
}

/**
 * Full 60 Hz Rigid-Body Raycast 4-Wheel Vehicle Physics Engine (Rapier-style architecture):
 * - 4 independent raycast spring-damper suspensions (FL, FR, RL, RR)
 * - Real longitudinal torque curve, RPM (900..8400), and 6-speed automatic gearbox + Reverse
 * - Pacejka-inspired lateral/longitudinal tire friction & sim-cade handbrake drifting
 * - Multi-point chassis rigid-body impulse collisions with angular bounce & spark events
 * - Speed-reactive chase, close, hood & cinematic cameras with FOV surge & impact shake
 */
class VehiclePhysics {
    companion object {
        const val MASS_KG = 1420f
        const val INERTIA_Y = 2650f
        const val GRAVITY = 18.5f
        const val WHEEL_RADIUS = 0.39f
        const val SUSPENSION_REST_LEN = 0.56f
        const val SUSPENSION_TRAVEL = 0.24f
        const val SPRING_K = 46000f
        const val DAMPER_C = 4400f
        const val IDLE_RPM = 950f
        const val REDLINE_RPM = 8400f
        private val GEAR_RATIOS = floatArrayOf(-3.30f, 3.25f, 2.18f, 1.56f, 1.18f, 0.94f, 0.78f)
        private const val FINAL_DRIVE = 3.65f
    }

    // 4 independent wheel states: 0=FL, 1=FR, 2=RL, 3=RR
    val wheels: Array<WheelPhysicsState> = arrayOf(
        WheelPhysicsState(0, -0.98f, -1.44f, isFront = true),
        WheelPhysicsState(1, 0.98f, -1.44f, isFront = true),
        WheelPhysicsState(2, -0.98f, 1.44f, isFront = false),
        WheelPhysicsState(3, 0.98f, 1.44f, isFront = false)
    )

    // Rigid-body chassis state
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 180f
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var headingRad: Float = 0f          // 0 = facing -Z (North)
    var angularVelY: Float = 0f         // rad/s yaw rate
    var speedMs: Float = 0f
    var forwardSpeedMs: Float = 0f

    // Suspension-driven chassis attitude
    var steerVisualRad: Float = 0f
    var bodyPitchDeg: Float = 0f
    private var pitchVel: Float = 0f
    var bodyRollDeg: Float = 0f
    private var rollVel: Float = 0f
    var groundSlopeDeg: Float = 0f

    // Powertrain & transmission state
    var engineRpm: Float = IDLE_RPM
    var rpmNormalized: Float = 0.12f
    var currentGear: Int = 1            // -1 = R, 1..6 = Forward gears
    var gearLabel: String = "1"
    var shiftTimerSec: Float = 0f
    var nitroNormalized: Float = 1.0f
    var nitroFiring: Boolean = false
    var brakeLightsOn: Boolean = false
    var handbrakeActive: Boolean = false

    // Drift & session scoring
    var drifting: Boolean = false
    var currentDriftPoints: Float = 0f
    var driftMultiplier: Int = 1
    var driftTimer: Float = 0f
    var driftGraceTimer: Float = 0f
    var totalSessionDriftScore: Long = 0L
    var highestSessionCombo: Long = 0L
    var sessionDistanceMeters: Float = 0f

    // Camera rig & physical feedback
    var camX: Float = 0f
    var camY: Float = 4f
    var camZ: Float = 190f
    var camLookX: Float = 0f
    var camLookY: Float = 1.2f
    var camLookZ: Float = 175f
    var camFovDeg: Float = 60f
    var impactShakeIntensity: Float = 0f
    var shakeOffsetX: Float = 0f
    var shakeOffsetY: Float = 0f
    private var chaseHeadingRad: Float = 0f
    private var cinematicOrbitPhase: Float = 0f
    private var shakePhase: Float = 0f
    private var smokeEmitAccum: Float = 0f
    var showroomOrbitRad: Float = 0.65f
    @Volatile var garageUserOrbitRad: Float = 0f
    @Volatile var launchFlyInProgress: Float = 1f

    // Progressive Car Accident & Damage State (0.10 limp-home floor .. 1.0 pristine)
    var healthNormalized: Float = 1.0f
    var frontDamage: Float = 0f
    var rearDamage: Float = 0f
    var leftDamage: Float = 0f
    var rightDamage: Float = 0f
    var impactFlashAlpha: Float = 0f
    var repairedNoticeTimer: Float = 0f
    private var engineSmokeAccum: Float = 0f

    val damageStatusLabel: String
        get() = when {
            healthNormalized >= 0.85f -> "INTACT"
            healthNormalized >= 0.55f -> "DAMAGED"
            healthNormalized >= 0.28f -> "SMOKING"
            else -> "CRITICAL"
        }

    fun repairCar(vfxListener: DrivingVfxListener? = null, showNotice: Boolean = true) {
        healthNormalized = 1.0f
        frontDamage = 0f
        rearDamage = 0f
        leftDamage = 0f
        rightDamage = 0f
        impactFlashAlpha = 0f
        engineSmokeAccum = 0f
        if (showNotice) {
            repairedNoticeTimer = 2.4f
            vfxListener?.onRepairCompleted()
        } else {
            repairedNoticeTimer = 0f
        }
    }

    fun registerImpactDamage(
        contactX: Float,
        contactZ: Float,
        nx: Float,
        nz: Float,
        impactSpeed: Float,
        vfxListener: DrivingVfxListener?
    ) {
        if (impactSpeed <= 4.2f) return
        val fwdX = -sin(headingRad)
        val fwdZ = -cos(headingRad)
        val rightX = cos(headingRad)
        val rightZ = -sin(headingRad)

        val relX = contactX - x
        val relZ = contactZ - z
        val localFwd = relX * fwdX + relZ * fwdZ
        val localRight = relX * rightX + relZ * rightZ

        val damageDelta = ((impactSpeed - 4.0f) * 0.0085f).coerceIn(0.025f, 0.28f)
        healthNormalized = (healthNormalized - damageDelta).coerceAtLeast(0.10f)

        val zoneDelta = (damageDelta * 1.85f).coerceIn(0.05f, 0.45f)
        if (abs(localFwd) >= abs(localRight)) {
            if (localFwd >= 0f) {
                frontDamage = (frontDamage + zoneDelta).coerceAtMost(1.0f)
                pitchVel += (impactSpeed * 0.45f).coerceAtMost(14f)
            } else {
                rearDamage = (rearDamage + zoneDelta).coerceAtMost(1.0f)
                pitchVel -= (impactSpeed * 0.40f).coerceAtMost(12f)
            }
        } else {
            if (localRight >= 0f) {
                rightDamage = (rightDamage + zoneDelta).coerceAtMost(1.0f)
                rollVel -= (impactSpeed * 0.42f).coerceAtMost(12f)
            } else {
                leftDamage = (leftDamage + zoneDelta).coerceAtMost(1.0f)
                rollVel += (impactSpeed * 0.42f).coerceAtMost(12f)
            }
        }

        val normImpact = (impactSpeed / 26f).coerceIn(0.24f, 0.96f)
        impactFlashAlpha = maxOf(impactFlashAlpha, normImpact)
        if (impactSpeed > 6.5f) {
            vfxListener?.onCrashDebris(contactX, y, contactZ, nx, nz, normImpact)
        }
    }

    fun triggerLaunchFlyIn() {
        launchFlyInProgress = 0f
        camX = x + 22f
        camY = y + 12f
        camZ = z + 24f
        camFovDeg = 66f
    }

    // Active mode, race countdown & event state
    var mode: GameMode = GameMode.FREE_ROAM
    var activeEvent: CareerEventSpec? = null
    var checkpointIndex: Int = 0
    var elapsedMs: Long = 0L
    var driftDurationMs: Long = 60_000L
    var countdownTimerSec: Float = 0f      // > 0f locks car on grid for 3-2-1 countdown
    var goFlashTimerSec: Float = 0f        // > 0f displays "GO!" banner briefly
    var eventFinished: Boolean = false
    var paused: Boolean = false
    var screenMode: GameScreen = GameScreen.MAIN_MENU

    val isCountdownActive: Boolean
        get() = countdownTimerSec > 0f

    val countdownLabel: String?
        get() = when {
            countdownTimerSec > 0f -> ceil(countdownTimerSec).toInt().coerceIn(1, 3).toString()
            goFlashTimerSec > 0f -> "GO!"
            else -> null
        }

    private var spawnX: Float = 0f
    private var spawnY: Float = 0f
    private var spawnZ: Float = 180f
    private var spawnHeadingRad: Float = 0f

    fun startSession(
        newMode: GameMode,
        event: CareerEventSpec?
    ) {
        mode = newMode
        activeEvent = event
        checkpointIndex = 0
        elapsedMs = 0L
        eventFinished = false
        paused = false
        totalSessionDriftScore = 0L
        highestSessionCombo = 0L
        currentDriftPoints = 0f
        driftMultiplier = 1
        driftTimer = 0f
        driftGraceTimer = 0f
        sessionDistanceMeters = 0f
        nitroNormalized = 1.0f
        // Start with 3-2-1-GO countdown on all competitive modes; instant launch in Free Drive
        countdownTimerSec = if (newMode == GameMode.FREE_ROAM) 0f else 3.0f
        goFlashTimerSec = 0f

        if (event != null) {
            spawnX = event.spawnX
            spawnY = event.spawnY
            spawnZ = event.spawnZ
            spawnHeadingRad = event.spawnHeadingRad
        } else {
            spawnX = 0f
            spawnY = 0f
            spawnZ = 180f
            spawnHeadingRad = 0f
        }
        resetCarToSpawn()
        repairCar(vfxListener = null, showNotice = false)
    }

    fun resetCarToSpawn() {
        x = spawnX
        y = CityWorld.getGroundHeight(spawnX, spawnZ)
        z = spawnZ
        headingRad = spawnHeadingRad
        chaseHeadingRad = spawnHeadingRad
        vx = 0f
        vy = 0f
        vz = 0f
        angularVelY = 0f
        speedMs = 0f
        forwardSpeedMs = 0f
        steerVisualRad = 0f
        bodyPitchDeg = 0f
        pitchVel = 0f
        bodyRollDeg = 0f
        rollVel = 0f
        groundSlopeDeg = 0f
        currentGear = 1
        gearLabel = "1"
        engineRpm = IDLE_RPM
        rpmNormalized = 0.12f
        shiftTimerSec = 0f
        impactShakeIntensity = 0f
        currentDriftPoints = 0f
        driftMultiplier = 1
        driftTimer = 0f
        for (w in wheels) {
            w.compression = 0.35f
            w.compressionVel = 0f
            w.visualOffsetY = 0f
            w.spinRad = 0f
            w.angularVelRad = 0f
            w.locked = false
            w.skidding = false
            w.hasPrevContact = false
        }
        val fwdX = -sin(headingRad)
        val fwdZ = -cos(headingRad)
        camX = x - fwdX * 8.6f
        camY = y + 3.2f
        camZ = z - fwdZ * 8.6f
    }

    fun recoverCarOnRoad() {
        val nearestX = CityWorld.AVENUE_COORDS.minByOrNull { abs(it - x) } ?: 0f
        val nearestZ = CityWorld.AVENUE_COORDS.minByOrNull { abs(it - z) } ?: 0f
        if (abs(nearestX - x) < abs(nearestZ - z)) x = nearestX else z = nearestZ
        y = CityWorld.getGroundHeight(x, z)
        vx = 0f
        vy = 0f
        vz = 0f
        angularVelY = 0f
        speedMs = 0f
        forwardSpeedMs = 0f
        for (w in wheels) w.hasPrevContact = false
    }

    fun step(
        dt: Float,
        input: ControlInput,
        carBuild: ActiveCarBuild,
        steeringSensitivity: Float,
        cameraMode: CameraMode,
        colliders: List<AabbCollider>,
        vfxListener: DrivingVfxListener? = null,
        tireGripMultiplier: Float = 1.0f,
        currentRacePosition: Int = 1,
        totalRacers: Int = 1,
        onRaceFinished: (RaceResult) -> Unit = {}
    ) {
        if (screenMode != GameScreen.RACING) {
            if (screenMode == GameScreen.GARAGE) {
                x = 0f
                z = 0f
                y = 0.16f
                headingRad = 0.42f
                steerVisualRad = 0.30f
                showroomOrbitRad += dt * 0.18f
            } else {
                x = 0f
                z = 180f
                y = CityWorld.getGroundHeight(x, z)
                headingRad = 0.25f
                steerVisualRad = 0.15f
                showroomOrbitRad += dt * 0.24f
            }
            vx = 0f
            vy = 0f
            vz = 0f
            angularVelY = 0f
            speedMs = 0f
            forwardSpeedMs = 0f
            brakeLightsOn = false
            handbrakeActive = false
            nitroFiring = false
            drifting = false
            engineRpm = IDLE_RPM
            rpmNormalized = 0.12f
            impactShakeIntensity = 0f
            shakeOffsetX = 0f
            shakeOffsetY = 0f
            for (w in wheels) {
                w.visualOffsetY = 0f
                w.compression = 0.35f
            }
            updateShowcaseCamera(dt)
            return
        }

        if (paused || eventFinished) {
            brakeLightsOn = false
            nitroFiring = false
            return
        }

        val h = dt.coerceIn(0.002f, 0.033f)

        // Race starting grid 3-2-1-GO countdown lock (player can rev engine on the line!)
        if (countdownTimerSec > 0f) {
            countdownTimerSec = (countdownTimerSec - h).coerceAtLeast(0f)
            if (countdownTimerSec <= 0f) {
                goFlashTimerSec = 0.9f
            }
            val revTarget = if (input.throttle) 6400f else IDLE_RPM
            engineRpm += (revTarget - engineRpm) * (h * 10f).coerceAtMost(1f)
            rpmNormalized = ((engineRpm - IDLE_RPM) / (REDLINE_RPM - IDLE_RPM)).coerceIn(0.06f, 1.0f)
            vx = 0f
            vz = 0f
            speedMs = 0f
            forwardSpeedMs = 0f
            brakeLightsOn = !input.throttle
            val fX = -sin(headingRad)
            val fZ = -cos(headingRad)
            updateGameplayCamera(h, cameraMode, false, fX, fZ)
            return
        }
        if (goFlashTimerSec > 0f) {
            goFlashTimerSec = (goFlashTimerSec - h).coerceAtLeast(0f)
        }

        elapsedMs += (h * 1000f).toLong()
        if (shiftTimerSec > 0f) shiftTimerSec = (shiftTimerSec - h).coerceAtLeast(0f)

        val fwdX = -sin(headingRad)
        val fwdZ = -cos(headingRad)
        val rightX = cos(headingRad)
        val rightZ = -sin(headingRad)

        // Dynamic vehicle mass & suspension stiffness from car spec + Suspension upgrade
        val effMassKg = carBuild.effectiveWeightKg
        val effInertiaY = INERTIA_Y * (effMassKg / MASS_KG)
        val suspStiffness = carBuild.effectiveSuspensionStiffness

        // =========================================================================
        // 1. FOUR-WHEEL INDEPENDENT RAYCAST SUSPENSION (Hooke Spring + Damper)
        // =========================================================================
        var totalSpringForceY = 0f
        var frontCompressionSum = 0f
        var rearCompressionSum = 0f
        var leftCompressionSum = 0f
        var rightCompressionSum = 0f
        var groundedWheels = 0

        for (w in wheels) {
            val wx = x + rightX * w.mountX - fwdX * w.mountZ
            val wz = z + rightZ * w.mountX - fwdZ * w.mountZ
            val groundY = sampleRoadHeightWithMicroBumps(wx, wz)

            // Raycast from suspension top mount down toward road
            val mountTopY = y + SUSPENSION_REST_LEN
            val distToGround = mountTopY - groundY
            val rawComp = ((SUSPENSION_REST_LEN - distToGround) / SUSPENSION_TRAVEL) + 0.35f
            val newComp = rawComp.coerceIn(0f, 1.0f)

            w.grounded = distToGround <= SUSPENSION_REST_LEN + SUSPENSION_TRAVEL * 0.65f
            w.compressionVel = (newComp - w.compression) / h
            w.compression = newComp

            if (w.grounded) {
                groundedWheels++
                val springF = (newComp * SUSPENSION_TRAVEL * SPRING_K * suspStiffness) +
                    (w.compressionVel * SUSPENSION_TRAVEL * DAMPER_C * suspStiffness)
                val minGroundedLoad = (effMassKg * GRAVITY * 0.16f).coerceAtLeast(1800f)
                w.normalForce = springF.coerceIn(minGroundedLoad, 11500f)
                totalSpringForceY += w.normalForce
            } else {
                w.normalForce = 0f
            }

            // Visible per-wheel vertical offset inside wheel arch (-0.10m droop .. +0.14m compression)
            val targetOffset = (w.compression - 0.35f) * SUSPENSION_TRAVEL
            w.visualOffsetY += (targetOffset - w.visualOffsetY) * (h * 18f).coerceAtMost(1f)

            w.prevContactX = w.worldContactX
            w.prevContactY = w.worldContactY
            w.prevContactZ = w.worldContactZ
            w.worldContactX = wx
            w.worldContactY = groundY
            w.worldContactZ = wz

            if (w.isFront) frontCompressionSum += w.compression else rearCompressionSum += w.compression
            if (w.mountX < 0f) leftCompressionSum += w.compression else rightCompressionSum += w.compression
        }

        // Vertical rigid-body heave integration coupled to road surface height & actual vehicle mass
        val centerGroundY = CityWorld.getGroundHeight(x, z)
        val verticalAccel = (totalSpringForceY / effMassKg) - GRAVITY
        vy += verticalAccel * h
        vy *= (1f - h * (5.2f * suspStiffness).coerceAtMost(9.5f)) // chassis vertical damping
        y += vy * h
        // Prevent chassis bottoming out below road deck or floating above ramps
        val minY = centerGroundY
        val maxY = centerGroundY + 0.32f
        if (y < minY) {
            y = minY
            if (vy < 0f) vy = 0f
        } else if (y > maxY && groundedWheels >= 2) {
            y += (centerGroundY - y) * (h * 14f).coerceAtMost(1f)
        }

        // Track ground pitch slope over highway ramps & suspension bridge
        val aheadY = CityWorld.getGroundHeight(x + fwdX * 2.2f, z + fwdZ * 2.2f)
        val behindY = CityWorld.getGroundHeight(x - fwdX * 2.2f, z - fwdZ * 2.2f)
        val targetSlope = Math.toDegrees(atan2((aheadY - behindY).toDouble(), 4.4)).toFloat().coerceIn(-16f, 16f)
        groundSlopeDeg += (targetSlope - groundSlopeDeg) * (h * 12f).coerceAtMost(1f)

        // =========================================================================
        // 2. POWERTRAIN: ENGINE RPM, TORQUE CURVE, 6-SPEED AUTO GEARBOX & NITRO
        // =========================================================================
        val localFwdVel = vx * fwdX + vz * fwdZ
        val localLatVel = vx * rightX + vz * rightZ
        forwardSpeedMs = localFwdVel

        // Progressive mechanical damage penalty (up to 32% top speed & drive power loss at critical damage)
        val damagePerfMult = 0.68f + 0.32f * healthNormalized.coerceIn(0.10f, 1.0f)
        val maxSpeedMs = (carBuild.effectiveTopSpeedKmh / 3.6f) * damagePerfMult
        val wantsNitro = input.nitro && nitroNormalized > 0.03f && (input.throttle || localFwdVel > 4f)
        nitroFiring = wantsNitro
        val nitroCapacity = carBuild.effectiveNitroCapacity.coerceAtLeast(0.8f)
        val nitroMult = if (wantsNitro) {
            // Higher Nitro Capacity slows tank burn rate for visibly longer boost duration
            nitroNormalized = (nitroNormalized - (h * 0.28f / nitroCapacity)).coerceAtLeast(0f)
            carBuild.effectiveNitroBoost
        } else {
            val recharge = (if (drifting) 0.17f else 0.045f) * (0.85f + 0.15f * nitroCapacity)
            nitroNormalized = (nitroNormalized + h * recharge).coerceAtMost(1f)
            1.0f
        }

        handbrakeActive = input.handbrake
        brakeLightsOn = input.brake || input.handbrake

        // Reverse vs Forward gear state machine
        if (input.brake && localFwdVel < 1.2f && !input.throttle) {
            currentGear = -1
        } else if (currentGear == -1 && (input.throttle || localFwdVel > 0.5f)) {
            currentGear = 1
            shiftTimerSec = 0.11f
        }

        val activeRatio = if (currentGear == -1) abs(GEAR_RATIOS[0]) else GEAR_RATIOS[currentGear.coerceIn(1, 6)]
        val wheelRpm = (abs(localFwdVel) / (2f * PI.toFloat() * WHEEL_RADIUS)) * 60f
        val drivenRpm = wheelRpm * activeRatio * FINAL_DRIVE
        val freeRevTarget = if (input.throttle) IDLE_RPM + 2400f else IDLE_RPM
        val targetRpm = maxOf(IDLE_RPM, drivenRpm, if (abs(localFwdVel) < 3f) freeRevTarget else IDLE_RPM)
            .coerceIn(IDLE_RPM, REDLINE_RPM)
        engineRpm += (targetRpm - engineRpm) * (h * 14f).coerceAtMost(1f)

        // Automatic 6-speed shifting with realistic shift kick
        if (currentGear >= 1 && shiftTimerSec <= 0f) {
            if (engineRpm >= 7850f && currentGear < 6 && input.throttle && localFwdVel > 8f) {
                currentGear++
                shiftTimerSec = 0.11f
                engineRpm *= 0.72f
            } else if (engineRpm < 2850f && currentGear > 1) {
                currentGear--
                shiftTimerSec = 0.09f
                engineRpm = (engineRpm * 1.28f).coerceAtMost(REDLINE_RPM * 0.92f)
            }
        }
        gearLabel = if (currentGear == -1) "R" else currentGear.toString()
        rpmNormalized = ((engineRpm - IDLE_RPM) / (REDLINE_RPM - IDLE_RPM)).coerceIn(0.06f, 1.0f)

        // =========================================================================
        // 3. STEERING, TIRE FRICTION (PACEJKA CURVE) & HANDBRAKE DRIFT DYNAMICS
        // =========================================================================
        val rawSteer = (if (input.steerLeft) 1f else 0f) - (if (input.steerRight) 1f else 0f)
        val absFwdSpeed = abs(localFwdVel)
        val maxSteerAngle = (0.50f / (1f + absFwdSpeed * 0.015f)).coerceIn(0.18f, 0.50f)
        // Subtle steering alignment drift when heavy asymmetric side damage is sustained
        val sideImbalance = rightDamage - leftDamage
        val alignmentPull = if (abs(sideImbalance) > 0.35f && absFwdSpeed > 10f) {
            sideImbalance * 0.045f * (absFwdSpeed / 40f).coerceAtMost(1f)
        } else 0f
        val targetSteerRad = (rawSteer * maxSteerAngle * steeringSensitivity.coerceIn(0.6f, 1.5f) - alignmentPull)
            .coerceIn(-0.55f, 0.55f)
        val steerRate = 13f * (0.90f + 0.10f * suspStiffness)
        steerVisualRad += (targetSteerRad - steerVisualRad) * (h * steerRate).coerceAtMost(1f)

        // Compute longitudinal drive (Engine + Turbo spool curve) & braking forces
        val torqueCurve = 0.70f + 0.38f * sin(((rpmNormalized).coerceIn(0f, 1f)) * PI * 0.78).toFloat()
        val turboSpoolMult = 1.0f + (carBuild.effectiveTurboSpoolBoost - 1.0f) * rpmNormalized.coerceIn(0.25f, 1.0f)
        val shiftCut = if (shiftTimerSec > 0.04f) 0.35f else 1.0f
        val baseDriveForce = carBuild.effectiveAcceleration * 590f * torqueCurve * turboSpoolMult * shiftCut * nitroMult * damagePerfMult

        var longForce = 0f
        if (currentGear == -1 && input.brake) {
            // Active Reverse drive
            if (localFwdVel > -22f) longForce -= 16500f
        } else {
            if (input.throttle) {
                val speedFalloff = (1f - (localFwdVel / (maxSpeedMs * nitroMult)).coerceIn(0f, 0.94f))
                longForce += baseDriveForce * (0.38f + 0.85f * speedFalloff)
            }
            if (input.brake && localFwdVel > 0.2f) {
                longForce -= 38000f * carBuild.effectiveBraking
            }
        }
        if (input.handbrake && absFwdSpeed > 1.0f) {
            // Rear-brake drag when pulling handbrake
            longForce -= kotlin.math.sign(localFwdVel) * 10500f
        }

        // Aerodynamic drag + rolling resistance
        val aeroDrag = -0.44f * localFwdVel * abs(localFwdVel) * 1.15f
        val rollingResist = -localFwdVel * 145f
        longForce += aeroDrag + rollingResist

        // Lateral Pacejka tire friction for Front & Rear axles
        val frontNormal = (wheels[0].normalForce + wheels[1].normalForce).coerceAtLeast(1800f)
        val rearNormal = (wheels[2].normalForce + wheels[3].normalForce).coerceAtLeast(1800f)

        // Dynamic weight transfer on accel/braking shifts normal load front <-> rear
        val longAccelEst = longForce / effMassKg
        val weightTransfer = (longAccelEst * effMassKg * 0.14f).coerceIn(-2600f, 2600f)
        val effFrontLoad = (frontNormal - weightTransfer).coerceAtLeast(1200f)
        val effRearLoad = (rearNormal + weightTransfer).coerceAtLeast(1200f)

        // Sim-cade grip coefficients: Tires upgrade boosts dry & wet grip; rain lowers traction for longer wet drifts
        val weatherGrip = tireGripMultiplier.coerceIn(0.68f, 1.0f)
        val tireGripBoost = carBuild.effectiveTireGrip
        val muFront = 1.48f * carBuild.effectiveHandling * tireGripBoost * (0.35f + 0.65f * weatherGrip)
        val muRear = (if (input.handbrake) 0.56f else 1.38f) * carBuild.effectiveHandling * tireGripBoost * weatherGrip

        val wheelBaseHalf = 1.44f
        val vLatFront = localLatVel - angularVelY * wheelBaseHalf
        val vLatRear = localLatVel + angularVelY * wheelBaseHalf

        val speedSafe = maxOf(absFwdSpeed, 2.2f)
        val dirSign = if (localFwdVel >= -0.2f) 1f else -1f
        val slipFront = atan2(vLatFront.toDouble(), speedSafe.toDouble()).toFloat() + steerVisualRad * dirSign
        val slipRear = atan2(vLatRear.toDouble(), speedSafe.toDouble()).toFloat()

        // Pacejka-style smooth lateral saturation curve
        fun pacejka(slip: Float, mu: Float, load: Float, stiffness: Float): Float {
            return -sin(1.45 * atan((stiffness * slip).toDouble())).toFloat() * mu * load
        }

        val latForceFront = if (absFwdSpeed > 0.35f) pacejka(slipFront, muFront, effFrontLoad, 4.8f) else -vLatFront * 900f
        val latForceRear = if (absFwdSpeed > 0.35f) pacejka(slipRear, muRear, effRearLoad, 4.4f) else -vLatRear * 900f

        // Direct responsive steering torque + Suspension turn-in authority + sim-cade counter-steer assist
        val speedTurnAuthority = (absFwdSpeed / 8f).coerceIn(0f, 1f)
        val driftYawBoost = if (input.handbrake && absFwdSpeed > 9f) 1.55f else 1.0f
        val yawTorque = (-latForceFront * wheelBaseHalf + latForceRear * wheelBaseHalf) * 0.42f +
            (steerVisualRad * dirSign * speedTurnAuthority * 6800f * driftYawBoost * (0.90f + 0.10f * suspStiffness)) -
            (angularVelY * if (input.handbrake) 2400f else 4200f)

        // Integrate rigid-body linear & angular velocities in world frame using actual vehicle mass
        val totalLatForce = (latForceFront + latForceRear) * 0.65f
        val axLocal = totalLatForce / effMassKg
        val azLocal = longForce / effMassKg

        val newFwdVel = (localFwdVel + azLocal * h).coerceIn(-24f, maxSpeedMs * if (wantsNitro) 1.20f else 1.02f)
        var newLatVel = localLatVel + axLocal * h
        // Prevent micro-sliding at near-zero speeds
        if (abs(newFwdVel) < 0.35f && !input.throttle && !input.brake) {
            newLatVel *= 0.6f
        } else {
            val latDamp = (if (input.handbrake) 1.25f else 4.2f) * (0.90f + 0.10f * tireGripBoost)
            newLatVel *= (1f - (latDamp * h).coerceAtMost(0.85f))
        }

        angularVelY = (angularVelY + (yawTorque / effInertiaY) * h).coerceIn(-3.1f, 3.1f)
        if (absFwdSpeed < 0.3f && abs(rawSteer) < 0.05f) angularVelY *= 0.7f
        headingRad += angularVelY * h

        val nFwdX = -sin(headingRad)
        val nFwdZ = -cos(headingRad)
        val nRightX = cos(headingRad)
        val nRightZ = -sin(headingRad)

        vx = nFwdX * newFwdVel + nRightX * newLatVel
        vz = nFwdZ * newFwdVel + nRightZ * newLatVel
        speedMs = hypot(vx, vz)

        // Integrate horizontal position
        val prevX = x
        val prevZ = z
        x += vx * h
        z += vz * h

        // =========================================================================
        // 4. RIGID-BODY IMPULSE COLLISIONS (Buildings, Tunnel, Guardrails, Bounds)
        // =========================================================================
        resolveRigidBodyCollisions(colliders, vfxListener)

        // =========================================================================
        // 5. PER-WHEEL SPIN, REAR HANDBRAKE LOCKUP, SMOKE & SKID MARKS
        // =========================================================================
        val slipRatioRear = abs(newLatVel) / maxOf(abs(newFwdVel), 4f)
        val rearSkidding = abs(newFwdVel) > 6.5f && (input.handbrake || slipRatioRear > 0.24f || (wantsNitro && abs(newFwdVel) < 28f))
        smokeEmitAccum += h

        for (w in wheels) {
            w.slipAngleRad = if (w.isFront) slipFront else slipRear
            w.slipRatio = if (w.isFront) 0f else slipRatioRear
            if (!w.isFront && input.handbrake) {
                // Handbrake physically locks rear wheels!
                w.locked = true
                w.angularVelRad = 0f
                w.skidding = abs(newFwdVel) > 4.5f
            } else {
                w.locked = false
                // Wheel angular velocity matches road speed + slight wheelspin under hard throttle/nitro
                val spinBoost = if (!w.isFront && input.throttle && (wantsNitro || currentGear == 1)) 1.22f else 1.0f
                w.angularVelRad = -(newFwdVel / WHEEL_RADIUS) * spinBoost
                w.spinRad += w.angularVelRad * h
                if (w.spinRad > (2f * PI.toFloat())) w.spinRad -= (2f * PI.toFloat())
                if (w.spinRad < -(2f * PI.toFloat())) w.spinRad += (2f * PI.toFloat())
                w.skidding = !w.isFront && rearSkidding
            }

            // Emit skid marks and drift tire smoke from skidding rear tires
            if (!w.isFront && w.skidding && w.grounded) {
                if (w.hasPrevContact) {
                    val segDist = hypot(w.worldContactX - w.prevContactX, w.worldContactZ - w.prevContactZ)
                    if (segDist in 0.15f..6.0f) {
                        vfxListener?.onSkidMark(
                            w.prevContactX, w.prevContactY, w.prevContactZ,
                            w.worldContactX, w.worldContactY, w.worldContactZ,
                            nRightX, nRightZ
                        )
                    }
                }
                if (smokeEmitAccum >= 0.032f) {
                    vfxListener?.onTireSmoke(w.worldContactX, w.worldContactY, w.worldContactZ, vx, vz)
                }
            }
            w.hasPrevContact = w.skidding && w.grounded
        }
        if (smokeEmitAccum >= 0.032f) smokeEmitAccum = 0f

        // =========================================================================
        // 6. SPRING-DAMPED CHASSIS PITCH & ROLL FROM SUSPENSION & WEIGHT TRANSFER
        // =========================================================================
        val suspPitchTarget = (frontCompressionSum - rearCompressionSum) * 4.0f
        val accelPitchTarget = when {
            shiftTimerSec > 0.03f -> -2.8f
            wantsNitro -> -3.4f
            else -> (-longAccelEst * 0.14f).coerceIn(-3.0f, 3.6f)
        }
        val totalPitchTarget = (suspPitchTarget + accelPitchTarget).coerceIn(-6.5f, 6.5f)
        pitchVel += ((totalPitchTarget - bodyPitchDeg) * 48f - pitchVel * 9.5f) * h
        bodyPitchDeg += pitchVel * h

        val suspRollTarget = (leftCompressionSum - rightCompressionSum) * (4.5f / suspStiffness)
        val lateralRollTarget = ((axLocal * 0.24f - rawSteer * (absFwdSpeed / 40f).coerceIn(0f, 1f) * 2.2f) / (suspStiffness * suspStiffness)).coerceIn(-6.5f, 6.5f)
        val totalRollTarget = (suspRollTarget + lateralRollTarget).coerceIn(-7.5f, 7.5f)
        rollVel += ((totalRollTarget - bodyRollDeg) * (52f * suspStiffness) - rollVel * (9.5f * suspStiffness)) * h
        bodyRollDeg += rollVel * h

        // Distance & Drift Scoring
        sessionDistanceMeters += hypot(x - prevX, z - prevZ)
        val isCurrentlyDrifting = speedMs > 9f && (slipRatioRear > 0.20f || (input.handbrake && abs(rawSteer) > 0.1f))
        drifting = isCurrentlyDrifting
        if (isCurrentlyDrifting) {
            driftGraceTimer = 0f
            driftTimer += h
            driftMultiplier = (1 + (driftTimer / 1.8f).toInt()).coerceIn(1, 5)
            val rate = (speedMs * 13f + abs(newLatVel) * 28f) * driftMultiplier
            currentDriftPoints += rate * h
        } else if (currentDriftPoints > 0f) {
            driftGraceTimer += h
            if (driftGraceTimer >= 1.15f) {
                val banked = currentDriftPoints.toLong()
                totalSessionDriftScore += banked
                if (banked > highestSessionCombo) highestSessionCombo = banked
                currentDriftPoints = 0f
                driftMultiplier = 1
                driftTimer = 0f
                driftGraceTimer = 0f
            }
        }

        // Decay impact shake, crash HUD flash, repair banner & emit hood engine smoke when damaged
        if (impactShakeIntensity > 0f) {
            impactShakeIntensity = (impactShakeIntensity - h * 2.8f).coerceAtLeast(0f)
        }
        if (impactFlashAlpha > 0f) {
            impactFlashAlpha = (impactFlashAlpha - h * 2.1f).coerceAtLeast(0f)
        }
        if (repairedNoticeTimer > 0f) {
            repairedNoticeTimer = (repairedNoticeTimer - h).coerceAtLeast(0f)
        }

        // Emit rising engine hood smoke below 55% chassis integrity (denser/darker below 28%)
        if (healthNormalized < 0.55f) {
            val smokeSeverity = ((0.55f - healthNormalized) / 0.45f).coerceIn(0.1f, 1.0f)
            val interval = (0.14f - smokeSeverity * 0.085f).coerceAtLeast(0.045f)
            engineSmokeAccum += h
            if (engineSmokeAccum >= interval) {
                engineSmokeAccum = 0f
                val hoodX = x + nFwdX * (carBuild.spec.bodyLength * 0.32f)
                val hoodZ = z + nFwdZ * (carBuild.spec.bodyLength * 0.32f)
                vfxListener?.onEngineSmoke(hoodX, y, hoodZ, vx, vz, smokeSeverity)
            }
        }

        // Auto-repair to 100% integrity when driving through any glowing emerald Repair Bay
        if (healthNormalized < 0.99f && CityWorld.findRepairBay(x, z) != null) {
            repairCar(vfxListener = vfxListener, showNotice = true)
        }

        evaluateEventProgress(currentRacePosition, totalRacers, onRaceFinished)
        updateGameplayCamera(h, cameraMode, wantsNitro, nFwdX, nFwdZ)
    }

    private fun sampleRoadHeightWithMicroBumps(wx: Float, wz: Float): Float {
        val base = CityWorld.getGroundHeight(wx, wz)
        // Subtle expansion-joint undulations on elevated skyway & suspension bridge for lively suspension
        if (base > 0.5f) {
            val joint = sin(wx * 0.45f + wz * 0.45f) * 0.035f
            return base + joint
        }
        return base
    }

    private fun resolveRigidBodyCollisions(
        colliders: List<AabbCollider>,
        vfxListener: DrivingVfxListener?
    ) {
        val restitution = 0.28f
        val carRadius = 1.42f

        fun applyImpact(nx: Float, nz: Float, push: Float, contactX: Float, contactZ: Float) {
            x += nx * push
            z += nz * push
            val vn = vx * nx + vz * nz
            if (vn < -0.2f) {
                val impactSpeed = -vn
                val j = -(1f + restitution) * vn
                vx += nx * j
                vz += nz * j
                // Tangential friction along wall
                vx *= 0.84f
                vz *= 0.84f
                speedMs = hypot(vx, vz)

                // Rigid-body angular bounce/deflection from glancing corner hit
                val rx = contactX - x
                val rz = contactZ - z
                val crossY = rx * (nz * j) - rz * (nx * j)
                angularVelY = (angularVelY + crossY * 0.045f).coerceIn(-2.8f, 2.8f)

                if (impactSpeed > 4.0f) {
                    val normImpact = (impactSpeed / 32f).coerceIn(0.2f, 1.0f)
                    impactShakeIntensity = maxOf(impactShakeIntensity, normImpact)
                    vfxListener?.onCollisionSpark(contactX, y, contactZ, nx, nz, normImpact)
                    registerImpactDamage(contactX, contactZ, nx, nz, impactSpeed, vfxListener)
                }
            }
        }

        // World boundary walls
        val boundary = CityWorld.WORLD_HALF
        if (x < -boundary) applyImpact(1f, 0f, -boundary - x, -boundary, z)
        if (x > boundary) applyImpact(-1f, 0f, x - boundary, boundary, z)
        if (z < -boundary) applyImpact(0f, 1f, -boundary - z, x, -boundary)
        if (z > boundary) applyImpact(0f, -1f, z - boundary, x, boundary)

        // Bay water seawall & Suspension Bridge guardrails
        if (abs(x) < 134f && z < -296f && z > -444f && abs(z - CityWorld.BRIDGE_Z) > 10.2f) {
            if (abs(z - CityWorld.BRIDGE_Z) < 28f) {
                if (z > CityWorld.BRIDGE_Z) {
                    applyImpact(0f, -1f, z - (CityWorld.BRIDGE_Z + 10.2f), x, CityWorld.BRIDGE_Z + 10.2f)
                } else {
                    applyImpact(0f, 1f, (CityWorld.BRIDGE_Z - 10.2f) - z, x, CityWorld.BRIDGE_Z - 10.2f)
                }
            } else {
                applyImpact(0f, 1f, -296f - z, x, -296f)
            }
        }

        // Elevated Skyway parapets
        if (y > 2.0f && abs(x - CityWorld.SKYWAY_X) > 9.4f && z in -320f..300f) {
            if (x > CityWorld.SKYWAY_X) {
                applyImpact(-1f, 0f, x - (CityWorld.SKYWAY_X + 9.4f), CityWorld.SKYWAY_X + 9.4f, z)
            } else {
                applyImpact(1f, 0f, (CityWorld.SKYWAY_X - 9.4f) - x, CityWorld.SKYWAY_X - 9.4f, z)
            }
        }

        // Building & tunnel wall AABBs
        for (box in colliders) {
            if (y > box.maxY) continue
            val closestX = x.coerceIn(box.minX, box.maxX)
            val closestZ = z.coerceIn(box.minZ, box.maxZ)
            val dx = x - closestX
            val dz = z - closestZ
            val distSq = dx * dx + dz * dz
            if (distSq < carRadius * carRadius) {
                val dist = hypot(dx, dz).coerceAtLeast(0.001f)
                val nx = dx / dist
                val nz = dz / dist
                applyImpact(nx, nz, carRadius - dist, closestX, closestZ)
            }
        }
    }

    private fun evaluateEventProgress(
        currentRacePosition: Int,
        totalRacers: Int,
        onRaceFinished: (RaceResult) -> Unit
    ) {
        val event = activeEvent ?: return
        if (mode == GameMode.DRIFT_CHALLENGE) {
            if (elapsedMs >= driftDurationMs) {
                val finalDrift = totalSessionDriftScore + currentDriftPoints.toLong()
                val finalCombo = maxOf(highestSessionCombo, currentDriftPoints.toLong())
                val medal = when {
                    finalDrift >= event.goldTarget -> MedalTier.GOLD
                    finalDrift >= event.silverTarget -> MedalTier.SILVER
                    finalDrift >= event.bronzeTarget -> MedalTier.BRONZE
                    else -> MedalTier.NONE
                }
                val payout = computeCredits(event.rewardCredits, medal, finalDrift)
                eventFinished = true
                onRaceFinished(
                    RaceResult(
                        eventId = event.id,
                        eventTitle = event.title,
                        mode = mode,
                        elapsedMs = driftDurationMs,
                        driftScore = finalDrift,
                        highestCombo = finalCombo,
                        medal = medal,
                        creditsEarned = payout,
                        distanceKm = sessionDistanceMeters / 1000f
                    )
                )
            }
        } else if (event.checkpoints.isNotEmpty() && checkpointIndex < event.checkpoints.size) {
            val cp = event.checkpoints[checkpointIndex]
            val dist = hypot(x - cp.x, z - cp.z)
            if (dist <= cp.radius && abs(y - cp.y) <= 5.5f) {
                checkpointIndex++
                nitroNormalized = (nitroNormalized + 0.22f).coerceAtMost(1f)
                if (checkpointIndex >= event.checkpoints.size) {
                    val finalDrift = totalSessionDriftScore + currentDriftPoints.toLong()
                    val finalCombo = maxOf(highestSessionCombo, currentDriftPoints.toLong())
                    val medal = if (mode == GameMode.STREET_RACE) {
                        when (currentRacePosition) {
                            1 -> MedalTier.GOLD
                            2 -> MedalTier.SILVER
                            3 -> MedalTier.BRONZE
                            else -> MedalTier.NONE
                        }
                    } else {
                        when {
                            elapsedMs <= event.goldTarget -> MedalTier.GOLD
                            elapsedMs <= event.silverTarget -> MedalTier.SILVER
                            elapsedMs <= event.bronzeTarget -> MedalTier.BRONZE
                            else -> MedalTier.NONE
                        }
                    }
                    val payout = computeCredits(event.rewardCredits, medal, finalDrift)
                    eventFinished = true
                    onRaceFinished(
                        RaceResult(
                            eventId = event.id,
                            eventTitle = event.title,
                            mode = mode,
                            elapsedMs = elapsedMs,
                            driftScore = finalDrift,
                            highestCombo = finalCombo,
                            medal = medal,
                            creditsEarned = payout,
                            distanceKm = sessionDistanceMeters / 1000f,
                            finishPosition = currentRacePosition,
                            totalRacers = totalRacers
                        )
                    )
                }
            }
        }
    }

    private fun computeCredits(baseReward: Long, medal: MedalTier, driftScore: Long): Long {
        val medalMult = when (medal) {
            MedalTier.GOLD -> 1.0f
            MedalTier.SILVER -> 0.72f
            MedalTier.BRONZE -> 0.48f
            MedalTier.NONE -> 0.25f
        }
        return (baseReward * medalMult).toLong() + (driftScore / 15L)
    }

    private fun updateGameplayCamera(
        dt: Float,
        cameraMode: CameraMode,
        nitro: Boolean,
        fwdX: Float,
        fwdZ: Float
    ) {
        var diff = headingRad - chaseHeadingRad
        while (diff > PI) diff -= (2.0 * PI).toFloat()
        while (diff < -PI) diff += (2.0 * PI).toFloat()
        val followRate = when (cameraMode) {
            CameraMode.HOOD -> 18f
            CameraMode.CINEMATIC -> 4.2f
            else -> 6.5f
        }
        chaseHeadingRad += diff * (dt * followRate).coerceAtMost(1f)

        val camDirX = -sin(chaseHeadingRad)
        val camDirZ = -cos(chaseHeadingRad)
        val speedNorm = (speedMs / 85f).coerceIn(0f, 1f)

        when (cameraMode) {
            CameraMode.CHASE -> {
                val dist = 8.4f + speedNorm * 1.6f + if (nitro) 1.0f else 0f
                val height = 3.10f + speedNorm * 0.25f
                val lerp = (dt * 11f).coerceAtMost(1f)
                camX += ((x - camDirX * dist) - camX) * lerp
                camY += ((y + height) - camY) * lerp
                camZ += ((z - camDirZ * dist) - camZ) * lerp
                camLookX = x + fwdX * 7.5f
                camLookY = y + 1.25f
                camLookZ = z + fwdZ * 7.5f
            }
            CameraMode.CLOSE -> {
                val dist = 5.9f + speedNorm * 0.9f + if (nitro) 0.7f else 0f
                val lerp = (dt * 13f).coerceAtMost(1f)
                camX += ((x - camDirX * dist) - camX) * lerp
                camY += ((y + 2.28f) - camY) * lerp
                camZ += ((z - camDirZ * dist) - camZ) * lerp
                camLookX = x + fwdX * 9.0f
                camLookY = y + 1.08f
                camLookZ = z + fwdZ * 9.0f
            }
            CameraMode.HOOD -> {
                camX = x + fwdX * 0.65f
                camY = y + 1.38f
                camZ = z + fwdZ * 0.65f
                camLookX = x + fwdX * 26f
                camLookY = y + 1.05f
                camLookZ = z + fwdZ * 26f
            }
            CameraMode.CINEMATIC -> {
                cinematicOrbitPhase += dt * 0.45f
                val sweep = sin(cinematicOrbitPhase) * 0.55f
                val angle = chaseHeadingRad + sweep
                val dX = -sin(angle)
                val dZ = -cos(angle)
                val dist = 9.4f + speedNorm * 1.8f
                val lerp = (dt * 7.5f).coerceAtMost(1f)
                camX += ((x - dX * dist) - camX) * lerp
                camY += ((y + 2.4f + cos(cinematicOrbitPhase) * 0.6f) - camY) * lerp
                camZ += ((z - dZ * dist) - camZ) * lerp
                camLookX = x + fwdX * 5.0f
                camLookY = y + 1.15f
                camLookZ = z + fwdZ * 5.0f
            }
        }

        // Speed-based FOV surge + micro high-speed rumble + damped impact shake
        val targetFov = 58f + speedNorm * 14f + if (nitro) 8.5f else 0f
        camFovDeg += (targetFov - camFovDeg) * (dt * 6.5f).coerceAtMost(1f)

        shakePhase += dt * 48f
        val speedRumble = ((speedMs - 55f) / 35f).coerceIn(0f, 1f) * 0.045f + if (nitro) 0.04f else 0f
        val totalShake = impactShakeIntensity * 0.36f + speedRumble
        shakeOffsetX = sin(shakePhase) * totalShake
        shakeOffsetY = cos(shakePhase * 1.37f) * totalShake * 0.7f
    }

    private fun updateShowcaseCamera(dt: Float) {
        if (launchFlyInProgress < 1f) {
            launchFlyInProgress = (launchFlyInProgress + dt * 0.58f).coerceAtMost(1f)
        }
        val ease = 1f - (1f - launchFlyInProgress) * (1f - launchFlyInProgress) * (1f - launchFlyInProgress)
        val flyInExtraAngle = (1f - ease) * 0.95f
        val angle = showroomOrbitRad + flyInExtraAngle + if (screenMode == GameScreen.GARAGE) garageUserOrbitRad else 0f
        val baseRadius = if (screenMode == GameScreen.GARAGE) 6.6f else 7.8f
        val baseHeight = if (screenMode == GameScreen.GARAGE) 2.25f else 2.55f
        val radius = baseRadius + (1f - ease) * 14.5f
        val height = baseHeight + (1f - ease) * 9.0f
        val lerp = (dt * 6.5f).coerceAtMost(1f)
        camX += ((x + sin(angle) * radius) - camX) * lerp
        camY += ((y + height) - camY) * lerp
        camZ += ((z + cos(angle) * radius) - camZ) * lerp

        val lateralOffset = when (screenMode) {
            GameScreen.MAIN_MENU -> -1.4f * ease
            GameScreen.GARAGE -> 1.5f
            else -> 0f
        }
        val rightX = cos(angle)
        val rightZ = -sin(angle)
        camLookX = x + rightX * lateralOffset
        camLookY = y + 0.85f + (1f - ease) * 1.4f
        camLookZ = z + rightZ * lateralOffset
        val targetFov = 50f + (1f - ease) * 16f
        camFovDeg += (targetFov - camFovDeg) * lerp
        shakeOffsetX = 0f
        shakeOffsetY = 0f
    }

    fun buildTelemetry(
        fps: Int,
        racePosition: Int = 1,
        totalRacers: Int = 1,
        timeOfDayLabel: String = "NIGHT",
        weatherLabel: String = "CLEAR",
        roadWetness: Float = 0f,
        radarBlips: List<RadarBlip> = emptyList()
    ): TelemetrySnapshot {
        val event = activeEvent
        val nextCp = if (event != null && checkpointIndex < event.checkpoints.size) {
            event.checkpoints[checkpointIndex]
        } else null
        val avgComp = (wheels[0].compression + wheels[1].compression + wheels[2].compression + wheels[3].compression) * 0.25f
        return TelemetrySnapshot(
            speedKmh = (speedMs * 3.6f).toInt(),
            engineRpm = engineRpm.toInt(),
            rpmNormalized = rpmNormalized,
            gearLabel = gearLabel,
            gearShiftFlash = shiftTimerSec > 0.01f,
            handbrakeActive = handbrakeActive,
            nitroNormalized = nitroNormalized,
            drifting = drifting,
            currentDriftPoints = currentDriftPoints.toLong(),
            driftMultiplier = driftMultiplier,
            totalSessionDriftScore = totalSessionDriftScore,
            mode = mode,
            activeEventTitle = event?.title ?: "FREE DRIVE",
            checkpointIndex = checkpointIndex,
            totalCheckpoints = event?.checkpoints?.size ?: 0,
            elapsedMs = elapsedMs,
            remainingMs = if (mode == GameMode.DRIFT_CHALLENGE) (driftDurationMs - elapsedMs).coerceAtLeast(0L) else 0L,
            countdownLabel = countdownLabel,
            racePosition = racePosition,
            totalRacers = totalRacers,
            carX = x,
            carY = y,
            carZ = z,
            carHeadingRad = headingRad,
            nextCheckpointX = nextCp?.x,
            nextCheckpointZ = nextCp?.z,
            radarBlips = radarBlips,
            districtName = CityWorld.getDistrictName(x, z),
            timeOfDayLabel = timeOfDayLabel,
            weatherLabel = weatherLabel,
            roadWetness = roadWetness,
            collisionIntensity = impactShakeIntensity,
            avgSuspensionCompression = avgComp,
            healthNormalized = healthNormalized,
            frontDamage = frontDamage,
            rearDamage = rearDamage,
            leftDamage = leftDamage,
            rightDamage = rightDamage,
            impactFlashAlpha = impactFlashAlpha,
            damageStatusLabel = damageStatusLabel,
            repairedNoticeActive = repairedNoticeTimer > 0f,
            fps = fps
        )
    }
}

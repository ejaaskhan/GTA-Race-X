package com.example.engine

import com.example.data.CareerEventSpec
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.RadarBlip
import com.example.data.RimStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class AiRacerState(
    val index: Int,
    val name: String,
    val carId: String,
    val paintHex: String,
    val underglowHex: String,
    val rimStyle: RimStyle,
    val baseSkill: Float // 0.92..1.05 pace factor
) {
    var active: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var vx: Float = 0f
    var vz: Float = 0f
    var headingRad: Float = 0f
    var speedMs: Float = 0f
    var steerVisualRad: Float = 0f
    var wheelSpinRad: Float = 0f
    var checkpointIndex: Int = 0
    var finished: Boolean = false
    var finishTimeMs: Long = 0L
    var overtakeOffset: Float = 0f
    var nitroActive: Boolean = false
    var brakeLightsOn: Boolean = false
}

/**
 * Competitive 3-opponent AI street racing system:
 * - Staggered starting grid alignment synchronized with the 3-2-1-GO countdown
 * - Checkpoint waypoint pathfinding with look-ahead curve braking
 * - Active lateral lane-shift overtaking around slower traffic and rival racers
 * - Real-time race position ranking (1ST..4TH) and vehicle-to-rival rigid-body collisions
 */
class AiRacerSystem {
    val racers: Array<AiRacerState> = arrayOf(
        AiRacerState(
            index = 0,
            name = "KAI VEX",
            carId = "apex_kuro",
            paintHex = "#1565C0",
            underglowHex = "#00E5FF",
            rimStyle = RimStyle.CHROME,
            baseSkill = 0.98f
        ),
        AiRacerState(
            index = 1,
            name = "NOVA SOLIS",
            carId = "solstice_v10",
            paintHex = "#FFB800",
            underglowHex = "#FFB800",
            rimStyle = RimStyle.GOLD,
            baseSkill = 1.03f
        ),
        AiRacerState(
            index = 2,
            name = "RAVEN X",
            carId = "spectre_rs",
            paintHex = "#ECEFF1",
            underglowHex = "#E50914",
            rimStyle = RimStyle.CRIMSON,
            baseSkill = 0.94f
        )
    )

    var activeRace: Boolean = false
    private var currentEvent: CareerEventSpec? = null

    fun setupForSession(mode: GameMode, event: CareerEventSpec?) {
        currentEvent = event
        activeRace = (mode == GameMode.STREET_RACE && event != null && event.checkpoints.isNotEmpty())
        if (!activeRace || event == null) {
            for (r in racers) r.active = false
            return
        }

        val fwdX = -sin(event.spawnHeadingRad)
        val fwdZ = -cos(event.spawnHeadingRad)
        val rightX = cos(event.spawnHeadingRad)
        val rightZ = -sin(event.spawnHeadingRad)

        // Staggered 2x2 motorsport starting grid alongside/ahead of player at spawn
        val gridOffsets = arrayOf(
            Pair(3.6f, 4.5f),   // Front-Right (P1)
            Pair(-3.6f, 9.5f),  // Front-Left (P2)
            Pair(3.6f, -3.5f)   // Mid-Right (P3, alongside player at 0,0)
        )

        for (i in racers.indices) {
            val r = racers[i]
            val (latOff, fwdOff) = gridOffsets[i]
            r.active = true
            r.x = event.spawnX + rightX * latOff + fwdX * fwdOff
            r.z = event.spawnZ + rightZ * latOff + fwdZ * fwdOff
            r.y = CityWorld.getGroundHeight(r.x, r.z)
            r.headingRad = event.spawnHeadingRad
            r.vx = 0f
            r.vz = 0f
            r.speedMs = 0f
            r.steerVisualRad = 0f
            r.wheelSpinRad = 0f
            r.checkpointIndex = 0
            r.finished = false
            r.finishTimeMs = 0L
            r.overtakeOffset = if (i % 2 == 0) 2.5f else -2.5f
            r.nitroActive = false
            r.brakeLightsOn = false
        }
    }

    fun step(
        dt: Float,
        countdownActive: Boolean,
        elapsedMs: Long,
        playerPhysics: VehiclePhysics,
        trafficSystem: TrafficSystem,
        roadWetness: Float,
        vfxListener: DrivingVfxListener?
    ) {
        if (!activeRace) return
        val event = currentEvent ?: return
        val checkpoints = event.checkpoints
        if (checkpoints.isEmpty()) return
        val h = dt.coerceIn(0.002f, 0.033f)

        val playerProgress = computeProgressScore(
            playerPhysics.checkpointIndex,
            playerPhysics.x,
            playerPhysics.z,
            checkpoints
        )

        for (r in racers) {
            if (!r.active) continue
            if (countdownActive) {
                r.speedMs = 0f
                r.vx = 0f
                r.vz = 0f
                r.brakeLightsOn = true
                r.nitroActive = false
                continue
            }

            if (r.finished) {
                // Coast gently after crossing finish line
                r.speedMs = (r.speedMs - 14f * h).coerceAtLeast(10f)
                val fx = -sin(r.headingRad)
                val fz = -cos(r.headingRad)
                r.vx = fx * r.speedMs
                r.vz = fz * r.speedMs
                r.x += r.vx * h
                r.z += r.vz * h
                r.y = CityWorld.getGroundHeight(r.x, r.z)
                r.wheelSpinRad = (r.wheelSpinRad - (r.speedMs / 0.39f) * h) % (2f * PI.toFloat())
                r.nitroActive = false
                r.brakeLightsOn = true
                continue
            }

            val cp = checkpoints[r.checkpointIndex]
            val toCpX = cp.x - r.x
            val toCpZ = cp.z - r.z
            val distToCp = hypot(toCpX, toCpZ)

            // Advance checkpoint when passing inside radius
            if (distToCp <= cp.radius + 2.5f) {
                r.checkpointIndex++
                if (r.checkpointIndex >= checkpoints.size) {
                    r.finished = true
                    r.finishTimeMs = elapsedMs
                    continue
                }
            }

            val targetCp = checkpoints[r.checkpointIndex]
            val dirX = targetCp.x - r.x
            val dirZ = targetCp.z - r.z
            val dirLen = hypot(dirX, dirZ).coerceAtLeast(0.01f)
            val normDirX = dirX / dirLen
            val normDirZ = dirZ / dirLen
            val laneRightX = -normDirZ
            val laneRightZ = normDirX

            // Active overtaking: scan ahead for slower traffic cars or rival racers and shift laterally
            var desiredOvertake = 0f
            val fx = -sin(r.headingRad)
            val fz = -cos(r.headingRad)
            val rx = cos(r.headingRad)
            val rz = -sin(r.headingRad)

            for (tv in trafficSystem.vehicles) {
                if (!tv.active || abs(tv.y - r.y) > 2.2f) continue
                val ox = tv.x - r.x
                val oz = tv.z - r.z
                val ahead = ox * fx + oz * fz
                val lat = ox * rx + oz * rz
                if (ahead in 2f..36f && abs(lat) < 4.2f) {
                    desiredOvertake = if (lat >= 0f) -5.4f else 5.4f
                    break
                }
            }
            // Also overtake slower AI rivals or player if directly blocking
            if (desiredOvertake == 0f) {
                val px = playerPhysics.x - r.x
                val pz = playerPhysics.z - r.z
                val pAhead = px * fx + pz * fz
                val pLat = px * rx + pz * rz
                if (pAhead in 2f..28f && abs(pLat) < 3.6f && playerPhysics.speedMs < r.speedMs + 3f) {
                    desiredOvertake = if (pLat >= 0f) -4.8f else 4.8f
                }
            }
            for (other in racers) {
                if (other.index == r.index || !other.active) continue
                val ox = other.x - r.x
                val oz = other.z - r.z
                val oAhead = ox * fx + oz * fz
                val oLat = ox * rx + oz * rz
                if (oAhead in 1f..22f && abs(oLat) < 3.4f && other.speedMs < r.speedMs + 2f) {
                    desiredOvertake = if (oLat >= 0f) -4.5f else 4.5f
                }
            }

            r.overtakeOffset += (desiredOvertake - r.overtakeOffset) * (h * 4.0f).coerceAtMost(1f)
            val effectiveOffset = if (dirLen < 22f) r.overtakeOffset * (dirLen / 22f) else r.overtakeOffset
            val aimX = targetCp.x + laneRightX * effectiveOffset
            val aimZ = targetCp.z + laneRightZ * effectiveOffset

            val desiredHeading = atan2(-(aimX - r.x).toDouble(), -(aimZ - r.z).toDouble()).toFloat()
            var angleError = desiredHeading - r.headingRad
            while (angleError > PI) angleError -= (2.0 * PI).toFloat()
            while (angleError < -PI) angleError += (2.0 * PI).toFloat()

            val maxTurnRate = (2.35f - (r.speedMs / 95f) * 0.7f).coerceIn(1.25f, 2.35f)
            val turnStep = angleError.coerceIn(-maxTurnRate * h, maxTurnRate * h)
            r.headingRad += turnStep
            r.steerVisualRad = (angleError * 0.8f).coerceIn(-0.45f, 0.45f)

            // Dynamic pace with corner slowing, wet-road caution, and subtle rubber-banding
            val spec = GameCatalog.carById(r.carId)
            val rivalProgress = computeProgressScore(r.checkpointIndex, r.x, r.z, checkpoints)
            val gap = playerProgress - rivalProgress
            val rubberBand = when {
                gap > 90f -> 1.14f    // Catch up slightly if far behind
                gap < -110f -> 0.89f  // Ease off slightly if way ahead
                else -> 1.0f
            }
            val wetPenalty = 1f - roadWetness * 0.08f
            val cornerFactor = (1f - (abs(angleError) / 1.15f).coerceIn(0f, 0.56f))
            val topSpeedMs = (spec.baseTopSpeedKmh / 3.6f) * 0.80f * r.baseSkill * rubberBand * wetPenalty
            val targetSpeed = (topSpeedMs * cornerFactor).coerceAtLeast(18f)

            r.nitroActive = abs(angleError) < 0.14f && dirLen > 65f && gap > 30f
            val accel = (spec.baseAcceleration * 0.78f) * if (r.nitroActive) 1.25f else 1.0f

            if (r.speedMs < targetSpeed) {
                r.speedMs = (r.speedMs + accel * h).coerceAtMost(targetSpeed)
                r.brakeLightsOn = false
            } else {
                r.speedMs = (r.speedMs - 26f * h).coerceAtLeast(targetSpeed)
                r.brakeLightsOn = r.speedMs > targetSpeed + 2.5f
            }

            val nfx = -sin(r.headingRad)
            val nfz = -cos(r.headingRad)
            r.vx = nfx * r.speedMs
            r.vz = nfz * r.speedMs
            r.x += r.vx * h
            r.z += r.vz * h
            r.y = CityWorld.getGroundHeight(r.x, r.z)
            r.wheelSpinRad = (r.wheelSpinRad - (r.speedMs / 0.39f) * h) % (2f * PI.toFloat())
        }

        // Resolve player-to-AI-rival wheel-to-wheel collisions
        resolvePlayerRivalCollisions(playerPhysics, vfxListener)
    }

    private fun resolvePlayerRivalCollisions(p: VehiclePhysics, vfxListener: DrivingVfxListener?) {
        val minDist = 2.65f
        for (r in racers) {
            if (!r.active || abs(p.y - r.y) > 2.2f) continue
            val dx = p.x - r.x
            val dz = p.z - r.z
            val distSq = dx * dx + dz * dz
            if (distSq < minDist * minDist && distSq > 0.0001f) {
                val dist = hypot(dx, dz)
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist
                p.x += nx * overlap * 0.5f
                p.z += nz * overlap * 0.5f
                r.x -= nx * overlap * 0.5f
                r.z -= nz * overlap * 0.5f

                val relVn = (p.vx - r.vx) * nx + (p.vz - r.vz) * nz
                if (relVn < -0.5f) {
                    val impactSpeed = -relVn
                    val j = -1.25f * relVn
                    p.vx += nx * j * 0.45f
                    p.vz += nz * j * 0.45f
                    p.speedMs = hypot(p.vx, p.vz)
                    r.speedMs = (r.speedMs * 0.90f).coerceAtLeast(8f)
                    if (impactSpeed > 3.5f) {
                        val normImpact = (impactSpeed / 28f).coerceIn(0.22f, 0.95f)
                        p.impactShakeIntensity = maxOf(p.impactShakeIntensity, normImpact)
                        vfxListener?.onCollisionSpark((p.x + r.x) * 0.5f, p.y, (p.z + r.z) * 0.5f, nx, nz, normImpact)
                    }
                }
            }
        }
    }

    fun computePlayerPosition(playerPhysics: VehiclePhysics): Int {
        if (!activeRace) return 1
        val checkpoints = currentEvent?.checkpoints ?: return 1
        if (checkpoints.isEmpty()) return 1

        val playerScore = computeProgressScore(
            playerPhysics.checkpointIndex,
            playerPhysics.x,
            playerPhysics.z,
            checkpoints
        )
        var aheadCount = 0
        for (r in racers) {
            if (!r.active) continue
            val rivalScore = if (r.finished) {
                Float.MAX_VALUE - r.finishTimeMs.toFloat()
            } else {
                computeProgressScore(r.checkpointIndex, r.x, r.z, checkpoints)
            }
            if (rivalScore > playerScore) aheadCount++
        }
        return (aheadCount + 1).coerceIn(1, racers.size + 1)
    }

    fun totalParticipants(): Int = if (activeRace) racers.size + 1 else 1

    fun collectRivalBlips(out: MutableList<RadarBlip>) {
        if (!activeRace) return
        for (r in racers) {
            if (r.active) out.add(RadarBlip(r.x, r.z, isRival = true))
        }
    }

    private fun computeProgressScore(
        cpIdx: Int,
        x: Float,
        z: Float,
        checkpoints: List<com.example.data.CheckpointNode>
    ): Float {
        if (cpIdx >= checkpoints.size) return checkpoints.size * 10000f + 5000f
        val cp = checkpoints[cpIdx]
        val dist = hypot(x - cp.x, z - cp.z)
        return cpIdx * 10000f - dist
    }
}

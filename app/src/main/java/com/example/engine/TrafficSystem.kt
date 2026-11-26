package com.example.engine

import com.example.data.RadarBlip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class TrafficSignalState {
    NS_GREEN,
    NS_YELLOW,
    EW_GREEN,
    EW_YELLOW
}

enum class TrafficVehicleType(
    val baseSpeedMs: Float,
    val length: Float,
    val width: Float,
    val height: Float
) {
    SEDAN(15.5f, 4.4f, 1.92f, 1.42f),
    COUPE(20.5f, 4.2f, 1.94f, 1.28f),
    VAN(12.2f, 5.1f, 2.08f, 2.05f)
}

class TrafficVehicle(
    val id: Int,
    val type: TrafficVehicleType,
    val colorR: Float,
    val colorG: Float,
    val colorB: Float
) {
    var active: Boolean = true
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var vx: Float = 0f
    var vz: Float = 0f
    var headingRad: Float = 0f   // 0 = -Z (North), PI = +Z (South), -PI/2 = +X (East), +PI/2 = -X (West)
    var speedMs: Float = type.baseSpeedMs
    var targetSpeedMs: Float = type.baseSpeedMs
    var wheelSpinRad: Float = 0f

    // Lane state: moving along avenue coordinate either NS (axisIsNS = true) or EW (axisIsNS = false)
    var axisIsNS: Boolean = true
    var avenueCoord: Float = 0f
    var directionSign: Int = -1  // -1 = North/West, +1 = South/East
    var stoppedAtSignal: Boolean = false
    var brakeLightsOn: Boolean = false
    var knockbackTimer: Float = 0f
    var knockbackVx: Float = 0f
    var knockbackVz: Float = 0f
}

/**
 * Pooled AI Traffic & Synchronized Intersection Traffic Signal Engine optimized for 4–8 GB RAM mobile GPUs.
 * - Road-following right-hand lane keeping across all city avenues, highway, bridge, and tunnel
 * - Distinct cruising speeds per vehicle type (Sedan, Sport Coupe, Delivery Van)
 * - Obeys live synchronized North-South / East-West intersection traffic signals
 * - Real vehicle-to-player rigid-body impulse collisions with spark bursts and knockback recovery
 */
class TrafficSystem {
    companion object {
        const val MAX_POOL = 26
        private const val LANE_OFFSET = 4.6f
        private const val SIGNAL_CYCLE_SEC = 22f // 9s NS green, 2s NS yellow, 9s EW green, 2s EW yellow
        private const val STOP_LINE_DIST = 17.5f
    }

    var signalTimerSec: Float = 0f
    var signalState: TrafficSignalState = TrafficSignalState.NS_GREEN

    val vehicles: Array<TrafficVehicle>
    private val rng = Random(4207)

    init {
        val palette = arrayOf(
            floatArrayOf(0.84f, 0.86f, 0.90f), // Silver
            floatArrayOf(0.16f, 0.24f, 0.44f), // Midnight Blue
            floatArrayOf(0.76f, 0.14f, 0.16f), // Street Red
            floatArrayOf(0.22f, 0.24f, 0.28f), // Charcoal
            floatArrayOf(0.90f, 0.72f, 0.14f), // Taxi Yellow
            floatArrayOf(0.14f, 0.44f, 0.36f), // Emerald
            floatArrayOf(0.88f, 0.42f, 0.12f)  // Cargo Orange
        )
        vehicles = Array(MAX_POOL) { idx ->
            val type = when (idx % 5) {
                0 -> TrafficVehicleType.COUPE
                4 -> TrafficVehicleType.VAN
                else -> TrafficVehicleType.SEDAN
            }
            val c = palette[idx % palette.size]
            TrafficVehicle(idx, type, c[0], c[1], c[2])
        }
        resetTraffic(MAX_POOL, avoidX = 0f, avoidZ = 180f)
    }

    fun isRedForAxis(axisIsNS: Boolean): Boolean = when (signalState) {
        TrafficSignalState.NS_GREEN -> !axisIsNS
        TrafficSignalState.NS_YELLOW -> true
        TrafficSignalState.EW_GREEN -> axisIsNS
        TrafficSignalState.EW_YELLOW -> true
    }

    fun resetTraffic(activeCount: Int, avoidX: Float, avoidZ: Float) {
        val clamped = activeCount.coerceIn(0, MAX_POOL)
        val avenues = CityWorld.AVENUE_COORDS
        for (i in 0 until MAX_POOL) {
            val v = vehicles[i]
            v.active = i < clamped
            v.knockbackTimer = 0f
            v.knockbackVx = 0f
            v.knockbackVz = 0f
            v.stoppedAtSignal = false
            v.brakeLightsOn = false
            if (!v.active) continue

            val axisNS = (i % 2 == 0)
            val coord = avenues[(i * 3 + 1) % avenues.size]
            val dir = if (i % 4 < 2) -1 else 1
            var posAlong = -300f + ((i * 53) % 600).toFloat()

            // Avoid spawning directly on top of the player start grid or in the bay channel
            v.axisIsNS = axisNS
            v.avenueCoord = coord
            v.directionSign = dir
            assignLanePosition(v, posAlong)
            if (hypot(v.x - avoidX, v.z - avoidZ) < 42f || isInBayWater(v.x, v.z)) {
                posAlong = if (posAlong > 0f) posAlong - 140f else posAlong + 140f
                assignLanePosition(v, posAlong)
            }
            v.speedMs = v.type.baseSpeedMs * (0.88f + rng.nextFloat() * 0.24f)
            v.targetSpeedMs = v.speedMs
            v.y = CityWorld.getGroundHeight(v.x, v.z)
        }
    }

    fun step(
        dt: Float,
        activeCount: Int,
        playerPhysics: VehiclePhysics,
        vfxListener: DrivingVfxListener?
    ) {
        val h = dt.coerceIn(0.002f, 0.033f)

        // 1. Update synchronized city intersection traffic signals
        signalTimerSec = (signalTimerSec + h) % SIGNAL_CYCLE_SEC
        signalState = when {
            signalTimerSec < 9.0f -> TrafficSignalState.NS_GREEN
            signalTimerSec < 11.0f -> TrafficSignalState.NS_YELLOW
            signalTimerSec < 20.0f -> TrafficSignalState.EW_GREEN
            else -> TrafficSignalState.EW_YELLOW
        }

        val clamped = activeCount.coerceIn(0, MAX_POOL)
        val redNS = isRedForAxis(axisIsNS = true)
        val redEW = isRedForAxis(axisIsNS = false)

        for (i in 0 until MAX_POOL) {
            val v = vehicles[i]
            v.active = i < clamped
            if (!v.active) continue

            // Handle post-collision knockback slide before resuming road AI
            if (v.knockbackTimer > 0f) {
                v.knockbackTimer = (v.knockbackTimer - h).coerceAtLeast(0f)
                v.x += v.knockbackVx * h
                v.z += v.knockbackVz * h
                v.knockbackVx *= (1f - h * 3.2f)
                v.knockbackVz *= (1f - h * 3.2f)
                v.y = CityWorld.getGroundHeight(v.x, v.z)
                v.brakeLightsOn = true
                if (v.knockbackTimer <= 0.01f) {
                    // Gently snap back onto nearest avenue lane
                    snapToNearestAvenue(v)
                }
                continue
            }

            // Compute desired forward unit vector from lane direction
            val fwdX: Float
            val fwdZ: Float
            val desiredHeading: Float
            if (v.axisIsNS) {
                fwdX = 0f
                fwdZ = v.directionSign.toFloat()
                desiredHeading = if (v.directionSign < 0) 0f else PI.toFloat()
            } else {
                fwdX = v.directionSign.toFloat()
                fwdZ = 0f
                desiredHeading = if (v.directionSign > 0) (-PI * 0.5).toFloat() else (PI * 0.5).toFloat()
            }

            // Smooth heading alignment
            var hDiff = desiredHeading - v.headingRad
            while (hDiff > PI) hDiff -= (2.0 * PI).toFloat()
            while (hDiff < -PI) hDiff += (2.0 * PI).toFloat()
            v.headingRad += hDiff * (h * 9f).coerceAtMost(1f)

            // Check approaching signalized intersection stop line (skip elevated highway & bridge span)
            val alongPos = if (v.axisIsNS) v.z else v.x
            val isElevated = v.y > 1.5f
            var mustStop = false

            if (!isElevated) {
                val axisRed = if (v.axisIsNS) redNS else redEW
                if (axisRed) {
                    for (inter in CityWorld.AVENUE_COORDS) {
                        val distToInter = (inter - alongPos) * v.directionSign
                        if (distToInter in (STOP_LINE_DIST - 5.5f)..(STOP_LINE_DIST + 12f)) {
                            mustStop = true
                            break
                        }
                    }
                }
            }

            // Check distance to vehicle directly ahead in same lane (or player car)
            var leadSpeedLimit = v.targetSpeedMs
            val pDx = playerPhysics.x - v.x
            val pDz = playerPhysics.z - v.z
            val pAhead = pDx * fwdX + pDz * fwdZ
            val pLat = abs(pDx * (-fwdZ) + pDz * fwdX)
            if (pAhead in 0f..22f && pLat < 3.4f && abs(playerPhysics.y - v.y) < 2.2f) {
                if (pAhead < 9.5f) mustStop = true else leadSpeedLimit = minOf(leadSpeedLimit, playerPhysics.speedMs * 0.85f)
            }

            for (j in 0 until clamped) {
                if (j == i) continue
                val other = vehicles[j]
                if (!other.active) continue
                val oDx = other.x - v.x
                val oDz = other.z - v.z
                val oAhead = oDx * fwdX + oDz * fwdZ
                val oLat = abs(oDx * (-fwdZ) + oDz * fwdX)
                if (oAhead in 0f..19f && oLat < 3.0f && abs(other.y - v.y) < 2.2f) {
                    if (oAhead < 8.5f) {
                        mustStop = true
                    } else {
                        leadSpeedLimit = minOf(leadSpeedLimit, other.speedMs * 0.9f)
                    }
                }
            }

            v.stoppedAtSignal = mustStop
            val desiredSpeed = if (mustStop) 0f else leadSpeedLimit
            val accelRate = if (desiredSpeed < v.speedMs) 22f else 7.5f
            if (v.speedMs < desiredSpeed) {
                v.speedMs = (v.speedMs + accelRate * h).coerceAtMost(desiredSpeed)
                v.brakeLightsOn = false
            } else {
                v.speedMs = (v.speedMs - accelRate * h).coerceAtLeast(desiredSpeed)
                v.brakeLightsOn = (v.speedMs > 0.2f && desiredSpeed < v.speedMs - 0.5f) || mustStop
            }

            v.vx = fwdX * v.speedMs
            v.vz = fwdZ * v.speedMs
            v.x += v.vx * h
            v.z += v.vz * h

            // Gently steer back onto right-hand lane centerline at physically bounded lateral speed
            val maxLatStep = 5.5f * h
            if (v.axisIsNS) {
                val targetX = v.avenueCoord + if (v.directionSign < 0) LANE_OFFSET else -LANE_OFFSET
                v.x += ((targetX - v.x) * (h * 4f).coerceAtMost(1f)).coerceIn(-maxLatStep, maxLatStep)
            } else {
                val targetZ = v.avenueCoord + if (v.directionSign > 0) LANE_OFFSET else -LANE_OFFSET
                v.z += ((targetZ - v.z) * (h * 4f).coerceAtMost(1f)).coerceIn(-maxLatStep, maxLatStep)
            }

            // Intersection turning & city perimeter wrap-around
            handleIntersectionAndBounds(v)

            v.y = CityWorld.getGroundHeight(v.x, v.z)
            v.wheelSpinRad = (v.wheelSpinRad - (v.speedMs / 0.36f) * h) % (2f * PI.toFloat())
        }

        // 2. Resolve player-to-traffic rigid-body collisions (active racing only)
        if (playerPhysics.screenMode == com.example.data.GameScreen.RACING && !playerPhysics.paused) {
            resolvePlayerTrafficCollisions(clamped, playerPhysics, vfxListener)
        }
    }

    private fun handleIntersectionAndBounds(v: TrafficVehicle) {
        val along = if (v.axisIsNS) v.z else v.x
        // Wrap around expanded outer city/coastal/mountain ring cleanly
        if (along < -550f || along > 550f) {
            val wrapped = if (along < -550f) 530f else -530f
            assignLanePosition(v, wrapped)
            if (isInBayWater(v.x, v.z)) {
                v.axisIsNS = false
                v.avenueCoord = CityWorld.BRIDGE_Z
                assignLanePosition(v, -320f)
            }
            return
        }

        // Avoid bay water gap on x=0, z < -285
        if (v.axisIsNS && abs(v.avenueCoord) < 120f && v.z < -250f && v.directionSign < 0) {
            v.axisIsNS = false
            v.avenueCoord = -240f
            v.directionSign = if (rng.nextBoolean()) 1 else -1
            v.z = -240f + if (v.directionSign > 0) LANE_OFFSET else -LANE_OFFSET
            return
        }

        // Occasional clean 90-degree turn right at ground-level intersection centers
        if (v.y < 1.0f && abs(v.avenueCoord - CityWorld.SKYWAY_X) > 20f) {
            for (inter in CityWorld.AVENUE_COORDS) {
                if (abs(along - inter) < 1.1f && rng.nextFloat() < 0.045f) {
                    if (inter == CityWorld.BRIDGE_Z && v.axisIsNS) continue
                    v.axisIsNS = !v.axisIsNS
                    v.avenueCoord = inter
                    v.directionSign = if (rng.nextBoolean()) 1 else -1
                    break
                }
            }
        }
    }

    private fun resolvePlayerTrafficCollisions(
        clamped: Int,
        p: VehiclePhysics,
        vfxListener: DrivingVfxListener?
    ) {
        for (i in 0 until clamped) {
            val v = vehicles[i]
            if (!v.active || abs(p.y - v.y) > 2.2f) continue
            val dx = p.x - v.x
            val dz = p.z - v.z
            val minDist = 2.65f + (v.type.width - 1.9f) * 0.5f
            val distSq = dx * dx + dz * dz
            if (distSq < minDist * minDist && distSq > 0.0001f) {
                val dist = hypot(dx, dz)
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist

                // Separate player and traffic car proportionally to mass
                p.x += nx * overlap * 0.60f
                p.z += nz * overlap * 0.60f
                v.x -= nx * overlap * 0.40f
                v.z -= nz * overlap * 0.40f

                val relVx = p.vx - v.vx
                val relVz = p.vz - v.vz
                val relVn = relVx * nx + relVz * nz
                if (relVn < -0.5f) {
                    val impactSpeed = -relVn
                    val j = -(1.32f) * relVn
                    p.vx += nx * j * 0.52f
                    p.vz += nz * j * 0.52f
                    p.vx *= 0.86f
                    p.vz *= 0.86f
                    p.speedMs = hypot(p.vx, p.vz)
                    p.angularVelY = (p.angularVelY + ((dx * nz - dz * nx) * 0.08f)).coerceIn(-2.6f, 2.6f)

                    // Knock traffic vehicle away realistically
                    v.knockbackVx = v.vx - nx * j * 0.65f
                    v.knockbackVz = v.vz - nz * j * 0.65f
                    v.knockbackTimer = 0.85f
                    v.speedMs = 0f

                    if (impactSpeed > 3.5f) {
                        val normImpact = (impactSpeed / 28f).coerceIn(0.25f, 1.0f)
                        p.impactShakeIntensity = maxOf(p.impactShakeIntensity, normImpact)
                        val cx = (p.x + v.x) * 0.5f
                        val cz = (p.z + v.z) * 0.5f
                        vfxListener?.onCollisionSpark(cx, p.y, cz, nx, nz, normImpact)
                        p.registerImpactDamage(cx, cz, nx, nz, impactSpeed, vfxListener)
                    }
                }
            }
        }
    }

    private fun assignLanePosition(v: TrafficVehicle, along: Float) {
        if (v.axisIsNS) {
            v.x = v.avenueCoord + if (v.directionSign < 0) LANE_OFFSET else -LANE_OFFSET
            v.z = along
            v.headingRad = if (v.directionSign < 0) 0f else PI.toFloat()
        } else {
            v.x = along
            v.z = v.avenueCoord + if (v.directionSign > 0) LANE_OFFSET else -LANE_OFFSET
            v.headingRad = if (v.directionSign > 0) (-PI * 0.5).toFloat() else (PI * 0.5).toFloat()
        }
    }

    private fun snapToNearestAvenue(v: TrafficVehicle) {
        val nearestX = CityWorld.AVENUE_COORDS.minByOrNull { abs(it - v.x) } ?: 0f
        val nearestZ = CityWorld.AVENUE_COORDS.minByOrNull { abs(it - v.z) } ?: 0f
        if (abs(nearestX - v.x) < abs(nearestZ - v.z)) {
            v.axisIsNS = true
            v.avenueCoord = nearestX
        } else {
            v.axisIsNS = false
            v.avenueCoord = nearestZ
        }
        v.speedMs = v.type.baseSpeedMs * 0.5f
    }

    private fun isInBayWater(x: Float, z: Float): Boolean =
        abs(x) < 134f && z < -296f && z > -444f && abs(z - CityWorld.BRIDGE_Z) > 10.5f

    fun collectNearbyBlips(playerX: Float, playerZ: Float, maxDist: Float, out: MutableList<RadarBlip>) {
        val maxSq = maxDist * maxDist
        for (v in vehicles) {
            if (!v.active) continue
            val dx = v.x - playerX
            val dz = v.z - playerZ
            if (dx * dx + dz * dz <= maxSq) {
                out.add(RadarBlip(v.x, v.z, isRival = false))
            }
        }
    }
}

package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class AabbCollider(
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    val maxY: Float
)

class CityChunk(
    val centerX: Float,
    val centerZ: Float,
    val buildingsMesh: GlMesh?,
    val propsMesh: GlMesh?,
    val neonMesh: GlMesh?,
    val lodMesh: GlMesh?
) {
    fun release() {
        buildingsMesh?.release()
        propsMesh?.release()
        neonMesh?.release()
        lodMesh?.release()
    }
}

class CityWorld {
    val roadMesh: GlMesh
    val bridgeDeckMesh: GlMesh
    val tunnelMesh: GlMesh
    val structuresMesh: GlMesh
    val landmarksNeonMesh: GlMesh
    val groundBaseMesh: GlMesh
    val waterMesh: GlMesh
    val checkpointRingMesh: GlMesh
    val startLineGantryMesh: GlMesh
    val finishLineGantryMesh: GlMesh
    val signalBulbMesh: GlMesh
    val trafficSedanMesh: GlMesh
    val trafficVanMesh: GlMesh
    val trafficWheelMesh: GlMesh
    val signalPositions: List<Pair<Float, Float>>
    val chunks: List<CityChunk>
    val colliders: List<AabbCollider>

    companion object {
        const val WORLD_HALF = 420f
        const val ROAD_WIDTH = 22f
        val AVENUE_COORDS = listOf(-360f, -240f, -120f, 0f, 120f, 240f, 360f)

        // Elevated East Highway on x = 360
        const val SKYWAY_X = 360f
        const val SKYWAY_HEIGHT = 9.5f

        // Cable-stayed Suspension Bridge on z = -360 spanning x = -140..+140
        const val BRIDGE_Z = -360f
        const val BRIDGE_HALF_SPAN = 140f
        const val BRIDGE_PEAK_HEIGHT = 7.5f

        // West Neon Tunnel on x = -240 spanning z = -100..+100
        const val TUNNEL_X = -240f
        const val TUNNEL_HALF_LEN = 100f

        fun getGroundHeight(x: Float, z: Float): Float {
            // 1. Suspension Bridge along z = -360 (width +/- 14m)
            if (abs(z - BRIDGE_Z) < 14f && abs(x) < BRIDGE_HALF_SPAN) {
                val t = 1f - abs(x) / BRIDGE_HALF_SPAN // 0 at ends, 1 at center
                return BRIDGE_PEAK_HEIGHT * sin(t * PI * 0.5).toFloat()
            }

            // 2. Elevated East Skyway along x = 360 (width +/- 13m)
            if (abs(x - SKYWAY_X) < 13f && z in -320f..300f) {
                return when {
                    z in -200f..160f -> SKYWAY_HEIGHT
                    z in 160f..300f -> {
                        val t = (300f - z) / 140f
                        SKYWAY_HEIGHT * smoothstep(t)
                    }
                    z in -320f..-200f -> {
                        val t = (z + 320f) / 120f
                        SKYWAY_HEIGHT * smoothstep(t)
                    }
                    else -> 0f
                }
            }
            return 0f
        }

        fun getDistrictName(x: Float, z: Float): String = when {
            abs(z - BRIDGE_Z) < 35f && abs(x) < 160f -> "BRIDGE"
            abs(x - TUNNEL_X) < 25f && abs(z) < TUNNEL_HALF_LEN + 10f -> "TUNNEL"
            abs(x - SKYWAY_X) < 30f && z in -320f..300f -> "HIGHWAY"
            x < -120f && z < -120f -> "RACING DISTRICT"
            x < -120f && z >= 120f -> "RESIDENTIAL DISTRICT"
            x >= 120f && z >= 120f -> "INDUSTRIAL DISTRICT"
            abs(x) < 180f && abs(z) < 180f -> "DOWNTOWN"
            z < -220f -> "BRIDGE APPROACH"
            x >= 180f -> "HIGHWAY CORRIDOR"
            else -> "DOWNTOWN"
        }

        private fun smoothstep(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }

    init {
        val colliderList = mutableListOf<AabbCollider>()

        // 1. Dark midnight city base slab & reflective bay water
        val baseB = MeshBuilder(16)
        baseB.addQuad(
            -560f, -0.25f, 560f,
            560f, -0.25f, 560f,
            560f, -0.25f, -560f,
            -560f, -0.25f, -560f,
            0.06f, 0.07f, 0.10f
        )
        groundBaseMesh = baseB.build()

        val waterB = MeshBuilder(16)
        // Bay channel running under the suspension bridge at z = -360
        waterB.addQuad(
            -136f, -0.08f, -295f,
            136f, -0.08f, -295f,
            136f, -0.08f, -445f,
            -136f, -0.08f, -445f,
            0.04f, 0.14f, 0.28f,
            uScale = 6f, vScale = 4f
        )
        waterMesh = waterB.build()

        // 2. Ground city roads & intersections
        val roadB = MeshBuilder(2048)
        for (coord in AVENUE_COORDS) {
            // North-South avenue along x = coord (skip water channel gap on x=0 at z<-300 and skyway elevated section)
            if (coord != SKYWAY_X) {
                var z = -360f
                while (z < 360f) {
                    val zNext = z + 40f
                    val inBay = abs(coord) < 130f && z < -300f
                    if (!inBay) {
                        addRoadSegmentNS(roadB, coord, z, zNext, 0.04f, 0.04f, ROAD_WIDTH)
                    }
                    z = zNext
                }
            }
            // East-West avenue along z = coord (skip suspension bridge span on z = -360)
            var x = -360f
            while (x < 360f) {
                val xNext = x + 40f
                val onBridge = coord == BRIDGE_Z && x >= -140f && xNext <= 140f
                if (!onBridge) {
                    addRoadSegmentEW(roadB, coord, x, xNext, 0.05f, 0.05f, ROAD_WIDTH)
                }
                x = xNext
            }
        }
        // Ground connectors at south/north ends of East Skyway
        addRoadSegmentNS(roadB, SKYWAY_X, 300f, 360f, 0.04f, 0.04f, ROAD_WIDTH)
        addRoadSegmentNS(roadB, SKYWAY_X, -360f, -320f, 0.04f, 0.04f, ROAD_WIDTH)
        roadMesh = roadB.build()

        // 3. Elevated East Highway + Suspension Bridge deck + Piers & Cables
        val deckB = MeshBuilder(1024)
        val structB = MeshBuilder(2048)
        val landmarkNeonB = MeshBuilder(1024)

        // Elevated Skyway along x = 360 (z = -320..+300)
        val skySteps = 31
        for (i in 0 until skySteps) {
            val z0 = -320f + i * 20f
            val z1 = z0 + 20f
            val y0 = getGroundHeight(SKYWAY_X, z0)
            val y1 = getGroundHeight(SKYWAY_X, z1)
            addRoadSegmentNS(deckB, SKYWAY_X, z0, z1, y0 + 0.06f, y1 + 0.06f, 20f)

            // Side concrete parapets + neon strips
            val midY = (y0 + y1) * 0.5f
            val midZ = (z0 + z1) * 0.5f
            structB.addBox(SKYWAY_X - 10.6f, midY + 0.55f, midZ, 1.0f, 1.2f, 20f, 0.25f, 0.28f, 0.35f)
            structB.addBox(SKYWAY_X + 10.6f, midY + 0.55f, midZ, 1.0f, 1.2f, 20f, 0.25f, 0.28f, 0.35f)
            landmarkNeonB.addBox(SKYWAY_X - 10.0f, midY + 0.95f, midZ, 0.22f, 0.20f, 19.6f, 0.95f, 0.12f, 0.18f)
            landmarkNeonB.addBox(SKYWAY_X + 10.0f, midY + 0.95f, midZ, 0.22f, 0.20f, 19.6f, 0.0f, 0.88f, 1.0f)

            // Support concrete piers every 40m
            if (i % 2 == 0 && midY > 2.2f) {
                structB.addBox(SKYWAY_X - 7.5f, midY * 0.5f, midZ, 2.4f, midY, 2.4f, 0.22f, 0.25f, 0.32f)
                structB.addBox(SKYWAY_X + 7.5f, midY * 0.5f, midZ, 2.4f, midY, 2.4f, 0.22f, 0.25f, 0.32f)
                // Overhead highway lamp post
                structB.addCylinderY(SKYWAY_X - 10.2f, midY + 4.5f, midZ, 0.18f, 0.14f, 7.5f, 6, 0.35f, 0.38f, 0.45f)
                landmarkNeonB.addBox(SKYWAY_X - 8.5f, midY + 8.1f, midZ, 3.2f, 0.25f, 0.5f, 0.85f, 0.96f, 1.0f)
            }
        }
        // Highway gantry signs over skyway
        addHighwayGantry(structB, landmarkNeonB, SKYWAY_X, SKYWAY_HEIGHT, 60f, true)
        addHighwayGantry(structB, landmarkNeonB, SKYWAY_X, SKYWAY_HEIGHT, -120f, true)

        // Cable-Stayed Suspension Bridge along z = -360 (x = -140..+140)
        val bridgeSteps = 28
        for (i in 0 until bridgeSteps) {
            val x0 = -BRIDGE_HALF_SPAN + i * 10f
            val x1 = x0 + 10f
            val y0 = getGroundHeight(x0, BRIDGE_Z)
            val y1 = getGroundHeight(x1, BRIDGE_Z)
            addRoadSegmentEW(deckB, BRIDGE_Z, x0, x1, y0 + 0.06f, y1 + 0.06f, 22f)

            val midY = (y0 + y1) * 0.5f
            val midX = (x0 + x1) * 0.5f
            structB.addBox(midX, midY + 0.6f, BRIDGE_Z - 11.4f, 10f, 1.2f, 0.9f, 0.26f, 0.30f, 0.38f)
            structB.addBox(midX, midY + 0.6f, BRIDGE_Z + 11.4f, 10f, 1.2f, 0.9f, 0.26f, 0.30f, 0.38f)
            landmarkNeonB.addBox(midX, midY + 1.05f, BRIDGE_Z - 10.9f, 9.6f, 0.22f, 0.22f, 0.0f, 0.90f, 1.0f)
            landmarkNeonB.addBox(midX, midY + 1.05f, BRIDGE_Z + 10.9f, 9.6f, 0.22f, 0.22f, 0.98f, 0.14f, 0.20f)
        }
        // Twin Suspension Bridge Pylons at x = -65 and x = +65
        for (px in listOf(-65f, 65f)) {
            val deckY = getGroundHeight(px, BRIDGE_Z)
            val pylonH = 44f
            structB.addBox(px, pylonH * 0.5f, BRIDGE_Z - 12.2f, 3.2f, pylonH, 3.2f, 0.30f, 0.34f, 0.44f)
            structB.addBox(px, pylonH * 0.5f, BRIDGE_Z + 12.2f, 3.2f, pylonH, 3.2f, 0.30f, 0.34f, 0.44f)
            // Cross beams
            structB.addBox(px, pylonH - 4f, BRIDGE_Z, 2.6f, 2.8f, 25f, 0.32f, 0.36f, 0.46f)
            structB.addBox(px, deckY + 14f, BRIDGE_Z, 2.2f, 2.2f, 25f, 0.32f, 0.36f, 0.46f)
            // Glowing crimson pylon beacons & vertical neon strips
            landmarkNeonB.addBox(px, pylonH + 1.2f, BRIDGE_Z - 12.2f, 1.4f, 2.4f, 1.4f, 1.0f, 0.12f, 0.18f)
            landmarkNeonB.addBox(px, pylonH + 1.2f, BRIDGE_Z + 12.2f, 1.4f, 2.4f, 1.4f, 1.0f, 0.12f, 0.18f)
            landmarkNeonB.addBox(px, pylonH * 0.55f, BRIDGE_Z - 10.5f, 0.35f, pylonH * 0.8f, 0.35f, 0.0f, 0.90f, 1.0f)
            landmarkNeonB.addBox(px, pylonH * 0.55f, BRIDGE_Z + 10.5f, 0.35f, pylonH * 0.8f, 0.35f, 0.0f, 0.90f, 1.0f)

            // Angled stay cables radiating from pylon top to bridge deck
            for (offset in listOf(-50f, -35f, -20f, 20f, 35f, 50f)) {
                val tx = px + offset
                val ty = getGroundHeight(tx, BRIDGE_Z) + 1.1f
                addCableBar(landmarkNeonB, px, pylonH - 5f, BRIDGE_Z - 11.4f, tx, ty, BRIDGE_Z - 11.4f)
                addCableBar(landmarkNeonB, px, pylonH - 5f, BRIDGE_Z + 11.4f, tx, ty, BRIDGE_Z + 11.4f)
            }
        }
        bridgeDeckMesh = deckB.build()

        // 4. West Neon Tunnel (x = -240, z = -100..+100)
        val tunnelB = MeshBuilder(512)
        val tunnelH = 9.2f
        val tunnelHalfW = 12.2f
        // Left & right tunnel walls + ceiling roof
        tunnelB.addBox(TUNNEL_X - tunnelHalfW, tunnelH * 0.5f, 0f, 2.6f, tunnelH, TUNNEL_HALF_LEN * 2f, 0.85f, 0.88f, 0.94f, 8f, 1f)
        tunnelB.addBox(TUNNEL_X + tunnelHalfW, tunnelH * 0.5f, 0f, 2.6f, tunnelH, TUNNEL_HALF_LEN * 2f, 0.85f, 0.88f, 0.94f, 8f, 1f)
        tunnelB.addBox(TUNNEL_X, tunnelH + 1.2f, 0f, tunnelHalfW * 2f + 2.6f, 2.4f, TUNNEL_HALF_LEN * 2f, 0.20f, 0.24f, 0.32f, 2f, 8f, includeBottom = true)
        // Colliders for tunnel side walls so car stays inside
        colliderList.add(AabbCollider(TUNNEL_X - tunnelHalfW - 1.5f, TUNNEL_X - tunnelHalfW + 1.4f, -TUNNEL_HALF_LEN, TUNNEL_HALF_LEN, tunnelH))
        colliderList.add(AabbCollider(TUNNEL_X + tunnelHalfW - 1.4f, TUNNEL_X + tunnelHalfW + 1.5f, -TUNNEL_HALF_LEN, TUNNEL_HALF_LEN, tunnelH))
        // Glowing overhead light bars inside the tunnel & neon portal rings
        var tz = -92f
        while (tz <= 92f) {
            landmarkNeonB.addBox(TUNNEL_X, tunnelH - 0.15f, tz, 9.5f, 0.28f, 1.4f, 0.0f, 0.92f, 1.0f)
            landmarkNeonB.addBox(TUNNEL_X - tunnelHalfW + 1.45f, 2.8f, tz, 0.2f, 0.35f, 14f, 0.98f, 0.12f, 0.20f)
            landmarkNeonB.addBox(TUNNEL_X + tunnelHalfW - 1.45f, 2.8f, tz, 0.2f, 0.35f, 14f, 0.98f, 0.12f, 0.20f)
            tz += 18f
        }
        for (endZ in listOf(-TUNNEL_HALF_LEN, TUNNEL_HALF_LEN)) {
            landmarkNeonB.addBox(TUNNEL_X, tunnelH + 0.5f, endZ, tunnelHalfW * 2f, 0.65f, 0.8f, 1.0f, 0.14f, 0.22f)
        }
        tunnelMesh = tunnelB.build()

        // Major Downtown highway/avenue gantry signs
        addHighwayGantry(structB, landmarkNeonB, 0f, 0f, 60f, true)
        addHighwayGantry(structB, landmarkNeonB, 0f, 0f, -60f, true)
        addHighwayGantry(structB, landmarkNeonB, 120f, 0f, 0f, false)

        structuresMesh = structB.build()
        landmarksNeonMesh = landmarkNeonB.build()

        // 5. Spatial City Chunks across all 7 distinct architectural districts
        val chunkList = mutableListOf<CityChunk>()
        val signalPosList = mutableListOf<Pair<Float, Float>>()
        val rng = Random(2026)

        for (bx in 0 until AVENUE_COORDS.size - 1) {
            for (bz in 0 until AVENUE_COORDS.size - 1) {
                val xMin = AVENUE_COORDS[bx] + 15f
                val xMax = AVENUE_COORDS[bx + 1] - 15f
                val zMin = AVENUE_COORDS[bz] + 15f
                val zMax = AVENUE_COORDS[bz + 1] - 15f
                val cx = (xMin + xMax) * 0.5f
                val cz = (zMin + zMax) * 0.5f

                // Skip water bay blocks directly south/north of suspension bridge
                if (abs(cx) < 125f && cz < -290f) continue

                val bldB = MeshBuilder(512)
                val propB = MeshBuilder(512)
                val neonB = MeshBuilder(256)
                val lodB = MeshBuilder(128)

                val isRacingDistrict = cx < -120f && cz < -120f
                val isResidential = cx < -120f && cz >= 120f
                val isIndustrial = cx >= 120f && cz >= 120f
                val isDowntown = !isRacingDistrict && !isResidential && !isIndustrial && max(abs(cx), abs(cz)) <= 190f

                // Raised district sidewalk / plaza slab
                val padR = if (isResidential) 0.14f else if (isIndustrial) 0.18f else 0.16f
                val padG = if (isResidential) 0.22f else if (isIndustrial) 0.17f else 0.18f
                val padB = if (isResidential) 0.16f else if (isIndustrial) 0.16f else 0.23f
                propB.addBox(cx, 0.22f, cz, xMax - xMin + 4f, 0.44f, zMax - zMin + 4f, padR, padG, padB)

                if (isRacingDistrict) {
                    // Red & white motorsport apex kerbstones along racing circuit edges
                    for (k in -3..3) {
                        val isRed = (k and 1) == 0
                        val kr = if (isRed) 0.92f else 0.94f
                        val kg = if (isRed) 0.10f else 0.94f
                        val kb = if (isRed) 0.14f else 0.96f
                        propB.addBox(xMin - 1.2f, 0.26f, cz + k * 10f, 1.8f, 0.34f, 9.2f, kr, kg, kb)
                        propB.addBox(cx + k * 10f, 0.26f, zMax + 1.2f, 9.2f, 0.34f, 1.8f, kr, kg, kb)
                    }
                    // Tiered Spectator Grandstands + Motorsport Pit Tower
                    bldB.addBox(cx, 5.5f, cz - 14f, 52f, 11f, 18f, 0.72f, 0.74f, 0.80f, 4f, 1f)
                    propB.addBox(cx, 12.2f, cz - 12f, 54f, 1.2f, 22f, 0.22f, 0.24f, 0.30f) // Canopy roof
                    neonB.addBox(cx, 12.6f, cz - 1.5f, 52f, 0.8f, 0.6f, 0.98f, 0.12f, 0.18f) // Crimson circuit ribbon
                    // Race control tower
                    bldB.addBox(cx + 20f, 14f, cz + 18f, 16f, 28f, 16f, 0.84f, 0.86f, 0.92f, 2f, 3f)
                    neonB.addBox(cx + 20f, 28.4f, cz + 18f, 16.5f, 0.9f, 16.5f, 0.0f, 0.90f, 1.0f)
                    // Stadium floodlight towers
                    for (fx in listOf(cx - 26f, cx + 26f)) {
                        propB.addCylinderY(fx, 13f, cz + 18f, 0.45f, 0.30f, 26f, 6, 0.36f, 0.40f, 0.48f)
                        neonB.addBox(fx, 26.2f, cz + 18f, 6.5f, 1.6f, 1.2f, 0.96f, 0.98f, 1.0f)
                    }
                    lodB.addBox(cx, 7f, cz, 56f, 14f, 56f, 0.36f, 0.38f, 0.46f)
                    colliderList.add(AabbCollider(cx - 27f, cx + 27f, cz - 24f, cz - 4f, 14f))
                    colliderList.add(AabbCollider(cx + 11f, cx + 29f, cz + 9f, cz + 27f, 28f))
                } else if (isIndustrial) {
                    // Wide steel cargo warehouses + twin cylindrical silos + crane towers
                    bldB.addBox(cx - 12f, 8.5f, cz - 14f, 40f, 17f, 24f, 0.64f, 0.60f, 0.56f, 3f, 1.5f)
                    bldB.addBox(cx + 14f, 7.0f, cz + 16f, 32f, 14f, 22f, 0.56f, 0.58f, 0.62f, 2.5f, 1.2f)
                    // Industrial storage silos
                    propB.addCylinderY(cx + 22f, 11f, cz - 16f, 6.2f, 6.2f, 22f, 10, 0.48f, 0.52f, 0.56f)
                    propB.addCylinderY(cx - 22f, 10f, cz + 16f, 5.4f, 5.4f, 20f, 10, 0.54f, 0.46f, 0.38f)
                    // Amber industrial sodium hazard beacons & gantry beam
                    neonB.addBox(cx - 12f, 17.5f, cz - 2f, 38f, 0.65f, 0.5f, 1.0f, 0.62f, 0.10f)
                    neonB.addBox(cx + 22f, 22.5f, cz - 16f, 1.6f, 1.2f, 1.6f, 1.0f, 0.45f, 0.08f)
                    lodB.addBox(cx, 8f, cz, 58f, 16f, 58f, 0.34f, 0.32f, 0.30f)
                    colliderList.add(AabbCollider(cx - 33f, cx + 9f, cz - 27f, cz - 1f, 17f))
                    colliderList.add(AabbCollider(cx - 3f, cx + 31f, cz + 4f, cz + 28f, 14f))
                    colliderList.add(AabbCollider(cx + 15f, cx + 29f, cz - 23f, cz - 9f, 22f))
                } else {
                    // Downtown Skyscrapers OR Warm-Lit Residential Apartments
                    val subOffsets = listOf(
                        Pair(-22f, -22f),
                        Pair(22f, -22f),
                        Pair(-22f, 22f),
                        Pair(22f, 22f)
                    )
                    for ((ox, oz) in subOffsets) {
                        val bX = cx + ox
                        val bZ = cz + oz
                        val bW = if (isResidential) rng.nextFloat() * 8f + 18f else rng.nextFloat() * 12f + 22f
                        val bD = if (isResidential) rng.nextFloat() * 8f + 18f else rng.nextFloat() * 12f + 22f
                        val bH = when {
                            isDowntown -> rng.nextFloat() * 68f + 38f
                            isResidential -> rng.nextFloat() * 11f + 9f
                            else -> rng.nextFloat() * 34f + 18f
                        }

                        val tintR = if (isResidential) 0.86f + rng.nextFloat() * 0.12f else 0.72f + rng.nextFloat() * 0.25f
                        val tintG = if (isResidential) 0.78f + rng.nextFloat() * 0.12f else 0.76f + rng.nextFloat() * 0.22f
                        val tintB = if (isResidential) 0.68f + rng.nextFloat() * 0.12f else 0.84f + rng.nextFloat() * 0.16f
                        val uvX = (bW / 14f).coerceAtLeast(1f)
                        val uvY = (bH / 18f).coerceAtLeast(1f)

                        bldB.addBox(bX, bH * 0.5f, bZ, bW, bH, bD, tintR, tintG, tintB, uvX, uvY)
                        lodB.addBox(bX, bH * 0.5f, bZ, bW, bH, bD, tintR * 0.42f, tintG * 0.48f, tintB * 0.62f)

                        if (isResidential) {
                            // Terracotta/slate roof cap & warm porch light
                            propB.addBox(bX, bH + 0.9f, bZ, bW + 1.2f, 1.8f, bD + 1.2f, 0.36f, 0.22f, 0.18f)
                            neonB.addBox(bX, 3.6f, bZ + bD * 0.5f + 0.2f, bW * 0.45f, 0.45f, 0.25f, 1.0f, 0.82f, 0.46f)
                        } else if (bH > 54f) {
                            val topW = bW * 0.55f
                            val topH = 12f
                            bldB.addBox(bX, bH + topH * 0.5f, bZ, topW, topH, topW, tintR * 0.9f, tintG * 0.9f, tintB, 1f, 1f)
                            val crownColor = rng.nextInt(3)
                            val nr = if (crownColor == 0) 0.98f else if (crownColor == 1) 0.0f else 1.0f
                            val ng = if (crownColor == 0) 0.10f else if (crownColor == 1) 0.90f else 0.72f
                            val nb = if (crownColor == 0) 0.18f else if (crownColor == 1) 1.0f else 0.10f
                            neonB.addBox(bX, bH + 0.35f, bZ, bW + 0.4f, 0.7f, bD + 0.4f, nr, ng, nb)
                            neonB.addBox(bX, bH + topH + 3.5f, bZ, 0.8f, 1.2f, 0.8f, 1.0f, 0.1f, 0.15f)
                        } else if (rng.nextFloat() < 0.6f) {
                            val nr = if (rng.nextBoolean()) 0.98f else 0.0f
                            val ng = if (nr > 0.5f) 0.12f else 0.88f
                            val nb = if (nr > 0.5f) 0.22f else 1.0f
                            neonB.addBox(bX, 5.2f, bZ + bD * 0.5f + 0.2f, bW * 0.72f, 0.75f, 0.35f, nr, ng, nb)
                        }

                        colliderList.add(
                            AabbCollider(
                                minX = bX - bW * 0.5f - 0.6f,
                                maxX = bX + bW * 0.5f + 0.6f,
                                minZ = bZ - bD * 0.5f - 0.6f,
                                maxZ = bZ + bD * 0.5f + 0.6f,
                                maxY = bH
                            )
                        )
                    }
                }

                // Street lamps along the block perimeter facing avenues
                addStreetLamp(propB, neonB, xMin - 1.5f, cz - 24f, 1f, 0f)
                addStreetLamp(propB, neonB, xMin - 1.5f, cz + 24f, 1f, 0f)
                addStreetLamp(propB, neonB, xMax + 1.5f, cz - 24f, -1f, 0f)
                addStreetLamp(propB, neonB, xMax + 1.5f, cz + 24f, -1f, 0f)
                addStreetLamp(propB, neonB, cx, zMin - 1.5f, 0f, 1f)
                addStreetLamp(propB, neonB, cx, zMax + 1.5f, 0f, -1f)

                // Stylized urban trees (denser greenery in Residential District)
                addUrbanTree(propB, xMin + 3f, zMin + 3f)
                addUrbanTree(propB, xMax - 3f, zMax - 3f)
                if (isResidential) {
                    addUrbanTree(propB, xMin + 3f, zMax - 3f)
                    addUrbanTree(propB, xMax - 3f, zMin + 3f)
                    addUrbanTree(propB, cx, cz)
                } else if (!isIndustrial) {
                    addUrbanTree(propB, cx, cz)
                }

                // Intersection traffic signal pole & record world coordinates for dynamic live bulbs
                addTrafficSignal(propB, neonB, xMin - 2f, zMin - 2f)
                signalPosList.add(Pair(xMin - 5.6f, zMin - 2f))
                addRoadSign(propB, neonB, xMax + 2f, zMax + 2f)

                chunkList.add(
                    CityChunk(
                        centerX = cx,
                        centerZ = cz,
                        buildingsMesh = if (bldB.isEmpty()) null else bldB.build(),
                        propsMesh = if (propB.isEmpty()) null else propB.build(),
                        neonMesh = if (neonB.isEmpty()) null else neonB.build(),
                        lodMesh = if (lodB.isEmpty()) null else lodB.build()
                    )
                )
            }
        }
        chunks = chunkList
        colliders = colliderList
        signalPositions = signalPosList

        // 6. Glowing 3D Checkpoint Portal Beacon Mesh
        val cpB = MeshBuilder(128)
        cpB.addBox(-8.5f, 5.5f, 0f, 0.9f, 11f, 0.9f, 1.0f, 0.14f, 0.20f)
        cpB.addBox(8.5f, 5.5f, 0f, 0.9f, 11f, 0.9f, 1.0f, 0.14f, 0.20f)
        cpB.addBox(0f, 11.0f, 0f, 17.8f, 1.0f, 1.0f, 0.0f, 0.92f, 1.0f)
        cpB.addCylinderY(0f, 18f, 0f, 1.8f, 0.6f, 36f, 10, 1.0f, 0.18f, 0.24f)
        checkpointRingMesh = cpB.build()

        // 7. 3D Start Line Gantry Mesh (Crimson/White starting lights arch)
        val startB = MeshBuilder(128)
        startB.addBox(-11.2f, 5.0f, 0f, 1.1f, 10f, 1.1f, 0.24f, 0.28f, 0.35f)
        startB.addBox(11.2f, 5.0f, 0f, 1.1f, 10f, 1.1f, 0.24f, 0.28f, 0.35f)
        startB.addBox(0f, 9.4f, 0f, 23.5f, 1.6f, 1.2f, 0.92f, 0.10f, 0.14f)
        startB.addBox(0f, 0.09f, 0f, 21f, 0.05f, 1.6f, 0.95f, 0.95f, 0.98f)
        startLineGantryMesh = startB.build()

        // 8. 3D Checkered Finish Line Gantry Mesh (Alternating black & white motorsport banner)
        val finB = MeshBuilder(160)
        finB.addBox(-11.2f, 5.5f, 0f, 1.1f, 11f, 1.1f, 0.95f, 0.78f, 0.14f)
        finB.addBox(11.2f, 5.5f, 0f, 1.1f, 11f, 1.1f, 0.95f, 0.78f, 0.14f)
        for (c in -4..4) {
            val isWhite = (c and 1) == 0
            val v = if (isWhite) 0.96f else 0.08f
            finB.addBox(c * 2.4f, 10.6f, 0f, 2.35f, 1.4f, 1.2f, v, v, v)
            val v2 = if (isWhite) 0.08f else 0.96f
            finB.addBox(c * 2.4f, 9.2f, 0f, 2.35f, 1.4f, 1.2f, v2, v2, v2)
            finB.addBox(c * 2.3f, 0.09f, 0f, 2.2f, 0.05f, 2.0f, v, v, v)
        }
        finishLineGantryMesh = finB.build()

        // 9. Compact Dynamic Traffic Signal Bulb Mesh
        val bulbB = MeshBuilder(32)
        bulbB.addBox(0f, 0f, 0f, 0.58f, 0.58f, 0.58f, 1f, 1f, 1f)
        signalBulbMesh = bulbB.build()

        // 10. Shared Low-Poly Traffic Vehicle Meshes (optimized for 4-8 GB RAM)
        val sedanB = MeshBuilder(96)
        sedanB.addBox(0f, 0.62f, 0f, 1.90f, 0.68f, 4.35f, 1f, 1f, 1f)
        sedanB.addBox(0f, 1.16f, 0.15f, 1.62f, 0.52f, 2.15f, 0.16f, 0.20f, 0.28f)
        sedanB.addBox(-0.62f, 0.64f, -2.18f, 0.36f, 0.18f, 0.08f, 1.0f, 0.96f, 0.84f)
        sedanB.addBox(0.62f, 0.64f, -2.18f, 0.36f, 0.18f, 0.08f, 1.0f, 0.96f, 0.84f)
        sedanB.addBox(0f, 0.68f, 2.18f, 1.56f, 0.16f, 0.08f, 0.95f, 0.10f, 0.12f)
        trafficSedanMesh = sedanB.build()

        val vanB = MeshBuilder(96)
        vanB.addBox(0f, 0.98f, 0.10f, 2.06f, 1.44f, 4.95f, 1f, 1f, 1f)
        vanB.addBox(0f, 1.25f, -1.75f, 1.88f, 0.65f, 1.05f, 0.14f, 0.18f, 0.24f)
        vanB.addBox(-0.68f, 0.62f, -2.42f, 0.36f, 0.22f, 0.08f, 1.0f, 0.96f, 0.84f)
        vanB.addBox(0.68f, 0.62f, -2.42f, 0.36f, 0.22f, 0.08f, 1.0f, 0.96f, 0.84f)
        trafficVanMesh = vanB.build()

        val tWheelB = MeshBuilder(64)
        tWheelB.addCylinderY(0f, 0f, 0f, 0.36f, 0.36f, 0.26f, 8, 0.12f, 0.13f, 0.16f)
        trafficWheelMesh = tWheelB.build()
    }

    private fun addRoadSegmentNS(
        b: MeshBuilder, x: Float, z0: Float, z1: Float, y0: Float, y1: Float, width: Float
    ) {
        val hw = width * 0.5f
        val vTile = abs(z1 - z0) / 22f
        b.addQuad(
            x - hw, y0, z0,
            x + hw, y0, z0,
            x + hw, y1, z1,
            x - hw, y1, z1,
            1f, 1f, 1f,
            uScale = 1f, vScale = vTile
        )
    }

    private fun addRoadSegmentEW(
        b: MeshBuilder, z: Float, x0: Float, x1: Float, y0: Float, y1: Float, width: Float
    ) {
        val hw = width * 0.5f
        val vTile = abs(x1 - x0) / 22f
        b.addQuad(
            x0, y0, z + hw,
            x0, y0, z - hw,
            x1, y1, z - hw,
            x1, y1, z + hw,
            1f, 1f, 1f,
            uScale = 1f, vScale = vTile
        )
    }

    private fun addStreetLamp(propB: MeshBuilder, neonB: MeshBuilder, x: Float, z: Float, dirX: Float, dirZ: Float) {
        propB.addCylinderY(x, 4.2f, z, 0.16f, 0.11f, 8.4f, 6, 0.28f, 0.32f, 0.38f)
        // Curved arm reaching out over the avenue
        val armX = x - dirX * 1.6f
        val armZ = z - dirZ * 1.6f
        propB.addBox((x + armX) * 0.5f, 8.3f, (z + armZ) * 0.5f, if (dirX != 0f) 3.2f else 0.22f, 0.20f, if (dirZ != 0f) 3.2f else 0.22f, 0.32f, 0.36f, 0.42f)
        // Emissive warm-white/cyan LED street lamp fixture
        neonB.addBox(armX, 8.15f, armZ, 1.1f, 0.22f, 0.55f, 0.90f, 0.96f, 1.0f)
    }

    private fun addUrbanTree(propB: MeshBuilder, x: Float, z: Float) {
        // Trunk
        propB.addCylinderY(x, 1.8f, z, 0.32f, 0.22f, 3.6f, 6, 0.28f, 0.20f, 0.14f)
        // Two-tier dark midnight emerald foliage cones
        propB.addCylinderY(x, 4.4f, z, 2.3f, 0.5f, 3.4f, 7, 0.08f, 0.32f, 0.18f)
        propB.addCylinderY(x, 6.4f, z, 1.7f, 0.15f, 2.8f, 7, 0.10f, 0.40f, 0.22f)
    }

    private fun addTrafficSignal(propB: MeshBuilder, neonB: MeshBuilder, x: Float, z: Float) {
        propB.addCylinderY(x, 3.5f, z, 0.18f, 0.14f, 7.0f, 6, 0.26f, 0.29f, 0.35f)
        propB.addBox(x - 2.2f, 6.8f, z, 4.6f, 0.25f, 0.25f, 0.26f, 0.29f, 0.35f)
        propB.addBox(x - 3.6f, 6.3f, z, 0.55f, 1.3f, 0.45f, 0.12f, 0.13f, 0.16f)
        neonB.addBox(x - 3.6f, 6.65f, z + 0.25f, 0.30f, 0.30f, 0.12f, 1.0f, 0.12f, 0.16f)
        neonB.addBox(x - 3.6f, 6.0f, z + 0.25f, 0.30f, 0.30f, 0.12f, 0.0f, 0.95f, 0.45f)
    }

    private fun addRoadSign(propB: MeshBuilder, neonB: MeshBuilder, x: Float, z: Float) {
        propB.addCylinderY(x, 1.8f, z, 0.09f, 0.09f, 3.6f, 5, 0.45f, 0.48f, 0.55f)
        neonB.addBox(x, 3.4f, z, 1.15f, 1.15f, 0.14f, 0.95f, 0.14f, 0.18f)
    }

    private fun addHighwayGantry(
        structB: MeshBuilder, neonB: MeshBuilder, cx: Float, baseY: Float, cz: Float, spansX: Boolean
    ) {
        val span = 24f
        if (spansX) {
            structB.addBox(cx - span * 0.5f, baseY + 5f, cz, 0.8f, 10f, 0.8f, 0.32f, 0.36f, 0.44f)
            structB.addBox(cx + span * 0.5f, baseY + 5f, cz, 0.8f, 10f, 0.8f, 0.32f, 0.36f, 0.44f)
            structB.addBox(cx, baseY + 9.4f, cz, span + 1f, 0.9f, 0.8f, 0.32f, 0.36f, 0.44f)
            // Illuminated green/crimson overhead route sign board
            structB.addBox(cx - 4.5f, baseY + 9.4f, cz + 0.5f, 7.5f, 2.4f, 0.3f, 0.04f, 0.45f, 0.26f)
            neonB.addBox(cx + 4.8f, baseY + 9.4f, cz + 0.5f, 6.2f, 1.8f, 0.3f, 0.96f, 0.12f, 0.18f)
        } else {
            structB.addBox(cx, baseY + 5f, cz - span * 0.5f, 0.8f, 10f, 0.8f, 0.32f, 0.36f, 0.44f)
            structB.addBox(cx, baseY + 5f, cz + span * 0.5f, 0.8f, 10f, 0.8f, 0.32f, 0.36f, 0.44f)
            structB.addBox(cx, baseY + 9.4f, cz, 0.8f, 0.9f, span + 1f, 0.32f, 0.36f, 0.44f)
            structB.addBox(cx + 0.5f, baseY + 9.4f, cz - 4.5f, 0.3f, 2.4f, 7.5f, 0.04f, 0.45f, 0.26f)
            neonB.addBox(cx + 0.5f, baseY + 9.4f, cz + 4.8f, 0.3f, 1.8f, 6.2f, 0.0f, 0.88f, 1.0f)
        }
    }

    private fun addCableBar(
        b: MeshBuilder,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float
    ) {
        val thickness = 0.16f
        b.addQuad(
            x0, y0 - thickness, z0,
            x1, y1 - thickness, z1,
            x1, y1 + thickness, z1,
            x0, y0 + thickness, z0,
            0.72f, 0.88f, 1.0f
        )
    }

    fun release() {
        roadMesh.release()
        bridgeDeckMesh.release()
        tunnelMesh.release()
        structuresMesh.release()
        landmarksNeonMesh.release()
        groundBaseMesh.release()
        waterMesh.release()
        checkpointRingMesh.release()
        startLineGantryMesh.release()
        finishLineGantryMesh.release()
        signalBulbMesh.release()
        trafficSedanMesh.release()
        trafficVanMesh.release()
        trafficWheelMesh.release()
        chunks.forEach { it.release() }
    }
}

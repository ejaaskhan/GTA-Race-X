package com.example.engine

import com.example.data.BodyKitStyle
import com.example.data.CarSpec
import com.example.data.SpoilerStyle
import com.example.data.VehicleClass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WheelMount(
    val x: Float,
    val y: Float,
    val z: Float,
    val front: Boolean
)

class CarMeshes(
    val bodyMesh: GlMesh,
    val windowsMesh: GlMesh,
    val trimMesh: GlMesh,
    val bodyKitMeshes: Map<BodyKitStyle, GlMesh>,
    val spoilerMeshes: Map<SpoilerStyle, GlMesh>,
    val headlightsMesh: GlMesh,
    val taillightsMesh: GlMesh,
    val wheelMesh: GlMesh,
    val nitroFlameMesh: GlMesh,
    val shadowMesh: GlMesh,
    val underglowMesh: GlMesh,
    val wheelMounts: List<WheelMount>,
    val wheelRadius: Float
) {
    fun release() {
        bodyMesh.release()
        windowsMesh.release()
        trimMesh.release()
        bodyKitMeshes.values.forEach { it.release() }
        spoilerMeshes.values.forEach { it.release() }
        headlightsMesh.release()
        taillightsMesh.release()
        wheelMesh.release()
        nitroFlameMesh.release()
        shadowMesh.release()
        underglowMesh.release()
    }
}

class ShowroomStageMeshes(
    val floorAndWallsMesh: GlMesh,
    val turntableMesh: GlMesh,
    val neonRingCrimsonMesh: GlMesh,
    val neonRingCyanMesh: GlMesh,
    val studioLightsMesh: GlMesh
) {
    fun release() {
        floorAndWallsMesh.release()
        turntableMesh.release()
        neonRingCrimsonMesh.release()
        neonRingCyanMesh.release()
        studioLightsMesh.release()
    }
}

object CarMeshFactory {

    fun buildCar(spec: CarSpec): CarMeshes {
        val len = spec.bodyLength
        val w = spec.bodyWidth
        val wheelRadius = if (spec.vehicleClass == VehicleClass.MUSCLE) 0.41f else 0.39f
        val wheelWidth = if (spec.vehicleClass == VehicleClass.HYPERCAR || spec.vehicleClass == VehicleClass.MUSCLE) 0.37f else 0.34f
        val wheelY = wheelRadius
        val frontZ = -len * 0.31f // Car faces -Z in local space
        val rearZ = len * 0.31f
        val trackHalf = w * 0.48f

        // 1. Metallic Sculpted Car Body tailored distinctly to each of the 5 Vehicle Classes
        val bodyB = MeshBuilder(640)
        when (spec.vehicleClass) {
            VehicleClass.MUSCLE -> {
                // Broad, planted squared-shoulder American V8 Muscle Coupe
                bodyB.addBox(0f, 0.56f, 0f, w * 0.90f, 0.52f, len * 0.96f, 1f, 1f, 1f)
                // Long muscular flat hood
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.52f, topY = 0.88f, cz = -len * 0.28f,
                    bottomW = w * 0.98f, bottomL = len * 0.40f,
                    topW = w * 0.90f, topL = len * 0.34f,
                    topZOffset = 0.14f,
                    r = 1f, g = 1f, b = 1f
                )
                // Wide rear quarter panels
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.52f, topY = 0.94f, cz = len * 0.28f,
                    bottomW = w * 1.02f, bottomL = len * 0.38f,
                    topW = w * 0.94f, topL = len * 0.32f,
                    topZOffset = -0.05f,
                    r = 1f, g = 1f, b = 1f
                )
                // Boxy fastback roof cap
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 1.20f, topY = 1.30f, cz = 0.16f,
                    bottomW = w * 0.72f, bottomL = len * 0.38f,
                    topW = w * 0.66f, topL = len * 0.32f,
                    topZOffset = 0.04f,
                    r = 0.96f, g = 0.96f, b = 0.96f
                )
            }

            VehicleClass.DRIFT -> {
                // Compact agile JDM Street Drift coupe with sharp slanted nose & vented wide doors
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.30f, topY = 0.76f, cz = 0f,
                    bottomW = w * 0.78f, bottomL = len * 0.95f,
                    topW = w * 0.86f, topL = len * 0.86f,
                    topZOffset = 0.06f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.30f, topY = 0.72f, cz = -len * 0.30f,
                    bottomW = w * 0.96f, bottomL = len * 0.34f,
                    topW = w * 0.84f, topL = len * 0.26f,
                    topZOffset = 0.22f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addBox(0f, 0.56f, 0f, w * 0.98f, 0.44f, len * 0.38f, 1f, 1f, 1f)
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.46f, topY = 0.88f, cz = len * 0.29f,
                    bottomW = w * 1.00f, bottomL = len * 0.36f,
                    topW = w * 0.88f, topL = len * 0.28f,
                    topZOffset = -0.04f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 1.15f, topY = 1.24f, cz = 0.14f,
                    bottomW = w * 0.66f, bottomL = len * 0.36f,
                    topW = w * 0.58f, topL = len * 0.28f,
                    topZOffset = 0.04f,
                    r = 0.95f, g = 0.95f, b = 0.95f
                )
            }

            VehicleClass.SUPERCAR -> {
                // Ultra-low mid-engine Italian exotic wedge with sculpted side air ducts
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.26f, topY = 0.72f, cz = 0.02f,
                    bottomW = w * 0.80f, bottomL = len * 0.96f,
                    topW = w * 0.88f, topL = len * 0.86f,
                    topZOffset = 0.08f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.26f, topY = 0.66f, cz = -len * 0.32f,
                    bottomW = w * 0.98f, bottomL = len * 0.34f,
                    topW = w * 0.78f, topL = len * 0.22f,
                    topZOffset = 0.28f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addBox(0f, 0.52f, 0.04f, w * 1.00f, 0.40f, len * 0.36f, 1f, 1f, 1f)
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.46f, topY = 0.88f, cz = len * 0.28f,
                    bottomW = w * 1.04f, bottomL = len * 0.38f,
                    topW = w * 0.86f, topL = len * 0.28f,
                    topZOffset = -0.06f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 1.06f, topY = 1.15f, cz = 0.06f,
                    bottomW = w * 0.58f, bottomL = len * 0.30f,
                    topW = w * 0.52f, topL = len * 0.22f,
                    topZOffset = 0.04f,
                    r = 0.95f, g = 0.95f, b = 0.95f
                )
            }

            VehicleClass.HYPERCAR -> {
                // Extreme Le Mans-style low prototype with teardrop canopy & roof aero intake scoop
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.24f, topY = 0.68f, cz = 0.02f,
                    bottomW = w * 0.78f, bottomL = len * 0.98f,
                    topW = w * 0.86f, topL = len * 0.88f,
                    topZOffset = 0.08f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.24f, topY = 0.62f, cz = -len * 0.33f,
                    bottomW = w * 0.98f, bottomL = len * 0.34f,
                    topW = w * 0.72f, topL = len * 0.20f,
                    topZOffset = 0.30f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addBox(0f, 0.50f, 0.05f, w * 1.02f, 0.36f, len * 0.38f, 1f, 1f, 1f)
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.44f, topY = 0.86f, cz = len * 0.28f,
                    bottomW = w * 1.06f, bottomL = len * 0.40f,
                    topW = w * 0.84f, topL = len * 0.28f,
                    topZOffset = -0.08f,
                    r = 1f, g = 1f, b = 1f
                )
                // Sleek prototype roof + overhead intake snorkel
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 1.04f, topY = 1.13f, cz = 0.04f,
                    bottomW = w * 0.54f, bottomL = len * 0.28f,
                    topW = w * 0.46f, topL = len * 0.20f,
                    topZOffset = 0.04f,
                    r = 0.95f, g = 0.95f, b = 0.95f
                )
                bodyB.addBox(0f, 1.18f, 0.26f, 0.26f, 0.14f, 0.82f, 0.95f, 0.95f, 0.95f)
            }

            VehicleClass.SPORTS -> {
                // Balanced modern Sports GT coupe
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.30f, topY = 0.78f, cz = 0f,
                    bottomW = w * 0.76f, bottomL = len * 0.96f,
                    topW = w * 0.84f, topL = len * 0.86f,
                    topZOffset = 0.05f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.32f, topY = 0.74f, cz = -len * 0.30f,
                    bottomW = w * 0.96f, bottomL = len * 0.34f,
                    topW = w * 0.82f, topL = len * 0.24f,
                    topZOffset = 0.24f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addBox(0f, 0.58f, 0f, w * 0.98f, 0.44f, len * 0.36f, 1f, 1f, 1f)
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 0.48f, topY = 0.90f, cz = len * 0.28f,
                    bottomW = w * 1.02f, bottomL = len * 0.38f,
                    topW = w * 0.88f, topL = len * 0.28f,
                    topZOffset = -0.06f,
                    r = 1f, g = 1f, b = 1f
                )
                bodyB.addTrapezoidPrism(
                    cx = 0f, bottomY = 1.14f, topY = 1.24f, cz = 0.18f,
                    bottomW = w * 0.62f, bottomL = len * 0.34f,
                    topW = w * 0.56f, topL = len * 0.26f,
                    topZOffset = 0.04f,
                    r = 0.95f, g = 0.95f, b = 0.95f
                )
            }
        }
        // Fender eyebrows above all 4 wheel arches
        for (sx in listOf(-1f, 1f)) {
            bodyB.addBox(sx * (trackHalf - 0.04f), 0.80f, frontZ, 0.32f, 0.14f, 0.95f, 1f, 1f, 1f)
            bodyB.addBox(sx * (trackHalf - 0.02f), 0.86f, rearZ, 0.36f, 0.16f, 1.02f, 1f, 1f, 1f)
        }

        // 2. Separate Cockpit Glass Greenhouse Mesh (rendered dynamically with WindowTint color/metallic)
        val winB = MeshBuilder(128)
        val glassBottomY = when (spec.vehicleClass) {
            VehicleClass.MUSCLE -> 0.84f
            VehicleClass.SUPERCAR, VehicleClass.HYPERCAR -> 0.70f
            else -> 0.76f
        }
        val glassTopY = when (spec.vehicleClass) {
            VehicleClass.MUSCLE -> 1.22f
            VehicleClass.SUPERCAR -> 1.08f
            VehicleClass.HYPERCAR -> 1.06f
            else -> 1.16f
        }
        val glassCz = if (spec.vehicleClass == VehicleClass.SUPERCAR || spec.vehicleClass == VehicleClass.HYPERCAR) 0.08f else 0.16f
        winB.addTrapezoidPrism(
            cx = 0f, bottomY = glassBottomY, topY = glassTopY, cz = glassCz,
            bottomW = w * 0.74f, bottomL = len * 0.48f,
            topW = w * 0.58f, topL = len * 0.28f,
            topZOffset = 0.10f,
            r = 1f, g = 1f, b = 1f
        )

        // 3. Base Carbon Trim, Recessed Front Radiator Grille, Side Aero Ducts, Mirrors on Stalks, Interior Rollbar & Polished Exhausts
        val trimB = MeshBuilder(512)
        // Recessed central honeycomb grille + twin side brake-cooling air intakes
        trimB.addBox(0f, 0.38f, -len * 0.482f, w * 0.52f, 0.20f, 0.12f, 0.06f, 0.07f, 0.09f)
        trimB.addBox(0f, 0.41f, -len * 0.488f, w * 0.48f, 0.03f, 0.06f, 0.24f, 0.26f, 0.32f)
        trimB.addBox(0f, 0.35f, -len * 0.488f, w * 0.48f, 0.03f, 0.06f, 0.24f, 0.26f, 0.32f)
        for (sx in listOf(-1f, 1f)) {
            // Side front brake-cooling air ducts
            trimB.addBox(sx * (w * 0.36f), 0.36f, -len * 0.474f, w * 0.18f, 0.16f, 0.12f, 0.07f, 0.08f, 0.10f)
            // Sculpted side-skirt aero vents behind front wheels
            trimB.addBox(sx * (w * 0.485f), 0.52f, -len * 0.12f, 0.06f, 0.22f, 0.46f, 0.08f, 0.09f, 0.12f)
            // Aerodynamic side mirrors with angled mounting stalks
            trimB.addBox(sx * (w * 0.44f), 0.84f, -0.34f, 0.14f, 0.05f, 0.10f, 0.10f, 0.11f, 0.14f)
            trimB.addBox(sx * (w * 0.53f), 0.89f, -0.35f, 0.22f, 0.11f, 0.17f, 0.16f, 0.18f, 0.22f)
            // Recessed wheel-well dark liners so wheels sit cleanly inside fenders
            trimB.addBox(sx * (trackHalf - 0.10f), wheelY + 0.08f, frontZ, 0.24f, wheelRadius * 1.4f, wheelRadius * 2.15f, 0.04f, 0.04f, 0.05f)
            trimB.addBox(sx * (trackHalf - 0.10f), wheelY + 0.08f, rearZ, 0.26f, wheelRadius * 1.45f, wheelRadius * 2.20f, 0.04f, 0.04f, 0.05f)
        }
        // Subtle interior cockpit twin bucket-seat headrests & dark racing roll-bar silhouette
        val seatY = (glassBottomY + glassTopY) * 0.50f
        trimB.addBox(-0.30f, seatY, glassCz + 0.14f, 0.34f, 0.32f, 0.16f, 0.11f, 0.12f, 0.15f)
        trimB.addBox(0.30f, seatY, glassCz + 0.14f, 0.34f, 0.32f, 0.16f, 0.11f, 0.12f, 0.15f)
        trimB.addBox(0f, glassTopY - 0.09f, glassCz + 0.26f, w * 0.52f, 0.05f, 0.05f, 0.24f, 0.26f, 0.30f)

        // Rear lower bumper diffuser surround + Exhaust tips with dark inner bores
        trimB.addBox(0f, 0.36f, len * 0.482f, w * 0.84f, 0.24f, 0.10f, 0.07f, 0.08f, 0.11f)
        if (spec.vehicleClass == VehicleClass.MUSCLE) {
            // Iconic chrome supercharger V8 blower scoop protruding from hood!
            trimB.addBox(0f, 0.94f, -len * 0.24f, 0.54f, 0.22f, 0.78f, 0.74f, 0.78f, 0.84f)
            trimB.addBox(0f, 0.98f, -len * 0.32f, 0.46f, 0.14f, 0.16f, 0.88f, 0.12f, 0.14f)
            // Quad muscular exhausts with dark bores
            for (ex in listOf(-0.56f, -0.34f, 0.34f, 0.56f)) {
                trimB.addBox(ex, 0.34f, len * 0.505f, 0.16f, 0.14f, 0.24f, 0.68f, 0.72f, 0.78f)
                trimB.addBox(ex, 0.34f, len * 0.532f, 0.11f, 0.09f, 0.05f, 0.05f, 0.05f, 0.07f)
            }
        } else {
            // Sculpted carbon hood extractor vent
            trimB.addBox(0f, 0.75f, -len * 0.22f, w * 0.38f, 0.05f, 0.55f, 0.09f, 0.10f, 0.13f)
            // Polished dual/quad sports exhaust tips with dark inner bores
            val exhaustOffsets = if (spec.vehicleClass == VehicleClass.HYPERCAR || spec.vehicleClass == VehicleClass.SUPERCAR) {
                listOf(-0.46f, -0.24f, 0.24f, 0.46f)
            } else {
                listOf(-0.36f, 0.36f)
            }
            for (ex in exhaustOffsets) {
                trimB.addBox(ex, 0.34f, len * 0.505f, 0.18f, 0.14f, 0.24f, 0.64f, 0.68f, 0.74f)
                trimB.addBox(ex, 0.34f, len * 0.532f, 0.12f, 0.09f, 0.05f, 0.04f, 0.05f, 0.07f)
            }
        }
        if (spec.vehicleClass == VehicleClass.SUPERCAR || spec.vehicleClass == VehicleClass.HYPERCAR) {
            // Mid-engine rear carbon deck louvers + sculpted side air intakes
            for (li in 0..3) {
                trimB.addBox(0f, 0.88f, len * (0.14f + li * 0.06f), w * 0.52f, 0.05f, 0.14f, 0.09f, 0.10f, 0.13f)
            }
            for (sx in listOf(-1f, 1f)) {
                trimB.addBox(sx * (w * 0.50f), 0.62f, len * 0.10f, 0.10f, 0.26f, 0.62f, 0.07f, 0.08f, 0.11f)
            }
        }

        // 4. Four Swappable 3D Body Kit Meshes (STREET, WIDEBODY, TRACK_CARBON, APEX_PRO)
        val bodyKitMeshes = buildBodyKits(len, w, frontZ, rearZ, trackHalf)

        // 5. Four Swappable 3D Spoiler Meshes (STOCK, LIP, GT_WING, HYPER_AERO)
        val spoilerMeshes = buildSpoilers(len, w, spec.spoilerHeight)

        // 6. Emissive Angular LED Headlights + Swept DRL Accent Brows (tinted dynamically by HeadlightStyle)
        val headB = MeshBuilder(96)
        val headY = if (spec.vehicleClass == VehicleClass.MUSCLE) 0.64f else 0.54f
        headB.addBox(-w * 0.34f, headY, -len * 0.468f, 0.46f, 0.11f, 0.14f, 1.0f, 1.0f, 1.0f)
        headB.addBox(w * 0.34f, headY, -len * 0.468f, 0.46f, 0.11f, 0.14f, 1.0f, 1.0f, 1.0f)
        headB.addBox(-w * 0.35f, headY + 0.08f, -len * 0.456f, 0.48f, 0.03f, 0.18f, 0.92f, 0.96f, 1.0f)
        headB.addBox(w * 0.35f, headY + 0.08f, -len * 0.456f, 0.48f, 0.03f, 0.18f, 0.92f, 0.96f, 1.0f)

        // 7. Emissive Full-Width Crimson LED Taillights + Center Brake Strip
        val tailB = MeshBuilder(96)
        tailB.addBox(0f, 0.68f, len * 0.478f, w * 0.84f, 0.10f, 0.10f, 1.0f, 0.10f, 0.14f)
        tailB.addBox(-w * 0.35f, 0.68f, len * 0.485f, 0.38f, 0.14f, 0.06f, 1.0f, 0.06f, 0.10f)
        tailB.addBox(w * 0.35f, 0.68f, len * 0.485f, 0.38f, 0.14f, 0.06f, 1.0f, 0.06f, 0.10f)

        // 8. Realistic Visible Wheel Mesh (grooved rubber tire + metallic 5-spoke rim)
        val wheelB = MeshBuilder(256)
        wheelB.addWheelXAxis(
            radius = wheelRadius,
            width = wheelWidth,
            segments = 16,
            tireR = 0.22f, tireG = 0.24f, tireB = 0.28f,
            rimR = 1.0f, rimG = 1.0f, rimB = 1.0f
        )

        // 9. Dual Nitro Exhaust Flame Cones
        val flameB = MeshBuilder(64)
        flameB.addBox(-0.34f, 0.34f, len * 0.50f + 0.55f, 0.20f, 0.18f, 1.15f, 0.0f, 0.92f, 1.0f)
        flameB.addBox(0.34f, 0.34f, len * 0.50f + 0.55f, 0.20f, 0.18f, 1.15f, 0.0f, 0.92f, 1.0f)

        // 10. Realistic Feathered Elliptical Ground Contact Shadow & Neon Underglow Quad
        val shadowB = MeshBuilder(96)
        val shadowSegs = 20
        val rxInner = w * 0.56f
        val rzInner = len * 0.50f
        val rxOuter = w * 0.72f
        val rzOuter = len * 0.62f
        for (i in 0 until shadowSegs) {
            val a0 = (i.toFloat() / shadowSegs) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / shadowSegs) * (2f * PI.toFloat())
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            // Dense dark inner umbra oval
            shadowB.addQuad(
                0f, 0.055f, 0f,
                c0 * rxInner, 0.055f, s0 * rzInner,
                c1 * rxInner, 0.055f, s1 * rzInner,
                0f, 0.055f, 0f,
                0.01f, 0.01f, 0.02f
            )
            // Soft outer penumbra ring
            shadowB.addQuad(
                c0 * rxInner, 0.052f, s0 * rzInner,
                c0 * rxOuter, 0.052f, s0 * rzOuter,
                c1 * rxOuter, 0.052f, s1 * rzOuter,
                c1 * rxInner, 0.052f, s1 * rzInner,
                0.04f, 0.05f, 0.07f
            )
        }

        val underglowB = MeshBuilder(8)
        underglowB.addQuad(
            -w * 0.74f, 0.08f, len * 0.55f,
            w * 0.74f, 0.08f, len * 0.55f,
            w * 0.74f, 0.08f, -len * 0.55f,
            -w * 0.74f, 0.08f, -len * 0.55f,
            1f, 1f, 1f
        )

        val mounts = listOf(
            WheelMount(-trackHalf, wheelY, frontZ, front = true),
            WheelMount(trackHalf, wheelY, frontZ, front = true),
            WheelMount(-trackHalf, wheelY, rearZ, front = false),
            WheelMount(trackHalf, wheelY, rearZ, front = false)
        )

        return CarMeshes(
            bodyMesh = bodyB.build(),
            windowsMesh = winB.build(),
            trimMesh = trimB.build(),
            bodyKitMeshes = bodyKitMeshes,
            spoilerMeshes = spoilerMeshes,
            headlightsMesh = headB.build(),
            taillightsMesh = tailB.build(),
            wheelMesh = wheelB.build(),
            nitroFlameMesh = flameB.build(),
            shadowMesh = shadowB.build(),
            underglowMesh = underglowB.build(),
            wheelMounts = mounts,
            wheelRadius = wheelRadius
        )
    }

    private fun buildBodyKits(
        len: Float,
        w: Float,
        frontZ: Float,
        rearZ: Float,
        trackHalf: Float
    ): Map<BodyKitStyle, GlMesh> {
        val map = LinkedHashMap<BodyKitStyle, GlMesh>()
        for (kit in BodyKitStyle.entries) {
            val b = MeshBuilder(256)
            when (kit) {
                BodyKitStyle.STREET -> {
                    // Clean factory front splitter, subtle side skirts, standard diffuser
                    b.addBox(0f, 0.25f, -len * 0.47f, w * 0.96f, 0.08f, 0.40f, 0.12f, 0.13f, 0.16f)
                    b.addBox(-w * 0.48f, 0.24f, 0f, 0.12f, 0.09f, len * 0.40f, 0.12f, 0.13f, 0.16f)
                    b.addBox(w * 0.48f, 0.24f, 0f, 0.12f, 0.09f, len * 0.40f, 0.12f, 0.13f, 0.16f)
                    b.addBox(0f, 0.32f, len * 0.47f, w * 0.86f, 0.22f, 0.20f, 0.10f, 0.11f, 0.14f)
                }

                BodyKitStyle.WIDEBODY -> {
                    // Flared bolt-on wide fender arches, aggressive chin lip & deep side aero skirts
                    b.addBox(0f, 0.23f, -len * 0.49f, w * 1.12f, 0.10f, 0.54f, 0.11f, 0.12f, 0.15f)
                    for (sx in listOf(-1f, 1f)) {
                        b.addBox(sx * (trackHalf + 0.11f), 0.62f, frontZ, 0.22f, 0.48f, 1.12f, 0.14f, 0.15f, 0.19f)
                        b.addBox(sx * (trackHalf + 0.13f), 0.66f, rearZ, 0.26f, 0.54f, 1.22f, 0.14f, 0.15f, 0.19f)
                        b.addBox(sx * (w * 0.54f), 0.23f, 0f, 0.22f, 0.12f, len * 0.44f, 0.11f, 0.12f, 0.15f)
                    }
                    b.addBox(0f, 0.32f, len * 0.48f, w * 1.04f, 0.26f, 0.26f, 0.09f, 0.10f, 0.13f)
                }

                BodyKitStyle.TRACK_CARBON -> {
                    // Extended racing front splitter with crimson edge + front dive-plane canards + rear diffuser strakes
                    b.addBox(0f, 0.22f, -len * 0.51f, w * 1.08f, 0.07f, 0.64f, 0.10f, 0.11f, 0.14f)
                    b.addBox(0f, 0.22f, -len * 0.54f, w * 1.06f, 0.05f, 0.08f, 0.90f, 0.10f, 0.15f)
                    for (sx in listOf(-1f, 1f)) {
                        b.addBox(sx * (w * 0.52f), 0.42f, -len * 0.44f, 0.18f, 0.06f, 0.38f, 0.12f, 0.13f, 0.16f)
                        b.addBox(sx * (w * 0.52f), 0.23f, 0f, 0.20f, 0.10f, len * 0.44f, 0.10f, 0.11f, 0.14f)
                        b.addBox(sx * (w * 0.56f), 0.29f, len * 0.14f, 0.06f, 0.18f, 0.38f, 0.90f, 0.10f, 0.15f)
                    }
                    b.addBox(0f, 0.31f, len * 0.49f, w * 0.96f, 0.26f, 0.30f, 0.09f, 0.10f, 0.13f)
                    for (fx in listOf(-0.48f, -0.16f, 0.16f, 0.48f)) {
                        b.addBox(fx, 0.28f, len * 0.51f, 0.05f, 0.24f, 0.26f, 0.20f, 0.22f, 0.26f)
                    }
                }

                BodyKitStyle.APEX_PRO -> {
                    // Full widebody + extended splitter + dual canards + glowing crimson trim blades + race diffuser
                    b.addBox(0f, 0.21f, -len * 0.52f, w * 1.16f, 0.08f, 0.68f, 0.09f, 0.10f, 0.13f)
                    b.addBox(0f, 0.21f, -len * 0.56f, w * 1.14f, 0.05f, 0.09f, 0.0f, 0.88f, 1.0f)
                    for (sx in listOf(-1f, 1f)) {
                        b.addBox(sx * (trackHalf + 0.12f), 0.62f, frontZ, 0.24f, 0.50f, 1.16f, 0.12f, 0.13f, 0.17f)
                        b.addBox(sx * (trackHalf + 0.14f), 0.66f, rearZ, 0.28f, 0.56f, 1.26f, 0.12f, 0.13f, 0.17f)
                        b.addBox(sx * (w * 0.56f), 0.42f, -len * 0.44f, 0.22f, 0.06f, 0.42f, 0.92f, 0.10f, 0.16f)
                        b.addBox(sx * (w * 0.56f), 0.22f, 0f, 0.24f, 0.11f, len * 0.46f, 0.09f, 0.10f, 0.13f)
                    }
                    b.addBox(0f, 0.31f, len * 0.50f, w * 1.08f, 0.28f, 0.34f, 0.08f, 0.09f, 0.12f)
                    for (fx in listOf(-0.62f, -0.30f, 0f, 0.30f, 0.62f)) {
                        b.addBox(fx, 0.27f, len * 0.52f, 0.05f, 0.26f, 0.30f, 0.92f, 0.10f, 0.16f)
                    }
                }
            }
            map[kit] = b.build()
        }
        return map
    }

    private fun buildSpoilers(
        len: Float,
        w: Float,
        baseHeight: Float
    ): Map<SpoilerStyle, GlMesh> {
        val map = LinkedHashMap<SpoilerStyle, GlMesh>()
        val wingZ = len * 0.43f
        for (style in SpoilerStyle.entries) {
            val b = MeshBuilder(128)
            when (style) {
                SpoilerStyle.STOCK -> {
                    // Clean low factory decklid edge bar
                    b.addBox(0f, 0.90f, wingZ + 0.04f, w * 0.80f, 0.05f, 0.18f, 0.14f, 0.15f, 0.19f)
                }

                SpoilerStyle.LIP -> {
                    // Swept aggressive ducktail lip spoiler
                    b.addTrapezoidPrism(
                        cx = 0f, bottomY = 0.88f, topY = 1.05f, cz = wingZ + 0.05f,
                        bottomW = w * 0.88f, bottomL = 0.26f,
                        topW = w * 0.92f, topL = 0.14f,
                        topZOffset = 0.09f,
                        r = 0.13f, g = 0.14f, b = 0.18f
                    )
                }

                SpoilerStyle.GT_WING -> {
                    // Elevated Carbon GT Wing with twin uprights & crimson endplates
                    val h = baseHeight.coerceAtLeast(0.32f)
                    val wingY = 0.88f + h
                    b.addBox(-0.48f, 0.88f + h * 0.5f, wingZ, 0.08f, h, 0.22f, 0.12f, 0.13f, 0.16f)
                    b.addBox(0.48f, 0.88f + h * 0.5f, wingZ, 0.08f, h, 0.22f, 0.12f, 0.13f, 0.16f)
                    b.addBox(0f, wingY, wingZ, w * 0.98f, 0.07f, 0.34f, 0.12f, 0.13f, 0.16f)
                    b.addBox(-w * 0.49f, wingY + 0.02f, wingZ, 0.06f, 0.22f, 0.42f, 0.92f, 0.10f, 0.16f)
                    b.addBox(w * 0.49f, wingY + 0.02f, wingZ, 0.06f, 0.22f, 0.42f, 0.92f, 0.10f, 0.16f)
                }

                SpoilerStyle.HYPER_AERO -> {
                    // Double-deck swan-neck hypercar track wing + central aero sharkfin
                    val h = (baseHeight + 0.18f).coerceAtLeast(0.48f)
                    val wingY = 0.88f + h
                    b.addBox(-0.56f, 0.88f + h * 0.52f, wingZ, 0.08f, h, 0.26f, 0.10f, 0.11f, 0.14f)
                    b.addBox(0.56f, 0.88f + h * 0.52f, wingZ, 0.08f, h, 0.26f, 0.10f, 0.11f, 0.14f)
                    b.addBox(0f, wingY, wingZ, w * 1.10f, 0.07f, 0.38f, 0.10f, 0.11f, 0.14f)
                    b.addBox(0f, wingY + 0.12f, wingZ + 0.08f, w * 1.06f, 0.05f, 0.20f, 0.16f, 0.18f, 0.22f)
                    b.addBox(-w * 0.55f, wingY + 0.06f, wingZ + 0.02f, 0.06f, 0.32f, 0.52f, 0.0f, 0.88f, 1.0f)
                    b.addBox(w * 0.55f, wingY + 0.06f, wingZ + 0.02f, 0.06f, 0.32f, 0.52f, 0.0f, 0.88f, 1.0f)
                    // Central vertical aero sharkfin spine
                    b.addBox(0f, 1.08f, len * 0.24f, 0.06f, 0.36f, 0.96f, 0.12f, 0.13f, 0.16f)
                }
            }
            map[style] = b.build()
        }
        return map
    }

    /**
     * Builds the dedicated Lit Neon 3D Showroom Stage rendered in Garage mode:
     * - Dark reflective studio floor & octagonal architectural backdrop walls
     * - Bevelled metallic circular turntable pedestal
     * - Dual concentric glowing Crimson & Cyan neon floor rings
     * - Overhead motorsport studio lighting gantry bars
     */
    fun buildShowroomStage(): ShowroomStageMeshes {
        // 1. Showroom dark floor + surrounding architectural columns & studio backdrop
        val floorB = MeshBuilder(512)
        floorB.addQuad(
            -30f, 0f, 30f,
            30f, 0f, 30f,
            30f, 0f, -30f,
            -30f, 0f, -30f,
            0.05f, 0.06f, 0.08f
        )
        // Curved studio cyclorama pillars around the perimeter
        val wallRadius = 15.5f
        val wallSegs = 16
        for (i in 0 until wallSegs) {
            val a0 = (i.toFloat() / wallSegs) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / wallSegs) * (2f * PI.toFloat())
            val x0 = cos(a0) * wallRadius
            val z0 = sin(a0) * wallRadius
            val x1 = cos(a1) * wallRadius
            val z1 = sin(a1) * wallRadius
            floorB.addQuad(x0, 0f, z0, x1, 0f, z1, x1, 8.5f, z1, x0, 8.5f, z0, 0.04f, 0.05f, 0.07f)
            if (i % 2 == 0) {
                floorB.addBox(x0 * 0.94f, 4.2f, z0 * 0.94f, 0.45f, 8.4f, 0.45f, 0.10f, 0.12f, 0.16f)
            }
        }
        // Overhead steel light gantry trusses
        floorB.addBox(0f, 5.4f, -4.5f, 11.5f, 0.24f, 0.32f, 0.14f, 0.16f, 0.20f)
        floorB.addBox(0f, 5.4f, 4.5f, 11.5f, 0.24f, 0.32f, 0.14f, 0.16f, 0.20f)
        floorB.addBox(-5.6f, 2.7f, -4.5f, 0.28f, 5.4f, 0.28f, 0.14f, 0.16f, 0.20f)
        floorB.addBox(5.6f, 2.7f, -4.5f, 0.28f, 5.4f, 0.28f, 0.14f, 0.16f, 0.20f)
        floorB.addBox(-5.6f, 2.7f, 4.5f, 0.28f, 5.4f, 0.28f, 0.14f, 0.16f, 0.20f)
        floorB.addBox(5.6f, 2.7f, 4.5f, 0.28f, 5.4f, 0.28f, 0.14f, 0.16f, 0.20f)

        // 2. Circular Metallic Turntable Pedestal (height = 0.16m)
        val turnB = MeshBuilder(512)
        val segs = 36
        val outerR = 3.65f
        val innerR = 3.35f
        val topY = 0.16f
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segs) * (2f * PI.toFloat())
            val c0 = cos(a0)
            val s0 = sin(a0)
            val c1 = cos(a1)
            val s1 = sin(a1)
            // Top disc wedge
            turnB.addQuad(
                0f, topY, 0f,
                c0 * innerR, topY, s0 * innerR,
                c1 * innerR, topY, s1 * innerR,
                0f, topY, 0f,
                0.11f, 0.13f, 0.17f
            )
            // Bevelled metallic outer lip
            turnB.addQuad(
                c0 * innerR, topY, s0 * innerR,
                c0 * outerR, 0.02f, s0 * outerR,
                c1 * outerR, 0.02f, s1 * outerR,
                c1 * innerR, topY, s1 * innerR,
                0.20f, 0.22f, 0.28f
            )
        }

        // 3. Glowing Racing Crimson Inner Turntable Ring
        val crimsonB = MeshBuilder(256)
        addNeonFloorRing(crimsonB, innerRadius = 3.42f, outerRadius = 3.56f, y = 0.165f, segs = 36, r = 0.96f, g = 0.08f, bCol = 0.14f)

        // 4. Glowing Nitro Cyan Outer Stage Ring
        val cyanB = MeshBuilder(256)
        addNeonFloorRing(cyanB, innerRadius = 4.60f, outerRadius = 4.74f, y = 0.025f, segs = 40, r = 0.0f, g = 0.90f, bCol = 1.0f)

        // 5. Overhead Emissive Studio Softbox Light Strips & Wall Accent Bars
        val lightB = MeshBuilder(256)
        lightB.addBox(0f, 5.22f, -4.5f, 8.8f, 0.10f, 0.24f, 0.96f, 0.98f, 1.0f)
        lightB.addBox(0f, 5.22f, 4.5f, 8.8f, 0.10f, 0.24f, 0.96f, 0.98f, 1.0f)
        lightB.addBox(0f, 5.25f, 0f, 2.8f, 0.08f, 6.2f, 0.94f, 0.96f, 1.0f)
        // Vertical crimson/cyan neon studio accent strips on the walls
        for (i in 0 until 8) {
            val a = (i.toFloat() / 8f) * (2f * PI.toFloat())
            val wx = cos(a) * 14.3f
            val wz = sin(a) * 14.3f
            val isRed = i % 2 == 0
            lightB.addBox(
                wx, 3.6f, wz, 0.16f, 5.2f, 0.16f,
                if (isRed) 0.94f else 0.0f,
                if (isRed) 0.08f else 0.88f,
                if (isRed) 0.14f else 1.0f
            )
        }

        return ShowroomStageMeshes(
            floorAndWallsMesh = floorB.build(),
            turntableMesh = turnB.build(),
            neonRingCrimsonMesh = crimsonB.build(),
            neonRingCyanMesh = cyanB.build(),
            studioLightsMesh = lightB.build()
        )
    }

    private fun addNeonFloorRing(
        b: MeshBuilder,
        innerRadius: Float,
        outerRadius: Float,
        y: Float,
        segs: Int,
        r: Float,
        g: Float,
        bCol: Float
    ) {
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segs) * (2f * PI.toFloat())
            val c0 = cos(a0)
            val s0 = sin(a0)
            val c1 = cos(a1)
            val s1 = sin(a1)
            b.addQuad(
                c0 * innerRadius, y, s0 * innerRadius,
                c0 * outerRadius, y, s0 * outerRadius,
                c1 * outerRadius, y, s1 * outerRadius,
                c1 * innerRadius, y, s1 * innerRadius,
                r, g, bCol
            )
        }
    }
}

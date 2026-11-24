package com.example.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.random.Random

/**
 * Zero-allocation object-pooled 3D driving VFX system rendering:
 * - Persistent fading asphalt skid marks under slipping rear tires
 * - Rising volumetric tire drift smoke puffs & wet rain road spray
 * - Additive cyan/electric-blue Nitro exhaust flame trail particles
 * - High-contrast metallic collision spark bursts
 * - Quality-scaled camera-local 3D falling rain streaks
 * Dynamically honors Low/Medium/High particle budgets for stable 60 FPS on 6-8 GB RAM devices.
 */
class DrivingVfxSystem {

    private enum class ParticleKind {
        SMOKE,
        SPARK,
        NITRO_FLAME
    }

    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var size = 0.2f
        var age = 0f
        var maxAge = 0.5f
        var r = 1f
        var g = 1f
        var b = 1f
        var kind = ParticleKind.SMOKE
    }

    private class SkidQuad {
        var active = false
        var x0 = 0f; var y0 = 0f; var z0 = 0f
        var x1 = 0f; var y1 = 0f; var z1 = 0f
        var x2 = 0f; var y2 = 0f; var z2 = 0f
        var x3 = 0f; var y3 = 0f; var z3 = 0f
        var age = 0f
    }

    companion object {
        const val MAX_PARTICLES = 144
        const val MAX_SKIDS = 180
        private const val SKID_MAX_AGE = 9.0f
        private const val FLOATS_PER_VERT = 6
        private const val MAX_QUAD_COUNT = MAX_PARTICLES + MAX_SKIDS

        private const val VFX_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aColor;
            uniform mat4 uVpMatrix;
            out vec3 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uVpMatrix * vec4(aPos, 1.0);
            }
        """

        private const val VFX_FS = """#version 300 es
            precision mediump float;
            in vec3 vColor;
            uniform float uAlpha;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, uAlpha);
            }
        """
    }

    private val particles = Array(MAX_PARTICLES) { Particle() }
    private var nextParticleIdx = 0
    @Volatile var particleBudget: Int = MAX_PARTICLES

    private val skids = Array(MAX_SKIDS) { SkidQuad() }
    private var nextSkidIdx = 0

    private val rng = Random(909)
    private var vboId: Int = 0
    private val cpuVertexData = FloatArray(MAX_QUAD_COUNT * 6 * FLOATS_PER_VERT)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(cpuVertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var programId: Int = 0
    private var uVpMatrix: Int = 0
    private var uAlpha: Int = 0

    fun initGl() {
        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        vboId = buffers[0]
        programId = compileProgram(VFX_VS, VFX_FS)
        uVpMatrix = GLES30.glGetUniformLocation(programId, "uVpMatrix")
        uAlpha = GLES30.glGetUniformLocation(programId, "uAlpha")
    }

    fun clear() {
        for (p in particles) p.active = false
        for (s in skids) s.active = false
    }

    fun activeParticleCount(): Int {
        var count = 0
        for (p in particles) if (p.active) count++
        return count
    }

    fun activeNitroParticleCount(): Int {
        var count = 0
        for (p in particles) if (p.active && p.kind == ParticleKind.NITRO_FLAME) count++
        return count
    }

    private fun acquireParticle(): Particle {
        val cap = particleBudget.coerceIn(24, MAX_PARTICLES)
        if (nextParticleIdx >= cap) nextParticleIdx = 0
        val p = particles[nextParticleIdx]
        nextParticleIdx = (nextParticleIdx + 1) % cap
        return p
    }

    fun emitTireSmoke(wx: Float, wy: Float, wz: Float, carVx: Float, carVz: Float) {
        val p = acquireParticle()
        p.active = true
        p.kind = ParticleKind.SMOKE
        p.x = wx + (rng.nextFloat() - 0.5f) * 0.35f
        p.y = wy + 0.18f
        p.z = wz + (rng.nextFloat() - 0.5f) * 0.35f
        p.vx = carVx * 0.14f + (rng.nextFloat() - 0.5f) * 1.6f
        p.vy = 1.2f + rng.nextFloat() * 1.1f
        p.vz = carVz * 0.14f + (rng.nextFloat() - 0.5f) * 1.6f
        p.size = 0.42f + rng.nextFloat() * 0.30f
        p.age = 0f
        p.maxAge = 0.55f + rng.nextFloat() * 0.30f
        val shade = 0.66f + rng.nextFloat() * 0.16f
        p.r = shade
        p.g = shade + 0.02f
        p.b = shade + 0.06f
    }

    /** Emits pooled glowing cyan/electric-blue Nitro exhaust particles behind dual tailpipes. */
    fun emitNitroFlame(wx: Float, wy: Float, wz: Float, backDirX: Float, backDirZ: Float, carVx: Float, carVz: Float) {
        val p = acquireParticle()
        p.active = true
        p.kind = ParticleKind.NITRO_FLAME
        p.x = wx + (rng.nextFloat() - 0.5f) * 0.14f
        p.y = wy + (rng.nextFloat() - 0.5f) * 0.08f
        p.z = wz + (rng.nextFloat() - 0.5f) * 0.14f
        val jetSpeed = 9f + rng.nextFloat() * 6f
        p.vx = carVx * 0.35f + backDirX * jetSpeed + (rng.nextFloat() - 0.5f) * 1.2f
        p.vy = 0.25f + rng.nextFloat() * 0.55f
        p.vz = carVz * 0.35f + backDirZ * jetSpeed + (rng.nextFloat() - 0.5f) * 1.2f
        p.size = 0.26f + rng.nextFloat() * 0.18f
        p.age = 0f
        p.maxAge = 0.18f + rng.nextFloat() * 0.12f
        val hotCore = rng.nextFloat() > 0.45f
        p.r = if (hotCore) 0.55f else 0.05f
        p.g = if (hotCore) 0.96f else 0.80f
        p.b = 1.0f
    }

    fun emitCollisionSparks(wx: Float, wy: Float, wz: Float, nx: Float, nz: Float, intensity: Float) {
        val maxSparks = if (particleBudget <= 48) 8 else 16
        val count = (5 + (intensity * 10f).toInt()).coerceIn(5, maxSparks)
        for (i in 0 until count) {
            val p = acquireParticle()
            p.active = true
            p.kind = ParticleKind.SPARK
            p.x = wx
            p.y = wy + 0.45f + rng.nextFloat() * 0.35f
            p.z = wz
            val spread = 5f + intensity * 8f
            p.vx = nx * (3f + rng.nextFloat() * spread) + (rng.nextFloat() - 0.5f) * spread
            p.vy = 2.8f + rng.nextFloat() * 5.2f
            p.vz = nz * (3f + rng.nextFloat() * spread) + (rng.nextFloat() - 0.5f) * spread
            p.size = 0.14f + rng.nextFloat() * 0.10f
            p.age = 0f
            p.maxAge = 0.28f + rng.nextFloat() * 0.24f
            p.r = 1.0f
            p.g = 0.55f + rng.nextFloat() * 0.42f
            p.b = 0.10f
        }
    }

    fun emitSkidMark(
        prevX: Float, prevY: Float, prevZ: Float,
        currX: Float, currY: Float, currZ: Float,
        rightX: Float, rightZ: Float
    ) {
        val q = skids[nextSkidIdx]
        nextSkidIdx = (nextSkidIdx + 1) % MAX_SKIDS
        val hw = 0.22f
        val yLift = 0.08f
        q.active = true
        q.age = 0f
        q.x0 = prevX - rightX * hw; q.y0 = prevY + yLift; q.z0 = prevZ - rightZ * hw
        q.x1 = prevX + rightX * hw; q.y1 = prevY + yLift; q.z1 = prevZ + rightZ * hw
        q.x2 = currX + rightX * hw; q.y2 = currY + yLift; q.z2 = currZ + rightZ * hw
        q.x3 = currX - rightX * hw; q.y3 = currY + yLift; q.z3 = currZ - rightZ * hw
    }

    fun update(dt: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.age += dt
            if (p.age >= p.maxAge) {
                p.active = false
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            when (p.kind) {
                ParticleKind.SPARK -> p.vy -= 18f * dt
                ParticleKind.NITRO_FLAME -> {
                    p.vx *= (1f - dt * 4.0f)
                    p.vz *= (1f - dt * 4.0f)
                    p.size += dt * 1.15f
                }
                ParticleKind.SMOKE -> {
                    p.vx *= (1f - dt * 1.8f)
                    p.vz *= (1f - dt * 1.8f)
                    p.size += dt * 1.45f
                }
            }
        }
        for (s in skids) {
            if (!s.active) continue
            s.age += dt
            if (s.age >= SKID_MAX_AGE) {
                s.active = false
            }
        }
    }

    fun render(vpMatrix: FloatArray, camX: Float, camZ: Float, carX: Float, carZ: Float) {
        if (programId == 0 || vboId == 0) return

        val dx = carX - camX
        val dz = carZ - camZ
        val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.001f)
        val rightX = -dz / len
        val rightZ = dx / len

        GLES30.glUseProgram(programId)
        GLES30.glUniformMatrix4fv(uVpMatrix, 1, false, vpMatrix, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)

        // Pass 1: Asphalt Skid Marks (batched single draw call)
        var vFloats = 0
        for (s in skids) {
            if (!s.active) continue
            val fade = (1f - s.age / SKID_MAX_AGE).coerceIn(0f, 1f)
            val shade = 0.04f + (1f - fade) * 0.04f
            vFloats = putQuad(
                vFloats,
                s.x0, s.y0, s.z0,
                s.x1, s.y1, s.z1,
                s.x2, s.y2, s.z2,
                s.x3, s.y3, s.z3,
                shade, shade, shade
            )
        }
        if (vFloats > 0) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUniform1f(uAlpha, 0.72f)
            drawDynamicBatch(vFloats)
        }

        // Pass 2: Tire Drift Smoke & Wet Spray (batched single draw call)
        vFloats = 0
        for (p in particles) {
            if (!p.active || p.kind != ParticleKind.SMOKE) continue
            val life = (1f - p.age / p.maxAge).coerceIn(0f, 1f)
            val hs = p.size * 0.5f
            vFloats = putQuad(
                vFloats,
                p.x - rightX * hs, p.y - hs, p.z - rightZ * hs,
                p.x + rightX * hs, p.y - hs, p.z + rightZ * hs,
                p.x + rightX * hs, p.y + hs, p.z + rightZ * hs,
                p.x - rightX * hs, p.y + hs, p.z - rightZ * hs,
                p.r * life, p.g * life, p.b * life
            )
        }
        if (vFloats > 0) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUniform1f(uAlpha, 0.42f)
            drawDynamicBatch(vFloats)
        }

        // Pass 3: Additive Glowing Collision Sparks & Nitro Exhaust Flame Trails (batched single draw call!)
        vFloats = 0
        for (p in particles) {
            if (!p.active || p.kind == ParticleKind.SMOKE) continue
            val life = (1f - p.age / p.maxAge).coerceIn(0f, 1f)
            val hs = p.size * 0.5f
            vFloats = putQuad(
                vFloats,
                p.x - rightX * hs, p.y - hs, p.z - rightZ * hs,
                p.x + rightX * hs, p.y - hs, p.z + rightZ * hs,
                p.x + rightX * hs, p.y + hs, p.z + rightZ * hs,
                p.x - rightX * hs, p.y + hs, p.z - rightZ * hs,
                p.r * life, p.g * life, p.b * life
            )
        }
        if (vFloats > 0) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            GLES30.glUniform1f(uAlpha, 0.94f)
            drawDynamicBatch(vFloats)
        }

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /**
     * Renders quality-scaled pooled camera-local 3D falling raindrop streaks in a single dynamic draw call.
     */
    fun renderRain(
        vpMatrix: FloatArray,
        camX: Float, camZ: Float,
        carX: Float, carY: Float, carZ: Float,
        rainIntensity: Float,
        timeSec: Float,
        densityScale: Float = 1.0f
    ) {
        if (programId == 0 || vboId == 0 || rainIntensity < 0.05f) return

        val dx = carX - camX
        val dz = carZ - camZ
        val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.001f)
        val rightX = -dz / len
        val rightZ = dx / len

        val baseDrops = (35 + (rainIntensity * 85f).toInt())
        val dropCount = (baseDrops * densityScale.coerceIn(0.4f, 1.0f)).toInt().coerceIn(22, 120)
        var vFloats = 0
        val hw = 0.045f
        val streakH = 1.75f
        val fallSpeed = 35f

        for (i in 0 until dropCount) {
            val fi = i.toFloat()
            val rx = (((fi * 73.13f) % 44f) - 22f)
            val rz = (((fi * 41.79f) % 44f) - 22f)
            val yPhase = ((fi * 17.3f - timeSec * fallSpeed) % 24f + 24f) % 24f
            val wx = carX + rx
            val wy = carY + yPhase
            val wz = carZ + rz

            vFloats = putQuad(
                vFloats,
                wx - rightX * hw, wy, wz - rightZ * hw,
                wx + rightX * hw, wy, wz + rightZ * hw,
                wx + rightX * hw + 0.14f, wy + streakH, wz + rightZ * hw + 0.08f,
                wx - rightX * hw + 0.14f, wy + streakH, wz - rightZ * hw + 0.08f,
                0.76f, 0.86f, 0.98f
            )
        }

        if (vFloats > 0) {
            GLES30.glUseProgram(programId)
            GLES30.glUniformMatrix4fv(uVpMatrix, 1, false, vpMatrix, 0)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glDepthMask(false)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUniform1f(uAlpha, (0.28f + rainIntensity * 0.24f).coerceAtMost(0.55f))
            drawDynamicBatch(vFloats)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private fun putQuad(
        offset: Int,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float
    ): Int {
        if (offset + 36 > cpuVertexData.size) return offset
        var o = offset
        o = putVert(o, x0, y0, z0, r, g, b)
        o = putVert(o, x1, y1, z1, r, g, b)
        o = putVert(o, x2, y2, z2, r, g, b)
        o = putVert(o, x0, y0, z0, r, g, b)
        o = putVert(o, x2, y2, z2, r, g, b)
        o = putVert(o, x3, y3, z3, r, g, b)
        return o
    }

    private fun putVert(o: Int, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float): Int {
        cpuVertexData[o] = x
        cpuVertexData[o + 1] = y
        cpuVertexData[o + 2] = z
        cpuVertexData[o + 3] = r
        cpuVertexData[o + 4] = g
        cpuVertexData[o + 5] = b
        return o + 6
    }

    private fun drawDynamicBatch(floatCount: Int) {
        vertexBuffer.clear()
        vertexBuffer.put(cpuVertexData, 0, floatCount)
        vertexBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, vertexBuffer, GLES30.GL_DYNAMIC_DRAW)

        val stride = FLOATS_PER_VERT * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, floatCount / FLOATS_PER_VERT)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}

package com.example.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlMesh(
    vertices: FloatArray,
    indices: IntArray
) {
    private val vboId: Int
    private val iboId: Int
    val indexCount: Int = indices.size

    init {
        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vboId = buffers[0]
        iboId = buffers[1]

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vBuf.position(0)

        val iBuf: IntBuffer = ByteBuffer.allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indices)
        iBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw() {
        if (indexCount == 0) return
        val stride = 11 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)

        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)

        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, stride, 8 * 4)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        val buffers = intArrayOf(vboId, iboId)
        GLES30.glDeleteBuffers(2, buffers, 0)
    }
}

class MeshBuilder(initialVertices: Int = 256) {
    private var verts = FloatArray(initialVertices * 11)
    private var vertCount = 0
    private var idx = IntArray(initialVertices * 2)
    private var idxCount = 0

    private fun ensureVerts(extra: Int) {
        val needed = (vertCount + extra) * 11
        if (needed > verts.size) {
            verts = verts.copyOf(maxOf(verts.size * 2, needed))
        }
    }

    private fun ensureIdx(extra: Int) {
        val needed = idxCount + extra
        if (needed > idx.size) {
            idx = idx.copyOf(maxOf(idx.size * 2, needed))
        }
    }

    fun addVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        u: Float, v: Float,
        r: Float, g: Float, b: Float
    ): Int {
        ensureVerts(1)
        val o = vertCount * 11
        verts[o] = x
        verts[o + 1] = y
        verts[o + 2] = z
        verts[o + 3] = nx
        verts[o + 4] = ny
        verts[o + 5] = nz
        verts[o + 6] = u
        verts[o + 7] = v
        verts[o + 8] = r
        verts[o + 9] = g
        verts[o + 10] = b
        return vertCount++
    }

    fun addQuad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float,
        uScale: Float = 1f,
        vScale: Float = 1f
    ) {
        val ux = x1 - x0
        val uy = y1 - y0
        val uz = z1 - z0
        val vx = x3 - x0
        val vy = y3 - y0
        val vz = z3 - z0
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        nx /= len
        ny /= len
        nz /= len

        val i0 = addVertex(x0, y0, z0, nx, ny, nz, 0f, 0f, r, g, b)
        val i1 = addVertex(x1, y1, z1, nx, ny, nz, uScale, 0f, r, g, b)
        val i2 = addVertex(x2, y2, z2, nx, ny, nz, uScale, vScale, r, g, b)
        val i3 = addVertex(x3, y3, z3, nx, ny, nz, 0f, vScale, r, g, b)

        ensureIdx(6)
        idx[idxCount++] = i0
        idx[idxCount++] = i1
        idx[idxCount++] = i2
        idx[idxCount++] = i0
        idx[idxCount++] = i2
        idx[idxCount++] = i3
    }

    fun addBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        uvTileX: Float = 1f,
        uvTileY: Float = 1f,
        includeBottom: Boolean = false
    ) {
        val hx = sx * 0.5f
        val hy = sy * 0.5f
        val hz = sz * 0.5f
        val x0 = cx - hx
        val x1 = cx + hx
        val y0 = cy - hy
        val y1 = cy + hy
        val z0 = cz - hz
        val z1 = cz + hz

        // Top (+Y)
        addQuad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, uvTileX, uvTileY)
        // Front (+Z)
        addQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, uvTileX, uvTileY)
        // Back (-Z)
        addQuad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, uvTileX, uvTileY)
        // Right (+X)
        addQuad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, uvTileX, uvTileY)
        // Left (-X)
        addQuad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, uvTileX, uvTileY)
        if (includeBottom) {
            addQuad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, uvTileX, uvTileY)
        }
    }

    fun addTrapezoidPrism(
        cx: Float, bottomY: Float, topY: Float, cz: Float,
        bottomW: Float, bottomL: Float,
        topW: Float, topL: Float,
        topZOffset: Float,
        r: Float, g: Float, b: Float
    ) {
        val bx0 = cx - bottomW * 0.5f
        val bx1 = cx + bottomW * 0.5f
        val bz0 = cz - bottomL * 0.5f
        val bz1 = cz + bottomL * 0.5f

        val tx0 = cx - topW * 0.5f
        val tx1 = cx + topW * 0.5f
        val tz0 = cz + topZOffset - topL * 0.5f
        val tz1 = cz + topZOffset + topL * 0.5f

        // Top
        addQuad(tx0, topY, tz1, tx1, topY, tz1, tx1, topY, tz0, tx0, topY, tz0, r, g, b)
        // Front (+Z)
        addQuad(bx0, bottomY, bz1, bx1, bottomY, bz1, tx1, topY, tz1, tx0, topY, tz1, r, g, b)
        // Back (-Z)
        addQuad(bx1, bottomY, bz0, bx0, bottomY, bz0, tx0, topY, tz0, tx1, topY, tz0, r, g, b)
        // Right (+X)
        addQuad(bx1, bottomY, bz1, bx1, bottomY, bz0, tx1, topY, tz0, tx1, topY, tz1, r, g, b)
        // Left (-X)
        addQuad(bx0, bottomY, bz0, bx0, bottomY, bz1, tx0, topY, tz1, tx0, topY, tz0, r, g, b)
    }

    fun addCylinderY(
        cx: Float, cy: Float, cz: Float,
        radiusBottom: Float, radiusTop: Float, height: Float,
        segments: Int,
        r: Float, g: Float, b: Float
    ) {
        val y0 = cy - height * 0.5f
        val y1 = cy + height * 0.5f
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0)
            val s0 = sin(a0)
            val c1 = cos(a1)
            val s1 = sin(a1)
            addQuad(
                cx + c0 * radiusBottom, y0, cz + s0 * radiusBottom,
                cx + c1 * radiusBottom, y0, cz + s1 * radiusBottom,
                cx + c1 * radiusTop, y1, cz + s1 * radiusTop,
                cx + c0 * radiusTop, y1, cz + s0 * radiusTop,
                r, g, b,
                uScale = 1f / segments,
                vScale = 1f
            )
        }
    }

    fun addWheelXAxis(
        radius: Float,
        width: Float,
        segments: Int,
        tireR: Float, tireG: Float, tireB: Float,
        rimR: Float, rimG: Float, rimB: Float
    ) {
        val hw = width * 0.5f
        val rimRad = radius * 0.68f
        // Tire tread barrel along X axis
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val y0 = sin(a0) * radius
            val z0 = cos(a0) * radius
            val y1 = sin(a1) * radius
            val z1 = cos(a1) * radius
            // Alternate slight brightness on tread blocks so rolling motion pops visually
            val shade = if (i % 2 == 0) 1.0f else 0.72f
            addQuad(
                -hw, y0, z0,
                hw, y0, z0,
                hw, y1, z1,
                -hw, y1, z1,
                tireR * shade, tireG * shade, tireB * shade,
                uScale = 1f, vScale = 0.5f
            )
            // Outer & inner sidewalls
            val iy0 = sin(a0) * rimRad
            val iz0 = cos(a0) * rimRad
            val iy1 = sin(a1) * rimRad
            val iz1 = cos(a1) * rimRad
            addQuad(hw, iy0, iz0, hw, y0, z0, hw, y1, z1, hw, iy1, iz1, tireR, tireG, tireB)
            addQuad(-hw, y0, z0, -hw, iy0, iz0, -hw, iy1, iz1, -hw, y1, z1, tireR, tireG, tireB)
        }
        // High-contrast 5-spoke metallic alloy rim on both faces so wheel spin is unmistakable
        val spokeCount = 5
        for (s in 0 until spokeCount) {
            val angle = (2.0 * PI * s / spokeCount).toFloat()
            val aL = angle - 0.16f
            val aR = angle + 0.16f
            val yL = sin(aL) * rimRad
            val zL = cos(aL) * rimRad
            val yR = sin(aR) * rimRad
            val zR = cos(aR) * rimRad
            val cY = sin(angle) * (rimRad * 0.2f)
            val cZ = cos(angle) * (rimRad * 0.2f)
            // Bright spoke bar on +X face and -X face
            addQuad(hw + 0.01f, cY, cZ, hw + 0.01f, yL, zL, hw + 0.01f, yR, zR, hw + 0.01f, -cY, -cZ, rimR, rimG, rimB)
            addQuad(-hw - 0.01f, yR, zR, -hw - 0.01f, yL, zL, -hw - 0.01f, cY, cZ, -hw - 0.01f, -cY, -cZ, rimR, rimG, rimB)
        }
        // Crimson center lug cap
        addBox(0f, 0f, 0f, width + 0.04f, radius * 0.28f, radius * 0.28f, 0.92f, 0.12f, 0.18f)
    }

    fun build(): GlMesh {
        return GlMesh(
            verts.copyOf(vertCount * 11),
            idx.copyOf(idxCount)
        )
    }

    fun isEmpty(): Boolean = idxCount == 0
}

package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.random.Random

class ProceduralTextures {
    val asphaltTex: Int
    val buildingTex: Int
    val tunnelTex: Int
    val bridgeTex: Int
    val wheelTex: Int

    init {
        asphaltTex = uploadBitmap(createAsphaltBitmap())
        buildingTex = uploadBitmap(createBuildingBitmap())
        tunnelTex = uploadBitmap(createTunnelBitmap())
        bridgeTex = uploadBitmap(createBridgeBitmap())
        wheelTex = uploadBitmap(createWheelBitmap())
    }

    fun release() {
        val ids = intArrayOf(asphaltTex, buildingTex, tunnelTex, bridgeTex, wheelTex)
        GLES30.glDeleteTextures(ids.size, ids, 0)
    }

    private fun uploadBitmap(bitmap: Bitmap): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val id = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
        return id
    }

    private fun createAsphaltBitmap(): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(24, 27, 34))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(42)

        // Subtle longitudinal rubber tire-wear tracks in each of the 4 lanes
        paint.color = Color.rgb(18, 21, 27)
        for (laneCenter in floatArrayOf(size * 0.14f, size * 0.36f, size * 0.64f, size * 0.86f)) {
            canvas.drawRect(laneCenter - 26f, 0f, laneCenter - 8f, size.toFloat(), paint)
            canvas.drawRect(laneCenter + 8f, 0f, laneCenter + 26f, size.toFloat(), paint)
        }

        // Layered asphalt mineral aggregate + micro wet sheen patches
        for (i in 0 until 5200) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            val shade = rng.nextInt(16, 48)
            paint.color = Color.rgb(shade, shade + 2, shade + 6)
            val s = 1.6f + rng.nextFloat() * 2.2f
            canvas.drawRect(x, y, x + s, y + s, paint)
        }

        // Subtle asphalt repair tar seams
        paint.color = Color.rgb(12, 14, 19)
        paint.strokeWidth = 2.0f
        canvas.drawLine(36f, 118f, 195f, 142f, paint)
        canvas.drawLine(312f, 340f, 470f, 326f, paint)

        // Outer solid reflective white-silver shoulder stripes
        paint.color = Color.rgb(198, 208, 224)
        canvas.drawRect(18f, 0f, 27f, size.toFloat(), paint)
        canvas.drawRect(size - 27f, 0f, size - 18f, size.toFloat(), paint)

        // Crisp double yellow center median divider
        paint.color = Color.rgb(242, 182, 34)
        canvas.drawRect(size * 0.5f - 10f, 0f, size * 0.5f - 3f, size.toFloat(), paint)
        canvas.drawRect(size * 0.5f + 3f, 0f, size * 0.5f + 10f, size.toFloat(), paint)

        // Dashed white lane dividers + reflector cat's-eye studs
        for (y in 20 until size step 128) {
            paint.color = Color.rgb(228, 235, 246)
            canvas.drawRect(size * 0.25f - 3.5f, y.toFloat(), size * 0.25f + 3.5f, (y + 66).toFloat(), paint)
            canvas.drawRect(size * 0.75f - 3.5f, y.toFloat(), size * 0.75f + 3.5f, (y + 66).toFloat(), paint)
            // Subtle cyan/amber road studs
            paint.color = Color.rgb(120, 232, 255)
            canvas.drawRect(size * 0.25f - 4f, (y + 88).toFloat(), size * 0.25f + 4f, (y + 96).toFloat(), paint)
            canvas.drawRect(size * 0.75f - 4f, (y + 88).toFloat(), size * 0.75f + 4f, (y + 96).toFloat(), paint)
        }
        return bmp
    }

    private fun createBuildingBitmap(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(16, 20, 30))

        val paint = Paint()
        val rng = Random(1337)
        val cols = 8
        val rows = 12
        val cellW = size / cols.toFloat()
        val cellH = size / rows.toFloat()

        // Horizontal metallic mullions
        paint.color = Color.rgb(34, 42, 58)
        for (r in 0..rows) {
            canvas.drawRect(0f, r * cellH, size.toFloat(), r * cellH + 2.5f, paint)
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val litChance = rng.nextFloat()
                paint.color = when {
                    litChance < 0.14f -> Color.rgb(255, 210, 120) // warm office
                    litChance < 0.24f -> Color.rgb(95, 225, 255)  // cyan neon office
                    litChance < 0.30f -> Color.rgb(255, 70, 90)   // crimson accent suite
                    litChance < 0.42f -> Color.rgb(190, 215, 245) // cool white lit
                    else -> Color.rgb(22, 30, 46)                 // dark midnight glass
                }
                val padX = 4f
                val padY = 4f
                canvas.drawRect(
                    c * cellW + padX,
                    r * cellH + padY,
                    (c + 1) * cellW - padX,
                    (r + 1) * cellH - padY,
                    paint
                )
            }
        }
        return bmp
    }

    private fun createTunnelBitmap(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(28, 32, 42))

        val paint = Paint()
        // Ribbed concrete panels
        paint.color = Color.rgb(42, 48, 60)
        for (x in 0 until size step 32) {
            canvas.drawRect(x.toFloat(), 0f, (x + 6).toFloat(), size.toFloat(), paint)
        }
        // Horizontal glowing safety strips (crimson & cyan)
        paint.color = Color.rgb(245, 35, 50)
        canvas.drawRect(0f, 52f, size.toFloat(), 64f, paint)
        paint.color = Color.rgb(0, 229, 255)
        canvas.drawRect(0f, 188f, size.toFloat(), 200f, paint)
        return bmp
    }

    private fun createBridgeBitmap(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(34, 38, 48))

        val paint = Paint()
        // Expansion joints
        paint.color = Color.rgb(55, 62, 76)
        for (y in 0 until size step 64) {
            canvas.drawRect(0f, y.toFloat(), size.toFloat(), (y + 4).toFloat(), paint)
        }
        // Bright cyan edge guide strips
        paint.color = Color.rgb(0, 215, 245)
        canvas.drawRect(8f, 0f, 14f, size.toFloat(), paint)
        canvas.drawRect(size - 14f, 0f, size - 8f, size.toFloat(), paint)
        // Dashed red-white center lane
        paint.color = Color.rgb(240, 244, 250)
        for (y in 8 until size step 48) {
            canvas.drawRect(size * 0.5f - 3f, y.toFloat(), size * 0.5f + 3f, (y + 24).toFloat(), paint)
        }
        return bmp
    }

    private fun createWheelBitmap(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(36, 39, 46))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Diagonal tire tread ribs so rolling rotation is highly visible
        paint.color = Color.rgb(66, 72, 84)
        paint.strokeWidth = 6f
        for (i in -size until size * 2 step 20) {
            canvas.drawLine(i.toFloat(), 0f, (i + 32).toFloat(), size.toFloat(), paint)
        }
        return bmp
    }
}

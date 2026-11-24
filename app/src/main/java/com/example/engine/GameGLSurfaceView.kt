package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.example.data.GameScreen

@SuppressLint("ViewConstructor")
class GameGLSurfaceView(
    context: Context,
    private val gameRenderer: GameRenderer
) : GLSurfaceView(context) {

    private var lastTouchX: Float = 0f

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(gameRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Allow 360-degree horizontal turntable rotation in Garage mode
        if (gameRenderer.physics.screenMode == GameScreen.GARAGE) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    lastTouchX = event.x
                    gameRenderer.physics.garageUserOrbitRad -= dx * 0.0085f
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

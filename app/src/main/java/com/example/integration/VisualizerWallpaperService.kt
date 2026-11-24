package com.example.integration

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.AAVisualizerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class VisualizerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VisualizerWallpaperEngine()

    inner class VisualizerWallpaperEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var renderJob: Job? = null
        private var phase = 0f

        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startLoop() else stopLoop()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            stopLoop()
            super.onSurfaceDestroyed(holder)
        }

        private fun startLoop() {
            if (renderJob != null) return
            renderJob = scope.launch {
                val app = applicationContext as? AAVisualizerApp
                while (isActive) {
                    val holder = surfaceHolder
                    val canvas: Canvas? = runCatching { holder.lockCanvas() }.getOrNull()
                    if (canvas != null) {
                        try {
                            drawFrame(canvas, app)
                        } finally {
                            runCatching { holder.unlockCanvasAndPost(canvas) }
                        }
                    }
                    phase += 0.045f
                    delay(24)
                }
            }
        }

        private fun stopLoop() {
            renderJob?.cancel()
            renderJob = null
        }

        private fun drawFrame(canvas: Canvas, app: AAVisualizerApp?) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawColor(Color.rgb(7, 9, 17))

            val frame = app?.container?.analysisEngine?.analysisFrame?.value
            val spectrum = frame?.spectrum
            val bass = frame?.bass ?: 0.15f
            val cx = w / 2f
            val cy = h / 2f
            val baseR = min(w, h) * (0.22f + bass * 0.08f)

            ringPaint.color = Color.argb(180, 0, 229, 255)
            canvas.drawCircle(cx, cy, baseR * 0.88f, ringPaint)

            val bars = 48
            for (i in 0 until bars) {
                val angle = (i.toFloat() / bars) * 2f * PI.toFloat() + phase * 0.35f
                val mag = spectrum?.getOrNull(i % spectrum.size)
                    ?: (0.12f + 0.12f * sin(phase * 2f + i * 0.4f).let { kotlin.math.abs(it) })
                val len = baseR * 0.7f * mag + 18f
                val x1 = cx + cos(angle) * baseR
                val y1 = cy + sin(angle) * baseR
                val x2 = cx + cos(angle) * (baseR + len)
                val y2 = cy + sin(angle) * (baseR + len)
                barPaint.color = if (i % 2 == 0) Color.rgb(0, 229, 255) else Color.rgb(124, 77, 255)
                barPaint.strokeWidth = 6f
                canvas.drawLine(x1, y1, x2, y2, barPaint)
            }
        }
    }
}

package com.example.ui.visualizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.model.AudioAnalysisFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawSacredMandala(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = min(size.width, size.height) * 0.44f
    val rings = 5
    for (ring in 1..rings) {
        val sides = 5 + ring
        val rot = phase * (if (ring % 2 == 0) 0.5f else -0.4f)
        val band = spec[(ring * 10).coerceIn(0, spec.lastIndex)] * intensity
        val radius = maxR * (ring / rings.toFloat()) * (0.78f + band * 0.35f + beat * 0.08f)
        val path = Path()
        for (s in 0 until sides) {
            val a = rot + (s.toFloat() / sides) * 2f * PI.toFloat()
            val p = Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
            if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(
            path,
            color = lerp(palette.primary, palette.secondary, ring / rings.toFloat()),
            style = Stroke(width = 2.5f + band * 3f)
        )
    }
}

internal fun DrawScope.drawKaleidoscope(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    rms: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = min(size.width, size.height) * 0.45f
    val petals = 12
    for (i in 0 until petals) {
        val a = (i.toFloat() / petals) * 2f * PI.toFloat() + phase * 0.5f
        val mag = spec[(i * 4) % spec.size] * intensity
        val r = maxR * (0.3f + mag * 0.65f + rms * 0.15f)
        val spread = 0.22f + mag * 0.18f
        val p1 = Offset(center.x + cos(a - spread) * r * 0.55f, center.y + sin(a - spread) * r * 0.55f)
        val pTip = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        val p2 = Offset(center.x + cos(a + spread) * r * 0.55f, center.y + sin(a + spread) * r * 0.55f)
        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(p1.x, p1.y)
            lineTo(pTip.x, pTip.y)
            lineTo(p2.x, p2.y)
            close()
        }
        val c = lerp(palette.primary, palette.tertiary, i.toFloat() / petals)
        drawPath(path, color = c.copy(alpha = 0.35f))
        drawPath(path, color = c, style = Stroke(width = 2f))
    }
}

internal fun DrawScope.drawHexMatrix(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val cols = 9
    val rows = 6
    val cellW = size.width / (cols + 0.5f)
    val cellH = size.height / (rows + 0.5f)
    val r = min(cellW, cellH) * 0.44f
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val offsetX = if (row % 2 == 1) cellW * 0.5f else 0f
            val cx = cellW * (col + 0.5f) + offsetX
            val cy = cellH * (row + 0.65f)
            val idx = ((row * cols + col) * spec.size) / (rows * cols)
            val mag = (spec[idx.coerceIn(0, spec.lastIndex)] * intensity).coerceIn(0.08f, 1f)
            val hexPath = Path()
            for (k in 0 until 6) {
                val ang = phase * 0.2f + (k * PI / 3.0).toFloat()
                val px = cx + cos(ang) * r * (0.45f + mag * 0.55f)
                val py = cy + sin(ang) * r * (0.45f + mag * 0.55f)
                if (k == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
            }
            hexPath.close()
            val c = lerp(palette.secondary, palette.primary, mag)
            drawPath(hexPath, color = c.copy(alpha = 0.18f + mag * 0.65f))
            drawPath(hexPath, color = c, style = Stroke(width = 2f))
        }
    }
}

internal fun DrawScope.drawPsychedelicPlasma(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    bass: Float
) {
    val blobs = 6
    val maxR = min(size.width, size.height) * 0.45f
    for (i in 0 until blobs) {
        val a = phase * (0.6f + i * 0.15f) + i * 1.1f
        val cx = size.width * 0.5f + cos(a) * size.width * 0.22f
        val cy = size.height * 0.5f + sin(a * 1.3f) * size.height * 0.22f
        val mag = spec[(i * 9) % spec.size] * intensity
        val r = maxR * (0.45f + mag * 0.5f + bass * 0.25f)
        val c = when (i % 3) {
            0 -> palette.primary
            1 -> palette.secondary
            else -> palette.tertiary
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )
    }
}

internal fun DrawScope.drawLavaAurora(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    rms: Float
) {
    val bands = 5
    for (b in 0 until bands) {
        val path = Path()
        val yCenter = size.height * (0.25f + b * 0.13f)
        path.moveTo(0f, size.height)
        for (i in 0..32) {
            val t = i / 32f
            val x = t * size.width
            val mag = spec[(b * 10 + i / 3) % spec.size] * intensity
            val y = yCenter + sin(t * 5f + phase * (0.8f + b * 0.2f)) * (35f + mag * 75f + rms * 25f)
            path.lineTo(x, y)
        }
        path.lineTo(size.width, size.height)
        path.close()
        val c = lerp(palette.primary, palette.tertiary, b / bands.toFloat())
        drawPath(
            path,
            brush = Brush.verticalGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent))
        )
    }
}

internal fun DrawScope.drawHypnoTunnel(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = min(size.width, size.height) * 0.52f
    val rings = 14
    for (i in 0 until rings) {
        val prog = ((i / rings.toFloat()) + (phase * 0.25f)) % 1f
        val r = prog * maxR
        val warp = spec[(i * 4) % spec.size] * intensity * 12f
        val offset = Offset(
            center.x + cos(phase + i * 0.4f) * (10f + beat * 14f),
            center.y + sin(phase * 1.2f + i * 0.4f) * (10f + beat * 14f)
        )
        drawCircle(
            color = lerp(palette.secondary, palette.primary, prog).copy(alpha = (1f - prog) * 0.9f),
            radius = (r + warp).coerceAtLeast(2f),
            center = offset,
            style = Stroke(width = 2.5f + prog * 4f)
        )
    }
}

internal fun DrawScope.drawWinampPeak(
    spec: FloatArray,
    peaks: FloatArray,
    palette: VisualizerPalette,
    intensity: Float
) {
    val cols = 24
    val segs = 16
    val gapX = size.width * 0.01f
    val colW = (size.width - gapX * (cols + 1)) / cols
    val gapY = 3f
    val totalH = size.height * 0.86f
    val segH = (totalH - gapY * segs) / segs
    val baseY = size.height * 0.93f

    for (c in 0 until cols) {
        val bin = (c * spec.size) / cols
        val level = ((spec[bin] * intensity).coerceIn(0f, 1f) * segs).toInt()
        val peakSeg = ((peaks[bin].coerceIn(0f, 1f)) * (segs - 1)).toInt()
        val x = gapX + c * (colW + gapX)
        for (s in 0 until segs) {
            val y = baseY - (s + 1) * (segH + gapY)
            val segColor = when {
                s >= 13 -> Color(0xFFFF3D00)
                s >= 9 -> Color(0xFFFFC400)
                else -> Color(0xFF00E676)
            }
            val active = s < level || s == peakSeg
            drawRect(
                color = if (active) segColor else segColor.copy(alpha = 0.1f),
                topLeft = Offset(x, y),
                size = Size(colW, segH)
            )
        }
    }
}

internal fun DrawScope.drawCrtPhosphor(
    waveform: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val scale = min(size.width, size.height) * 0.42f * intensity
    val path = Path()
    val half = waveform.size / 2
    for (i in 0 until half) {
        val xSample = waveform[i]
        val ySample = waveform[i + half]
        val rotX = xSample * cos(phase * 0.4f) - ySample * sin(phase * 0.4f)
        val rotY = xSample * sin(phase * 0.4f) + ySample * cos(phase * 0.4f)
        val px = center.x + rotX * scale
        val py = center.y + rotY * scale
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, color = Color(0xFF00FF66).copy(alpha = 0.35f), style = Stroke(width = 8f))
    drawPath(path, color = Color(0xFFB9F6CA), style = Stroke(width = 2.5f))
    // Scanline vignette
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += 8f
    }
}

internal fun DrawScope.drawPixelMatrix(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float
) {
    val cols = 16
    val rows = 12
    val cellW = size.width / cols
    val cellH = size.height / rows
    val radius = min(cellW, cellH) * 0.32f
    for (c in 0 until cols) {
        val mag = (spec[(c * spec.size) / cols] * intensity).coerceIn(0f, 1f)
        val litRows = (mag * rows).toInt()
        for (r in 0 until rows) {
            val rowFromBottom = rows - 1 - r
            val lit = rowFromBottom < litRows
            val cx = (c + 0.5f) * cellW
            val cy = (r + 0.5f) * cellH
            val color = lerp(palette.primary, palette.secondary, rowFromBottom / rows.toFloat())
            drawCircle(
                color = if (lit) color else color.copy(alpha = 0.1f),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
    }
}

internal fun DrawScope.drawSolarHalo(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    bass: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val dim = min(size.width, size.height)
    val baseR = dim * (0.2f + bass * 0.12f * intensity + beat * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(palette.primary.copy(alpha = 0.6f), palette.secondary.copy(alpha = 0.25f), Color.Transparent),
            center = center,
            radius = baseR * 1.9f
        ),
        radius = baseR * 1.9f,
        center = center
    )
    drawCircle(
        color = palette.highlight.copy(alpha = 0.85f),
        radius = baseR,
        center = center,
        style = Stroke(width = 4f + beat * 6f)
    )
    drawCircle(
        color = palette.secondary.copy(alpha = 0.55f),
        radius = baseR * (1.25f + 0.08f * sin(phase * 2f)),
        center = center,
        style = Stroke(width = 2.5f)
    )
}

internal fun DrawScope.drawOrbitalGyro(
    frame: AudioAnalysisFrame,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val dim = min(size.width, size.height) * 0.38f
    val bands = floatArrayOf(frame.bass, frame.mid, frame.treble)
    val colors = arrayOf(palette.primary, palette.secondary, palette.tertiary)
    for (ring in 0 until 3) {
        val rx = dim * (0.65f + ring * 0.2f + bands[ring] * 0.2f * intensity)
        val ry = rx * (0.35f + 0.3f * kotlin.math.abs(sin(phase * (0.7f + ring * 0.35f))))
        val rot = phase * (0.8f - ring * 0.4f)
        val path = Path()
        for (i in 0..48) {
            val t = (i / 48f) * 2f * PI.toFloat()
            val lx = cos(t) * rx
            val ly = sin(t) * ry
            val px = center.x + lx * cos(rot) - ly * sin(rot)
            val py = center.y + lx * sin(rot) + ly * cos(rot)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = colors[ring], style = Stroke(width = 3.5f + bands[ring] * 3f))
    }
}

internal fun DrawScope.drawAutoMinimal(
    frame: AudioAnalysisFrame,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val midY = size.height * 0.55f
    val maxAmp = size.height * 0.25f * intensity
    val path = Path()
    for (i in 0..48) {
        val t = i / 48f
        val x = t * size.width
        val env = sin(t * PI.toFloat())
        val specVal = frame.spectrum[(t * 63).toInt().coerceIn(0, 63)]
        val y = midY - specVal * maxAmp * env
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path,
        brush = Brush.horizontalGradient(listOf(palette.primary, palette.highlight, palette.secondary)),
        style = Stroke(width = 5f + frame.beatIntensity * 3f, cap = StrokeCap.Round)
    )
    drawCircle(
        color = palette.primary.copy(alpha = 0.22f + frame.bass * 0.35f),
        radius = min(size.width, size.height) * (0.14f + frame.beatIntensity * 0.05f),
        center = Offset(size.width * 0.5f, midY)
    )
}

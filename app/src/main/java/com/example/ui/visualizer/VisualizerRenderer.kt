package com.example.ui.visualizer

import androidx.compose.ui.geometry.CornerRadius
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
import com.example.model.VisualizerColorScheme
import com.example.model.VisualizerConfig
import com.example.model.VisualizerStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class VisualizerPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val highlight: Color
)

fun resolveVisualizerPalette(
    config: VisualizerConfig,
    albumPrimary: Color? = null,
    albumSecondary: Color? = null
): VisualizerPalette {
    val base = when (config.colorScheme) {
        VisualizerColorScheme.CYBER_CYAN -> VisualizerPalette(
            Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFF00E676), Color(0xFFFFFFFF)
        )
        VisualizerColorScheme.NEON_VIOLET -> VisualizerPalette(
            Color(0xFFB388FF), Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFFFFFFFF)
        )
        VisualizerColorScheme.SUNSET_EMBER -> VisualizerPalette(
            Color(0xFFFF6E40), Color(0xFFFFC400), Color(0xFFFF1744), Color(0xFFFFF8E1)
        )
        VisualizerColorScheme.EMERALD_MATRIX -> VisualizerPalette(
            Color(0xFF00E676), Color(0xFF1DE9B6), Color(0xFF76FF03), Color(0xFFE0FFF4)
        )
        VisualizerColorScheme.RAINBOW_PRISM -> VisualizerPalette(
            Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFFFFC400), Color(0xFFFFFFFF)
        )
        VisualizerColorScheme.MONOCHROME_ICE -> VisualizerPalette(
            Color(0xFFE3F2FD), Color(0xFF90CAF9), Color(0xFF42A5F5), Color(0xFFFFFFFF)
        )
        VisualizerColorScheme.ALBUM_ART -> VisualizerPalette(
            albumPrimary ?: Color(config.customAccentHex),
            albumSecondary ?: Color(0xFF7C4DFF),
            Color(0xFF00E5FF),
            Color(0xFFFFFFFF)
        )
    }
    val b = config.brightness.coerceIn(0.2f, 1.0f)
    val a = config.transparency.coerceIn(0.2f, 1.0f)
    return VisualizerPalette(
        primary = base.primary.copy(alpha = a) * b,
        secondary = base.secondary.copy(alpha = a) * b,
        tertiary = base.tertiary.copy(alpha = a) * b,
        highlight = base.highlight.copy(alpha = a) * b
    )
}

private operator fun Color.times(factor: Float): Color =
    Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha
    )

fun DrawScope.renderVisualizerFrame(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    phase: Float,
    peaks: FloatArray,
    albumPrimary: Color? = null,
    albumSecondary: Color? = null
) {
    val palette = resolveVisualizerPalette(config, albumPrimary, albumSecondary)
    val intensity = config.intensity.coerceIn(0.3f, 2.0f)
    val spec = frame.spectrum

    // Update peak-hold array
    for (i in 0 until min(spec.size, peaks.size)) {
        val v = (spec[i] * intensity).coerceIn(0f, 1f)
        if (v >= peaks[i]) {
            peaks[i] = v
        } else {
            peaks[i] = (peaks[i] - 0.012f).coerceAtLeast(v)
        }
    }

    when (config.activeStyle) {
        VisualizerStyle.CLASSIC_BARS -> drawClassicBars(spec, peaks, palette, intensity)
        VisualizerStyle.MIRROR_SPECTRUM -> drawMirrorSpectrum(spec, palette, intensity, frame.beatIntensity)
        VisualizerStyle.LIQUID_CONTOUR -> drawLiquidContour(spec, palette, intensity, phase)
        VisualizerStyle.CIRCULAR_SPECTRUM -> drawCircularSpectrum(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.DUAL_RING -> drawDualRing(spec, palette, intensity, phase, frame.bass, frame.treble)
        VisualizerStyle.RADIAL_SUNBURST -> drawRadialSunburst(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.OSCILLOSCOPE -> drawOscilloscope(frame.waveform, palette, intensity, frame.beatIntensity)
        VisualizerStyle.RIBBON_FLOW -> drawRibbonFlow(spec, frame.waveform, palette, intensity, phase)
        VisualizerStyle.TERRAIN_GRID -> drawTerrainGrid(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.PARTICLE_CONSTELLATION -> drawParticleConstellation(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.STARFIELD_WARP -> drawStarfieldWarp(spec, palette, intensity, phase, frame.bass, frame.beatIntensity)
        VisualizerStyle.EMBER_VORTEX -> drawEmberVortex(spec, palette, intensity, phase, frame.mid)
        VisualizerStyle.SACRED_MANDALA -> drawSacredMandala(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.KALEIDOSCOPE -> drawKaleidoscope(spec, palette, intensity, phase, frame.rms)
        VisualizerStyle.HEX_MATRIX -> drawHexMatrix(spec, palette, intensity, phase)
        VisualizerStyle.PSYCHEDELIC_PLASMA -> drawPsychedelicPlasma(spec, palette, intensity, phase, frame.bass)
        VisualizerStyle.LAVA_AURORA -> drawLavaAurora(spec, palette, intensity, phase, frame.rms)
        VisualizerStyle.HYPNO_TUNNEL -> drawHypnoTunnel(spec, palette, intensity, phase, frame.beatIntensity)
        VisualizerStyle.WINAMP_PEAK -> drawWinampPeak(spec, peaks, palette, intensity)
        VisualizerStyle.CRT_PHOSPHOR -> drawCrtPhosphor(frame.waveform, palette, intensity, phase)
        VisualizerStyle.PIXEL_MATRIX -> drawPixelMatrix(spec, palette, intensity)
        VisualizerStyle.SOLAR_HALO -> drawSolarHalo(spec, palette, intensity, phase, frame.bass, frame.beatIntensity)
        VisualizerStyle.ORBITAL_GYRO -> drawOrbitalGyro(frame, palette, intensity, phase)
        VisualizerStyle.AUTO_MINIMAL -> drawAutoMinimal(frame, palette, intensity, phase)
    }
}

internal fun bandColor(index: Int, total: Int, palette: VisualizerPalette, scheme: VisualizerColorScheme): Color {
    val t = (index.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    return if (scheme == VisualizerColorScheme.RAINBOW_PRISM) {
        Color.hsv((t * 320f) % 360f, 0.88f, 1f).copy(alpha = palette.primary.alpha)
    } else {
        if (t < 0.5f) lerp(palette.primary, palette.secondary, t * 2f)
        else lerp(palette.secondary, palette.tertiary, (t - 0.5f) * 2f)
    }
}

private fun DrawScope.drawClassicBars(
    spec: FloatArray,
    peaks: FloatArray,
    palette: VisualizerPalette,
    intensity: Float
) {
    val bars = 40
    val gap = size.width * 0.008f
    val barW = (size.width - gap * (bars + 1)) / bars
    val maxH = size.height * 0.86f
    val bottomY = size.height * 0.93f

    for (i in 0 until bars) {
        val bin = (i * spec.size) / bars
        val mag = (spec[bin] * intensity).coerceIn(0.02f, 1f)
        val barH = mag * maxH
        val x = gap + i * (barW + gap)
        val topY = bottomY - barH

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(palette.highlight, palette.primary, palette.secondary),
                startY = topY,
                endY = bottomY
            ),
            topLeft = Offset(x, topY),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW * 0.35f, barW * 0.35f)
        )

        val peakY = bottomY - (peaks[bin].coerceIn(0.02f, 1f) * maxH)
        drawRect(
            color = palette.highlight,
            topLeft = Offset(x, peakY - 3f),
            size = Size(barW, 3f)
        )
    }
}

private fun DrawScope.drawMirrorSpectrum(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    beat: Float
) {
    val bars = 44
    val midY = size.height / 2f
    val barW = size.width / (bars * 1.4f)
    val step = size.width / bars
    for (i in 0 until bars) {
        val idx = (i * spec.size) / bars
        val mag = (spec[idx] * intensity).coerceIn(0.03f, 1f)
        val halfH = mag * (size.height * 0.42f)
        val x = i * step + (step - barW) * 0.5f
        val c = lerp(palette.primary, palette.secondary, i.toFloat() / bars)
        drawRoundRect(
            color = c,
            topLeft = Offset(x, midY - halfH),
            size = Size(barW, halfH * 2f),
            cornerRadius = CornerRadius(barW * 0.5f)
        )
    }
    drawLine(
        color = palette.highlight.copy(alpha = 0.35f + beat * 0.45f),
        start = Offset(0f, midY),
        end = Offset(size.width, midY),
        strokeWidth = 2f + beat * 3f
    )
}

private fun DrawScope.drawLiquidContour(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val path = Path()
    val fillPath = Path()
    val count = 48
    val baseY = size.height * 0.85f
    path.moveTo(0f, baseY)
    fillPath.moveTo(0f, size.height)
    fillPath.lineTo(0f, baseY)

    var prevX = 0f
    var prevY = baseY
    for (i in 0 until count) {
        val x = (i.toFloat() / (count - 1)) * size.width
        val mag = (spec[(i * spec.size) / count] * intensity).coerceIn(0f, 1f)
        val wave = sin(phase * 2.5f + i * 0.25f) * 8f
        val y = (baseY - mag * size.height * 0.65f + wave).coerceIn(size.height * 0.08f, baseY)
        val cx = (prevX + x) * 0.5f
        path.cubicTo(cx, prevY, cx, y, x, y)
        fillPath.cubicTo(cx, prevY, cx, y, x, y)
        prevX = x
        prevY = y
    }
    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            listOf(palette.primary.copy(alpha = 0.48f), palette.secondary.copy(alpha = 0.08f))
        )
    )
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(listOf(palette.primary, palette.secondary, palette.tertiary)),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCircularSpectrum(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseR = min(size.width, size.height) * (0.22f + beat * 0.03f)
    val maxBar = min(size.width, size.height) * 0.24f
    val count = 64
    for (i in 0 until count) {
        val angle = (i.toFloat() / count) * 2f * PI.toFloat() + phase * 0.35f
        val mag = (spec[i % spec.size] * intensity).coerceIn(0.05f, 1f)
        val rOut = baseR + mag * maxBar
        val p1 = Offset(center.x + cos(angle) * baseR, center.y + sin(angle) * baseR)
        val p2 = Offset(center.x + cos(angle) * rOut, center.y + sin(angle) * rOut)
        val c = lerp(palette.primary, palette.secondary, i.toFloat() / count)
        drawLine(c, p1, p2, strokeWidth = 5f, cap = StrokeCap.Round)
    }
    drawCircle(
        color = palette.highlight.copy(alpha = 0.25f + beat * 0.4f),
        radius = baseR * 0.85f,
        center = center,
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawDualRing(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    bass: Float,
    treble: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val dim = min(size.width, size.height)
    val innerR = dim * (0.15f + bass * 0.05f)
    val outerR = dim * (0.31f + treble * 0.04f)
    val count = 48
    for (i in 0 until count) {
        val a1 = (i.toFloat() / count) * 2f * PI.toFloat() + phase * 0.6f
        val a2 = (i.toFloat() / count) * 2f * PI.toFloat() - phase * 0.45f
        val bassMag = (spec[i % 16] * intensity).coerceIn(0.04f, 1f) * dim * 0.11f
        val trebMag = (spec[32 + (i % 32)] * intensity).coerceIn(0.04f, 1f) * dim * 0.14f

        drawLine(
            color = palette.primary,
            start = Offset(center.x + cos(a1) * innerR, center.y + sin(a1) * innerR),
            end = Offset(center.x + cos(a1) * (innerR + bassMag), center.y + sin(a1) * (innerR + bassMag)),
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.secondary,
            start = Offset(center.x + cos(a2) * outerR, center.y + sin(a2) * outerR),
            end = Offset(center.x + cos(a2) * (outerR + trebMag), center.y + sin(a2) * (outerR + trebMag)),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRadialSunburst(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = min(size.width, size.height) * 0.47f
    val rays = 36
    drawCircle(
        brush = Brush.radialGradient(
            listOf(palette.primary.copy(alpha = 0.45f + beat * 0.4f), Color.Transparent),
            center = center,
            radius = maxR * 0.7f
        ),
        radius = maxR * 0.7f,
        center = center
    )
    for (i in 0 until rays) {
        val ang = (i.toFloat() / rays) * 2f * PI.toFloat() + phase * 0.25f
        val mag = (spec[(i * 2) % spec.size] * intensity).coerceIn(0.08f, 1f)
        val r = maxR * (0.25f + mag * 0.75f)
        drawLine(
            brush = Brush.linearGradient(
                listOf(palette.highlight, lerp(palette.primary, palette.tertiary, i.toFloat() / rays)),
                start = center,
                end = Offset(center.x + cos(ang) * r, center.y + sin(ang) * r)
            ),
            start = center,
            end = Offset(center.x + cos(ang) * r, center.y + sin(ang) * r),
            strokeWidth = 3f + mag * 5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOscilloscope(
    waveform: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    beat: Float
) {
    val midY = size.height / 2f
    val amp = size.height * 0.36f * intensity
    val pathMain = Path()
    val pathEcho = Path()
    for (i in waveform.indices) {
        val x = (i.toFloat() / (waveform.size - 1).coerceAtLeast(1)) * size.width
        val y = midY + waveform[i] * amp
        val yEcho = midY + waveform[i] * amp * 0.55f
        if (i == 0) {
            pathMain.moveTo(x, y)
            pathEcho.moveTo(x, yEcho)
        } else {
            pathMain.lineTo(x, y)
            pathEcho.lineTo(x, yEcho)
        }
    }
    drawPath(pathEcho, color = palette.secondary.copy(alpha = 0.5f), style = Stroke(width = 3f))
    drawPath(
        pathMain,
        brush = Brush.horizontalGradient(listOf(palette.primary, palette.highlight, palette.tertiary)),
        style = Stroke(width = 4.5f + beat * 2.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawRibbonFlow(
    spec: FloatArray,
    waveform: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float
) {
    val layers = 4
    val midY = size.height / 2f
    for (l in 0 until layers) {
        val path = Path()
        val bandEnergy = spec[(l * 14).coerceIn(0, spec.lastIndex)] * intensity
        val color = when (l) {
            0 -> palette.primary
            1 -> palette.secondary
            2 -> palette.tertiary
            else -> palette.highlight
        }.copy(alpha = 0.72f - l * 0.12f)

        for (i in 0..64) {
            val t = i / 64f
            val x = t * size.width
            val wf = waveform.getOrElse((t * (waveform.size - 1)).toInt()) { 0f }
            val env = sin(t * PI.toFloat())
            val y = midY + sin(t * 7f + phase * (1.5f + l * 0.4f) + l) * (size.height * 0.18f * (0.4f + bandEnergy)) * env +
                wf * size.height * 0.14f * intensity
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 4f - l * 0.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawTerrainGrid(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val horizonY = size.height * 0.36f
    val rows = 10
    val cols = 16
    for (r in 0 until rows) {
        val depth = (r.toFloat() / rows)
        val yBase = horizonY + depth * depth * (size.height - horizonY)
        val path = Path()
        for (c in 0..cols) {
            val nx = c.toFloat() / cols
            val perspectiveX = size.width * 0.5f + (nx - 0.5f) * size.width * (0.35f + depth * 0.9f)
            val mag = spec[((c + r * 3) % spec.size)] * intensity
            val elevation = mag * (45f * depth + 10f) * (1f + beat * 0.3f)
            val y = yBase - elevation
            if (c == 0) path.moveTo(perspectiveX, y) else path.lineTo(perspectiveX, y)
        }
        drawPath(
            path,
            color = lerp(palette.secondary, palette.primary, depth).copy(alpha = 0.4f + depth * 0.55f),
            style = Stroke(width = 2f + depth * 2f)
        )
    }
    // Sun disc
    val sunR = min(size.width, size.height) * (0.12f + beat * 0.02f)
    drawCircle(
        brush = Brush.verticalGradient(listOf(palette.tertiary, palette.secondary)),
        radius = sunR,
        center = Offset(size.width * 0.5f, horizonY - sunR * 0.35f + sin(phase) * 3f)
    )
}

private fun DrawScope.drawParticleConstellation(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    beat: Float
) {
    val count = 28
    val pts = Array(count) { i ->
        val mag = spec[(i * 2) % spec.size] * intensity
        val rx = 0.5f + 0.42f * cos(phase * 0.4f + i * 1.37f)
        val ry = 0.5f + 0.42f * sin(phase * 0.52f + i * 2.11f - mag * 0.6f)
        Offset(rx * size.width, ry * size.height)
    }
    val maxDist = min(size.width, size.height) * (0.28f + beat * 0.08f)
    for (i in 0 until count) {
        for (j in i + 1 until count) {
            val dx = pts[i].x - pts[j].x
            val dy = pts[i].y - pts[j].y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < maxDist) {
                val alpha = (1f - dist / maxDist) * 0.65f
                drawLine(palette.primary.copy(alpha = alpha), pts[i], pts[j], strokeWidth = 1.8f)
            }
        }
        val r = 4f + spec[(i * 2) % spec.size] * 10f * intensity
        drawCircle(lerp(palette.primary, palette.secondary, i.toFloat() / count), r, pts[i])
    }
}

private fun DrawScope.drawStarfieldWarp(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    bass: Float,
    beat: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 0.55f
    val stars = 48
    for (i in 0 until stars) {
        val angle = (i * 2.39996f) + phase * 0.12f
        val progress = ((phase * (0.45f + bass * 0.9f) + i / stars.toFloat()) % 1f)
        val r = progress * progress * maxR
        val rTail = ((progress - 0.08f - beat * 0.06f).coerceAtLeast(0f)).let { it * it * maxR }
        val p1 = Offset(center.x + cos(angle) * rTail, center.y + sin(angle) * rTail)
        val p2 = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
        val mag = spec[i % spec.size] * intensity
        drawLine(
            color = lerp(palette.secondary, palette.highlight, progress),
            start = p1,
            end = p2,
            strokeWidth = 1.5f + progress * 4f + mag * 3f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawEmberVortex(
    spec: FloatArray,
    palette: VisualizerPalette,
    intensity: Float,
    phase: Float,
    mid: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = min(size.width, size.height) * 0.45f
    val particles = 56
    for (i in 0 until particles) {
        val t = i.toFloat() / particles
        val angle = t * 14f + phase * (1.2f + mid * 1.4f)
        val mag = spec[i % spec.size] * intensity
        val r = maxR * (0.1f + t * 0.85f) + mag * 18f
        val pos = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
        drawCircle(
            color = lerp(palette.tertiary, palette.primary, t),
            radius = 3f + mag * 8f,
            center = pos
        )
    }
}

package com.example.audio

import com.example.model.AudioAnalysisFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class FftProcessor(private val fftSize: Int = 1024, private val sampleRate: Int = 44100) {
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5f * (1f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val prevMagnitudes = FloatArray(fftSize / 2)
    private val smoothedSpectrum = FloatArray(64)
    private val fluxHistory = FloatArray(24)
    private var fluxIndex = 0
    private var beatEnvelope = 0f
    private var lastBeatMs = 0L

    private val bandBinRanges: Array<IntRange> = Array(64) { band ->
        val minFreq = 40.0
        val maxFreq = 16500.0
        val fStart = minFreq * (maxFreq / minFreq).pow(band / 64.0)
        val fEnd = minFreq * (maxFreq / minFreq).pow((band + 1) / 64.0)
        val binHz = sampleRate.toDouble() / fftSize
        val startBin = (fStart / binHz).toInt().coerceIn(1, fftSize / 2 - 1)
        val endBin = (fEnd / binHz).toInt().coerceIn(startBin, fftSize / 2 - 1)
        startBin..endBin
    }

    fun processPcm16(
        pcm: ShortArray,
        length: Int,
        sensitivity: Float = 1.0f,
        frequencyTilt: Float = 0f
    ): AudioAnalysisFrame {
        val count = length.coerceAtMost(fftSize)
        var sumSq = 0.0
        var peak = 1e-6f

        for (i in 0 until fftSize) {
            val sample = if (i < count) pcm[i] / 32768f else 0f
            val absS = kotlin.math.abs(sample)
            if (absS > peak) peak = absS
            sumSq += sample * sample
            real[i] = sample * window[i]
            imag[i] = 0f
        }

        val rms = sqrt(sumSq / fftSize.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
        val peakDb = (20f * log10(peak.coerceAtLeast(1e-5f))).coerceIn(-60f, 0f)

        val waveform = FloatArray(128) { idx ->
            val srcIdx = (idx * count) / 128
            if (srcIdx < count) (pcm[srcIdx] / 32768f) else 0f
        }

        fftInPlace(real, imag)

        val half = fftSize / 2
        var spectralFlux = 0f
        val magnitudes = FloatArray(half)
        for (i in 1 until half) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]) / (fftSize * 0.25f)
            magnitudes[i] = mag
            val diff = mag - prevMagnitudes[i]
            if (diff > 0f) spectralFlux += diff
            prevMagnitudes[i] = mag
        }

        fluxHistory[fluxIndex] = spectralFlux
        fluxIndex = (fluxIndex + 1) % fluxHistory.size
        val avgFlux = fluxHistory.average().toFloat().coerceAtLeast(0.05f)
        val now = System.currentTimeMillis()
        val isBeat = spectralFlux > avgFlux * 1.48f && rms > 0.03f && (now - lastBeatMs > 170L)
        if (isBeat) {
            lastBeatMs = now
            beatEnvelope = 1f
        } else {
            beatEnvelope = (beatEnvelope * 0.86f).coerceAtLeast(0f)
        }

        val outSpectrum = FloatArray(64)
        for (b in 0 until 64) {
            var bandMax = 0f
            for (bin in bandBinRanges[b]) {
                if (magnitudes[bin] > bandMax) bandMax = magnitudes[bin]
            }
            val tiltFactor = 1f + frequencyTilt * ((b - 32f) / 32f) * 0.55f
            val scaled = (ln(1f + bandMax * 18f * sensitivity * tiltFactor) / ln(1f + 18f))
                .coerceIn(0f, 1.35f)
            val prev = smoothedSpectrum[b]
            val smooth = if (scaled > prev) prev + (scaled - prev) * 0.55f else prev * 0.84f + scaled * 0.16f
            smoothedSpectrum[b] = smooth
            outSpectrum[b] = smooth.coerceIn(0f, 1f)
        }

        val bass = averageRange(outSpectrum, 0, 12)
        val mid = averageRange(outSpectrum, 12, 40)
        val treble = averageRange(outSpectrum, 40, 64)

        return AudioAnalysisFrame(
            spectrum = outSpectrum,
            waveform = waveform,
            bass = bass,
            mid = mid,
            treble = treble,
            rms = (rms * 2.2f * sensitivity).coerceIn(0f, 1f),
            beatIntensity = beatEnvelope,
            isBeat = isBeat,
            peakDb = peakDb,
            timestampMs = now
        )
    }

    fun processFftBytes(
        fftBytes: ByteArray,
        waveformBytes: ByteArray?,
        sensitivity: Float = 1.0f,
        frequencyTilt: Float = 0f
    ): AudioAnalysisFrame {
        val n = fftBytes.size / 2
        val half = n.coerceAtLeast(2)
        var spectralFlux = 0f
        val magnitudes = FloatArray(half)
        for (k in 1 until half) {
            val re = fftBytes[2 * k].toInt()
            val im = fftBytes[2 * k + 1].toInt()
            val mag = sqrt((re * re + im * im).toFloat()) / 128f
            magnitudes[k] = mag
            val diff = mag - (prevMagnitudes.getOrElse(k) { 0f })
            if (diff > 0f) spectralFlux += diff
            if (k < prevMagnitudes.size) prevMagnitudes[k] = mag
        }

        val outSpectrum = FloatArray(64)
        for (b in 0 until 64) {
            val k = ((b / 63f).pow(1.45f) * (half - 2)).toInt().coerceIn(1, half - 1)
            val tiltFactor = 1f + frequencyTilt * ((b - 32f) / 32f) * 0.55f
            val valNorm = (ln(1f + magnitudes[k] * 14f * sensitivity * tiltFactor) / ln(1f + 14f))
                .coerceIn(0f, 1f)
            val prev = smoothedSpectrum[b]
            val smooth = if (valNorm > prev) prev + (valNorm - prev) * 0.5f else prev * 0.85f + valNorm * 0.15f
            smoothedSpectrum[b] = smooth
            outSpectrum[b] = smooth
        }

        val waveform = FloatArray(128) { idx ->
            if (waveformBytes != null && waveformBytes.isNotEmpty()) {
                val src = (idx * waveformBytes.size) / 128
                ((waveformBytes[src].toInt() and 0xFF) - 128) / 128f
            } else 0f
        }

        val rms = sqrt(waveform.map { it * it }.average()).toFloat().coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        fluxHistory[fluxIndex] = spectralFlux
        fluxIndex = (fluxIndex + 1) % fluxHistory.size
        val avgFlux = fluxHistory.average().toFloat().coerceAtLeast(0.05f)
        val isBeat = spectralFlux > avgFlux * 1.45f && (now - lastBeatMs > 170L)
        if (isBeat) {
            lastBeatMs = now
            beatEnvelope = 1f
        } else {
            beatEnvelope = (beatEnvelope * 0.86f).coerceAtLeast(0f)
        }

        return AudioAnalysisFrame(
            spectrum = outSpectrum,
            waveform = waveform,
            bass = averageRange(outSpectrum, 0, 12),
            mid = averageRange(outSpectrum, 12, 40),
            treble = averageRange(outSpectrum, 40, 64),
            rms = (rms * 2f * sensitivity).coerceIn(0f, 1f),
            beatIntensity = beatEnvelope,
            isBeat = isBeat,
            peakDb = (20f * log10(rms.coerceAtLeast(1e-4f))).coerceIn(-60f, 0f),
            timestampMs = now
        )
    }

    private fun averageRange(arr: FloatArray, start: Int, end: Int): Float {
        var sum = 0f
        val safeEnd = end.coerceAtMost(arr.size)
        val count = (safeEnd - start).coerceAtLeast(1)
        for (i in start until safeEnd) sum += arr[i]
        return (sum / count).coerceIn(0f, 1f)
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

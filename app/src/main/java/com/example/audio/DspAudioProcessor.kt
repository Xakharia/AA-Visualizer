package com.example.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.example.model.EQ_BANDS_10
import com.example.model.EqProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Manages hardware Android AudioEffects (DynamicsProcessing 10-band on API 28+ or Equalizer+BassBoost)
 * AND provides a deterministic stereo software biquad DSP chain for built-in PCM playback.
 */
class DspAudioProcessor {
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var legacyBassBoost: BassBoost? = null
    private var attachedSessionId: Int = -1

    private val _dspStatus = MutableStateFlow("Software 10-Band DSP Ready")
    val dspStatus: StateFlow<String> = _dspStatus.asStateFlow()

    @Volatile
    var activeProfile: EqProfile = EqProfile(name = "Default", category = com.example.model.ProfileCategory.MUSIC)
        private set

    @Volatile
    var isEnabled: Boolean = true
        private set

    private val softwareChain = SoftwareBiquadChain(44100)

    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == attachedSessionId) return
        releaseHardwareEffects()
        attachedSessionId = sessionId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10,
                    true, 3,
                    true, 10,
                    true
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                    enabled = isEnabled
                }
                _dspStatus.value = "DynamicsProcessing 10-Band (Session $sessionId)"
                applyProfileToHardware()
                return
            }.onFailure {
                Log.w("DspAudioProcessor", "DynamicsProcessing unavailable: ${it.message}")
            }
        }

        runCatching {
            legacyEqualizer = Equalizer(0, sessionId).apply { enabled = isEnabled }
            legacyBassBoost = BassBoost(0, sessionId).apply { enabled = isEnabled }
            _dspStatus.value = "Legacy Equalizer + BassBoost (Session $sessionId)"
            applyProfileToHardware()
        }.onFailure {
            _dspStatus.value = "Software 10-Band DSP Active"
        }
    }

    fun updateProfile(profile: EqProfile, enabled: Boolean) {
        activeProfile = profile
        isEnabled = enabled
        softwareChain.configure(profile, enabled)
        applyProfileToHardware()
    }

    fun processStereoPcm16(buffer: ShortArray, sampleCount: Int) {
        softwareChain.processInterleavedStereo(buffer, sampleCount)
    }

    private fun applyProfileToHardware() {
        val profile = activeProfile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                runCatching {
                    dp.enabled = isEnabled
                    dp.setInputGainAllChannelsTo(if (isEnabled) profile.masterGainDb else 0f)
                    for (i in 0 until 10) {
                        val centerFreq = EQ_BANDS_10[i].centerFreqHz.toFloat()
                        val cutoff = if (i < 9) {
                            (centerFreq + EQ_BANDS_10[i + 1].centerFreqHz) * 0.5f
                        } else 20000f
                        val extraBass = if (profile.smartBassEnabled && i <= 1) (profile.bassBoost / 250f) else 0f
                        val extraTreble = if (i >= 8) profile.trebleBoostDb * 0.5f else 0f
                        val gain = (profile.bandsDb.getOrElse(i) { 0f } + extraBass + extraTreble)
                            .coerceIn(-12f, 12f)
                        val band = DynamicsProcessing.EqBand(true, cutoff, gain)
                        dp.setPostEqBandAllChannelsTo(i, band)
                    }
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        profile.limiterEnabled,
                        0,
                        1f,
                        60f,
                        10f,
                        -0.5f,
                        0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                }
                return
            }
        }

        legacyEqualizer?.let { eq ->
            runCatching {
                eq.enabled = isEnabled
                val bands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (b in 0 until bands) {
                    val mappedIdx = ((b.toFloat() / bands.coerceAtLeast(1)) * 10).toInt().coerceIn(0, 9)
                    val db = profile.bandsDb.getOrElse(mappedIdx) { 0f }
                    val mb = (db * 100f).toInt().coerceIn(minMb, maxMb)
                    eq.setBandLevel(b.toShort(), mb.toShort())
                }
            }
        }
        legacyBassBoost?.let { bb ->
            runCatching {
                bb.enabled = isEnabled && (profile.bassBoost > 0 || profile.smartBassEnabled)
                val strength = if (profile.smartBassEnabled) {
                    (profile.bassBoost + 250).coerceIn(0, 1000)
                } else {
                    profile.bassBoost.coerceIn(0, 1000)
                }
                bb.setStrength(strength.toShort())
            }
        }
    }

    fun releaseHardwareEffects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dynamicsProcessing?.release() }
            dynamicsProcessing = null
        }
        runCatching { legacyEqualizer?.release() }
        runCatching { legacyBassBoost?.release() }
        legacyEqualizer = null
        legacyBassBoost = null
        attachedSessionId = -1
    }
}

private class SoftwareBiquadChain(private val sampleRate: Int) {
    private val filtersL = Array(10) { Biquad() }
    private val filtersR = Array(10) { Biquad() }

    @Volatile private var enabled = true
    @Volatile private var gainLinear = 1f
    @Volatile private var leftGain = 1f
    @Volatile private var rightGain = 1f
    @Volatile private var agcEnabled = false
    @Volatile private var limiterEnabled = true
    private var agcGain = 1f

    fun configure(profile: EqProfile, isEnabled: Boolean) {
        enabled = isEnabled
        gainLinear = 10f.pow((profile.masterGainDb.coerceIn(-12f, 12f)) / 20f)
        val bal = profile.balance.coerceIn(-1f, 1f)
        leftGain = if (bal > 0f) 1f - bal else 1f
        rightGain = if (bal < 0f) 1f + bal else 1f
        agcEnabled = profile.agcEnabled
        limiterEnabled = profile.limiterEnabled

        for (i in 0 until 10) {
            val freq = EQ_BANDS_10[i].centerFreqHz.toDouble()
            val smartBassDb = if (profile.smartBassEnabled && i <= 1) (profile.bassBoost / 220f) else (profile.bassBoost / 400f * (if (i <= 1) 1f else 0f))
            val trebleDb = if (i >= 8) profile.trebleBoostDb * 0.55f else 0f
            val db = (profile.bandsDb.getOrElse(i) { 0f } + smartBassDb + trebleDb).coerceIn(-15f, 15f)
            filtersL[i].setPeaking(sampleRate.toDouble(), freq, 1.25, db.toDouble())
            filtersR[i].setPeaking(sampleRate.toDouble(), freq, 1.25, db.toDouble())
        }
    }

    fun processInterleavedStereo(buffer: ShortArray, sampleCount: Int) {
        if (!enabled) return
        var i = 0
        while (i + 1 < sampleCount) {
            var l = buffer[i] / 32768f
            var r = buffer[i + 1] / 32768f

            for (b in 0 until 10) {
                l = filtersL[b].process(l)
                r = filtersR[b].process(r)
            }

            if (agcEnabled) {
                val level = sqrt((l * l + r * r) * 0.5f)
                val target = if (level > 0.01f) (0.28f / level).coerceIn(0.6f, 2.0f) else 1f
                agcGain += (target - agcGain) * 0.0008f
                l *= agcGain
                r *= agcGain
            }

            l *= gainLinear * leftGain
            r *= gainLinear * rightGain

            if (limiterEnabled) {
                l = tanh(l.toDouble()).toFloat()
                r = tanh(r.toDouble()).toFloat()
            } else {
                l = l.coerceIn(-1f, 1f)
                r = r.coerceIn(-1f, 1f)
            }

            buffer[i] = (l * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i + 1] = (r * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            i += 2
        }
    }

    private class Biquad {
        @Volatile private var b0 = 1f
        @Volatile private var b1 = 0f
        @Volatile private var b2 = 0f
        @Volatile private var a1 = 0f
        @Volatile private var a2 = 0f
        private var z1 = 0f
        private var z2 = 0f

        fun setPeaking(fs: Double, f0: Double, q: Double, gainDb: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * (f0 / fs).coerceIn(0.001, 0.48)
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)
            val a0 = 1.0 + alpha / a
            b0 = ((1.0 + alpha * a) / a0).toFloat()
            b1 = ((-2.0 * cosW0) / a0).toFloat()
            b2 = ((1.0 - alpha * a) / a0).toFloat()
            a1 = ((-2.0 * cosW0) / a0).toFloat()
            a2 = ((1.0 - alpha / a) / a0).toFloat()
        }

        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }
}

package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.AudioAnalysisFrame
import com.example.model.AudioInputSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioAnalysisEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fftProcessor = FftProcessor()

    private val _analysisFrame = MutableStateFlow(AudioAnalysisFrame())
    val analysisFrame: StateFlow<AudioAnalysisFrame> = _analysisFrame.asStateFlow()

    private val _activeSource = MutableStateFlow(AudioInputSource.BUILTIN_GROOVE)
    val activeSource: StateFlow<AudioInputSource> = _activeSource.asStateFlow()

    private val _statusText = MutableStateFlow("Built-in Synth Ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    @Volatile var sensitivity: Float = 1.1f
    @Volatile var frequencyTilt: Float = 0.0f

    private var captureJob: Job? = null
    private var visualizer: Visualizer? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null

    fun submitInternalPcm(pcm: ShortArray, length: Int) {
        if (_activeSource.value == AudioInputSource.BUILTIN_GROOVE) {
            _analysisFrame.value = fftProcessor.processPcm16(pcm, length, sensitivity, frequencyTilt)
        }
    }

    fun switchSource(
        source: AudioInputSource,
        externalSessionId: Int = 0,
        projection: MediaProjection? = null
    ) {
        stopExternalCapture()
        _activeSource.value = source

        when (source) {
            AudioInputSource.BUILTIN_GROOVE -> {
                _statusText.value = "Built-in PCM Engine Active"
            }
            AudioInputSource.EXTERNAL_SESSION -> {
                startSessionVisualizer(externalSessionId)
            }
            AudioInputSource.PLAYBACK_CAPTURE -> {
                if (projection != null) {
                    mediaProjection = projection
                    startPlaybackCapture(projection)
                } else {
                    _statusText.value = "Playback Capture requires session consent"
                }
            }
            AudioInputSource.MICROPHONE -> {
                startMicCapture()
            }
        }
    }

    private fun startSessionVisualizer(sessionId: Int) {
        if (!hasRecordAudioPermission()) {
            _statusText.value = "Grant RECORD_AUDIO permission to attach Visualizer"
            return
        }
        val targetSession = sessionId.coerceAtLeast(0)
        runCatching {
            val vis = Visualizer(targetSession).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                enabled = true
            }
            visualizer = vis
            _statusText.value = if (targetSession > 0) {
                "Visualizer attached to Session $targetSession"
            } else {
                "Visualizer attached to Output Mix (Session 0)"
            }
            captureJob = scope.launch {
                val fft = ByteArray(vis.captureSize)
                val wave = ByteArray(vis.captureSize)
                while (isActive) {
                    val r1 = vis.getFft(fft)
                    val r2 = vis.getWaveForm(wave)
                    if (r1 == Visualizer.SUCCESS) {
                        _analysisFrame.value = fftProcessor.processFftBytes(
                            fft,
                            if (r2 == Visualizer.SUCCESS) wave else null,
                            sensitivity,
                            frequencyTilt
                        )
                    }
                    delay(16)
                }
            }
        }.onFailure {
            Log.w("AudioAnalysisEngine", "Visualizer attach failed: ${it.message}")
            _statusText.value = "Session Visualizer unavailable on this device (${it.message})"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPlaybackCapture(projection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _statusText.value = "AudioPlaybackCapture requires Android 10+"
            return
        }
        if (!hasRecordAudioPermission()) {
            _statusText.value = "Grant RECORD_AUDIO permission for Playback Capture"
            return
        }

        runCatching {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val record = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            audioRecord = record
            record.startRecording()
            _statusText.value = "MediaProjection Playback Capture Active"

            captureJob = scope.launch(Dispatchers.IO) {
                val buf = ShortArray(1024)
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        _analysisFrame.value = fftProcessor.processPcm16(buf, read, sensitivity, frequencyTilt)
                    }
                }
            }
        }.onFailure {
            _statusText.value = "Playback Capture failed: ${it.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicCapture() {
        if (!hasRecordAudioPermission()) {
            _statusText.value = "Grant RECORD_AUDIO permission for Microphone mode"
            return
        }
        runCatching {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            audioRecord = record
            record.startRecording()
            _statusText.value = "Microphone Ambient Analysis Active"

            captureJob = scope.launch(Dispatchers.IO) {
                val buf = ShortArray(1024)
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        _analysisFrame.value = fftProcessor.processPcm16(buf, read, sensitivity, frequencyTilt)
                    }
                }
            }
        }.onFailure {
            _statusText.value = "Microphone capture failed: ${it.message}"
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun stopExternalCapture() {
        captureJob?.cancel()
        captureJob = null
        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        audioRecord = null
    }

    fun release() {
        stopExternalCapture()
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }
}

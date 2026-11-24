package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.model.PlaybackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class BuiltinTrackPreset(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val bpm: Float,
    val durationMs: Long,
    val rootHz: Float
)

val BUILTIN_DEMO_TRACKS = listOf(
    BuiltinTrackPreset(0, "Midnight City", "M83", "Hurry Up, We're Dreaming", 105f, 243_000L, 110f),
    BuiltinTrackPreset(1, "Blinding Lights", "The Weeknd", "After Hours", 171f, 200_000L, 130.81f),
    BuiltinTrackPreset(2, "Instant Crush", "Daft Punk", "Random Access Memories", 110f, 337_000L, 98f),
    BuiltinTrackPreset(3, "AA Synth Pulse", "AA Studio Ensemble", "Automotive Waves", 120f, 210_000L, 116.54f)
)

class BuiltinAudioPlayer(
    private val context: Context,
    private val dspProcessor: DspAudioProcessor,
    private val onPcmFrame: (ShortArray, Int) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _playbackState = MutableStateFlow(
        PlaybackMetadata(
            packageName = context.packageName,
            appLabel = "AA Synth Engine",
            title = BUILTIN_DEMO_TRACKS[0].title,
            artist = BUILTIN_DEMO_TRACKS[0].artist,
            album = BUILTIN_DEMO_TRACKS[0].album,
            durationMs = BUILTIN_DEMO_TRACKS[0].durationMs,
            positionMs = 0L,
            isPlaying = false
        )
    )
    val playbackState: StateFlow<PlaybackMetadata> = _playbackState.asStateFlow()

    private var selectedDemoIndex = 0
    private var localFileUri: Uri? = null

    fun playDemoTrack(index: Int = selectedDemoIndex) {
        selectedDemoIndex = index.coerceIn(0, BUILTIN_DEMO_TRACKS.lastIndex)
        localFileUri = null
        val preset = BUILTIN_DEMO_TRACKS[selectedDemoIndex]
        startSynthPlayback(preset)
    }

    fun playLocalFile(uri: Uri) {
        localFileUri = uri
        val meta = extractFileMetadata(uri)
        startLocalFilePlayback(uri, meta)
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            val uri = localFileUri
            if (uri != null) {
                playLocalFile(uri)
            } else {
                playDemoTrack(selectedDemoIndex)
            }
        }
    }

    fun nextTrack() {
        playDemoTrack((selectedDemoIndex + 1) % BUILTIN_DEMO_TRACKS.size)
    }

    fun prevTrack() {
        val prev = if (selectedDemoIndex == 0) BUILTIN_DEMO_TRACKS.lastIndex else selectedDemoIndex - 1
        playDemoTrack(prev)
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
        _playbackState.value = _playbackState.value.copy(isPlaying = false, updatedAtMs = System.currentTimeMillis())
    }

    fun release() {
        pause()
    }

    private fun startSynthPlayback(preset: BuiltinTrackPreset) {
        pause()
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        dspProcessor.attachToSession(track.audioSessionId)
        track.play()

        _playbackState.value = PlaybackMetadata(
            packageName = context.packageName,
            appLabel = "AA Synth Engine",
            title = preset.title,
            artist = preset.artist,
            album = preset.album,
            durationMs = preset.durationMs,
            positionMs = 0L,
            isPlaying = true,
            audioSessionId = track.audioSessionId
        )

        playJob = scope.launch {
            val framesPerChunk = 1024
            val stereoBuffer = ShortArray(framesPerChunk * 2)
            val monoForFft = ShortArray(framesPerChunk)
            var sampleCursor = 0L
            val chordRoots = floatArrayOf(1f, 1.189f, 1.498f, 1.335f)
            val arpSteps = floatArrayOf(1f, 1.25f, 1.5f, 2f, 1.5f, 1.25f, 1.875f, 1.5f)
            val startWall = System.currentTimeMillis()
            var noiseSeed = 1337

            while (isActive) {
                val beatsPerSec = preset.bpm / 60.0
                for (i in 0 until framesPerChunk) {
                    val t = sampleCursor.toDouble() / sampleRate
                    val beatPos = t * beatsPerSec
                    val beatFrac = beatPos - kotlin.math.floor(beatPos)
                    val barIdx = (beatPos.toInt() / 4) % 4
                    val stepIdx = (beatPos * 2.0).toInt() % arpSteps.size

                    // Kick drum on every beat
                    val kickEnv = exp(-beatFrac * 12.0)
                    val kickFreq = 48.0 + 95.0 * exp(-beatFrac * 26.0)
                    val kick = sin(2.0 * PI * kickFreq * (beatFrac / beatsPerSec)) * kickEnv * 0.62

                    // Warm sub & mid bassline
                    val root = preset.rootHz * chordRoots[barIdx]
                    val bassEnv = 0.55 + 0.45 * sin(2.0 * PI * beatsPerSec * 0.5 * t)
                    val bass = (sin(2.0 * PI * root * 0.5 * t) * 0.65 +
                        sin(2.0 * PI * root * t) * 0.35) * 0.34 * bassEnv

                    // Lush stereo synth pad + harmonic chord
                    val pad1 = sin(2.0 * PI * root * 2.0 * t)
                    val pad2 = sin(2.0 * PI * root * 2.5 * t)
                    val pad3 = sin(2.0 * PI * root * 3.0 * t)
                    val padL = (pad1 + pad2 * 0.8 + pad3 * 0.6) * 0.14
                    val padR = (pad1 * 0.8 + pad2 + pad3 * 0.7) * 0.14

                    // Crisp arpeggio + off-beat hi-hat
                    val arpFrac = (beatPos * 2.0) - kotlin.math.floor(beatPos * 2.0)
                    val arpFreq = root * 4.0 * arpSteps[stepIdx]
                    val arp = sin(2.0 * PI * arpFreq * t) * exp(-arpFrac * 5.0) * 0.18

                    noiseSeed = noiseSeed * 1664525 + 1013904223
                    val white = ((noiseSeed ushr 16) and 0xFFFF) / 32768.0 - 1.0
                    val hatFrac = (beatPos * 2.0 + 0.5) - kotlin.math.floor(beatPos * 2.0 + 0.5)
                    val hiHat = white * exp(-hatFrac * 28.0) * 0.14

                    val left = ((kick + bass + padL + arp * 0.8 + hiHat) * 0.85).coerceIn(-0.98, 0.98)
                    val right = ((kick + bass + padR + arp * 1.1 + hiHat * 0.9) * 0.85).coerceIn(-0.98, 0.98)

                    stereoBuffer[i * 2] = (left * 32767.0).toInt().toShort()
                    stereoBuffer[i * 2 + 1] = (right * 32767.0).toInt().toShort()
                    sampleCursor++
                }

                dspProcessor.processStereoPcm16(stereoBuffer, stereoBuffer.size)

                for (i in 0 until framesPerChunk) {
                    monoForFft[i] = ((stereoBuffer[i * 2].toInt() + stereoBuffer[i * 2 + 1].toInt()) / 2).toShort()
                }
                onPcmFrame(monoForFft, framesPerChunk)

                val written = track.write(stereoBuffer, 0, stereoBuffer.size)
                if (written < 0) break

                val elapsed = (System.currentTimeMillis() - startWall) % preset.durationMs.coerceAtLeast(1L)
                _playbackState.value = _playbackState.value.copy(
                    positionMs = elapsed,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    private fun startLocalFilePlayback(uri: Uri, meta: PlaybackMetadata) {
        pause()
        playJob = scope.launch(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            var track: AudioTrack? = null
            try {
                extractor.setDataSource(context, uri, null)
                var audioTrackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        format = fmt
                        break
                    }
                }
                if (audioTrackIndex == -1 || format == null) {
                    playDemoTrack(0)
                    return@launch
                }
                extractor.selectTrack(audioTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf.coerceAtLeast(4096) * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                dspProcessor.attachToSession(track.audioSessionId)
                track.play()

                _playbackState.value = meta.copy(
                    isPlaying = true,
                    audioSessionId = track.audioSessionId
                )

                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
                val info = MediaCodec.BufferInfo()
                var eos = false
                while (isActive && !eos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    var outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    while (outIdx >= 0 && isActive) {
                        val outBuf: ByteBuffer = codec.getOutputBuffer(outIdx)!!
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val shortCount = info.size / 2
                        val pcm = ShortArray(shortCount)
                        outBuf.asShortBuffer().get(pcm)
                        if (channels >= 2) {
                            dspProcessor.processStereoPcm16(pcm, shortCount)
                            val mono = ShortArray(shortCount / channels) { idx ->
                                pcm[idx * channels]
                            }
                            onPcmFrame(mono, mono.size)
                        } else {
                            onPcmFrame(pcm, shortCount)
                        }
                        track.write(pcm, 0, shortCount)
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = info.presentationTimeUs / 1000L,
                            updatedAtMs = System.currentTimeMillis()
                        )
                        codec.releaseOutputBuffer(outIdx, false)
                        outIdx = codec.dequeueOutputBuffer(info, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("BuiltinAudioPlayer", "Local decode error: ${e.message}")
            } finally {
                runCatching { codec?.stop(); codec?.release() }
                runCatching { extractor.release() }
                runCatching { track?.stop(); track?.release() }
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            }
        }
    }

    private fun extractFileMetadata(uri: Uri): PlaybackMetadata {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Local Audio"
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Local Library"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Device Storage"
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 180_000L
            val art = mmr.embeddedPicture
            PlaybackMetadata(
                packageName = context.packageName,
                appLabel = "Local Audio Player",
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                isPlaying = true,
                artworkBytes = art
            )
        } catch (_: Exception) {
            PlaybackMetadata(packageName = context.packageName, appLabel = "Local Audio", title = "Local Track")
        } finally {
            runCatching { mmr.release() }
        }
    }
}

package com.example.model

import androidx.compose.runtime.Immutable

data class EqBandSpec(
    val index: Int,
    val centerFreqHz: Int,
    val shortLabel: String
)

val EQ_BANDS_10: List<EqBandSpec> = listOf(
    EqBandSpec(0, 50, "50"),
    EqBandSpec(1, 100, "100"),
    EqBandSpec(2, 200, "200"),
    EqBandSpec(3, 400, "400"),
    EqBandSpec(4, 800, "800"),
    EqBandSpec(5, 1600, "1.6k"),
    EqBandSpec(6, 3200, "3.2k"),
    EqBandSpec(7, 6400, "6.4k"),
    EqBandSpec(8, 12000, "12k"),
    EqBandSpec(9, 16000, "16k")
)

enum class ProfileCategory(val label: String) {
    MUSIC("Music"),
    PODCAST("Podcasts"),
    MOVIE("Movies"),
    CUSTOM("Custom")
}

@Immutable
data class EqProfile(
    val id: Long = 0,
    val name: String,
    val category: ProfileCategory,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val bandsDb: List<Float> = List(10) { 0f },
    val bassBoost: Int = 0, // 0..1000
    val trebleBoostDb: Float = 0f, // -12..+12
    val masterGainDb: Float = 0f, // -12..+12
    val balance: Float = 0f, // -1f (Left) .. +1f (Right)
    val agcEnabled: Boolean = false,
    val smartBassEnabled: Boolean = false,
    val limiterEnabled: Boolean = true
)

enum class OutputDeviceType(val label: String, val shortLabel: String) {
    PHONE_SPEAKER("Smartphone Speakers", "Phone"),
    HEADPHONES_BLUETOOTH("Headphones & BT Earbuds", "Headphones"),
    EXTERNAL_SPEAKER("External Speakers", "External"),
    USB_DAC("USB DAC", "USB DAC"),
    CAR_AUDIO("Car Audio System", "Car Audio")
}

enum class AudioInputSource(val label: String, val description: String) {
    BUILTIN_GROOVE("Built-in Synth / Local File", "Zero-permission PCM synth or local audio file with full 10-band DSP"),
    EXTERNAL_SESSION("External Player Session", "Hooks players that broadcast AudioEffect session IDs (e.g., Poweramp)"),
    PLAYBACK_CAPTURE("System Playback Capture", "Android 10+ MediaProjection capture for permitted audio apps"),
    MICROPHONE("Microphone Input", "Universal ambient capture fallback via AudioRecord")
}

@Immutable
data class PlaybackMetadata(
    val packageName: String = "com.example.synth",
    val appLabel: String = "AA Synth Engine",
    val title: String = "Midnight Highway",
    val artist: String = "AA Studio Ensemble",
    val album: String = "Automotive Waves",
    val durationMs: Long = 225_000L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val audioSessionId: Int = 0,
    val artworkUri: String? = null,
    val artworkBytes: ByteArray? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Immutable
data class AudioAnalysisFrame(
    val spectrum: FloatArray = FloatArray(64),
    val waveform: FloatArray = FloatArray(128),
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val rms: Float = 0f,
    val beatIntensity: Float = 0f,
    val isBeat: Boolean = false,
    val peakDb: Float = -60f,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioAnalysisFrame) return false
        return timestampMs == other.timestampMs && rms == other.rms && beatIntensity == other.beatIntensity
    }

    override fun hashCode(): Int = timestampMs.hashCode() * 31 + rms.hashCode()
}

data class DeviceRouting(
    val deviceType: OutputDeviceType,
    val profileId: Long,
    val autoSwitchEnabled: Boolean = true
)

data class AppRouting(
    val packageName: String,
    val appLabel: String,
    val profileId: Long,
    val autoSwitchEnabled: Boolean = true
)

object PredefinedProfiles {
    val all: List<EqProfile> by lazy {
        var order = 0
        fun p(
            name: String,
            category: ProfileCategory,
            bands: List<Float>,
            bass: Int = 0,
            treble: Float = 0f,
            gain: Float = 0f,
            agc: Boolean = false,
            smartBass: Boolean = false
        ) = EqProfile(
            id = (order + 1).toLong(),
            name = name,
            category = category,
            isBuiltIn = true,
            sortOrder = order++,
            bandsDb = bands,
            bassBoost = bass,
            trebleBoostDb = treble,
            masterGainDb = gain,
            balance = 0f,
            agcEnabled = agc,
            smartBassEnabled = smartBass,
            limiterEnabled = true
        )

        listOf(
            // 22 Music Profiles
            p("Default", ProfileCategory.MUSIC, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            p("Bass", ProfileCategory.MUSIC, listOf(7.5f, 6.5f, 4.5f, 1.5f, 0f, -0.5f, 0f, 1.5f, 2f, 2f), bass = 520, smartBass = true),
            p("Pop", ProfileCategory.MUSIC, listOf(-1f, 1.5f, 3.5f, 4f, 2.5f, 0f, 1.5f, 3f, 3.5f, 3f), bass = 180),
            p("Rock", ProfileCategory.MUSIC, listOf(4.5f, 3.5f, 1.5f, -1f, -2f, 0.5f, 3f, 4.5f, 5f, 4f), bass = 250),
            p("Hip-Hop", ProfileCategory.MUSIC, listOf(6.5f, 5.5f, 3f, 0.5f, -1.5f, 0.5f, 2f, 2.5f, 3.5f, 3f), bass = 480, smartBass = true),
            p("Vocal", ProfileCategory.MUSIC, listOf(-3f, -2.5f, -0.5f, 2.5f, 5f, 5.5f, 4f, 2f, 0.5f, 0f)),
            p("Loudness", ProfileCategory.MUSIC, listOf(5.5f, 4.5f, 2f, 0f, -1f, 0f, 2f, 4f, 5f, 4.5f), bass = 320, agc = true),
            p("Highway", ProfileCategory.MUSIC, listOf(5f, 4f, 2.5f, 1.5f, 2f, 3f, 3.5f, 4f, 3.5f, 2.5f), bass = 350, agc = true, smartBass = true),
            p("V-Shape", ProfileCategory.MUSIC, listOf(6f, 4.5f, 1.5f, -2f, -4f, -2.5f, 1f, 4f, 5.5f, 5f), bass = 300),
            p("R&B", ProfileCategory.MUSIC, listOf(5.5f, 5f, 3f, 0.5f, -1f, 1.5f, 2.5f, 3f, 3.5f, 3f), bass = 400, smartBass = true),
            p("Rap", ProfileCategory.MUSIC, listOf(6f, 5f, 2.5f, 0f, -1f, 1.5f, 3f, 3.5f, 3f, 2f), bass = 460, smartBass = true),
            p("Latin", ProfileCategory.MUSIC, listOf(4f, 3f, 0.5f, 0f, -1.5f, 1f, 2.5f, 4f, 4.5f, 4f), bass = 260),
            p("Dance", ProfileCategory.MUSIC, listOf(5.5f, 4.5f, 2f, 0f, 0.5f, 2f, 3.5f, 4.5f, 4f, 3.5f), bass = 380, smartBass = true),
            p("EDM", ProfileCategory.MUSIC, listOf(7f, 6f, 3f, -1f, -2f, 1f, 3f, 5f, 5.5f, 5f), bass = 560, smartBass = true),
            p("Electronic", ProfileCategory.MUSIC, listOf(5.5f, 4.5f, 2f, -0.5f, -1f, 1.5f, 3f, 4.5f, 5f, 4.5f), bass = 420, smartBass = true),
            p("Disco", ProfileCategory.MUSIC, listOf(4.5f, 3.5f, 1.5f, 0f, 1f, 2.5f, 3.5f, 4f, 3.5f, 2.5f), bass = 320),
            p("Metal", ProfileCategory.MUSIC, listOf(4.5f, 3.5f, 1f, -2f, -3f, 1f, 3.5f, 5f, 4.5f, 3.5f), bass = 280),
            p("Country", ProfileCategory.MUSIC, listOf(2.5f, 2f, 0.5f, 0f, 1.5f, 2.5f, 3f, 3.5f, 2.5f, 1.5f)),
            p("Acoustic", ProfileCategory.MUSIC, listOf(1.5f, 2f, 1.5f, 1f, 2f, 2.5f, 3f, 3.5f, 3f, 2f)),
            p("Jazz", ProfileCategory.MUSIC, listOf(2.5f, 2f, 1f, 1.5f, -1f, 1f, 2.5f, 3f, 3.5f, 2.5f)),
            p("Classical", ProfileCategory.MUSIC, listOf(3f, 2f, 0f, 0f, 0f, 0.5f, 1.5f, 2.5f, 3.5f, 4f)),
            p("Lo-Fi", ProfileCategory.MUSIC, listOf(3.5f, 4f, 2.5f, 1f, 0.5f, -1f, -2.5f, -4f, -5.5f, -6.5f), bass = 220),

            // 6 Podcast Profiles
            p("Clear Voice", ProfileCategory.PODCAST, listOf(-5f, -4f, -1.5f, 2f, 4.5f, 5.5f, 4f, 2f, 0f, -1f), agc = true),
            p("Deep Voice", ProfileCategory.PODCAST, listOf(-2f, 2f, 3.5f, 2.5f, 1f, 2.5f, 3f, 1.5f, 0f, -1f), agc = true),
            p("Bright", ProfileCategory.PODCAST, listOf(-4f, -3f, -1f, 1.5f, 3.5f, 5f, 5.5f, 4.5f, 3f, 2f), agc = true),
            p("Highway Talk", ProfileCategory.PODCAST, listOf(-6f, -4f, 0f, 3f, 5f, 6f, 5f, 3f, 1f, 0f), gain = 2.5f, agc = true),
            p("Audiobook", ProfileCategory.PODCAST, listOf(-3.5f, -2f, 0.5f, 2.5f, 3.5f, 4f, 3f, 1.5f, 0f, -1.5f), agc = true),
            p("Remote Guest", ProfileCategory.PODCAST, listOf(-5f, -3f, 1f, 3.5f, 4f, 4.5f, 3f, 1f, -2f, -3.5f), agc = true),

            // 6 Movie Profiles
            p("Cinema", ProfileCategory.MOVIE, listOf(6f, 5f, 2.5f, 0.5f, 1f, 2.5f, 3f, 4f, 4.5f, 4f), bass = 450, smartBass = true),
            p("Action", ProfileCategory.MOVIE, listOf(7f, 6f, 3.5f, 1f, 0f, 1.5f, 3.5f, 5f, 5f, 4.5f), bass = 540, smartBass = true),
            p("Sci-Fi", ProfileCategory.MOVIE, listOf(5.5f, 4.5f, 2f, 0f, 1.5f, 3f, 4.5f, 5.5f, 6f, 5.5f), bass = 420, smartBass = true),
            p("Musical", ProfileCategory.MOVIE, listOf(3f, 2.5f, 1f, 1.5f, 3f, 4f, 3.5f, 3.5f, 4f, 3f), bass = 200),
            p("Dialogue", ProfileCategory.MOVIE, listOf(-4f, -2.5f, 0f, 2.5f, 4.5f, 5.5f, 4f, 2f, 0.5f, 0f), agc = true),
            p("Night Mode", ProfileCategory.MOVIE, listOf(-4f, -2f, 0.5f, 2f, 3.5f, 4f, 2.5f, 1f, -1f, -2f), gain = -2f, agc = true)
        )
    }
}

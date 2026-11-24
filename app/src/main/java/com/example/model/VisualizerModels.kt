package com.example.model

import androidx.compose.runtime.Immutable

enum class VisualizerCategory(val label: String) {
    SPECTRUM("Spectrum"),
    RADIAL("Radial & Halo"),
    WAVEFORM("Waveform"),
    PARTICLES("Particles"),
    GEOMETRIC("Geometric"),
    PSYCHEDELIC("Psychedelic"),
    RETRO("Retro 2000s")
}

enum class VisualizerStyle(
    val index: Int,
    val title: String,
    val category: VisualizerCategory,
    val description: String
) {
    CLASSIC_BARS(0, "Classic Spectrum", VisualizerCategory.SPECTRUM, "64-band segmented LED bars with peak hold"),
    MIRROR_SPECTRUM(1, "Mirror Horizon", VisualizerCategory.SPECTRUM, "Symmetrical center-axis spectrum with glow"),
    LIQUID_CONTOUR(2, "Liquid Contour", VisualizerCategory.SPECTRUM, "Smooth cubic-bezier frequency curve"),
    CIRCULAR_SPECTRUM(3, "Circular Ring", VisualizerCategory.RADIAL, "360-degree radial spectrum bars"),
    DUAL_RING(4, "Dual Counter-Ring", VisualizerCategory.RADIAL, "Inner bass ring and outer treble corona"),
    RADIAL_SUNBURST(5, "Radial Sunburst", VisualizerCategory.RADIAL, "Beat-reactive star rays and energy core"),
    OSCILLOSCOPE(6, "Neon Oscilloscope", VisualizerCategory.WAVEFORM, "Crisp multi-trace time-domain scope"),
    RIBBON_FLOW(7, "Harmonic Ribbons", VisualizerCategory.WAVEFORM, "Layered sine-modulated silk waves"),
    TERRAIN_GRID(8, "Synthwave Terrain", VisualizerCategory.WAVEFORM, "3D perspective horizon grid wave"),
    PARTICLE_CONSTELLATION(9, "Constellation", VisualizerCategory.PARTICLES, "Connected nodes reacting to beat transients"),
    STARFIELD_WARP(10, "Starfield Warp", VisualizerCategory.PARTICLES, "Depth-accelerated star particles on bass hits"),
    EMBER_VORTEX(11, "Ember Vortex", VisualizerCategory.PARTICLES, "Swirling orbital particles driven by mids"),
    SACRED_MANDALA(12, "Sacred Mandala", VisualizerCategory.GEOMETRIC, "Rotating polygonal harmonic lattice"),
    KALEIDOSCOPE(13, "Prism Kaleidoscope", VisualizerCategory.GEOMETRIC, "Symmetric crystalline shards pulsing to audio"),
    HEX_MATRIX(14, "Hexagon Matrix", VisualizerCategory.GEOMETRIC, "Honeycomb audio-reactive cell grid"),
    PSYCHEDELIC_PLASMA(15, "Chroma Plasma", VisualizerCategory.PSYCHEDELIC, "Organic interference rings and hue shifts"),
    LAVA_AURORA(16, "Lava Aurora", VisualizerCategory.PSYCHEDELIC, "Fluid morphing chromatic energy fields"),
    HYPNO_TUNNEL(17, "Hypno Tunnel", VisualizerCategory.PSYCHEDELIC, "Warped concentric depth rings zooming on beat"),
    WINAMP_PEAK(18, "2000s Peak Meter", VisualizerCategory.RETRO, "Retro green-amber-red segmented VU analyzer"),
    CRT_PHOSPHOR(19, "CRT Phosphor XY", VisualizerCategory.RETRO, "Vintage Lissajous phase & scanline scope"),
    PIXEL_MATRIX(20, "Dot Matrix VU", VisualizerCategory.RETRO, "Retro 16x12 hardware LED matrix display"),
    SOLAR_HALO(21, "Solar Halo", VisualizerCategory.RADIAL, "Luminous breathing aura expanding on bass"),
    ORBITAL_GYRO(22, "Orbital Gyro", VisualizerCategory.RADIAL, "Tilted 3D gyroscopic rings per frequency band"),
    AUTO_MINIMAL(23, "Auto Horizon", VisualizerCategory.RADIAL, "Low-distraction automotive pulse & horizon")
}

enum class VisualizerColorScheme(val label: String) {
    CYBER_CYAN("Cyber Cyan"),
    NEON_VIOLET("Neon Violet"),
    SUNSET_EMBER("Sunset Ember"),
    EMERALD_MATRIX("Emerald Matrix"),
    RAINBOW_PRISM("Rainbow Prism"),
    MONOCHROME_ICE("Monochrome Ice"),
    ALBUM_ART("Album Art Adaptive")
}

enum class AppThemeStyle(val label: String, val subtitle: String) {
    DARK_STUDIO("Dark Studio", "Deep obsidian surface & luminous accents"),
    PURE_OLED("Pure Black OLED", "True #000000 black for zero glow bleed"),
    GLASS("Glassmorphic", "Translucent frosted panels & hairline light"),
    SKEUOMORPHIC("Hardware Rack", "Metallic bezels & recessed DSP meters"),
    FLAT_MINIMAL("Flat Contrast", "Crisp matte automotive high-legibility UI")
}

enum class CarScreenPreset(
    val label: String,
    val aspectRatio: Float?,
    val widthDpLabel: String
) {
    DEVICE_NATIVE("Device Native", null, "Adaptive"),
    AA_STANDARD("AA Standard (800×480)", 800f / 480f, "5:3 Car"),
    AA_HD("AA HD (1280×720)", 1280f / 720f, "16:9 Car"),
    AA_ULTRAWIDE("AA Ultrawide (1920×720)", 1920f / 720f, "8:3 Wide"),
    AA_PORTRAIT("AA Portrait Split (720×1080)", 720f / 1080f, "2:3 Portrait")
}

@Immutable
data class VisualizerConfig(
    val styleIndex: Int = 0,
    val colorScheme: VisualizerColorScheme = VisualizerColorScheme.CYBER_CYAN,
    val backgroundHex: Long = 0xFF070911,
    val useOledBlackBg: Boolean = false,
    val showAlbumArtBg: Boolean = true,
    val intensity: Float = 1.0f, // 0.3..2.0
    val sensitivity: Float = 1.1f, // 0.3..2.5
    val frequencyTilt: Float = 0.0f, // -1.0 (bass focus) .. +1.0 (treble focus)
    val animationSpeed: Float = 1.0f, // 0.3..2.0
    val brightness: Float = 1.0f, // 0.2..1.0
    val transparency: Float = 0.9f, // 0.2..1.0
    val showTrackOverlay: Boolean = true,
    val showLyricsOverlay: Boolean = true,
    val showClockOverlay: Boolean = true,
    val themeStyle: AppThemeStyle = AppThemeStyle.DARK_STUDIO,
    val customAccentHex: Long = 0xFF00E5FF,
    val carScreenPreset: CarScreenPreset = CarScreenPreset.DEVICE_NATIVE,
    val screensaverBrightness: Float = 0.65f
) {
    val activeStyle: VisualizerStyle
        get() = VisualizerStyle.entries.getOrElse(styleIndex) { VisualizerStyle.CLASSIC_BARS }
}

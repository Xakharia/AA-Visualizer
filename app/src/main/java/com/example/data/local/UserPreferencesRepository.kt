package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeStyle
import com.example.model.AudioInputSource
import com.example.model.CarScreenPreset
import com.example.model.VisualizerColorScheme
import com.example.model.VisualizerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aa_visualizer_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val STYLE_INDEX = intPreferencesKey("style_index")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val BACKGROUND_HEX = longPreferencesKey("background_hex")
        val OLED_BG = booleanPreferencesKey("oled_bg")
        val ALBUM_ART_BG = booleanPreferencesKey("album_art_bg")
        val INTENSITY = floatPreferencesKey("intensity")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val FREQ_TILT = floatPreferencesKey("freq_tilt")
        val ANIM_SPEED = floatPreferencesKey("anim_speed")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val TRANSPARENCY = floatPreferencesKey("transparency")
        val SHOW_TRACK = booleanPreferencesKey("show_track")
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACCENT_HEX = longPreferencesKey("accent_hex")
        val CAR_PRESET = stringPreferencesKey("car_preset")
        val SCREENSAVER_BRIGHTNESS = floatPreferencesKey("screensaver_brightness")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val AUDIO_INPUT_SOURCE = stringPreferencesKey("audio_input_source")
        val AUTO_EQ_SWITCH = booleanPreferencesKey("auto_eq_switch")
        val AUTO_LYRICS_FETCH = booleanPreferencesKey("auto_lyrics_fetch")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    }

    val visualizerConfig: Flow<VisualizerConfig> = context.dataStore.data.map { prefs ->
        VisualizerConfig(
            styleIndex = prefs[Keys.STYLE_INDEX] ?: 0,
            colorScheme = prefs[Keys.COLOR_SCHEME]?.let {
                runCatching { VisualizerColorScheme.valueOf(it) }.getOrNull()
            } ?: VisualizerColorScheme.CYBER_CYAN,
            backgroundHex = prefs[Keys.BACKGROUND_HEX] ?: 0xFF070911,
            useOledBlackBg = prefs[Keys.OLED_BG] ?: false,
            showAlbumArtBg = prefs[Keys.ALBUM_ART_BG] ?: true,
            intensity = prefs[Keys.INTENSITY] ?: 1.0f,
            sensitivity = prefs[Keys.SENSITIVITY] ?: 1.1f,
            frequencyTilt = prefs[Keys.FREQ_TILT] ?: 0.0f,
            animationSpeed = prefs[Keys.ANIM_SPEED] ?: 1.0f,
            brightness = prefs[Keys.BRIGHTNESS] ?: 1.0f,
            transparency = prefs[Keys.TRANSPARENCY] ?: 0.9f,
            showTrackOverlay = prefs[Keys.SHOW_TRACK] ?: true,
            showLyricsOverlay = prefs[Keys.SHOW_LYRICS] ?: true,
            showClockOverlay = prefs[Keys.SHOW_CLOCK] ?: true,
            themeStyle = prefs[Keys.THEME_STYLE]?.let {
                runCatching { AppThemeStyle.valueOf(it) }.getOrNull()
            } ?: AppThemeStyle.DARK_STUDIO,
            customAccentHex = prefs[Keys.ACCENT_HEX] ?: 0xFF00E5FF,
            carScreenPreset = prefs[Keys.CAR_PRESET]?.let {
                runCatching { CarScreenPreset.valueOf(it) }.getOrNull()
            } ?: CarScreenPreset.DEVICE_NATIVE,
            screensaverBrightness = prefs[Keys.SCREENSAVER_BRIGHTNESS] ?: 0.65f
        )
    }

    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] ?: 1L }

    val audioInputSource: Flow<AudioInputSource> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUDIO_INPUT_SOURCE]?.let {
            runCatching { AudioInputSource.valueOf(it) }.getOrNull()
        } ?: AudioInputSource.BUILTIN_GROOVE
    }

    val autoEqSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_EQ_SWITCH] ?: true }
    val autoLyricsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_LYRICS_FETCH] ?: true }
    val eqEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.EQ_ENABLED] ?: true }

    suspend fun updateVisualizerConfig(transform: (VisualizerConfig) -> VisualizerConfig) {
        context.dataStore.edit { prefs ->
            val current = VisualizerConfig(
                styleIndex = prefs[Keys.STYLE_INDEX] ?: 0,
                colorScheme = prefs[Keys.COLOR_SCHEME]?.let {
                    runCatching { VisualizerColorScheme.valueOf(it) }.getOrNull()
                } ?: VisualizerColorScheme.CYBER_CYAN,
                backgroundHex = prefs[Keys.BACKGROUND_HEX] ?: 0xFF070911,
                useOledBlackBg = prefs[Keys.OLED_BG] ?: false,
                showAlbumArtBg = prefs[Keys.ALBUM_ART_BG] ?: true,
                intensity = prefs[Keys.INTENSITY] ?: 1.0f,
                sensitivity = prefs[Keys.SENSITIVITY] ?: 1.1f,
                frequencyTilt = prefs[Keys.FREQ_TILT] ?: 0.0f,
                animationSpeed = prefs[Keys.ANIM_SPEED] ?: 1.0f,
                brightness = prefs[Keys.BRIGHTNESS] ?: 1.0f,
                transparency = prefs[Keys.TRANSPARENCY] ?: 0.9f,
                showTrackOverlay = prefs[Keys.SHOW_TRACK] ?: true,
                showLyricsOverlay = prefs[Keys.SHOW_LYRICS] ?: true,
                showClockOverlay = prefs[Keys.SHOW_CLOCK] ?: true,
                themeStyle = prefs[Keys.THEME_STYLE]?.let {
                    runCatching { AppThemeStyle.valueOf(it) }.getOrNull()
                } ?: AppThemeStyle.DARK_STUDIO,
                customAccentHex = prefs[Keys.ACCENT_HEX] ?: 0xFF00E5FF,
                carScreenPreset = prefs[Keys.CAR_PRESET]?.let {
                    runCatching { CarScreenPreset.valueOf(it) }.getOrNull()
                } ?: CarScreenPreset.DEVICE_NATIVE,
                screensaverBrightness = prefs[Keys.SCREENSAVER_BRIGHTNESS] ?: 0.65f
            )
            val updated = transform(current)
            prefs[Keys.STYLE_INDEX] = updated.styleIndex
            prefs[Keys.COLOR_SCHEME] = updated.colorScheme.name
            prefs[Keys.BACKGROUND_HEX] = updated.backgroundHex
            prefs[Keys.OLED_BG] = updated.useOledBlackBg
            prefs[Keys.ALBUM_ART_BG] = updated.showAlbumArtBg
            prefs[Keys.INTENSITY] = updated.intensity
            prefs[Keys.SENSITIVITY] = updated.sensitivity
            prefs[Keys.FREQ_TILT] = updated.frequencyTilt
            prefs[Keys.ANIM_SPEED] = updated.animationSpeed
            prefs[Keys.BRIGHTNESS] = updated.brightness
            prefs[Keys.TRANSPARENCY] = updated.transparency
            prefs[Keys.SHOW_TRACK] = updated.showTrackOverlay
            prefs[Keys.SHOW_LYRICS] = updated.showLyricsOverlay
            prefs[Keys.SHOW_CLOCK] = updated.showClockOverlay
            prefs[Keys.THEME_STYLE] = updated.themeStyle.name
            prefs[Keys.ACCENT_HEX] = updated.customAccentHex
            prefs[Keys.CAR_PRESET] = updated.carScreenPreset.name
            prefs[Keys.SCREENSAVER_BRIGHTNESS] = updated.screensaverBrightness
        }
    }

    suspend fun setActiveProfileId(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun setAudioInputSource(source: AudioInputSource) {
        context.dataStore.edit { it[Keys.AUDIO_INPUT_SOURCE] = source.name }
    }

    suspend fun setAutoEqSwitch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EQ_SWITCH] = enabled }
    }

    suspend fun setAutoLyrics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LYRICS_FETCH] = enabled }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }
}

package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AAVisualizerApp
import com.example.model.AppRouting
import com.example.model.AudioAnalysisFrame
import com.example.model.AudioInputSource
import com.example.model.DeviceRouting
import com.example.model.EqProfile
import com.example.model.LyricsUiState
import com.example.model.OutputDeviceType
import com.example.model.PlaybackMetadata
import com.example.model.VisualizerConfig
import com.example.service.AudioMonitorService
import com.example.service.MediaSessionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AAVisualizerApp
    private val container = app.container
    private val repo = container.repository

    val analysisFrame: StateFlow<AudioAnalysisFrame> = container.analysisEngine.analysisFrame
    val activeInputSource: StateFlow<AudioInputSource> = container.analysisEngine.activeSource
    val engineStatus: StateFlow<String> = container.analysisEngine.statusText
    val dspStatus: StateFlow<String> = container.dspProcessor.dspStatus
    val currentDevice: StateFlow<OutputDeviceType> = container.outputMonitor.currentDevice
    val deviceDetailName: StateFlow<String> = container.outputMonitor.deviceDetailName
    val currentPlayback: StateFlow<PlaybackMetadata> = container.currentPlayback
    val profiles: StateFlow<List<EqProfile>> = container.profiles
    val activeProfile: StateFlow<EqProfile> = container.activeProfile
    val eqEnabled: StateFlow<Boolean> = container.eqEnabled
    val lyricsState: StateFlow<LyricsUiState> = container.lyricsState
    val notificationAccessGranted: StateFlow<Boolean> = MediaSessionBridge.notificationAccessGranted

    val visualizerConfig: StateFlow<VisualizerConfig> = repo.visualizerConfig.stateIn(
        viewModelScope, SharingStarted.Eagerly, VisualizerConfig()
    )
    val deviceRoutings: StateFlow<List<DeviceRouting>> = repo.deviceRoutings.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )
    val appRoutings: StateFlow<List<AppRouting>> = repo.appRoutings.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )
    val autoEqSwitchEnabled: StateFlow<Boolean> = repo.autoEqSwitchEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val autoLyricsEnabled: StateFlow<Boolean> = repo.autoLyricsEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    val albumBitmap: StateFlow<Bitmap?> = currentPlayback.map { meta ->
        meta.artworkBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val albumAccentColors: StateFlow<Pair<Color?, Color?>> = albumBitmap.map { bmp ->
        extractBitmapPalette(bmp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null to null)

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isScreensaverPreview = MutableStateFlow(false)
    val isScreensaverPreview: StateFlow<Boolean> = _isScreensaverPreview.asStateFlow()

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    fun setScreensaverPreview(active: Boolean) {
        _isScreensaverPreview.value = active
    }

    fun refreshSystemStatus() {
        MediaSessionBridge.pollActiveSessions(app)
        container.outputMonitor.refresh()
    }

    // Playback Controls
    fun togglePlayPause() {
        if (activeInputSource.value == AudioInputSource.BUILTIN_GROOVE ||
            currentPlayback.value.packageName == app.packageName
        ) {
            container.builtinPlayer.togglePlayPause()
        } else {
            MediaSessionBridge.togglePlayPause()
        }
    }

    fun nextTrack() {
        if (activeInputSource.value == AudioInputSource.BUILTIN_GROOVE ||
            currentPlayback.value.packageName == app.packageName
        ) {
            container.builtinPlayer.nextTrack()
        } else {
            MediaSessionBridge.skipNext()
        }
    }

    fun prevTrack() {
        if (activeInputSource.value == AudioInputSource.BUILTIN_GROOVE ||
            currentPlayback.value.packageName == app.packageName
        ) {
            container.builtinPlayer.prevTrack()
        } else {
            MediaSessionBridge.skipPrev()
        }
    }

    fun playDemoTrack(index: Int) {
        viewModelScope.launch {
            repo.setAudioInputSource(AudioInputSource.BUILTIN_GROOVE)
            container.analysisEngine.switchSource(AudioInputSource.BUILTIN_GROOVE)
            container.builtinPlayer.playDemoTrack(index)
        }
    }

    fun playLocalAudioUri(uri: Uri) {
        viewModelScope.launch {
            repo.setAudioInputSource(AudioInputSource.BUILTIN_GROOVE)
            container.analysisEngine.switchSource(AudioInputSource.BUILTIN_GROOVE)
            container.builtinPlayer.playLocalFile(uri)
        }
    }

    fun switchInputSource(source: AudioInputSource) {
        viewModelScope.launch {
            repo.setAudioInputSource(source)
            if (source != AudioInputSource.BUILTIN_GROOVE) {
                container.builtinPlayer.pause()
            }
            if (source != AudioInputSource.PLAYBACK_CAPTURE) {
                val extSession = MediaSessionBridge.externalSessionId.value
                container.analysisEngine.switchSource(source, externalSessionId = extSession)
            }
        }
    }

    fun startPlaybackCaptureWithProjection(resultCode: Int, data: Intent) {
        viewModelScope.launch {
            repo.setAudioInputSource(AudioInputSource.PLAYBACK_CAPTURE)
            container.builtinPlayer.pause()
            val intent = Intent(app, AudioMonitorService::class.java).apply {
                action = AudioMonitorService.ACTION_START_PROJECTION
                putExtra(AudioMonitorService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioMonitorService.EXTRA_DATA_INTENT, data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
    }

    // Equalizer & Profiles
    fun selectProfile(profileId: Long) {
        viewModelScope.launch { repo.setActiveProfileId(profileId) }
    }

    fun cycleNextProfile() {
        val list = profiles.value
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == activeProfile.value.id }
        val next = list[(idx + 1) % list.size]
        selectProfile(next.id)
    }

    fun toggleEqEnabled() {
        viewModelScope.launch { repo.setEqEnabled(!eqEnabled.value) }
    }

    fun updateBand(index: Int, valueDb: Float) {
        val current = activeProfile.value
        val updatedBands = current.bandsDb.toMutableList().apply {
            if (index in indices) this[index] = valueDb.coerceIn(-12f, 12f)
        }
        saveEditedProfile(current.copy(bandsDb = updatedBands))
    }

    fun updateBassBoost(strength: Int) {
        saveEditedProfile(activeProfile.value.copy(bassBoost = strength.coerceIn(0, 1000)))
    }

    fun updateTreble(db: Float) {
        saveEditedProfile(activeProfile.value.copy(trebleBoostDb = db.coerceIn(-12f, 12f)))
    }

    fun updateMasterGain(db: Float) {
        saveEditedProfile(activeProfile.value.copy(masterGainDb = db.coerceIn(-12f, 12f)))
    }

    fun updateBalance(balance: Float) {
        saveEditedProfile(activeProfile.value.copy(balance = balance.coerceIn(-1f, 1f)))
    }

    fun toggleAgc() {
        val current = activeProfile.value
        saveEditedProfile(current.copy(agcEnabled = !current.agcEnabled))
    }

    fun toggleSmartBass() {
        val current = activeProfile.value
        saveEditedProfile(current.copy(smartBassEnabled = !current.smartBassEnabled))
    }

    fun toggleLimiter() {
        val current = activeProfile.value
        saveEditedProfile(current.copy(limiterEnabled = !current.limiterEnabled))
    }

    private fun saveEditedProfile(updated: EqProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateProfile(updated)
        }
    }

    fun createCustomProfile(name: String, copyCurrent: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = repo.createCustomProfile(name, if (copyCurrent) activeProfile.value else null)
            repo.setActiveProfileId(newId)
        }
    }

    fun duplicateProfile(profile: EqProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = repo.duplicateProfile(profile)
            repo.setActiveProfileId(newId)
        }
    }

    fun renameProfile(profile: EqProfile, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.renameCustomProfile(profile, newName)
        }
    }

    fun moveProfileOrder(profileId: Long, delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.moveProfileOrder(profileId, delta)
        }
    }

    fun deleteCustomProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCustomProfile(profileId)
            if (activeProfile.value.id == profileId) {
                repo.setActiveProfileId(1L)
            }
        }
    }

    // Routing
    fun setDeviceRouting(deviceType: OutputDeviceType, profileId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setDeviceRouting(deviceType, profileId, enabled)
        }
    }

    fun setAppRouting(packageName: String, appLabel: String, profileId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setAppRouting(packageName, appLabel, profileId, enabled)
        }
    }

    fun deleteAppRouting(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAppRouting(packageName)
        }
    }

    fun setAutoEqSwitch(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoEqSwitch(enabled) }
    }

    // Visualizer config
    fun selectVisualizerStyle(index: Int) {
        viewModelScope.launch {
            repo.updateVisualizerConfig { it.copy(styleIndex = index.coerceIn(0, 23)) }
        }
    }

    fun updateVisualizerConfig(transform: (VisualizerConfig) -> VisualizerConfig) {
        viewModelScope.launch { repo.updateVisualizerConfig(transform) }
    }

    // Lyrics
    fun fetchLyrics(title: String, artist: String, durationSec: Int = 0) {
        container.loadLyricsFor(title, artist, durationSec)
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        container.adjustLyricsOffset(deltaMs)
    }

    fun setAutoLyrics(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoLyrics(enabled) }
    }

    private fun extractBitmapPalette(bmp: Bitmap?): Pair<Color?, Color?> {
        if (bmp == null) return null to null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null to null
        val c1 = Color(bmp.getPixel(w / 3, h / 3))
        val c2 = Color(bmp.getPixel((w * 2) / 3, (h * 2) / 3))
        return c1 to c2
    }
}

package com.example

import android.app.Application
import android.content.Context
import com.example.audio.AudioAnalysisEngine
import com.example.audio.AudioOutputMonitor
import com.example.audio.BuiltinAudioPlayer
import com.example.audio.DspAudioProcessor
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.AudioRepository
import com.example.integration.EqProfileWidgetProvider
import com.example.model.AudioInputSource
import com.example.model.EqProfile
import com.example.model.LyricsUiState
import com.example.model.PlaybackMetadata
import com.example.model.ProfileCategory
import com.example.service.MediaSessionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = AppDatabase.getInstance(context)
    val prefs = UserPreferencesRepository(context)
    val repository = AudioRepository(database, prefs)

    val dspProcessor = DspAudioProcessor()
    val analysisEngine = AudioAnalysisEngine(context)
    val builtinPlayer = BuiltinAudioPlayer(context, dspProcessor) { pcm, len ->
        analysisEngine.submitInternalPcm(pcm, len)
    }
    val outputMonitor = AudioOutputMonitor(context)

    val profiles: StateFlow<List<EqProfile>> = repository.profiles.stateIn(
        scope, SharingStarted.Eagerly, emptyList()
    )
    val activeProfileId: StateFlow<Long> = repository.activeProfileId.stateIn(
        scope, SharingStarted.Eagerly, 1L
    )
    val eqEnabled: StateFlow<Boolean> = repository.eqEnabled.stateIn(
        scope, SharingStarted.Eagerly, true
    )

    val activeProfile: StateFlow<EqProfile> = combine(profiles, activeProfileId) { list, id ->
        list.firstOrNull { it.id == id }
            ?: list.firstOrNull()
            ?: EqProfile(id = 1L, name = "Default", category = ProfileCategory.MUSIC)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        EqProfile(id = 1L, name = "Default", category = ProfileCategory.MUSIC)
    )

    val currentPlayback: StateFlow<PlaybackMetadata> = combine(
        builtinPlayer.playbackState,
        MediaSessionBridge.externalPlayback,
        analysisEngine.activeSource
    ) { internal, external, src ->
        if (src != AudioInputSource.BUILTIN_GROOVE && external != null) {
            external
        } else if (external != null && external.isPlaying && !internal.isPlaying) {
            external
        } else {
            internal
        }
    }.stateIn(scope, SharingStarted.Eagerly, PlaybackMetadata())

    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState

    private var lastFetchedTrackKey: String? = null

    init {
        outputMonitor.start()
        MediaSessionBridge.refreshAccessStatus(context)

        scope.launch {
            repository.ensureSeeded()
            val savedSrc = repository.audioInputSource.first()
            if (savedSrc == AudioInputSource.BUILTIN_GROOVE) {
                analysisEngine.switchSource(AudioInputSource.BUILTIN_GROOVE)
            }
        }

        scope.launch {
            combine(activeProfile, eqEnabled) { profile, enabled ->
                profile to enabled
            }.collect { (profile, enabled) ->
                dspProcessor.updateProfile(profile, enabled)
                EqProfileWidgetProvider.notifyDataChanged(context, profile.name, outputMonitor.currentDevice.value.shortLabel)
            }
        }

        scope.launch {
            repository.visualizerConfig.collect { cfg ->
                analysisEngine.sensitivity = cfg.sensitivity
                analysisEngine.frequencyTilt = cfg.frequencyTilt
            }
        }

        // Auto-apply profile when output device or external app changes
        scope.launch {
            combine(
                outputMonitor.currentDevice,
                MediaSessionBridge.externalPlayback,
                repository.autoEqSwitchEnabled
            ) { device, external, autoEnabled ->
                Triple(device, external?.packageName, autoEnabled)
            }.collect { (device, pkg, autoEnabled) ->
                if (autoEnabled) {
                    val resolved = repository.resolveProfileForContext(pkg, device)
                    if (resolved != null && resolved != activeProfileId.value) {
                        repository.setActiveProfileId(resolved)
                    }
                }
            }
        }

        // Auto-fetch lyrics when track changes & update active line index smoothly
        scope.launch {
            currentPlayback.collect { meta ->
                val key = "${meta.artist}|${meta.title}"
                val autoLyrics = repository.autoLyricsEnabled.first()
                if (autoLyrics && meta.title.isNotBlank() && key != lastFetchedTrackKey) {
                    lastFetchedTrackKey = key
                    loadLyricsFor(meta.title, meta.artist, (meta.durationMs / 1000L).toInt())
                }
                updateActiveLyricLine(meta.positionMs)
            }
        }
    }

    fun onExternalAudioSessionOpened(sessionId: Int, pkg: String?) {
        dspProcessor.attachToSession(sessionId)
        if (analysisEngine.activeSource.value == AudioInputSource.EXTERNAL_SESSION) {
            analysisEngine.switchSource(AudioInputSource.EXTERNAL_SESSION, externalSessionId = sessionId)
        }
        scope.launch {
            if (repository.autoEqSwitchEnabled.first()) {
                val resolved = repository.resolveProfileForContext(pkg, outputMonitor.currentDevice.value)
                if (resolved != null) {
                    repository.setActiveProfileId(resolved)
                }
            }
        }
    }

    fun loadLyricsFor(title: String, artist: String, durationSec: Int) {
        scope.launch {
            _lyricsState.value = _lyricsState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.fetchLyrics(title, artist, durationSec)
            result.onSuccess { synced ->
                _lyricsState.value = _lyricsState.value.copy(
                    isLoading = false,
                    lyrics = synced,
                    errorMessage = null
                )
                updateActiveLyricLine(currentPlayback.value.positionMs)
            }.onFailure { err ->
                _lyricsState.value = _lyricsState.value.copy(
                    isLoading = false,
                    lyrics = null,
                    activeLineIndex = -1,
                    errorMessage = err.message ?: "Lyrics unavailable"
                )
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _lyricsState.value = _lyricsState.value.copy(
            syncOffsetMs = (_lyricsState.value.syncOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L)
        )
        updateActiveLyricLine(currentPlayback.value.positionMs)
    }

    private fun updateActiveLyricLine(positionMs: Long) {
        val state = _lyricsState.value
        val lines = state.lyrics?.lines ?: return
        if (lines.isEmpty()) return
        val adjusted = positionMs + state.syncOffsetMs
        var activeIdx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= adjusted) activeIdx = i else break
        }
        if (activeIdx != state.activeLineIndex) {
            _lyricsState.value = state.copy(activeLineIndex = activeIdx)
        }
    }
}

class AAVisualizerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

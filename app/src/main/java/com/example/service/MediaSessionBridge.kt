package com.example.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import com.example.model.PlaybackMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

object MediaSessionBridge {
    private val _externalPlayback = MutableStateFlow<PlaybackMetadata?>(null)
    val externalPlayback: StateFlow<PlaybackMetadata?> = _externalPlayback.asStateFlow()

    private val _externalSessionId = MutableStateFlow(0)
    val externalSessionId: StateFlow<Int> = _externalSessionId.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private var activeController: MediaController? = null

    fun refreshAccessStatus(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val comp = ComponentName(context, MediaNotificationListenerService::class.java).flattenToString()
        val granted = enabledListeners.contains(comp) || enabledListeners.contains(context.packageName)
        _notificationAccessGranted.value = granted
        return granted
    }

    fun updateFromController(context: Context, controller: MediaController?) {
        activeController = controller
        if (controller == null) {
            _externalPlayback.value = null
            return
        }
        val pkg = controller.packageName
        val appLabel = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

        val meta = controller.metadata
        val pb = controller.playbackState
        val isPlaying = pb?.state == PlaybackState.STATE_PLAYING
        val pos = computeLivePosition(pb)
        val artBytes = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val compressedArt = artBytes?.let { bmp ->
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
                out.toByteArray()
            }
        }

        _externalPlayback.value = PlaybackMetadata(
            packageName = pkg,
            appLabel = appLabel,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: appLabel,
            album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionMs = pos,
            isPlaying = isPlaying,
            audioSessionId = _externalSessionId.value,
            artworkUri = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            artworkBytes = compressedArt,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun updateAudioSessionId(sessionId: Int) {
        _externalSessionId.value = sessionId.coerceAtLeast(0)
    }

    fun togglePlayPause() {
        val c = activeController ?: return
        val state = c.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrev() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(positionMs)
    }

    fun pollActiveSessions(context: Context) {
        if (!refreshAccessStatus(context)) return
        runCatching {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val comp = ComponentName(context, MediaNotificationListenerService::class.java)
            val list = mgr.getActiveSessions(comp)
            val chosen = list.firstOrNull {
                it.packageName != context.packageName &&
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: list.firstOrNull { it.packageName != context.packageName }
            updateFromController(context, chosen)
        }
    }

    private fun computeLivePosition(pb: PlaybackState?): Long {
        if (pb == null) return 0L
        var pos = pb.position
        if (pb.state == PlaybackState.STATE_PLAYING) {
            val delta = SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime
            pos += (delta * pb.playbackSpeed).toLong()
        }
        return pos.coerceAtLeast(0L)
    }
}

package com.example.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListenerService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var currentController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaSessionBridge.updateFromController(applicationContext, currentController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            MediaSessionBridge.updateFromController(applicationContext, currentController)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachToBestController(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaSessionBridge.refreshAccessStatus(applicationContext)
        runCatching {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = mgr
            val comp = ComponentName(this, MediaNotificationListenerService::class.java)
            mgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
            attachToBestController(mgr.getActiveSessions(comp))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        runCatching { currentController?.unregisterCallback(controllerCallback) }
        currentController = null
        MediaSessionBridge.updateFromController(applicationContext, null)
        MediaSessionBridge.refreshAccessStatus(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Session changes are handled directly by OnActiveSessionsChangedListener
    }

    private fun attachToBestController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull {
            it.packageName != packageName &&
                it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull { it.packageName != packageName }

        if (currentController?.sessionToken != chosen?.sessionToken) {
            runCatching { currentController?.unregisterCallback(controllerCallback) }
            currentController = chosen
            chosen?.registerCallback(controllerCallback)
        }
        MediaSessionBridge.updateFromController(applicationContext, chosen)
    }
}

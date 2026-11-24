package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.example.AAVisualizerApp

class AudioEffectSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val app = context.applicationContext as? AAVisualizerApp ?: return

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (sessionId > 0 && pkg != context.packageName) {
                    MediaSessionBridge.updateAudioSessionId(sessionId)
                    app.container.onExternalAudioSessionOpened(sessionId, pkg)
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (sessionId > 0 && pkg != context.packageName) {
                    MediaSessionBridge.updateAudioSessionId(0)
                }
            }
        }
    }
}

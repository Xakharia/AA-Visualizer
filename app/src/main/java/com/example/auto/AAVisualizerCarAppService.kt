package com.example.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.AAVisualizerApp
import com.example.model.ProfileCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Compliant Android Auto Car App Library Service (androidx.car.app).
 * Note: Android Auto prohibits arbitrary 60 FPS Canvas/OpenGL visualizers on the car head-unit.
 * This service exposes distraction-optimized Track + Lyric status, EQ profile switching, and DSP controls.
 */
class AAVisualizerCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
    }

    override fun onCreateSession(): Session = AAVisualizerSession()
}

class AAVisualizerSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return CarDashboardScreen(carContext)
    }
}

class CarDashboardScreen(carContext: CarContext) : Screen(carContext) {
    private val app = carContext.applicationContext as AAVisualizerApp
    private val container = app.container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                scope.launch {
                    container.currentPlayback.collect { invalidate() }
                }
                scope.launch {
                    container.activeProfile.collect { invalidate() }
                }
                scope.launch {
                    container.lyricsState.collect { invalidate() }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val meta = container.currentPlayback.value
        val profile = container.activeProfile.value
        val eqOn = container.eqEnabled.value
        val lyrics = container.lyricsState.value
        val currentLyric = lyrics.lyrics?.lines?.getOrNull(lyrics.activeLineIndex)?.text
            ?: if (lyrics.isLoading) "Fetching synchronized lyrics..." else "Audio-reactive visualizer active on phone display"

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("${meta.title} — ${meta.artist}")
                    .addText("Source: ${meta.appLabel} • ${container.outputMonitor.currentDevice.value.shortLabel}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("EQ: ${profile.name} (${if (eqOn) "Active" else "Bypassed"})")
                    .addText("Bass ${profile.bassBoost / 10}% • Smart Bass ${if (profile.smartBassEnabled) "ON" else "OFF"}")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Lyric Line")
                    .addText(currentLyric)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Switch EQ Profile")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        screenManager.push(CarEqProfilesScreen(carContext))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (eqOn) "Bypass EQ" else "Enable EQ")
                    .setOnClickListener {
                        scope.launch {
                            container.repository.setEqEnabled(!eqOn)
                            invalidate()
                        }
                    }
                    .build()
            )

        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle("AA Visualizer")
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(header)
            .build()
    }
}

class CarEqProfilesScreen(carContext: CarContext) : Screen(carContext) {
    private val app = carContext.applicationContext as AAVisualizerApp
    private val container = app.container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var selectedCategory: ProfileCategory = ProfileCategory.MUSIC

    override fun onGetTemplate(): Template {
        val profiles = container.profiles.value.filter { it.category == selectedCategory }
        val activeId = container.activeProfileId.value

        val listBuilder = ItemList.Builder()
        profiles.take(12).forEach { profile ->
            val marker = if (profile.id == activeId) " (Active)" else ""
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${profile.name}$marker")
                    .addText("Gain ${profile.masterGainDb} dB • Bass ${profile.bassBoost / 10}%")
                    .setOnClickListener {
                        scope.launch {
                            container.repository.setActiveProfileId(profile.id)
                            screenManager.pop()
                        }
                    }
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(selectedCategory.label)
                    .setOnClickListener {
                        val cats = ProfileCategory.entries
                        selectedCategory = cats[(cats.indexOf(selectedCategory) + 1) % cats.size]
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("EQ Profiles • ${selectedCategory.label}")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }
}

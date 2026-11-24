package com.example.integration

import android.service.dreams.DreamService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.AAVisualizerApp
import com.example.model.VisualizerConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.visualizer.renderVisualizerFrame
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VisualizerDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val app = applicationContext as AAVisualizerApp
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@VisualizerDreamService)
            setViewTreeSavedStateRegistryOwner(this@VisualizerDreamService)
            setContent {
                DreamVisualizerContent(app)
            }
        }
        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

@Composable
private fun DreamVisualizerContent(app: AAVisualizerApp) {
    val frame by app.container.analysisEngine.analysisFrame.collectAsStateWithLifecycle()
    val config by app.container.repository.visualizerConfig.collectAsStateWithLifecycle(initialValue = VisualizerConfig())
    val meta by app.container.currentPlayback.collectAsStateWithLifecycle()

    var phase by remember { mutableFloatStateOf(0f) }
    val peaks = remember { FloatArray(64) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(config.animationSpeed) {
        while (true) {
            withFrameNanos {
                phase += 0.016f * config.animationSpeed
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val effectiveCfg = config.copy(
        useOledBlackBg = true,
        brightness = config.screensaverBrightness.coerceIn(0.2f, 1.0f)
    )
    val timeText = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderVisualizerFrame(frame, effectiveCfg, phase, peaks)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp)
        ) {
            if (config.showClockOverlay) {
                Text(
                    text = timeText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 42.sp,
                    color = Color.White.copy(alpha = config.screensaverBrightness)
                )
            }
            if (config.showTrackOverlay) {
                Text(
                    text = "${meta.title} • ${meta.artist}",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.65f * config.screensaverBrightness)
                )
            }
        }
    }
}

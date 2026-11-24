package com.example.ui.settings

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.integration.VisualizerWallpaperService
import com.example.model.AppThemeStyle
import com.example.model.AudioAnalysisFrame
import com.example.model.AudioInputSource
import com.example.model.PlaybackMetadata
import com.example.model.VisualizerConfig
import com.example.ui.theme.AccentPaletteOptions
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.visualizer.renderVisualizerFrame
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndToolsScreen(
    activeSource: AudioInputSource,
    engineStatus: String,
    notificationAccessGranted: Boolean,
    config: VisualizerConfig,
    frame: AudioAnalysisFrame,
    playback: PlaybackMetadata,
    screensaverPreview: Boolean,
    onSetScreensaverPreview: (Boolean) -> Unit,
    onSelectInputSource: (AudioInputSource) -> Unit,
    onMediaProjectionResult: (Int, Intent) -> Unit,
    onUpdateConfig: ((VisualizerConfig) -> VisualizerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) onSelectInputSource(activeSource)
        }
    )

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                onMediaProjectionResult(result.resultCode, data)
            }
        }
    )

    if (screensaverPreview) {
        BackHandler { onSetScreensaverPreview(false) }
        ScreensaverPreviewOverlay(
            frame = frame,
            config = config,
            playback = playback,
            onDismiss = { onSetScreensaverPreview(false) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Audio Input Source Selection
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audio Analysis Input Source", style = MaterialTheme.typography.titleMedium)
                Text(
                    engineStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                AudioInputSource.entries.forEach { src ->
                    val selected = src == activeSource
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (src) {
                                    AudioInputSource.BUILTIN_GROOVE -> onSelectInputSource(src)
                                    AudioInputSource.EXTERNAL_SESSION,
                                    AudioInputSource.MICROPHONE -> {
                                        onSelectInputSource(src)
                                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                    AudioInputSource.PLAYBACK_CAPTURE -> {
                                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                                    }
                                }
                            }
                            .testTag("input_source_${src.name}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(src.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                src.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // System Permissions & Background Monitoring
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Media Detection & Permissions", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (notificationAccessGranted) "Notification Access: Granted (Spotify / Poweramp / YT Music monitored)"
                    else "Notification Access: Not granted yet. Required to detect external players.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notificationAccessGranted) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Media Access")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Notification Perm")
                        }
                    }
                }
            }
        }

        // Personalization & Theme Styles
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Personalization & Visual Style", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppThemeStyle.entries) { style ->
                        FilterChip(
                            selected = config.themeStyle == style,
                            onClick = { onUpdateConfig { it.copy(themeStyle = style) } },
                            label = { Text(style.label) }
                        )
                    }
                }
                Text("Custom Accent Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPaletteOptions.forEach { (_, hex) ->
                        val selected = config.customAccentHex == hex
                        Box(
                            modifier = Modifier
                                .size(if (selected) 36.dp else 30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { onUpdateConfig { it.copy(customAccentHex = hex) } }
                        )
                    }
                }
            }
        }

        // Home Screen, Charging Screensaver & Android Auto Info
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live Wallpaper, Screensaver & Car", style = MaterialTheme.typography.titleMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Charging Screensaver Brightness", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${(config.screensaverBrightness * 100).toInt()}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = config.screensaverBrightness,
                    onValueChange = { v -> onUpdateConfig { it.copy(screensaverBrightness = v) } },
                    valueRange = 0.2f..1.0f
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, VisualizerWallpaperService::class.java)
                                    )
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Live Wallpaper")
                    }
                    Button(
                        onClick = { onSetScreensaverPreview(true) },
                        modifier = Modifier.weight(1f).testTag("preview_screensaver_button")
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Screensaver")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Add the AA Visualizer Home Widget from your launcher's widget picker for 1-tap EQ cycling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Android Auto CarAppService exposes Track/Lyric info & EQ profile switching via templated Car UI; full 60 FPS visualizers run on the Car Dashboard phone display.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScreensaverPreviewOverlay(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    playback: PlaybackMetadata,
    onDismiss: () -> Unit
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val peaks = remember { FloatArray(64) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(config.animationSpeed) {
        while (true) {
            withFrameNanos { phase += 0.016f * config.animationSpeed }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val clock = remember(nowMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs)) }
    val oledCfg = config.copy(useOledBlackBg = true, brightness = config.screensaverBrightness)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderVisualizerFrame(frame, oledCfg, phase, peaks)
        }
        Column(modifier = Modifier.align(Alignment.TopStart).padding(28.dp)) {
            Text(
                clock,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 44.sp,
                color = Color.White.copy(alpha = config.screensaverBrightness)
            )
            Text(
                "${playback.title} • ${playback.artist}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f * config.screensaverBrightness)
            )
        }
        Text(
            "Tap anywhere to exit OLED Charging Screensaver",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

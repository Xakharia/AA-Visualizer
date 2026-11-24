package com.example.ui.visualizer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.BUILTIN_DEMO_TRACKS
import com.example.model.AudioAnalysisFrame
import com.example.model.AudioInputSource
import com.example.model.LyricsUiState
import com.example.model.PlaybackMetadata
import com.example.model.VisualizerCategory
import com.example.model.VisualizerColorScheme
import com.example.model.VisualizerConfig
import com.example.model.VisualizerStyle
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerScreen(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    playback: PlaybackMetadata,
    lyricsState: LyricsUiState,
    albumBitmap: Bitmap?,
    albumColors: Pair<Color?, Color?>,
    activeSource: AudioInputSource,
    engineStatus: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onPlayDemoTrack: (Int) -> Unit,
    onPickLocalFile: (Uri) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onUpdateConfig: ((VisualizerConfig) -> VisualizerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val peaks = remember { FloatArray(64) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<VisualizerCategory?>(null) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onPickLocalFile) }
    )

    LaunchedEffect(config.animationSpeed) {
        while (true) {
            withFrameNanos {
                phase += 0.016f * config.animationSpeed
            }
        }
    }

    if (isFullscreen) {
        BackHandler { onToggleFullscreen() }
        FullscreenVisualizerStage(
            frame = frame,
            config = config,
            playback = playback,
            lyricsState = lyricsState,
            albumBitmap = albumBitmap,
            albumColors = albumColors,
            phase = phase,
            peaks = peaks,
            onExitFullscreen = onToggleFullscreen,
            onTogglePlayPause = onTogglePlayPause
        )
        return
    }

    val filteredStyles = remember(selectedCategory) {
        if (selectedCategory == null) VisualizerStyle.entries
        else VisualizerStyle.entries.filter { it.category == selectedCategory }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VisualizerStageCard(
                    frame = frame,
                    config = config,
                    playback = playback,
                    albumBitmap = albumBitmap,
                    albumColors = albumColors,
                    phase = phase,
                    peaks = peaks,
                    engineStatus = engineStatus,
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenCustomize = { showCustomizeSheet = true },
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlaybackControlCard(
                        playback = playback,
                        activeSource = activeSource,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevTrack = onPrevTrack,
                        onNextTrack = onNextTrack,
                        onPlayDemo = onPlayDemoTrack,
                        onOpenFilePicker = { audioPicker.launch(arrayOf("audio/*")) }
                    )
                    StyleSelectorSection(
                        styles = filteredStyles,
                        selectedStyleIndex = config.styleIndex,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        onSelectStyle = onSelectStyle
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VisualizerStageCard(
                    frame = frame,
                    config = config,
                    playback = playback,
                    albumBitmap = albumBitmap,
                    albumColors = albumColors,
                    phase = phase,
                    peaks = peaks,
                    engineStatus = engineStatus,
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenCustomize = { showCustomizeSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                )

                PlaybackControlCard(
                    playback = playback,
                    activeSource = activeSource,
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevTrack = onPrevTrack,
                    onNextTrack = onNextTrack,
                    onPlayDemo = onPlayDemoTrack,
                    onOpenFilePicker = { audioPicker.launch(arrayOf("audio/*")) }
                )

                StyleSelectorSection(
                    styles = filteredStyles,
                    selectedStyleIndex = config.styleIndex,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    onSelectStyle = onSelectStyle
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(onDismissRequest = { showCustomizeSheet = false }) {
            VisualizerCustomizationSheet(
                config = config,
                onUpdateConfig = onUpdateConfig
            )
        }
    }
}

@Composable
private fun VisualizerStageCard(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    playback: PlaybackMetadata,
    albumBitmap: Bitmap?,
    albumColors: Pair<Color?, Color?>,
    phase: Float,
    peaks: FloatArray,
    engineStatus: String,
    onToggleFullscreen: () -> Unit,
    onOpenCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (config.useOledBlackBg) Color.Black else Color(config.backgroundHex)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (config.showAlbumArtBg && albumBitmap != null && !config.useOledBlackBg) {
                Image(
                    bitmap = albumBitmap.asImageBitmap(),
                    contentDescription = "Album artwork backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.2f)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                renderVisualizerFrame(
                    frame = frame,
                    config = config,
                    phase = phase,
                    peaks = peaks,
                    albumPrimary = albumColors.first,
                    albumSecondary = albumColors.second
                )
            }

            // Top telemetry + controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "${config.activeStyle.title} • ${frame.peakDb.toInt()} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenCustomize,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("customize_visualizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Customize visualizer",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("fullscreen_visualizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Enter fullscreen visualizer",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom frequency energy strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playback.artist} • $engineStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnergyBadge("BASS", frame.bass, MaterialTheme.colorScheme.primary)
                    EnergyBadge("MID", frame.mid, MaterialTheme.colorScheme.secondary)
                    EnergyBadge("HIGH", frame.treble, MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun EnergyBadge(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
        Text(
            "${(value * 100).toInt()}%",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun PlaybackControlCard(
    playback: PlaybackMetadata,
    activeSource: AudioInputSource,
    onTogglePlayPause: () -> Unit,
    onPrevTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPlayDemo: (Int) -> Unit,
    onOpenFilePicker: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playback.appLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playback.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevTrack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track")
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNextTrack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track")
                    }
                }
            }

            // Quick synth tracks + local audio file button for instant live testing
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenFilePicker,
                        label = { Text("Pick Audio File") },
                        leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pick_audio_file_chip")
                    )
                }
                items(BUILTIN_DEMO_TRACKS) { demo ->
                    val isCurrent = playback.title == demo.title && activeSource == AudioInputSource.BUILTIN_GROOVE
                    FilterChip(
                        selected = isCurrent,
                        onClick = { onPlayDemo(demo.id) },
                        label = { Text(demo.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleSelectorSection(
    styles: List<VisualizerStyle>,
    selectedStyleIndex: Int,
    selectedCategory: VisualizerCategory?,
    onSelectCategory: (VisualizerCategory?) -> Unit,
    onSelectStyle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "24 Visualizer Engines",
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All (24)") }
                )
            }
            items(VisualizerCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat.label) }
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(styles, key = { it.index }) { style ->
                val selected = style.index == selectedStyleIndex
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .width(156.dp)
                        .clickable { onSelectStyle(style.index) }
                        .testTag("visualizer_style_${style.index}")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "#${style.index + 1} • ${style.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = style.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizerCustomizationSheet(
    config: VisualizerConfig,
    onUpdateConfig: ((VisualizerConfig) -> VisualizerConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Visualizer Customization", style = MaterialTheme.typography.titleLarge)

        Text("Color Scheme", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VisualizerColorScheme.entries) { scheme ->
                FilterChip(
                    selected = config.colorScheme == scheme,
                    onClick = { onUpdateConfig { it.copy(colorScheme = scheme) } },
                    label = { Text(scheme.label) }
                )
            }
        }

        ToggleRow("Pure Black OLED Background", config.useOledBlackBg) { checked ->
            onUpdateConfig { it.copy(useOledBlackBg = checked) }
        }
        ToggleRow("Album Artwork Backdrop", config.showAlbumArtBg) { checked ->
            onUpdateConfig { it.copy(showAlbumArtBg = checked) }
        }
        ToggleRow("Fullscreen Track Info", config.showTrackOverlay) { checked ->
            onUpdateConfig { it.copy(showTrackOverlay = checked) }
        }
        ToggleRow("Fullscreen Synced Lyrics", config.showLyricsOverlay) { checked ->
            onUpdateConfig { it.copy(showLyricsOverlay = checked) }
        }
        ToggleRow("Fullscreen Digital Clock", config.showClockOverlay) { checked ->
            onUpdateConfig { it.copy(showClockOverlay = checked) }
        }

        SliderParam("Intensity", config.intensity, 0.3f..2.0f) { v ->
            onUpdateConfig { it.copy(intensity = v) }
        }
        SliderParam("Sensitivity", config.sensitivity, 0.3f..2.5f) { v ->
            onUpdateConfig { it.copy(sensitivity = v) }
        }
        SliderParam("Frequency Response Tilt", config.frequencyTilt, -1.0f..1.0f) { v ->
            onUpdateConfig { it.copy(frequencyTilt = v) }
        }
        SliderParam("Animation Speed", config.animationSpeed, 0.3f..2.0f) { v ->
            onUpdateConfig { it.copy(animationSpeed = v) }
        }
        SliderParam("Brightness", config.brightness, 0.2f..1.0f) { v ->
            onUpdateConfig { it.copy(brightness = v) }
        }
        SliderParam("Transparency", config.transparency, 0.2f..1.0f) { v ->
            onUpdateConfig { it.copy(transparency = v) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderParam(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.2f".format(Locale.US, value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun FullscreenVisualizerStage(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    playback: PlaybackMetadata,
    lyricsState: LyricsUiState,
    albumBitmap: Bitmap?,
    albumColors: Pair<Color?, Color?>,
    phase: Float,
    peaks: FloatArray,
    onExitFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    var showHud by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val clockStr = remember(nowMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs)) }
    val activeLyric = lyricsState.lyrics?.lines?.getOrNull(lyricsState.activeLineIndex)?.text

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (config.useOledBlackBg) Color.Black else Color(config.backgroundHex))
            .clickable { showHud = !showHud }
    ) {
        if (config.showAlbumArtBg && albumBitmap != null && !config.useOledBlackBg) {
            Image(
                bitmap = albumBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f)
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderVisualizerFrame(frame, config, phase, peaks, albumColors.first, albumColors.second)
        }

        AnimatedVisibility(
            visible = showHud,
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .testTag("exit_fullscreen_button")
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (config.showClockOverlay) {
                Text(clockStr, fontFamily = JetBrainsMonoFontFamily, fontSize = 28.sp, color = Color.White.copy(alpha = 0.9f))
            }
            if (config.showTrackOverlay) {
                Text(playback.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(playback.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.78f))
            }
            if (config.showLyricsOverlay && !activeLyric.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = activeLyric,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

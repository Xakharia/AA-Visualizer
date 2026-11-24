package com.example.ui.dashboard

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioAnalysisFrame
import com.example.model.CarScreenPreset
import com.example.model.EqProfile
import com.example.model.LyricsUiState
import com.example.model.OutputDeviceType
import com.example.model.PlaybackMetadata
import com.example.model.VisualizerConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.visualizer.renderVisualizerFrame
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CarDashboardScreen(
    frame: AudioAnalysisFrame,
    config: VisualizerConfig,
    playback: PlaybackMetadata,
    activeProfile: EqProfile,
    currentDevice: OutputDeviceType,
    lyricsState: LyricsUiState,
    albumBitmap: Bitmap?,
    albumColors: Pair<Color?, Color?>,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onCycleEqProfile: () -> Unit,
    onUpdateConfig: ((VisualizerConfig) -> VisualizerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val peaks = remember { FloatArray(64) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var carFullscreen by remember { mutableStateOf(false) }

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

    if (carFullscreen) {
        BackHandler { carFullscreen = false }
    }

    val clockText = remember(nowMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs)) }
    val currentLyric = lyricsState.lyrics?.lines?.getOrNull(lyricsState.activeLineIndex)?.text
        ?: if (lyricsState.isLoading) "Loading synced lyrics..." else "Audio-reactive automotive dashboard active"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (carFullscreen) 8.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!carFullscreen) {
            // Android Auto Screen Size Data Presets (Portrait & Landscape Adaptive Modes)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(CarScreenPreset.entries) { preset ->
                    FilterChip(
                        selected = config.carScreenPreset == preset,
                        onClick = { onUpdateConfig { it.copy(carScreenPreset = preset) } },
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("car_preset_${preset.name}")
                    )
                }
            }
        }

        val presetRatio = config.carScreenPreset.aspectRatio
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val containerModifier = if (presetRatio != null) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(presetRatio, matchHeightConstraintsFirst = presetRatio < 1f)
            } else {
                Modifier.fillMaxSize()
            }

            Surface(
                modifier = containerModifier,
                shape = RoundedCornerShape(24.dp),
                color = if (config.useOledBlackBg) Color.Black else Color(0xFF060912),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideCarLayout = maxWidth > maxHeight
                    val isUltraWide = (maxWidth / maxHeight.coerceAtLeast(1.dp)) >= 1.9f

                    if (config.showAlbumArtBg && albumBitmap != null && !config.useOledBlackBg) {
                        Image(
                            bitmap = albumBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.16f)
                        )
                    }

                    if (isWideCarLayout) {
                        // Landscape / Android Auto Widescreen Dashboard Layout
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Left/Main Visualizer Stage
                            Box(
                                modifier = Modifier
                                    .weight(if (isUltraWide) 1.55f else 1.25f)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    renderVisualizerFrame(frame, config, phase, peaks, albumColors.first, albumColors.second)
                                }
                                if (config.showClockOverlay) {
                                    Text(
                                        text = clockText,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 22.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                    )
                                }
                            }

                            // Right Driver-Friendly Info & Large Controls Pane
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        AlbumThumb(albumBitmap)
                                        Column {
                                            Text(
                                                playback.title,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${playback.artist} • ${currentDevice.shortLabel}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.72f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { carFullscreen = !carFullscreen },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            if (carFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = "Toggle car fullscreen",
                                            tint = Color.White
                                        )
                                    }
                                }

                                if (config.showLyricsOverlay) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            text = currentLyric,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                                        )
                                    }
                                }

                                CarTransportBar(
                                    isPlaying = playback.isPlaying,
                                    activeProfileName = activeProfile.name,
                                    onPrev = onPrevTrack,
                                    onPlayPause = onTogglePlayPause,
                                    onNext = onNextTrack,
                                    onCycleEq = onCycleEqProfile
                                )
                            }
                        }
                    } else {
                        // Portrait Automotive / Split Layout
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = if (config.showClockOverlay) "$clockText • ${currentDevice.shortLabel}" else currentDevice.shortLabel,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { carFullscreen = !carFullscreen },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        if (carFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Toggle car fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                            ) {
                                renderVisualizerFrame(frame, config, phase, peaks, albumColors.first, albumColors.second)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AlbumThumb(albumBitmap)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playback.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        playback.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (config.showLyricsOverlay) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = currentLyric,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    )
                                }
                            }

                            CarTransportBar(
                                isPlaying = playback.isPlaying,
                                activeProfileName = activeProfile.name,
                                onPrev = onPrevTrack,
                                onPlayPause = onTogglePlayPause,
                                onNext = onNextTrack,
                                onCycleEq = onCycleEqProfile
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumThumb(bitmap: Bitmap?) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CarTransportBar(
    isPlaying: Boolean,
    activeProfileName: String,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onCycleEq: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
            }
        }

        OutlinedButton(
            onClick = onCycleEq,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = " $activeProfileName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

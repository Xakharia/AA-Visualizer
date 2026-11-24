package com.example.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricsUiState
import com.example.model.PlaybackMetadata
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun LyricsScreen(
    playback: PlaybackMetadata,
    lyricsState: LyricsUiState,
    autoLyricsEnabled: Boolean,
    onSetAutoLyrics: (Boolean) -> Unit,
    onRefreshLyrics: (String, String, Int) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var fullscreenLyrics by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(lyricsState.activeLineIndex) {
        if (lyricsState.activeLineIndex >= 0) {
            runCatching {
                listState.animateScrollToItem((lyricsState.activeLineIndex - 2).coerceAtLeast(0))
            }
        }
    }

    if (fullscreenLyrics) {
        BackHandler { fullscreenLyrics = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp)
        ) {
            LyricsListContent(
                lyricsState = lyricsState,
                playback = playback,
                listState = listState,
                isFullscreen = true,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { fullscreenLyrics = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen lyrics", tint = Color.White)
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track & LRCLIB Header
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playback.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playback.artist} • LRCLIB (metadata minimized)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { showSearchDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("search_lyrics_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search LRCLIB lyrics")
                        }
                        IconButton(
                            onClick = {
                                onRefreshLyrics(
                                    playback.title,
                                    playback.artist,
                                    (playback.durationMs / 1000L).toInt()
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh lyrics")
                        }
                        IconButton(
                            onClick = { fullscreenLyrics = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("fullscreen_lyrics_button")
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen lyrics")
                        }
                    }
                }

                if (playback.durationMs > 0L) {
                    val progress = (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onAdjustOffset(-500L) }) { Text("-0.5s") }
                        Text(
                            text = "Sync ${"%+.1f".format(Locale.US, lyricsState.syncOffsetMs / 1000f)}s",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(onClick = { onAdjustOffset(500L) }) { Text("+0.5s") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Auto", style = MaterialTheme.typography.labelLarge)
                        Switch(checked = autoLyricsEnabled, onCheckedChange = onSetAutoLyrics)
                    }
                }
            }
        }

        // Synchronized Lyrics Body
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LyricsListContent(
                lyricsState = lyricsState,
                playback = playback,
                listState = listState,
                isFullscreen = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showSearchDialog) {
        ManualLyricsSearchDialog(
            initialTitle = playback.title,
            initialArtist = playback.artist,
            onDismiss = { showSearchDialog = false },
            onSearch = { title, artist ->
                onRefreshLyrics(title, artist, 0)
                showSearchDialog = false
            }
        )
    }
}

@Composable
private fun LyricsListContent(
    lyricsState: LyricsUiState,
    playback: PlaybackMetadata,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        lyricsState.isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Fetching synchronized LRC from LRCLIB...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        lyricsState.lyrics == null || lyricsState.lyrics.lines.isEmpty() -> {
            Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = lyricsState.errorMessage ?: "Start playback or search a track title to load timed lyrics.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            val lines = lyricsState.lyrics.lines
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = modifier
            ) {
                itemsIndexed(lines) { index, line ->
                    val isActive = index == lyricsState.activeLineIndex
                    val mm = (line.timeMs / 60_000L)
                    val ss = (line.timeMs % 60_000L) / 1000L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isFullscreen) {
                            Text(
                                text = "%02d:%02d".format(Locale.US, mm, ss),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.titleLarge.copy(
                                    fontSize = if (isFullscreen) 26.sp else 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = if (isFullscreen) 18.sp else 15.sp
                                )
                            },
                            color = when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isFullscreen -> Color.White.copy(alpha = 0.48f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualLyricsSearchDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onSearch: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search LRCLIB Lyrics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onSearch(title, artist) }) { Text("Fetch LRC") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

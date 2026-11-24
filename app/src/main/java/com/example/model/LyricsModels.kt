package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Immutable
data class SyncedLyrics(
    val trackTitle: String,
    val artistName: String,
    val durationSec: Int,
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val source: String = "LRCLIB"
)

@Immutable
data class LyricsUiState(
    val isLoading: Boolean = false,
    val lyrics: SyncedLyrics? = null,
    val activeLineIndex: Int = -1,
    val syncOffsetMs: Long = 0L,
    val errorMessage: String? = null
)

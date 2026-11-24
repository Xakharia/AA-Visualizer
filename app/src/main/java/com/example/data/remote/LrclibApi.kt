package com.example.data.remote

import com.example.model.LyricLine
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class LrclibTrackDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "duration") val duration: Double? = null,
    @Json(name = "plainLyrics") val plainLyrics: String? = null,
    @Json(name = "syncedLyrics") val syncedLyrics: String? = null
)

interface LrclibService {
    @GET("api/get")
    suspend fun getExactLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") durationSeconds: Int?
    ): Response<LrclibTrackDto>

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): Response<List<LrclibTrackDto>>
}

object LrclibClient {
    val api: LrclibService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "AAVisualizer/1.0 (Android Audio Companion)")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrclibService::class.java)
    }
}

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

    fun parse(syncedLrc: String?, plainLyrics: String?, durationSec: Int): List<LyricLine> {
        if (!syncedLrc.isNullOrBlank()) {
            val result = mutableListOf<LyricLine>()
            syncedLrc.lineSequence().forEach { rawLine ->
                val matches = lineRegex.findAll(rawLine).toList()
                if (matches.isNotEmpty()) {
                    val text = matches.last().groupValues[4].trim()
                    if (text.isNotEmpty()) {
                        matches.forEach { m ->
                            val min = m.groupValues[1].toLongOrNull() ?: 0L
                            val sec = m.groupValues[2].toLongOrNull() ?: 0L
                            val fracRaw = m.groupValues[3]
                            val millis = when (fracRaw.length) {
                                0 -> 0L
                                1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                                2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                                else -> fracRaw.take(3).toLongOrNull() ?: 0L
                            }
                            result += LyricLine(min * 60_000L + sec * 1_000L + millis, text)
                        }
                    }
                }
            }
            if (result.isNotEmpty()) {
                return result.sortedBy { it.timeMs }
            }
        }

        if (!plainLyrics.isNullOrBlank()) {
            val lines = plainLyrics.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val totalMs = (durationSec.coerceAtLeast(30) * 1000L)
                val stepMs = totalMs / lines.size.coerceAtLeast(1)
                return lines.mapIndexed { idx, line ->
                    LyricLine(timeMs = idx * stepMs, text = line)
                }
            }
        }
        return emptyList()
    }
}

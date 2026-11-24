package com.example

import com.example.audio.FftProcessor
import com.example.data.remote.LrcParser
import com.example.model.EQ_BANDS_10
import com.example.model.PredefinedProfiles
import com.example.model.ProfileCategory
import com.example.model.VisualizerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun predefinedProfilesCountAndBandsAreValid() {
        val all = PredefinedProfiles.all
        assertEquals(34, all.size)
        assertEquals(22, all.count { it.category == ProfileCategory.MUSIC })
        assertEquals(6, all.count { it.category == ProfileCategory.PODCAST })
        assertEquals(6, all.count { it.category == ProfileCategory.MOVIE })
        assertEquals(10, EQ_BANDS_10.size)
        assertEquals(16000, EQ_BANDS_10.last().centerFreqHz)
        assertTrue(all.all { it.bandsDb.size == 10 })
    }

    @Test
    fun visualizerCollectionHas24UniqueStyles() {
        val styles = VisualizerStyle.entries
        assertEquals(24, styles.size)
        assertEquals(24, styles.map { it.index }.distinct().size)
    }

    @Test
    fun lrcParserExtractsSyncedLinesInOrder() {
        val sampleLrc = """
            [00:12.50]First line here
            [00:05.20]Intro line
            [01:02.00]Chorus begins
        """.trimIndent()
        val parsed = LrcParser.parse(sampleLrc, null, 180)
        assertEquals(3, parsed.size)
        assertEquals(5_200L, parsed[0].timeMs)
        assertEquals("Intro line", parsed[0].text)
        assertEquals(12_500L, parsed[1].timeMs)
        assertEquals(62_000L, parsed[2].timeMs)
    }

    @Test
    fun fftProcessorDetectsAudioEnergy() {
        val fft = FftProcessor(1024, 44100)
        val pcm = ShortArray(1024) { i ->
            (sin(2.0 * PI * 440.0 * i / 44100.0) * 16000.0).toInt().toShort()
        }
        val frame = fft.processPcm16(pcm, pcm.size)
        assertTrue(frame.rms > 0.1f)
        assertTrue(frame.spectrum.any { it > 0.1f })
    }
}

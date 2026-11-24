package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        composeRule.mainClock.autoAdvance = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AA Visualizer", appName)
    }

    @Test
    fun `main tabs render and navigate cleanly`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_equalizer").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("master_eq_switch").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_lyrics").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("search_lyrics_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_car_dashboard").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("car_preset_AA_ULTRAWIDE").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("preview_screensaver_button")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

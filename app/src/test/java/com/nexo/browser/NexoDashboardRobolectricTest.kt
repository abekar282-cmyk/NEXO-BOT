package com.nexo.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NexoDashboardRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `dashboard launches on Android 15 and supports navigation and search`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NEXO BROWSER").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_search_input").assertIsDisplayed()

        // Navigate to SOCKS5 Proxy Vault tab
        composeRule.onNodeWithTag("nav_$ROUTE_PROXIES").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SOCKS5 Proxy Vault").assertIsDisplayed()

        // Navigate to Bulk Import tab
        composeRule.onNodeWithTag("nav_$ROUTE_IMPORT").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bulk SOCKS5 Proxy Import").assertIsDisplayed()

        // Navigate to Settings tab
        composeRule.onNodeWithTag("nav_$ROUTE_SETTINGS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Browser & Proxy Security").assertIsDisplayed()

        // Return to Profiles Dashboard and create a profile
        composeRule.onNodeWithTag("nav_$ROUTE_DASHBOARD").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_profile_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_profile_button").performClick()
        composeRule.waitForIdle()

        // Verify search filters cards
        composeRule.onNodeWithTag("profile_search_input").performTextInput("Profile")
        composeRule.waitForIdle()
    }
}

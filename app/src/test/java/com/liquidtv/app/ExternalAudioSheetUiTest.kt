package com.liquidtv.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.ui.screens.player.ExternalAudioSheet
import com.liquidtv.app.ui.theme.LiquidTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class ExternalAudioSheetUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ExternalAudioSheet renders channels, filters, sync, volume, and detach actions`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var selectedFilter = AudioBrandFilter.ALL
        var detachedCalled = false
        var offsetDelta = 0L

        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        channels = channels,
                        activeChannel = channels[0],
                        status = ExternalAudioStatus.ACTIVE,
                        statusMessage = "نشط: beIN SPORTS 1 HD",
                        selectedBrandFilter = selectedFilter,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = { detachedCalled = true },
                    onRefreshCatalog = {},
                    onSearchQueryChange = {},
                    onSelectBrandFilter = { selectedFilter = it },
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = { offsetDelta += it },
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("external_audio_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("beIN SPORTS 1 HD").assertIsDisplayed()
        composeRule.onNodeWithText("ALWAN 2 FHD").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_sync_plus").performClick()
        composeRule.onNodeWithTag("external_audio_detach_button").performClick()
        composeRule.onNodeWithTag("external_audio_filter_alwan").performClick()

        assertEquals(ExternalAudioSyncPolicy.STEP_MS, offsetDelta)
        assertTrue(detachedCalled)
        assertEquals(AudioBrandFilter.ALWAN, selectedFilter)
    }
}

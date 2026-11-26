package com.liquidtv.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.player.PlayerUiState
import com.liquidtv.app.ui.screens.player.PlayerOverlaySheetDefaults
import com.liquidtv.app.ui.screens.player.PlayerSubtitlesAndTracksSheet
import kotlin.math.abs
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


    @Test
    fun `ExternalAudioSheet and PlayerSubtitlesAndTracksSheet share exact compact dimensions across all states on TV`() {
        verifyUnifiedCompactDimensions(expectedMaxWidthDp = 440f, expectedMaxHeightDp = 440f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w390dp-h800dp-xhdpi")
    fun `ExternalAudioSheet and PlayerSubtitlesAndTracksSheet share exact compact dimensions on Phone Portrait`() {
        verifyUnifiedCompactDimensions(expectedMaxWidthDp = 390f, expectedMaxHeightDp = 440f)
    }

    @Test
    fun `ExternalAudioSheet search input filters and updates cleanly without clipping`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var latestQuery by mutableStateOf("")
        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        channels = channels,
                        searchQuery = latestQuery,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = {},
                    onRefreshCatalog = {},
                    onSearchQueryChange = { latestQuery = it },
                    onSelectBrandFilter = {},
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithTag("external_audio_search_input").performTextInput("ALWAN")
        composeRule.waitForIdle()
        assertEquals("ALWAN", latestQuery)
        composeRule.onNodeWithText("ALWAN 2 FHD").assertIsDisplayed()
    }

    private fun verifyUnifiedCompactDimensions(expectedMaxWidthDp: Float, expectedMaxHeightDp: Float) {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var showAudio by mutableStateOf(true)
        var audioUiState by mutableStateOf(
            ExternalAudioUiState(isSheetVisible = true, isLoadingCatalog = true, channels = emptyList()),
        )
        var playerUiState by mutableStateOf(
            PlayerUiState(
                mediaType = MediaType.MOVIE,
                subtitleMetadata = SubtitleMediaMetadata(title = "Test Movie", mediaType = MediaType.MOVIE),
                audioTracks = listOf(TrackInfo(0, 0, "ar", "Arabic Stereo", false)),
                subtitleSearchResults = listOf(
                    OnlineSubtitleResult(url = "http://ex/s1.srt", label = "Arabic WEB-DL Subtitles.srt", language = "ar", addonName = "OpenSubtitles"),
                ),
            ),
        )

        composeRule.setContent {
            LiquidTVTheme {
                if (showAudio) {
                    ExternalAudioSheet(
                        state = audioUiState,
                        onSelectChannel = {},
                        onDetachAudio = {},
                        onRefreshCatalog = {},
                        onSearchQueryChange = {},
                        onSelectBrandFilter = {},
                        onVolumeChange = {},
                        onToggleLoudnessBoost = {},
                        onAdjustSyncOffset = {},
                        onResetSyncOffset = {},
                        onClose = {},
                    )
                } else {
                    PlayerSubtitlesAndTracksSheet(
                        uiState = playerUiState,
                        onSelectAudio = {},
                        onSelectSubtitle = {},
                        onToggleExternalSubtitles = {},
                        onLoadExternalSubtitleUrl = { _, _ -> },
                        onPickLocalSubtitleUri = {},
                        onSelectSearchLanguage = {},
                        onSearchOnlineSubtitles = {},
                        onApplyOnlineSubtitle = {},
                        onUpdateStyle = {},
                        onAdjustDelay = {},
                        onSetDelay = {},
                        onClose = {},
                    )
                }
            }
        }

        // State 1: Audio loading (empty)
        composeRule.waitForIdle()
        val audioLoadingBounds = composeRule.onNodeWithTag("external_audio_sheet").getUnclippedBoundsInRoot()

        // State 2: Audio populated + active
        audioUiState = ExternalAudioUiState(
            isSheetVisible = true,
            isLoadingCatalog = false,
            channels = channels,
            activeChannel = channels[0],
            status = ExternalAudioStatus.ACTIVE,
            statusMessage = "نشط: beIN SPORTS 1 HD",
        )
        composeRule.waitForIdle()
        val audioActiveBounds = composeRule.onNodeWithTag("external_audio_sheet").getUnclippedBoundsInRoot()
        val audioRowBounds = composeRule.onNodeWithTag("external_audio_channel_1").getUnclippedBoundsInRoot()

        // State 3: Subtitles sheet (Tracks tab)
        showAudio = false
        composeRule.waitForIdle()
        val subTracksBounds = composeRule.onNodeWithTag("player_tracks_sheet").getUnclippedBoundsInRoot()
        val subOptionRowBounds = composeRule.onNodeWithTag("disable_all_subtitles_row").getUnclippedBoundsInRoot()

        // State 4: Subtitles sheet (Online Search tab with result card)
        composeRule.onNodeWithTag("player_tab_online_search").performClick()
        composeRule.waitForIdle()
        val subOnlineBounds = composeRule.onNodeWithTag("player_tracks_sheet").getUnclippedBoundsInRoot()
        val onlineResultBounds = composeRule.onNodeWithTag("online_subtitle_result_card").getUnclippedBoundsInRoot()

        // Verify compact size ceiling (much smaller than old 720x620 & 660x580)
        assertTrue(audioActiveBounds.width.value <= expectedMaxWidthDp + 0.5f)
        assertTrue(audioActiveBounds.height.value <= expectedMaxHeightDp + 0.5f)

        // Verify exact width & height match across all states and across both sheets
        assertTrue(abs(audioLoadingBounds.width.value - audioActiveBounds.width.value) < 0.5f)
        assertTrue(abs(audioLoadingBounds.height.value - audioActiveBounds.height.value) < 0.5f)
        assertTrue(abs(audioActiveBounds.width.value - subTracksBounds.width.value) < 0.5f)
        assertTrue(abs(audioActiveBounds.height.value - subTracksBounds.height.value) < 0.5f)
        assertTrue(abs(subTracksBounds.width.value - subOnlineBounds.width.value) < 0.5f)
        assertTrue(abs(subTracksBounds.height.value - subOnlineBounds.height.value) < 0.5f)

        // Verify unified internal card heights (48.dp)
        val expectedRowHeight = PlayerOverlaySheetDefaults.OptionCardHeight.value
        assertTrue(abs(audioRowBounds.height.value - expectedRowHeight) < 0.5f)
        assertTrue(abs(subOptionRowBounds.height.value - expectedRowHeight) < 0.5f)
        assertTrue(abs(onlineResultBounds.height.value - expectedRowHeight) < 0.5f)
    }
}

package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.settings.SettingsViewModel
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.ui.theme.ThemeAtmosphere
import com.liquidtv.app.updater.AppUpdateManager
import com.liquidtv.app.updater.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class ThemeSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            UserPreferencesDataStore(context).setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            UserPreferencesDataStore(context).setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `AppThemeCatalog exposes all 10 original SONIX themes with exact sort order default and HEX tokens`() {
        val themes = AppThemeCatalog.allThemes
        assertEquals(10, themes.size)
        assertEquals((10..19).toList(), themes.map { it.sortOrder })
        assertTrue(themes.all { it.isActive })
        assertEquals(listOf("royal-navy"), themes.filter { it.isDefault }.map { it.id })
        assertEquals("royal-navy", AppThemeCatalog.defaultTheme.id)

        val expectedIds = listOf(
            "royal-navy",
            "celestial-cyan",
            "ruby-velvet",
            "pearl-marble",
            "emerald-palace",
            "midnight-sapphire",
            "carrara-marble",
            "imperial-amethyst",
            "imperial-bronze",
            "black-onyx",
        )
        assertEquals(expectedIds, themes.map { it.id })
        assertEquals(expectedIds, themes.map { it.atmosphere.slug })

        // Light mode classification must hold strictly for Pearl Marble & Carrara Marble
        themes.forEach { theme ->
            val expectLight = theme.id == "pearl-marble" || theme.id == "carrara-marble"
            assertEquals("Light flag mismatch for ${theme.id}", expectLight, theme.isLight)
        }

        // Exact ARGB token verification for every SONIX theme
        val expectedHexTokens = mapOf(
            "royal-navy" to listOf(
                0xFF050B19, 0xFF0B1A33, 0xFF1A3152, 0xFF1B3555, 0xFFF8F6F0, 0xFFC9D5E4,
                0xFF91A4BC, 0xFF0C1A2D, 0xFF132844, 0xFFE4C27A, 0xFF4D6C91, 0xFFB7D8FF,
                0xFF28496C, 0xFFF0787C, 0xFF68D5A0, 0xFF376A9D, 0xFFD5A85E,
            ),
            "celestial-cyan" to listOf(
                0xFF04151F, 0xFF082B3A, 0xFF0B5260, 0xFF124052, 0xFFF2FEFF, 0xFFC0E5E8,
                0xFF82B7BE, 0xFF082432, 0xFF0D3444, 0xFF65E3E8, 0xFF367F8E, 0xFF8AF0F2,
                0xFF1B596B, 0xFFFF7E91, 0xFF65D8AE, 0xFF25A8B1, 0xFFB9D7C7,
            ),
            "ruby-velvet" to listOf(
                0xFF17060D, 0xFF2A0B19, 0xFF54172F, 0xFF42172D, 0xFFFFF5F5, 0xFFE8CDD4,
                0xFFB895A2, 0xFF260D1A, 0xFF351426, 0xFFD9A46D, 0xFF87445D, 0xFFF1B7C9,
                0xFF61233F, 0xFFFF7187, 0xFF72CEA2, 0xFFA13A61, 0xFFD6A16A,
            ),
            "pearl-marble" to listOf(
                0xFFE9E0D1, 0xFFFFFDF7, 0xFFD7C8B3, 0xFFFFFDF8, 0xFF30271F, 0xFF5C5043,
                0xFF897B6B, 0xFFF9F4EA, 0xFFF1E9DC, 0xFFB28A51, 0xFFC9B99F, 0xFFA17A46,
                0xFFE6D8C5, 0xFFB94C53, 0xFF337B5A, 0xFFC7B18D, 0xFFA88C67,
            ),
            "emerald-palace" to listOf(
                0xFF04150F, 0xFF0A261B, 0xFF174A35, 0xFF194331, 0xFFF4FBF4, 0xFFC6D9CB,
                0xFF8EAA98, 0xFF0B2118, 0xFF123326, 0xFFDCC17A, 0xFF46795D, 0xFFB8E6C4,
                0xFF245A40, 0xFFE8797D, 0xFF66D49A, 0xFF2F9068, 0xFFD2AD62,
            ),
            "midnight-sapphire" to listOf(
                0xFF050B1C, 0xFF0B1740, 0xFF1A3670, 0xFF172D57, 0xFFF4F8FF, 0xFFC8D6EC,
                0xFF8FA5C7, 0xFF0A1634, 0xFF112348, 0xFF91B8F1, 0xFF3F6398, 0xFFAFCFFF,
                0xFF244477, 0xFFEF7889, 0xFF68D2A2, 0xFF326CC1, 0xFF8BADE0,
            ),
            "carrara-marble" to listOf(
                0xFFE2E8ED, 0xFFFFFFFF, 0xFFCDD7E0, 0xFFFFFFFF, 0xFF26323D, 0xFF4C5C68,
                0xFF778794, 0xFFF8FAFC, 0xFFEEF2F5, 0xFF7895AC, 0xFFBCC9D3, 0xFF6F8EA8,
                0xFFDFE7ED, 0xFFB84D59, 0xFF2F805E, 0xFF9DB6C8, 0xFFB9A58B,
            ),
            "imperial-amethyst" to listOf(
                0xFF100819, 0xFF21102F, 0xFF432052, 0xFF342144, 0xFFFBF6FF, 0xFFDCCCE8,
                0xFFA998B9, 0xFF1B1027, 0xFF291936, 0xFFD5A8E8, 0xFF725487, 0xFFD9B9F3,
                0xFF4A2E60, 0xFFF07E98, 0xFF6DCDA5, 0xFF8553B3, 0xFFC18BBE,
            ),
            "imperial-bronze" to listOf(
                0xFF120D09, 0xFF24180F, 0xFF49301A, 0xFF3A2818, 0xFFFFF8EE, 0xFFE5D2BB,
                0xFFB29A7F, 0xFF21170F, 0xFF302114, 0xFFD9A66A, 0xFF80603B, 0xFFE9C69A,
                0xFF56391F, 0xFFE97C6F, 0xFF7AC99B, 0xFFA56D3E, 0xFFD5A45E,
            ),
            "black-onyx" to listOf(
                0xFF050609, 0xFF101318, 0xFF252A31, 0xFF252B34, 0xFFF7F8FA, 0xFFCED3DA,
                0xFF929DA9, 0xFF11151B, 0xFF1B2028, 0xFFD9DDE2, 0xFF66717E, 0xFFD3DCE5,
                0xFF39414C, 0xFFE77A83, 0xFF72C79D, 0xFF73869A, 0xFFB5A18A,
            ),
        )

        themes.forEach { theme ->
            val p = theme.palette
            val actual = listOf(
                p.background, p.backgroundTop, p.backgroundBottom, p.card, p.text, p.textSecondary,
                p.muted, p.panel, p.surface, p.accent, p.border, p.focus,
                p.selected, p.danger, p.success, p.waveOne, p.waveTwo,
            ).map { it.toArgb() }
            val expected = expectedHexTokens.getValue(theme.id).map { it.toInt() }
            assertEquals("Exact SONIX token mismatch for ${theme.id}", expected, actual)
        }
    }

    @Test
    fun `All 10 themes preserve strong text, button, and accent contrast across light and dark modes`() {
        AppThemeCatalog.allThemes.forEach { theme ->
            val p = theme.palette
            assertTrue("${theme.id}: text on background", contrastRatio(p.text, p.background) >= 4.5f)
            assertTrue("${theme.id}: text on card", contrastRatio(p.text, p.card) >= 4.5f)
            assertTrue("${theme.id}: text on panel", contrastRatio(p.text, p.panel) >= 4.5f)
            assertTrue("${theme.id}: text on surface", contrastRatio(p.text, p.surface) >= 4.5f)
            assertTrue("${theme.id}: text on selected", contrastRatio(p.text, p.selected) >= 4.5f)
            assertTrue("${theme.id}: textSecondary on card", contrastRatio(p.textSecondary, p.card) >= 4.0f)
            assertTrue("${theme.id}: onAccent on accent", contrastRatio(p.onAccent, p.accent) >= 4.5f)
            assertTrue("${theme.id}: onDanger on danger", contrastRatio(p.onDanger, p.danger) >= 4.0f)
            assertTrue("${theme.id}: accentText on card", contrastRatio(p.accentText, p.card) >= 3.5f)
            assertTrue("${theme.id}: accentText on background", contrastRatio(p.accentText, p.background) >= 3.5f)
        }
        assertEquals(ThemeAtmosphere.entries.size, 10)
    }

    @Test
    fun `UserPreferencesDataStore persists global app theme independently from screensaver ambient theme`() = runTest {
        val store = UserPreferencesDataStore(context)

        // Fresh UserPreferences defaults to Royal Navy
        assertEquals("royal-navy", com.liquidtv.app.domain.model.UserPreferences().appTheme)
        assertEquals("royal-navy", store.getAppTheme().first())
        val initialAmbient = store.getAmbientTheme().first()

        // Persisting every SONIX theme leaves ambient theme untouched
        AppThemeCatalog.allThemes.forEach { theme ->
            store.setAppTheme(theme.id)
            val prefs = store.userPreferences.first()
            assertEquals(theme.id, prefs.appTheme)
            assertEquals(initialAmbient, prefs.theme)
        }

        // Unknown / corrupted ID falls back safely to Royal Navy
        store.setAppTheme("non_existent_theme")
        assertEquals("royal-navy", store.getAppTheme().first())
        assertEquals("royal-navy", AppThemeCatalog.resolve(null).id)
        assertEquals("royal-navy", AppThemeCatalog.resolve("  ").id)
        store.setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
    }

    @Test
    fun `Settings Themes section displays all 10 themes and switches global AppTheme immediately`() {
        val store = UserPreferencesDataStore(context)
        runBlocking {
            store.setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }

        val settingsVm = SettingsViewModel(
            context = context,
            userPreferencesRepository = store,
            channelRepository = StubChannelRepository(),
            subscriptionRepository = StubSubscriptionRepository(),
        )
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), store), store)

        var activeThemeId = ""
        var activeIsLight = false
        var activeBgArgb = 0

        composeRule.setContent {
            val prefs by store.userPreferences.collectAsState(initial = com.liquidtv.app.domain.model.UserPreferences())
            val resolvedTheme = AppThemeCatalog.resolve(prefs.appTheme)
            LiquidTVTheme(theme = resolvedTheme) {
                activeThemeId = AppTheme.current.id
                activeIsLight = AppTheme.current.isLight
                activeBgArgb = AppTheme.colors.background.toArgb()

                Box(Modifier.width(1080.dp).height(700.dp)) {
                    SettingsScreen(
                        viewModel = settingsVm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { activeThemeId == "royal-navy" }
        composeRule.waitForIdle()
        assertEquals("royal-navy", activeThemeId)
        assertFalse(activeIsLight)

        // Open the new "الثيمات" tab in Settings
        composeRule.onNodeWithTag("settings_section_themes").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("الثيم المفعّل حالياً", substring = true).assertIsDisplayed()

        // Select Light theme "لؤلؤة القصور" (Pearl Marble)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_pearl-marble"))
        composeRule.onNodeWithTag("theme_card_pearl-marble").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { activeThemeId == "pearl-marble" }
        composeRule.waitForIdle()

        assertEquals("pearl-marble", activeThemeId)
        assertTrue(activeIsLight)
        assertEquals(0xFFE9E0D1.toInt(), activeBgArgb)
        composeRule.onNodeWithTag("theme_active_badge_pearl-marble").assertIsDisplayed()

        // Switch immediately to Dark theme "مخمل العقيق" (Ruby Velvet)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_ruby-velvet"))
        composeRule.onNodeWithTag("theme_card_ruby-velvet").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { activeThemeId == "ruby-velvet" }
        composeRule.waitForIdle()

        assertEquals("ruby-velvet", activeThemeId)
        assertFalse(activeIsLight)
        assertEquals(0xFF17060D.toInt(), activeBgArgb)

        // Scroll to the end and verify Black Onyx (#10, sortOrder 19) is selectable
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_black-onyx"))
        composeRule.onNodeWithTag("theme_card_black-onyx").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { activeThemeId == "black-onyx" }
        composeRule.waitForIdle()

        assertEquals("black-onyx", activeThemeId)
        assertEquals(0xFF050609.toInt(), activeBgArgb)
    }

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

private class StubSubscriptionRepository : SubscriptionRepository {
    override val allSubscriptions: Flow<List<Subscription>> = flowOf(emptyList())
    override val activeSubscription: Flow<Subscription?> = flowOf(null)
    override val syncInfo: Flow<PlaylistInfo> = flowOf(PlaylistInfo())
    override suspend fun getActiveSubscriptionOnce(): Subscription? = null
    override suspend fun getSubscriptionById(id: String): Subscription? = null
    override suspend fun hasAnySubscription(): Boolean = false
    override suspend fun addOrUpdateXtreamSubscription(
        existingId: String?,
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
        activate: Boolean,
    ): Result<Subscription> = Result.failure(UnsupportedOperationException())

    override suspend fun addOrUpdateM3uSubscription(
        existingId: String?,
        displayName: String,
        playlistUrl: String,
        userAgent: String?,
        referer: String?,
        activate: Boolean,
    ): Result<Subscription> = Result.failure(UnsupportedOperationException())

    override suspend fun activateSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun syncSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun syncActiveSubscription(): Result<Unit> = Result.success(Unit)
    override suspend fun syncActiveEpg(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getDecryptedXtreamPassword(subscriptionId: String): String? = null
    override suspend fun getDecryptedM3uUrl(subscriptionId: String): String? = null
}

private class StubChannelRepository : ChannelRepository {
    override fun getAllChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getMovies(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getSeries(): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getSeriesById(seriesId: String): Flow<SeriesItem?> = flowOf(null)
    override fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>> = flowOf(emptyList())
    override suspend fun loadSeriesDetails(seriesId: String): Result<Unit> = Result.success(Unit)
    override fun getFavoriteChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getRecentlyWatched(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCategory(category: String, mediaType: MediaType): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCountry(country: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchChannels(query: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchSeries(query: String): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getAllCategories(mediaType: MediaType): Flow<List<Category>> = flowOf(emptyList())
    override fun getAllLanguages(): Flow<List<Language>> = flowOf(emptyList())
    override fun getAllCountries(): Flow<List<Country>> = flowOf(emptyList())
    override fun getLockedCategoryNames(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setCategoryLocked(categoryName: String, locked: Boolean) = Unit
    override suspend fun getChannelById(id: String): Channel? = null
    override suspend fun getEpisodeById(episodeId: String): EpisodeItem? = null
    override suspend fun toggleFavorite(channelId: String) = Unit
    override suspend fun toggleSeriesFavorite(seriesId: String) = Unit
    override suspend fun recordWatch(channelId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long) = Unit
    override fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary> = flowOf(ChannelEpgSummary())
    override suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary = ChannelEpgSummary()
    override suspend fun refreshPlaylists(): Result<Unit> = Result.success(Unit)
    override suspend fun clearWatchHistory() = Unit
    override fun getPlaylistInfo(): Flow<PlaylistInfo> = flowOf(PlaylistInfo())
}

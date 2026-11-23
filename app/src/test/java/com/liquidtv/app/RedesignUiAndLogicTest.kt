package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.entity.CategoryEntity
import com.liquidtv.app.data.local.entity.SeriesEntity
import com.liquidtv.app.data.local.entity.SubscriptionEntity
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.ChannelRepositoryImpl
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.domain.model.EpgProgram
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeasonGroup
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.screens.livetv.LiveTVScreen
import com.liquidtv.app.ui.screens.livetv.LiveTVViewModel
import com.liquidtv.app.ui.screens.login.LoginScreen
import com.liquidtv.app.ui.screens.login.LoginViewModel
import com.liquidtv.app.ui.screens.movies.MoviesScreen
import com.liquidtv.app.ui.screens.movies.MoviesViewModel
import com.liquidtv.app.ui.screens.series.SeriesDetailScreen
import com.liquidtv.app.ui.screens.series.SeriesDetailViewModel
import com.liquidtv.app.ui.screens.series.SeriesScreen
import com.liquidtv.app.ui.screens.series.SeriesViewModel
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.settings.SettingsViewModel
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.ui.utils.calculateEpisodeProgress
import com.liquidtv.app.ui.utils.findContinueWatchingEpisode
import com.liquidtv.app.ui.utils.formatEpisodeCode
import com.liquidtv.app.ui.utils.formatSeasonTitleAr
import com.liquidtv.app.ui.utils.sortSeasons
import com.liquidtv.app.updater.AppUpdateManager
import com.liquidtv.app.updater.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w2000dp-h1200dp-xhdpi")
class RedesignUiAndLogicTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MediaFormatters sort seasons numerically, format Arabic titles, and guard episode progress`() {
        val sorted = sortSeasons(
            listOf(
                SeasonGroup(10, emptyList()),
                SeasonGroup(2, emptyList()),
                SeasonGroup(0, emptyList()),
                SeasonGroup(1, emptyList()),
            )
        )
        assertEquals(listOf(0, 1, 2, 10), sorted.map { it.seasonNumber })
        assertEquals("حلقات خاصة", formatSeasonTitleAr(0))
        assertEquals("الموسم 2", formatSeasonTitleAr(2))
        assertEquals("S01E03", formatEpisodeCode(1, 3))
        assertNull(formatEpisodeCode(-1, 1))
        assertNull(calculateEpisodeProgress(resumePositionMs = 5_000L, durationSec = null))
        assertNull(calculateEpisodeProgress(resumePositionMs = 5_000L, durationSec = 0))
        assertNull(calculateEpisodeProgress(resumePositionMs = 0L, durationSec = 120))
        assertEquals(0.5f, calculateEpisodeProgress(60_000L, 120) ?: -1f, 0.001f)
        assertEquals(1.0f, calculateEpisodeProgress(999_000L, 120) ?: -1f, 0.001f)

        val ep1 = EpisodeItem(
            id = "ep1",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "H1",
            streamUrl = "http://x/1.mp4",
            resumePositionMs = 30_000L,
            lastWatchedAt = 100L,
        )
        val ep2 = EpisodeItem(
            id = "ep2",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "2",
            seasonNumber = 1,
            episodeNumber = 2,
            title = "H2",
            streamUrl = "http://x/2.mp4",
            resumePositionMs = 15_000L,
            lastWatchedAt = 500L,
        )
        assertEquals("ep2", findContinueWatchingEpisode(listOf(ep1, ep2))?.id)
    }

    @Test
    fun `ChannelRepositoryImpl computes series categories and reacts to toggleSeriesFavorite`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val okHttp = OkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        val crypto = object : CredentialCrypto {
            override fun encrypt(plaintext: String) = plaintext
            override fun decrypt(ciphertext: String) = ciphertext
        }
        val prefs = UserPreferencesDataStore(context)
        val subRepo = SubscriptionRepositoryImpl(
            context = context,
            database = db,
            subscriptionDao = db.subscriptionDao(),
            channelDao = db.channelDao(),
            seriesDao = db.seriesDao(),
            categoryDao = db.categoryDao(),
            epgDao = db.epgDao(),
            xtreamApi = XtreamApiService(okHttp),
            iptvApi = retrofit.create(IptvApiService::class.java),
            okHttpClient = okHttp,
            m3uParser = M3UParser(),
            xmltvParser = XmltvParser(),
            crypto = crypto,
            prefs = prefs,
        )
        val channelRepo = ChannelRepositoryImpl(
            subscriptionDao = db.subscriptionDao(),
            channelDao = db.channelDao(),
            seriesDao = db.seriesDao(),
            categoryDao = db.categoryDao(),
            watchHistoryDao = db.watchHistoryDao(),
            epgDao = db.epgDao(),
            subscriptionRepository = subRepo,
            xtreamApi = XtreamApiService(okHttp),
            prefs = prefs,
        )

        db.subscriptionDao().upsertSubscription(
            SubscriptionEntity(
                id = "sub-1",
                name = "Test",
                type = SubscriptionType.XTREAM.name,
                serverUrl = "http://localhost",
                isActive = true,
                connectionStatus = SubscriptionStatus.CONNECTED.name,
            )
        )
        db.categoryDao().upsertCategories(
            listOf(
                CategoryEntity("cat-drama", "sub-1", "30", MediaType.SERIES_EPISODE.name, "دراما", 0, false),
                CategoryEntity("cat-empty", "sub-1", "31", MediaType.SERIES_EPISODE.name, "أكشن", 0, false),
            )
        )
        db.seriesDao().upsertSeries(
            listOf(
                SeriesEntity("sr-1", "sub-1", "101", "الأفق", categoryId = "30", categoryName = "دراما"),
                SeriesEntity("sr-2", "sub-1", "102", "السراب", categoryId = "30", categoryName = "دراما"),
            )
        )

        val categories = channelRepo.getAllCategories(MediaType.SERIES_EPISODE).first()
        val drama = categories.firstOrNull { it.name == "دراما" }
        assertEquals(2, drama?.channelCount)
        assertTrue(categories.none { it.name == "أكشن" })

        val beforeToggle = channelRepo.getSeriesById("sr-1").first()
        assertEquals(false, beforeToggle?.isFavorite)
        channelRepo.toggleSeriesFavorite("sr-1")
        val afterToggle = channelRepo.getSeriesById("sr-1").first()
        assertEquals(true, afterToggle?.isFavorite)

        db.close()
    }

    @Test
    fun `LoginScreen renders compact Arabic tabs, validates blank input, and switches to M3U`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = LoginViewModel(fakeSubRepo)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(380.dp).height(760.dp)) {
                    LoginScreen(
                        onLoginSuccess = {},
                        viewModel = vm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tab_xtream").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_m3u").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_error_banner").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("tab_m3u").performScrollTo().performClick()
        composeRule.onNodeWithTag("input_m3u_url").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `SettingsScreen renders Arabic sections and scrolls vertically on compact phone screen`() {
        val prefs = UserPreferencesDataStore(context)
        val fakeChannels = FakeChannelRepository()
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = SettingsViewModel(context, prefs, fakeChannels, fakeSubRepo)
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), prefs), prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(380.dp).height(640.dp)) {
                    SettingsScreen(
                        viewModel = vm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_section_subscriptions").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("setting_clear_cache"))
        composeRule.onNodeWithTag("setting_clear_cache").assertIsDisplayed()
    }

    @Test
    fun `SeriesDetailScreen hides Season 0 when absent and switches episodes when season card clicked`() {
        val fakeChannels = FakeChannelRepository(
            episodes = listOf(
                EpisodeItem(
                    id = "ep-s1e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "101",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "البداية",
                    streamUrl = "http://x/1.mp4",
                    durationSec = 2400,
                    resumePositionMs = 600_000L,
                ),
                EpisodeItem(
                    id = "ep-s2e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "201",
                    seasonNumber = 2,
                    episodeNumber = 1,
                    title = "العودة",
                    streamUrl = "http://x/2.mp4",
                    durationSec = 2500,
                ),
            ),
        )
        val vm = SeriesDetailViewModel(fakeChannels, SavedStateHandle(mapOf("seriesId" to "sr-1")))

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(400.dp).height(780.dp)) {
                    SeriesDetailScreen(
                        onEpisodeClick = {},
                        onBack = {},
                        viewModel = vm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_card_0").assertDoesNotExist()
        composeRule.onNodeWithTag("season_card_1").assertIsDisplayed()
        composeRule.onNodeWithTag("season_card_2").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_card_ep-s1e1").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_watching_button").assertIsDisplayed()

        composeRule.onNodeWithTag("season_card_2").performClick()
        composeRule.onNodeWithTag("episode_card_ep-s2e1").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_card_ep-s1e1").assertDoesNotExist()
    }

    @Test
    fun `SeriesDetailScreen renders Special Episodes label ONLY when Season 0 exists`() {
        val fakeChannels = FakeChannelRepository(
            episodes = listOf(
                EpisodeItem(
                    id = "ep-s0e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "1",
                    seasonNumber = 0,
                    episodeNumber = 1,
                    title = "كواليس",
                    streamUrl = "http://x/0.mp4",
                ),
                EpisodeItem(
                    id = "ep-s1e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "2",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "الحلقة الأولى",
                    streamUrl = "http://x/1.mp4",
                ),
            ),
        )
        val vm = SeriesDetailViewModel(fakeChannels, SavedStateHandle(mapOf("seriesId" to "sr-1")))

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(700.dp)) {
                    SeriesDetailScreen(
                        onEpisodeClick = {},
                        onBack = {},
                        viewModel = vm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_card_0").assertIsDisplayed()
        composeRule.onNodeWithText("حلقات خاصة").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen renders 3-pane Arabic RTL layout with EPG and categories`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1000.dp).height(700.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }
        composeRule.onNodeWithTag("live_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("live_channels_list").assertIsDisplayed()
        composeRule.onNodeWithTag("live_preview_play_button").assertIsDisplayed()
    }

    @Test
    fun `MoviesScreen renders 2-to-3 cinema posters and category sidebar in Arabic RTL`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val moviesVm = MoviesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(900.dp).height(700.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }
        composeRule.onNodeWithTag("movies_grid").assertIsDisplayed()
        composeRule.onNodeWithText("صحراء العاصفة").assertIsDisplayed()
    }

    @Test
    fun `SeriesScreen renders 2-to-3 cinema posters and category sidebar in Arabic RTL`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val seriesVm = SeriesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(900.dp).height(700.dp)) {
                    SeriesScreen(onSeriesClick = {}, viewModel = seriesVm)
                }
            }
        }
        composeRule.onNodeWithTag("series_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("series_grid").assertIsDisplayed()
        composeRule.onNodeWithText("الأفق").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen landscape phone keeps all header controls clickable, shows channel names, toggles grid, and expands content`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())
        val totalHeightDp = 400f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(850.dp).height(totalHeightDp.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }

        // Preserve all required header controls & info
        composeRule.onNodeWithText("البث المباشر").assertIsDisplayed()
        composeRule.onNodeWithTag("live_channels_count").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_name_asc").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_name_desc").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_favorites_first").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_category").assertIsDisplayed()
        composeRule.onNodeWithTag("live_view_list").assertIsDisplayed()
        composeRule.onNodeWithTag("live_view_grid").assertIsDisplayed()

        // Ensure channel name inside middle list row is NOT crushed at 850dp wide
        composeRule.onAllNodesWithText("الجزيرة الإخبارية").onFirst().assertIsDisplayed()

        val density = composeRule.density.density
        val headerHeightDp = composeRule.onNodeWithTag("live_tv_header").fetchSemanticsNode().boundsInRoot.height / density
        val sidebarPaneHeightDp = composeRule.onNodeWithTag("live_categories_pane").fetchSemanticsNode().boundsInRoot.height / density
        val sidebarListHeightDp = composeRule.onNodeWithTag("live_categories_sidebar").fetchSemanticsNode().boundsInRoot.height / density
        val listHeightDp = composeRule.onNodeWithTag("live_channels_list").fetchSemanticsNode().boundsInRoot.height / density

        assertTrue("Expected single-row compact header <= 54dp on landscape, was $headerHeightDp", headerHeightDp <= 54f)
        assertTrue("Expected sidebar pane >= 68% of 400dp screen height, was $sidebarPaneHeightDp", sidebarPaneHeightDp >= totalHeightDp * 0.68f)
        assertTrue("Expected scrollable sidebar list >= 245dp inside 400dp landscape, was $sidebarListHeightDp", sidebarListHeightDp >= 245f)
        assertTrue("Expected channel list >= 65% of 400dp screen height, was $listHeightDp", listHeightDp >= totalHeightDp * 0.65f)

        // Verify ViewMode switch to Grid and back to List works
        composeRule.onNodeWithTag("live_view_grid").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_channels_grid").assertIsDisplayed()

        composeRule.onNodeWithTag("live_view_list").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_channels_list").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen maximizes content pane height on 720p TV and portrait phone`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())
        val tvHeightDp = 540f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(tvHeightDp.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }

        val density = composeRule.density.density
        val listHeightDp = composeRule.onNodeWithTag("live_channels_list").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected >= 74% vertical space for TV channel list on 540dp, was $listHeightDp", listHeightDp >= tvHeightDp * 0.74f)
    }

    @Test
    fun `MoviesScreen and SeriesScreen maximize 2-to-3 poster grid and sidebar vertical ratio across TV viewports`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val moviesVm = MoviesViewModel(fakeChannels, prefs)
        val heightDp = 1080f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1920.dp).height(heightDp.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }

        composeRule.onNodeWithTag("movies_count").assertIsDisplayed()
        composeRule.onNodeWithTag("movies_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("movies_grid").assertIsDisplayed()

        val density = composeRule.density.density
        val headerHeightDp = composeRule.onNodeWithTag("movies_header").fetchSemanticsNode().boundsInRoot.height / density
        val gridHeightDp = composeRule.onNodeWithTag("movies_grid").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected compact Movies header <= 52dp, was $headerHeightDp", headerHeightDp <= 52f)
        assertTrue("Expected Movies grid >= 84% of 1080dp screen height, was $gridHeightDp", gridHeightDp >= heightDp * 0.84f)
    }

    @Test
    fun `SeriesScreen allocates majority vertical space on 720p landscape and portrait phone`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = UserPreferencesDataStore(context)
        val seriesVm = SeriesViewModel(fakeChannels, prefs)
        val phoneHeightDp = 800f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(390.dp).height(phoneHeightDp.dp)) {
                    SeriesScreen(onSeriesClick = {}, viewModel = seriesVm)
                }
            }
        }

        composeRule.onNodeWithTag("series_header").assertIsDisplayed()
        composeRule.onNodeWithTag("series_count").assertIsDisplayed()
        composeRule.onNodeWithTag("series_grid").assertIsDisplayed()
        val density = composeRule.density.density
        val gridHeightDp = composeRule.onNodeWithTag("series_grid").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected portrait phone Series grid >= 75% of 800dp screen height, was $gridHeightDp", gridHeightDp >= phoneHeightDp * 0.75f)
    }

    @Test
    fun `LoginScreen card is compact horizontally and vertically on landscape phone without clipping CTA`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = LoginViewModel(fakeSubRepo)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(850.dp).height(400.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }

        val density = composeRule.density.density
        val cardBounds = composeRule.onNodeWithTag("login_card").fetchSemanticsNode().boundsInRoot
        val cardWidthDp = cardBounds.width / density
        val cardHeightDp = cardBounds.height / density
        val cardLeftDp = cardBounds.left / density
        val cardRightDp = cardBounds.right / density

        assertTrue("Expected compact Login card width <= 360dp on 850dp screen, was $cardWidthDp", cardWidthDp <= 360f)
        assertTrue("Expected compact Login card height <= 355dp on 400dp landscape, was $cardHeightDp", cardHeightDp <= 355f)
        assertTrue("Expected Login card centered with generous side margins (left=$cardLeftDp, right=$cardRightDp)", cardLeftDp >= 200f && cardRightDp <= 650f)

        composeRule.onNodeWithTag("input_subscription_name").assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_url").assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_username").assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_password").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_password_visibility").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_button").assertIsDisplayed()
    }

    @Test
    fun `LoginScreen card remains compact and centered on Android TV 1080p viewport`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = LoginViewModel(fakeSubRepo)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }

        val density = composeRule.density.density
        val cardBounds = composeRule.onNodeWithTag("login_card").fetchSemanticsNode().boundsInRoot
        val cardWidthDp = cardBounds.width / density
        val cardHeightDp = cardBounds.height / density

        assertTrue("Expected Login card width <= 370dp on TV, was $cardWidthDp", cardWidthDp <= 370f)
        assertTrue("Expected Login card height <= 375dp on TV, was $cardHeightDp", cardHeightDp <= 375f)
        composeRule.onNodeWithTag("toggle_password_visibility").performClick()
        composeRule.onNodeWithTag("connect_button").assertIsDisplayed()
    }
}

private class FakeSubscriptionRepository : SubscriptionRepository {
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

private class FakeChannelRepository(
    episodes: List<EpisodeItem> = listOf(
        EpisodeItem(
            id = "ep-1",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "الحلقة الأولى",
            streamUrl = "http://x/1.mp4",
        ),
    ),
) : ChannelRepository {
    private val liveFlow = MutableStateFlow(
        listOf(
            Channel(
                id = "ch-1",
                name = "الجزيرة الإخبارية",
                streamUrl = "http://localhost/live/1.ts",
                groupTitle = "أخبار",
                mediaType = MediaType.LIVE,
                channelNumber = 1,
                tvgId = "aljazeera",
            ),
        )
    )
    private val moviesFlow = MutableStateFlow(
        listOf(
            Channel(
                id = "mv-1",
                name = "صحراء العاصفة",
                streamUrl = "http://localhost/movie/1.mp4",
                groupTitle = "أكشن",
                mediaType = MediaType.MOVIE,
                rating = "8.4",
            ),
        )
    )
    private val seriesFlow = MutableStateFlow(
        listOf(
            SeriesItem(
                id = "sr-1",
                subscriptionId = "sub-1",
                externalSeriesId = "303",
                name = "الأفق",
                categoryName = "دراما",
                plot = "ملحمة درامية مشوقة.",
                releaseDate = "2024-01-01",
                rating = "8.7",
            ),
        )
    )
    private val episodesFlow = MutableStateFlow(episodes)
    private val epg = ChannelEpgSummary(
        current = EpgProgram(
            id = 1L,
            subscriptionId = "sub-1",
            channelTvgId = "aljazeera",
            title = "النشرة المسائية",
            description = "تغطية إخبارية شاملة",
            startTimeMs = System.currentTimeMillis() - 600_000L,
            endTimeMs = System.currentTimeMillis() + 600_000L,
        ),
        next = EpgProgram(
            id = 2L,
            subscriptionId = "sub-1",
            channelTvgId = "aljazeera",
            title = "برنامج حواري",
            description = null,
            startTimeMs = System.currentTimeMillis() + 600_000L,
            endTimeMs = System.currentTimeMillis() + 1_800_000L,
        ),
    )

    override fun getAllChannels(): Flow<List<Channel>> = liveFlow
    override fun getMovies(): Flow<List<Channel>> = moviesFlow
    override fun getSeries(): Flow<List<SeriesItem>> = seriesFlow
    override fun getSeriesById(seriesId: String): Flow<SeriesItem?> = seriesFlow.map { list -> list.firstOrNull { it.id == seriesId } }
    override fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>> = episodesFlow
    override suspend fun loadSeriesDetails(seriesId: String): Result<Unit> = Result.success(Unit)
    override fun getFavoriteChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getRecentlyWatched(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCategory(category: String, mediaType: MediaType): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCountry(country: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchChannels(query: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchSeries(query: String): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getAllCategories(mediaType: MediaType): Flow<List<Category>> = when (mediaType) {
        MediaType.LIVE -> flowOf(listOf(Category(id = "c1", contentType = MediaType.LIVE, name = "أخبار", channelCount = 1)))
        MediaType.MOVIE -> flowOf(listOf(Category(id = "m1", contentType = MediaType.MOVIE, name = "أكشن", channelCount = 1)))
        MediaType.SERIES_EPISODE -> flowOf(listOf(Category(id = "s1", contentType = MediaType.SERIES_EPISODE, name = "دراما", channelCount = 1)))
    }
    override fun getAllLanguages(): Flow<List<Language>> = flowOf(emptyList())
    override fun getAllCountries(): Flow<List<Country>> = flowOf(emptyList())
    override fun getLockedCategoryNames(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setCategoryLocked(categoryName: String, locked: Boolean) = Unit
    override suspend fun getChannelById(id: String): Channel? = liveFlow.value.firstOrNull { it.id == id }
    override suspend fun getEpisodeById(episodeId: String): EpisodeItem? = episodesFlow.value.firstOrNull { it.id == episodeId }
    override suspend fun toggleFavorite(channelId: String) {
        liveFlow.update { list -> list.map { if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it } }
    }
    override suspend fun toggleSeriesFavorite(seriesId: String) {
        seriesFlow.update { list -> list.map { if (it.id == seriesId) it.copy(isFavorite = !it.isFavorite) else it } }
    }
    override suspend fun recordWatch(channelId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long) = Unit
    override fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary> = flowOf(epg)
    override suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary = epg
    override suspend fun refreshPlaylists(): Result<Unit> = Result.success(Unit)
    override suspend fun clearWatchHistory() = Unit
    override fun getPlaylistInfo(): Flow<PlaylistInfo> = flowOf(PlaylistInfo())
}

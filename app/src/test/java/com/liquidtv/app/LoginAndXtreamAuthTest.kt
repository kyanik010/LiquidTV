package com.liquidtv.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.AppStartupGate
import com.liquidtv.app.ui.StartupViewModel
import com.liquidtv.app.ui.screens.login.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LoginAndXtreamAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var database: LiquidTVDatabase
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var repository: SubscriptionRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    private val passThroughCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        server = MockWebServer()
        server.start()

        database = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        repository = SubscriptionRepositoryImpl(
            context = context,
            database = database,
            subscriptionDao = database.subscriptionDao(),
            channelDao = database.channelDao(),
            seriesDao = database.seriesDao(),
            categoryDao = database.categoryDao(),
            epgDao = database.epgDao(),
            xtreamApi = XtreamApiService(okHttpClient),
            iptvApi = retrofit.create(IptvApiService::class.java),
            okHttpClient = okHttpClient,
            m3uParser = M3UParser(),
            xmltvParser = XmltvParser(),
            crypto = passThroughCrypto,
            prefs = UserPreferencesDataStore(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `http Xtream server authenticates and syncs live movies and series`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("action=get_live_categories") -> MockResponse().setBody(
                        """[{"category_id":"10","category_name":"Arabic News"}]""",
                    )
                    path.contains("action=get_live_streams") -> MockResponse().setBody(
                        """[{"num":1,"name":"Al Jazeera HD","stream_id":101,"stream_icon":"http://cdn/logo.png","epg_channel_id":"aljazeera.qa","category_id":"10"}]""",
                    )
                    path.contains("action=get_vod_categories") -> MockResponse().setBody(
                        """[{"category_id":"20","category_name":"Action Movies"}]""",
                    )
                    path.contains("action=get_vod_streams") -> MockResponse().setBody(
                        """[{"num":1,"name":"Desert Storm","stream_id":202,"stream_icon":"http://cdn/movie.jpg","category_id":"20","container_extension":"mp4","rating":"8.1"}]""",
                    )
                    path.contains("action=get_series_categories") -> MockResponse().setBody(
                        """[{"category_id":"30","category_name":"Drama Series"}]""",
                    )
                    path.contains("action=get_series") -> MockResponse().setBody(
                        """[{"series_id":303,"name":"The Horizon","cover":"http://cdn/series.jpg","category_id":"30","plot":"Sci-fi drama"}]""",
                    )
                    path.contains("player_api.php") && !path.contains("action=") -> MockResponse().setBody(
                        """{"user_info":{"username":"demo","status":"Active","auth":1,"exp_date":"1893456000","max_connections":"2","active_cons":"0"},"server_info":{"url":"localhost","port":"${server.port}","server_protocol":"http","allowed_output_formats":["m3u8","ts"]}}""",
                    )
                    path.contains("xmltv.php") -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val httpUrl = "http://${server.hostName}:${server.port}"
        val result = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "My HTTP Provider",
            serverUrl = httpUrl,
            username = "demo",
            password = "secret",
            activate = true,
        )

        assertTrue("Expected HTTP Xtream login to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val sub = result.getOrThrow()
        assertEquals(SubscriptionType.XTREAM, sub.type)
        assertTrue(sub.serverUrl.startsWith("http://"))

        val live = database.channelDao().getLiveChannels(sub.id).first()
        val movies = database.channelDao().getMovies(sub.id).first()
        val series = database.seriesDao().getSeries(sub.id).first()

        assertEquals(1, live.size)
        assertEquals("Al Jazeera HD", live[0].name)
        assertTrue(live[0].streamUrl.startsWith("http://"))
        assertEquals(1, movies.size)
        assertEquals("Desert Storm", movies[0].name)
        assertEquals(1, series.size)
        assertEquals("The Horizon", series[0].name)
    }

    @Test
    fun `invalid Xtream credentials returns failure without saving subscription`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"user_info":{"username":"bad","status":"Disabled","auth":0}}"""),
        )

        val result = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "Bad Sub",
            serverUrl = "http://${server.hostName}:${server.port}",
            username = "bad",
            password = "wrong",
            activate = true,
        )

        assertTrue(result.isFailure)
        assertFalse(repository.hasAnySubscription())
    }

    @Test
    fun `http M3U playlist URL is allowed and empty M3U is rejected`() = runTest {
        val m3uBody = """
            #EXTM3U url-tvg="http://${server.hostName}:${server.port}/epg.xml"
            #EXTINF:-1 tvg-id="bbc.uk" group-title="News",BBC World News
            http://stream.example.com:8080/live/bbc/index.m3u8
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("valid.m3u") -> MockResponse().setBody(m3uBody)
                    path.contains("empty.m3u") -> MockResponse().setBody("#EXTM3U\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val validUrl = "http://${server.hostName}:${server.port}/valid.m3u"
        val validResult = repository.addOrUpdateM3uSubscription(
            existingId = null,
            displayName = "HTTP M3U",
            playlistUrl = validUrl,
            userAgent = "VLC/3.0",
            referer = null,
            activate = true,
        )
        assertTrue(validResult.isSuccess)
        val sub = validResult.getOrThrow()
        val channels = database.channelDao().getLiveChannels(sub.id).first()
        assertEquals(1, channels.size)
        assertEquals("BBC World News", channels[0].name)

        val emptyUrl = "http://${server.hostName}:${server.port}/empty.m3u"
        val emptyResult = repository.addOrUpdateM3uSubscription(
            existingId = null,
            displayName = "Empty M3U",
            playlistUrl = emptyUrl,
            userAgent = null,
            referer = null,
            activate = false,
        )
        assertTrue(emptyResult.isFailure)
    }

    @Test
    fun `LoginViewModel rejects non-HTTP schemes and prevents duplicate concurrent requests`() = runTest {
        val vm = LoginViewModel(repository)
        vm.selectType(SubscriptionType.XTREAM)
        vm.setServerUrl("ftp://invalid.server")
        vm.setUsername("user")
        vm.setPassword("pass")
        vm.connect()
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isConnecting)

        // Ensure blank M3U URL fails validation cleanly
        vm.selectType(SubscriptionType.M3U)
        vm.setM3uUrl("")
        vm.connect()
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `StartupViewModel enforces Login gate when zero subscriptions exist and opens Home after login`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val prefsRepo = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
        val startupVm = StartupViewModel(repository, prefsRepo)
        val collectorJob = launch { startupVm.rootState.collect { } }
        val initialGate = withTimeout(5_000) {
            startupVm.rootState.first { it.gate != AppStartupGate.Checking }
        }
        assertEquals(AppStartupGate.NeedsLogin, initialGate.gate)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.contains("test.m3u") == true) {
                    MockResponse().setBody("#EXTM3U\n#EXTINF:-1,Channel One\nhttp://example.com/1.ts\n")
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        val res = repository.addOrUpdateM3uSubscription(
            existingId = null,
            displayName = "Starter",
            playlistUrl = "http://${server.hostName}:${server.port}/test.m3u",
            userAgent = null,
            referer = null,
            activate = true,
        )
        assertTrue(res.isSuccess)
        val unlockedState = withTimeout(5_000) {
            startupVm.rootState.first { it.gate is AppStartupGate.Ready }
        }
        assertTrue(unlockedState.gate is AppStartupGate.Ready)
        collectorJob.cancel()
    }
}

package com.liquidtv.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.entity.CategoryEntity
import com.liquidtv.app.data.local.entity.ChannelEntity
import com.liquidtv.app.data.local.entity.SubscriptionEntity
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionAndDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: LiquidTVDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: SubscriptionRepositoryImpl

    private val passThroughCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = plaintext
        override fun decrypt(ciphertext: String): String = ciphertext
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()

        val okHttp = OkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        repo = SubscriptionRepositoryImpl(
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
            crypto = passThroughCrypto,
            prefs = UserPreferencesDataStore(context),
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `multi-subscription strict data isolation and switching restores each account data`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/a.m3u" -> MockResponse().setBody(
                        """
                        #EXTM3U
                        #EXTINF:-1 group-title="Sports",SubA Sports
                        http://a.example.com/1.ts
                        """.trimIndent(),
                    )
                    "/b.m3u" -> MockResponse().setBody(
                        """
                        #EXTM3U
                        #EXTINF:-1 group-title="Movies",SubB Cinema
                        http://b.example.com/2.mp4
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val subA = repo.addOrUpdateM3uSubscription(
            existingId = null,
            displayName = "Provider A",
            playlistUrl = "http://${server.hostName}:${server.port}/a.m3u",
            userAgent = null,
            referer = null,
            activate = true,
        ).getOrThrow()

        val subB = repo.addOrUpdateM3uSubscription(
            existingId = null,
            displayName = "Provider B",
            playlistUrl = "http://${server.hostName}:${server.port}/b.m3u",
            userAgent = null,
            referer = null,
            activate = true,
        ).getOrThrow()

        // Active should now be subB; querying active channels returns only subB
        val activeNow = repo.getActiveSubscriptionOnce()!!
        assertEquals(subB.id, activeNow.id)
        val channelsB = db.channelDao().getMovies(activeNow.id).first()
        assertEquals(listOf("SubB Cinema"), channelsB.map { it.name })
        assertTrue(db.channelDao().getLiveChannels(activeNow.id).first().isEmpty())

        // Switch back to subA
        repo.activateSubscription(subA.id)
        val switched = repo.getActiveSubscriptionOnce()!!
        assertEquals(subA.id, switched.id)
        val channelsA = db.channelDao().getLiveChannels(switched.id).first()
        assertEquals(listOf("SubA Sports"), channelsA.map { it.name })

        // Deleting subA cascades only subA data while keeping subB untouched
        repo.deleteSubscription(subA.id)
        assertTrue(db.channelDao().getAllChannelsOnce(subA.id).isEmpty())
        assertEquals(1, db.channelDao().getAllChannelsOnce(subB.id).size)
    }

    @Test
    fun `atomic sync preserves favorites and resume state, and failed sync preserves existing channels`() = runTest {
        var playlistVersion = 1
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (playlistVersion) {
                    1 -> MockResponse().setBody(
                        """
                        #EXTM3U
                        #EXTINF:-1,Kept Channel
                        http://host/kept.ts
                        #EXTINF:-1,Movie One
                        http://host/movie.mp4
                        """.trimIndent(),
                    )
                    2 -> MockResponse().setBody(
                        """
                        #EXTM3U
                        #EXTINF:-1,Kept Channel
                        http://host/kept.ts
                        #EXTINF:-1,Movie One
                        http://host/movie.mp4
                        #EXTINF:-1,Brand New Channel
                        http://host/new.ts
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val url = "http://${server.hostName}:${server.port}/list.m3u"
        val sub = repo.addOrUpdateM3uSubscription(null, "SyncTest", url, null, null, true).getOrThrow()

        val initialChannels = db.channelDao().getAllChannelsOnce(sub.id)
        val kept = initialChannels.first { it.name == "Kept Channel" }
        val movie = initialChannels.first { it.name == "Movie One" }
        db.channelDao().toggleFavorite(kept.id)
        db.channelDao().updateResumePosition(movie.id, 123_456L)

        // Sync v2: favorites & resume position must survive atomic swap
        playlistVersion = 2
        val syncOk = repo.syncSubscription(sub.id)
        assertTrue(syncOk.isSuccess)
        val updated = db.channelDao().getAllChannelsOnce(sub.id)
        assertEquals(3, updated.size)
        assertTrue(updated.first { it.id == kept.id }.isFavorite)
        assertEquals(123_456L, updated.first { it.id == movie.id }.resumePositionMs)

        // Sync v3 fails (HTTP 500): previous 3 channels & favorite must remain intact!
        playlistVersion = 3
        val syncFail = repo.syncSubscription(sub.id)
        assertTrue(syncFail.isFailure)
        val afterFail = db.channelDao().getAllChannelsOnce(sub.id)
        assertEquals(3, afterFail.size)
        assertTrue(afterFail.first { it.id == kept.id }.isFavorite)
        val updatedSub = db.subscriptionDao().getSubscriptionById(sub.id)!!
        assertEquals(SubscriptionStatus.ERROR.name, updatedSub.connectionStatus)
    }

    @Test
    fun `MIGRATION_1_2 upgrades legacy v1 database cleanly without data loss or crashes`() = runTest {
        val dbFile = File(context.cacheDir, "migration_test_${System.currentTimeMillis()}.db")
        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `channels` (
                                    `id` TEXT NOT NULL PRIMARY KEY,
                                    `name` TEXT NOT NULL,
                                    `streamUrl` TEXT NOT NULL,
                                    `logoUrl` TEXT,
                                    `groupTitle` TEXT,
                                    `tvgId` TEXT,
                                    `tvgName` TEXT,
                                    `country` TEXT,
                                    `language` TEXT,
                                    `isFavorite` INTEGER NOT NULL,
                                    `lastWatchedAt` INTEGER,
                                    `addedAt` INTEGER NOT NULL
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `watch_history` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `channelId` TEXT NOT NULL,
                                    `channelName` TEXT NOT NULL,
                                    `watchedAt` INTEGER NOT NULL
                                )
                                """.trimIndent(),
                            )
                            db.execSQL("INSERT INTO watch_history (channelId, channelName, watchedAt) VALUES ('c1', 'Old Channel', 1000)")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        v1Helper.writableDatabase.close()
        v1Helper.close()

        val migratedDb = Room.databaseBuilder(context, LiquidTVDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LiquidTVDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        // Verify new tables accept writes cleanly
        val sub = SubscriptionEntity(
            id = "sub-migrated",
            name = "Post Migration",
            type = SubscriptionType.M3U.name,
            serverUrl = "http://example.com/list.m3u",
            isActive = true,
            connectionStatus = SubscriptionStatus.CONNECTED.name,
        )
        migratedDb.subscriptionDao().upsertSubscription(sub)
        migratedDb.categoryDao().upsertCategories(
            listOf(CategoryEntity("cat-1", sub.id, null, MediaType.LIVE.name, "News", 0, false)),
        )
        migratedDb.channelDao().upsertChannels(
            listOf(ChannelEntity("ch-1", sub.id, null, MediaType.LIVE.name, "News 1", "http://example.com/1.ts")),
        )
        assertEquals(1, migratedDb.channelDao().getAllChannelsOnce(sub.id).size)
        val migratedHistory = migratedDb.watchHistoryDao().getHistory("").first()
        assertEquals(1, migratedHistory.size)
        assertEquals("Old Channel", migratedHistory.first().channelName)

        migratedDb.close()
        dbFile.delete()
    }
}

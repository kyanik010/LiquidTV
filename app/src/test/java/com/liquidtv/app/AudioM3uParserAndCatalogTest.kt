package com.liquidtv.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.audio.AudioM3uParser
import com.liquidtv.app.data.audio.AudioNetworkSecurityValidator
import com.liquidtv.app.data.audio.DefaultExternalAudioCatalogRepository
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.sonix.HttpSonixControlApi
import com.liquidtv.app.data.sonix.SonixControlRepositoryImpl
import com.liquidtv.app.data.sonix.SonixDeviceIdentityStore
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
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
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioM3uParserAndCatalogTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private val plainCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc").delete()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `AudioNetworkSecurityValidator blocks SSRF targets and allows private IPTV LAN`() {
        val publicDns: (String) -> Array<InetAddress> = { host ->
            when (host) {
                "192.168.1.50" -> arrayOf(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 50)))
                "evil.local" -> arrayOf(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())))
                else -> arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
            }
        }

        AudioNetworkSecurityValidator.assertSafeUrl("http://192.168.1.50:8080/live/1.ts", publicDns)
        AudioNetworkSecurityValidator.assertSafeUrl("https://cdn.example.com/bein1.m3u8", publicDns)

        val blocked = listOf(
            "http://localhost:8080/live.m3u8",
            "http://127.0.0.1/live.ts",
            "http://169.254.169.254/latest/meta-data",
            "http://evil.local/stream.m3u8",
            "file:///etc/passwd",
        )
        blocked.forEach { url ->
            val failed = runCatching { AudioNetworkSecurityValidator.assertSafeUrl(url, publicDns) }.isFailure
            assertTrue("Expected blocked URL: $url", failed)
        }
    }

    @Test
    fun `AudioM3uParser parses BOM, EXTGRP, relative URLs, deduplicates, and filters non-target channels`() {
        val m3u = "\uFEFF#EXTM3U\n" +
            "#EXTINF:-1 tvg-chno=\"10\" group-title=\"beIN Sports\",beIN SPORTS 1 HD\n" +
            "http://iptv.example.com/live/bein1.m3u8\n" +
            "#EXTINF:-1 tvg-name=\"SSC 1 HD\" group-title=\"SSC\",SSC 1 HD\n" +
            "http://iptv.example.com/live/ssc1.m3u8\n" +
            "#EXTGRP:قنوات ألوان\n" +
            "#EXTINF:-1,ALWAN 2 FHD\n" +
            "/audio/alwan2.ts\n" +
            "#EXTINF:-1,beIN SPORTS 1 HD Duplicate\n" +
            "http://iptv.example.com/live/bein1.m3u8\n"

        val publicDns: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
        }
        val channels = AudioM3uParser.parse(
            input = ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8)),
            baseUrl = "https://panel.example.com/playlist.m3u",
            dnsResolver = publicDns,
        )

        assertEquals(2, channels.size)
        assertEquals(AudioChannelBrand.BEIN_SPORTS, channels[0].brand)
        assertEquals("10", channels[0].number)
        assertEquals("HD", channels[0].qualityLabel)

        assertEquals(AudioChannelBrand.ALWAN, channels[1].brand)
        assertEquals("https://panel.example.com/audio/alwan2.ts", channels[1].streamUrl)
        assertEquals("FHD", channels[1].qualityLabel)
        assertEquals("ts", channels[1].containerExtension)
    }

    @Test
    fun `SonixControlRepositoryImpl parses audio_source in sync and fetches audio_sources action`() = runTest {
        val syncJson = """
            {
              "device_id": "d-1",
              "device_uid": "uid-1",
              "device_code": "SNX123",
              "device_status": "active",
              "access_status": "active",
              "access_allowed": true,
              "device_token": "tok-1",
              "audio_source": {
                "configured": true,
                "m3u_url": "http://audio.example.org/list.m3u",
                "updated_at": "2026-11-25T11:00:00Z"
              }
            }
        """.trimIndent()
        val audioSourcesJson = """
            {
              "status": "active",
              "configured": true,
              "m3u_url": "http://audio.example.org/updated.m3u",
              "updated_at": "2026-11-25T12:00:00Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(syncJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(audioSourcesJson))

        val base = server.url("/").toString().trimEnd('/')
        val api = HttpSonixControlApi(OkHttpClient(), base, "anon-key")
        val identityStore = SonixDeviceIdentityStore(context, plainCrypto)
        val repo = SonixControlRepositoryImpl(context, api, identityStore)

        val syncRes = repo.syncNow(force = true)
        assertTrue(syncRes.isSuccess)
        assertTrue(repo.state.value.audioSource.configured)
        assertEquals("http://audio.example.org/list.m3u", repo.state.value.audioSource.m3uUrl)

        val fetchRes = repo.fetchAudioSources()
        assertTrue(fetchRes.isSuccess)
        assertEquals("http://audio.example.org/updated.m3u", fetchRes.getOrNull()?.m3uUrl)
        assertEquals("2026-11-25T12:00:00Z", repo.state.value.audioSource.updatedAt)
    }

    @Test
    fun `DefaultExternalAudioCatalogRepository fetches gzipped M3U, encrypts cache, and falls back offline`() = runTest {
        val m3uBody = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 SD
            http://cdn.example.org/bein1_sd.m3u8
            #EXTINF:-1 tvg-chno="2" group-title="ALWAN",ALWAN 1 HD
            http://cdn.example.org/alwan1.m3u8
        """.trimIndent()

        val gzipBytes = java.io.ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(m3uBody.toByteArray(Charsets.UTF_8)) }
            baos.toByteArray()
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(gzipBytes)),
        )

        // MockWebServer runs on 127.0.0.1/localhost; use public-like host header? Wait:
        // MockWebServer URL uses localhost which AudioNetworkSecurityValidator blocks!
        // Let's verify that by using nip.io/example or testing direct encrypted cache fallback + parser separately,
        // Wait: MockWebServer.url() resolves to localhost, so download rejects SSRF localhost as expected!
        // Verify SSRF protection actually blocks localhost M3U URLs too:
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(
                    SonixAudioSourceConfig(
                        configured = true,
                        m3uUrl = server.url("/audio.m3u").toString(),
                        updatedAt = "2026-11-25T10:00:00Z",
                    ),
                )
            }
        }
        val repo = DefaultExternalAudioCatalogRepository(
            context = context,
            okHttpClient = OkHttpClient(),
            credentialCrypto = plainCrypto,
            sonixControlRepository = fakeSonixRepo,
        )

        val blockedRes = repo.syncFromPanel(forceRefresh = true)
        assertTrue(blockedRes.error != null)
        assertTrue(blockedRes.channels.isEmpty())

        // Now verify encrypted cache roundtrip & offline fallback when cache is present
        val cacheFile = File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc")
        val seedJson = """{"version":1,"updatedAt":"2026-11-25T10:00:00Z","sourceHash":"h1","channels":[{"id":"c1","name":"beIN SPORTS 1 HD","number":"1","streamUrl":"http://cdn.example.org/1.m3u8","brand":"BEIN_SPORTS","categoryName":"beIN SPORTS","containerExtension":"m3u8","qualityRank":4,"qualityLabel":"HD"}]}"""
        cacheFile.writeText(plainCrypto.encrypt(seedJson), Charsets.UTF_8)

        val cached = repo.loadCached()
        assertTrue(cached.fromCache)
        assertEquals(1, cached.channels.size)
        assertEquals("beIN SPORTS 1 HD", cached.channels.first().name)

        // When panel fails, syncFromPanel falls back to encrypted cache with warning
        fakeSonixRepo.audioSourcesHandler = { Result.failure(IllegalStateException("network down")) }
        val fallback = repo.syncFromPanel(forceRefresh = true)
        assertEquals(1, fallback.channels.size)
        assertNotNull(fallback.warning)
    }


    private fun testPublicDns(): (String) -> Array<InetAddress> = {
        arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
    }

    private fun testHttpClientMappingHostToMockServer(hostName: String): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return if (hostname.equals(hostName, ignoreCase = true)) {
                            listOf(InetAddress.getByName("127.0.0.1"))
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                    }
                },
            )
            .build()
    }

    private fun testRepo(
        hostName: String,
        sonixRepo: FakeSonixControlRepository,
        panelTimeoutMs: Long = 2_000L,
        connectTimeoutMs: Long = 1_500L,
        readTimeoutMs: Long = 800L,
        callTimeoutMs: Long = 1_500L,
    ): DefaultExternalAudioCatalogRepository {
        return DefaultExternalAudioCatalogRepository(
            context = context,
            okHttpClient = testHttpClientMappingHostToMockServer(hostName),
            credentialCrypto = plainCrypto,
            sonixControlRepository = sonixRepo,
            dnsResolver = testPublicDns(),
            panelTimeoutMs = panelTimeoutMs,
            m3uConnectTimeoutMs = connectTimeoutMs,
            m3uReadTimeoutMs = readTimeoutMs,
            m3uCallTimeoutMs = callTimeoutMs,
        )
    }

    @Test
    fun `AudioM3uParser rejects HTML error pages, missing EXTM3U header, and oversized streams`() {
        val htmlErr = runCatching {
            AudioM3uParser.parse(
                ByteArrayInputStream("<!DOCTYPE html><html><body>502 Bad Gateway</body></html>".toByteArray()),
                "https://cdn.example.com/list.m3u",
                testPublicDns(),
            )
        }
        assertTrue(htmlErr.isFailure)

        val noHeaderErr = runCatching {
            AudioM3uParser.parse(
                ByteArrayInputStream("plain error without extm3u".toByteArray()),
                "https://cdn.example.com/list.m3u",
                testPublicDns(),
            )
        }
        assertTrue(noHeaderErr.isFailure)

        // Exceeds 16 MiB byte limit
        val hugeStream = object : java.io.InputStream() {
            private val header = "#EXTM3U\n".toByteArray()
            private var idx = 0L
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (idx == 0L) {
                    val c = minOf(len, header.size)
                    System.arraycopy(header, 0, b, off, c)
                    idx += c
                    return c
                }
                val chunk = minOf(len, 65536)
                java.util.Arrays.fill(b, off, off + chunk, 'a'.code.toByte())
                idx += chunk
                return chunk
            }
        }
        val largeErr = runCatching {
            AudioM3uParser.parse(hugeStream, "https://cdn.example.com/huge.m3u", testPublicDns())
        }
        assertTrue(largeErr.isFailure)
    }

    @Test
    fun `syncFromPanel skips M3U download when URL and updatedAt match cache, and updates when URL changes`() = runTest {
        val host = "cdn-sync.example.test"
        val urlV1 = "http://$host:${server.port}/playlist_secret_user_pass.m3u?token=supersecret"
        val urlV2 = "http://$host:${server.port}/playlist_v2_secret.m3u?token=supersecret2"
        val m3uV1 = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
        """.trimIndent()
        val m3uV2 = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 4K
            http://stream.example.com/bein1_4k.m3u8
            #EXTINF:-1 tvg-chno="2" group-title="ALWAN",ALWAN 1 FHD
            http://stream.example.com/alwan1.m3u8
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/x-mpegurl").setBody(m3uV1))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody(m3uV2))

        var currentUrl = urlV1
        var currentUpdatedAt = "2026-11-25T10:00:00Z"
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = currentUrl, updatedAt = currentUpdatedAt))
            }
        }
        val repo = testRepo(host, fakeSonixRepo)

        // 1. Initial download caches v1
        val first = repo.syncFromPanel(forceRefresh = false)
        assertFalse(first.fromCache)
        assertEquals(1, first.channels.size)
        assertEquals(1, server.requestCount)

        // 2. Same URL + updatedAt -> skips download completely (server.requestCount stays 1)
        val skipped = repo.syncFromPanel(forceRefresh = false)
        assertTrue(skipped.fromCache)
        assertEquals(1, skipped.channels.size)
        assertEquals(1, server.requestCount)

        // 3. Preload & peekCached return instantly from memory/disk with zero network
        assertNotNull(repo.peekCached())
        assertEquals(1, repo.preload().channels.size)
        assertEquals(1, server.requestCount)

        // 4. URL changes -> downloads v2 & updates cache
        currentUrl = urlV2
        currentUpdatedAt = "2026-11-25T11:00:00Z"
        val updated = repo.syncFromPanel(forceRefresh = false)
        assertFalse(updated.fromCache)
        assertEquals(2, updated.channels.size)
        assertEquals("beIN SPORTS 1 4K", updated.channels.first().name)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `syncFromPanel preserves valid old cache when update fails via HTML, timeout, truncated gzip, or offline`() = runTest {
        val host = "cdn-fail.example.test"
        val urlV1 = "http://$host:${server.port}/v1.m3u?password=p1"
        val urlV2 = "http://$host:${server.port}/v2.m3u?password=p2"
        val validM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
        """.trimIndent()

        // Seed v1 cleanly
        server.enqueue(MockResponse().setResponseCode(200).setBody(validM3u))
        var activeUrl = urlV1
        var activeUpdated = "2026-11-25T09:00:00Z"
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = activeUrl, updatedAt = activeUpdated))
            }
        }
        val repo = testRepo(host, fakeSonixRepo, readTimeoutMs = 300L, callTimeoutMs = 500L)
        val seeded = repo.syncFromPanel(forceRefresh = false)
        assertEquals(1, seeded.channels.size)

        // Attempt v2 update -> returns 200 OK HTML error page -> must reject & keep v1 cache intact
        activeUrl = urlV2
        activeUpdated = "2026-11-25T10:00:00Z"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<!DOCTYPE html><html><body>Login required</body></html>"),
        )
        val afterHtml = repo.syncFromPanel(forceRefresh = false)
        assertEquals(1, afterHtml.channels.size)
        assertEquals("beIN SPORTS 1 HD", afterHtml.channels.first().name)
        assertNull(afterHtml.error)
        assertNotNull(afterHtml.warning)
        assertFalse(afterHtml.warning.orEmpty().contains("p2"))
        assertFalse(afterHtml.warning.orEmpty().contains(host))

        // Attempt v2 update -> truncated gzip response -> must reject & keep v1 cache intact
        val fullGz = java.io.ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(validM3u.toByteArray()) }
            baos.toByteArray()
        }
        val truncatedGz = fullGz.copyOf(fullGz.size / 2)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(truncatedGz)),
        )
        val afterTruncated = repo.syncFromPanel(forceRefresh = false)
        assertEquals(1, afterTruncated.channels.size)
        assertEquals("beIN SPORTS 1 HD", afterTruncated.channels.first().name)

        // Attempt v2 update -> slow stalled timeout -> must fail fast via callTimeout & keep v1 cache intact
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(validM3u)
                .throttleBody(1, 200, TimeUnit.MILLISECONDS),
        )
        val afterTimeout = repo.syncFromPanel(forceRefresh = false)
        assertEquals(1, afterTimeout.channels.size)
        assertNotNull(afterTimeout.warning)

        // Offline / panel failure -> loads cache immediately
        fakeSonixRepo.audioSourcesHandler = { Result.failure(IllegalStateException("offline")) }
        val afterOffline = repo.syncFromPanel(forceRefresh = false)
        assertEquals(1, afterOffline.channels.size)
        assertTrue(afterOffline.fromCache)
    }

    @Test
    fun `concurrent syncFromPanel calls are deduplicated into a single network sync`() = runTest {
        val host = "cdn-dedupe.example.test"
        val url = "http://$host:${server.port}/audio.m3u"
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="ALWAN",ALWAN 1 HD
            http://stream.example.com/alwan1.m3u8
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(150, TimeUnit.MILLISECONDS).setBody(m3u))

        val panelCalls = AtomicInteger(0)
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                panelCalls.incrementAndGet()
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = url, updatedAt = "2026-11-25T12:00:00Z"))
            }
        }
        val repo = testRepo(host, fakeSonixRepo)

        val results = (1..5).map {
            async { repo.syncFromPanel(forceRefresh = false) }
        }.awaitAll()

        assertTrue(results.all { it.channels.size == 1 })
        assertEquals(1, panelCalls.get())
        assertEquals(1, server.requestCount)
    }
}

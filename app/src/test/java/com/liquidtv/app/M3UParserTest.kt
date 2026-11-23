package com.liquidtv.app

import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3UParserTest {

    private val parser = M3UParser()

    @Test
    fun `parses UTF-8 BOM, XMLTV header, Arabic names, and commas inside quoted attributes and titles`() = runTest {
        val m3u = "\uFEFF" + """
            #EXTM3U x-tvg-url="http://epg.provider.local/guide.xml"
            #EXTINF:-1 tvg-id="mbc1.ae" tvg-name="MBC 1, HD" tvg-logo="http://img/mbc.png" group-title="Arabic, Entertainment" tvg-country="AE" tvg-language="Arabic",MBC 1, Live & HD
            http://iptv.local:8080/live/user/pass/1001.ts
            #EXTINF:-1 group-title="VOD Movies",Action Movie: Part 1, Director's Cut
            http://iptv.local:8080/movie/user/pass/2001.mp4
            #EXTINF:-1,قناة الجزيرة الإخبارية
            https://cdn.example.com/aljazeera/index.m3u8
        """.trimIndent().replace("\n", "\r\n")

        val result = parser.parse(m3u, subscriptionId = "sub-1")
        assertEquals("http://epg.provider.local/guide.xml", result.xmltvUrl)
        assertEquals(3, result.channels.size)

        val ch1 = result.channels[0]
        assertEquals("sub-1", ch1.subscriptionId)
        assertEquals("MBC 1, Live & HD", ch1.name)
        assertEquals("Arabic, Entertainment", ch1.groupTitle)
        assertEquals("United Arab Emirates", ch1.country)
        assertEquals("Arabic", ch1.language)
        assertEquals("mbc1.ae", ch1.tvgId)
        assertEquals(MediaType.LIVE, ch1.mediaType)

        val ch2 = result.channels[1]
        assertEquals("Action Movie: Part 1, Director's Cut", ch2.name)
        assertEquals(MediaType.MOVIE, ch2.mediaType)

        val ch3 = result.channels[2]
        assertEquals("قناة الجزيرة الإخبارية", ch3.name)
    }

    @Test
    fun `extracts EXTVLCOPT and EXTHTTP headers and skips malformed entries without crashing`() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Bad Channel Without Url
            #NOT_A_URL
            #EXTINF:-1,Stream With Custom Headers
            #EXTVLCOPT:http-user-agent=CustomPlayer/2.0
            #EXTVLCOPT:http-referrer=http://portal.local/
            #EXTHTTP:{"Authorization":"Bearer abc123"}
            http://edge.local/stream/42.m3u8
        """.trimIndent()

        val result = parser.parse(m3u, subscriptionId = "sub-2")
        assertEquals(1, result.channels.size)
        val ch = result.channels.first()
        assertEquals("Stream With Custom Headers", ch.name)
        assertEquals("CustomPlayer/2.0", ch.httpHeaders["User-Agent"])
        assertEquals("http://portal.local/", ch.httpHeaders["Referer"])
        assertEquals("Bearer abc123", ch.httpHeaders["Authorization"])
    }

    @Test
    fun `deterministic IDs are stable across parses and scoped by subscriptionId`() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Channel Alpha
            http://stream.local/alpha.ts
            #EXTINF:-1,Channel Alpha
            http://stream.local/alpha.ts
            #EXTINF:-1,Channel Beta
            http://stream.local/beta.ts
        """.trimIndent()

        val first = parser.parse(m3u, subscriptionId = "sub-A")
        val second = parser.parse(m3u, subscriptionId = "sub-A")
        val otherSub = parser.parse(m3u, subscriptionId = "sub-B")

        // Duplicate URL deduplicated
        assertEquals(2, first.channels.size)
        assertEquals(first.channels.map { it.id }, second.channels.map { it.id })
        assertNotEquals(first.channels[0].id, otherSub.channels[0].id)
    }

    @Test
    fun `streams 6000 channel large M3U playlist efficiently and supports cancellation`() = runTest {
        val sb = StringBuilder(6000 * 90)
        sb.appendLine("#EXTM3U")
        for (i in 1..6000) {
            sb.appendLine("""#EXTINF:-1 tvg-id="ch$i" group-title="Group ${i % 20}",Channel #$i""")
            sb.appendLine("http://stream.local:8080/live/u/p/$i.ts")
        }
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        var lastProgress = 0f
        val parsed = parser.parseStream(
            inputStream = ByteArrayInputStream(bytes),
            contentLengthBytes = bytes.size.toLong(),
            subscriptionId = "large-sub",
            onProgress = { p -> lastProgress = p },
        )

        assertEquals(6000, parsed.channels.size)
        assertTrue(lastProgress >= 0.9f)

        // Cancellation test
        val job = Job()
        var cancelled = false
        val child = launch(job) {
            try {
                job.cancel()
                parser.parseStream(ByteArrayInputStream(bytes), bytes.size.toLong(), "cancel-sub")
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        child.join()
        assertTrue(cancelled)
    }
}

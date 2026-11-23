package com.liquidtv.app.data.parser

import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.MediaType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory-efficient streaming M3U / M3U8 playlist parser supporting:
 * - Large playlists streamed line-by-line via BufferedReader (no full-file `content.lines()`)
 * - UTF-8 BOM stripping, CRLF/LF/CR line endings, Arabic and full Unicode
 * - `#EXTM3U` (`x-tvg-url`, `url-tvg`, `tvg-url` for EPG)
 * - `#EXTINF` with quoted/unquoted attributes, commas inside quoted attributes AND channel names
 * - `#EXTGRP`, `#EXTVLCOPT` (`http-user-agent`, `http-referrer`), `#EXTHTTP` JSON headers
 * - Deterministic stable IDs scoped by subscriptionId (never random UUIDs)
 * - Cooperative coroutine cancellation & real byte-based progress reporting
 */
@Singleton
class M3UParser @Inject constructor() {

    companion object {
        private const val EXT_M3U = "#EXTM3U"
        private const val EXT_INF = "#EXTINF:"
        private const val EXT_GRP = "#EXTGRP:"
        private const val EXT_VLC_OPT = "#EXTVLCOPT:"
        private const val EXT_HTTP = "#EXTHTTP:"
        private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]*))""")
        private val SUPPORTED_SCHEMES = listOf("http://", "https://", "rtmp://", "rtmps://", "rtsp://", "udp://", "rtp://")

        private val COUNTRY_NAMES = mapOf(
            "us" to "United States", "uk" to "United Kingdom", "gb" to "United Kingdom",
            "in" to "India", "de" to "Germany", "fr" to "France", "es" to "Spain",
            "it" to "Italy", "jp" to "Japan", "cn" to "China", "br" to "Brazil",
            "ru" to "Russia", "ca" to "Canada", "au" to "Australia", "mx" to "Mexico",
            "kr" to "South Korea", "nl" to "Netherlands", "ar" to "Argentina",
            "tr" to "Turkey", "sa" to "Saudi Arabia", "ae" to "United Arab Emirates",
            "eg" to "Egypt", "pk" to "Pakistan", "ng" to "Nigeria", "za" to "South Africa",
            "ma" to "Morocco", "dz" to "Algeria", "tn" to "Tunisia", "jo" to "Jordan",
            "kw" to "Kuwait", "qa" to "Qatar", "bh" to "Bahrain", "om" to "Oman",
            "lb" to "Lebanon", "iq" to "Iraq", "pl" to "Poland", "se" to "Sweden",
            "no" to "Norway", "dk" to "Denmark", "fi" to "Finland", "pt" to "Portugal",
            "be" to "Belgium", "ch" to "Switzerland", "at" to "Austria", "nz" to "New Zealand",
            "sg" to "Singapore", "my" to "Malaysia", "ph" to "Philippines",
            "id" to "Indonesia", "th" to "Thailand", "vn" to "Vietnam", "gr" to "Greece",
        )
    }

    data class ParseResult(
        val channels: List<Channel>,
        val totalLines: Int,
        val parsedEntries: Int,
        val skippedEntries: Int,
        val xmltvUrl: String? = null,
    )

    suspend fun parse(
        content: String,
        subscriptionId: String = "",
        defaultUserAgent: String? = null,
        defaultReferer: String? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): ParseResult {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return parseStream(
            inputStream = ByteArrayInputStream(bytes),
            contentLengthBytes = bytes.size.toLong(),
            subscriptionId = subscriptionId,
            defaultUserAgent = defaultUserAgent,
            defaultReferer = defaultReferer,
            onProgress = onProgress,
        )
    }

    suspend fun parseStream(
        inputStream: InputStream,
        contentLengthBytes: Long = -1L,
        subscriptionId: String = "",
        defaultUserAgent: String? = null,
        defaultReferer: String? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): ParseResult {
        val channels = ArrayList<Channel>(256)
        val seenIds = HashMap<String, Int>()
        val seenUrls = HashSet<String>()
        var totalLines = 0
        var parsedEntries = 0
        var skippedEntries = 0
        var xmltvUrl: String? = null

        var pendingExtInf: String? = null
        var pendingExtGrp: String? = null
        val pendingHeaders = LinkedHashMap<String, String>()
        var bytesReadApprox = 0L

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32 * 1024).use { reader ->
            var rawLine: String? = reader.readLine()
            while (rawLine != null) {
                totalLines++
                if (totalLines % 250 == 0) {
                    currentCoroutineContext().ensureActive()
                    if (contentLengthBytes > 0L) {
                        val frac = (bytesReadApprox.toFloat() / contentLengthBytes.toFloat()).coerceIn(0f, 0.99f)
                        onProgress?.invoke(frac)
                    }
                }

                bytesReadApprox += rawLine.length + 1L
                val line = if (totalLines == 1) rawLine.removePrefix("\uFEFF").trim() else rawLine.trim()

                when {
                    line.isEmpty() -> Unit
                    line.startsWith(EXT_M3U, ignoreCase = true) -> {
                        if (xmltvUrl == null) {
                            xmltvUrl = extractXmltvUrl(line)
                        }
                    }
                    line.startsWith(EXT_INF, ignoreCase = true) -> {
                        pendingExtInf = line
                    }
                    line.startsWith(EXT_GRP, ignoreCase = true) -> {
                        pendingExtGrp = line.substring(EXT_GRP.length).trim().takeIf { it.isNotEmpty() }
                    }
                    line.startsWith(EXT_VLC_OPT, ignoreCase = true) -> {
                        parseVlcOption(line.substring(EXT_VLC_OPT.length).trim(), pendingHeaders)
                    }
                    line.startsWith(EXT_HTTP, ignoreCase = true) -> {
                        parseExtHttpJson(line.substring(EXT_HTTP.length).trim(), pendingHeaders)
                    }
                    line.startsWith("#") -> Unit
                    isStreamUrl(line) -> {
                        val streamUrl = line
                        if (!seenUrls.add(streamUrl)) {
                            skippedEntries++
                        } else {
                            val mergedHeaders = LinkedHashMap<String, String>()
                            if (!defaultUserAgent.isNullOrBlank()) mergedHeaders["User-Agent"] = defaultUserAgent
                            if (!defaultReferer.isNullOrBlank()) mergedHeaders["Referer"] = defaultReferer
                            mergedHeaders.putAll(pendingHeaders)

                            val channel = try {
                                if (pendingExtInf != null) {
                                    parseEntry(
                                        extInf = pendingExtInf,
                                        streamUrl = streamUrl,
                                        subscriptionId = subscriptionId,
                                        fallbackGroup = pendingExtGrp,
                                        httpHeaders = mergedHeaders,
                                        seenIds = seenIds,
                                    )
                                } else {
                                    val name = extractChannelNameFromUrl(streamUrl)
                                    val mediaType = inferMediaType(streamUrl = streamUrl, groupTitle = pendingExtGrp)
                                    val stableId = makeUniqueStableId(
                                        subscriptionId = subscriptionId,
                                        mediaType = mediaType,
                                        baseKey = sha256Short("$name|$streamUrl"),
                                        seenIds = seenIds,
                                    )
                                    Channel(
                                        id = stableId,
                                        subscriptionId = subscriptionId,
                                        mediaType = mediaType,
                                        name = name,
                                        streamUrl = streamUrl,
                                        groupTitle = pendingExtGrp,
                                        httpHeaders = mergedHeaders,
                                    )
                                }
                            } catch (_: Exception) {
                                skippedEntries++
                                null
                            }

                            if (channel != null) {
                                channels.add(channel)
                                parsedEntries++
                            }
                        }
                        pendingExtInf = null
                        pendingExtGrp = null
                        pendingHeaders.clear()
                    }
                    else -> {
                        if (pendingExtInf != null) {
                            skippedEntries++
                            pendingExtInf = null
                            pendingExtGrp = null
                            pendingHeaders.clear()
                        }
                    }
                }
                rawLine = reader.readLine()
            }
        }

        onProgress?.invoke(1f)
        return ParseResult(
            channels = channels,
            totalLines = totalLines,
            parsedEntries = parsedEntries,
            skippedEntries = skippedEntries,
            xmltvUrl = xmltvUrl,
        )
    }

    private fun extractXmltvUrl(extM3uLine: String): String? {
        val attrs = parseAttributes(extM3uLine.removePrefix(EXT_M3U))
        return (attrs["x-tvg-url"] ?: attrs["url-tvg"] ?: attrs["tvg-url"])
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private fun parseEntry(
        extInf: String,
        streamUrl: String,
        subscriptionId: String,
        fallbackGroup: String?,
        httpHeaders: Map<String, String>,
        seenIds: MutableMap<String, Int>,
    ): Channel {
        val afterPrefix = extInf.substring(EXT_INF.length)
        val splitIndex = findDisplayNameCommaIndex(afterPrefix)
        val attrSection = if (splitIndex >= 0) afterPrefix.substring(0, splitIndex) else afterPrefix
        val displayName = if (splitIndex >= 0) afterPrefix.substring(splitIndex + 1).trim() else ""

        val attrs = parseAttributes(attrSection)
        val tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
        val tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
        val tvgLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
        val groupTitle = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: fallbackGroup
        val tvgCountry = attrs["tvg-country"]?.takeIf { it.isNotBlank() }?.lowercase()
        val tvgLanguage = attrs["tvg-language"]?.takeIf { it.isNotBlank() }
        val tvgChno = attrs["tvg-chno"]?.toIntOrNull()

        val name = displayName.takeIf { it.isNotBlank() }
            ?: tvgName?.takeIf { it.isNotBlank() }
            ?: extractChannelNameFromUrl(streamUrl)

        val baseStableKey = if (tvgId != null) {
            sha256Short("tvgid:${tvgId.lowercase()}|$streamUrl")
        } else {
            sha256Short("name:${name.lowercase()}|$streamUrl")
        }
        val mediaType = inferMediaType(streamUrl = streamUrl, groupTitle = groupTitle)
        val stableId = makeUniqueStableId(subscriptionId, mediaType, baseStableKey, seenIds)

        val countryCodeFromId = tvgId
            ?.substringBefore("@")
            ?.substringAfterLast(".", "")
            ?.takeIf { it.length == 2 && it.all { c -> c.isLetter() } }
            ?.lowercase()
        val resolvedCountryCode = tvgCountry ?: countryCodeFromId
        val finalCountry = resolvedCountryCode?.let { COUNTRY_NAMES[it] ?: it.uppercase() }

        return Channel(
            id = stableId,
            subscriptionId = subscriptionId,
            externalId = tvgId ?: baseStableKey,
            mediaType = mediaType,
            name = name,
            streamUrl = streamUrl,
            logoUrl = tvgLogo,
            groupTitle = groupTitle,
            tvgId = tvgId,
            tvgName = tvgName,
            country = finalCountry,
            language = tvgLanguage,
            channelNumber = tvgChno,
            httpHeaders = httpHeaders,
        )
    }

    /**
     * Scans `#EXTINF` after prefix, ignoring commas inside single or double quotes,
     * and returns the comma index separating attributes from the channel display name.
     */
    private fun findDisplayNameCommaIndex(text: String): Int {
        var inDoubleQuotes = false
        var inSingleQuotes = false
        for (i in text.indices) {
            when (val ch = text[i]) {
                '"' -> if (!inSingleQuotes) inDoubleQuotes = !inDoubleQuotes
                '\'' -> if (!inDoubleQuotes) inSingleQuotes = !inSingleQuotes
                ',' -> if (!inDoubleQuotes && !inSingleQuotes) return i
            }
        }
        return -1
    }

    private fun parseAttributes(segment: String): Map<String, String> {
        val attrs = HashMap<String, String>()
        ATTR_REGEX.findAll(segment).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }.trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                attrs[key] = value
            }
        }
        return attrs
    }

    private fun parseVlcOption(opt: String, outHeaders: MutableMap<String, String>) {
        val eqIdx = opt.indexOf('=')
        if (eqIdx <= 0) return
        val key = opt.substring(0, eqIdx).trim().lowercase()
        val value = opt.substring(eqIdx + 1).trim()
        if (value.isEmpty()) return
        when (key) {
            "http-user-agent" -> outHeaders["User-Agent"] = value
            "http-referrer", "http-referer" -> outHeaders["Referer"] = value
            "http-origin" -> outHeaders["Origin"] = value
        }
    }

    private fun parseExtHttpJson(raw: String, outHeaders: MutableMap<String, String>) {
        if (!raw.startsWith("{")) return
        try {
            val jsonObj = Json.parseToJsonElement(raw).jsonObject
            jsonObj.forEach { (key, elem) ->
                val value = elem.jsonPrimitive.contentOrNull?.trim()
                if (!value.isNullOrEmpty()) {
                    outHeaders[key] = value
                }
            }
        } catch (_: Exception) {
            // Malformed #EXTHTTP line — ignore gracefully
        }
    }

    private fun inferMediaType(streamUrl: String, groupTitle: String?): MediaType {
        val cleanUrl = streamUrl.lowercase().substringBefore('?')
        val pathOnly = cleanUrl.substringAfter("://").substringAfter("/", "")
        val isMovieExt = pathOnly.endsWith(".mp4") ||
            pathOnly.endsWith(".mkv") ||
            pathOnly.endsWith(".avi") ||
            pathOnly.endsWith(".mov") ||
            pathOnly.endsWith(".m4v") ||
            pathOnly.endsWith(".wmv") ||
            pathOnly.endsWith(".flv")
        val isMoviePath = pathOnly.contains("/movie/") || pathOnly.contains("/vod/")
        val isLiveExt = pathOnly.endsWith(".ts") || pathOnly.endsWith(".m3u8")
        val lowerGroup = groupTitle?.lowercase().orEmpty()
        val isMovieGroup = lowerGroup.contains("vod") || lowerGroup.contains("movie") || lowerGroup.contains("أفلام")
        return if (isMovieExt || isMoviePath || (!isLiveExt && isMovieGroup)) {
            MediaType.MOVIE
        } else {
            MediaType.LIVE
        }
    }

    private fun isStreamUrl(line: String): Boolean =
        SUPPORTED_SCHEMES.any { line.startsWith(it, ignoreCase = true) }

    private fun makeUniqueStableId(
        subscriptionId: String,
        mediaType: MediaType,
        baseKey: String,
        seenIds: MutableMap<String, Int>,
    ): String {
        val typeTag = if (mediaType == MediaType.MOVIE) "vod" else "live"
        val prefix = if (subscriptionId.isNotBlank()) "${subscriptionId}_${typeTag}_$baseKey" else "${typeTag}_$baseKey"
        val count = seenIds.getOrDefault(prefix, 0)
        seenIds[prefix] = count + 1
        return if (count == 0) prefix else "${prefix}_$count"
    }

    private fun sha256Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun extractChannelNameFromUrl(url: String): String {
        return try {
            val path = url.substringAfter("://").substringAfter("/")
            val last = path.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
            last.replace(Regex("[_\\-+]"), " ").trim().takeIf { it.isNotBlank() }
                ?: "Unknown Channel"
        } catch (_: Exception) {
            "Unknown Channel"
        }
    }
}

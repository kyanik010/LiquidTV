package com.liquidtv.app.data.audio

import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.URL

object AudioM3uParser {
    private const val MAX_M3U_CHARS = 128 * 1024 * 1024
    private val ATTRIBUTE_REGEX_CACHE = HashMap<String, Regex>()

    fun parse(
        input: InputStream,
        baseUrl: String,
        dnsResolver: (String) -> Array<InetAddress> = { host -> InetAddress.getAllByName(host) },
    ): List<ExternalAudioChannel> {
        val rawChannels = mutableListOf<ExternalAudioChannel>()
        val seenUrls = HashSet<String>()
        val validatedHosts = HashSet<String>()
        val base = runCatching { URL(baseUrl) }.getOrNull()

        var pendingInfo: String? = null
        var pendingGroup = ""
        var channelNumber = 0
        var totalChars = 0

        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                totalChars += rawLine.length
                if (totalChars > MAX_M3U_CHARS) {
                    throw IllegalStateException("حجم قائمة M3U تجاوز الحد الآمن (128 ميغابايت)")
                }
                val line = rawLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingInfo = line
                        val attrGroup = extractAttribute(line, "group-title")
                        if (attrGroup.isNotBlank()) pendingGroup = attrGroup
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pendingGroup = line.substringAfter(':').trim()
                    }
                    line.startsWith("#") -> Unit
                    pendingInfo != null -> {
                        val info = pendingInfo.orEmpty()
                        val displayName = info.substringAfterLast(',', "").trim()
                        val name = extractAttribute(info, "tvg-name").ifBlank { displayName }
                        val group = pendingGroup.ifBlank { extractAttribute(info, "group-title") }
                        val brand = AudioSourceClassifier.classify(name, group)

                        if (brand != AudioChannelBrand.OTHER) {
                            val resolved = resolveUrl(base, baseUrl, line)
                            val uri = resolved?.let { runCatching { URL(it) }.getOrNull() }
                            if (uri != null &&
                                (uri.protocol.equals("http", true) || uri.protocol.equals("https", true)) &&
                                !uri.host.isNullOrBlank()
                            ) {
                                val hostKey = uri.host.lowercase()
                                val hostSafe = if (validatedHosts.contains(hostKey)) {
                                    true
                                } else {
                                    val ok = runCatching {
                                        AudioNetworkSecurityValidator.assertSafeUrl(resolved, dnsResolver)
                                    }.isSuccess
                                    if (ok) validatedHosts.add(hostKey)
                                    ok
                                }
                                if (hostSafe && seenUrls.add(resolved)) {
                                    channelNumber++
                                    val chNo = extractAttribute(info, "tvg-chno").ifBlank { channelNumber.toString() }
                                    val cleanName = name.ifBlank { "قناة صوتية $channelNumber" }
                                    val qRank = AudioSourceClassifier.qualityRank(cleanName, group)
                                    val extension = uri.path.substringAfterLast('.', "").lowercase()
                                        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "m3u8"
                                    rawChannels += ExternalAudioChannel(
                                        id = resolved,
                                        name = cleanName,
                                        number = chNo,
                                        streamUrl = resolved,
                                        brand = brand,
                                        categoryName = group.ifBlank { brand.displayName },
                                        containerExtension = extension,
                                        qualityRank = qRank,
                                        qualityLabel = AudioSourceClassifier.qualityLabel(qRank),
                                    )
                                }
                            }
                        }
                        pendingInfo = null
                        pendingGroup = ""
                    }
                }
            }
        }
        return AudioSourceClassifier.sortChannels(rawChannels)
    }

    private fun resolveUrl(base: URL?, baseUrl: String, line: String): String? {
        return try {
            if (base != null) URL(base, line).toString() else URL(URL(baseUrl), line).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAttribute(line: String, name: String): String {
        val regex = synchronized(ATTRIBUTE_REGEX_CACHE) {
            ATTRIBUTE_REGEX_CACHE.getOrPut(name.lowercase()) {
                Regex("""(?:^|\s)${Regex.escape(name)}="([^"]*)"""", RegexOption.IGNORE_CASE)
            }
        }
        return regex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
}

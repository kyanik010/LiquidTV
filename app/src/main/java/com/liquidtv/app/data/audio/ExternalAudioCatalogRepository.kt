package com.liquidtv.app.data.audio

import android.content.Context
import android.util.Log
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.security.LogSanitizer
import com.liquidtv.app.domain.model.ExternalAudioCatalogResult
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.repository.SonixControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

interface ExternalAudioCatalogRepository {
    suspend fun loadCached(): ExternalAudioCatalogResult
    suspend fun syncFromPanel(forceRefresh: Boolean = false): ExternalAudioCatalogResult
}

@Serializable
private data class CachedAudioCatalogEnvelope(
    val version: Int = 1,
    val updatedAt: String = "",
    val sourceHash: String = "",
    val channels: List<ExternalAudioChannel> = emptyList(),
)

@Singleton
class DefaultExternalAudioCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val credentialCrypto: CredentialCrypto,
    private val sonixControlRepository: SonixControlRepository,
) : ExternalAudioCatalogRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    override suspend fun loadCached(): ExternalAudioCatalogResult = withContext(Dispatchers.IO) {
        readCachedInternal() ?: ExternalAudioCatalogResult(
            channels = emptyList(),
            fromCache = false,
            error = "لا توجد مصادر صوت محفوظة بعد",
        )
    }

    override suspend fun syncFromPanel(forceRefresh: Boolean): ExternalAudioCatalogResult =
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val cached = readCachedInternal()
                val panelResult = sonixControlRepository.fetchAudioSources()
                val config = panelResult.getOrNull() ?: sonixControlRepository.state.value.audioSource

                if (panelResult.isFailure && !config.configured) {
                    val panelErr = panelResult.exceptionOrNull()?.message ?: "تعذّر جلب مصادر الصوت من لوحة التحكم"
                    return@withContext if (cached != null && cached.channels.isNotEmpty()) {
                        cached.copy(warning = "$panelErr؛ تم استخدام القنوات المحفوظة")
                    } else {
                        ExternalAudioCatalogResult(error = panelErr)
                    }
                }

                val m3uUrl = config.m3uUrl?.trim().orEmpty()
                if (!config.configured || m3uUrl.isBlank()) {
                    return@withContext ExternalAudioCatalogResult(
                        channels = emptyList(),
                        error = "لم يتم ضبط رابط قائمة الصوت الخارجي لهذا الجهاز في لوحة SONIX",
                    )
                }

                val updatedAt = config.updatedAt?.trim().orEmpty()
                val sourceHash = sha256(m3uUrl)
                val cachedEnvelope = readCachedEnvelope()
                if (!forceRefresh &&
                    cached != null &&
                    cachedEnvelope != null &&
                    cached.channels.isNotEmpty() &&
                    cachedEnvelope.sourceHash == sourceHash &&
                    (updatedAt.isEmpty() || cachedEnvelope.updatedAt == updatedAt)
                ) {
                    return@withContext cached.copy(fromCache = true)
                }

                try {
                    val channels = downloadAndParseM3u(m3uUrl)
                    if (channels.isEmpty()) {
                        throw IllegalStateException("لم يتم العثور على قنوات beIN SPORTS أو ALWAN داخل قائمة M3U")
                    }
                    val envelope = CachedAudioCatalogEnvelope(
                        version = 1,
                        updatedAt = updatedAt,
                        sourceHash = sourceHash,
                        channels = channels,
                    )
                    persistEncrypted(envelope)
                    ExternalAudioCatalogResult(
                        channels = channels,
                        updatedAt = updatedAt.ifBlank { null },
                        fromCache = false,
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Failed to sync external audio M3U from ${LogSanitizer.sanitizeUrl(m3uUrl)}: ${e.message}",
                    )
                    if (cached != null && cached.channels.isNotEmpty()) {
                        cached.copy(
                            fromCache = true,
                            warning = "تعذّر تحديث قائمة الصوت؛ تم استخدام النسخة المحفوظة",
                        )
                    } else {
                        ExternalAudioCatalogResult(
                            error = e.message ?: "تعذّر تحميل قائمة الصوت الخارجي",
                        )
                    }
                }
            }
        }

    private fun downloadAndParseM3u(initialUrl: String): List<ExternalAudioChannel> {
        val noRedirectClient = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        var currentUrl = initialUrl
        var redirectCount = 0
        var finalResponse: Response? = null

        try {
            while (true) {
                AudioNetworkSecurityValidator.assertSafeUrl(currentUrl)
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
                    .build()

                val response = noRedirectClient.newCall(request).execute()
                if (response.code in 300..399) {
                    val location = response.header("Location")
                    response.close()
                    if (location.isNullOrBlank()) {
                        throw IllegalStateException("أعاد خادم قائمة M3U تحويلاً دون وجهة")
                    }
                    redirectCount++
                    if (redirectCount > MAX_REDIRECTS) {
                        throw IllegalStateException("تجاوزت قائمة M3U عدد التحويلات المسموح")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                } else {
                    finalResponse = response
                    break
                }
            }

            val response = finalResponse
            response.use { res ->
                if (!res.isSuccessful) {
                    throw IllegalStateException("فشل جلب قائمة M3U (HTTP ${res.code})")
                }
                val body = res.body ?: throw IllegalStateException("استجابة قائمة M3U فارغة")
                val rawStream = body.byteStream()
                val stream = if (res.header("Content-Encoding").equals("gzip", ignoreCase = true)) {
                    GZIPInputStream(rawStream, 64 * 1024)
                } else {
                    rawStream
                }
                return AudioM3uParser.parse(stream, currentUrl)
            }
        } catch (e: Exception) {
            finalResponse?.close()
            throw e
        }
    }

    private fun readCachedInternal(): ExternalAudioCatalogResult? {
        val envelope = readCachedEnvelope() ?: return null
        if (envelope.channels.isEmpty()) return null
        return ExternalAudioCatalogResult(
            channels = AudioSourceClassifier.sortChannels(envelope.channels),
            updatedAt = envelope.updatedAt.ifBlank { null },
            fromCache = true,
        )
    }

    private fun readCachedEnvelope(): CachedAudioCatalogEnvelope? {
        val file = cacheFile
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            val stored = file.readText(Charsets.UTF_8)
            val decrypted = credentialCrypto.decrypt(stored)
            json.decodeFromString<CachedAudioCatalogEnvelope>(decrypted)
        }.getOrNull()
    }

    private fun persistEncrypted(envelope: CachedAudioCatalogEnvelope) {
        val file = cacheFile
        val parent = file.parentFile ?: throw IllegalStateException("تعذر تحديد مجلد ذاكرة الصوت")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد ذاكرة الصوت")
        }
        val plaintext = json.encodeToString(envelope)
        val encrypted = credentialCrypto.encrypt(plaintext)
        val tempFile = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(encrypted.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                android.system.Os.rename(tempFile.absolutePath, file.absolutePath)
            } catch (_: Throwable) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    throw IllegalStateException("تعذر حفظ ذاكرة الصوت بشكل ذري")
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ExternalAudioCatalog"
        private const val CACHE_FILE_NAME = "liquidtv_external_audio_catalog_v1.enc"
        private const val MAX_REDIRECTS = 5
    }
}

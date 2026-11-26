package com.liquidtv.app.data.audio

import android.content.Context
import android.util.Log
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.ExternalAudioCatalogResult
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.repository.SonixControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

interface ExternalAudioCatalogRepository {
    fun peekCached(): ExternalAudioCatalogResult? = null
    suspend fun preload(): ExternalAudioCatalogResult = loadCached()
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

private data class CachedAudioCatalogState(
    val envelope: CachedAudioCatalogEnvelope,
    val result: ExternalAudioCatalogResult,
)

private data class InFlightSync(
    val forceRefresh: Boolean,
    val deferred: CompletableDeferred<ExternalAudioCatalogResult?>,
)

@Singleton
class DefaultExternalAudioCatalogRepository internal constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val credentialCrypto: CredentialCrypto,
    private val sonixControlRepository: SonixControlRepository,
    private val dnsResolver: (String) -> Array<InetAddress>,
    private val panelTimeoutMs: Long,
    private val m3uConnectTimeoutMs: Long,
    private val m3uReadTimeoutMs: Long,
    private val m3uCallTimeoutMs: Long,
) : ExternalAudioCatalogRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        credentialCrypto: CredentialCrypto,
        sonixControlRepository: SonixControlRepository,
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        credentialCrypto = credentialCrypto,
        sonixControlRepository = sonixControlRepository,
        dnsResolver = { host -> InetAddress.getAllByName(host) },
        panelTimeoutMs = DEFAULT_PANEL_TIMEOUT_MS,
        m3uConnectTimeoutMs = DEFAULT_M3U_CONNECT_TIMEOUT_MS,
        m3uReadTimeoutMs = DEFAULT_M3U_READ_TIMEOUT_MS,
        m3uCallTimeoutMs = DEFAULT_M3U_CALL_TIMEOUT_MS,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val cacheReadLock = Any()
    private val inFlightLock = Any()
    private val inMemoryCache = AtomicReference<CachedAudioCatalogState?>(null)

    @Volatile
    private var inFlightSync: InFlightSync? = null

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    override fun peekCached(): ExternalAudioCatalogResult? = inMemoryCache.get()?.result

    override suspend fun preload(): ExternalAudioCatalogResult = loadCached()

    override suspend fun loadCached(): ExternalAudioCatalogResult {
        inMemoryCache.get()?.let { return it.result }
        return withContext(Dispatchers.IO) {
            readCachedState()?.result ?: ExternalAudioCatalogResult(
                channels = emptyList(),
                fromCache = false,
                error = "لا توجد مصادر صوت محفوظة بعد",
            )
        }
    }

    override suspend fun syncFromPanel(forceRefresh: Boolean): ExternalAudioCatalogResult {
        while (true) {
            coroutineContext.ensureActive()
            var isOwner = false
            var ownerSync: InFlightSync? = null
            var joinSync: InFlightSync? = null

            synchronized(inFlightLock) {
                val active = inFlightSync
                if (active != null) {
                    joinSync = active
                } else {
                    val created = InFlightSync(forceRefresh, CompletableDeferred())
                    inFlightSync = created
                    ownerSync = created
                    isOwner = true
                }
            }

            if (!isOwner) {
                val active = joinSync!!
                val sharedResult = active.deferred.await()
                if (sharedResult != null && (!forceRefresh || active.forceRefresh)) {
                    return sharedResult
                }
                continue
            }

            val mine = ownerSync!!
            var resolvedResult: ExternalAudioCatalogResult? = null
            try {
                resolvedResult = syncMutex.withLock {
                    withContext(Dispatchers.IO) {
                        executeSyncInternal(forceRefresh)
                    }
                }
                return resolvedResult
            } finally {
                mine.deferred.complete(resolvedResult)
                synchronized(inFlightLock) {
                    if (inFlightSync === mine) {
                        inFlightSync = null
                    }
                }
            }
        }
    }

    private suspend fun executeSyncInternal(forceRefresh: Boolean): ExternalAudioCatalogResult {
        val cachedState = readCachedState()
        val cached = cachedState?.result
        val cachedEnvelope = cachedState?.envelope

        val panelResult = try {
            withTimeout(panelTimeoutMs) {
                sonixControlRepository.fetchAudioSources()
            }
        } catch (e: TimeoutCancellationException) {
            runCatching { Log.w(TAG, "Panel audio.sources check timed out") }
            return fallbackOrError(
                cached = cached,
                errorMsg = "انتهت مهلة الاتصال بلوحة التحكم؛ تحقق من الإنترنت",
                warningMsg = "تعذّر تحديث مصادر الصوت من اللوحة؛ تم استخدام القنوات المحفوظة",
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "Panel audio.sources check failed (${e::class.java.simpleName})") }
            return fallbackOrError(
                cached = cached,
                errorMsg = e.message ?: "تعذّر جلب مصادر الصوت من لوحة التحكم",
                warningMsg = "تعذّر تحديث مصادر الصوت من اللوحة؛ تم استخدام القنوات المحفوظة",
            )
        }

        if (panelResult.isFailure) {
            val panelErr = panelResult.exceptionOrNull()?.message ?: "تعذّر جلب مصادر الصوت من لوحة التحكم"
            return fallbackOrError(
                cached = cached,
                errorMsg = panelErr,
                warningMsg = "$panelErr؛ تم استخدام القنوات المحفوظة",
            )
        }

        val config = panelResult.getOrNull() ?: sonixControlRepository.state.value.audioSource
        val m3uUrl = config.m3uUrl?.trim().orEmpty()
        if (!config.configured || m3uUrl.isBlank()) {
            return if (cached != null && cached.channels.isNotEmpty() && !forceRefresh) {
                cached.copy(
                    fromCache = true,
                    warning = "لم يتم ضبط رابط قائمة الصوت الخارجي لهذا الجهاز في لوحة SONIX",
                )
            } else {
                ExternalAudioCatalogResult(
                    channels = emptyList(),
                    error = "لم يتم ضبط رابط قائمة الصوت الخارجي لهذا الجهاز في لوحة SONIX",
                )
            }
        }

        val updatedAt = config.updatedAt?.trim().orEmpty()
        val sourceHash = sha256(m3uUrl)
        if (!forceRefresh &&
            cached != null &&
            cachedEnvelope != null &&
            cached.channels.isNotEmpty() &&
            cachedEnvelope.sourceHash == sourceHash &&
            (updatedAt.isEmpty() || cachedEnvelope.updatedAt == updatedAt)
        ) {
            return cached.copy(fromCache = true)
        }

        return try {
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
            val freshResult = ExternalAudioCatalogResult(
                channels = channels,
                updatedAt = updatedAt.ifBlank { null },
                fromCache = false,
            )
            inMemoryCache.set(CachedAudioCatalogState(envelope, freshResult.copy(fromCache = true)))
            freshResult
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // Do not log M3U URL, query tokens, username, or password
            runCatching { Log.w(TAG, "External audio M3U sync failed (${e::class.java.simpleName})") }
            fallbackOrError(
                cached = cached,
                errorMsg = e.message ?: "تعذّر تحميل قائمة الصوت الخارجي",
                warningMsg = "تعذّر تحديث قائمة الصوت؛ تم استخدام النسخة المحفوظة",
            )
        }
    }

    private fun fallbackOrError(
        cached: ExternalAudioCatalogResult?,
        errorMsg: String,
        warningMsg: String,
    ): ExternalAudioCatalogResult {
        return if (cached != null && cached.channels.isNotEmpty()) {
            cached.copy(
                fromCache = true,
                warning = warningMsg,
                error = null,
            )
        } else {
            ExternalAudioCatalogResult(
                channels = emptyList(),
                error = errorMsg,
            )
        }
    }

    private fun downloadAndParseM3u(initialUrl: String): List<ExternalAudioChannel> {
        val downloadClient = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(m3uConnectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(m3uReadTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(m3uCallTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

        var currentUrl = initialUrl
        var redirectCount = 0
        var finalResponse: Response? = null

        try {
            while (true) {
                AudioNetworkSecurityValidator.assertSafeUrl(currentUrl, dnsResolver)
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
                    .build()

                val response = downloadClient.newCall(request).execute()
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
                val contentType = (res.header("Content-Type") ?: res.body?.contentType()?.toString())
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()
                if (isRejectedContentType(contentType)) {
                    throw IllegalStateException("أعاد المصدر استجابة غير صالحة ($contentType) بدلاً من قائمة M3U")
                }

                val body = res.body ?: throw IllegalStateException("استجابة قائمة M3U فارغة")
                val declaredLength = body.contentLength()
                if (declaredLength > AudioM3uParser.MAX_M3U_BYTES) {
                    throw IllegalStateException("حجم قائمة M3U تجاوز الحد الآمن (16 ميغابايت)")
                }

                val boundedRawStream = BoundedInputStream(body.byteStream(), AudioM3uParser.MAX_M3U_BYTES)
                val stream = if (res.header("Content-Encoding").equals("gzip", ignoreCase = true)) {
                    GZIPInputStream(boundedRawStream, 64 * 1024)
                } else {
                    boundedRawStream
                }
                return AudioM3uParser.parse(stream, currentUrl, dnsResolver)
            }
        } catch (e: Exception) {
            finalResponse?.close()
            throw e
        }
    }

    private fun isRejectedContentType(mime: String): Boolean {
        if (mime.isEmpty()) return false
        return mime == "text/html" ||
            mime == "application/xhtml+xml" ||
            mime == "application/json" ||
            mime == "text/json" ||
            mime == "application/xml" ||
            mime == "text/xml" ||
            mime == "application/pdf" ||
            mime == "application/zip" ||
            mime.startsWith("image/") ||
            mime.startsWith("video/")
    }

    private fun readCachedState(): CachedAudioCatalogState? {
        inMemoryCache.get()?.let { return it }
        synchronized(cacheReadLock) {
            inMemoryCache.get()?.let { return it }
            val envelope = readCachedEnvelopeFromDisk() ?: return null
            if (envelope.channels.isEmpty()) return null
            val sorted = AudioSourceClassifier.sortChannels(envelope.channels)
            val result = ExternalAudioCatalogResult(
                channels = sorted,
                updatedAt = envelope.updatedAt.ifBlank { null },
                fromCache = true,
            )
            val state = CachedAudioCatalogState(
                envelope = envelope.copy(channels = sorted),
                result = result,
            )
            inMemoryCache.set(state)
            return state
        }
    }

    private fun readCachedEnvelopeFromDisk(): CachedAudioCatalogEnvelope? {
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
        private const val DEFAULT_PANEL_TIMEOUT_MS = 12_000L
        private const val DEFAULT_M3U_CONNECT_TIMEOUT_MS = 10_000L
        private const val DEFAULT_M3U_READ_TIMEOUT_MS = 15_000L
        private const val DEFAULT_M3U_CALL_TIMEOUT_MS = 25_000L
    }
}

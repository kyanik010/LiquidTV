package com.liquidtv.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.dao.*
import com.liquidtv.app.data.local.entity.*
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.remote.XtreamAuthResponse
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.security.LogSanitizer
import com.liquidtv.app.domain.model.*
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LiquidTVDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val channelDao: ChannelDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val epgDao: EpgDao,
    private val xtreamApi: XtreamApiService,
    private val iptvApi: IptvApiService,
    private val okHttpClient: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xmltvParser: XmltvParser,
    private val crypto: CredentialCrypto,
    private val prefs: UserPreferencesRepository,
) : SubscriptionRepository {

    private val syncMutex = Mutex()

    private val _syncInfo = MutableStateFlow(PlaylistInfo())
    override val syncInfo: Flow<PlaylistInfo> = _syncInfo.asStateFlow()

    override val allSubscriptions: Flow<List<Subscription>> =
        subscriptionDao.getAllSubscriptions().map { list -> list.map { it.toDomain() } }

    override val activeSubscription: Flow<Subscription?> =
        subscriptionDao.getActiveSubscription().map { it?.toDomain() }

    override suspend fun getActiveSubscriptionOnce(): Subscription? =
        subscriptionDao.getActiveSubscriptionOnce()?.toDomain()

    override suspend fun getSubscriptionById(id: String): Subscription? =
        subscriptionDao.getSubscriptionById(id)?.toDomain()

    override suspend fun hasAnySubscription(): Boolean =
        subscriptionDao.getAllSubscriptionsOnce().isNotEmpty()

    override suspend fun addOrUpdateXtreamSubscription(
        existingId: String?,
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
        activate: Boolean,
    ): Result<Subscription> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val cleanUser = username.trim()
                val cleanPass = password.trim()
                require(cleanUser.isNotEmpty()) { "اسم المستخدم مطلوب" }
                require(cleanPass.isNotEmpty()) { "كلمة المرور مطلوبة" }

                val normalizedUrl = xtreamApi.normalizeServerUrl(serverUrl)
                val timeoutSec = prefs.userPreferences.first().connectionTimeout

                _syncInfo.value = PlaylistInfo(
                    subscriptionId = existingId,
                    name = displayName.ifBlank { normalizedUrl },
                    isLoading = true,
                    loadingProgress = 0.08f,
                    statusMessage = "جارٍ الاتصال بخادم Xtream...",
                )

                val authResponse = xtreamApi.authenticate(
                    serverUrl = normalizedUrl,
                    username = cleanUser,
                    password = cleanPass,
                    timeoutSeconds = timeoutSec,
                )

                val subId = existingId ?: UUID.randomUUID().toString()
                val existing = existingId?.let { subscriptionDao.getSubscriptionById(it) }
                val finalName = displayName.trim().ifBlank {
                    normalizedUrl.toHttpUrlOrNull()?.host ?: "Xtream Subscription"
                }
                val xmltvUrl = xtreamApi.buildXmltvUrl(normalizedUrl, cleanUser, cleanPass)
                val userInfo = authResponse.userInfo

                val entity = SubscriptionEntity(
                    id = subId,
                    name = finalName,
                    type = SubscriptionType.XTREAM.name,
                    serverUrl = normalizedUrl,
                    username = cleanUser,
                    encryptedPassword = crypto.encrypt(cleanPass),
                    encryptedM3uUrl = null,
                    xmltvUrl = xmltvUrl,
                    isActive = activate || existing?.isActive == true || !hasAnySubscription(),
                    connectionStatus = SubscriptionStatus.CONNECTED.name,
                    lastError = null,
                    accountStatus = userInfo?.status,
                    expDateEpochSec = userInfo?.expDateEpochSec,
                    maxConnections = userInfo?.maxConnections,
                    activeConnections = userInfo?.activeConnections,
                    isTrial = userInfo?.isTrial,
                    lastSyncedAt = existing?.lastSyncedAt,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )

                database.withTransaction {
                    subscriptionDao.upsertSubscription(entity)
                    if (entity.isActive) {
                        subscriptionDao.setActiveSubscription(subId)
                    }
                }

                syncXtreamInternal(
                    subscription = entity,
                    password = cleanPass,
                    authResponse = authResponse,
                    timeoutSec = timeoutSec,
                )

                val saved = subscriptionDao.getSubscriptionById(subId) ?: entity
                Result.success(saved.toDomain())
            } catch (e: Exception) {
                val safeMsg = sanitizeError(e)
                _syncInfo.update {
                    it.copy(isLoading = false, statusMessage = null, error = safeMsg)
                }
                Result.failure(Exception(safeMsg, e))
            }
        }
    }

    override suspend fun addOrUpdateM3uSubscription(
        existingId: String?,
        displayName: String,
        playlistUrl: String,
        userAgent: String?,
        referer: String?,
        activate: Boolean,
    ): Result<Subscription> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val cleanUrl = playlistUrl.trim()
                require(cleanUrl.isNotEmpty()) { "رابط قائمة التشغيل مطلوب" }
                require(
                    cleanUrl.startsWith("http://", ignoreCase = true) ||
                        cleanUrl.startsWith("https://", ignoreCase = true),
                ) {
                    "يجب أن يبدأ رابط القائمة بـ http:// أو https://"
                }
                val parsedUrl = cleanUrl.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("رابط قائمة M3U غير صالح")

                val subId = existingId ?: UUID.randomUUID().toString()
                val existing = existingId?.let { subscriptionDao.getSubscriptionById(it) }
                val finalName = displayName.trim().ifBlank { parsedUrl.host }
                val timeoutSec = prefs.userPreferences.first().connectionTimeout

                _syncInfo.value = PlaylistInfo(
                    subscriptionId = subId,
                    name = finalName,
                    isLoading = true,
                    loadingProgress = 0.08f,
                    statusMessage = "جارٍ تنزيل قائمة M3U...",
                )

                val parseResult = downloadAndParseM3u(
                    subscriptionId = subId,
                    playlistUrl = cleanUrl,
                    userAgent = userAgent?.trim()?.takeIf { it.isNotEmpty() },
                    referer = referer?.trim()?.takeIf { it.isNotEmpty() },
                    timeoutSec = timeoutSec,
                )

                val now = System.currentTimeMillis()
                val entity = SubscriptionEntity(
                    id = subId,
                    name = finalName,
                    type = SubscriptionType.M3U.name,
                    serverUrl = "${parsedUrl.scheme}://${parsedUrl.host}",
                    username = null,
                    encryptedPassword = null,
                    encryptedM3uUrl = crypto.encrypt(cleanUrl),
                    userAgent = userAgent?.trim()?.takeIf { it.isNotEmpty() },
                    referer = referer?.trim()?.takeIf { it.isNotEmpty() },
                    xmltvUrl = parseResult.xmltvUrl,
                    isActive = activate || existing?.isActive == true || !hasAnySubscription(),
                    connectionStatus = SubscriptionStatus.CONNECTED.name,
                    lastError = null,
                    lastSyncedAt = now,
                    createdAt = existing?.createdAt ?: now,
                )

                persistM3uDataAtomically(entity, parseResult)

                val saved = subscriptionDao.getSubscriptionById(subId) ?: entity
                Result.success(saved.toDomain())
            } catch (e: Exception) {
                val safeMsg = sanitizeError(e)
                _syncInfo.update {
                    it.copy(isLoading = false, statusMessage = null, error = safeMsg)
                }
                Result.failure(Exception(safeMsg, e))
            }
        }
    }

    override suspend fun activateSubscription(subscriptionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sub = subscriptionDao.getSubscriptionById(subscriptionId)
                ?: return@withContext Result.failure(IllegalArgumentException("Subscription not found"))
            subscriptionDao.setActiveSubscription(subscriptionId)
            val count = channelDao.getAllChannelsOnce(subscriptionId).size
            _syncInfo.value = PlaylistInfo(
                subscriptionId = subscriptionId,
                name = sub.name,
                totalChannels = count,
                lastRefreshed = sub.lastSyncedAt,
                isLoading = false,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncSubscription(subscriptionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val sub = subscriptionDao.getSubscriptionById(subscriptionId)
                ?: return@withContext Result.failure(IllegalArgumentException("Subscription not found"))
            val timeoutSec = prefs.userPreferences.first().connectionTimeout

            try {
                _syncInfo.value = PlaylistInfo(
                    subscriptionId = sub.id,
                    name = sub.name,
                    isLoading = true,
                    loadingProgress = 0.05f,
                    statusMessage = "جارٍ الاتصال بـ ${sub.name}...",
                )

                when (sub.type) {
                    SubscriptionType.XTREAM.name -> {
                        val user = sub.username ?: throw IllegalStateException("Missing Xtream username")
                        val pass = sub.encryptedPassword?.let { crypto.decrypt(it) }
                            ?: throw IllegalStateException("Missing Xtream password")
                        val auth = xtreamApi.authenticate(sub.serverUrl, user, pass, timeoutSec)
                        val updatedSub = sub.copy(
                            connectionStatus = SubscriptionStatus.CONNECTED.name,
                            lastError = null,
                            accountStatus = auth.userInfo?.status,
                            expDateEpochSec = auth.userInfo?.expDateEpochSec,
                            maxConnections = auth.userInfo?.maxConnections,
                            activeConnections = auth.userInfo?.activeConnections,
                            isTrial = auth.userInfo?.isTrial,
                        )
                        subscriptionDao.upsertSubscription(updatedSub)
                        syncXtreamInternal(updatedSub, pass, auth, timeoutSec)
                    }
                    SubscriptionType.M3U.name -> {
                        val url = sub.encryptedM3uUrl?.let { crypto.decrypt(it) }
                            ?: throw IllegalStateException("Missing M3U URL")
                        val parseResult = downloadAndParseM3u(
                            subscriptionId = sub.id,
                            playlistUrl = url,
                            userAgent = sub.userAgent,
                            referer = sub.referer,
                            timeoutSec = timeoutSec,
                        )
                        val updated = sub.copy(
                            xmltvUrl = parseResult.xmltvUrl ?: sub.xmltvUrl,
                            connectionStatus = SubscriptionStatus.CONNECTED.name,
                            lastError = null,
                            lastSyncedAt = System.currentTimeMillis(),
                        )
                        persistM3uDataAtomically(updated, parseResult)
                    }
                    else -> throw IllegalStateException("Unsupported subscription type: ${sub.type}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                val safeMsg = sanitizeError(e)
                val status = if (safeMsg.contains("expired", ignoreCase = true) ||
                    safeMsg.contains("منتهي", ignoreCase = true)
                ) {
                    SubscriptionStatus.EXPIRED.name
                } else {
                    SubscriptionStatus.ERROR.name
                }
                subscriptionDao.updateSyncStatus(sub.id, status, safeMsg, sub.lastSyncedAt)
                _syncInfo.update {
                    it.copy(isLoading = false, statusMessage = null, error = safeMsg)
                }
                Result.failure(Exception(safeMsg, e))
            }
        }
    }

    override suspend fun syncActiveSubscription(): Result<Unit> {
        val active = subscriptionDao.getActiveSubscriptionOnce()
            ?: return Result.failure(IllegalStateException("لا يوجد اشتراك IPTV مفعّل"))
        return syncSubscription(active.id)
    }

    override suspend fun syncActiveEpg(): Result<Unit> = withContext(Dispatchers.IO) {
        val sub = subscriptionDao.getActiveSubscriptionOnce()
            ?: return@withContext Result.failure(IllegalStateException("لا يوجد اشتراك مفعّل"))
        val xmltvUrl = sub.xmltvUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.success(Unit)

        try {
            val timeoutSec = prefs.userPreferences.first().connectionTimeout.coerceAtLeast(20).toLong()
            val channels = channelDao.getAllChannelsOnce(sub.id)
            val tvgIds = channels.mapNotNull { it.tvgId?.takeIf { id -> id.isNotBlank() } }.toSet()

            val client = okHttpClient.newBuilder()
                .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                .readTimeout((timeoutSec * 3).coerceAtLeast(45L), TimeUnit.SECONDS)
                .build()
            val reqBuilder = Request.Builder().url(xmltvUrl).get()
            sub.userAgent?.let { reqBuilder.header("User-Agent", it) }
            sub.referer?.let { reqBuilder.header("Referer", it) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("EPG returned HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(IOException("Empty EPG response"))
                val programs = body.byteStream().use { stream ->
                    xmltvParser.parse(
                        inputStream = stream,
                        subscriptionId = sub.id,
                        channelTvgIdsFilter = tvgIds.takeIf { it.isNotEmpty() },
                    )
                }
                epgDao.replaceEpgForSubscriptionAtomically(sub.id, programs)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeError(e), e))
        }
    }

    override suspend fun deleteSubscription(subscriptionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                val wasActive = subscriptionDao.getSubscriptionById(subscriptionId)?.isActive == true
                subscriptionDao.deleteSubscriptionById(subscriptionId)
                if (wasActive) {
                    val remaining = subscriptionDao.getAllSubscriptionsOnce().firstOrNull()
                    if (remaining != null) {
                        subscriptionDao.setActiveSubscription(remaining.id)
                    }
                }
            }
            val nextActive = subscriptionDao.getActiveSubscriptionOnce()
            _syncInfo.value = PlaylistInfo(
                subscriptionId = nextActive?.id,
                name = nextActive?.name.orEmpty(),
                lastRefreshed = nextActive?.lastSyncedAt,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDecryptedXtreamPassword(subscriptionId: String): String? =
        subscriptionDao.getSubscriptionById(subscriptionId)?.encryptedPassword?.let {
            runCatching { crypto.decrypt(it) }.getOrNull()
        }

    override suspend fun getDecryptedM3uUrl(subscriptionId: String): String? =
        subscriptionDao.getSubscriptionById(subscriptionId)?.encryptedM3uUrl?.let {
            runCatching { crypto.decrypt(it) }.getOrNull()
        }

    private suspend fun downloadAndParseM3u(
        subscriptionId: String,
        playlistUrl: String,
        userAgent: String?,
        referer: String?,
        timeoutSec: Int,
    ): M3UParser.ParseResult {
        val headers = LinkedHashMap<String, String>()
        if (!userAgent.isNullOrBlank()) headers["User-Agent"] = userAgent
        if (!referer.isNullOrBlank()) headers["Referer"] = referer

        val client = okHttpClient.newBuilder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout((timeoutSec * 3L).coerceAtLeast(45L), TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder().url(playlistUrl).get()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        val response = client.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Playlist server returned HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty playlist response")
            val contentLength = body.contentLength()
            _syncInfo.update {
                it.copy(loadingProgress = 0.25f, statusMessage = "جارٍ تحليل القنوات...")
            }
            val result = body.byteStream().use { stream ->
                m3uParser.parseStream(
                    inputStream = stream,
                    contentLengthBytes = contentLength,
                    subscriptionId = subscriptionId,
                    defaultUserAgent = userAgent,
                    defaultReferer = referer,
                ) { parseFrac ->
                    _syncInfo.update {
                        it.copy(loadingProgress = 0.25f + parseFrac * 0.60f)
                    }
                }
            }
            if (result.channels.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على قنوات صالحة للتشغيل في قائمة M3U هذه.")
            }
            return result
        }
    }

    private suspend fun persistM3uDataAtomically(
        subscription: SubscriptionEntity,
        parseResult: M3UParser.ParseResult,
    ) {
        _syncInfo.update {
            it.copy(loadingProgress = 0.88f, statusMessage = "جارٍ حفظ ${parseResult.channels.size} قناة...")
        }

        val preservedChannelState = channelDao.getPreservedUserState(subscription.id)
            .associateBy { it.id }
        val existingLockedCategories = categoryDao.getAllCategoriesOnce(subscription.id)
            .filter { it.isLocked }
            .map { it.name }
            .toSet()

        val channelEntities = parseResult.channels.map { ch ->
            val prev = preservedChannelState[ch.id]
            val headersJson = if (ch.httpHeaders.isNotEmpty()) {
                JSONObject(ch.httpHeaders).toString()
            } else null

            ChannelEntity(
                id = ch.id,
                subscriptionId = subscription.id,
                externalId = ch.externalId,
                mediaType = ch.mediaType.name,
                name = ch.name,
                streamUrl = ch.streamUrl,
                logoUrl = ch.logoUrl,
                groupTitle = ch.groupTitle,
                categoryId = ch.categoryId,
                tvgId = ch.tvgId,
                tvgName = ch.tvgName,
                country = ch.country,
                language = ch.language,
                channelNumber = ch.channelNumber,
                httpHeadersJson = headersJson,
                isFavorite = prev?.isFavorite ?: false,
                lastWatchedAt = prev?.lastWatchedAt,
                resumePositionMs = prev?.resumePositionMs ?: 0L,
                addedAt = ch.addedAt,
            )
        }

        val liveCategories = parseResult.channels
            .filter { it.mediaType == MediaType.LIVE }
            .mapNotNull { it.groupTitle?.takeIf { g -> g.isNotBlank() } }
            .distinct()
            .mapIndexed { idx, name ->
                CategoryEntity(
                    id = "${subscription.id}_LIVE_${name.lowercase()}",
                    subscriptionId = subscription.id,
                    externalCategoryId = name,
                    contentType = MediaType.LIVE.name,
                    name = name,
                    sortOrder = idx,
                    isLocked = name in existingLockedCategories,
                )
            }
        val movieCategories = parseResult.channels
            .filter { it.mediaType == MediaType.MOVIE }
            .mapNotNull { it.groupTitle?.takeIf { g -> g.isNotBlank() } }
            .distinct()
            .mapIndexed { idx, name ->
                CategoryEntity(
                    id = "${subscription.id}_MOVIE_${name.lowercase()}",
                    subscriptionId = subscription.id,
                    externalCategoryId = name,
                    contentType = MediaType.MOVIE.name,
                    name = name,
                    sortOrder = idx,
                    isLocked = name in existingLockedCategories,
                )
            }

        database.withTransaction {
            subscriptionDao.upsertSubscription(subscription)
            if (subscription.isActive) {
                subscriptionDao.setActiveSubscription(subscription.id)
            }
            seriesDao.deleteSeriesForSubscription(subscription.id)
            channelDao.replaceChannelsAtomically(subscription.id, null, channelEntities)
            categoryDao.replaceCategoriesAtomically(subscription.id, MediaType.LIVE.name, liveCategories)
            categoryDao.replaceCategoriesAtomically(subscription.id, MediaType.MOVIE.name, movieCategories)
            categoryDao.deleteCategoriesByType(subscription.id, MediaType.SERIES_EPISODE.name)
        }

        val liveCount = channelEntities.count { it.mediaType == MediaType.LIVE.name }
        val movieCount = channelEntities.count { it.mediaType == MediaType.MOVIE.name }
        _syncInfo.value = PlaylistInfo(
            subscriptionId = subscription.id,
            name = subscription.name,
            totalChannels = liveCount,
            totalMovies = movieCount,
            totalSeries = 0,
            lastRefreshed = subscription.lastSyncedAt,
            isLoading = false,
            loadingProgress = 1f,
        )
    }

    private suspend fun syncXtreamInternal(
        subscription: SubscriptionEntity,
        password: String,
        authResponse: XtreamAuthResponse,
        timeoutSec: Int,
    ) = withContext(Dispatchers.IO) {
        val username = subscription.username ?: ""
        val serverUrl = subscription.serverUrl
        val allowedFormats = authResponse.userInfo?.allowedOutputFormats.orEmpty()

        _syncInfo.update {
            it.copy(loadingProgress = 0.25f, statusMessage = "جارٍ تحميل القنوات والأفلام والمسلسلات...")
        }

        val liveCatsDeferred = async { xtreamApi.fetchLiveCategories(serverUrl, username, password, timeoutSec) }
        val liveStreamsDeferred = async { xtreamApi.fetchLiveStreams(serverUrl, username, password, timeoutSec) }
        val vodCatsDeferred = async { xtreamApi.fetchVodCategories(serverUrl, username, password, timeoutSec) }
        val vodStreamsDeferred = async { xtreamApi.fetchVodStreams(serverUrl, username, password, timeoutSec) }
        val seriesCatsDeferred = async { xtreamApi.fetchSeriesCategories(serverUrl, username, password, timeoutSec) }
        val seriesListDeferred = async { xtreamApi.fetchSeriesList(serverUrl, username, password, timeoutSec) }

        val liveCats = liveCatsDeferred.await()
        val liveStreams = liveStreamsDeferred.await()
        val vodCats = vodCatsDeferred.await()
        val vodStreams = vodStreamsDeferred.await()
        val seriesCats = seriesCatsDeferred.await()
        val seriesDtos = seriesListDeferred.await()

        _syncInfo.update {
            it.copy(loadingProgress = 0.72f, statusMessage = "جارٍ تنظيم المحتوى...")
        }

        val liveCatMap = liveCats.associate { it.categoryId to it.categoryName }
        val vodCatMap = vodCats.associate { it.categoryId to it.categoryName }
        val seriesCatMap = seriesCats.associate { it.categoryId to it.categoryName }

        val preservedChannels = channelDao.getPreservedUserState(subscription.id).associateBy { it.id }
        val preservedSeries = seriesDao.getPreservedSeriesState(subscription.id).associateBy { it.id }
        val existingLockedCategories = categoryDao.getAllCategoriesOnce(subscription.id)
            .filter { it.isLocked }
            .map { it.name }
            .toSet()

        val liveEntities = liveStreams.map { dto ->
            val id = "${subscription.id}_live_${dto.streamId}"
            val prev = preservedChannels[id]
            val streamUrl = xtreamApi.buildLiveStreamUrl(
                serverUrl = serverUrl,
                username = username,
                password = password,
                streamId = dto.streamId,
                allowedFormats = allowedFormats,
                directSource = dto.directSource,
            )
            ChannelEntity(
                id = id,
                subscriptionId = subscription.id,
                externalId = dto.streamId,
                mediaType = MediaType.LIVE.name,
                name = dto.name,
                streamUrl = streamUrl,
                logoUrl = dto.streamIcon,
                groupTitle = dto.categoryId?.let { liveCatMap[it] },
                categoryId = dto.categoryId,
                tvgId = dto.epgChannelId ?: dto.streamId,
                tvgName = dto.name,
                channelNumber = dto.num,
                isFavorite = prev?.isFavorite ?: false,
                lastWatchedAt = prev?.lastWatchedAt,
                resumePositionMs = prev?.resumePositionMs ?: 0L,
            )
        }

        val vodEntities = vodStreams.map { dto ->
            val id = "${subscription.id}_movie_${dto.streamId}"
            val prev = preservedChannels[id]
            val streamUrl = xtreamApi.buildVodStreamUrl(
                serverUrl = serverUrl,
                username = username,
                password = password,
                streamId = dto.streamId,
                containerExtension = dto.containerExtension,
                directSource = dto.directSource,
            )
            ChannelEntity(
                id = id,
                subscriptionId = subscription.id,
                externalId = dto.streamId,
                mediaType = MediaType.MOVIE.name,
                name = dto.name,
                streamUrl = streamUrl,
                logoUrl = dto.streamIcon,
                groupTitle = dto.categoryId?.let { vodCatMap[it] },
                categoryId = dto.categoryId,
                containerExtension = dto.containerExtension,
                rating = dto.rating,
                isFavorite = prev?.isFavorite ?: false,
                lastWatchedAt = prev?.lastWatchedAt,
                resumePositionMs = prev?.resumePositionMs ?: 0L,
            )
        }

        val seriesEntities = seriesDtos.map { dto ->
            val id = "${subscription.id}_series_${dto.seriesId}"
            val prev = preservedSeries[id]
            SeriesEntity(
                id = id,
                subscriptionId = subscription.id,
                externalSeriesId = dto.seriesId,
                name = dto.name,
                coverUrl = dto.cover,
                categoryId = dto.categoryId,
                categoryName = dto.categoryId?.let { seriesCatMap[it] },
                plot = dto.plot,
                cast = dto.cast,
                director = dto.director,
                genre = dto.genre,
                releaseDate = dto.releaseDate,
                rating = dto.rating,
                lastModified = dto.lastModified,
                isFavorite = prev?.isFavorite ?: false,
                lastWatchedAt = prev?.lastWatchedAt,
            )
        }

        val liveCatEntities = liveCats.mapIndexed { i, c ->
            CategoryEntity(
                id = "${subscription.id}_LIVE_${c.categoryId}",
                subscriptionId = subscription.id,
                externalCategoryId = c.categoryId,
                contentType = MediaType.LIVE.name,
                name = c.categoryName,
                sortOrder = i,
                isLocked = c.categoryName in existingLockedCategories,
            )
        }
        val vodCatEntities = vodCats.mapIndexed { i, c ->
            CategoryEntity(
                id = "${subscription.id}_MOVIE_${c.categoryId}",
                subscriptionId = subscription.id,
                externalCategoryId = c.categoryId,
                contentType = MediaType.MOVIE.name,
                name = c.categoryName,
                sortOrder = i,
                isLocked = c.categoryName in existingLockedCategories,
            )
        }
        val seriesCatEntities = seriesCats.mapIndexed { i, c ->
            CategoryEntity(
                id = "${subscription.id}_SERIES_${c.categoryId}",
                subscriptionId = subscription.id,
                externalCategoryId = c.categoryId,
                contentType = MediaType.SERIES_EPISODE.name,
                name = c.categoryName,
                sortOrder = i,
                isLocked = c.categoryName in existingLockedCategories,
            )
        }

        val now = System.currentTimeMillis()
        val updatedSub = subscription.copy(
            connectionStatus = SubscriptionStatus.CONNECTED.name,
            lastError = null,
            lastSyncedAt = now,
        )

        database.withTransaction {
            subscriptionDao.upsertSubscription(updatedSub)
            channelDao.replaceChannelsAtomically(subscription.id, null, liveEntities + vodEntities)
            seriesDao.replaceSeriesAtomically(subscription.id, seriesEntities)
            categoryDao.replaceCategoriesAtomically(subscription.id, MediaType.LIVE.name, liveCatEntities)
            categoryDao.replaceCategoriesAtomically(subscription.id, MediaType.MOVIE.name, vodCatEntities)
            categoryDao.replaceCategoriesAtomically(subscription.id, MediaType.SERIES_EPISODE.name, seriesCatEntities)
        }

        _syncInfo.value = PlaylistInfo(
            subscriptionId = subscription.id,
            name = subscription.name,
            totalChannels = liveEntities.size,
            totalMovies = vodEntities.size,
            totalSeries = seriesEntities.size,
            lastRefreshed = now,
            isLoading = false,
            loadingProgress = 1f,
        )
    }

    private fun sanitizeError(e: Exception): String {
        val raw = e.message ?: "فشل الاتصال بالخادم"
        return LogSanitizer.sanitizeUrl(raw)
    }

    private fun SubscriptionEntity.toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        type = runCatching { SubscriptionType.valueOf(type) }.getOrDefault(SubscriptionType.M3U),
        serverUrl = serverUrl,
        username = username,
        maskedCredentialHint = when (type) {
            SubscriptionType.XTREAM.name -> username?.let { "المستخدم: $it" }
            SubscriptionType.M3U.name -> serverUrl
            else -> null
        },
        userAgent = userAgent,
        referer = referer,
        xmltvUrl = xmltvUrl,
        isActive = isActive,
        connectionStatus = runCatching { SubscriptionStatus.valueOf(connectionStatus) }
            .getOrDefault(SubscriptionStatus.DISCONNECTED),
        lastError = lastError,
        accountStatus = accountStatus,
        expDateEpochSec = expDateEpochSec,
        maxConnections = maxConnections,
        activeConnections = activeConnections,
        isTrial = isTrial,
        lastSyncedAt = lastSyncedAt,
        createdAt = createdAt,
    )
}

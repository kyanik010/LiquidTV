package com.liquidtv.app.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val mediaType: MediaType = MediaType.LIVE,
    val epgSummary: ChannelEpgSummary = ChannelEpgSummary(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val isError: Boolean = false,
    val isRetrying: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 3,
    val errorMessage: String? = null,
    val showControls: Boolean = true,
    val showTrackSheet: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val allChannels: List<Channel> = emptyList(),
    val currentChannelIndex: Int = -1,
    val nextEpisodeId: String? = null,
    val previousEpisodeId: String? = null,
    val externalSubtitleError: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _boundPlayer = MutableStateFlow<Player?>(null)
    val boundPlayer: StateFlow<Player?> = _boundPlayer.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var currentPrefs: UserPreferences = UserPreferences()
    private var retryJob: Job? = null
    private var progressJob: Job? = null
    private var epgJob: Job? = null
    private var activeSeriesId: String? = null
    private var activeItemKey: String? = null
    private var lastSubmittedUrl: String? = null
    private var pendingStartRequest: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = mediaController ?: return
            val hasError = playbackState == Player.STATE_IDLE && player.playerError != null
            if (playbackState == Player.STATE_READY) {
                retryJob?.cancel()
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isRetrying = false,
                        isError = false,
                        retryAttempt = 0,
                        errorMessage = null,
                        durationMs = player.duration.coerceAtLeast(0L),
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isPlaying = player.isPlaying,
                        isError = hasError,
                    )
                }
            }

            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                persistResumePosition()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    audioTracks = PlayerMediaHelper.extractTracks(tracks, C.TRACK_TYPE_AUDIO),
                    subtitleTracks = PlayerMediaHelper.extractTracks(tracks, C.TRACK_TYPE_TEXT),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    init {
        connectMediaController()
        observeChannelsAndPreferences()
    }

    private fun connectMediaController() {
        viewModelScope.launch {
            try {
                val sessionToken = SessionToken(
                    context,
                    ComponentName(context, LiquidTVPlaybackService::class.java),
                )
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                controllerFuture = future
                val controller = future.await()
                mediaController = controller
                controller.addListener(playerListener)
                _boundPlayer.value = controller
                startProgressLoop()
                pendingStartRequest?.invoke()
                pendingStartRequest = null
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isError = true,
                        errorMessage = "Unable to start playback service (${e.localizedMessage ?: "error"}).",
                    )
                }
            }
        }
    }

    private fun observeChannelsAndPreferences() {
        viewModelScope.launch {
            channelRepository.getAllChannels().collect { channels ->
                val currentId = _state.value.channel?.id
                val index = channels.indexOfFirst { it.id == currentId }
                _state.update {
                    it.copy(
                        allChannels = channels,
                        currentChannelIndex = if (index >= 0) index else it.currentChannelIndex,
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                currentPrefs = prefs
                _state.update { it.copy(maxRetries = prefs.maxRetries) }
            }
        }
    }

    fun loadItem(itemId: String, mediaType: MediaType = MediaType.LIVE) {
        val key = "${mediaType.name}:$itemId"
        if (activeItemKey == key && !_state.value.isError && lastSubmittedUrl != null) {
            return
        }
        retryJob?.cancel()
        epgJob?.cancel()
        activeItemKey = key
        activeSeriesId = null

        viewModelScope.launch {
            when (mediaType) {
                MediaType.SERIES_EPISODE -> {
                    val episode = channelRepository.getEpisodeById(itemId)
                    if (episode == null) {
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isError = true,
                                errorMessage = "Episode could not be found.",
                            )
                        }
                        return@launch
                    }
                    activeSeriesId = episode.seriesId
                    val siblings = channelRepository.getEpisodesForSeries(episode.seriesId).firstOrNull().orEmpty()
                    val epIdx = siblings.indexOfFirst { it.id == episode.id }
                    val prevEp = if (epIdx > 0) siblings[epIdx - 1].id else null
                    val nextEp = if (epIdx in 0 until siblings.lastIndex) siblings[epIdx + 1].id else null

                    val syntheticChannel = Channel(
                        id = episode.id,
                        subscriptionId = episode.subscriptionId,
                        externalId = episode.externalEpisodeId,
                        mediaType = MediaType.SERIES_EPISODE,
                        name = "S${episode.seasonNumber}E${episode.episodeNumber} • ${episode.title}",
                        streamUrl = episode.streamUrl,
                        logoUrl = episode.coverUrl,
                        groupTitle = "Series",
                        containerExtension = episode.containerExtension,
                        plot = episode.plot,
                        durationSec = episode.durationSec,
                        resumePositionMs = episode.resumePositionMs,
                    )
                    _state.update {
                        it.copy(
                            channel = syntheticChannel,
                            mediaType = MediaType.SERIES_EPISODE,
                            epgSummary = ChannelEpgSummary(),
                            isError = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            isBuffering = true,
                            errorMessage = null,
                            showControls = true,
                            nextEpisodeId = nextEp,
                            previousEpisodeId = prevEp,
                        )
                    }
                    startPlayback(syntheticChannel, resumeMs = episode.resumePositionMs)
                    channelRepository.recordEpisodeWatch(episode.id, episode.resumePositionMs, (episode.durationSec ?: 0) * 1000L)
                }
                MediaType.LIVE, MediaType.MOVIE -> {
                    val channel = channelRepository.getChannelById(itemId)
                    if (channel == null) {
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isError = true,
                                errorMessage = "Stream item could not be found.",
                            )
                        }
                        return@launch
                    }
                    val allChannels = _state.value.allChannels
                    val index = allChannels.indexOfFirst { it.id == itemId }
                    _state.update {
                        it.copy(
                            channel = channel,
                            mediaType = channel.mediaType,
                            currentChannelIndex = index,
                            isError = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            isBuffering = true,
                            errorMessage = null,
                            showControls = true,
                            nextEpisodeId = null,
                            previousEpisodeId = null,
                        )
                    }
                    val startPos = if (channel.mediaType == MediaType.MOVIE) channel.resumePositionMs else 0L
                    startPlayback(channel, resumeMs = startPos)
                    channelRepository.recordWatch(channel.id, startPos, (channel.durationSec ?: 0) * 1000L)
                    if (channel.mediaType == MediaType.LIVE) {
                        observeEpgForChannel(channel)
                    } else {
                        _state.update { it.copy(epgSummary = ChannelEpgSummary()) }
                    }
                }
            }
        }
    }

    private fun observeEpgForChannel(channel: Channel) {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            launch {
                val quickSummary = channelRepository.fetchShortEpgIfNeeded(channel)
                if (quickSummary.current != null || quickSummary.next != null) {
                    _state.update { it.copy(epgSummary = quickSummary) }
                }
            }
            channelRepository.observeChannelEpg(channel).collect { summary ->
                if (summary.current != null || summary.next != null) {
                    _state.update { it.copy(epgSummary = summary) }
                }
            }
        }
    }

    private fun startPlayback(
        channel: Channel,
        resumeMs: Long = 0L,
        externalSubtitleUrl: String? = null,
        externalSubtitleLang: String = "und",
    ) {
        lastSubmittedUrl = channel.streamUrl
        val action = {
            val controller = mediaController
            if (controller != null) {
                val mediaItem = PlayerMediaHelper.buildMediaItem(
                    mediaId = channel.id,
                    title = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    explicitExtension = channel.containerExtension,
                    headers = channel.httpHeaders,
                    externalSubtitleUrl = externalSubtitleUrl,
                    externalSubtitleLang = externalSubtitleLang,
                )
                controller.stop()
                controller.clearMediaItems()
                if (resumeMs > 5_000L && channel.mediaType != MediaType.LIVE) {
                    controller.setMediaItem(mediaItem, resumeMs)
                } else {
                    controller.setMediaItem(mediaItem)
                }
                controller.prepare()
                controller.playWhenReady = true
            }
        }
        if (mediaController != null) {
            action()
        } else {
            pendingStartRequest = action
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val currentState = _state.value
        val activeChannel = currentState.channel ?: return

        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            mediaController?.let {
                it.seekToDefaultPosition()
                it.prepare()
            }
            return
        }

        val maxRetries = currentPrefs.maxRetries.coerceIn(0, 5)
        val permanent = PlayerMediaHelper.isPermanentError(error)
        val nextAttempt = currentState.retryAttempt + 1

        if (!permanent && nextAttempt <= maxRetries) {
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRetrying = true,
                        isBuffering = true,
                        isError = false,
                        retryAttempt = nextAttempt,
                        maxRetries = maxRetries,
                        errorMessage = "Reconnecting ($nextAttempt/$maxRetries)…",
                    )
                }
                delay(PlayerMediaHelper.backoffDelayMs(nextAttempt))
                if (isActive && _state.value.channel?.id == activeChannel.id) {
                    val resumeAt = if (activeChannel.mediaType != MediaType.LIVE) {
                        mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    } else 0L
                    startPlayback(activeChannel, resumeMs = resumeAt)
                }
            }
        } else {
            _state.update {
                it.copy(
                    isRetrying = false,
                    isBuffering = false,
                    isError = true,
                    errorMessage = PlayerMediaHelper.formatPlaybackError(error),
                )
            }
        }
    }

    private fun handlePlaybackEnded() {
        val currentState = _state.value
        if (!currentPrefs.autoPlay) return
        when (currentState.mediaType) {
            MediaType.SERIES_EPISODE -> {
                currentState.nextEpisodeId?.let { nextId ->
                    loadItem(nextId, MediaType.SERIES_EPISODE)
                }
            }
            MediaType.LIVE -> nextChannel()
            MediaType.MOVIE -> Unit
        }
    }

    fun play() { mediaController?.play() }
    fun pause() { mediaController?.pause() }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration.coerceAtLeast(0L)
        if (duration > 0L) {
            val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
            controller.seekTo(target)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun nextChannel() {
        val nextEp = _state.value.nextEpisodeId
        if (_state.value.mediaType == MediaType.SERIES_EPISODE && nextEp != null) {
            loadItem(nextEp, MediaType.SERIES_EPISODE)
            return
        }
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val next = (index + 1) % channels.size
        loadItem(channels[next].id, MediaType.LIVE)
    }

    fun previousChannel() {
        val prevEp = _state.value.previousEpisodeId
        if (_state.value.mediaType == MediaType.SERIES_EPISODE && prevEp != null) {
            loadItem(prevEp, MediaType.SERIES_EPISODE)
            return
        }
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val prev = if (index <= 0) channels.size - 1 else index - 1
        loadItem(channels[prev].id, MediaType.LIVE)
    }

    fun toggleFavorite() {
        val item = _state.value.channel ?: return
        if (item.mediaType == MediaType.SERIES_EPISODE) return
        viewModelScope.launch {
            channelRepository.toggleFavorite(item.id)
            val updated = channelRepository.getChannelById(item.id)
            _state.update { it.copy(channel = updated ?: it.channel) }
        }
    }

    fun retry() {
        val channel = _state.value.channel ?: return
        retryJob?.cancel()
        _state.update {
            it.copy(
                isError = false,
                isRetrying = false,
                retryAttempt = 0,
                isBuffering = true,
                errorMessage = null,
            )
        }
        val resumeAt = if (channel.mediaType != MediaType.LIVE) {
            mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        } else 0L
        startPlayback(channel, resumeMs = resumeAt)
    }

    fun selectAudioTrack(track: TrackInfo) {
        val controller = mediaController ?: return
        val groups = controller.currentTracks.groups
        if (track.groupIndex !in groups.indices) return
        val trackGroup = groups[track.groupIndex].mediaTrackGroup
        val updated = controller.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(trackGroup, track.trackIndex))
            .build()
        controller.trackSelectionParameters = updated
    }

    fun selectSubtitleTrack(track: TrackInfo?) {
        val controller = mediaController ?: return
        val builder = controller.trackSelectionParameters.buildUpon()
        if (track == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val groups = controller.currentTracks.groups
            if (track.groupIndex !in groups.indices) return
            val trackGroup = groups[track.groupIndex].mediaTrackGroup
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(TrackSelectionOverride(trackGroup, track.trackIndex))
        }
        controller.trackSelectionParameters = builder.build()
    }

    fun loadExternalSubtitle(url: String, language: String = "und") {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            _state.update { it.copy(externalSubtitleError = "Subtitle URL must start with http:// or https://") }
            return
        }
        val channel = _state.value.channel ?: return
        val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        _state.update { it.copy(externalSubtitleError = null) }
        mediaController?.let { controller ->
            val updated = controller.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            controller.trackSelectionParameters = updated
        }
        startPlayback(
            channel = channel,
            resumeMs = pos,
            externalSubtitleUrl = trimmed,
            externalSubtitleLang = language.ifBlank { "und" },
        )
    }

    fun openTrackSheet() = _state.update { it.copy(showTrackSheet = true, externalSubtitleError = null) }
    fun closeTrackSheet() = _state.update { it.copy(showTrackSheet = false, externalSubtitleError = null) }

    fun showControls() = _state.update { it.copy(showControls = true) }
    fun hideControls() = _state.update { it.copy(showControls = false) }

    fun stopAndExit() {
        retryJob?.cancel()
        progressJob?.cancel()
        epgJob?.cancel()
        persistResumePosition()
        mediaController?.let { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val controller = mediaController
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _state.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                    if (tick % 5 == 0 && controller.isPlaying) {
                        persistResumePosition()
                    }
                }
                tick++
                delay(1_000L)
            }
        }
    }

    private fun persistResumePosition() {
        val currentChannel = _state.value.channel ?: return
        if (currentChannel.mediaType == MediaType.LIVE) return
        val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: return
        if (pos > 0L) {
            viewModelScope.launch {
                channelRepository.updateResumePosition(currentChannel.id, currentChannel.mediaType, pos)
            }
        }
    }

    override fun onCleared() {
        retryJob?.cancel()
        progressJob?.cancel()
        epgJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController = null
        _boundPlayer.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }
}

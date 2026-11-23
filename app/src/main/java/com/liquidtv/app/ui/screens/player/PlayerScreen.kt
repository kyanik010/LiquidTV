package com.liquidtv.app.ui.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.player.PlayerViewModel
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: String,
    mediaType: MediaType = MediaType.LIVE,
    onBack: () -> Unit,
    onAmbientMode: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val boundPlayer by viewModel.boundPlayer.collectAsState()

    val screenFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    var focusedActionText by remember { mutableStateOf<String?>("Play / Pause") }
    var interactionCounter by remember { mutableIntStateOf(0) }

    val handleExit = {
        viewModel.stopAndExit()
        onBack()
    }

    BackHandler {
        when {
            uiState.showTrackSheet -> viewModel.closeTrackSheet()
            uiState.showControls -> viewModel.hideControls()
            else -> handleExit()
        }
    }

    LaunchedEffect(channelId, mediaType) {
        viewModel.loadItem(channelId, mediaType)
    }

    LaunchedEffect(Unit) {
        try {
            screenFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(uiState.showControls, uiState.showTrackSheet, interactionCounter) {
        if (uiState.showControls && !uiState.showTrackSheet) {
            delay(6000)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(uiState.showControls) {
        if (uiState.showControls && !uiState.showTrackSheet) {
            delay(150)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    interactionCounter++
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls && !uiState.showTrackSheet) {
                                viewModel.showControls()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls && uiState.mediaType != MediaType.LIVE) {
                                viewModel.seekBy(15_000L)
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!uiState.showControls && uiState.mediaType != MediaType.LIVE) {
                                viewModel.seekBy(-15_000L)
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            viewModel.pause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.nextChannel()
                            viewModel.showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.previousChannel()
                            viewModel.showControls()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        // Unified Media3 PlayerView bound cleanly to MediaController
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        DisposableEffect(playerViewRef, boundPlayer) {
            val view = playerViewRef
            if (view != null) {
                view.player = boundPlayer
            }
            onDispose {
                if (view != null) {
                    view.player = null
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    interactionCounter++
                    if (uiState.showControls) viewModel.hideControls() else viewModel.showControls()
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    player = boundPlayer
                    playerViewRef = this
                }
            },
            update = { view ->
                if (view.player !== boundPlayer) {
                    view.player = boundPlayer
                }
            },
        )

        // Buffering / Auto-Reconnecting Indicator
        if ((uiState.isBuffering || uiState.isRetrying) && !uiState.isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    glassAlpha = 0.28f,
                    borderAlpha = 0.5f,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = LiquidColors.AccentBlue,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = if (uiState.isRetrying) {
                                uiState.errorMessage ?: "Reconnecting stream…"
                            } else {
                                "Buffering Stream…"
                            },
                            style = LiquidTypography.TitleMedium,
                            color = LiquidColors.TextPrimary,
                        )
                    }
                }
            }
        }

        // Error Glass Overlay
        if (uiState.isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier.widthIn(min = 320.dp, max = 520.dp).padding(24.dp),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.35f,
                    glassColor = Color(0xFF1E0A0A),
                    borderAlpha = 0.6f,
                    borderColor = LiquidColors.ErrorRed,
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = LiquidColors.WarningAmber,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Playback Issue",
                            style = LiquidTypography.TitleLarge,
                            color = LiquidColors.TextPrimary,
                        )
                        Text(
                            text = uiState.errorMessage ?: "Stream URL is offline or unreachable.",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HighVisButton(
                                icon = Icons.Filled.Refresh,
                                text = "Retry",
                                onClick = { viewModel.retry() },
                            )
                            HighVisButton(
                                icon = Icons.Filled.SkipNext,
                                text = "Next",
                                onClick = { viewModel.nextChannel() },
                            )
                            HighVisButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                text = "Exit",
                                onClick = handleExit,
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(animationSpec = spring(stiffness = 500f)),
            exit = fadeOut(animationSpec = spring(stiffness = 500f)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Info Bar + EPG Program Info
                uiState.channel?.let { channel ->
                    LiquidGlassSurface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 36.dp, top = 36.dp, end = 36.dp)
                            .widthIn(max = 660.dp),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.35f,
                        borderAlpha = 0.45f,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (channel.logoUrl != null) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = channel.name,
                                            style = LiquidTypography.TitleMedium,
                                            color = LiquidColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (uiState.mediaType == MediaType.LIVE) {
                                            LiveBadge()
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        channel.groupTitle?.let {
                                            Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue)
                                        }
                                        channel.country?.let {
                                            Text("• $it", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                                        }
                                        channel.language?.let {
                                            Text("• $it", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                                        }
                                    }
                                }
                            }

                            // EPG Current & Next
                            val nowProgram = uiState.epgSummary.current
                            val nextProgram = uiState.epgSummary.next
                            if (nowProgram != null || nextProgram != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    nowProgram?.let { curr ->
                                        Text(
                                            text = "Now: ${curr.title}",
                                            style = LiquidTypography.BodySmall,
                                            color = LiquidColors.AccentBlueLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        LinearProgressIndicator(
                                            progress = { curr.progressFraction() },
                                            color = LiquidColors.AccentBlueLight,
                                            trackColor = Color.White.copy(alpha = 0.16f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                        )
                                    }
                                    nextProgram?.let { nxt ->
                                        Text(
                                            text = "Next: ${nxt.title}",
                                            style = LiquidTypography.LabelSmall,
                                            color = LiquidColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Dock
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // VOD / Episode seek bar
                    if (uiState.mediaType != MediaType.LIVE && uiState.durationMs > 0L) {
                        Row(
                            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = formatDuration(uiState.currentPositionMs),
                                style = LiquidTypography.LabelSmall,
                                color = Color.White,
                            )
                            Slider(
                                value = uiState.currentPositionMs.toFloat(),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = LiquidColors.AccentBlueLight,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDuration(uiState.durationMs),
                                style = LiquidTypography.LabelSmall,
                                color = Color.White,
                            )
                        }
                    }

                    focusedActionText?.let { actionText ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xDD000000),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .border(1.dp, LiquidColors.AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = actionText,
                                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = Color.White,
                            )
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier.wrapContentWidth(),
                        cornerRadius = 40.dp,
                        glassAlpha = 0.38f,
                        borderAlpha = 0.50f,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlayerControlButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                description = "Back to Menu",
                                onFocused = { focusedActionText = "Back" },
                                onClick = handleExit,
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipPrevious,
                                description = "Previous",
                                onFocused = { focusedActionText = "Previous" },
                                onClick = {
                                    viewModel.previousChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                description = if (uiState.isPlaying) "Pause" else "Play",
                                isPrimary = true,
                                focusRequester = playPauseFocusRequester,
                                onFocused = {
                                    focusedActionText = if (uiState.isPlaying) "Pause" else "Play"
                                },
                                onClick = {
                                    if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipNext,
                                description = "Next",
                                onFocused = { focusedActionText = "Next" },
                                onClick = {
                                    viewModel.nextChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.Subtitles,
                                description = "Audio & Subtitles",
                                onFocused = { focusedActionText = "Audio & Subtitles" },
                                onClick = {
                                    viewModel.openTrackSheet()
                                    interactionCounter++
                                },
                                modifier = Modifier.testTag("player_tracks_button"),
                            )

                            if (uiState.mediaType != MediaType.SERIES_EPISODE) {
                                PlayerControlButton(
                                    icon = if (uiState.channel?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    description = if (uiState.channel?.isFavorite == true) "Remove from Favorites" else "Add to Favorites",
                                    iconTint = if (uiState.channel?.isFavorite == true) LiquidColors.LiveRed else LiquidColors.TextPrimary,
                                    onFocused = {
                                        focusedActionText = if (uiState.channel?.isFavorite == true) "Unfavorite" else "Favorite"
                                    },
                                    onClick = {
                                        viewModel.toggleFavorite()
                                        interactionCounter++
                                    },
                                )
                            }

                            PlayerControlButton(
                                icon = Icons.Filled.NightlightRound,
                                description = "Ambient Mode",
                                onFocused = { focusedActionText = "Ambient Mode" },
                                onClick = onAmbientMode,
                            )
                        }
                    }
                }
            }
        }

        // Audio & Subtitle Track Selection Dialog
        if (uiState.showTrackSheet) {
            TrackSelectionOverlay(
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                externalSubtitleError = uiState.externalSubtitleError,
                onSelectAudio = { viewModel.selectAudioTrack(it) },
                onSelectSubtitle = { viewModel.selectSubtitleTrack(it) },
                onLoadExternalSubtitle = { url, lang -> viewModel.loadExternalSubtitle(url, lang) },
                onClose = { viewModel.closeTrackSheet() },
            )
        }
    }
}

@Composable
private fun TrackSelectionOverlay(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    externalSubtitleError: String?,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onLoadExternalSubtitle: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var externalSubUrl by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 340.dp, max = 560.dp)
                .heightIn(max = 520.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            cornerRadius = 24.dp,
            glassAlpha = 0.30f,
            borderAlpha = 0.55f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Audio & Subtitles", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    item {
                        Text("Audio Tracks", style = LiquidTypography.TitleSmall, color = LiquidColors.AccentBlueLight)
                    }
                    if (audioTracks.isEmpty()) {
                        item {
                            Text("No alternate audio tracks detected in this stream.", style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
                        }
                    } else {
                        items(audioTracks, key = { "a_${it.groupIndex}_${it.trackIndex}" }) { track ->
                            TrackOptionRow(
                                label = track.label,
                                selected = track.isSelected,
                                onClick = { onSelectAudio(track) },
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Subtitles / Captions", style = LiquidTypography.TitleSmall, color = LiquidColors.AccentVioletLight)
                    }
                    item {
                        TrackOptionRow(
                            label = "Off (Disable Subtitles)",
                            selected = subtitleTracks.none { it.isSelected },
                            onClick = { onSelectSubtitle(null) },
                        )
                    }
                    if (subtitleTracks.isEmpty()) {
                        item {
                            Text("No embedded subtitle tracks in this stream.", style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
                        }
                    } else {
                        items(subtitleTracks, key = { "s_${it.groupIndex}_${it.trackIndex}" }) { track ->
                            TrackOptionRow(
                                label = track.label,
                                selected = track.isSelected,
                                onClick = { onSelectSubtitle(track) },
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("External Subtitle URL (.srt / .vtt)", style = LiquidTypography.TitleSmall, color = LiquidColors.AccentCyan)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = externalSubUrl,
                                onValueChange = { externalSubUrl = it },
                                singleLine = true,
                                placeholder = { Text("http://.../subs.srt", color = LiquidColors.TextDisabled) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = LiquidColors.AccentBlueLight,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { onLoadExternalSubtitle(externalSubUrl, "und") },
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Load")
                            }
                        }
                        externalSubtitleError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = LiquidTypography.LabelSmall, color = LiquidColors.ErrorRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> LiquidColors.AccentBlue.copy(alpha = 0.25f)
                    isFocused -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (selected) LiquidColors.AccentBlueLight else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LiquidTypography.BodyMedium, color = Color.White, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = LiquidColors.AccentBlueLight)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    description: String,
    isPrimary: Boolean = false,
    iconTint: Color = LiquidColors.TextPrimary,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "btnScale",
    )

    val buttonSize = if (isPrimary) 56.dp else 48.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .then(
                if (isFocused) {
                    Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(LiquidColors.AccentBlue, LiquidColors.AccentBlueLight)
                            )
                        )
                        .border(BorderStroke(3.5.dp, Color.White), CircleShape)
                } else if (isPrimary) {
                    Modifier
                        .background(LiquidColors.AccentBlue.copy(alpha = 0.85f))
                        .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused || isPrimary) Color.White else iconTint,
            modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp),
        )
    }
}

@Composable
private fun HighVisButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(LiquidColors.AccentBlue)
                        .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

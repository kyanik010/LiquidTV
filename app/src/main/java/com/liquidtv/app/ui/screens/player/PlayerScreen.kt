package com.liquidtv.app.ui.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.delay
import java.util.Locale

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
    val colors = AppTheme.colors

    val screenFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    var focusedActionText by remember { mutableStateOf<String?>("تشغيل / إيقاف مؤقت") }
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
                    glassAlpha = 0.88f,
                    borderAlpha = 0.65f,
                    borderColor = colors.border,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = if (uiState.isRetrying) {
                                uiState.errorMessage ?: "جارٍ إعادة الاتصال بالبث…"
                            } else {
                                "جارٍ تحميل البث…"
                            },
                            style = LiquidTypography.TitleMedium,
                            color = colors.text,
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
                    modifier = Modifier.widthIn(min = 300.dp, max = 520.dp).padding(20.dp),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.94f,
                    glassColor = colors.panel,
                    borderAlpha = 0.85f,
                    borderColor = colors.danger,
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "مشكلة في التشغيل",
                            style = LiquidTypography.TitleLarge,
                            color = colors.text,
                        )
                        Text(
                            text = uiState.errorMessage ?: "رابط البث غير متاح أو تعذر الوصول إليه.",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            HighVisButton(
                                icon = Icons.Filled.Refresh,
                                text = "إعادة المحاولة",
                                onClick = { viewModel.retry() },
                            )
                            HighVisButton(
                                icon = Icons.Filled.SkipNext,
                                text = "التالي",
                                onClick = { viewModel.nextChannel() },
                            )
                            HighVisButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                text = "خروج",
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                // Top Info Bar + EPG Program Info
                uiState.channel?.let { channel ->
                    LiquidGlassSurface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .widthIn(max = 660.dp),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.88f,
                        borderAlpha = 0.65f,
                        borderColor = colors.border,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (!channel.logoUrl.isNullOrBlank()) {
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
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (uiState.mediaType == MediaType.LIVE) {
                                            LiveBadge()
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        channel.groupTitle?.let {
                                            Text(it, style = LiquidTypography.LabelSmall, color = colors.accentText)
                                        }
                                        channel.country?.let {
                                            Text("• $it", style = LiquidTypography.LabelSmall, color = colors.muted)
                                        }
                                        channel.language?.let {
                                            Text("• $it", style = LiquidTypography.LabelSmall, color = colors.muted)
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
                                            text = "الآن: ${curr.title}",
                                            style = LiquidTypography.BodySmall,
                                            color = colors.accentText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        LinearProgressIndicator(
                                            progress = { curr.progressFraction() },
                                            color = colors.accent,
                                            trackColor = colors.border.copy(alpha = 0.40f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                        )
                                    }
                                    nextProgram?.let { nxt ->
                                        Text(
                                            text = "التالي: ${nxt.title}",
                                            style = LiquidTypography.LabelSmall,
                                            color = colors.textSecondary,
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
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // VOD / Episode seek bar
                    if (uiState.mediaType != MediaType.LIVE && uiState.durationMs > 0L) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 620.dp)
                                .fillMaxWidth()
                                .background(colors.panel.copy(alpha = 0.84f), RoundedCornerShape(16.dp))
                                .border(1.dp, colors.border.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = formatDuration(uiState.currentPositionMs),
                                style = LiquidTypography.LabelSmall,
                                color = colors.text,
                            )
                            Slider(
                                value = uiState.currentPositionMs.toFloat(),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                    inactiveTrackColor = colors.border.copy(alpha = 0.45f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDuration(uiState.durationMs),
                                style = LiquidTypography.LabelSmall,
                                color = colors.text,
                            )
                        }
                    }

                    focusedActionText?.let { actionText ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = colors.panel.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .border(1.dp, colors.focus.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = actionText,
                                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = colors.text,
                            )
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier.wrapContentWidth(),
                        cornerRadius = 40.dp,
                        glassAlpha = 0.88f,
                        borderAlpha = 0.65f,
                        borderColor = colors.border,
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlayerControlButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                description = "رجوع إلى القائمة",
                                onFocused = { focusedActionText = "رجوع" },
                                onClick = handleExit,
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipPrevious,
                                description = "السابق",
                                onFocused = { focusedActionText = "السابق" },
                                onClick = {
                                    viewModel.previousChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                description = if (uiState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                                isPrimary = true,
                                focusRequester = playPauseFocusRequester,
                                onFocused = {
                                    focusedActionText = if (uiState.isPlaying) "إيقاف مؤقت" else "تشغيل"
                                },
                                onClick = {
                                    if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipNext,
                                description = "التالي",
                                onFocused = { focusedActionText = "التالي" },
                                onClick = {
                                    viewModel.nextChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.Subtitles,
                                description = "الصوت والترجمة",
                                onFocused = { focusedActionText = "الصوت والترجمة" },
                                onClick = {
                                    viewModel.openTrackSheet()
                                    interactionCounter++
                                },
                                modifier = Modifier.testTag("player_tracks_button"),
                            )

                            if (uiState.mediaType != MediaType.SERIES_EPISODE) {
                                PlayerControlButton(
                                    icon = if (uiState.channel?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    description = if (uiState.channel?.isFavorite == true) "إزالة من المفضلة" else "إضافة للمفضلة",
                                    iconTint = if (uiState.channel?.isFavorite == true) colors.danger else colors.text,
                                    onFocused = {
                                        focusedActionText = if (uiState.channel?.isFavorite == true) "إزالة من المفضلة" else "إضافة للمفضلة"
                                    },
                                    onClick = {
                                        viewModel.toggleFavorite()
                                        interactionCounter++
                                    },
                                )
                            }

                            PlayerControlButton(
                                icon = Icons.Filled.NightlightRound,
                                description = "الوضع المحيطي",
                                onFocused = { focusedActionText = "الوضع المحيطي" },
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
    val colors = AppTheme.colors
    var externalSubUrl by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.dialogScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(min = 300.dp, max = 560.dp)
                .heightIn(max = 520.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            cornerRadius = 24.dp,
            glassAlpha = 0.94f,
            glassColor = colors.panel,
            borderAlpha = 0.75f,
            borderColor = colors.border,
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
                    Text("الصوت والترجمة", style = LiquidTypography.TitleLarge, color = colors.text)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = colors.text)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    item {
                        Text("مسارات الصوت", style = LiquidTypography.TitleSmall, color = colors.accentText)
                    }
                    if (audioTracks.isEmpty()) {
                        item {
                            Text("لا توجد مسارات صوتية إضافية في هذا البث.", style = LiquidTypography.BodySmall, color = colors.muted)
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
                        Text("الترجمة والشرح", style = LiquidTypography.TitleSmall, color = colors.accentText)
                    }
                    item {
                        TrackOptionRow(
                            label = "إيقاف (بدون ترجمة)",
                            selected = subtitleTracks.none { it.isSelected },
                            onClick = { onSelectSubtitle(null) },
                        )
                    }
                    if (subtitleTracks.isEmpty()) {
                        item {
                            Text("لا توجد مسارات ترجمة مدمجة في هذا البث.", style = LiquidTypography.BodySmall, color = colors.muted)
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
                        Text("رابط ترجمة خارجية (.srt / .vtt)", style = LiquidTypography.TitleSmall, color = colors.accentText)
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
                                placeholder = { Text("http://.../subs.srt", color = colors.muted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.text,
                                    unfocusedTextColor = colors.text,
                                    cursorColor = colors.accent,
                                    focusedBorderColor = colors.focus,
                                    unfocusedBorderColor = colors.border,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { onLoadExternalSubtitle(externalSubUrl, "und") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
                                ),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("تحميل")
                            }
                        }
                        externalSubtitleError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = LiquidTypography.LabelSmall, color = colors.danger)
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
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> colors.selected
                    isFocused -> colors.surface
                    else -> colors.card.copy(alpha = 0.65f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> colors.focus
                    selected -> colors.accent
                    else -> colors.border.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LiquidTypography.BodyMedium, color = colors.text, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "محدد", tint = colors.accentText)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    description: String,
    isPrimary: Boolean = false,
    iconTint: Color = Color.Unspecified,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val resolvedTint = if (iconTint != Color.Unspecified) iconTint else colors.text
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
                                colors = listOf(colors.accent, colors.accentSecondary)
                            )
                        )
                        .border(BorderStroke(3.5.dp, colors.focus), CircleShape)
                } else if (isPrimary) {
                    Modifier
                        .background(colors.accent.copy(alpha = 0.90f))
                        .border(BorderStroke(1.5.dp, colors.focus.copy(alpha = 0.55f)), CircleShape)
                } else {
                    Modifier
                        .background(colors.surface.copy(alpha = 0.88f))
                        .border(BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused || isPrimary) colors.onAccent else resolvedTint,
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
    val colors = AppTheme.colors
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
                        .background(colors.accent)
                        .border(BorderStroke(3.dp, colors.focus), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .background(colors.surface)
                        .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(20.dp))
                }
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fg = if (isFocused) colors.onAccent else colors.text
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                color = fg,
            )
        }
    }
}

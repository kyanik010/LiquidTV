package com.liquidtv.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidtv.app.data.audio.AudioSourceClassifier
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExternalAudioSheet(
    state: ExternalAudioUiState,
    onSelectChannel: (ExternalAudioChannel) -> Unit,
    onDetachAudio: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectBrandFilter: (AudioBrandFilter) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleLoudnessBoost: () -> Unit,
    onAdjustSyncOffset: (Long) -> Unit,
    onResetSyncOffset: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val initialFilterFocus = remember { FocusRequester() }
    val filteredChannels = remember(state.channels, state.selectedBrandFilter, state.searchQuery) {
        AudioSourceClassifier.filterChannels(
            channels = state.channels,
            brandFilter = state.selectedBrandFilter,
            query = state.searchQuery,
        )
    }
    val beinCount = remember(state.channels) {
        state.channels.count { it.brand == AudioChannelBrand.BEIN_SPORTS }
    }
    val alwanCount = remember(state.channels) {
        state.channels.count { it.brand == AudioChannelBrand.ALWAN }
    }

    LaunchedEffect(Unit) {
        try {
            initialFilterFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    PlayerOverlaySheetCard(
        testTag = "external_audio_sheet",
        onDismiss = onClose,
        maxCardWidth = 760.dp,
        maxCardHeight = 620.dp,
    ) {
        // Ultra-compact Header Row (title + playback state + sync button + close)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (state.status == ExternalAudioStatus.ACTIVE) colors.success else colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "الصوت الخارجي",
                            style = LiquidTypography.TitleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                            ),
                            color = colors.text,
                            maxLines = 1,
                        )
                        if (state.channels.isNotEmpty()) {
                            Text(
                                text = "(${state.channels.size})",
                                style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                                color = colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                    val subtitleText = when (state.status) {
                        ExternalAudioStatus.ACTIVE -> "✓ ${state.statusMessage ?: state.activeChannel?.name.orEmpty()}"
                        ExternalAudioStatus.CONNECTING,
                        ExternalAudioStatus.BUFFERING,
                        ExternalAudioStatus.RECOVERING -> state.statusMessage ?: "جارٍ التحضير..."
                        ExternalAudioStatus.ERROR -> state.errorMessage ?: "تعذّر تشغيل الصوت الخارجي"
                        ExternalAudioStatus.LOADING_CATALOG -> "جارٍ مزامنة مصادر الصوت..."
                        ExternalAudioStatus.IDLE -> "اختر قناة صوتية مستقلة فوق الفيديو"
                    }
                    val subtitleColor = when (state.status) {
                        ExternalAudioStatus.ACTIVE -> colors.success
                        ExternalAudioStatus.ERROR -> colors.danger
                        ExternalAudioStatus.CONNECTING,
                        ExternalAudioStatus.BUFFERING,
                        ExternalAudioStatus.RECOVERING -> colors.accent
                        else -> colors.textSecondary
                    }
                    Text(
                        text = subtitleText,
                        style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SyncCatalogPillButton(
                    isSyncing = state.isLoadingCatalog,
                    onClick = onRefreshCatalog,
                    modifier = Modifier.testTag("external_audio_sync_catalog_button"),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("external_audio_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "إغلاق",
                        tint = colors.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Responsive Split: 2 columns on Wide/Landscape (guarantees full-height channel list!), stacked compact on Portrait
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isWideSplit = maxWidth >= 520.dp
            if (isWideSplit) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Start Dock: Controls, Search, Filters, Sync Status (sleek fixed 266dp width)
                    Column(
                        modifier = Modifier
                            .width(266.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ExternalAudioSyncStatusBar(
                            state = state,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AudioSheetAlerts(state = state)
                        CompactAudioSearchInput(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BrandFilterRow(
                            selectedFilter = state.selectedBrandFilter,
                            allCount = state.channels.size,
                            beinCount = beinCount,
                            alwanCount = alwanCount,
                            initialFocusRequester = initialFilterFocus,
                            onSelectBrandFilter = onSelectBrandFilter,
                        )
                        AudioMixerPanel(
                            state = state,
                            onVolumeChange = onVolumeChange,
                            onToggleLoudnessBoost = onToggleLoudnessBoost,
                            onAdjustSyncOffset = onAdjustSyncOffset,
                            onResetSyncOffset = onResetSyncOffset,
                            onDetachAudio = onDetachAudio,
                        )
                    }

                    // End Pane: Full-Height Channel List (6+ visible channels on Landscape Phone, 10+ on TV)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface.copy(alpha = 0.35f))
                            .border(1.dp, colors.border.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(6.dp),
                    ) {
                        ExternalAudioChannelListContent(
                            state = state,
                            filteredChannels = filteredChannels,
                            onSelectChannel = onSelectChannel,
                            onRefreshCatalog = onRefreshCatalog,
                        )
                    }
                }
            } else {
                // Portrait / Narrow Layout: Compressed Controls + Tall Channel List (6+ channels visible)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ExternalAudioSyncStatusBar(
                        state = state,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AudioSheetAlerts(state = state)
                    AudioMixerPanel(
                        state = state,
                        onVolumeChange = onVolumeChange,
                        onToggleLoudnessBoost = onToggleLoudnessBoost,
                        onAdjustSyncOffset = onAdjustSyncOffset,
                        onResetSyncOffset = onResetSyncOffset,
                        onDetachAudio = onDetachAudio,
                    )
                    CompactAudioSearchInput(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BrandFilterRow(
                        selectedFilter = state.selectedBrandFilter,
                        allCount = state.channels.size,
                        beinCount = beinCount,
                        alwanCount = alwanCount,
                        initialFocusRequester = initialFilterFocus,
                        onSelectBrandFilter = onSelectBrandFilter,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        ExternalAudioChannelListContent(
                            state = state,
                            filteredChannels = filteredChannels,
                            onSelectChannel = onSelectChannel,
                            onRefreshCatalog = onRefreshCatalog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSheetAlerts(state: ExternalAudioUiState) {
    val colors = AppTheme.colors
    state.connectionLimitWarning?.let { warning ->
        StatusBanner(text = warning, accent = colors.warning)
    }
    if (state.syncStage != ExternalAudioSyncStage.FAILED) {
        state.catalogWarning?.let { warning ->
            StatusBanner(text = warning, accent = colors.warning)
        }
    }
    val activeError = state.errorMessage
        ?: if (state.syncStage != ExternalAudioSyncStage.FAILED) state.catalogError else null
    if (activeError != null) {
        StatusBanner(text = activeError, accent = colors.danger)
    }
}

@Composable
private fun BrandFilterRow(
    selectedFilter: AudioBrandFilter,
    allCount: Int,
    beinCount: Int,
    alwanCount: Int,
    initialFocusRequester: FocusRequester,
    onSelectBrandFilter: (AudioBrandFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AudioBrandFilter.entries.forEach { filter ->
            val count = when (filter) {
                AudioBrandFilter.ALL -> allCount
                AudioBrandFilter.BEIN_SPORTS -> beinCount
                AudioBrandFilter.ALWAN -> alwanCount
            }
            FilterPillButton(
                label = "${filter.labelAr} ($count)",
                selected = selectedFilter == filter,
                onClick = { onSelectBrandFilter(filter) },
                modifier = (if (filter == AudioBrandFilter.ALL) Modifier.focusRequester(initialFocusRequester) else Modifier)
                    .testTag("external_audio_filter_${filter.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun AudioMixerPanel(
    state: ExternalAudioUiState,
    onVolumeChange: (Int) -> Unit,
    onToggleLoudnessBoost: () -> Unit,
    onAdjustSyncOffset: (Long) -> Unit,
    onResetSyncOffset: () -> Unit,
    onDetachAudio: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.62f))
            .border(1.dp, colors.border.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Volume Control Row (compact 30dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "${state.volumePercent}%",
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = colors.text,
                modifier = Modifier.width(30.dp),
                maxLines = 1,
            )
            SmallStepButton(
                label = "-10",
                onClick = { onVolumeChange(state.volumePercent - 10) },
                modifier = Modifier.testTag("external_audio_vol_down"),
            )
            Slider(
                value = state.volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.border.copy(alpha = 0.45f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 44.dp)
                    .height(24.dp),
            )
            SmallStepButton(
                label = "+10",
                onClick = { onVolumeChange(state.volumePercent + 10) },
                modifier = Modifier.testTag("external_audio_vol_up"),
            )
            SmallStepButton(
                label = if (state.loudnessBoostEnabled) "تعزيز ✓" else "تعزيز",
                selected = state.loudnessBoostEnabled,
                onClick = onToggleLoudnessBoost,
                modifier = Modifier.testTag("external_audio_boost_toggle"),
            )
        }

        // Sync Offset & Detach Row (compact 30dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = ExternalAudioSyncPolicy.formatOffsetAr(state.syncOffsetMs),
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = colors.text,
                maxLines = 1,
            )
            SmallStepButton(
                label = "-0.5ث",
                onClick = { onAdjustSyncOffset(-ExternalAudioSyncPolicy.STEP_MS) },
                modifier = Modifier.testTag("external_audio_sync_minus"),
            )
            SmallStepButton(
                label = "تصفير",
                onClick = onResetSyncOffset,
                modifier = Modifier.testTag("external_audio_sync_reset"),
            )
            SmallStepButton(
                label = "+0.5ث",
                onClick = { onAdjustSyncOffset(ExternalAudioSyncPolicy.STEP_MS) },
                modifier = Modifier.testTag("external_audio_sync_plus"),
            )
            if (state.isAttached) {
                Button(
                    onClick = onDetachAudio,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger.copy(alpha = 0.90f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("external_audio_detach_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "فصل",
                        style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalAudioChannelListContent(
    state: ExternalAudioUiState,
    filteredChannels: List<ExternalAudioChannel>,
    onSelectChannel: (ExternalAudioChannel) -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    val colors = AppTheme.colors
    when {
        state.isLoadingCatalog && state.channels.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.syncStageMessage?.takeIf { it.isNotBlank() }
                        ?: "جارٍ تحميل وتصنيف قنوات الصوت الخارجي...",
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        filteredChannels.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.channels.isEmpty()) {
                        "لا توجد قنوات صوت محفوظة حالياً."
                    } else {
                        "لا توجد نتائج مطابقة لبحثك."
                    },
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.channels.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRefreshCatalog,
                        enabled = !state.isLoadingCatalog,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("external_audio_empty_sync_button"),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مزامنة الصوتيات", style = LiquidTypography.LabelMedium)
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("external_audio_channel_list"),
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    val isSelected = state.activeChannel?.id == channel.id
                    val isConnectingThis = isSelected &&
                        (state.status == ExternalAudioStatus.CONNECTING ||
                            state.status == ExternalAudioStatus.BUFFERING ||
                            state.status == ExternalAudioStatus.RECOVERING)
                    val isActiveThis = isSelected && state.status == ExternalAudioStatus.ACTIVE
                    ExternalAudioChannelRow(
                        channel = channel,
                        isSelected = isSelected,
                        isActive = isActiveThis,
                        isConnecting = isConnectingThis,
                        onClick = { onSelectChannel(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAudioSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "ابحث عن قناة صوتية...",
                    style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LiquidTypography.LabelSmall.copy(color = colors.text, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("external_audio_search_input"),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "مسح البحث",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        selected -> colors.accent.copy(alpha = 0.24f)
        else -> colors.surface.copy(alpha = 0.55f)
    }
    val border = when {
        isFocused -> colors.focus
        selected -> colors.accent
        else -> colors.border.copy(alpha = 0.45f)
    }
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, border, RoundedCornerShape(15.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
            ),
            color = if (selected || isFocused) colors.text else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SmallStepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        selected -> colors.accent.copy(alpha = 0.24f)
        isFocused -> colors.accent.copy(alpha = 0.18f)
        else -> colors.panel.copy(alpha = 0.85f)
    }
    val border = when {
        isFocused -> colors.focus
        selected -> colors.accent
        else -> colors.border.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExternalAudioChannelRow(
    channel: ExternalAudioChannel,
    isSelected: Boolean,
    isActive: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = when {
        isActive -> colors.success.copy(alpha = 0.16f)
        isSelected -> colors.selected
        isFocused -> colors.surface.copy(alpha = 0.90f)
        else -> colors.card.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isFocused -> colors.focus
        isActive -> colors.success
        isSelected -> colors.accent
        else -> colors.border.copy(alpha = 0.45f)
    }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("external_audio_channel_${channel.number}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.panel.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.number,
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = colors.accent,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = LiquidTypography.BodySmall.copy(
                    fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.categoryName,
                style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (channel.qualityLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = channel.qualityLabel,
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.panel.copy(alpha = 0.8f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = channel.brand.displayName,
                style = LiquidTypography.LabelSmall.copy(fontSize = 9.sp),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        when {
            isConnecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            isActive -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "نشط",
                tint = colors.success,
                modifier = Modifier.size(18.dp),
            )
            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "تشغيل",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SyncCatalogPillButton(
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = when {
        isSyncing -> colors.accent.copy(alpha = 0.22f)
        isFocused -> colors.accent.copy(alpha = 0.30f)
        else -> colors.accent.copy(alpha = 0.16f)
    }
    val borderColor = when {
        isFocused -> colors.focus
        else -> colors.accent.copy(alpha = 0.72f)
    }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(17.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isSyncing,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = "مزامنة الصوتيات",
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExternalAudioSyncStatusBar(
    state: ExternalAudioUiState,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val accentColor = when (state.syncStage) {
        ExternalAudioSyncStage.FAILED -> colors.danger
        ExternalAudioSyncStage.SUCCEEDED -> colors.success
        ExternalAudioSyncStage.CONNECTING_PANEL,
        ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
        ExternalAudioSyncStage.PARSING_CHANNELS,
        ExternalAudioSyncStage.SAVING_LOCAL -> colors.accent
        ExternalAudioSyncStage.IDLE -> colors.border
    }
    val bgColor = when (state.syncStage) {
        ExternalAudioSyncStage.IDLE -> colors.surface.copy(alpha = 0.50f)
        else -> accentColor.copy(alpha = 0.12f)
    }
    val stageText = when {
        state.syncStage != ExternalAudioSyncStage.IDLE && !state.syncStageMessage.isNullOrBlank() ->
            state.syncStageMessage
        state.channels.isNotEmpty() ->
            "محفوظ محلياً (${state.channels.size})"
        else ->
            "اضغط مزامنة الصوتيات للتحديث"
    }
    val textColor = when (state.syncStage) {
        ExternalAudioSyncStage.FAILED -> colors.danger
        ExternalAudioSyncStage.SUCCEEDED -> colors.success
        ExternalAudioSyncStage.CONNECTING_PANEL,
        ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
        ExternalAudioSyncStage.PARSING_CHANNELS,
        ExternalAudioSyncStage.SAVING_LOCAL -> colors.accent
        ExternalAudioSyncStage.IDLE -> colors.textSecondary
    }
    val formattedLastSync = remember(state.lastSyncedAtMs, state.lastUpdatedAt) {
        formatExternalAudioSyncTime(state.lastSyncedAtMs, state.lastUpdatedAt)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 5.dp)
            .testTag("external_audio_sync_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (state.syncStage) {
                ExternalAudioSyncStage.CONNECTING_PANEL,
                ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
                ExternalAudioSyncStage.PARSING_CHANNELS,
                ExternalAudioSyncStage.SAVING_LOCAL -> CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.6.dp,
                    color = colors.accent,
                )
                ExternalAudioSyncStage.FAILED -> Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(12.dp),
                )
                ExternalAudioSyncStage.SUCCEEDED -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(12.dp),
                )
                ExternalAudioSyncStage.IDLE -> Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = stageText,
                style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp),
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formattedLastSync,
            style = LiquidTypography.LabelSmall.copy(fontSize = 9.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("external_audio_last_synced_text"),
        )
    }
}

private fun formatExternalAudioSyncTime(
    lastSyncedAtMs: Long?,
    fallbackUpdatedAt: String?,
): String {
    if (lastSyncedAtMs != null && lastSyncedAtMs > 0L) {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        return "آخر مزامنة: ${formatter.format(Date(lastSyncedAtMs))}"
    }
    val updated = fallbackUpdatedAt?.trim().orEmpty()
    if (updated.isNotBlank()) {
        return "آخر تحديث: $updated"
    }
    return "لم تتم المزامنة بعد"
}

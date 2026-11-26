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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

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
    ) {
        // Compact Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
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
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (state.status == ExternalAudioStatus.ACTIVE) colors.success else colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الصوت الخارجي",
                        style = LiquidTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitleText = when (state.status) {
                        ExternalAudioStatus.ACTIVE -> "✓ ${state.statusMessage ?: state.activeChannel?.name.orEmpty()}"
                        ExternalAudioStatus.CONNECTING,
                        ExternalAudioStatus.BUFFERING,
                        ExternalAudioStatus.RECOVERING -> state.statusMessage ?: "جارٍ التحضير..."
                        ExternalAudioStatus.ERROR -> state.errorMessage ?: "تعذّر تشغيل الصوت الخارجي"
                        ExternalAudioStatus.LOADING_CATALOG -> "جارٍ تحديث مصادر الصوت..."
                        ExternalAudioStatus.IDLE -> "تشغيل قناة صوتية مستقلة فوق الفيديو"
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
                        style = LiquidTypography.LabelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onRefreshCatalog,
                    enabled = !state.isLoadingCatalog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("external_audio_refresh_button"),
                ) {
                    if (state.isLoadingCatalog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "تحديث قائمة الصوت",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("external_audio_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "إغلاق",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Compact Warnings / Errors
        state.connectionLimitWarning?.let { warning ->
            StatusBanner(text = warning, accent = colors.warning)
        }
        state.catalogWarning?.let { warning ->
            StatusBanner(text = warning, accent = colors.warning)
        }
        val activeError = state.errorMessage ?: state.catalogError
        if (activeError != null) {
            StatusBanner(text = activeError, accent = colors.danger)
        }

        // Compact Audio Mixer & Sync Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = 0.65f))
                .border(1.dp, colors.border.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "${state.volumePercent}%",
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.width(34.dp),
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
                        .widthIn(min = 60.dp)
                        .height(32.dp),
                )
                SmallStepButton(
                    label = "+10",
                    onClick = { onVolumeChange(state.volumePercent + 10) },
                    modifier = Modifier.testTag("external_audio_vol_up"),
                )
                FilterPillButton(
                    label = if (state.loudnessBoostEnabled) "تعزيز ✓" else "تعزيز",
                    selected = state.loudnessBoostEnabled,
                    onClick = onToggleLoudnessBoost,
                    modifier = Modifier.testTag("external_audio_boost_toggle"),
                )
            }

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
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = ExternalAudioSyncPolicy.formatOffsetAr(state.syncOffsetMs),
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold),
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
                    Spacer(modifier = Modifier.width(2.dp))
                    Button(
                        onClick = onDetachAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.danger.copy(alpha = 0.90f),
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("external_audio_detach_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "فصل الصوت",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Compact Search Field (48dp touch target, zero text clipping)
        CompactAudioSearchInput(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
        )

        // Brand Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AudioBrandFilter.entries.forEach { filter ->
                val count = when (filter) {
                    AudioBrandFilter.ALL -> state.channels.size
                    AudioBrandFilter.BEIN_SPORTS -> beinCount
                    AudioBrandFilter.ALWAN -> alwanCount
                }
                FilterPillButton(
                    label = "${filter.labelAr} ($count)",
                    selected = state.selectedBrandFilter == filter,
                    onClick = { onSelectBrandFilter(filter) },
                    modifier = (if (filter == AudioBrandFilter.ALL) Modifier.focusRequester(initialFilterFocus) else Modifier)
                        .testTag("external_audio_filter_${filter.name.lowercase()}"),
                )
            }
        }

        // Channel List (fills exact remaining card space consistently across states)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
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
                            text = "جارٍ تحميل وتصنيف قنوات الصوت الخارجي...",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
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
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.channels.isEmpty()) {
                                "لا توجد قنوات صوت متاحة حالياً."
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
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تحديث المصادر", style = LiquidTypography.LabelMedium)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.55f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = shape,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "ابحث عن قناة صوتية...",
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LiquidTypography.BodySmall.copy(color = colors.text),
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
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "مسح البحث",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
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
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = LiquidTypography.LabelSmall,
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
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, border, RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
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
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) colors.accent.copy(alpha = 0.22f) else colors.panel.copy(alpha = 0.85f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
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
    PlayerCompactOptionCard(
        selected = isSelected,
        isActive = isActive,
        onClick = onClick,
        modifier = Modifier.testTag("external_audio_channel_${channel.number}"),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.panel.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.number,
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                maxLines = 1,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = LiquidTypography.BodySmall.copy(
                    fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.categoryName,
                style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
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
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = channel.qualityLabel,
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.panel.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = channel.brand.displayName,
                style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        when {
            isConnecting -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            isActive -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "نشط",
                tint = colors.success,
                modifier = Modifier.size(20.dp),
            )
            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "تشغيل",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

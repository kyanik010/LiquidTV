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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.liquidtv.app.ui.components.LiquidGlassSurface
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.dialogScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )

        LiquidGlassSurface(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(min = 320.dp, max = 720.dp)
                .heightIn(max = 620.dp)
                .testTag("external_audio_sheet"),
            cornerRadius = 24.dp,
            glassAlpha = 0.96f,
            glassColor = colors.panel,
            borderAlpha = 0.80f,
            borderColor = colors.border,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (state.status == ExternalAudioStatus.ACTIVE) colors.success else colors.accent,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الصوت الخارجي",
                                style = LiquidTypography.TitleLarge,
                                color = colors.text,
                            )
                            val subtitleText = when (state.status) {
                                ExternalAudioStatus.ACTIVE -> "✓ ${state.statusMessage ?: state.activeChannel?.name.orEmpty()}"
                                ExternalAudioStatus.CONNECTING,
                                ExternalAudioStatus.BUFFERING,
                                ExternalAudioStatus.RECOVERING -> state.statusMessage ?: "جارٍ التحضير..."
                                ExternalAudioStatus.ERROR -> state.errorMessage ?: "تعذّر تشغيل الصوت الخارجي"
                                ExternalAudioStatus.LOADING_CATALOG -> "جارٍ تحميل مصادر الصوت..."
                                ExternalAudioStatus.IDLE -> "تشغيل قناة صوتية مستقلة فوق الفيديو الحالي"
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "تحديث قائمة الصوت",
                                    tint = colors.text,
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
                            )
                        }
                    }
                }

                // Connection / Catalog Warnings & Errors
                state.connectionLimitWarning?.let { warning ->
                    StatusBanner(
                        text = warning,
                        accent = colors.warning,
                    )
                }
                state.catalogWarning?.let { warning ->
                    StatusBanner(
                        text = warning,
                        accent = colors.warning,
                    )
                }
                val activeError = state.errorMessage ?: state.catalogError
                if (activeError != null) {
                    StatusBanner(
                        text = activeError,
                        accent = colors.danger,
                    )
                }

                // Active Audio Mixer & Sync Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface.copy(alpha = 0.65f))
                        .border(1.dp, colors.border.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Volume + Boost + Detach Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${state.volumePercent}%",
                            style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            modifier = Modifier.width(42.dp),
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
                            modifier = Modifier.weight(1f),
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

                    // Sync Offset + Detach Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = ExternalAudioSyncPolicy.formatOffsetAr(state.syncOffsetMs),
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = onDetachAudio,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.danger.copy(alpha = 0.90f),
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("external_audio_detach_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("فصل الصوت", style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                // Search & Brand Filter Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "ابحث عن قناة صوتية...",
                                style = LiquidTypography.BodySmall,
                                color = colors.textSecondary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = colors.textSecondary,
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "مسح البحث",
                                        tint = colors.textSecondary,
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.focus,
                            unfocusedBorderColor = colors.border.copy(alpha = 0.55f),
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            cursorColor = colors.accent,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("external_audio_search_input"),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                // Channel List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 270.dp),
                ) {
                    when {
                        state.isLoadingCatalog && state.channels.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(color = colors.accent)
                                Text(
                                    text = "جارٍ تحميل وتصنيف قنوات الصوت الخارجي...",
                                    style = LiquidTypography.BodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        filteredChannels.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    text = if (state.channels.isEmpty()) {
                                        "لا توجد قنوات صوت متاحة حالياً."
                                    } else {
                                        "لا توجد نتائج مطابقة لبحثك."
                                    },
                                    style = LiquidTypography.BodySmall,
                                    color = colors.textSecondary,
                                )
                                if (state.channels.isEmpty()) {
                                    OutlinedButton(
                                        onClick = onRefreshCatalog,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("تحديث المصادر")
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
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
    }
}

@Composable
private fun StatusBanner(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = LiquidTypography.LabelSmall,
            color = accent,
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, border, RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected || isFocused) colors.text else colors.textSecondary,
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) colors.accent.copy(alpha = 0.22f) else colors.panel.copy(alpha = 0.85f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
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

    val rowBg = when {
        isActive -> colors.success.copy(alpha = 0.16f)
        isSelected -> colors.accent.copy(alpha = 0.16f)
        isFocused -> colors.surface.copy(alpha = 0.90f)
        else -> colors.surface.copy(alpha = 0.50f)
    }
    val rowBorder = when {
        isFocused -> colors.focus
        isActive -> colors.success
        isSelected -> colors.accent
        else -> colors.border.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .border(if (isFocused) 2.dp else 1.dp, rowBorder, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("external_audio_channel_${channel.number}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Channel Number Pill
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
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
                style = LiquidTypography.BodyMedium.copy(
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.categoryName,
                style = LiquidTypography.LabelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (channel.qualityLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = channel.qualityLabel,
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.panel.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = channel.brand.displayName,
                style = LiquidTypography.LabelSmall,
                color = colors.textSecondary,
            )
        }

        when {
            isConnecting -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            isActive -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "نشط",
                tint = colors.success,
                modifier = Modifier.size(22.dp),
            )
            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "تشغيل",
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

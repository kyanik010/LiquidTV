package com.liquidtv.app.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun SearchScreen(
    onChannelClick: (String, MediaType) -> Unit,
    onSeriesClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isCompact = maxWidth < 600.dp
            val hPad = if (isCompact) 16.dp else 36.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isCompact) 76.dp else 88.dp, start = hPad, end = hPad),
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    cornerRadius = 50.dp,
                    glassAlpha = 0.20f,
                    borderAlpha = 0.40f,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "بحث",
                            tint = LiquidColors.AccentBlue,
                            modifier = Modifier.size(24.dp),
                        )
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.setQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            textStyle = LiquidTypography.BodyLarge.copy(color = LiquidColors.TextPrimary),
                            cursorBrush = SolidColor(LiquidColors.AccentBlue),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = "ابحث عن قناة أو فيلم أو مسلسل…",
                                        style = LiquidTypography.BodyLarge,
                                        color = LiquidColors.TextDisabled,
                                    )
                                }
                                innerTextField()
                            },
                        )
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearQuery() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "مسح",
                                    tint = LiquidColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                when {
                    uiState.query.isEmpty() -> {
                        if (uiState.searchHistory.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "عمليات البحث الأخيرة",
                                        style = LiquidTypography.TitleSmall,
                                        color = LiquidColors.TextSecondary,
                                    )
                                    Text(
                                        "مسح الكل",
                                        style = LiquidTypography.LabelSmall,
                                        color = LiquidColors.AccentBlue,
                                        modifier = Modifier
                                            .clickable { viewModel.clearHistory() }
                                            .padding(4.dp),
                                    )
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(uiState.searchHistory) { historyItem ->
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isFocused by interactionSource.collectIsFocusedAsState()

                                        LiquidGlassSurface(
                                            modifier = Modifier
                                                .clickable(interactionSource, null) { viewModel.setQuery(historyItem) }
                                                .focusable(interactionSource = interactionSource),
                                            cornerRadius = 20.dp,
                                            glassAlpha = if (isFocused) 0.3f else 0.12f,
                                            borderAlpha = if (isFocused) 0.6f else 0.18f,
                                            isFocused = isFocused,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(Icons.Filled.History, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(14.dp))
                                                Text(historyItem, style = LiquidTypography.BodySmall, color = LiquidColors.TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.Search, null, tint = LiquidColors.TextDisabled, modifier = Modifier.size(56.dp))
                                Text(
                                    "ابحث في البث المباشر والأفلام والمسلسلات",
                                    style = LiquidTypography.TitleMedium,
                                    color = LiquidColors.TextSecondary,
                                )
                                Text(
                                    "اكتب أي اسم أو تصنيف أو دولة أو لغة",
                                    style = LiquidTypography.BodySmall,
                                    color = LiquidColors.TextTertiary,
                                )
                            }
                        }
                    }
                    uiState.isSearching -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LiquidColors.AccentBlue)
                        }
                    }
                    !uiState.hasResults -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidGlassSurface(
                                cornerRadius = 20.dp,
                                glassAlpha = 0.15f,
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(48.dp))
                                    Text(
                                        "لا توجد نتائج مطابقة لـ \"${uiState.query}\"",
                                        style = LiquidTypography.TitleMedium,
                                        color = LiquidColors.TextPrimary,
                                    )
                                    Text(
                                        "تأكد من كتابة الكلمة بشكل صحيح أو جرب كلمة بحث أعم",
                                        style = LiquidTypography.BodySmall,
                                        color = LiquidColors.TextTertiary,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val total = uiState.results.size + uiState.seriesResults.size
                        Text(
                            text = "تم العثور على $total نتيجة",
                            style = LiquidTypography.LabelMedium,
                            color = LiquidColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isCompact) 155.dp else 190.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = if (isCompact) 96.dp else 32.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(uiState.results, key = { "ch_${it.id}" }) { channel ->
                                SearchResultGridCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.id, channel.mediaType) },
                                )
                            }
                            items(uiState.seriesResults, key = { "sr_${it.id}" }) { series ->
                                SeriesSearchGridCard(
                                    series = series,
                                    onClick = { onSeriesClick(series.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultGridCard(
    channel: Channel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "searchCardScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.25f else 0.12f,
        borderAlpha = if (isFocused) 0.7f else 0.18f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(LiquidColors.GlassTertiary, RoundedCornerShape(10.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(32.dp))
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    if (channel.mediaType == MediaType.LIVE) {
                        LiveBadge()
                    } else {
                        MediaKindBadge("فيلم", LiquidColors.AccentViolet)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.groupTitle ?: channel.country ?: "عام",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeriesSearchGridCard(
    series: SeriesItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "seriesSearchScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.25f else 0.12f,
        borderAlpha = if (isFocused) 0.7f else 0.18f,
        borderColor = if (isFocused) LiquidColors.AccentViolet else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(LiquidColors.GlassTertiary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!series.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = series.coverUrl,
                        contentDescription = series.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentVioletLight, modifier = Modifier.size(32.dp))
                }
                Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                    MediaKindBadge("مسلسل", LiquidColors.AccentCyan)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = series.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = series.categoryName ?: series.genre ?: "مسلسل",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaKindBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = LiquidTypography.LabelSmall, color = Color.White)
    }
}

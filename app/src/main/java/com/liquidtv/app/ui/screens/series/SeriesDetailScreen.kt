package com.liquidtv.app.ui.screens.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.SeasonGroup
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.calculateEpisodeProgress
import com.liquidtv.app.ui.utils.formatDurationMinutesAr
import com.liquidtv.app.ui.utils.formatEpisodeCode
import com.liquidtv.app.ui.utils.formatResumeTimeAr
import com.liquidtv.app.ui.utils.formatSeasonTitleAr

@Composable
fun SeriesDetailScreen(
    onEpisodeClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val isWide = maxWidth >= 760.dp
            val hPad = if (isWide) 32.dp else 16.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Top Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                            }
                            Text(
                                text = state.series?.name ?: "تفاصيل المسلسل",
                                style = LiquidTypography.HeadlineSmall,
                                color = LiquidColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = viewModel::refreshDetails,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "تحديث الحلقات", tint = LiquidColors.TextSecondary)
                            }
                            state.series?.let { series ->
                                IconButton(
                                    onClick = viewModel::toggleFavorite,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = if (series.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = if (series.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                        tint = if (series.isFavorite) LiquidColors.LiveRed else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }

                // Hero Banner Card
                state.series?.let { series ->
                    item {
                        SeriesHeroBanner(
                            series = series,
                            seasonsCount = state.seasons.size,
                            totalEpisodesCount = state.totalEpisodesCount,
                            firstEpisode = state.seasons.firstOrNull()?.episodes?.firstOrNull(),
                            continueWatchingEpisode = state.continueWatchingEpisode,
                            isWide = isWide,
                            onPlayEpisode = onEpisodeClick,
                        )
                    }
                }

                // Visual Selectable Season Cards
                if (state.seasons.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "المواسم (${state.seasons.size})",
                                style = LiquidTypography.TitleMedium,
                                color = LiquidColors.TextPrimary,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("seasons_row"),
                            ) {
                                items(state.seasons, key = { it.seasonNumber }) { season ->
                                    SeasonGlassCard(
                                        season = season,
                                        isSelected = season.seasonNumber == state.selectedSeason,
                                        onClick = { viewModel.selectSeason(season.seasonNumber) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Episodes Section Header
                if (!state.isLoading && state.seasons.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "حلقات ${formatSeasonTitleAr(state.selectedSeason)}",
                                style = LiquidTypography.TitleMedium,
                                color = LiquidColors.AccentVioletLight,
                            )
                            Text(
                                text = "${state.currentSeasonEpisodes.size} حلقة",
                                style = LiquidTypography.LabelMedium,
                                color = LiquidColors.TextTertiary,
                            )
                        }
                    }
                }

                // Loading / Empty / Error / Episodes List
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = LiquidColors.AccentVioletLight)
                        }
                    }
                } else if (state.currentSeasonEpisodes.isEmpty()) {
                    item {
                        LiquidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            glassAlpha = 0.14f,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentVioletLight, modifier = Modifier.size(40.dp))
                                Text(
                                    text = state.error ?: "لا توجد حلقات متاحة لهذا المسلسل حالياً.",
                                    style = LiquidTypography.BodyMedium,
                                    color = LiquidColors.TextSecondary,
                                )
                                Button(onClick = viewModel::refreshDetails) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    }
                } else {
                    items(state.currentSeasonEpisodes, key = { it.id }) { ep ->
                        EpisodeCinemaRowCard(
                            episode = ep,
                            seriesCoverUrl = state.series?.coverUrl,
                            onClick = { onEpisodeClick(ep.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeroBanner(
    series: SeriesItem,
    seasonsCount: Int,
    totalEpisodesCount: Int,
    firstEpisode: EpisodeItem?,
    continueWatchingEpisode: EpisodeItem?,
    isWide: Boolean,
    onPlayEpisode: (String) -> Unit,
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        glassAlpha = 0.18f,
        borderAlpha = 0.28f,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Atmospheric backdrop
            if (!series.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = series.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.22f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.MidnightNavy.copy(alpha = 0.45f),
                                LiquidColors.MidnightNavy.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // 2:3 Poster
                    Box(
                        modifier = Modifier
                            .width(if (isWide) 130.dp else 102.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LiquidColors.GlassTertiary)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
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
                            Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentVioletLight, modifier = Modifier.size(38.dp))
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = series.name,
                            style = LiquidTypography.HeadlineSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Real Metadata Chips Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            series.categoryName?.takeIf { it.isNotBlank() }?.let { cat ->
                                MetaPill(text = cat, tint = LiquidColors.AccentVioletLight)
                            }
                            series.releaseDate?.takeIf { it.isNotBlank() }?.let { date ->
                                MetaPill(text = date.take(4), tint = LiquidColors.TextSecondary)
                            }
                            series.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                                MetaPill(text = "★ $rating", tint = LiquidColors.AccentGold)
                            }
                        }

                        if (seasonsCount > 0 || totalEpisodesCount > 0) {
                            Text(
                                text = "$seasonsCount مواسم • $totalEpisodesCount حلقة",
                                style = LiquidTypography.LabelMedium,
                                color = LiquidColors.AccentBlueLight,
                            )
                        }

                        series.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                            Text(
                                text = plot,
                                style = LiquidTypography.BodySmall,
                                color = LiquidColors.TextSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        series.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                            Text(
                                text = "التصنيف الفني: $genre",
                                style = LiquidTypography.LabelSmall,
                                color = LiquidColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        series.director?.takeIf { it.isNotBlank() }?.let { director ->
                            Text(
                                text = "الإخراج: $director",
                                style = LiquidTypography.LabelSmall,
                                color = LiquidColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        series.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                            Text(
                                text = "طاقم العمل: $cast",
                                style = LiquidTypography.LabelSmall,
                                color = LiquidColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Action CTAs
                if (continueWatchingEpisode != null || firstEpisode != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (continueWatchingEpisode != null) {
                            val epCode = formatEpisodeCode(continueWatchingEpisode.seasonNumber, continueWatchingEpisode.episodeNumber)
                            Button(
                                onClick = { onPlayEpisode(continueWatchingEpisode.id) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiquidColors.AccentViolet,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("continue_watching_button"),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (epCode != null) "متابعة المشاهدة ($epCode)" else "متابعة المشاهدة",
                                    style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else if (firstEpisode != null) {
                            Button(
                                onClick = { onPlayEpisode(firstEpisode.id) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiquidColors.AccentViolet,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("play_first_episode_button"),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "تشغيل الحلقة الأولى",
                                    style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold),
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
private fun MetaPill(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .background(tint.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = LiquidTypography.LabelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun SeasonGlassCard(
    season: SeasonGroup,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .widthIn(min = 126.dp)
            .heightIn(min = 60.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("season_card_${season.seasonNumber}"),
        cornerRadius = 16.dp,
        glassAlpha = if (isSelected) 0.36f else if (isFocused) 0.24f else 0.12f,
        borderAlpha = if (isFocused) 0.88f else if (isSelected) 0.65f else 0.18f,
        borderColor = if (isSelected || isFocused) LiquidColors.AccentVioletLight else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = formatSeasonTitleAr(season.seasonNumber),
                style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.White else LiquidColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = "${season.episodes.size} حلقة",
                style = LiquidTypography.LabelSmall,
                color = if (isSelected) LiquidColors.AccentVioletLight else LiquidColors.TextTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EpisodeCinemaRowCard(
    episode: EpisodeItem,
    seriesCoverUrl: String?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val thumbUrl = episode.coverUrl?.takeIf { it.isNotBlank() } ?: seriesCoverUrl?.takeIf { it.isNotBlank() }
    val codeLabel = formatEpisodeCode(episode.seasonNumber, episode.episodeNumber)
    val durationLabel = formatDurationMinutesAr(episode.durationSec)
    val progress = calculateEpisodeProgress(episode.resumePositionMs, episode.durationSec)
    val resumeLabel = formatResumeTimeAr(episode.resumePositionMs)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("episode_card_${episode.id}"),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.28f else 0.14f,
        borderAlpha = if (isFocused) 0.88f else 0.20f,
        borderColor = if (isFocused) LiquidColors.AccentVioletLight else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Thumbnail with play overlay
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LiquidColors.GlassTertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = LiquidColors.AccentVioletLight.copy(alpha = 0.45f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل الحلقة", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        codeLabel?.let { code ->
                            Box(
                                modifier = Modifier
                                    .background(LiquidColors.AccentViolet.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(code, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentVioletLight)
                            }
                        }
                        durationLabel?.let { dur ->
                            Text(dur, style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                        }
                        if (episode.lastWatchedAt != null) {
                            Text("تمت المشاهدة", style = LiquidTypography.LabelSmall, color = LiquidColors.SuccessGreen)
                        }
                    }

                    Text(
                        text = episode.title,
                        style = LiquidTypography.TitleSmall,
                        color = LiquidColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    episode.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Text(
                            text = plot,
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Progress bar ONLY when real durationSec > 0 and resumePositionMs > 0
            if (progress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = LiquidColors.AccentVioletLight,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    resumeLabel?.let { time ->
                        Text(
                            text = time,
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.AccentVioletLight,
                        )
                    }
                }
            }
        }
    }
}

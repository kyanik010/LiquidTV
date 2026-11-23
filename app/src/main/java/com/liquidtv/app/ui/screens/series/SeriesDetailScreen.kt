package com.liquidtv.app.ui.screens.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun SeriesDetailScreen(
    onEpisodeClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = state.series?.name ?: "Series Details",
                        style = LiquidTypography.HeadlineMedium,
                        color = LiquidColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                state.series?.let { series ->
                    IconButton(
                        onClick = viewModel::toggleFavorite,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (series.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite Series",
                            tint = if (series.isFavorite) LiquidColors.LiveRed else Color.White,
                        )
                    }
                }
            }

            state.series?.let { series ->
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    glassAlpha = 0.16f,
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LiquidColors.GlassTertiary),
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
                                Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentVioletLight, modifier = Modifier.size(40.dp))
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                series.categoryName?.let {
                                    Text(it, style = LiquidTypography.LabelMedium, color = LiquidColors.AccentVioletLight)
                                }
                                series.releaseDate?.takeIf { it.isNotBlank() }?.let {
                                    Text("• $it", style = LiquidTypography.LabelMedium, color = LiquidColors.TextSecondary)
                                }
                                series.rating?.takeIf { it.isNotBlank() }?.let {
                                    Text("• ★ $it", style = LiquidTypography.LabelMedium, color = LiquidColors.AccentGold)
                                }
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
                            series.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                                Text(
                                    text = "Cast: $cast",
                                    style = LiquidTypography.LabelSmall,
                                    color = LiquidColors.TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Season pills
            if (state.seasons.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.seasons, key = { it.seasonNumber }) { season ->
                        val isSelected = season.seasonNumber == state.selectedSeason
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        LiquidGlassSurface(
                            modifier = Modifier
                                .height(42.dp)
                                .clickable(interactionSource, null) { viewModel.selectSeason(season.seasonNumber) }
                                .focusable(interactionSource = interactionSource),
                            cornerRadius = 21.dp,
                            glassAlpha = if (isSelected) 0.35f else if (isFocused) 0.24f else 0.12f,
                            borderAlpha = if (isFocused) 0.85f else if (isSelected) 0.55f else 0.18f,
                            borderColor = if (isSelected) LiquidColors.AccentVioletLight else Color.White,
                            isFocused = isFocused,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Season ${season.seasonNumber}",
                                    style = LiquidTypography.LabelLarge,
                                    color = if (isSelected) Color.White else LiquidColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiquidColors.AccentBlue)
                }
            } else if (state.currentSeasonEpisodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error ?: "No episodes available for this series.",
                        style = LiquidTypography.BodyMedium,
                        color = LiquidColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.currentSeasonEpisodes, key = { it.id }) { ep ->
                        EpisodeRowCard(episode = ep, onClick = { onEpisodeClick(ep.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.28f else 0.14f,
        borderAlpha = if (isFocused) 0.85f else 0.20f,
        borderColor = if (isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LiquidColors.AccentBlue.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play episode", tint = LiquidColors.AccentBlueLight)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "E${episode.episodeNumber} • ${episode.title}",
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
            if (episode.resumePositionMs > 0L) {
                val mins = (episode.resumePositionMs / 60_000L).coerceAtLeast(1L)
                Text(
                    text = "Resume ${mins}m",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.AccentBlueLight,
                )
            }
        }
    }
}

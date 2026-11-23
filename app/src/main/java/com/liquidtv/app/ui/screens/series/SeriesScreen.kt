package com.liquidtv.app.ui.screens.series

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun SeriesScreen(
    onSeriesClick: (String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Series & Shows",
                    style = LiquidTypography.HeadlineMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(LiquidColors.TextPrimary, LiquidColors.AccentVioletLight),
                        ),
                    ),
                )
                Text(
                    text = "${uiState.series.size} series available",
                    style = LiquidTypography.BodySmall,
                    color = LiquidColors.TextTertiary,
                )

                if (uiState.categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            SeriesFilterPill(
                                label = "All",
                                selected = uiState.selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            SeriesFilterPill(
                                label = cat.name,
                                selected = uiState.selectedCategory.equals(cat.name, ignoreCase = true),
                                onClick = { viewModel.selectCategory(cat.name) },
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.series.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiquidColors.AccentBlue)
                }
            } else if (uiState.series.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LiquidGlassSurface(
                        modifier = Modifier.padding(32.dp),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.16f,
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentVioletLight, modifier = Modifier.size(48.dp))
                            Text("No Series Found", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                            Text(
                                "This playlist does not include TV series, or none match the selected category.",
                                style = LiquidTypography.BodyMedium,
                                color = LiquidColors.TextSecondary,
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 175.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.series, key = { it.id }) { series ->
                        SeriesGridCard(
                            series = series,
                            onClick = { onSeriesClick(series.id) },
                            onFavoriteToggle = { viewModel.toggleFavorite(series.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .height(40.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 20.dp,
        glassAlpha = if (selected) 0.35f else if (isFocused) 0.24f else 0.12f,
        borderAlpha = if (isFocused) 0.8f else if (selected) 0.5f else 0.18f,
        borderColor = if (selected) LiquidColors.AccentViolet else Color.White,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelMedium,
                color = if (selected) LiquidColors.AccentVioletLight else LiquidColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun SeriesGridCard(
    series: SeriesItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(255.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.32f else 0.16f,
        borderAlpha = if (isFocused) 0.85f else 0.22f,
        borderColor = if (isFocused) LiquidColors.AccentViolet else Color.White,
        isFocused = isFocused,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
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
                    Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentViolet.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                }
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = if (series.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (series.isFavorite) LiquidColors.LiveRed else Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = series.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                series.categoryName?.let {
                    Text(
                        text = it,
                        style = LiquidTypography.LabelSmall,
                        color = LiquidColors.AccentVioletLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

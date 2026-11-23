package com.liquidtv.app.ui.screens.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    onMovieClick: (String) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoriesSheet by remember { mutableStateOf(false) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isWide = maxWidth >= 720.dp
            val isCompactHeight = maxHeight < 520.dp
            val topPad = if (isCompactHeight) 60.dp else 64.dp
            val hPad = when {
                maxWidth >= 1100.dp -> 20.dp
                isWide -> 14.dp
                else -> 12.dp
            }
            val sidebarWidth = when {
                maxWidth >= 1200.dp -> 215.dp
                maxWidth >= 960.dp -> 192.dp
                else -> 168.dp
            }
            val gridMinCardWidth = when {
                isCompactHeight -> 96
                maxWidth >= 1600.dp -> 148
                maxWidth >= 1100.dp -> 124
                isWide -> 108
                else -> 112
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad, start = hPad, end = hPad),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 6.dp),
            ) {
                // Single-line compact header: Title + inline count pill (+ filter button on narrow portrait)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .testTag("movies_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.selectedCategory
                                ?: if (uiState.favoritesOnly) "الأفلام المفضلة" else "الأفلام",
                            style = (if (isCompactHeight) LiquidTypography.TitleMedium else LiquidTypography.TitleLarge).copy(
                                brush = Brush.linearGradient(
                                    listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                                ),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        LiquidGlassSurface(
                            cornerRadius = 12.dp,
                            glassAlpha = 0.14f,
                            borderAlpha = 0.22f,
                        ) {
                            Text(
                                text = "${uiState.movies.size} فيلم متاح",
                                style = LiquidTypography.LabelSmall,
                                color = LiquidColors.TextSecondary,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("movies_count"),
                            )
                        }
                    }

                    if (!isWide) {
                        Spacer(Modifier.width(8.dp))
                        LiquidGlassSurface(
                            modifier = Modifier
                                .height(38.dp)
                                .clickable { showCategoriesSheet = true },
                            cornerRadius = 19.dp,
                            glassAlpha = 0.20f,
                            borderColor = LiquidColors.AccentBlueLight,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Filled.FilterList, contentDescription = "التصنيفات", tint = LiquidColors.AccentBlueLight, modifier = Modifier.size(16.dp))
                                Text("التصنيفات", style = LiquidTypography.LabelMedium, color = Color.White)
                            }
                        }
                    }
                }

                if (!isWide && (uiState.categories.isNotEmpty() || uiState.favoritesCount > 0)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            MovieFilterPill(
                                label = "الكل (${uiState.totalCount})",
                                selected = uiState.selectedCategory == null && !uiState.favoritesOnly,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        item {
                            MovieFilterPill(
                                label = "المفضلة (${uiState.favoritesCount})",
                                selected = uiState.favoritesOnly,
                                onClick = { viewModel.selectFavoritesOnly() },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            MovieFilterPill(
                                label = "${cat.name} (${cat.channelCount})",
                                selected = !uiState.favoritesOnly && uiState.selectedCategory.equals(cat.name, ignoreCase = true),
                                onClick = { viewModel.selectCategory(cat.name) },
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.movies.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                    }
                } else if (uiState.error != null && uiState.movies.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LiquidGlassSurface(cornerRadius = 20.dp, glassAlpha = 0.16f) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = LiquidColors.ErrorRed, modifier = Modifier.size(44.dp))
                                Text(uiState.error.orEmpty(), style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
                                Button(onClick = viewModel::refresh) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    }
                } else if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 12.dp),
                    ) {
                        MoviesCategoriesSidebar(
                            categories = uiState.categories,
                            totalCount = uiState.totalCount,
                            favoritesCount = uiState.favoritesCount,
                            selectedCategory = uiState.selectedCategory,
                            favoritesOnly = uiState.favoritesOnly,
                            compactHeight = isCompactHeight,
                            onSelectAll = { viewModel.selectCategory(null) },
                            onSelectFavorites = { viewModel.selectFavoritesOnly() },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                        )

                        MoviesGridArea(
                            movies = uiState.movies,
                            onMovieClick = onMovieClick,
                            onFavoriteToggle = viewModel::toggleFavorite,
                            contentPadding = PaddingValues(bottom = 12.dp),
                            minCardWidth = gridMinCardWidth,
                            compactHeight = isCompactHeight,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    MoviesGridArea(
                        movies = uiState.movies,
                        onMovieClick = onMovieClick,
                        onFavoriteToggle = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        minCardWidth = gridMinCardWidth,
                        compactHeight = isCompactHeight,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    )
                }
            }
        }

        if (showCategoriesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCategoriesSheet = false },
                containerColor = LiquidColors.DeepSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("تصنيفات الأفلام", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            MovieSidebarRow(
                                label = "جميع الأفلام",
                                count = uiState.totalCount,
                                isSelected = uiState.selectedCategory == null && !uiState.favoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectCategory(null)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        item {
                            MovieSidebarRow(
                                label = "المفضلة",
                                count = uiState.favoritesCount,
                                isSelected = uiState.favoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectFavoritesOnly()
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            MovieSidebarRow(
                                label = cat.name,
                                count = cat.channelCount,
                                isSelected = !uiState.favoritesOnly && uiState.selectedCategory.equals(cat.name, ignoreCase = true),
                                compact = false,
                                onClick = {
                                    viewModel.selectCategory(cat.name)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviesCategoriesSidebar(
    categories: List<Category>,
    totalCount: Int,
    favoritesCount: Int,
    selectedCategory: String?,
    favoritesOnly: Boolean,
    compactHeight: Boolean,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 20.dp,
        glassAlpha = 0.14f,
        borderAlpha = 0.22f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = if (compactHeight) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 5.dp),
        ) {
            Text(
                text = "التصنيفات",
                style = if (compactHeight) LiquidTypography.TitleSmall else LiquidTypography.TitleMedium,
                color = LiquidColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = if (compactHeight) 0.dp else 2.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("movies_categories_sidebar"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "__all__") {
                    MovieSidebarRow(
                        label = "جميع الأفلام",
                        count = totalCount,
                        isSelected = selectedCategory == null && !favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectAll,
                    )
                }
                item(key = "__fav__") {
                    MovieSidebarRow(
                        label = "المفضلة",
                        count = favoritesCount,
                        isSelected = favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectFavorites,
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    MovieSidebarRow(
                        label = cat.name,
                        count = cat.channelCount,
                        isSelected = !favoritesOnly && selectedCategory.equals(cat.name, ignoreCase = true),
                        compact = compactHeight,
                        onClick = { onSelectCategory(cat.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieSidebarRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 36.dp else 38.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 11.dp,
        glassAlpha = if (isSelected) 0.34f else if (isFocused) 0.22f else 0.08f,
        borderAlpha = if (isFocused) 0.85f else if (isSelected) 0.55f else 0.14f,
        borderColor = if (isSelected || isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelLarge,
                color = if (isSelected) Color.White else LiquidColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = LiquidTypography.LabelSmall,
                color = if (isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun MoviesGridArea(
    movies: List<Channel>,
    onMovieClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    contentPadding: PaddingValues,
    minCardWidth: Int,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    if (movies.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LiquidGlassSurface(
                modifier = Modifier.padding(24.dp),
                cornerRadius = 20.dp,
                glassAlpha = 0.16f,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Movie, null, tint = LiquidColors.AccentBlueLight, modifier = Modifier.size(46.dp))
                    Text("لا توجد أفلام متاحة", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                    Text(
                        text = "لا يحتوي هذا الاشتراك على أفلام، أو لا توجد نتائج ضمن التصنيف المختار.",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextSecondary,
                    )
                }
            }
        }
        return
    }

    val gridGap = if (compactHeight) 8.dp else 10.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCardWidth.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gridGap),
        verticalArrangement = Arrangement.spacedBy(gridGap),
        modifier = modifier.testTag("movies_grid"),
    ) {
        items(movies, key = { it.id }) { movie ->
            MovieCinemaPosterCard(
                movie = movie,
                compactHeight = compactHeight,
                onClick = { onMovieClick(movie.id) },
                onFavoriteToggle = { onFavoriteToggle(movie.id) },
            )
        }
    }
}

@Composable
private fun MovieFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .height(36.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (selected) 0.35f else if (isFocused) 0.24f else 0.12f,
        borderAlpha = if (isFocused) 0.8f else if (selected) 0.55f else 0.18f,
        borderColor = if (selected || isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelMedium,
                color = if (selected) Color.White else LiquidColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun MovieCinemaPosterCard(
    movie: Channel,
    compactHeight: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.30f else 0.15f,
        borderAlpha = if (isFocused) 0.90f else 0.22f,
        borderColor = if (isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Strict 2:3 Cinema Poster Ratio — never squashed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(LiquidColors.GlassTertiary),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.logoUrl,
                        contentDescription = movie.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = LiquidColors.AccentBlueLight.copy(alpha = 0.45f),
                        modifier = Modifier.size(44.dp),
                    )
                }

                // Bottom Cinema Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        ),
                )

                movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "★ $rating",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = LiquidColors.AccentGold,
                        )
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (movie.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (movie.isFavorite) LiquidColors.LiveRed else Color.White,
                    )
                }

                if (movie.resumePositionMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(7.dp)
                            .background(LiquidColors.AccentBlue.copy(alpha = 0.90f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "متابعة",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }

                movie.releaseYear?.takeIf { it.isNotBlank() && it != "0" }?.let { year ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(7.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = year.take(4),
                            style = LiquidTypography.LabelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = if (compactHeight) 4.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = movie.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = if (compactHeight) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movie.groupTitle?.takeIf { it.isNotBlank() }?.let { group ->
                    Text(
                        text = group,
                        style = LiquidTypography.LabelSmall,
                        color = LiquidColors.AccentBlueLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

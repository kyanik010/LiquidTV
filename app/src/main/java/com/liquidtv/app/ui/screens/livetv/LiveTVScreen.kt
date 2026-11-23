package com.liquidtv.app.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.ChannelFilter
import com.liquidtv.app.domain.model.SortOrder
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.calculateEpgProgress
import com.liquidtv.app.ui.utils.formatEpgTimeRange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTVScreen(
    filterCategory: String? = null,
    filterLanguage: String? = null,
    filterCountry: String? = null,
    onChannelClick: (String) -> Unit,
    viewModel: LiveTVViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoriesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(filterCategory, filterLanguage, filterCountry) {
        if (filterCategory != null || filterLanguage != null || filterCountry != null) {
            viewModel.setFilter(
                ChannelFilter(
                    category = filterCategory,
                    language = filterLanguage,
                    country = filterCountry,
                )
            )
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isWide = maxWidth >= 840.dp
            val topPad = if (isWide) 84.dp else 74.dp
            val hPad = if (isWide) 24.dp else 14.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad, start = hPad, end = hPad),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header bar
                LiveTVHeader(
                    filterTitle = filterLanguage ?: filterCountry ?: uiState.filter.category
                        ?: if (uiState.filter.showFavoritesOnly) "القنوات المفضلة" else "البث المباشر",
                    channelsCount = uiState.channels.size,
                    sortOrder = uiState.filter.sortOrder,
                    viewMode = uiState.viewMode,
                    isWide = isWide,
                    onOpenCategoriesSheet = { showCategoriesSheet = true },
                    onSortSelected = viewModel::setSortOrder,
                    onViewModeSelected = viewModel::setViewMode,
                )

                if (uiState.isLoading && uiState.channels.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                    }
                } else if (uiState.error != null && uiState.channels.isEmpty()) {
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
                    // Wide TV / Tablet 3-Pane Liquid Glass Layout:
                    // Categories Sidebar | Channels List/Grid | Channel Info + EPG Preview
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LiveCategoriesSidebar(
                            categories = uiState.categories,
                            totalCount = uiState.totalChannelsCount,
                            favoritesCount = uiState.favoritesCount,
                            selectedCategory = uiState.filter.category,
                            favoritesOnly = uiState.filter.showFavoritesOnly,
                            onSelectAll = { viewModel.selectCategory(null) },
                            onSelectFavorites = { viewModel.selectFavoritesFilter() },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            modifier = Modifier
                                .width(225.dp)
                                .fillMaxHeight(),
                        )

                        LiquidGlassSurface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 22.dp,
                            glassAlpha = 0.12f,
                            borderAlpha = 0.20f,
                        ) {
                            LiveChannelsPane(
                                channels = uiState.channels,
                                selectedChannelId = uiState.selectedChannel?.id,
                                viewMode = uiState.viewMode,
                                onChannelFocused = viewModel::selectChannelForPreview,
                                onChannelClicked = { channel ->
                                    if (uiState.selectedChannel?.id == channel.id) {
                                        onChannelClick(channel.id)
                                    } else {
                                        viewModel.selectChannelForPreview(channel)
                                    }
                                },
                                onChannelPlayDirect = { channel -> onChannelClick(channel.id) },
                                onFavoriteToggle = viewModel::toggleFavorite,
                                contentPadding = PaddingValues(14.dp),
                            )
                        }

                        LiveChannelInfoEpgPane(
                            channel = uiState.selectedChannel,
                            epg = uiState.selectedEpg,
                            onPlayClick = { ch -> onChannelClick(ch.id) },
                            onFavoriteToggle = { ch -> viewModel.toggleFavorite(ch.id) },
                            modifier = Modifier
                                .width(290.dp)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    // Compact Phone Layout: Quick filter pills + selected EPG strip + full-width channel list
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        item {
                            CompactFilterChip(
                                label = "الكل (${uiState.totalChannelsCount})",
                                selected = uiState.filter.category == null && !uiState.filter.showFavoritesOnly,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        item {
                            CompactFilterChip(
                                label = "المفضلة (${uiState.favoritesCount})",
                                selected = uiState.filter.showFavoritesOnly,
                                onClick = { viewModel.selectFavoritesFilter() },
                            )
                        }
                        items(uiState.categories.take(12), key = { it.id }) { cat ->
                            CompactFilterChip(
                                label = "${cat.name} (${cat.channelCount})",
                                selected = uiState.filter.category.equals(cat.name, ignoreCase = true),
                                onClick = { viewModel.selectCategory(cat.name) },
                            )
                        }
                    }

                    uiState.selectedChannel?.let { selected ->
                        val cur = uiState.selectedEpg.current
                        if (cur != null) {
                            CompactSelectedEpgStrip(
                                channel = selected,
                                epg = uiState.selectedEpg,
                                onPlayClick = { onChannelClick(selected.id) },
                            )
                        }
                    }

                    LiveChannelsPane(
                        channels = uiState.channels,
                        selectedChannelId = uiState.selectedChannel?.id,
                        viewMode = uiState.viewMode,
                        onChannelFocused = viewModel::selectChannelForPreview,
                        onChannelClicked = { ch -> onChannelClick(ch.id) },
                        onChannelPlayDirect = { ch -> onChannelClick(ch.id) },
                        onFavoriteToggle = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(bottom = 88.dp),
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
                    Text(
                        text = "تصنيفات القنوات",
                        style = LiquidTypography.TitleLarge,
                        color = LiquidColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            CategorySidebarRow(
                                label = "جميع القنوات",
                                count = uiState.totalChannelsCount,
                                isSelected = uiState.filter.category == null && !uiState.filter.showFavoritesOnly,
                                onClick = {
                                    viewModel.selectCategory(null)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        item {
                            CategorySidebarRow(
                                label = "المفضلة",
                                count = uiState.favoritesCount,
                                isSelected = uiState.filter.showFavoritesOnly,
                                onClick = {
                                    viewModel.selectFavoritesFilter()
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            CategorySidebarRow(
                                label = cat.name,
                                count = cat.channelCount,
                                isSelected = uiState.filter.category.equals(cat.name, ignoreCase = true),
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
private fun LiveTVHeader(
    filterTitle: String,
    channelsCount: Int,
    sortOrder: SortOrder,
    viewMode: ViewMode,
    isWide: Boolean,
    onOpenCategoriesSheet: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onViewModeSelected: (ViewMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filterTitle,
                    style = LiquidTypography.HeadlineSmall.copy(
                        brush = Brush.linearGradient(
                            listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                        ),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$channelsCount قناة متاحة",
                    style = LiquidTypography.BodySmall,
                    color = LiquidColors.TextTertiary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isWide) {
                    LiquidGlassSurface(
                        modifier = Modifier
                            .height(42.dp)
                            .clickable(onClick = onOpenCategoriesSheet),
                        cornerRadius = 21.dp,
                        glassAlpha = 0.20f,
                        borderColor = LiquidColors.AccentBlueLight,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "التصنيفات", tint = LiquidColors.AccentBlueLight, modifier = Modifier.size(18.dp))
                            Text("التصنيفات", style = LiquidTypography.LabelMedium, color = Color.White)
                        }
                    }
                }

                // ViewMode toggle buttons (48.dp touch target)
                ViewModeIconButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = "عرض القائمة",
                    selected = viewMode == ViewMode.LIST,
                    onClick = { onViewModeSelected(ViewMode.LIST) },
                )
                ViewModeIconButton(
                    icon = Icons.Filled.GridView,
                    contentDescription = "عرض الشبكة",
                    selected = viewMode == ViewMode.GRID,
                    onClick = { onViewModeSelected(ViewMode.GRID) },
                )
            }
        }

        // Sort pills
        val sortOrders = listOf(
            "أ - ي" to SortOrder.NAME_ASC,
            "ي - أ" to SortOrder.NAME_DESC,
            "المفضلة" to SortOrder.FAVORITES_FIRST,
            "التصنيف" to SortOrder.CATEGORY,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sortOrders) { (label, order) ->
                val isSelected = sortOrder == order
                CompactFilterChip(
                    label = label,
                    selected = isSelected,
                    onClick = { onSortSelected(order) },
                )
            }
        }
    }
}

@Composable
private fun ViewModeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .size(44.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 22.dp,
        glassAlpha = if (selected) 0.34f else 0.14f,
        borderAlpha = if (isFocused) 0.85f else if (selected) 0.55f else 0.20f,
        borderColor = if (selected || isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) LiquidColors.AccentBlueLight else LiquidColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LiveCategoriesSidebar(
    categories: List<Category>,
    totalCount: Int,
    favoritesCount: Int,
    selectedCategory: String?,
    favoritesOnly: Boolean,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 22.dp,
        glassAlpha = 0.14f,
        borderAlpha = 0.22f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "التصنيفات",
                style = LiquidTypography.TitleMedium,
                color = LiquidColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live_categories_sidebar"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "__all__") {
                    CategorySidebarRow(
                        label = "جميع القنوات",
                        count = totalCount,
                        isSelected = selectedCategory == null && !favoritesOnly,
                        onClick = onSelectAll,
                    )
                }
                item(key = "__fav__") {
                    CategorySidebarRow(
                        label = "المفضلة",
                        count = favoritesCount,
                        isSelected = favoritesOnly,
                        onClick = onSelectFavorites,
                    )
                }
                items(categories, key = { it.id }) { category ->
                    CategorySidebarRow(
                        label = category.name,
                        count = category.channelCount,
                        isSelected = !favoritesOnly && selectedCategory.equals(category.name, ignoreCase = true),
                        onClick = { onSelectCategory(category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySidebarRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 14.dp,
        glassAlpha = if (isSelected) 0.34f else if (isFocused) 0.22f else 0.08f,
        borderAlpha = if (isFocused) 0.85f else if (isSelected) 0.55f else 0.14f,
        borderColor = if (isSelected || isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = LiquidTypography.LabelSmall,
                color = if (isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LiveChannelsPane(
    channels: List<Channel>,
    selectedChannelId: String?,
    viewMode: ViewMode,
    onChannelFocused: (Channel) -> Unit,
    onChannelClicked: (Channel) -> Unit,
    onChannelPlayDirect: (Channel) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(46.dp))
                Text("لا توجد قنوات مطابقة", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                Text("جرّب اختيار تصنيف آخر أو تحديث القائمة من الإعدادات.", style = LiquidTypography.BodySmall, color = LiquidColors.TextSecondary)
            }
        }
        return
    }

    when (viewMode) {
        ViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize(),
            ) {
                items(channels, key = { it.id }) { channel ->
                    LiveTVGridCard(
                        channel = channel,
                        isSelected = channel.id == selectedChannelId,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelPlayDirect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel.id) },
                    )
                }
            }
        }
        ViewMode.LIST -> {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
                    .fillMaxSize()
                    .testTag("live_channels_list"),
            ) {
                items(channels.size, key = { idx -> channels[idx].id }) { idx ->
                    val channel = channels[idx]
                    LiveTVListRow(
                        index = idx + 1,
                        channel = channel,
                        isSelected = channel.id == selectedChannelId,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelClicked(channel) },
                        onPlayClick = { onChannelPlayDirect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChannelInfoEpgPane(
    channel: Channel?,
    epg: ChannelEpgSummary,
    onPlayClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 22.dp,
        glassAlpha = 0.16f,
        borderAlpha = 0.24f,
    ) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "اختر قناة لعرض معلوماتها ودليل البرامج",
                    style = LiquidTypography.BodySmall,
                    color = LiquidColors.TextSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@LiquidGlassSurface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Uncropped channel logo showcase
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = 0.22f),
                                LiquidColors.GlassTertiary,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.AccentBlueLight, modifier = Modifier.size(48.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    LiveBadge()
                }
                IconButton(
                    onClick = { onFavoriteToggle(channel) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (channel.isFavorite) LiquidColors.LiveRed else Color.White,
                    )
                }
            }

            // Channel title & metadata
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleLarge,
                    color = LiquidColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    channel.groupTitle?.takeIf { it.isNotBlank() }?.let { group ->
                        Text(group, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlueLight)
                    }
                    channel.country?.takeIf { it.isNotBlank() }?.let { country ->
                        Text("• $country", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                    }
                    channel.language?.takeIf { it.isNotBlank() }?.let { lang ->
                        Text("• $lang", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                    }
                }
            }

            // Play CTA
            Button(
                onClick = { onPlayClick(channel) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiquidColors.AccentBlue,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("live_preview_play_button"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("تشغيل القناة", style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold))
            }

            // EPG Current & Next
            LiquidGlassSurface(
                cornerRadius = 16.dp,
                glassAlpha = 0.12f,
                borderAlpha = 0.20f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "دليل البرامج المباشر",
                        style = LiquidTypography.TitleSmall,
                        color = LiquidColors.AccentBlueLight,
                    )

                    val current = epg.current
                    val next = epg.next

                    if (current == null && next == null) {
                        Text(
                            text = "لا تتوفر بيانات دليل البرامج لهذه القناة حالياً",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextTertiary,
                        )
                    } else {
                        current?.let { prog ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("يُعرض الآن", style = LiquidTypography.LabelSmall, color = LiquidColors.AccentCyan)
                                    Text(
                                        text = formatEpgTimeRange(prog.startTimeMs, prog.endTimeMs),
                                        style = LiquidTypography.LabelSmall,
                                        color = LiquidColors.TextTertiary,
                                    )
                                }
                                Text(
                                    text = prog.title,
                                    style = LiquidTypography.TitleSmall,
                                    color = LiquidColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                prog.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = LiquidTypography.BodySmall,
                                        color = LiquidColors.TextSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { calculateEpgProgress(prog.startTimeMs, prog.endTimeMs) },
                                    color = LiquidColors.AccentBlueLight,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                        }

                        next?.let { prog ->
                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("التالي", style = LiquidTypography.LabelSmall, color = LiquidColors.AccentVioletLight)
                                    Text(
                                        text = formatEpgTimeRange(prog.startTimeMs, prog.endTimeMs),
                                        style = LiquidTypography.LabelSmall,
                                        color = LiquidColors.TextTertiary,
                                    )
                                }
                                Text(
                                    text = prog.title,
                                    style = LiquidTypography.BodySmall,
                                    color = LiquidColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
private fun CompactSelectedEpgStrip(
    channel: Channel,
    epg: ChannelEpgSummary,
    onPlayClick: () -> Unit,
) {
    val current = epg.current ?: return
    LiquidGlassSurface(
        cornerRadius = 14.dp,
        glassAlpha = 0.16f,
        borderColor = LiquidColors.AccentBlue.copy(alpha = 0.5f),
        onClick = onPlayClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveBadge()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${channel.name} • ${current.title}",
                    style = LiquidTypography.LabelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatEpgTimeRange(current.startTimeMs, current.endTimeMs),
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.TextTertiary,
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل", tint = LiquidColors.AccentBlueLight)
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .heightIn(min = 42.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 21.dp,
        glassAlpha = if (selected) 0.35f else if (isFocused) 0.22f else 0.12f,
        borderAlpha = if (isFocused) 0.80f else if (selected) 0.55f else 0.18f,
        borderColor = if (selected || isFocused) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelMedium,
                color = if (selected) Color.White else LiquidColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveTVGridCard(
    channel: Channel,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused || isSelected) 0.30f else 0.15f,
        borderAlpha = if (isFocused || isSelected) 0.85f else 0.22f,
        borderColor = if (isFocused || isSelected) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.22f else 0.08f),
                                LiquidColors.GlassTertiary,
                            ),
                        ),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.45f), modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    LiveBadge()
                }
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "المفضلة",
                        tint = if (channel.isFavorite) LiquidColors.LiveRed else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
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
                    color = if (isFocused || isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveTVListRow(
    index: Int,
    channel: Channel,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val displayNumber = String.format(Locale.US, "%03d", channel.channelNumber ?: index)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.30f else if (isSelected) 0.22f else 0.12f,
        borderAlpha = if (isFocused) 0.85f else if (isSelected) 0.55f else 0.18f,
        borderColor = if (isFocused || isSelected) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Channel Number Badge
            Box(
                modifier = Modifier
                    .widthIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayNumber,
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused || isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
                )
            }

            // Uncropped Logo Container
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.25f else 0.12f),
                                LiquidColors.GlassTertiary,
                            ),
                        ),
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.45f), modifier = Modifier.size(22.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    channel.groupTitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlueLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    channel.language?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary, maxLines = 1)
                    }
                    channel.country?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary, maxLines = 1)
                    }
                }
            }

            LiveBadge()

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = if (channel.isFavorite) LiquidColors.LiveRed else LiquidColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(LiquidColors.AccentBlue.copy(alpha = 0.20f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "تشغيل القناة",
                    tint = LiquidColors.AccentBlueLight,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

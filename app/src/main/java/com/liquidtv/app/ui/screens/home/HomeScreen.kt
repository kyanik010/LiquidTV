package com.liquidtv.app.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onChannelClick: (String, MediaType) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSubscriptionsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.featuredChannel?.id) {
        if (state.featuredChannel != null) {
            try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isCompact = maxWidth < 600.dp
            val hPad = if (isCompact) 16.dp else 40.dp
            val topNavSpace = if (isCompact) 76.dp else 88.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topNavSpace),
                contentPadding = PaddingValues(bottom = if (isCompact) 96.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 18.dp else 24.dp),
            ) {
                // Header
                item {
                    HomeHeader(
                        subscription = state.activeSubscription,
                        clockVisible = state.clockVisible,
                        isCompact = isCompact,
                        onSubscriptionsClick = onSubscriptionsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad)
                            .padding(top = 8.dp),
                    )
                }

                // Hero panel
                state.featuredChannel?.let { channel ->
                    item {
                        HeroPanel(
                            channel = channel,
                            epg = state.featuredEpg,
                            isCompact = isCompact,
                            onWatchClick = { onChannelClick(channel.id, channel.mediaType) },
                            onFavoriteClick = { viewModel.toggleFavorite(channel.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = hPad),
                            focusRequester = initialFocusRequester,
                        )
                    }
                }

                // Loading state
                if (state.isLoading && state.allChannels.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(32.dp))
                            LoadingIndicator(
                                message = state.loadingMessage ?: "جارٍ مزامنة البث والقوائم…",
                                progress = state.loadingProgress,
                            )
                        }
                    }
                } else if (state.allChannels.isEmpty() && state.featuredMovies.isEmpty() && state.featuredSeries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = hPad, vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidGlassSurface(
                                cornerRadius = 24.dp,
                                glassAlpha = 0.18f,
                                borderAlpha = 0.35f,
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LiveTv,
                                        contentDescription = null,
                                        tint = LiquidColors.AccentBlueLight,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = "لا توجد قنوات محمّلة لهذا الاشتراك",
                                        style = LiquidTypography.TitleMedium,
                                        color = LiquidColors.TextPrimary,
                                    )
                                    Text(
                                        text = state.error ?: "قم بتحديث الاشتراك النشط أو إدارة حسابات IPTV الخاصة بك.",
                                        style = LiquidTypography.BodySmall,
                                        color = LiquidColors.TextSecondary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        GlassButton(
                                            text = "تحديث",
                                            icon = Icons.Filled.Refresh,
                                            isPrimary = true,
                                            onClick = { viewModel.refreshPlaylists() },
                                        )
                                        GlassButton(
                                            text = "الاشتراكات",
                                            icon = Icons.Filled.Subscriptions,
                                            isPrimary = false,
                                            onClick = onSubscriptionsClick,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Continue Watching
                if (state.recentlyWatched.isNotEmpty()) {
                    item {
                        ChannelRow(
                            title = "متابعة المشاهدة",
                            channels = state.recentlyWatched,
                            horizontalPadding = hPad,
                            onChannelClick = { ch -> onChannelClick(ch.id, ch.mediaType) },
                        )
                    }
                }

                // Favorites
                if (state.favoriteChannels.isNotEmpty()) {
                    item {
                        ChannelRow(
                            title = "المفضلة",
                            channels = state.favoriteChannels,
                            horizontalPadding = hPad,
                            onChannelClick = { ch -> onChannelClick(ch.id, ch.mediaType) },
                        )
                    }
                }

                // Popular Categories
                if (state.popularCategories.isNotEmpty()) {
                    item {
                        CategoryRow(
                            title = "تصفح التصنيفات",
                            categories = state.popularCategories,
                            horizontalPadding = hPad,
                            onCategoryClick = onCategoryClick,
                        )
                    }
                }

                // All Channels preview
                if (state.allChannels.isNotEmpty()) {
                    item {
                        ChannelRow(
                            title = "القنوات المباشرة",
                            channels = state.allChannels,
                            horizontalPadding = hPad,
                            onChannelClick = { ch -> onChannelClick(ch.id, ch.mediaType) },
                        )
                    }
                }

                // Movies VOD preview
                if (state.featuredMovies.isNotEmpty()) {
                    item {
                        ChannelRow(
                            title = "الأفلام",
                            channels = state.featuredMovies,
                            horizontalPadding = hPad,
                            onChannelClick = { ch -> onChannelClick(ch.id, MediaType.MOVIE) },
                        )
                    }
                }

                // Series preview
                if (state.featuredSeries.isNotEmpty()) {
                    item {
                        SeriesRow(
                            title = "المسلسلات",
                            series = state.featuredSeries,
                            horizontalPadding = hPad,
                            onSeriesClick = onSeriesClick,
                        )
                    }
                }
            }

            // Error state
            state.error?.let { error ->
                GlassErrorBanner(
                    message = error,
                    onRetry = { viewModel.refreshPlaylists() },
                    onSubscriptions = onSubscriptionsClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = hPad, vertical = 20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeader(
    subscription: Subscription?,
    clockVisible: Boolean,
    isCompact: Boolean,
    onSubscriptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Calendar.getInstance() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("EEEE، d MMMM", Locale("ar")) }

    var currentTime by remember { mutableStateOf(timeFormat.format(now.time)) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Calendar.getInstance().time)
            kotlinx.coroutines.delay(30_000)
        }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 44.dp else 52.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(LiquidColors.AccentBlue.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 38.dp else 42.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                            ),
                            CircleShape,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    text = "LIQUID TV",
                    style = (if (isCompact) LiquidTypography.HeadlineMedium else LiquidTypography.HeadlineLarge).copy(
                        brush = Brush.linearGradient(
                            listOf(
                                LiquidColors.TextPrimary,
                                LiquidColors.AccentBlueLight,
                                LiquidColors.AccentVioletLight,
                            )
                        )
                    ),
                )
                Text(
                    text = "الإصدار ${BuildConfig.VERSION_NAME} • تجربة IPTV زجاجية",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.AccentBlue.copy(alpha = 0.75f),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            subscription?.let { sub ->
                val statusColor = when (sub.connectionStatus) {
                    SubscriptionStatus.CONNECTED -> LiquidColors.SuccessGreen
                    SubscriptionStatus.EXPIRED -> LiquidColors.WarningAmber
                    SubscriptionStatus.ERROR -> LiquidColors.ErrorRed
                    SubscriptionStatus.DISCONNECTED -> LiquidColors.TextTertiary
                }
                val subInteraction = remember { MutableInteractionSource() }
                val subFocused by subInteraction.collectIsFocusedAsState()
                LiquidGlassSurface(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(subInteraction, null, onClick = onSubscriptionsClick)
                        .focusable(interactionSource = subInteraction)
                        .testTag("home_subscription_pill"),
                    cornerRadius = 24.dp,
                    glassAlpha = if (subFocused) 0.28f else 0.16f,
                    borderAlpha = if (subFocused) 0.8f else 0.3f,
                    isFocused = subFocused,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape),
                        )
                        Text(
                            text = sub.name,
                            style = LiquidTypography.LabelMedium,
                            color = LiquidColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "إدارة الاشتراكات",
                            tint = LiquidColors.AccentBlueLight,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (clockVisible && !isCompact) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(currentTime, style = LiquidTypography.HeadlineMedium, color = LiquidColors.TextPrimary)
                    Text(dateFormat.format(Calendar.getInstance().time), style = LiquidTypography.BodySmall, color = LiquidColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(
    channel: Channel,
    epg: ChannelEpgSummary,
    isCompact: Boolean,
    onWatchClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    val watchInteraction = remember { MutableInteractionSource() }

    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 24.dp,
        glassAlpha = 0.18f,
        borderAlpha = 0.3f,
        highlightAlpha = 0.55f,
        accentGlowColor = LiquidColors.AccentBlue,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.0f),
                        0.4f to Color.Black.copy(alpha = 0.0f),
                        1.0f to Color.Black.copy(alpha = 0.35f),
                    )
                ),
        ) {
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeroLogoBox(channel = channel, size = 76.dp)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                LiveBadge()
                                channel.groupTitle?.let { group ->
                                    HeroCategoryPill(group)
                                }
                            }
                            Text(
                                text = channel.name,
                                style = LiquidTypography.TitleLarge,
                                color = LiquidColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    epg.current?.let { nowProg ->
                        Text(
                            text = "الآن: ${nowProg.title}",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.AccentBlueLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            text = "شاهد الآن",
                            icon = Icons.Filled.PlayArrow,
                            isPrimary = true,
                            onClick = onWatchClick,
                            interactionSource = watchInteraction,
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                        GlassButton(
                            text = if (channel.isFavorite) "في المفضلة" else "إضافة للمفضلة",
                            icon = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            isPrimary = false,
                            onClick = onFavoriteClick,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 270.dp, max = 340.dp)
                        .padding(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroLogoBox(channel = channel, size = 160.dp)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge()
                            channel.groupTitle?.let { group ->
                                HeroCategoryPill(group)
                            }
                        }
                        Text(
                            text = channel.name,
                            style = LiquidTypography.DisplayMedium,
                            color = LiquidColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        epg.current?.let { nowProg ->
                            Text(
                                text = "الآن: ${nowProg.title}",
                                style = LiquidTypography.BodySmall,
                                color = LiquidColors.AccentBlueLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            channel.country?.let { InfoChip(it) }
                            channel.language?.let { InfoChip(it) }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassButton(
                                text = "شاهد الآن",
                                icon = Icons.Filled.PlayArrow,
                                isPrimary = true,
                                onClick = onWatchClick,
                                interactionSource = watchInteraction,
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                            GlassButton(
                                text = if (channel.isFavorite) "في المفضلة" else "إضافة للمفضلة",
                                icon = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                isPrimary = false,
                                onClick = onFavoriteClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroLogoBox(channel: Channel, size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size + 24.dp)
                .background(
                    Brush.radialGradient(
                        listOf(LiquidColors.AccentBlue.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    Brush.radialGradient(
                        listOf(
                            LiquidColors.AccentBlue.copy(alpha = 0.18f),
                            LiquidColors.GlassPrimary,
                        )
                    ),
                    RoundedCornerShape(22.dp),
                )
                .border(1.dp, LiquidColors.GlassBorder, RoundedCornerShape(22.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.44f))
            }
        }
    }
}

@Composable
private fun HeroCategoryPill(group: String) {
    Box(
        modifier = Modifier
            .background(LiquidColors.AccentBlue.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
            .border(1.dp, LiquidColors.AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = group,
            style = LiquidTypography.LabelMedium,
            color = LiquidColors.AccentBlueLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .background(LiquidColors.GlassPrimary, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = LiquidTypography.LabelSmall, color = LiquidColors.TextSecondary)
    }
}

@Composable
private fun GlassButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "btn_scale")

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .then(
                if (isPrimary) Modifier.background(
                    Brush.linearGradient(listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)),
                    RoundedCornerShape(50.dp),
                ) else Modifier.background(LiquidColors.GlassPrimary, RoundedCornerShape(50.dp))
                    .border(1.dp, if (isFocused) LiquidColors.GlassBorderFocused else LiquidColors.GlassBorder, RoundedCornerShape(50.dp))
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LiquidColors.TextPrimary, modifier = Modifier.size(18.dp))
            Text(text, style = LiquidTypography.LabelLarge, color = LiquidColors.TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun ChannelRow(
    title: String,
    channels: List<Channel>,
    horizontalPadding: Dp,
    onChannelClick: (Channel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Text(
                text = title,
                style = LiquidTypography.HeadlineSmall,
                color = LiquidColors.TextPrimary,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                MiniChannelCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                )
            }
        }
    }
}

@Composable
private fun SeriesRow(
    title: String,
    series: List<SeriesItem>,
    horizontalPadding: Dp,
    onSeriesClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(listOf(LiquidColors.AccentViolet, LiquidColors.AccentCyan)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Text(
                text = title,
                style = LiquidTypography.HeadlineSmall,
                color = LiquidColors.TextPrimary,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(series, key = { it.id }) { item ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                LiquidGlassSurface(
                    modifier = Modifier
                        .width(156.dp)
                        .height(235.dp)
                        .clickable(interactionSource = interactionSource, indication = null) { onSeriesClick(item.id) }
                        .focusable(interactionSource = interactionSource),
                    cornerRadius = 18.dp,
                    glassAlpha = if (isFocused) 0.30f else 0.16f,
                    borderAlpha = if (isFocused) 0.85f else 0.25f,
                    isFocused = isFocused,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                                .background(LiquidColors.GlassTertiary),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!item.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(Icons.Filled.VideoLibrary, null, tint = LiquidColors.AccentViolet.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
                            }
                        }
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = item.name,
                                style = LiquidTypography.TitleSmall,
                                color = LiquidColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.categoryName?.let {
                                Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentVioletLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    categories: List<Category>,
    horizontalPadding: Dp,
    onCategoryClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(listOf(LiquidColors.AccentCyan, LiquidColors.AccentBlue)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Text(title, style = LiquidTypography.HeadlineSmall, color = LiquidColors.TextPrimary)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                MiniCategoryCard(category = category, onClick = { onCategoryClick(category.name) })
            }
        }
    }
}

@Composable
private fun MiniChannelCard(
    channel: Channel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .width(170.dp)
            .height(195.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.30f else 0.16f,
        borderAlpha = if (isFocused) 0.85f else 0.25f,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.20f else 0.08f),
                                LiquidColors.GlassTertiary,
                            )
                        ),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    )
                    .padding(10.dp),
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
                    Icon(Icons.Filled.LiveTv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                }
                if (channel.mediaType == MediaType.LIVE) {
                    LiveBadge(modifier = Modifier.align(Alignment.TopStart))
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(LiquidColors.AccentViolet.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("فيلم", style = LiquidTypography.LabelSmall, color = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(channel.name, style = LiquidTypography.TitleSmall, color = LiquidColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                channel.groupTitle?.let {
                    Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MiniCategoryCard(category: Category, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val colors = getCategoryCardColors(category.name)

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.first.copy(alpha = if (isFocused) 0.55f else 0.30f), colors.second.copy(alpha = if (isFocused) 0.45f else 0.20f))
                )
            )
            .border(
                1.dp,
                if (isFocused) colors.first.copy(alpha = 0.9f) else colors.first.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isFocused) 0.18f else 0.08f), Color.Transparent)
                    )
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(category.name, style = LiquidTypography.TitleSmall, color = LiquidColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${category.channelCount} قناة", style = LiquidTypography.LabelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

private fun getCategoryCardColors(name: String): Pair<Color, Color> = when {
    name.contains("sport", ignoreCase = true) || name.contains("رياض") -> LiquidColors.CategorySports to LiquidColors.CategorySportsEnd
    name.contains("news", ignoreCase = true) || name.contains("اخبار") || name.contains("أخبار") -> LiquidColors.CategoryNews to LiquidColors.CategoryNewsEnd
    name.contains("movie", ignoreCase = true) || name.contains("film", ignoreCase = true) || name.contains("cinema", ignoreCase = true) || name.contains("أفلام") -> LiquidColors.CategoryMovies to LiquidColors.CategoryMoviesEnd
    name.contains("music", ignoreCase = true) || name.contains("موسيق") -> LiquidColors.CategoryMusic to LiquidColors.CategoryMusicEnd
    name.contains("kid", ignoreCase = true) || name.contains("child", ignoreCase = true) || name.contains("cartoon", ignoreCase = true) || name.contains("أطفال") -> LiquidColors.CategoryKids to LiquidColors.CategoryKidsEnd
    name.contains("doc", ignoreCase = true) || name.contains("وثائق") -> LiquidColors.CategoryDoc to LiquidColors.CategoryDocEnd
    name.contains("entertain", ignoreCase = true) || name.contains("general", ignoreCase = true) || name.contains("ترفيه") -> LiquidColors.CategoryEnt to LiquidColors.CategoryEntEnd
    else -> LiquidColors.AccentBlue to LiquidColors.AccentViolet
}

@Composable
private fun LoadingIndicator(message: String, progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            progress = { if (progress > 0f) progress else 0f },
            color = LiquidColors.AccentBlue,
            modifier = Modifier.size(48.dp),
        )
        Text(message, style = LiquidTypography.BodyMedium, color = LiquidColors.TextSecondary)
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                color = LiquidColors.AccentBlue,
                trackColor = LiquidColors.GlassPrimary,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun GlassErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 14.dp,
        glassAlpha = 0.25f,
        borderAlpha = 0.4f,
        glassColor = LiquidColors.ErrorRed,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = LiquidColors.ErrorRed)
            Text(message, style = LiquidTypography.BodyMedium, color = LiquidColors.TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onSubscriptions) {
                Text("الحسابات", color = LiquidColors.TextPrimary)
            }
            TextButton(onClick = onRetry) {
                Text("إعادة المحاولة", color = LiquidColors.AccentBlueLight)
            }
        }
    }
}

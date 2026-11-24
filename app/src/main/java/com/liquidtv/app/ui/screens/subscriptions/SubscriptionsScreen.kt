package com.liquidtv.app.ui.screens.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.localizedSubscriptionStatusAr
import com.liquidtv.app.ui.utils.localizedSubscriptionTypeAr
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsScreen(
    onAddSubscription: () -> Unit,
    onEditSubscription: (String) -> Unit,
    onActivated: () -> Unit,
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }

    BackHandler { onBack() }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val colors = AppTheme.colors
            val isCompact = maxWidth < 600.dp
            val hPad = if (isCompact) 16.dp else 32.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.82f))
                                .border(1.dp, colors.border.copy(alpha = 0.55f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = colors.text,
                            )
                        }
                        Column {
                            Text(
                                text = "اشتراكات IPTV",
                                style = LiquidTypography.HeadlineMedium.copy(
                                    brush = Brush.linearGradient(
                                        listOf(colors.text, colors.accentText),
                                    ),
                                ),
                            )
                            Text(
                                text = "إدارة حسابات Xtream وملفات M3U",
                                style = LiquidTypography.BodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    GlassActionPill(
                        text = "إضافة اشتراك",
                        icon = Icons.Filled.Add,
                        isPrimary = true,
                        onClick = onAddSubscription,
                        modifier = Modifier.testTag("add_subscription_button"),
                    )
                }

                state.message?.let { msg ->
                    LiquidGlassSurface(
                        cornerRadius = 14.dp,
                        glassAlpha = 0.22f,
                        borderAlpha = 0.45f,
                        glassColor = colors.success,
                        borderColor = colors.success,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(msg, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearFeedback) {
                                Text("إغلاق", color = colors.accentText)
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    LiquidGlassSurface(
                        cornerRadius = 14.dp,
                        glassAlpha = 0.25f,
                        borderAlpha = 0.5f,
                        glassColor = colors.danger,
                        borderColor = colors.danger,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(err, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearFeedback) {
                                Text("إغلاق", color = colors.text)
                            }
                        }
                    }
                }

                if (state.subscriptions.isEmpty()) {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        glassAlpha = 0.18f,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Subscriptions,
                                contentDescription = null,
                                tint = colors.accentText,
                                modifier = Modifier.size(48.dp),
                            )
                            Text("لا توجد اشتراكات محفوظة", style = LiquidTypography.TitleMedium, color = colors.text)
                            Text(
                                "أضف حساب Xtream Codes أو رابط قائمة M3U لتحميل القنوات والمحتوى.",
                                style = LiquidTypography.BodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(state.subscriptions, key = { it.id }) { sub ->
                            SubscriptionCard(
                                subscription = sub,
                                isBusy = state.busySubscriptionId == sub.id ||
                                    (state.syncInfo.isLoading && state.syncInfo.subscriptionId == sub.id),
                                onActivate = {
                                    viewModel.activate(sub.id)
                                    onActivated()
                                },
                                onSync = { viewModel.sync(sub.id) },
                                onEdit = { onEditSubscription(sub.id) },
                                onDelete = { pendingDelete = sub },
                            )
                        }
                    }
                }
            }

            pendingDelete?.let { sub ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("حذف الاشتراك", color = colors.text) },
                    text = {
                        Text(
                            "هل تريد حذف \"${sub.name}\" وجميع قنواته وتصنيفاته ومفضلته ودليل البرامج المخزن؟",
                            color = colors.textSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.delete(sub.id)
                                pendingDelete = null
                            },
                        ) {
                            Text("حذف", color = colors.danger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("إلغاء", color = colors.textSecondary)
                        }
                    },
                    containerColor = colors.panel,
                    titleContentColor = colors.text,
                    textContentColor = colors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    isBusy: Boolean,
    onActivate: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val statusColor = when (subscription.connectionStatus) {
        SubscriptionStatus.CONNECTED -> colors.success
        SubscriptionStatus.EXPIRED -> colors.warning
        SubscriptionStatus.ERROR -> colors.danger
        SubscriptionStatus.DISCONNECTED -> colors.muted
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subscription_card_${subscription.id}"),
        cornerRadius = 20.dp,
        glassColor = if (subscription.isActive) colors.selected else colors.card,
        glassAlpha = if (subscription.isActive) 0.90f else 0.78f,
        borderAlpha = if (subscription.isActive) 0.85f else 0.40f,
        borderColor = if (subscription.isActive) colors.accent else colors.border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = subscription.name,
                            style = LiquidTypography.TitleMedium,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BadgeChip(
                            text = localizedSubscriptionTypeAr(subscription.type),
                            color = colors.accentText,
                        )
                        if (subscription.isActive) {
                            BadgeChip(
                                text = "نشط",
                                color = colors.success,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subscription.serverUrl,
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subscription.username?.let { user ->
                        Text(
                            text = "المستخدم: $user",
                            style = LiquidTypography.LabelSmall,
                            color = colors.muted,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                BadgeChip(
                    text = localizedSubscriptionStatusAr(subscription.connectionStatus),
                    color = statusColor,
                )
            }

            // Xtream account metadata (only rendered when actually provided by server)
            if (subscription.type == SubscriptionType.XTREAM &&
                (subscription.accountStatus != null ||
                    subscription.expDateEpochSec != null ||
                    subscription.maxConnections != null)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subscription.accountStatus?.let {
                        Text("الحالة: $it", style = LiquidTypography.LabelSmall, color = colors.textSecondary)
                    }
                    subscription.expDateEpochSec?.let { exp ->
                        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("ar")).format(Date(exp * 1000L))
                        Text("ينتهي في: $dateText", style = LiquidTypography.LabelSmall, color = colors.textSecondary)
                    }
                    if (subscription.activeConnections != null || subscription.maxConnections != null) {
                        Text(
                            "الاتصالات: ${subscription.activeConnections ?: "؟"} / ${subscription.maxConnections ?: "؟"}",
                            style = LiquidTypography.LabelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            subscription.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = LiquidTypography.BodySmall,
                    color = colors.danger,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!subscription.isActive) {
                    GlassActionPill(
                        text = "تفعيل الاشتراك",
                        icon = Icons.Filled.CheckCircle,
                        isPrimary = true,
                        enabled = !isBusy,
                        onClick = onActivate,
                    )
                }
                GlassActionPill(
                    text = if (isBusy) "جارٍ التحديث…" else "تحديث",
                    icon = Icons.Filled.Refresh,
                    isPrimary = false,
                    enabled = !isBusy,
                    onClick = onSync,
                )
                GlassActionPill(
                    text = "تعديل",
                    icon = Icons.Filled.Edit,
                    isPrimary = false,
                    enabled = !isBusy,
                    onClick = onEdit,
                )
                GlassActionPill(
                    text = "حذف",
                    icon = Icons.Filled.Delete,
                    isPrimary = false,
                    enabled = !isBusy,
                    tint = colors.danger,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun GlassActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val resolvedTint = if (isPrimary) colors.onAccent else (tint ?: colors.text)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isPrimary -> colors.accent
                    isFocused -> colors.selected
                    else -> colors.surface.copy(alpha = 0.80f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else if (isPrimary) colors.border.copy(alpha = 0.5f) else colors.border,
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(18.dp))
            Text(text, style = LiquidTypography.LabelLarge, color = resolvedTint)
        }
    }
}

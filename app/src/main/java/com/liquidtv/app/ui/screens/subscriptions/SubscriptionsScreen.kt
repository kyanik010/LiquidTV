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
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import java.text.DateFormat
import java.util.Date

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Column {
                        Text(
                            text = "IPTV Subscriptions",
                            style = LiquidTypography.HeadlineMedium.copy(
                                brush = Brush.linearGradient(
                                    listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                                ),
                            ),
                        )
                        Text(
                            text = "Manage Xtream Codes and M3U accounts",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                        )
                    }
                }

                GlassActionPill(
                    text = "Add Subscription",
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
                    glassColor = LiquidColors.SuccessGreen,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(msg, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearFeedback) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }

            state.error?.let { err ->
                LiquidGlassSurface(
                    cornerRadius = 14.dp,
                    glassAlpha = 0.25f,
                    borderAlpha = 0.5f,
                    glassColor = LiquidColors.ErrorRed,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(err, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearFeedback) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }

            if (state.subscriptions.isEmpty()) {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.16f,
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
                            tint = LiquidColors.AccentBlueLight,
                            modifier = Modifier.size(48.dp),
                        )
                        Text("No IPTV Subscriptions Saved", style = LiquidTypography.TitleMedium, color = Color.White)
                        Text(
                            "Add an Xtream Codes account or M3U playlist URL to load channels.",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
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
                title = { Text("Delete Subscription", color = Color.White) },
                text = {
                    Text(
                        "Remove \"${sub.name}\" and all its cached channels, categories, favorites, and EPG data?",
                        color = LiquidColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(sub.id)
                            pendingDelete = null
                        },
                    ) {
                        Text("Delete", color = LiquidColors.ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = LiquidColors.TextSecondary)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    isBusy: Boolean,
    onActivate: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (subscription.connectionStatus) {
        SubscriptionStatus.CONNECTED -> LiquidColors.SuccessGreen
        SubscriptionStatus.EXPIRED -> LiquidColors.WarningAmber
        SubscriptionStatus.ERROR -> LiquidColors.ErrorRed
        SubscriptionStatus.DISCONNECTED -> LiquidColors.TextTertiary
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subscription_card_${subscription.id}"),
        cornerRadius = 20.dp,
        glassAlpha = if (subscription.isActive) 0.24f else 0.14f,
        borderAlpha = if (subscription.isActive) 0.65f else 0.22f,
        borderColor = if (subscription.isActive) LiquidColors.AccentBlueLight else Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = subscription.name,
                            style = LiquidTypography.TitleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BadgeChip(
                            text = subscription.type.name,
                            color = LiquidColors.AccentVioletLight,
                        )
                        if (subscription.isActive) {
                            BadgeChip(
                                text = "ACTIVE",
                                color = LiquidColors.AccentBlueLight,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subscription.serverUrl,
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subscription.username?.let { user ->
                        Text(
                            text = "User: $user",
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.TextTertiary,
                        )
                    }
                }

                BadgeChip(
                    text = subscription.connectionStatus.name,
                    color = statusColor,
                )
            }

            // Xtream account metadata (only rendered when actually provided by server)
            if (subscription.type == SubscriptionType.XTREAM &&
                (subscription.accountStatus != null ||
                    subscription.expDateEpochSec != null ||
                    subscription.maxConnections != null)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    subscription.accountStatus?.let {
                        Text("Status: $it", style = LiquidTypography.LabelSmall, color = LiquidColors.TextSecondary)
                    }
                    subscription.expDateEpochSec?.let { exp ->
                        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000L))
                        Text("Expires: $dateText", style = LiquidTypography.LabelSmall, color = LiquidColors.TextSecondary)
                    }
                    if (subscription.activeConnections != null || subscription.maxConnections != null) {
                        Text(
                            "Connections: ${subscription.activeConnections ?: "?"} / ${subscription.maxConnections ?: "?"}",
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.TextSecondary,
                        )
                    }
                }
            }

            subscription.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = LiquidTypography.BodySmall,
                    color = LiquidColors.ErrorRed,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!subscription.isActive) {
                    GlassActionPill(
                        text = "Use Subscription",
                        icon = Icons.Filled.CheckCircle,
                        isPrimary = true,
                        enabled = !isBusy,
                        onClick = onActivate,
                    )
                }
                GlassActionPill(
                    text = if (isBusy) "Syncing…" else "Refresh",
                    icon = Icons.Filled.Refresh,
                    isPrimary = false,
                    enabled = !isBusy,
                    onClick = onSync,
                )
                GlassActionPill(
                    text = "Edit",
                    icon = Icons.Filled.Edit,
                    isPrimary = false,
                    enabled = !isBusy,
                    onClick = onEdit,
                )
                GlassActionPill(
                    text = "Delete",
                    icon = Icons.Filled.Delete,
                    isPrimary = false,
                    enabled = !isBusy,
                    tint = LiquidColors.ErrorRed,
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
            .background(color.copy(alpha = 0.20f))
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
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isPrimary) {
                    Brush.horizontalGradient(listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isFocused) 0.22f else 0.10f),
                            Color.White.copy(alpha = if (isFocused) 0.22f else 0.10f),
                        ),
                    )
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
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
            Icon(icon, contentDescription = null, tint = if (isPrimary) Color.White else tint, modifier = Modifier.size(18.dp))
            Text(text, style = LiquidTypography.LabelLarge, color = if (isPrimary) Color.White else tint)
        }
    }
}

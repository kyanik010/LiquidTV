package com.liquidtv.app.ui.screens.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun LoginScreen(
    editingSubscriptionId: String? = null,
    allowBack: Boolean = false,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val primaryInputRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(editingSubscriptionId) {
        viewModel.loadForEdit(editingSubscriptionId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        try {
            primaryInputRequester.requestFocus()
        } catch (_: Exception) {}
    }

    if (allowBack) {
        BackHandler(enabled = !state.isConnecting) {
            onBack()
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp),
                cornerRadius = 28.dp,
                glassAlpha = 0.20f,
                borderAlpha = 0.35f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (allowBack) {
                                IconButton(
                                    onClick = { if (!state.isConnecting) onBack() },
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
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LiveTv,
                                    contentDescription = "LIQUID TV",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = if (editingSubscriptionId != null) "Edit Subscription" else "LIQUID TV",
                                    style = LiquidTypography.HeadlineMedium.copy(
                                        brush = Brush.linearGradient(
                                            listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                                        ),
                                    ),
                                )
                                Text(
                                    text = "Sign in with Xtream Codes or M3U Playlist",
                                    style = LiquidTypography.BodySmall,
                                    color = LiquidColors.TextSecondary,
                                )
                            }
                        }
                    }

                    // Method selector tabs (Liquid Glass segmented pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LoginModeTab(
                            title = "Xtream Codes",
                            icon = Icons.Filled.Dns,
                            selected = state.selectedType == SubscriptionType.XTREAM,
                            enabled = !state.isConnecting,
                            onClick = { viewModel.selectType(SubscriptionType.XTREAM) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_xtream"),
                        )
                        LoginModeTab(
                            title = "M3U Playlist",
                            icon = Icons.Filled.PlaylistPlay,
                            selected = state.selectedType == SubscriptionType.M3U,
                            enabled = !state.isConnecting,
                            onClick = { viewModel.selectType(SubscriptionType.M3U) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_m3u"),
                        )
                    }

                    // Optional display name
                    GlassTextField(
                        value = state.displayName,
                        onValueChange = viewModel::setDisplayName,
                        label = "Playlist / Account Name (Optional)",
                        placeholder = "e.g. Living Room TV",
                        leadingIcon = Icons.Filled.Label,
                        enabled = !state.isConnecting,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.testTag("input_subscription_name"),
                    )

                    if (state.selectedType == SubscriptionType.XTREAM) {
                        GlassTextField(
                            value = state.serverUrl,
                            onValueChange = viewModel::setServerUrl,
                            label = "Server URL (HTTP or HTTPS)",
                            placeholder = "http://provider.example.com:8080",
                            leadingIcon = Icons.Filled.Language,
                            enabled = !state.isConnecting,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            modifier = Modifier
                                .focusRequester(primaryInputRequester)
                                .testTag("input_xtream_url"),
                        )

                        GlassTextField(
                            value = state.username,
                            onValueChange = viewModel::setUsername,
                            label = "Username",
                            placeholder = "Enter username",
                            leadingIcon = Icons.Filled.Person,
                            enabled = !state.isConnecting,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.testTag("input_xtream_username"),
                        )

                        GlassTextField(
                            value = state.password,
                            onValueChange = viewModel::setPassword,
                            label = "Password",
                            placeholder = "Enter password",
                            leadingIcon = Icons.Filled.Lock,
                            enabled = !state.isConnecting,
                            visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            onDone = { viewModel.connect() },
                            trailingContent = {
                                IconButton(
                                    onClick = viewModel::togglePasswordVisibility,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("toggle_password_visibility"),
                                ) {
                                    Icon(
                                        imageVector = if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (state.showPassword) "Hide password" else "Show password",
                                        tint = LiquidColors.TextSecondary,
                                    )
                                }
                            },
                            modifier = Modifier.testTag("input_xtream_password"),
                        )
                    } else {
                        GlassTextField(
                            value = state.m3uUrl,
                            onValueChange = viewModel::setM3uUrl,
                            label = "M3U / M3U8 Playlist URL",
                            placeholder = "http://provider.example.com/get.php?...",
                            leadingIcon = Icons.Filled.Link,
                            enabled = !state.isConnecting,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            onDone = { viewModel.connect() },
                            modifier = Modifier
                                .focusRequester(primaryInputRequester)
                                .testTag("input_m3u_url"),
                        )

                        // Optional HTTP Headers toggle for M3U
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.isConnecting) { viewModel.toggleAdvancedHeaders() }
                                .focusable()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "HTTP Headers (User-Agent / Referer)",
                                style = LiquidTypography.LabelLarge,
                                color = LiquidColors.AccentBlueLight,
                            )
                            Icon(
                                imageVector = if (state.showAdvancedHeaders) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Toggle HTTP headers",
                                tint = LiquidColors.AccentBlueLight,
                            )
                        }

                        AnimatedVisibility(visible = state.showAdvancedHeaders) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassTextField(
                                    value = state.userAgent,
                                    onValueChange = viewModel::setUserAgent,
                                    label = "Custom User-Agent (Optional)",
                                    placeholder = "LiquidTV/1.1 (Android TV)",
                                    leadingIcon = Icons.Filled.Devices,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    modifier = Modifier.testTag("input_m3u_user_agent"),
                                )
                                GlassTextField(
                                    value = state.referer,
                                    onValueChange = viewModel::setReferer,
                                    label = "Referer Header (Optional)",
                                    placeholder = "http://example.com/",
                                    leadingIcon = Icons.Filled.Http,
                                    enabled = !state.isConnecting,
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                    onDone = { viewModel.connect() },
                                    modifier = Modifier.testTag("input_m3u_referer"),
                                )
                            }
                        }
                    }

                    // Connection status / progress
                    AnimatedVisibility(visible = state.isConnecting) {
                        LiquidGlassSurface(
                            cornerRadius = 16.dp,
                            glassAlpha = 0.16f,
                            borderAlpha = 0.3f,
                            glassColor = LiquidColors.AccentBlue,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = LiquidColors.AccentBlueLight,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = state.statusMessage ?: "Connecting…",
                                        style = LiquidTypography.BodySmall,
                                        color = LiquidColors.TextPrimary,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress.coerceIn(0.05f, 1f) },
                                    color = LiquidColors.AccentBlueLight,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }

                    // Error message
                    AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
                        LiquidGlassSurface(
                            modifier = Modifier.testTag("login_error_banner"),
                            cornerRadius = 14.dp,
                            glassAlpha = 0.25f,
                            borderAlpha = 0.55f,
                            glassColor = LiquidColors.ErrorRed,
                            borderColor = LiquidColors.ErrorRed,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = LiquidColors.ErrorRed,
                                )
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    style = LiquidTypography.BodySmall,
                                    color = LiquidColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Connect Button
                    val connectInteraction = remember { MutableInteractionSource() }
                    val connectFocused by connectInteraction.collectIsFocusedAsState()
                    val connectScale by animateFloatAsState(
                        targetValue = if (connectFocused) 1.03f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
                        label = "connect_btn_scale",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .scale(connectScale)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    if (state.isConnecting) {
                                        listOf(
                                            LiquidColors.AccentBlue.copy(alpha = 0.45f),
                                            LiquidColors.AccentViolet.copy(alpha = 0.45f),
                                        )
                                    } else {
                                        listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                                    },
                                ),
                            )
                            .border(
                                width = if (connectFocused) 2.5.dp else 1.dp,
                                color = if (connectFocused) Color.White else Color.White.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(50),
                            )
                            .clickable(
                                interactionSource = connectInteraction,
                                indication = null,
                                enabled = !state.isConnecting,
                                onClick = viewModel::connect,
                            )
                            .focusable(interactionSource = connectInteraction)
                            .testTag("connect_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.isConnecting) "Connecting…" else if (editingSubscriptionId != null) "Save & Reconnect" else "Connect & Sync",
                            style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginModeTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
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
                when {
                    selected -> Brush.horizontalGradient(
                        listOf(
                            LiquidColors.AccentBlue.copy(alpha = 0.45f),
                            LiquidColors.AccentViolet.copy(alpha = 0.35f),
                        ),
                    )
                    else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                width = if (isFocused) 2.dp else if (selected) 1.dp else 0.dp,
                color = if (isFocused) Color.White else if (selected) LiquidColors.AccentBlueLight else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) Color.White else LiquidColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = LiquidTypography.LabelLarge,
                color = if (selected) Color.White else LiquidColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, style = LiquidTypography.LabelMedium) },
        placeholder = { Text(placeholder, color = LiquidColors.TextDisabled) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = LiquidColors.AccentBlueLight,
            )
        },
        trailingIcon = trailingContent,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() },
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LiquidColors.TextPrimary,
            unfocusedTextColor = LiquidColors.TextPrimary,
            disabledTextColor = LiquidColors.TextTertiary,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedBorderColor = LiquidColors.AccentBlueLight,
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedLabelColor = LiquidColors.AccentBlueLight,
            unfocusedLabelColor = LiquidColors.TextSecondary,
            cursorColor = LiquidColors.AccentBlueLight,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

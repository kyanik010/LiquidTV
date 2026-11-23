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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import androidx.compose.ui.text.style.TextOverflow
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                cornerRadius = 24.dp,
                glassAlpha = 0.22f,
                borderAlpha = 0.36f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Compact Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    contentDescription = "رجوع",
                                    tint = Color.White,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (editingSubscriptionId != null) "تعديل الاشتراك" else "LIQUID TV",
                                style = LiquidTypography.TitleLarge.copy(
                                    brush = Brush.linearGradient(
                                        listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                                    ),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "تسجيل الدخول عبر Xtream أو M3U",
                                style = LiquidTypography.BodySmall,
                                color = LiquidColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Compact segmented method pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LoginModeTab(
                            title = "Xtream",
                            icon = Icons.Filled.Dns,
                            selected = state.selectedType == SubscriptionType.XTREAM,
                            enabled = !state.isConnecting,
                            onClick = { viewModel.selectType(SubscriptionType.XTREAM) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_xtream"),
                        )
                        LoginModeTab(
                            title = "M3U",
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            selected = state.selectedType == SubscriptionType.M3U,
                            enabled = !state.isConnecting,
                            onClick = { viewModel.selectType(SubscriptionType.M3U) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_m3u"),
                        )
                    }

                    // Optional display name
                    CompactGlassTextField(
                        value = state.displayName,
                        onValueChange = viewModel::setDisplayName,
                        label = "اسم الاشتراك (اختياري)",
                        placeholder = "مثال: اشتراك المنزل",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        enabled = !state.isConnecting,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.testTag("input_subscription_name"),
                    )

                    if (state.selectedType == SubscriptionType.XTREAM) {
                        CompactGlassTextField(
                            value = state.serverUrl,
                            onValueChange = viewModel::setServerUrl,
                            label = "رابط الخادم",
                            placeholder = "http://example.com:8080",
                            leadingIcon = Icons.Filled.Language,
                            enabled = !state.isConnecting,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            modifier = Modifier
                                .focusRequester(primaryInputRequester)
                                .testTag("input_xtream_url"),
                        )

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val sideBySide = maxWidth >= 310.dp
                            if (sideBySide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CompactGlassTextField(
                                        value = state.username,
                                        onValueChange = viewModel::setUsername,
                                        label = "اسم المستخدم",
                                        placeholder = "المستخدم",
                                        leadingIcon = Icons.Filled.Person,
                                        enabled = !state.isConnecting,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_xtream_username"),
                                    )
                                    CompactGlassTextField(
                                        value = state.password,
                                        onValueChange = viewModel::setPassword,
                                        label = "كلمة المرور",
                                        placeholder = "كلمة المرور",
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
                                                    .size(40.dp)
                                                    .testTag("toggle_password_visibility"),
                                            ) {
                                                Icon(
                                                    imageVector = if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = if (state.showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                                    tint = LiquidColors.TextSecondary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_xtream_password"),
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CompactGlassTextField(
                                        value = state.username,
                                        onValueChange = viewModel::setUsername,
                                        label = "اسم المستخدم",
                                        placeholder = "المستخدم",
                                        leadingIcon = Icons.Filled.Person,
                                        enabled = !state.isConnecting,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier.testTag("input_xtream_username"),
                                    )
                                    CompactGlassTextField(
                                        value = state.password,
                                        onValueChange = viewModel::setPassword,
                                        label = "كلمة المرور",
                                        placeholder = "كلمة المرور",
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
                                                    .size(40.dp)
                                                    .testTag("toggle_password_visibility"),
                                            ) {
                                                Icon(
                                                    imageVector = if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = if (state.showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                                    tint = LiquidColors.TextSecondary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("input_xtream_password"),
                                    )
                                }
                            }
                        }
                    } else {
                        CompactGlassTextField(
                            value = state.m3uUrl,
                            onValueChange = viewModel::setM3uUrl,
                            label = "رابط قائمة M3U / M3U8",
                            placeholder = "http://example.com/get.php?...",
                            leadingIcon = Icons.Filled.Link,
                            enabled = !state.isConnecting,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            onDone = { viewModel.connect() },
                            modifier = Modifier
                                .focusRequester(primaryInputRequester)
                                .testTag("input_m3u_url"),
                        )

                        // Optional collapsible HTTP headers for M3U
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !state.isConnecting) { viewModel.toggleAdvancedHeaders() }
                                .focusable()
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "ترويسات HTTP إضافية (اختياري)",
                                style = LiquidTypography.LabelMedium,
                                color = LiquidColors.AccentBlueLight,
                            )
                            Icon(
                                imageVector = if (state.showAdvancedHeaders) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "تبديل خيارات الترويسات",
                                tint = LiquidColors.AccentBlueLight,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        AnimatedVisibility(visible = state.showAdvancedHeaders) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactGlassTextField(
                                    value = state.userAgent,
                                    onValueChange = viewModel::setUserAgent,
                                    label = "معرّف المستخدم (User-Agent)",
                                    placeholder = "LiquidTV/1.0",
                                    leadingIcon = Icons.Filled.Devices,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    modifier = Modifier.testTag("input_m3u_user_agent"),
                                )
                                CompactGlassTextField(
                                    value = state.referer,
                                    onValueChange = viewModel::setReferer,
                                    label = "المرجع (Referer)",
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
                            cornerRadius = 14.dp,
                            glassAlpha = 0.16f,
                            borderAlpha = 0.3f,
                            glassColor = LiquidColors.AccentBlue,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = LiquidColors.AccentBlueLight,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = state.statusMessage ?: "جارٍ الاتصال...",
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
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }

                    // Error message
                    AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
                        LiquidGlassSurface(
                            modifier = Modifier.testTag("login_error_banner"),
                            cornerRadius = 12.dp,
                            glassAlpha = 0.25f,
                            borderAlpha = 0.55f,
                            glassColor = LiquidColors.ErrorRed,
                            borderColor = LiquidColors.ErrorRed,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "خطأ",
                                    tint = LiquidColors.ErrorRed,
                                    modifier = Modifier.size(18.dp),
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
                            .height(48.dp)
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
                            text = if (state.isConnecting) {
                                "جارٍ الاتصال..."
                            } else if (editingSubscriptionId != null) {
                                "حفظ وإعادة الاتصال"
                            } else {
                                "اتصال ومزامنة"
                            },
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
            .height(42.dp)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) Color.White else LiquidColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = LiquidTypography.LabelMedium,
                color = if (selected) Color.White else LiquidColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun CompactGlassTextField(
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
        textStyle = LiquidTypography.BodySmall.copy(color = LiquidColors.TextPrimary),
        label = {
            Text(
                text = label,
                style = LiquidTypography.LabelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                style = LiquidTypography.BodySmall,
                color = LiquidColors.TextDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = LiquidColors.AccentBlueLight,
                modifier = Modifier.size(18.dp),
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
        shape = RoundedCornerShape(13.dp),
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

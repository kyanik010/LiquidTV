package com.liquidtv.app.ui.screens.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.data.sonix.SonixAssignedServer
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.PlaylistBadgeChip
import com.liquidtv.app.ui.components.PlaylistSubscriptionCard
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Two-section Liquid Glass login & playlists screen:
 * 1. اختيار السيرفر (Normal URL with 4 fields OR Control Panel Servers with 3 fields, no Host URL)
 * 2. قوائم التشغيل (Saved playlists, Device ID / device_code, connect/activate, sync, edit, delete)
 */
@Composable
fun LoginScreen(
    editingSubscriptionId: String? = null,
    allowBack: Boolean = false,
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val primaryInputRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }

    LaunchedEffect(editingSubscriptionId) {
        viewModel.loadForEdit(editingSubscriptionId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onLoginSuccess()
        }
    }

    BackHandler(enabled = allowBack && !state.isConnecting) {
        onBack()
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val isCompactHeight = maxHeight < 480.dp
            val maxCardWidth = when {
                state.activeSection == LoginSection.PLAYLISTS -> if (maxWidth < 720.dp) maxWidth * 0.95f else 620.dp
                maxWidth < 480.dp -> (maxWidth * 0.94f).coerceIn(280.dp, 390.dp)
                maxWidth < 720.dp -> 420.dp
                maxWidth < 1100.dp -> 460.dp
                else -> 490.dp
            }
            val fieldInputHeight = if (isCompactHeight) 36.dp else 42.dp
            val verticalGap = if (isCompactHeight) 6.dp else 10.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = if (isCompactHeight) 8.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val colors = AppTheme.colors
                LiquidGlassSurface(
                    modifier = Modifier
                        .widthIn(max = maxCardWidth)
                        .fillMaxWidth()
                        .testTag("login_card"),
                    cornerRadius = 22.dp,
                    glassAlpha = 0.24f,
                    borderAlpha = 0.38f,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactHeight) 14.dp else 18.dp,
                                vertical = if (isCompactHeight) 10.dp else 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(verticalGap),
                    ) {
                        // Header + SONIX Device Code badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (allowBack) {
                                IconButton(
                                    onClick = { if (!state.isConnecting) onBack() },
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
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(colors.accent, colors.waveOne),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LiveTv,
                                    contentDescription = "LIQUID TV",
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.editingSubscriptionId != null) "تعديل قائمة التشغيل" else "LIQUID TV",
                                    style = LiquidTypography.TitleMedium.copy(
                                        brush = Brush.linearGradient(
                                            listOf(colors.text, colors.accentText),
                                        ),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Primary Two-Section Switcher: 1. اختيار السيرفر | 2. قوائم التشغيل
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(colors.panel.copy(alpha = 0.84f))
                                .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LoginSectionTab(
                                title = "اختيار السيرفر",
                                icon = Icons.Filled.Dns,
                                selected = state.activeSection == LoginSection.SERVER_SELECTION,
                                enabled = !state.isConnecting,
                                onClick = { viewModel.selectSection(LoginSection.SERVER_SELECTION) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_section_server"),
                            )
                            LoginSectionTab(
                                title = "قوائم التشغيل (${state.savedPlaylists.size})",
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                selected = state.activeSection == LoginSection.PLAYLISTS,
                                enabled = !state.isConnecting,
                                onClick = { viewModel.selectSection(LoginSection.PLAYLISTS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_section_playlists"),
                            )
                        }

                        if (state.activeSection == LoginSection.SERVER_SELECTION) {
                            ServerSelectionSection(
                                state = state,
                                fieldInputHeight = fieldInputHeight,
                                isCompactHeight = isCompactHeight,
                                primaryInputRequester = primaryInputRequester,
                                viewModel = viewModel,
                            )
                        } else {
                            PlaylistsSection(
                                state = state,
                                viewModel = viewModel,
                                onRequestDelete = { pendingDelete = it },
                            )
                        }
                    }
                }
            }

            pendingDelete?.let { sub ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("حذف قائمة التشغيل", color = AppTheme.colors.text) },
                    text = {
                        Text(
                            "هل تريد حذف \"${sub.name}\" وجميع قنواته وتصنيفاته ومفضلته ودليل البرامج المخزن؟",
                            color = AppTheme.colors.textSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deletePlaylist(sub.id)
                                pendingDelete = null
                            },
                        ) {
                            Text("حذف", color = AppTheme.colors.danger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("إلغاء", color = AppTheme.colors.accentText)
                        }
                    },
                    containerColor = AppTheme.colors.panel,
                )
            }
        }
    }
}

@Composable
private fun ServerSelectionSection(
    state: LoginUiState,
    fieldInputHeight: Dp,
    isCompactHeight: Boolean,
    primaryInputRequester: FocusRequester,
    viewModel: LoginViewModel,
) {
    val colors = AppTheme.colors
    val isPanelLockedEdit = state.editingOrigin == SubscriptionOrigin.PANEL_SERVER

    if (!isPanelLockedEdit && state.sonixState.settings.allowNormalUrlLogin) {
        LoginSectionTab(
            title = "URL عادي",
            icon = Icons.Filled.Language,
            selected = state.isNormalUrlSelected,
            enabled = !state.isConnecting,
            onClick = { viewModel.selectServerMode(SubscriptionOrigin.NORMAL_URL) },
            modifier = Modifier.fillMaxWidth().testTag("tab_normal_url"),
        )
    }

    if (state.editingSubscriptionId != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isPanelLockedEdit) {
                    "وضع التعديل: سيرفر لوحة التحكم (رابط السيرفر محمي)"
                } else {
                    "وضع تعديل قائمة التشغيل"
                },
                style = LiquidTypography.LabelSmall,
                color = colors.accentText,
            )
            TextButton(onClick = viewModel::clearEditMode, enabled = !state.isConnecting) {
                Text("إلغاء التعديل", color = colors.textSecondary)
            }
        }
    }

    if (!isPanelLockedEdit && !state.isNormalUrlSelected && state.sonixState.settings.allowPanelServers) {
        PanelServerChooserBlock(
            state = state,
            onRefresh = viewModel::refreshPanelServers,
            onSelectServer = { srv -> viewModel.selectPanelServer(srv.id, srv.name) },
        )
    }

    // Field 1: اسم قائمة التشغيل (shown in both modes; in panel mode only once server selected or editing)
    val showCredentialFields = state.isNormalUrlSelected ||
        (state.serverMode == SubscriptionOrigin.PANEL_SERVER && !state.selectedPanelServerId.isNullOrBlank()) ||
        state.editingSubscriptionId != null

    if (showCredentialFields) {
        CompactGlassTextField(
            value = state.displayName,
            onValueChange = viewModel::setDisplayName,
            label = "اسم قائمة التشغيل",
            placeholder = "مثال: قائمة المنزل",
            leadingIcon = Icons.AutoMirrored.Filled.Label,
            enabled = !state.isConnecting,
            imeAction = ImeAction.Next,
            inputHeight = fieldInputHeight,
            fieldModifier = Modifier
                .focusRequester(primaryInputRequester)
                .testTag("input_subscription_name"),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 280.dp
            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactGlassTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        label = "اسم المستخدم",
                        placeholder = "username",
                        leadingIcon = Icons.Filled.Person,
                        enabled = !state.isConnecting,
                        imeAction = ImeAction.Next,
                        inputHeight = fieldInputHeight,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag("input_xtream_username"),
                    )
                    CompactGlassTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = "كلمة المرور",
                        placeholder = "password",
                        leadingIcon = Icons.Filled.Lock,
                        enabled = !state.isConnecting,
                        visualTransformation = if (state.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.serverMode == SubscriptionOrigin.NORMAL_URL) ImeAction.Next else ImeAction.Done,
                        onDone = if (state.serverMode == SubscriptionOrigin.PANEL_SERVER) viewModel::connect else null,
                        inputHeight = fieldInputHeight,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag("input_xtream_password"),
                        trailingContent = {
                            IconButton(
                                onClick = viewModel::togglePasswordVisibility,
                                modifier = Modifier.size(32.dp).testTag("toggle_password_visibility"),
                            ) {
                                Icon(
                                    imageVector = if (state.showPassword) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = "إظهار أو إخفاء كلمة المرور",
                                    tint = colors.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactGlassTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        label = "اسم المستخدم",
                        placeholder = "username",
                        leadingIcon = Icons.Filled.Person,
                        enabled = !state.isConnecting,
                        imeAction = ImeAction.Next,
                        inputHeight = fieldInputHeight,
                        fieldModifier = Modifier.testTag("input_xtream_username"),
                    )
                    CompactGlassTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = "كلمة المرور",
                        placeholder = "password",
                        leadingIcon = Icons.Filled.Lock,
                        enabled = !state.isConnecting,
                        visualTransformation = if (state.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.serverMode == SubscriptionOrigin.NORMAL_URL) ImeAction.Next else ImeAction.Done,
                        onDone = if (state.serverMode == SubscriptionOrigin.PANEL_SERVER) viewModel::connect else null,
                        inputHeight = fieldInputHeight,
                        fieldModifier = Modifier.testTag("input_xtream_password"),
                        trailingContent = {
                            IconButton(
                                onClick = viewModel::togglePasswordVisibility,
                                modifier = Modifier.size(32.dp).testTag("toggle_password_visibility"),
                            ) {
                                Icon(
                                    imageVector = if (state.showPassword) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = "إظهار أو إخفاء كلمة المرور",
                                    tint = colors.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        // Field 4: Host URL shown ONLY for Normal URL mode (NEVER for Control Panel Servers)
        if (state.serverMode == SubscriptionOrigin.NORMAL_URL) {
            CompactGlassTextField(
                value = state.serverUrl,
                onValueChange = viewModel::setServerUrl,
                label = "Host URL",
                placeholder = "http://example.com:8080",
                leadingIcon = Icons.Filled.Language,
                enabled = !state.isConnecting,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onDone = viewModel::connect,
                inputHeight = fieldInputHeight,
                fieldModifier = Modifier.testTag("input_xtream_url"),
            )
        }
    }

    if (!isPanelLockedEdit && state.isNormalUrlSelected && state.sonixState.settings.allowPanelServers) {
        PanelServerChooserBlock(
            state = state,
            onRefresh = viewModel::refreshPanelServers,
            onSelectServer = { srv -> viewModel.selectPanelServer(srv.id, srv.name) },
        )
    }

    // Progress indicator
    AnimatedVisibility(visible = state.isConnecting) {
        LiquidGlassSurface(
            modifier = Modifier.testTag("login_progress_card"),
            cornerRadius = 12.dp,
            glassAlpha = 0.72f,
            borderAlpha = 0.45f,
            glassColor = colors.surface,
            borderColor = colors.accent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = state.statusMessage ?: "جارٍ الاتصال...",
                        style = LiquidTypography.LabelMedium,
                        color = colors.text,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0.05f, 1f) },
                    color = colors.accent,
                    trackColor = colors.border.copy(alpha = 0.30f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }

    // Error message
    AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
        LiquidGlassSurface(
            modifier = Modifier.testTag("login_error_banner"),
            cornerRadius = 10.dp,
            glassAlpha = 0.25f,
            borderAlpha = 0.55f,
            glassColor = colors.danger,
            borderColor = colors.danger,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "خطأ",
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = LiquidTypography.LabelMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Connect Button
    if (showCredentialFields) {
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
                .height(if (isCompactHeight) 44.dp else 48.dp)
                .scale(connectScale)
                .clip(RoundedCornerShape(50))
                .background(
                    if (state.isConnecting) colors.accent.copy(alpha = 0.55f) else colors.accent,
                )
                .border(
                    width = if (connectFocused) 2.5.dp else 1.dp,
                    color = if (connectFocused) colors.focus else colors.border.copy(alpha = 0.55f),
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
                } else if (state.editingSubscriptionId != null) {
                    "حفظ وإعادة الاتصال"
                } else {
                    "اتصال ومزامنة"
                },
                style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onAccent,
            )
        }
    }
}

@Composable
private fun PanelServerChooserBlock(
    state: LoginUiState,
    onRefresh: () -> Unit,
    onSelectServer: (SonixAssignedServer) -> Unit,
) {
    val colors = AppTheme.colors
    val sonix = state.sonixState

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_servers_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "السيرفرات المتاحة",
                style = LiquidTypography.LabelLarge,
                color = colors.text,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface.copy(alpha = 0.78f))
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable(enabled = !sonix.isSyncing && !state.isConnecting, onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("refresh_panel_servers_button"),
            ) {
                if (sonix.isSyncing) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "تحديث السيرفرات",
                        tint = colors.accentText,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = if (sonix.isSyncing) "جارٍ التحديث..." else "تحديث",
                    style = LiquidTypography.LabelSmall,
                    color = colors.text,
                )
            }
        }

        when {
            sonix.isBlockedOrRevoked -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.danger,
                    glassAlpha = 0.22f,
                    borderColor = colors.danger,
                ) {
                    Text(
                        text = "تم حظر هذا الجهاز من لوحة التحكم. لا يمكن استخدام سيرفرات اللوحة.",
                        style = LiquidTypography.BodySmall,
                        color = colors.text,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            sonix.isPanelExpired -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.warning,
                    glassAlpha = 0.22f,
                    borderColor = colors.warning,
                ) {
                    Text(
                        text = "انتهت صلاحية اشتراك الجهاز في لوحة التحكم (${sonix.deviceCode ?: ""}). يمكنك استخدام URL عادي أو تجديد اشتراك الجهاز.",
                        style = LiquidTypography.BodySmall,
                        color = colors.text,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            sonix.availableServers.isEmpty() -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.surface,
                    glassAlpha = 0.72f,
                    borderColor = colors.border,
                    modifier = Modifier.testTag("panel_servers_empty"),
                ) {
                    Text(
                        text = if (sonix.isSyncing) {
                            "جارٍ جلب السيرفرات المتاحة من لوحة التحكم..."
                        } else {
                            "لا توجد سيرفرات متاحة حالياً في لوحة التحكم (${sonix.deviceCode ?: "—"})."
                        },
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sonix.availableServers.forEach { server ->
                        val selected = state.selectedPanelServerId == server.id
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) colors.selected else colors.surface.copy(alpha = 0.74f),
                                )
                                .border(
                                    width = if (focused || selected) 1.5.dp else 1.dp,
                                    color = when {
                                        focused -> colors.focus
                                        selected -> colors.accent
                                        else -> colors.border.copy(alpha = 0.45f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    enabled = !state.isConnecting,
                                    onClick = { onSelectServer(server) },
                                )
                                .focusable(interactionSource = interaction)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("panel_server_item_${server.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Dns,
                                    contentDescription = null,
                                    tint = if (selected) colors.accent else colors.accentText,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column {
                                    Text(
                                        text = server.name,
                                        style = LiquidTypography.TitleSmall,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    server.region?.takeIf { it.isNotBlank() }?.let { region ->
                                        Text(
                                            text = "المنطقة: $region",
                                            style = LiquidTypography.LabelSmall,
                                            color = colors.muted,
                                        )
                                    }
                                }
                            }
                            if (selected) {
                                PlaylistBadgeChip(text = "محدد", color = colors.accentText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIdCompactCard(deviceCode: String?) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("device_id_compact_card"),
        cornerRadius = 14.dp,
        glassColor = colors.surface,
        glassAlpha = 0.72f,
        borderColor = colors.border,
        borderAlpha = 0.55f,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ID", style = LiquidTypography.LabelSmall, color = colors.muted)
                Text(
                    text = deviceCode?.takeIf { it.isNotBlank() } ?: "جارٍ تسجيل الجهاز...",
                    style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.testTag("device_id_value"),
                )
            }
            Icon(Icons.Filled.Devices, contentDescription = "Device ID", tint = colors.accentText)
        }
    }
}

@Composable
private fun PlaylistsSection(
    state: LoginUiState,
    viewModel: LoginViewModel,
    onRequestDelete: (Subscription) -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_playlists_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DeviceIdCompactCard(deviceCode = state.sonixState.deviceCode)

        state.playlistMessage?.let { msg ->
            LiquidGlassSurface(
                cornerRadius = 12.dp,
                glassAlpha = 0.22f,
                borderAlpha = 0.45f,
                glassColor = colors.success,
                borderColor = colors.success,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(msg, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearError) {
                        Text("إغلاق", color = colors.accentText)
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            LiquidGlassSurface(
                cornerRadius = 12.dp,
                glassAlpha = 0.24f,
                borderAlpha = 0.5f,
                glassColor = colors.danger,
                borderColor = colors.danger,
            ) {
                Text(
                    text = err,
                    style = LiquidTypography.BodySmall,
                    color = colors.text,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.savedPlaylists.isEmpty()) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlists_empty_state"),
                cornerRadius = 16.dp,
                glassAlpha = 0.18f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(36.dp),
                    )
                    Text("لا توجد قوائم تشغيل محفوظة", style = LiquidTypography.TitleSmall, color = colors.text)
                    Text(
                        "انتقل إلى قسم \"اختيار السيرفر\" لإضافة قائمة عبر URL عادي أو سيرفرات لوحة التحكم.",
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.savedPlaylists.forEach { sub ->
                    PlaylistSubscriptionCard(
                        subscription = sub,
                        deviceCode = state.sonixState.deviceCode,
                        isBusy = state.busyPlaylistId == sub.id,
                        onConnectOrActivate = { viewModel.activatePlaylist(sub.id) },
                        onSync = { viewModel.syncPlaylist(sub.id) },
                        onEdit = { viewModel.startEditingPlaylist(sub) },
                        onDelete = { onRequestDelete(sub) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginSectionTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.selected else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else if (selected) 1.dp else 0.dp,
                color = if (isFocused) colors.focus else if (selected) colors.accent else Color.Transparent,
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
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (!enabled) colors.muted else if (selected) colors.text else colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = LiquidTypography.LabelMedium,
                color = if (!enabled) colors.muted else if (selected) colors.text else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    leadingIcon: ImageVector,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    inputHeight: Dp = 38.dp,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall,
            color = if (isFocused) colors.accentText else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(inputHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface.copy(alpha = if (isFocused) 0.92f else 0.72f))
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) colors.focus else colors.border,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(16.dp),
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = LiquidTypography.BodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = LiquidTypography.BodySmall.copy(
                        color = if (enabled) colors.text else colors.muted,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onDone?.invoke() },
                    ),
                    modifier = fieldModifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

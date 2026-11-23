package com.liquidtv.app.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.ParentalPinDialog
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.UpdateState
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Subscriptions("Subscriptions & Sync", Icons.Filled.Subscriptions),
    Playback("Playback", Icons.Filled.PlayCircle),
    Parental("Parental Control", Icons.Filled.Lock),
    Appearance("Appearance", Icons.Filled.Palette),
    Ambient("Ambient Mode", Icons.Filled.NightlightRound),
    Updates("Updates & GitHub", Icons.Filled.SystemUpdate),
    About("About", Icons.Filled.Info),
}

private enum class PinDialogAction {
    NONE,
    SETUP_ENABLE,
    VERIFY_DISABLE,
    CHANGE_PIN,
    VERIFY_LOCK_CATEGORIES,
}

@Composable
fun SettingsScreen(
    onManageSubscriptions: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedSection by remember { mutableStateOf(SettingsSection.Subscriptions) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var repoInput by remember { mutableStateOf("") }

    var pinAction by remember { mutableStateOf(PinDialogAction.NONE) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showLockedCategoriesDialog by remember { mutableStateOf(false) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 36.dp, end = 36.dp),
        ) {
            Text(
                text = "Settings",
                style = LiquidTypography.HeadlineMedium.copy(
                    brush = Brush.linearGradient(
                        listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight)
                    )
                ),
                color = LiquidColors.TextPrimary,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            uiState.successMessage?.let { msg ->
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassAlpha = 0.22f,
                    glassColor = LiquidColors.SuccessGreen,
                    borderColor = LiquidColors.SuccessGreen,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissSuccess) {
                            Text("OK", color = Color.White)
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassAlpha = 0.25f,
                    glassColor = LiquidColors.ErrorRed,
                    borderColor = LiquidColors.ErrorRed,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissError) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsSection.entries.forEach { section ->
                        val isSelected = selectedSection == section
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable(interactionSource, null) { selectedSection = section }
                                .focusable(interactionSource = interactionSource)
                                .testTag("settings_section_${section.name.lowercase()}"),
                            cornerRadius = 14.dp,
                            glassAlpha = if (isSelected) 0.35f else if (isFocused) 0.22f else 0.10f,
                            borderAlpha = if (isFocused) 0.7f else if (isSelected) 0.4f else 0.16f,
                            borderColor = if (isSelected) LiquidColors.AccentBlue else Color.White,
                            isFocused = isFocused,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    tint = if (isSelected) LiquidColors.AccentBlue else LiquidColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = section.title,
                                    style = LiquidTypography.TitleSmall,
                                    color = if (isSelected) LiquidColors.TextPrimary else LiquidColors.TextSecondary,
                                )
                            }
                        }
                    }
                }

                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.14f,
                    borderAlpha = 0.22f,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (selectedSection) {
                            SettingsSection.Subscriptions -> {
                                item {
                                    SettingHeader("IPTV Subscriptions & Sync", "Manage active Xtream/M3U accounts, schedule, and EPG")
                                }
                                item {
                                    val sub = uiState.activeSubscription
                                    SettingItemTile(
                                        title = "Manage Subscriptions",
                                        subtitle = if (sub != null) {
                                            "Active: ${sub.name} (${sub.type.name}) • ${sub.connectionStatus.name}"
                                        } else {
                                            "Add, edit, or switch IPTV accounts"
                                        },
                                        action = {
                                            Icon(Icons.Filled.ManageAccounts, null, tint = LiquidColors.AccentBlueLight)
                                        },
                                        onClick = onManageSubscriptions,
                                        testTag = "setting_manage_subscriptions",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Refresh Active Playlist",
                                        subtitle = if (uiState.isRefreshing) "Syncing channels & VOD…" else "Sync channels, categories, movies, and series now",
                                        action = {
                                            if (uiState.isRefreshing) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = LiquidColors.AccentBlue)
                                            } else {
                                                Icon(Icons.Filled.Refresh, null, tint = LiquidColors.AccentBlue)
                                            }
                                        },
                                        onClick = { viewModel.refreshPlaylists() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Sync EPG Program Guide",
                                        subtitle = "Download latest XMLTV schedule for active subscription",
                                        action = {
                                            Icon(Icons.Filled.Schedule, null, tint = LiquidColors.AccentCyan)
                                        },
                                        onClick = { viewModel.refreshEpg() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Refresh Playlist",
                                        subtitle = if (uiState.autoRefreshEnabled) {
                                            "Background sync enabled every ${uiState.autoRefreshInterval}h"
                                        } else {
                                            "Manual playlist sync only"
                                        },
                                        action = {
                                            Switch(
                                                checked = uiState.autoRefreshEnabled,
                                                onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setAutoRefreshEnabled(!uiState.autoRefreshEnabled) },
                                        testTag = "setting_auto_refresh_toggle",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Refresh Interval",
                                        subtitle = "Periodic sync frequency: every ${uiState.autoRefreshInterval} hours",
                                        action = {
                                            Text("${uiState.autoRefreshInterval}h", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val intervals = listOf(6, 12, 24, 48)
                                            val next = (intervals.indexOf(uiState.autoRefreshInterval) + 1) % intervals.size
                                            viewModel.setAutoRefreshInterval(intervals[next])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Clear Watch History & Search Cache",
                                        subtitle = "Delete recent watch history and search suggestions",
                                        action = {
                                            Icon(Icons.Filled.Delete, null, tint = LiquidColors.LiveRed)
                                        },
                                        onClick = { viewModel.clearCache() },
                                    )
                                }
                            }
                            SettingsSection.Playback -> {
                                item {
                                    SettingHeader("Playback & Streaming", "Media3 track selection, timeouts, and stream recovery")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Default Video Quality",
                                        subtitle = "Preferred maximum resolution constraint: ${uiState.defaultQuality}",
                                        action = {
                                            Text(uiState.defaultQuality, style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val qualities = listOf("Auto", "1080p", "720p", "480p", "Data Saver")
                                            val next = (qualities.indexOf(uiState.defaultQuality) + 1) % qualities.size
                                            viewModel.setDefaultQuality(qualities[next])
                                        },
                                        testTag = "setting_default_quality",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Play Next",
                                        subtitle = "Automatically play next episode or channel when playback completes",
                                        action = {
                                            Switch(
                                                checked = uiState.autoPlay,
                                                onCheckedChange = { viewModel.setAutoPlay(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setAutoPlay(!uiState.autoPlay) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Enable Subtitles by Default",
                                        subtitle = "Automatically select preferred text tracks when present",
                                        action = {
                                            Switch(
                                                checked = uiState.subtitlesEnabled,
                                                onCheckedChange = { viewModel.setSubtitlesEnabled(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setSubtitlesEnabled(!uiState.subtitlesEnabled) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Preferred Subtitle Language",
                                        subtitle = "ISO language code for subtitles: ${uiState.preferredSubtitleLanguage.uppercase()}",
                                        action = {
                                            Text(uiState.preferredSubtitleLanguage.uppercase(), style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val langs = listOf("en", "ar", "fr", "es", "de", "tr")
                                            val next = (langs.indexOf(uiState.preferredSubtitleLanguage) + 1) % langs.size
                                            viewModel.setPreferredSubtitleLanguage(langs[next])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Connection Timeout",
                                        subtitle = "Timeout duration for IPTV network & stream connections: ${uiState.connectionTimeout}s",
                                        action = {
                                            Text("${uiState.connectionTimeout}s", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val timeouts = listOf(10, 15, 20, 30)
                                            val nextIndex = (timeouts.indexOf(uiState.connectionTimeout) + 1) % timeouts.size
                                            viewModel.setConnectionTimeout(timeouts[nextIndex])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Retry Attempts",
                                        subtitle = "Automatic exponential-backoff reconnects on transient drop: ${uiState.maxRetries}",
                                        action = {
                                            Text("${uiState.maxRetries}", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val retries = listOf(0, 1, 2, 3, 5)
                                            val nextIndex = (retries.indexOf(uiState.maxRetries) + 1) % retries.size
                                            viewModel.setMaxRetries(retries[nextIndex])
                                        },
                                    )
                                }
                            }
                            SettingsSection.Parental -> {
                                item {
                                    SettingHeader("Parental Controls", "Protect locked categories with a PIN code")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Enable Parental Control",
                                        subtitle = if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                            "Protected categories require PIN verification"
                                        } else {
                                            "Lock sensitive categories with a 4–8 digit PIN"
                                        },
                                        action = {
                                            Switch(
                                                checked = uiState.parentalControlEnabled,
                                                onCheckedChange = {
                                                    pinError = null
                                                    pinAction = if (!uiState.parentalControlEnabled) {
                                                        PinDialogAction.SETUP_ENABLE
                                                    } else {
                                                        PinDialogAction.VERIFY_DISABLE
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            pinError = null
                                            pinAction = if (!uiState.parentalControlEnabled) {
                                                PinDialogAction.SETUP_ENABLE
                                            } else {
                                                PinDialogAction.VERIFY_DISABLE
                                            }
                                        },
                                        testTag = "setting_parental_toggle",
                                    )
                                }
                                if (uiState.hasParentalPin) {
                                    item {
                                        SettingItemTile(
                                            title = "Change Parental PIN",
                                            subtitle = "Update the PIN used for locked categories",
                                            action = {
                                                Icon(Icons.Filled.Key, null, tint = LiquidColors.AccentBlueLight)
                                            },
                                            onClick = {
                                                pinError = null
                                                pinAction = PinDialogAction.CHANGE_PIN
                                            },
                                        )
                                    }
                                }
                                item {
                                    SettingItemTile(
                                        title = "Locked Categories",
                                        subtitle = "${uiState.categories.count { it.isLocked }} of ${uiState.categories.size} categories locked",
                                        action = {
                                            Icon(Icons.Filled.Shield, null, tint = LiquidColors.AccentVioletLight)
                                        },
                                        onClick = {
                                            if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                                pinError = null
                                                pinAction = PinDialogAction.VERIFY_LOCK_CATEGORIES
                                            } else {
                                                showLockedCategoriesDialog = true
                                            }
                                        },
                                        testTag = "setting_locked_categories",
                                    )
                                }
                            }
                            SettingsSection.Appearance -> {
                                item {
                                    SettingHeader("Visual & Glass Experience", "Configure Liquid Glass theme and header display")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Ambient Theme",
                                        subtitle = "Theme styling for background and screensaver",
                                        action = {
                                            Text(
                                                text = uiState.theme.replaceFirstChar { it.uppercase() },
                                                style = LiquidTypography.LabelLarge,
                                                color = LiquidColors.AccentBlue,
                                            )
                                        },
                                        onClick = {
                                            val themes = listOf("midnight_glass", "aurora", "ocean", "cosmic", "minimal")
                                            val nextIndex = (themes.indexOf(uiState.theme) + 1) % themes.size
                                            viewModel.setTheme(themes[nextIndex])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Clock Display",
                                        subtitle = "Show clock in ambient mode and home header",
                                        action = {
                                            Switch(
                                                checked = uiState.clockVisible,
                                                onCheckedChange = { viewModel.setClockVisible(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setClockVisible(!uiState.clockVisible) },
                                    )
                                }
                            }
                            SettingsSection.Ambient -> {
                                item {
                                    SettingHeader("Ambient Screensaver", "Cinematic Liquid Glass idle display")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Enable Ambient Screensaver",
                                        subtitle = if (uiState.ambientEnabled) "Activates automatically after inactivity" else "Disabled",
                                        action = {
                                            Switch(
                                                checked = uiState.ambientEnabled,
                                                onCheckedChange = { viewModel.setAmbientEnabled(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setAmbientEnabled(!uiState.ambientEnabled) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Screensaver Timeout",
                                        subtitle = "Start screensaver after inactivity: ${uiState.screenSaverTimeout} min",
                                        action = {
                                            Text("${uiState.screenSaverTimeout} min", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val durations = listOf(2, 5, 10, 15)
                                            val nextIndex = (durations.indexOf(uiState.screenSaverTimeout) + 1) % durations.size
                                            viewModel.setScreenSaverTimeout(durations[nextIndex])
                                        },
                                    )
                                }
                            }
                            SettingsSection.Updates -> {
                                item {
                                    SettingHeader("App Updates & GitHub", "Verify and install signed releases from GitHub")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Check for Updates",
                                        subtitle = when (updateState) {
                                            is UpdateState.Checking -> "Checking GitHub for newer releases..."
                                            is UpdateState.UpToDate -> "You're on the latest version! (v${BuildConfig.VERSION_NAME})"
                                            is UpdateState.Available -> "Update Available: v${(updateState as UpdateState.Available).info.versionName} • Click to View"
                                            is UpdateState.Error -> "Check failed: ${(updateState as UpdateState.Error).message}"
                                            else -> "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                                        },
                                        action = {
                                            if (updateState is UpdateState.Checking) {
                                                CircularProgressIndicator(
                                                    color = LiquidColors.AccentBlue,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                                                            ),
                                                            RoundedCornerShape(50),
                                                        )
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                ) {
                                                    Text("Check Now", style = LiquidTypography.LabelMedium, color = Color.White)
                                                }
                                            }
                                        },
                                        onClick = { updateViewModel.checkForUpdate(uiState.githubRepo) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "GitHub Repository",
                                        subtitle = uiState.githubRepo.ifBlank { "Click to set repository (owner/repo)" },
                                        action = {
                                            Icon(Icons.Filled.Edit, null, tint = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            repoInput = uiState.githubRepo
                                            showRepoDialog = true
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Check on App Launch",
                                        subtitle = if (uiState.autoCheckUpdates) "Checks GitHub in the background when the app opens" else "Manual updates only",
                                        action = {
                                            Switch(
                                                checked = uiState.autoCheckUpdates,
                                                onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = LiquidColors.AccentBlue,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setAutoCheckUpdates(!uiState.autoCheckUpdates) },
                                    )
                                }
                            }
                            SettingsSection.About -> {
                                item {
                                    SettingHeader("About LIQUID TV", "Architected for Android TV, Google TV, Phones & Tablets")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Application Version",
                                        subtitle = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Liquid Glass Edition",
                                        action = {},
                                        onClick = {},
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Player & IPTV Engine",
                                        subtitle = "Unified AndroidX Media3 playback service supporting Xtream Codes & M3U over HTTP/HTTPS with XMLTV EPG.",
                                        action = {
                                            Icon(Icons.Filled.LiveTv, null, tint = LiquidColors.AccentBlueLight)
                                        },
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRepoDialog) {
            AlertDialog(
                onDismissRequest = { showRepoDialog = false },
                title = { Text("GitHub Repository", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Enter your GitHub repository as 'owner/repo' (for example: kyanik010/LiquidTV):",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = LiquidColors.AccentBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val sanitized = repoInput.trim()
                            .removePrefix("https://github.com/")
                            .removePrefix("http://github.com/")
                            .removePrefix("github.com/")
                            .removeSuffix(".git")
                            .trim('/')
                        viewModel.setGithubRepo(sanitized)
                        showRepoDialog = false
                    }) {
                        Text("Save", color = LiquidColors.AccentBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRepoDialog = false }) {
                        Text("Cancel", color = LiquidColors.TextTertiary)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }

        if (showLockedCategoriesDialog) {
            AlertDialog(
                onDismissRequest = { showLockedCategoriesDialog = false },
                title = { Text("Locked Categories", color = Color.White) },
                text = {
                    if (uiState.categories.isEmpty()) {
                        Text("No categories loaded in the active subscription.", color = LiquidColors.TextSecondary)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.categories, key = { it.id }) { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleCategoryLocked(cat.name, cat.isLocked) }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(cat.name, style = LiquidTypography.BodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = cat.isLocked,
                                        onCheckedChange = { viewModel.toggleCategoryLocked(cat.name, cat.isLocked) },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLockedCategoriesDialog = false }) {
                        Text("Done", color = LiquidColors.AccentBlueLight)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }

        ParentalPinDialog(
            visible = pinAction != PinDialogAction.NONE,
            title = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "Set Parental PIN"
                PinDialogAction.VERIFY_DISABLE -> "Disable Parental Control"
                PinDialogAction.CHANGE_PIN -> "Enter New Parental PIN"
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "Verify Parental PIN"
                PinDialogAction.NONE -> ""
            },
            subtitle = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "Choose a 4–8 digit PIN to protect locked categories."
                PinDialogAction.VERIFY_DISABLE -> "Enter your current PIN to disable Parental Control."
                PinDialogAction.CHANGE_PIN -> "Enter a new 4–8 digit PIN."
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "Enter your current PIN to manage locked categories."
                PinDialogAction.NONE -> ""
            },
            confirmLabel = "Confirm",
            errorMessage = pinError,
            onConfirm = { entered ->
                when (pinAction) {
                    PinDialogAction.SETUP_ENABLE -> {
                        viewModel.enableParentalWithPin(entered)
                        pinAction = PinDialogAction.NONE
                        pinError = null
                    }
                    PinDialogAction.CHANGE_PIN -> {
                        viewModel.updateParentalPin(entered)
                        pinAction = PinDialogAction.NONE
                        pinError = null
                    }
                    PinDialogAction.VERIFY_DISABLE -> {
                        scope.launch {
                            if (viewModel.verifyParentalPin(entered)) {
                                viewModel.disableParentalControl()
                                pinAction = PinDialogAction.NONE
                                pinError = null
                            } else {
                                pinError = "Incorrect PIN"
                            }
                        }
                    }
                    PinDialogAction.VERIFY_LOCK_CATEGORIES -> {
                        scope.launch {
                            if (viewModel.verifyParentalPin(entered)) {
                                pinAction = PinDialogAction.NONE
                                pinError = null
                                showLockedCategoriesDialog = true
                            } else {
                                pinError = "Incorrect PIN"
                            }
                        }
                    }
                    PinDialogAction.NONE -> Unit
                }
            },
            onDismiss = {
                pinAction = PinDialogAction.NONE
                pinError = null
            },
        )
    }
}

@Composable
private fun SettingHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(title, style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
        Text(subtitle, style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
    }
}

@Composable
private fun SettingItemTile(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "settingScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .let { m -> if (testTag != null) m.testTag(testTag) else m },
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.22f else 0.10f,
        borderAlpha = if (isFocused) 0.65f else 0.16f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
            }
            action()
        }
    }
}

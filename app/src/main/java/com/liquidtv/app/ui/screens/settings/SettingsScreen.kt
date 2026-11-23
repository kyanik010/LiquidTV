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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.liquidtv.app.ui.utils.localizedQualityAr
import com.liquidtv.app.ui.utils.localizedSubscriptionStatusAr
import com.liquidtv.app.ui.utils.localizedSubscriptionTypeAr
import com.liquidtv.app.ui.utils.localizedSubtitleLangAr
import com.liquidtv.app.ui.utils.localizedThemeAr
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.UpdateState
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Subscriptions("الاشتراكات والمزامنة", Icons.Filled.Subscriptions),
    Playback("التشغيل والبث", Icons.Filled.PlayCircle),
    Parental("الرقابة الأبوية", Icons.Filled.Lock),
    Appearance("المظهر", Icons.Filled.Palette),
    Ambient("وضع التوقف", Icons.Filled.NightlightRound),
    Updates("التحديثات", Icons.Filled.SystemUpdate),
    About("حول التطبيق", Icons.Filled.Info),
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isWide = maxWidth >= 760.dp
            val horizontalPad = if (isWide) 32.dp else 16.dp
            val topNavClearance = if (isWide) 84.dp else 76.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topNavClearance, start = horizontalPad, end = horizontalPad),
            ) {
                Text(
                    text = "الإعدادات",
                    style = LiquidTypography.HeadlineMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight),
                        ),
                    ),
                    color = LiquidColors.TextPrimary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                uiState.successMessage?.let { msg ->
                    LiquidGlassSurface(
                        cornerRadius = 12.dp,
                        glassAlpha = 0.22f,
                        glassColor = LiquidColors.SuccessGreen,
                        borderColor = LiquidColors.SuccessGreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissSuccess) {
                                Text("حسناً", color = Color.White)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(err, style = LiquidTypography.BodySmall, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissError) {
                                Text("إغلاق", color = Color.White)
                            }
                        }
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SettingsSection.entries.forEach { section ->
                                SettingsSectionTab(
                                    section = section,
                                    isSelected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        SettingsDetailPane(
                            selectedSection = selectedSection,
                            uiState = uiState,
                            updateState = updateState,
                            viewModel = viewModel,
                            updateViewModel = updateViewModel,
                            onManageSubscriptions = onManageSubscriptions,
                            onEditRepo = {
                                repoInput = uiState.githubRepo
                                showRepoDialog = true
                            },
                            onRequestPinAction = { action ->
                                pinError = null
                                pinAction = action
                            },
                            onOpenLockedCategories = { showLockedCategoriesDialog = true },
                            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(SettingsSection.entries) { section ->
                                SettingsSectionPill(
                                    section = section,
                                    isSelected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                )
                            }
                        }

                        SettingsDetailPane(
                            selectedSection = selectedSection,
                            uiState = uiState,
                            updateState = updateState,
                            viewModel = viewModel,
                            updateViewModel = updateViewModel,
                            onManageSubscriptions = onManageSubscriptions,
                            onEditRepo = {
                                repoInput = uiState.githubRepo
                                showRepoDialog = true
                            },
                            onRequestPinAction = { action ->
                                pinError = null
                                pinAction = action
                            },
                            onOpenLockedCategories = { showLockedCategoriesDialog = true },
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }

        if (showRepoDialog) {
            AlertDialog(
                onDismissRequest = { showRepoDialog = false },
                title = { Text("مستودع GitHub", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "أدخل اسم مستودع GitHub بصيغة 'owner/repo' (مثال: kyanik010/LiquidTV):",
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
                        Text("حفظ", color = LiquidColors.AccentBlueLight)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRepoDialog = false }) {
                        Text("إلغاء", color = LiquidColors.TextTertiary)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }

        if (showLockedCategoriesDialog) {
            AlertDialog(
                onDismissRequest = { showLockedCategoriesDialog = false },
                title = { Text("التصنيفات المقفلة", color = Color.White) },
                text = {
                    if (uiState.categories.isEmpty()) {
                        Text("لا توجد تصنيفات محمّلة في الاشتراك الحالي.", color = LiquidColors.TextSecondary)
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
                        Text("تم", color = LiquidColors.AccentBlueLight)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }

        ParentalPinDialog(
            visible = pinAction != PinDialogAction.NONE,
            title = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "تعيين رمز الحماية"
                PinDialogAction.VERIFY_DISABLE -> "إيقاف الرقابة الأبوية"
                PinDialogAction.CHANGE_PIN -> "إدخال رمز حماية جديد"
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "التحقق من رمز الحماية"
                PinDialogAction.NONE -> ""
            },
            subtitle = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "اختر رمزاً من 4 إلى 8 أرقام لحماية التصنيفات المقفلة."
                PinDialogAction.VERIFY_DISABLE -> "أدخل الرمز الحالي لإيقاف الرقابة الأبوية."
                PinDialogAction.CHANGE_PIN -> "أدخل رمزاً جديداً مكوّناً من 4 إلى 8 أرقام."
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "أدخل الرمز الحالي لإدارة التصنيفات المقفلة."
                PinDialogAction.NONE -> ""
            },
            confirmLabel = "تأكيد",
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
                                pinError = "رمز الحماية غير صحيح"
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
                                pinError = "رمز الحماية غير صحيح"
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
private fun SettingsSectionTab(
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = modifier
            .height(52.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("settings_section_${section.name.lowercase()}"),
        cornerRadius = 14.dp,
        glassAlpha = if (isSelected) 0.35f else if (isFocused) 0.22f else 0.10f,
        borderAlpha = if (isFocused) 0.7f else if (isSelected) 0.45f else 0.16f,
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
                tint = if (isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextSecondary,
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

@Composable
private fun SettingsSectionPill(
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .height(48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("settings_section_${section.name.lowercase()}"),
        cornerRadius = 24.dp,
        glassAlpha = if (isSelected) 0.36f else if (isFocused) 0.24f else 0.12f,
        borderAlpha = if (isFocused) 0.75f else if (isSelected) 0.55f else 0.18f,
        borderColor = if (isSelected) LiquidColors.AccentBlueLight else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.title,
                tint = if (isSelected) LiquidColors.AccentBlueLight else LiquidColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = section.title,
                style = LiquidTypography.LabelLarge,
                color = if (isSelected) Color.White else LiquidColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingsDetailPane(
    selectedSection: SettingsSection,
    uiState: SettingsUiState,
    updateState: UpdateState,
    viewModel: SettingsViewModel,
    updateViewModel: AppUpdateViewModel,
    onManageSubscriptions: () -> Unit,
    onEditRepo: () -> Unit,
    onRequestPinAction: (PinDialogAction) -> Unit,
    onOpenLockedCategories: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 22.dp,
        glassAlpha = 0.14f,
        borderAlpha = 0.22f,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_content_list"),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (selectedSection) {
                SettingsSection.Subscriptions -> {
                    item {
                        SettingHeader("اشتراكات IPTV والمزامنة", "إدارة حسابات Xtream وM3U والجدول الزمني ودليل البرامج")
                    }
                    item {
                        val sub = uiState.activeSubscription
                        SettingItemTile(
                            title = "إدارة الاشتراكات",
                            subtitle = if (sub != null) {
                                "المفعّل: ${sub.name} (${localizedSubscriptionTypeAr(sub.type)}) • ${localizedSubscriptionStatusAr(sub.connectionStatus)}"
                            } else {
                                "إضافة أو تعديل أو التبديل بين حسابات IPTV"
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
                            title = "تحديث الاشتراك الحالي",
                            subtitle = if (uiState.isRefreshing) "جارٍ مزامنة القنوات والأفلام..." else "مزامنة القنوات والتصنيفات والأفلام والمسلسلات الآن",
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
                            title = "مزامنة دليل البرامج (EPG)",
                            subtitle = "تنزيل أحدث جدول برامج XMLTV للاشتراك المفعّل",
                            action = {
                                Icon(Icons.Filled.Schedule, null, tint = LiquidColors.AccentCyan)
                            },
                            onClick = { viewModel.refreshEpg() },
                        )
                    }
                    item {
                        SettingItemTile(
                            title = "التحديث التلقائي للقائمة",
                            subtitle = if (uiState.autoRefreshEnabled) {
                                "المزامنة التلقائية في الخلفية مفعّلة كل ${uiState.autoRefreshInterval} ساعة"
                            } else {
                                "تحديث يدوي للقائمة فقط"
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
                            title = "الفاصل الزمني للتحديث",
                            subtitle = "تكرار المزامنة الدورية: كل ${uiState.autoRefreshInterval} ساعة",
                            action = {
                                Text("${uiState.autoRefreshInterval} س", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
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
                            title = "مسح سجل المشاهدة والبحث",
                            subtitle = "حذف سجل المشاهدة الأخيرة واقتراحات البحث المحفوظة",
                            action = {
                                Icon(Icons.Filled.Delete, null, tint = LiquidColors.LiveRed)
                            },
                            onClick = { viewModel.clearCache() },
                            testTag = "setting_clear_cache",
                        )
                    }
                }
                SettingsSection.Playback -> {
                    item {
                        SettingHeader("التشغيل والبث", "إعدادات اختيار المسار وجودة الفيديو وإعادة الاتصال التلقائي")
                    }
                    item {
                        SettingItemTile(
                            title = "جودة الفيديو الافتراضية",
                            subtitle = "الحد الأقصى المفضل للدقة: ${localizedQualityAr(uiState.defaultQuality)}",
                            action = {
                                Text(localizedQualityAr(uiState.defaultQuality), style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
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
                            title = "التشغيل التلقائي للتالي",
                            subtitle = "تشغيل الحلقة أو القناة التالية تلقائياً عند انتهاء العرض",
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
                            title = "تفعيل الترجمة افتراضياً",
                            subtitle = "اختيار مسار الترجمة المفضل تلقائياً عند توفره",
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
                            title = "لغة الترجمة المفضلة",
                            subtitle = "اللغة المختارة للمسارات النصية: ${localizedSubtitleLangAr(uiState.preferredSubtitleLanguage)}",
                            action = {
                                Text(localizedSubtitleLangAr(uiState.preferredSubtitleLanguage), style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
                            },
                            onClick = {
                                val langs = listOf("ar", "en", "fr", "es", "de", "tr")
                                val next = (langs.indexOf(uiState.preferredSubtitleLanguage) + 1) % langs.size
                                viewModel.setPreferredSubtitleLanguage(langs[next])
                            },
                        )
                    }
                    item {
                        SettingItemTile(
                            title = "مهلة الاتصال بالخادم",
                            subtitle = "مدة الانتظار القصوى لاتصالات الشبكة والبث: ${uiState.connectionTimeout} ثانية",
                            action = {
                                Text("${uiState.connectionTimeout} ث", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
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
                            title = "محاولات إعادة الاتصال",
                            subtitle = "عدد المحاولات التلقائية عند انقطاع البث المؤقت: ${uiState.maxRetries}",
                            action = {
                                Text("${uiState.maxRetries}", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
                            },
                            onClick = {
                                val retries = listOf(0, 1, 2, 3, 5)
                                val nextIndex = (retries.indexOf(uiState.maxRetries) + 1) % retries.size
                                viewModel.setMaxRetries(retries[nextIndex])
                            },
                            testTag = "setting_max_retries",
                        )
                    }
                }
                SettingsSection.Parental -> {
                    item {
                        SettingHeader("الرقابة الأبوية", "حماية التصنيفات المقفلة برمز PIN خاص")
                    }
                    item {
                        SettingItemTile(
                            title = "تفعيل الرقابة الأبوية",
                            subtitle = if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                "التصنيفات المحمية تتطلب التحقق برمز الحماية"
                            } else {
                                "قفل التصنيفات الحساسة برمز مكوّن من 4 إلى 8 أرقام"
                            },
                            action = {
                                Switch(
                                    checked = uiState.parentalControlEnabled,
                                    onCheckedChange = {
                                        onRequestPinAction(
                                            if (!uiState.parentalControlEnabled) PinDialogAction.SETUP_ENABLE
                                            else PinDialogAction.VERIFY_DISABLE,
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = LiquidColors.AccentBlue,
                                        checkedTrackColor = LiquidColors.AccentBlueDark,
                                    ),
                                )
                            },
                            onClick = {
                                onRequestPinAction(
                                    if (!uiState.parentalControlEnabled) PinDialogAction.SETUP_ENABLE
                                    else PinDialogAction.VERIFY_DISABLE,
                                )
                            },
                            testTag = "setting_parental_toggle",
                        )
                    }
                    if (uiState.hasParentalPin) {
                        item {
                            SettingItemTile(
                                title = "تغيير رمز الحماية",
                                subtitle = "تحديث رمز PIN المستخدم لفتح التصنيفات المقفلة",
                                action = {
                                    Icon(Icons.Filled.Key, null, tint = LiquidColors.AccentBlueLight)
                                },
                                onClick = { onRequestPinAction(PinDialogAction.CHANGE_PIN) },
                            )
                        }
                    }
                    item {
                        SettingItemTile(
                            title = "التصنيفات المقفلة",
                            subtitle = "${uiState.categories.count { it.isLocked }} من أصل ${uiState.categories.size} تصنيف مقفل",
                            action = {
                                Icon(Icons.Filled.Shield, null, tint = LiquidColors.AccentVioletLight)
                            },
                            onClick = {
                                if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                    onRequestPinAction(PinDialogAction.VERIFY_LOCK_CATEGORIES)
                                } else {
                                    onOpenLockedCategories()
                                }
                            },
                            testTag = "setting_locked_categories",
                        )
                    }
                }
                SettingsSection.Appearance -> {
                    item {
                        SettingHeader("المظهر وتجربة الزجاج السائل", "تخصيص سمة الخلفية وعرض الساعة")
                    }
                    item {
                        SettingItemTile(
                            title = "سمة الخلفية",
                            subtitle = "النمط البصري للخلفية ووضع التوقف: ${localizedThemeAr(uiState.theme)}",
                            action = {
                                Text(
                                    text = localizedThemeAr(uiState.theme),
                                    style = LiquidTypography.LabelLarge,
                                    color = LiquidColors.AccentBlueLight,
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
                            title = "إظهار الساعة",
                            subtitle = "عرض الساعة في الشاشة الرئيسية ووضع التوقف",
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
                        SettingHeader("وضع التوقف السينمائي", "شاشة انتظار أنيقة بتصميم الزجاج السائل")
                    }
                    item {
                        SettingItemTile(
                            title = "تفعيل شاشة التوقف",
                            subtitle = if (uiState.ambientEnabled) "تعمل تلقائياً عند عدم الاستخدام" else "معطّلة",
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
                            title = "مهلة التشغيل التلقائي",
                            subtitle = "تشغيل شاشة التوقف بعد مرور: ${uiState.screenSaverTimeout} دقيقة",
                            action = {
                                Text("${uiState.screenSaverTimeout} د", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlueLight)
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
                        SettingHeader("تحديثات التطبيق وGitHub", "التحقق من الإصدارات الموقّعة وتثبيتها مباشرة")
                    }
                    item {
                        SettingItemTile(
                            title = "التحقق من التحديثات",
                            subtitle = when (updateState) {
                                is UpdateState.Checking -> "جارٍ البحث في GitHub عن إصدارات جديدة..."
                                is UpdateState.UpToDate -> "أنت تستخدم أحدث إصدار! (v${BuildConfig.VERSION_NAME})"
                                is UpdateState.Available -> "يتوفر تحديث جديد: v${updateState.info.versionName} • اضغط للعرض"
                                is UpdateState.Error -> "تعذّر الفحص: ${updateState.message}"
                                else -> "الإصدار المثبّت: v${BuildConfig.VERSION_NAME} (بناء ${BuildConfig.VERSION_CODE})"
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
                                                    listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet),
                                                ),
                                                RoundedCornerShape(50),
                                            )
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Text("فحص الآن", style = LiquidTypography.LabelMedium, color = Color.White)
                                    }
                                }
                            },
                            onClick = { updateViewModel.checkForUpdate(uiState.githubRepo) },
                        )
                    }
                    item {
                        SettingItemTile(
                            title = "مستودع GitHub",
                            subtitle = uiState.githubRepo.ifBlank { "اضغط لتحديد المستودع (owner/repo)" },
                            action = {
                                Icon(Icons.Filled.Edit, null, tint = LiquidColors.AccentBlueLight)
                            },
                            onClick = onEditRepo,
                        )
                    }
                    item {
                        SettingItemTile(
                            title = "فحص تلقائي عند التشغيل",
                            subtitle = if (uiState.autoCheckUpdates) "يفحص GitHub في الخلفية عند فتح التطبيق" else "فحص يدوي للتحديثات فقط",
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
                        SettingHeader("حول LIQUID TV", "مصمم لأجهزة Android TV وGoogle TV والهواتف والأجهزة اللوحية")
                    }
                    item {
                        SettingItemTile(
                            title = "إصدار التطبيق",
                            subtitle = "v${BuildConfig.VERSION_NAME} (بناء ${BuildConfig.VERSION_CODE}) • إصدار الزجاج السائل",
                            action = {},
                            onClick = {},
                        )
                    }
                    item {
                        SettingItemTile(
                            title = "محرك التشغيل والبث",
                            subtitle = "خدمة تشغيل موحّدة عبر AndroidX Media3 تدعم Xtream Codes وM3U ودليل البرامج XMLTV.",
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

@Composable
private fun SettingHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp),
            ) {
                Text(title, style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
            }
            action()
        }
    }
}

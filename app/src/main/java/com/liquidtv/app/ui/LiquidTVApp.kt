package com.liquidtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.navigation.LiquidTVNavGraph
import com.liquidtv.app.ui.navigation.MirrorBubbleNavigation
import com.liquidtv.app.ui.navigation.NavDestination
import com.liquidtv.app.ui.navigation.Screen
import com.liquidtv.app.ui.navigation.toScreen
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.GlassUpdateDialog
import kotlinx.coroutines.delay

/**
 * Root composable that enforces the subscription startup gate,
 * manages the inactivity screensaver timer, and wires Mirror Bubble navigation.
 */
@Composable
fun LiquidTVApp(
    startupViewModel: StartupViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val rootState by startupViewModel.rootState.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val gate = rootState.gate
    if (gate is AppStartupGate.Checking) {
        AppBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = LiquidColors.AccentBlueLight)
                Text(
                    text = "LIQUID TV",
                    style = LiquidTypography.HeadlineMedium,
                    color = LiquidColors.TextPrimary,
                )
            }
        }
        return
    }

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val startDestination = remember(gate is AppStartupGate.Ready) {
        if (gate is AppStartupGate.Ready) Screen.Home.route else Screen.Login.createRoute(allowBack = false)
    }

    // If active subscriptions are all deleted while using the app, bounce back to Login immediately
    LaunchedEffect(gate, currentRoute) {
        if (gate is AppStartupGate.NeedsLogin &&
            currentRoute != null &&
            !currentRoute.startsWith("login")
        ) {
            navController.navigate(Screen.Login.createRoute(allowBack = false)) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val isFullScreenOverlayRoute = currentRoute == null ||
        currentRoute.startsWith("player/") ||
        currentRoute == Screen.Ambient.route ||
        currentRoute.startsWith("login") ||
        currentRoute == Screen.Subscriptions.route ||
        currentRoute.startsWith("series_detail/")

    val showNavBar = !isFullScreenOverlayRoute

    // Real inactivity screensaver timer respecting user preferences
    LaunchedEffect(
        rootState.preferences.ambientEnabled,
        rootState.preferences.screenSaverTimeout,
        currentRoute,
        lastInteractionMs,
        gate,
    ) {
        val prefs = rootState.preferences
        val eligibleRoute = currentRoute != null &&
            !currentRoute.startsWith("player/") &&
            currentRoute != Screen.Ambient.route &&
            !currentRoute.startsWith("login")
        if (gate is AppStartupGate.Ready && prefs.ambientEnabled && eligibleRoute) {
            val timeoutMs = (prefs.screenSaverTimeout.coerceAtLeast(1) * 60_000L)
            val elapsed = System.currentTimeMillis() - lastInteractionMs
            val remaining = (timeoutMs - elapsed).coerceAtLeast(0L)
            delay(remaining)
            navController.navigate(Screen.Ambient.route) {
                launchSingleTop = true
            }
        }
    }

    val selectedDestination = when {
        currentRoute?.startsWith("home") == true -> NavDestination.Home
        currentRoute?.startsWith("live_tv") == true -> NavDestination.LiveTV
        currentRoute?.startsWith("movies") == true -> NavDestination.Movies
        currentRoute?.startsWith("series") == true -> NavDestination.Series
        currentRoute?.startsWith("categories") == true -> NavDestination.Categories
        currentRoute?.startsWith("category/") == true -> NavDestination.Categories
        currentRoute?.startsWith("languages") == true -> NavDestination.Categories
        currentRoute?.startsWith("language/") == true -> NavDestination.Categories
        currentRoute?.startsWith("countries") == true -> NavDestination.Categories
        currentRoute?.startsWith("country/") == true -> NavDestination.Categories
        currentRoute?.startsWith("favorites") == true -> NavDestination.Favorites
        currentRoute?.startsWith("search") == true -> NavDestination.Search
        currentRoute?.startsWith("settings") == true -> NavDestination.Settings
        else -> NavDestination.Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidColors.MidnightNavy)
            .onPreviewKeyEvent {
                lastInteractionMs = System.currentTimeMillis()
                false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastInteractionMs = System.currentTimeMillis()
                    }
                }
            },
    ) {
        LiquidTVNavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        )

        if (showNavBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MirrorBubbleNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { destination ->
                        val screen = destination.toScreen()
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }

        GlassUpdateDialog(
            updateState = updateState,
            onUpdateClick = { info -> updateViewModel.startDownload(info) },
            onInstallClick = { updateViewModel.installCurrentUpdate() },
            onDismiss = { updateViewModel.dismiss() },
        )
    }
}

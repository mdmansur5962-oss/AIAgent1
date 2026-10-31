package com.aiagent.assistant.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiagent.assistant.presentation.components.AgentBottomBar
import com.aiagent.assistant.presentation.components.BottomBarItem
import com.aiagent.assistant.presentation.drawer.AppDrawerContent
import com.aiagent.assistant.presentation.navigation.AppNavGraph
import com.aiagent.assistant.presentation.navigation.LaunchRouter
import com.aiagent.assistant.presentation.navigation.Routes
import com.aiagent.assistant.presentation.navigation.Screen
import com.aiagent.assistant.presentation.navigation.navigateTo
import com.aiagent.assistant.presentation.navigation.navigateTopLevel
import kotlinx.coroutines.launch

@Composable
fun AgentRoot(startViewModel: AppStartViewModel = hiltViewModel()) {
    val onboardingDone by startViewModel.onboardingDone.collectAsStateWithLifecycle()
    val userName by startViewModel.userName.collectAsStateWithLifecycle()
    val appName by startViewModel.appName.collectAsStateWithLifecycle()
    val resolved = onboardingDone

    if (resolved == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else {
        AgentScaffold(
            initialStart = if (resolved) Screen.Home.route else Routes.ONBOARDING,
            userName = userName,
            appName = appName,
            onOnboardingFinished = { startViewModel.completeOnboarding() }
        )
    }
}

@Composable
private fun AgentScaffold(
    initialStart: String,
    userName: String,
    appName: String,
    onOnboardingFinished: () -> Unit
) {
    // শুরুর destination একবারই ঠিক হবে, পরে onboarding শেষ হলে graph যেন reset না হয়
    val startDestination = remember { initialStart }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute != null && currentRoute in Screen.chromeRoutes

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // notification বা ভাসমান বাবল থেকে এসে নির্দিষ্ট পেজে যাওয়া
    val pendingRoute by LaunchRouter.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (route != null) {
            navController.navigateTo(route)
            LaunchRouter.consume()
        }
    }

    val barItems = Screen.bottomNavItems.map { BottomBarItem(stringResource(it.titleRes), it.icon) }
    // Settings পেজ খুললে Profile ট্যাব জ্বলবে; বাকি drawer-পেজে কোনো ট্যাব জ্বলবে না (-1)
    val selectedIndex = when (currentRoute) {
        Routes.SETTINGS -> Screen.bottomNavItems.indexOf(Screen.Profile)
        Routes.CHAT_DETAIL -> Screen.bottomNavItems.indexOf(Screen.Chat)
        else -> Screen.bottomNavItems.indexOfFirst { it.route == currentRoute }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showChrome,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                userName = userName,
                appName = appName,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigateTo(route)
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showChrome) {
                    AgentBottomBar(
                        items = barItems,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            navController.navigateTopLevel(Screen.bottomNavItems[index].route)
                        }
                    )
                }
            }
        ) { innerPadding ->
            AppNavGraph(
                navController = navController,
                startDestination = startDestination,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOnboardingFinished = onOnboardingFinished,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            )
        }
    }
}

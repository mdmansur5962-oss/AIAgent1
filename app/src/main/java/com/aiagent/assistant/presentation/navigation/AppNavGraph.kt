package com.aiagent.assistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aiagent.assistant.presentation.components.PlaceholderScreen
import com.aiagent.assistant.presentation.home.HomeScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen() }
        composable(Screen.Chats.route) {
            PlaceholderScreen(title = stringResource(Screen.Chats.titleRes))
        }
        composable(Screen.Automations.route) {
            PlaceholderScreen(title = stringResource(Screen.Automations.titleRes))
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen(title = stringResource(Screen.Settings.titleRes))
        }
    }
}

package com.aiagent.assistant.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.aiagent.assistant.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Rounded.Home)
    data object Chats : Screen("chats", R.string.nav_chats, Icons.Rounded.ChatBubble)
    data object Automations : Screen("automations", R.string.nav_automations, Icons.Rounded.Bolt)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Rounded.Settings)

    companion object {
        val bottomNavItems: List<Screen> = listOf(Home, Chats, Automations, Settings)
    }
}

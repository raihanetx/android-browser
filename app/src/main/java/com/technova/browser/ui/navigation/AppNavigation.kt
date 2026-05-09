package com.technova.browser.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.technova.browser.ui.screens.BrowserScreen
import com.technova.browser.ui.screens.BookmarksScreen
import com.technova.browser.ui.screens.HistoryScreen
import com.technova.browser.ui.screens.SettingsScreen
import com.technova.browser.viewmodel.BrowserViewModel
import org.koin.androidx.compose.koinViewModel

sealed class Screen(val route: String) {
    object Browser : Screen("browser")
    object Bookmarks : Screen("bookmarks")
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Browser.route
    ) {
        composable(Screen.Browser.route) {
            val viewModel: BrowserViewModel = koinViewModel()
            BrowserScreen(
                viewModel = viewModel,
                onMenuClick = {
                    // Show bottom sheet menu or navigate to settings
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Bookmarks.route) {
            BookmarksScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

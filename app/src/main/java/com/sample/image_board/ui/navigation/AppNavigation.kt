package com.sample.image_board.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sample.image_board.ui.auth.AuthScreen
import com.sample.image_board.ui.auth.ForgotPasswordScreen
import com.sample.image_board.ui.detail.DetailScreen
import com.sample.image_board.ui.main.MainScreen
import com.sample.image_board.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object ForgotPassword : Screen("forgot_password")
    data object Main : Screen("main") // MainScreen with bottom nav
    data object Detail : Screen("detail/{threadId}") {
        fun createRoute(threadId: String) = "detail/$threadId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        // 0. Splash Screen
        composable(Screen.Splash.route) {
            com.sample.image_board.ui.splash.SplashScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    authViewModel = authViewModel
            )
        }

        // 1. Halaman Login
        composable(Screen.Login.route) {
            AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onForgotPasswordClick = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        // 2. Halaman Forgot Password
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        // 3. Main Screen (with Bottom Navigation: Feed, Search, Create)
        composable(Screen.Main.route) {
            MainScreen(
                    onThreadClick = { threadId ->
                        navController.navigate(Screen.Detail.createRoute(threadId))
                    },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) // Clear stack
                        }
                    },
                    authViewModel = authViewModel
            )
        }

        // 4. Halaman Detail
        composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            DetailScreen(threadId = threadId, onBack = { navController.popBackStack() })
        }
    }
}

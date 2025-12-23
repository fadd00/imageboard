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
import com.sample.image_board.ui.home.HomeScreen
import com.sample.image_board.ui.detail.DetailScreen
import com.sample.image_board.ui.create.CreateThreadScreen
import com.sample.image_board.viewmodel.HomeViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object ForgotPassword : Screen("forgot_password")
    data object Home : Screen("home")
    data object CreateThread : Screen("create_thread")
    data object Detail : Screen("detail/{threadId}") {
        fun createRoute(threadId: String) = "detail/$threadId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {

        // 1. Halaman Login
        composable(Screen.Login.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onForgotPasswordClick = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }

        // 2. Halaman Forgot Password
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 3. Halaman Home (Feed dengan Pagination)
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()

            HomeScreen(
                onFabClick = {
                    navController.navigate(Screen.CreateThread.route)
                },
                onThreadClick = { threadId ->
                    navController.navigate(Screen.Detail.createRoute(threadId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) // Clear stack
                    }
                },
                viewModel = homeViewModel
            )
        }

        // 4. Halaman Create Thread (Upload dengan Kamera/Galeri)
        composable(Screen.CreateThread.route) {
            CreateThreadScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onSuccess = {
                    // Navigate back ke home
                    navController.popBackStack()

                    // Trigger refresh home screen
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("thread_created", true)
                }
            )
        }

        // 5. Halaman Detail
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            DetailScreen(
                threadId = threadId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}


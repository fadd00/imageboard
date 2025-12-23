package com.sample.image_board.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sample.image_board.ui.create.CreateThreadScreen
import com.sample.image_board.ui.home.HomeScreen
import com.sample.image_board.ui.search.SearchScreen
import com.sample.image_board.viewmodel.AuthViewModel
import com.sample.image_board.viewmodel.HomeViewModel

sealed class BottomNavItem(
        val route: String,
        val title: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
) {
    data object Feed :
            BottomNavItem(
                    route = "feed",
                    title = "Feed",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home
            )
    data object Search :
            BottomNavItem(
                    route = "search",
                    title = "Search",
                    selectedIcon = Icons.Filled.Search,
                    unselectedIcon = Icons.Outlined.Search
            )
    data object Create :
            BottomNavItem(
                    route = "create",
                    title = "Create",
                    selectedIcon = Icons.Filled.Add,
                    unselectedIcon = Icons.Outlined.Add
            )
}

@Composable
fun MainScreen(
        onThreadClick: (String) -> Unit,
        onLogout: () -> Unit,
        authViewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()

    val items = listOf(BottomNavItem.Feed, BottomNavItem.Search, BottomNavItem.Create)

    Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { item ->
                        val selected =
                                currentDestination?.hierarchy?.any { it.route == item.route } ==
                                        true

                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            imageVector =
                                                    if (selected) item.selectedIcon
                                                    else item.unselectedIcon,
                                            contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                        )
                    }
                }
            }
    ) { innerPadding ->
        NavHost(
                navController = navController,
                startDestination = BottomNavItem.Feed.route,
                modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Feed.route) {
                HomeScreen(
                        onFabClick = { navController.navigate(BottomNavItem.Create.route) },
                        onThreadClick = onThreadClick,
                        onLogout = onLogout,
                        viewModel = homeViewModel,
                        authViewModel = authViewModel,
                        showFab = false // FAB tidak perlu karena sudah ada di bottom nav
                )
            }

            composable(BottomNavItem.Search.route) {
                SearchScreen(onThreadClick = onThreadClick, homeViewModel = homeViewModel)
            }

            composable(BottomNavItem.Create.route) {
                CreateThreadScreen(
                        onBackClick = { navController.navigate(BottomNavItem.Feed.route) },
                        onSuccess = {
                            homeViewModel.refresh()
                            navController.navigate(BottomNavItem.Feed.route) {
                                popUpTo(BottomNavItem.Feed.route) { inclusive = true }
                            }
                        }
                )
            }
        }
    }
}

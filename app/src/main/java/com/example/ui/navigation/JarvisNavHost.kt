package com.example.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.ConfirmationDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.ResearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlass
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceBorder
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.viewmodel.JarvisViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "CORE", Icons.Default.Home)
    object Chat : Screen("chat", "COMM", Icons.Default.Chat)
    object Tasks : Screen("tasks", "AGENT", Icons.Default.Psychology)
    object Memory : Screen("memory", "MATRIX", Icons.Default.Memory)
    object Research : Screen("research", "RESEARCH", Icons.Default.Biotech)
    object Tools : Screen("tools", "TOOLS", Icons.Default.Build)
    object Models : Screen("models", "MODELS", Icons.Default.Speed)
    object Settings : Screen("settings", "SYSTEM", Icons.Default.Settings)
}

@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val uiState by viewModel.uiState.collectAsState()

    val navItems = listOf(
        Screen.Home,
        Screen.Chat,
        Screen.Tasks,
        Screen.Memory,
        Screen.Research,
        Screen.Tools,
        Screen.Models,
        Screen.Settings
    )

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = JarvisSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                navItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF030712),
                            selectedTextColor = JarvisCyan,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted,
                            indicatorColor = JarvisCyan
                        ),
                        modifier = Modifier.testTag("nav_tab_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                        onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                        onNavigateToResearch = { navController.navigate(Screen.Research.route) }
                    )
                }
                composable(Screen.Chat.route) {
                    ChatScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                composable(Screen.Tasks.route) {
                    TasksScreen(viewModel = viewModel)
                }
                composable(Screen.Memory.route) {
                    MemoryScreen(viewModel = viewModel)
                }
                composable(Screen.Research.route) {
                    ResearchScreen(viewModel = viewModel)
                }
                composable(Screen.Tools.route) {
                    ToolsScreen(viewModel = viewModel)
                }
                composable(Screen.Models.route) {
                    ModelsScreen(viewModel = viewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = viewModel)
                }
            }

            // Global Confirmation Dialog for high/medium risk actions
            ConfirmationDialog(
                action = uiState.pendingConfirmation,
                onResolve = { approved -> viewModel.resolveConfirmation(approved) }
            )
        }
    }
}

package com.momentum.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.momentum.app.ui.screens.HabitDetailScreen
import com.momentum.app.ui.screens.HabitWizardScreen
import com.momentum.app.ui.screens.HabitsScreen
import com.momentum.app.ui.screens.ProgressScreen
import com.momentum.app.ui.screens.SettingsScreen
import com.momentum.app.ui.screens.TodayScreen

object Dest {
    const val TODAY = "today"
    const val HABITS = "habits"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val HABIT_NEW = "habit_new"
    const val HABIT_DETAIL = "habit_detail/{habitId}"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab(Dest.PROGRESS, "Progress"),
    Tab(Dest.TODAY, "Today"),
    Tab(Dest.HABITS, "Habits"),
    Tab(Dest.SETTINGS, "Settings"),
)

@Composable
fun MomentumRoot() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val current = navBackStackEntry?.destination
    val route = current?.route.orEmpty()
    val showBar = route != Dest.HABIT_NEW && !route.startsWith("habit_detail")

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab.route) {
                                        Dest.PROGRESS -> Icons.Default.BarChart
                                        Dest.TODAY -> Icons.Default.CalendarToday
                                        Dest.HABITS -> Icons.AutoMirrored.Filled.List
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.PROGRESS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Dest.TODAY) {
                TodayScreen()
            }
            composable(Dest.HABITS) {
                HabitsScreen(
                    onNewHabit = { navController.navigate(Dest.HABIT_NEW) },
                    onOpenHabit = { id ->
                        navController.navigate("habit_detail/$id")
                    },
                )
            }
            composable(Dest.PROGRESS) { ProgressScreen() }
            composable(Dest.SETTINGS) { SettingsScreen() }
            composable(Dest.HABIT_NEW) {
                HabitWizardScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = Dest.HABIT_DETAIL,
                arguments = listOf(navArgument("habitId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("habitId") ?: return@composable
                HabitDetailScreen(
                    habitId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

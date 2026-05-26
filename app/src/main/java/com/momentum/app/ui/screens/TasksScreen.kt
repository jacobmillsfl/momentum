package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val taskTabs = listOf("Habits", "Projects")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNewHabit: () -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var projectEditorOpen by remember { mutableStateOf(false) }
    var projectEditorId by remember { mutableStateOf<String?>(null) }
    var projectsRefreshSignal by remember { mutableIntStateOf(0) }

    Scaffold(
        floatingActionButton = {
            when (tabIndex) {
                0 -> FloatingActionButton(onClick = onNewHabit) {
                    Icon(Icons.Default.Add, contentDescription = "New habit")
                }
                else -> FloatingActionButton(
                    onClick = {
                        projectEditorId = null
                        projectEditorOpen = true
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New project")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Tasks",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            TabRow(selectedTabIndex = tabIndex) {
                taskTabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = { Text(label) },
                    )
                }
            }
            when (tabIndex) {
                0 -> HabitsScreen(
                    onOpenHabit = onOpenHabit,
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp),
                )
                1 -> ProjectsScreen(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp),
                    onEditProject = { id ->
                        projectEditorId = id
                        projectEditorOpen = true
                    },
                    refreshSignal = projectsRefreshSignal,
                )
            }
        }
    }

    if (projectEditorOpen) {
        ProjectEditorDialog(
            projectId = projectEditorId,
            onDismiss = {
                projectEditorOpen = false
                projectEditorId = null
            },
            onProjectChanged = { projectsRefreshSignal++ },
        )
    }
}

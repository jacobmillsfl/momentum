package com.momentum.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.ui.LocalRepository
import com.momentum.app.ui.LocalThemeController
import com.momentum.app.ui.navigation.MomentumRoot
import com.momentum.app.ui.screens.GoalDomainMigrationScreen
import com.momentum.app.ui.theme.MomentumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MomentumApp
        setContent {
            var themeMode by remember { mutableStateOf("system") }
            var migrationBlock by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                themeMode = app.repository.getKv(KvKeys.THEME_MODE) ?: "system"
            }
            LaunchedEffect(Unit) {
                migrationBlock = app.repository.shouldBlockForGoalDomainMigration()
            }
            MomentumTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalRepository provides app.repository,
                    LocalThemeController provides { mode ->
                        themeMode = mode
                    },
                ) {
                    when (migrationBlock) {
                        null -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        true -> GoalDomainMigrationScreen(
                            onComplete = { migrationBlock = false },
                        )
                        false -> MomentumRoot()
                    }
                }
            }
        }
    }
}

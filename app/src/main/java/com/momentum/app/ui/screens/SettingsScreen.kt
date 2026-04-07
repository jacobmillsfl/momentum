package com.momentum.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TimePicker
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.momentum.app.MomentumApp
import com.momentum.app.data.backup.MomentumBackupV1
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.ui.LocalRepository
import com.momentum.app.ui.LocalThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val reminderTimeDisplay12h: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun parseNotificationTimeTo24(notificationTime: String): Pair<Int, Int> {
    val parts = notificationTime.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return h to m
}

private fun formatNotificationTime12h(notificationTime: String): String {
    val (h, m) = parseNotificationTimeTo24(notificationTime)
    return try {
        LocalTime.of(h, m).format(reminderTimeDisplay12h)
    } catch (_: Exception) {
        LocalTime.of(9, 0).format(reminderTimeDisplay12h)
    }
}

private val weightSliderRange = 80f..400f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MomentumApp
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val applyTheme = LocalThemeController.current
    var freezeSteps by remember { mutableIntStateOf(3) }
    var themeMode by remember { mutableStateOf("system") }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var notificationTime by remember { mutableStateOf("09:00") }
    var showTimePicker by remember { mutableStateOf(false) }
    var targetSlider by remember { mutableFloatStateOf(175f) }
    var targetSet by remember { mutableStateOf(false) }
    var startingSlider by remember { mutableFloatStateOf(175f) }
    var startingSet by remember { mutableStateOf(false) }
    var backupFeedback by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<MomentumBackupV1?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = repo.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: throw IllegalStateException("Could not write to the chosen location.")
                }
                backupFeedback = "Backup saved."
            } catch (e: Exception) {
                backupFeedback = e.message ?: "Export failed."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Could not read the file.")
                val preview = repo.peekBackupJson(text)
                pendingRestoreJson = text
                pendingRestorePreview = preview
            } catch (e: Exception) {
                backupFeedback = e.message ?: "Could not read backup."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (granted) {
                repo.setKv(KvKeys.NOTIFICATIONS_ENABLED, "true")
                notificationsEnabled = true
                app.rescheduleNotificationsFromSettings()
            }
        }
    }

    LaunchedEffect(Unit) {
        freezeSteps = (repo.getKv(KvKeys.FREEZES_ALLOWED)?.toIntOrNull() ?: 3)
            .coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH)
        themeMode = repo.getKv(KvKeys.THEME_MODE) ?: "system"
        notificationsEnabled = repo.getKv(KvKeys.NOTIFICATIONS_ENABLED) == "true"
        notificationTime = repo.getKv(KvKeys.NOTIFICATION_TIME) ?: "09:00"
        val tw = repo.targetWeightLbs()
        targetSet = tw != null
        targetSlider = (tw ?: 175.0).toFloat().coerceIn(weightSliderRange.start, weightSliderRange.endInclusive)
        val sw = repo.startingWeightLbs()
        startingSet = sw != null
        startingSlider = (sw ?: 175.0).toFloat().coerceIn(weightSliderRange.start, weightSliderRange.endInclusive)
    }

    pendingRestorePreview?.let { preview ->
        val jsonPayload = pendingRestoreJson
        if (jsonPayload != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingRestoreJson = null
                    pendingRestorePreview = null
                },
                title = { Text("Restore backup?") },
                text = {
                    Text(
                        "This replaces all habits, sessions, logs, to-dos, and saved settings on this device.\n\n" +
                            "Backup: ${preview.habits.size} habits · ${preview.sessions.size} sessions · " +
                            "${preview.tasks.size} to-dos · ${preview.logs.size} logs · ${preview.kv.size} settings rows.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    repo.importBackupJson(jsonPayload)
                                    pendingRestoreJson = null
                                    pendingRestorePreview = null
                                    freezeSteps = (repo.getKv(KvKeys.FREEZES_ALLOWED)?.toIntOrNull() ?: 3)
                                        .coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH)
                                    themeMode = repo.getKv(KvKeys.THEME_MODE) ?: "system"
                                    applyTheme(themeMode)
                                    notificationsEnabled = repo.getKv(KvKeys.NOTIFICATIONS_ENABLED) == "true"
                                    notificationTime = repo.getKv(KvKeys.NOTIFICATION_TIME) ?: "09:00"
                                    val tw = repo.targetWeightLbs()
                                    targetSet = tw != null
                                    targetSlider = (tw ?: 175.0).toFloat()
                                        .coerceIn(weightSliderRange.start, weightSliderRange.endInclusive)
                                    val sw = repo.startingWeightLbs()
                                    startingSet = sw != null
                                    startingSlider = (sw ?: 175.0).toFloat()
                                        .coerceIn(weightSliderRange.start, weightSliderRange.endInclusive)
                                    app.rescheduleNotificationsFromSettings()
                                    backupFeedback = "Backup restored."
                                } catch (e: Exception) {
                                    backupFeedback = e.message ?: "Restore failed."
                                    pendingRestoreJson = null
                                    pendingRestorePreview = null
                                }
                            }
                        },
                    ) {
                        Text("Replace all data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingRestoreJson = null
                        pendingRestorePreview = null
                    }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    if (showTimePicker) {
        key(notificationTime) {
            val (h24, min0) = parseNotificationTimeTo24(notificationTime)
            val pickerHolder = remember { mutableStateOf<TimePicker?>(null) }
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerHolder.value?.let { tp ->
                                notificationTime = "%02d:%02d".format(tp.hour, tp.minute)
                            }
                            showTimePicker = false
                        },
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancel")
                    }
                },
                text = {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            TimePicker(ctx).apply {
                                setIs24HourView(false)
                                hour = h24
                                minute = min0
                                pickerHolder.value = this
                            }
                        },
                    )
                },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "system" to "System",
                "light" to "Light",
                "dark" to "Dark",
            ).forEach { (value, label) ->
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.setKv(KvKeys.THEME_MODE, value)
                            themeMode = value
                            applyTheme(value)
                        }
                    },
                ) {
                    Text(
                        label,
                        color = if (themeMode == value) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Notifications", style = MaterialTheme.typography.labelLarge)
        SectionCaption("Daily reminder to open Momentum and log habits. Exact time may vary slightly on some devices (inexact alarm).")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable reminders", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val ok = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (ok) {
                                scope.launch {
                                    repo.setKv(KvKeys.NOTIFICATIONS_ENABLED, "true")
                                    notificationsEnabled = true
                                    app.rescheduleNotificationsFromSettings()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            scope.launch {
                                repo.setKv(KvKeys.NOTIFICATIONS_ENABLED, "true")
                                notificationsEnabled = true
                                app.rescheduleNotificationsFromSettings()
                            }
                        }
                    } else {
                        scope.launch {
                            repo.setKv(KvKeys.NOTIFICATIONS_ENABLED, "false")
                            notificationsEnabled = false
                            app.rescheduleNotificationsFromSettings()
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reminder time", style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatNotificationTime12h(notificationTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = { showTimePicker = true },
                enabled = notificationsEnabled,
            ) {
                Text("Change")
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Target weight (lbs)", style = MaterialTheme.typography.labelLarge)
        SectionCaption("Optional. Teal dashed line on the Progress weight chart.")
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (targetSet) "%.1f lbs".format(targetSlider) else "Not set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { targetSet = false }) {
                Text("Clear")
            }
        }
        Slider(
            value = targetSlider,
            onValueChange = {
                targetSlider = it
                targetSet = true
            },
            valueRange = weightSliderRange,
        )
        Spacer(Modifier.height(16.dp))
        Text("Starting weight (lbs)", style = MaterialTheme.typography.labelLarge)
        SectionCaption("Optional. Red dashed line on the weight chart.")
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (startingSet) "%.1f lbs".format(startingSlider) else "Not set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { startingSet = false }) {
                Text("Clear")
            }
        }
        Slider(
            value = startingSlider,
            onValueChange = {
                startingSlider = it
                startingSet = true
            },
            valueRange = weightSliderRange,
        )
        Spacer(Modifier.height(20.dp))
        Text("Streak freezes per month", style = MaterialTheme.typography.labelLarge)
        SectionCaption("Used when a scheduled session is missed (up to ${KvKeys.MAX_STREAK_FREEZES_PER_MONTH} per month).")
        Spacer(Modifier.height(4.dp))
        Text(
            "$freezeSteps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = freezeSteps.toFloat(),
            onValueChange = { freezeSteps = it.roundToInt().coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH) },
            valueRange = 0f..KvKeys.MAX_STREAK_FREEZES_PER_MONTH.toFloat(),
            steps = KvKeys.MAX_STREAK_FREEZES_PER_MONTH - 1,
        )
        Spacer(Modifier.height(24.dp))
        Text("Backup & restore", style = MaterialTheme.typography.labelLarge)
        SectionCaption(
            "Export a JSON file you keep privately (cloud drive, Files, etc.). Import replaces everything on this device with that file—use after a reinstall or on a new phone.",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    backupFeedback = null
                    val name = "Momentum-backup-" +
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
                    exportLauncher.launch(name)
                },
            ) {
                Text("Export data")
            }
            OutlinedButton(
                onClick = {
                    backupFeedback = null
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
            ) {
                Text("Import data")
            }
        }
        backupFeedback?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.contains("failed", ignoreCase = true) ||
                    msg.contains("Could not", ignoreCase = true) ||
                    msg.contains("not supported", ignoreCase = true)
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    repo.setKv(
                        KvKeys.FREEZES_ALLOWED,
                        freezeSteps.coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH).toString(),
                    )
                    repo.setKv(KvKeys.NOTIFICATION_TIME, notificationTime)
                    repo.setKv(
                        KvKeys.TARGET_WEIGHT_LBS,
                        if (targetSet) "%.1f".format(targetSlider) else "",
                    )
                    repo.setKv(
                        KvKeys.STARTING_WEIGHT_LBS,
                        if (startingSet) "%.1f".format(startingSlider) else "",
                    )
                    if (notificationsEnabled) {
                        repo.setKv(KvKeys.NOTIFICATIONS_ENABLED, "true")
                    }
                    app.rescheduleNotificationsFromSettings()
                }
            },
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

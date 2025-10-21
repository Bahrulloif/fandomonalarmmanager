package com.tastamat.fandomon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tastamat.fandomon.utils.PermissionUtils
import com.tastamat.fandomon.utils.XiaomiUtils

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val usageGranted = remember { mutableStateOf<Boolean>(PermissionUtils.hasUsageStatsPermission(context)) }
    val notificationsEnabled = remember { mutableStateOf<Boolean>(PermissionUtils.areNotificationsEnabled(context)) }
    val exactAlarmsOk = remember { mutableStateOf<Boolean>(PermissionUtils.canScheduleExactAlarms(context)) }
    val accessibilityEnabled = remember { mutableStateOf<Boolean>(PermissionUtils.isAccessibilityServiceEnabled(context)) }
    val storageGranted = remember { mutableStateOf<Boolean>(PermissionUtils.hasStoragePermission(context)) }
    val isXiaomi = remember { mutableStateOf<Boolean>(XiaomiUtils.isXiaomiDevice()) }

    val allOk = usageGranted.value && notificationsEnabled.value && exactAlarmsOk.value && accessibilityEnabled.value && storageGranted.value

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setup Required") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("To work reliably, Fandomon needs these permissions and settings:", style = MaterialTheme.typography.bodyLarge)

            // Usage Access
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usage Access", style = MaterialTheme.typography.titleMedium)
                    Text("Allows Fandomon to detect if the target app is in foreground or frozen.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            PermissionUtils.requestUsageStatsPermission(context)
                        }) {
                            Text(if (usageGranted.value) "Granted" else "Grant")
                        }
                        OutlinedButton(onClick = { usageGranted.value = PermissionUtils.hasUsageStatsPermission(context) }) {
                            Text("Re-check")
                        }
                    }
                }
            }

            // Notifications
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text("Needed for high-priority restart prompts on Android 10+.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { PermissionUtils.openNotificationSettings(context) }) { Text("Open Settings") }
                        OutlinedButton(onClick = { notificationsEnabled.value = PermissionUtils.areNotificationsEnabled(context) }) { Text("Re-check") }
                    }
                }
            }

            // Exact alarms
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exact Alarms", style = MaterialTheme.typography.titleMedium)
                    Text("Required for reliable monitoring in Doze mode.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exactAlarmsOk.value = PermissionUtils.canScheduleExactAlarms(context) }) { Text("Re-check") }
                    }
                }
            }

            // Accessibility Service - CRITICAL for automatic restart
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (!accessibilityEnabled.value) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Fandomon Auto Launcher (CRITICAL)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (!accessibilityEnabled.value) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "REQUIRED for automatic restart without user interaction on Android 10+. " +
                        "Without this, you will receive notifications that require manual tapping.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "How to enable:\n" +
                        "1. Tap 'Open Settings' below\n" +
                        "2. Find 'Fandomon Auto Launcher' in the list\n" +
                        "3. Turn it ON\n" +
                        "4. Tap 'Allow' in the confirmation dialog",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { PermissionUtils.openAccessibilitySettings(context) },
                            colors = if (!accessibilityEnabled.value) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (accessibilityEnabled.value) "Enabled ✓" else "Open Settings")
                        }
                        OutlinedButton(onClick = {
                            accessibilityEnabled.value = PermissionUtils.isAccessibilityServiceEnabled(context)
                        }) {
                            Text("Re-check")
                        }
                    }
                }
            }

            // Storage permission for heartbeat file
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File Access for Freeze Detection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Needed to read Fandomat's heartbeat log (messages.log) to detect if app is frozen. " +
                        "This is how Fandomon knows when Fandomat crashes or stops responding.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { PermissionUtils.requestStoragePermission(context) }) {
                            Text(if (storageGranted.value) "Granted ✓" else "Grant Permission")
                        }
                        OutlinedButton(onClick = {
                            storageGranted.value = PermissionUtils.hasStoragePermission(context)
                        }) {
                            Text("Re-check")
                        }
                    }
                }
            }

            // Xiaomi / MIUI specific
            if (isXiaomi.value) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Xiaomi/MIUI Settings", style = MaterialTheme.typography.titleMedium)
                        Text("Enable Autostart and disable battery restrictions.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { XiaomiUtils.openAutoStartSettings(context) }) { Text("Open Autostart") }
                            Button(onClick = { XiaomiUtils.openBatterySettings(context) }) { Text("Open Battery") }
                        }
                    }
                }
            }

            Button(
                onClick = { if (allOk) onComplete() },
                enabled = allOk,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}



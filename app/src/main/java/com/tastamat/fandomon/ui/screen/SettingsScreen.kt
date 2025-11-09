package com.tastamat.fandomon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tastamat.fandomon.ui.viewmodel.SettingsViewModel
import com.tastamat.fandomon.utils.PermissionUtils
import com.tastamat.fandomon.utils.XiaomiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    // Force LTR text direction for ALL text fields
    val ltrTextStyle = TextStyle(textDirection = TextDirection.Ltr)

    // Local state for number fields to allow empty input
    var checkIntervalText by remember { mutableStateOf(state.checkIntervalMinutes.toString()) }
    var statusIntervalText by remember { mutableStateOf(state.statusReportIntervalMinutes.toString()) }
    var mqttPortText by remember { mutableStateOf(state.mqttPort.toString()) }

    // Update local state when state changes
    LaunchedEffect(state.checkIntervalMinutes) {
        checkIntervalText = state.checkIntervalMinutes.toString()
    }
    LaunchedEffect(state.statusReportIntervalMinutes) {
        statusIntervalText = state.statusReportIntervalMinutes.toString()
    }
    LaunchedEffect(state.mqttPort) {
        mqttPortText = state.mqttPort.toString()
    }

    // FORCE LTR layout direction for ENTIRE screen to fix RTL text input issues
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Fandomon Settings") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permissions quick status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Permissions Status", style = MaterialTheme.typography.titleMedium)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val usage = PermissionUtils.hasUsageStatsPermission(context)
                    val notif = PermissionUtils.areNotificationsEnabled(context)
                    val exact = PermissionUtils.canScheduleExactAlarms(context)
                    val isXiaomi = XiaomiUtils.isXiaomiDevice()
                    Text("Usage Access: ${if (usage) "OK" else "MISSING"}")
                    Text("Notifications: ${if (notif) "OK" else "MISSING"}")
                    Text("Exact Alarms: ${if (exact) "OK" else "CHECK"}")
                    if (isXiaomi) {
                        Text("Device: Xiaomi/MIUI (Autostart & Battery settings required)")
                    }
                }
            }
            
            // Monitoring Controls
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monitoring Control",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Status indicator
                        if (state.isMonitoringActive) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary
                                ) {}
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start button - filled when active, outlined when inactive
                        if (state.isMonitoringActive) {
                            Button(
                                onClick = { viewModel.startMonitoring() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Monitoring Active")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.startMonitoring() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start Monitoring")
                            }
                        }

                        // Stop button - filled when active, outlined when inactive
                        if (state.isMonitoringActive) {
                            FilledTonalButton(
                                onClick = { viewModel.stopMonitoring() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Stop Monitoring")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.stopMonitoring() },
                                modifier = Modifier.weight(1f),
                                enabled = false
                            ) {
                                Text("Stop Monitoring")
                            }
                        }
                    }
                }
            }

            // Device Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = state.deviceId,
                        onValueChange = { viewModel.updateDeviceId(it) },
                        label = { Text("Device ID") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ltrTextStyle
                    )

                    OutlinedTextField(
                        value = state.deviceName,
                        onValueChange = { viewModel.updateDeviceName(it) },
                        label = { Text("Device Name") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ltrTextStyle
                    )
                }
            }

            // Fandomat Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Fandomat Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = state.fandomatPackageName,
                        onValueChange = { viewModel.updateFandomatPackageName(it) },
                        label = { Text("Fandomat Package Name") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ltrTextStyle
                    )

                    OutlinedTextField(
                            value = checkIntervalText,
                            onValueChange = { newValue ->
                                checkIntervalText = newValue
                                // Only update ViewModel if valid number or empty
                                if (newValue.isEmpty()) {
                                    // Keep current value in ViewModel, just allow empty field
                                } else {
                                    newValue.toIntOrNull()?.let { minutes ->
                                        if (minutes > 0) {
                                            viewModel.updateCheckInterval(minutes)
                                        }
                                    }
                                }
                            },
                            label = { Text("Check Interval (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle,
                            isError = checkIntervalText.isNotEmpty() && checkIntervalText.toIntOrNull() == null,
                            supportingText = if (checkIntervalText.isNotEmpty() && checkIntervalText.toIntOrNull() == null) {
                                { Text("Please enter a valid number") }
                            } else null
                        )

                    OutlinedTextField(
                            value = statusIntervalText,
                            onValueChange = { newValue ->
                                statusIntervalText = newValue
                                // Only update ViewModel if valid number or empty
                                if (newValue.isEmpty()) {
                                    // Keep current value in ViewModel, just allow empty field
                                } else {
                                    newValue.toIntOrNull()?.let { minutes ->
                                        if (minutes > 0) {
                                            viewModel.updateStatusReportInterval(minutes)
                                        }
                                    }
                                }
                            },
                            label = { Text("Status Report Interval (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle,
                            isError = statusIntervalText.isNotEmpty() && statusIntervalText.toIntOrNull() == null,
                            supportingText = if (statusIntervalText.isNotEmpty() && statusIntervalText.toIntOrNull() == null) {
                                { Text("Please enter a valid number") }
                            } else null
                        )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-Restart Fandomat",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = state.autoRestartEnabled,
                            onCheckedChange = { viewModel.updateAutoRestartEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Heartbeat Freeze Detection",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Check logfile.txt for app freeze detection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.heartbeatEnabled,
                            onCheckedChange = { viewModel.updateHeartbeatEnabled(it) }
                        )
                    }
                }
            }

            // MQTT Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MQTT Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = state.mqttEnabled,
                            onCheckedChange = { viewModel.updateMqttEnabled(it) }
                        )
                    }

                    if (state.mqttEnabled) {
                        OutlinedTextField(
                            value = state.mqttBrokerUrl,
                            onValueChange = { viewModel.updateMqttBrokerUrl(it) },
                            label = { Text("Broker URL") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                                value = mqttPortText,
                                onValueChange = { newValue ->
                                    mqttPortText = newValue
                                    // Only update ViewModel if valid number or empty
                                    if (newValue.isEmpty()) {
                                        // Keep current value in ViewModel, just allow empty field
                                    } else {
                                        newValue.toIntOrNull()?.let { port ->
                                            if (port in 1..65535) {
                                                viewModel.updateMqttPort(port)
                                            }
                                        }
                                    }
                                },
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = ltrTextStyle,
                                isError = mqttPortText.isNotEmpty() && (mqttPortText.toIntOrNull() == null || mqttPortText.toIntOrNull() !in 1..65535),
                                supportingText = if (mqttPortText.isNotEmpty() && (mqttPortText.toIntOrNull() == null || mqttPortText.toIntOrNull() !in 1..65535)) {
                                    { Text("Port must be between 1 and 65535") }
                                } else null
                            )

                        OutlinedTextField(
                            value = state.mqttUsername,
                            onValueChange = { viewModel.updateMqttUsername(it) },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                            value = state.mqttPassword,
                            onValueChange = { viewModel.updateMqttPassword(it) },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                            value = state.mqttTopicEvents,
                            onValueChange = { viewModel.updateMqttTopicEvents(it) },
                            label = { Text("Events Topic") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                            value = state.mqttTopicStatus,
                            onValueChange = { viewModel.updateMqttTopicStatus(it) },
                            label = { Text("Status Topic") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                            value = state.mqttTopicCommands,
                            onValueChange = { viewModel.updateMqttTopicCommands(it) },
                            label = { Text("Commands Topic") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )
                    }
                }
            }

            // REST Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REST API Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = state.restEnabled,
                            onCheckedChange = { viewModel.updateRestEnabled(it) }
                        )
                    }

                    if (state.restEnabled) {
                        OutlinedTextField(
                            value = state.restBaseUrl,
                            onValueChange = { viewModel.updateRestBaseUrl(it) },
                            label = { Text("Base URL") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )

                        OutlinedTextField(
                            value = state.restApiKey,
                            onValueChange = { viewModel.updateRestApiKey(it) },
                            label = { Text("API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ltrTextStyle
                        )
                    }
                }
            }
        }
        }
    }
}

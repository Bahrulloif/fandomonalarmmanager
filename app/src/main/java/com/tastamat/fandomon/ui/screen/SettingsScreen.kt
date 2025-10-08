package com.tastamat.fandomon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tastamat.fandomon.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

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
            // Monitoring Controls
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Monitoring Control",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startMonitoring() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Monitoring")
                        }

                        OutlinedButton(
                            onClick = { viewModel.stopMonitoring() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Monitoring")
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.deviceName,
                        onValueChange = { viewModel.updateDeviceName(it) },
                        label = { Text("Device Name") },
                        modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.checkIntervalMinutes.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { minutes ->
                                viewModel.updateCheckInterval(minutes)
                            }
                        },
                        label = { Text("Check Interval (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.statusReportIntervalMinutes.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { minutes ->
                                viewModel.updateStatusReportInterval(minutes)
                            }
                        },
                        label = { Text("Status Report Interval (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.mqttPort.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { port ->
                                    viewModel.updateMqttPort(port)
                                }
                            },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.mqttUsername,
                            onValueChange = { viewModel.updateMqttUsername(it) },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.mqttPassword,
                            onValueChange = { viewModel.updateMqttPassword(it) },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.mqttTopicEvents,
                            onValueChange = { viewModel.updateMqttBrokerUrl(it) },
                            label = { Text("Events Topic") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.mqttTopicStatus,
                            onValueChange = { viewModel.updateMqttBrokerUrl(it) },
                            label = { Text("Status Topic") },
                            modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.restApiKey,
                            onValueChange = { viewModel.updateRestApiKey(it) },
                            label = { Text("API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

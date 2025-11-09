package com.tastamat.fandomon.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsState(
    val deviceId: String = "",
    val deviceName: String = "",
    val mqttEnabled: Boolean = false,
    val mqttBrokerUrl: String = "",
    val mqttPort: Int = 1883,
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val mqttTopicEvents: String = "fandomon/events",
    val mqttTopicStatus: String = "fandomon/status",
    val mqttTopicCommands: String = "fandomon/commands",
    val restEnabled: Boolean = false,
    val restBaseUrl: String = "",
    val restApiKey: String = "",
    val checkIntervalMinutes: Int = 5,
    val statusReportIntervalMinutes: Int = 15,
    val fandomatPackageName: String = "com.tastamat.fandomat",
    val autoRestartEnabled: Boolean = true,
    val isMonitoringActive: Boolean = false,  // New: track monitoring state
    val heartbeatEnabled: Boolean = true  // New: heartbeat freeze detection
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = SettingsState(
                deviceId = preferences.deviceId.first(),
                deviceName = preferences.deviceName.first(),
                mqttEnabled = preferences.mqttEnabled.first(),
                mqttBrokerUrl = preferences.mqttBrokerUrl.first(),
                mqttPort = preferences.mqttPort.first(),
                mqttUsername = preferences.mqttUsername.first(),
                mqttPassword = preferences.mqttPassword.first(),
                mqttTopicEvents = preferences.mqttTopicEvents.first(),
                mqttTopicStatus = preferences.mqttTopicStatus.first(),
                mqttTopicCommands = preferences.mqttTopicCommands.first(),
                restEnabled = preferences.restEnabled.first(),
                restBaseUrl = preferences.restBaseUrl.first(),
                restApiKey = preferences.restApiKey.first(),
                checkIntervalMinutes = preferences.checkIntervalMinutes.first(),
                statusReportIntervalMinutes = preferences.statusReportIntervalMinutes.first(),
                fandomatPackageName = preferences.fandomatPackageName.first(),
                autoRestartEnabled = preferences.autoRestartEnabled.first(),
                isMonitoringActive = preferences.monitoringActive.first(),
                heartbeatEnabled = preferences.heartbeatEnabled.first()
            )
        }
    }

    fun updateDeviceId(deviceId: String) {
        viewModelScope.launch {
            preferences.setDeviceId(deviceId)
            _state.value = _state.value.copy(deviceId = deviceId)
        }
    }

    fun updateDeviceName(deviceName: String) {
        viewModelScope.launch {
            preferences.setDeviceName(deviceName)
            _state.value = _state.value.copy(deviceName = deviceName)
        }
    }

    fun updateMqttEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMqttEnabled(enabled)
            _state.value = _state.value.copy(mqttEnabled = enabled)
        }
    }

    fun updateMqttBrokerUrl(url: String) {
        viewModelScope.launch {
            preferences.setMqttBrokerUrl(url)
            _state.value = _state.value.copy(mqttBrokerUrl = url)
        }
    }

    fun updateMqttPort(port: Int) {
        viewModelScope.launch {
            preferences.setMqttPort(port)
            _state.value = _state.value.copy(mqttPort = port)
        }
    }

    fun updateMqttUsername(username: String) {
        viewModelScope.launch {
            preferences.setMqttUsername(username)
            _state.value = _state.value.copy(mqttUsername = username)
        }
    }

    fun updateMqttPassword(password: String) {
        viewModelScope.launch {
            preferences.setMqttPassword(password)
            _state.value = _state.value.copy(mqttPassword = password)
        }
    }

    fun updateMqttTopicEvents(topic: String) {
        viewModelScope.launch {
            preferences.setMqttTopicEvents(topic)
            _state.value = _state.value.copy(mqttTopicEvents = topic)
        }
    }

    fun updateMqttTopicStatus(topic: String) {
        viewModelScope.launch {
            preferences.setMqttTopicStatus(topic)
            _state.value = _state.value.copy(mqttTopicStatus = topic)
        }
    }

    fun updateMqttTopicCommands(topic: String) {
        viewModelScope.launch {
            preferences.setMqttTopicCommands(topic)
            _state.value = _state.value.copy(mqttTopicCommands = topic)
        }
    }

    fun updateRestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRestEnabled(enabled)
            _state.value = _state.value.copy(restEnabled = enabled)
        }
    }

    fun updateRestBaseUrl(url: String) {
        viewModelScope.launch {
            preferences.setRestBaseUrl(url)
            _state.value = _state.value.copy(restBaseUrl = url)
        }
    }

    fun updateRestApiKey(apiKey: String) {
        viewModelScope.launch {
            preferences.setRestApiKey(apiKey)
            _state.value = _state.value.copy(restApiKey = apiKey)
        }
    }

    fun updateCheckInterval(minutes: Int) {
        viewModelScope.launch {
            preferences.setCheckIntervalMinutes(minutes)
            _state.value = _state.value.copy(checkIntervalMinutes = minutes)
            rescheduleAlarms()
        }
    }

    fun updateStatusReportInterval(minutes: Int) {
        viewModelScope.launch {
            preferences.setStatusReportIntervalMinutes(minutes)
            _state.value = _state.value.copy(statusReportIntervalMinutes = minutes)
            rescheduleAlarms()
        }
    }

    fun updateFandomatPackageName(packageName: String) {
        viewModelScope.launch {
            preferences.setFandomatPackageName(packageName)
            _state.value = _state.value.copy(fandomatPackageName = packageName)
        }
    }

    fun updateAutoRestartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoRestartEnabled(enabled)
            _state.value = _state.value.copy(autoRestartEnabled = enabled)
        }
    }

    fun updateHeartbeatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHeartbeatEnabled(enabled)
            _state.value = _state.value.copy(heartbeatEnabled = enabled)
        }
    }

    fun startMonitoring() {
        viewModelScope.launch {
            val checkInterval = preferences.checkIntervalMinutes.first()
            val statusInterval = preferences.statusReportIntervalMinutes.first()
            alarmScheduler.scheduleMonitoring(checkInterval, statusInterval)
            preferences.setMonitoringActive(true)
            _state.value = _state.value.copy(isMonitoringActive = true)
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            alarmScheduler.cancelAllAlarms()
            preferences.setMonitoringActive(false)
            _state.value = _state.value.copy(isMonitoringActive = false)
        }
    }

    private fun rescheduleAlarms() {
        viewModelScope.launch {
            alarmScheduler.cancelAllAlarms()
            val checkInterval = preferences.checkIntervalMinutes.first()
            val statusInterval = preferences.statusReportIntervalMinutes.first()
            alarmScheduler.scheduleMonitoring(checkInterval, statusInterval)
        }
    }
}

package com.tastamat.fandomon.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")

        val MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")
        val MQTT_BROKER_URL = stringPreferencesKey("mqtt_broker_url")
        val MQTT_PORT = intPreferencesKey("mqtt_port")
        val MQTT_USERNAME = stringPreferencesKey("mqtt_username")
        val MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
        val MQTT_TOPIC_EVENTS = stringPreferencesKey("mqtt_topic_events")
        val MQTT_TOPIC_STATUS = stringPreferencesKey("mqtt_topic_status")
        val MQTT_TOPIC_COMMANDS = stringPreferencesKey("mqtt_topic_commands")

        val REST_ENABLED = booleanPreferencesKey("rest_enabled")
        val REST_BASE_URL = stringPreferencesKey("rest_base_url")
        val REST_API_KEY = stringPreferencesKey("rest_api_key")

        val CHECK_INTERVAL_MINUTES = intPreferencesKey("check_interval_minutes")
        val STATUS_REPORT_INTERVAL_MINUTES = intPreferencesKey("status_report_interval_minutes")
        val FANDOMAT_PACKAGE_NAME = stringPreferencesKey("fandomat_package_name")
        val AUTO_RESTART_ENABLED = booleanPreferencesKey("auto_restart_enabled")
        val MONITORING_ACTIVE = booleanPreferencesKey("monitoring_active")
    }

    val deviceId: Flow<String> = context.dataStore.data.map { it[DEVICE_ID] ?: "" }
    val deviceName: Flow<String> = context.dataStore.data.map { it[DEVICE_NAME] ?: "" }

    val mqttEnabled: Flow<Boolean> = context.dataStore.data.map { it[MQTT_ENABLED] ?: false }
    val mqttBrokerUrl: Flow<String> = context.dataStore.data.map { it[MQTT_BROKER_URL] ?: "" }
    val mqttPort: Flow<Int> = context.dataStore.data.map { it[MQTT_PORT] ?: 1883 }
    val mqttUsername: Flow<String> = context.dataStore.data.map { it[MQTT_USERNAME] ?: "" }
    val mqttPassword: Flow<String> = context.dataStore.data.map { it[MQTT_PASSWORD] ?: "" }
    val mqttTopicEvents: Flow<String> = context.dataStore.data.map { it[MQTT_TOPIC_EVENTS] ?: "fandomon/events" }
    val mqttTopicStatus: Flow<String> = context.dataStore.data.map { it[MQTT_TOPIC_STATUS] ?: "fandomon/status" }
    val mqttTopicCommands: Flow<String> = context.dataStore.data.map { it[MQTT_TOPIC_COMMANDS] ?: "fandomon/commands" }

    val restEnabled: Flow<Boolean> = context.dataStore.data.map { it[REST_ENABLED] ?: false }
    val restBaseUrl: Flow<String> = context.dataStore.data.map { it[REST_BASE_URL] ?: "" }
    val restApiKey: Flow<String> = context.dataStore.data.map { it[REST_API_KEY] ?: "" }

    val checkIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[CHECK_INTERVAL_MINUTES] ?: 5 }
    val statusReportIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[STATUS_REPORT_INTERVAL_MINUTES] ?: 15 }
    val fandomatPackageName: Flow<String> = context.dataStore.data.map { it[FANDOMAT_PACKAGE_NAME] ?: "com.tastamat.fandomat" }
    val autoRestartEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RESTART_ENABLED] ?: true }
    val monitoringActive: Flow<Boolean> = context.dataStore.data.map { it[MONITORING_ACTIVE] ?: false }

    suspend fun setDeviceId(deviceId: String) {
        context.dataStore.edit { it[DEVICE_ID] = deviceId }
    }

    suspend fun setDeviceName(deviceName: String) {
        context.dataStore.edit { it[DEVICE_NAME] = deviceName }
    }

    suspend fun setMqttEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MQTT_ENABLED] = enabled }
    }

    suspend fun setMqttBrokerUrl(url: String) {
        context.dataStore.edit { it[MQTT_BROKER_URL] = url }
    }

    suspend fun setMqttPort(port: Int) {
        context.dataStore.edit { it[MQTT_PORT] = port }
    }

    suspend fun setMqttUsername(username: String) {
        context.dataStore.edit { it[MQTT_USERNAME] = username }
    }

    suspend fun setMqttPassword(password: String) {
        context.dataStore.edit { it[MQTT_PASSWORD] = password }
    }

    suspend fun setMqttTopicEvents(topic: String) {
        context.dataStore.edit { it[MQTT_TOPIC_EVENTS] = topic }
    }

    suspend fun setMqttTopicStatus(topic: String) {
        context.dataStore.edit { it[MQTT_TOPIC_STATUS] = topic }
    }

    suspend fun setMqttTopicCommands(topic: String) {
        context.dataStore.edit { it[MQTT_TOPIC_COMMANDS] = topic }
    }

    suspend fun setRestEnabled(enabled: Boolean) {
        context.dataStore.edit { it[REST_ENABLED] = enabled }
    }

    suspend fun setRestBaseUrl(url: String) {
        context.dataStore.edit { it[REST_BASE_URL] = url }
    }

    suspend fun setRestApiKey(apiKey: String) {
        context.dataStore.edit { it[REST_API_KEY] = apiKey }
    }

    suspend fun setCheckIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[CHECK_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setStatusReportIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[STATUS_REPORT_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setFandomatPackageName(packageName: String) {
        context.dataStore.edit { it[FANDOMAT_PACKAGE_NAME] = packageName }
    }

    suspend fun setAutoRestartEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RESTART_ENABLED] = enabled }
    }

    suspend fun setMonitoringActive(active: Boolean) {
        context.dataStore.edit { it[MONITORING_ACTIVE] = active }
    }
}

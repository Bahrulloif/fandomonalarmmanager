package com.tastamat.fandomon.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.data.remote.api.RetrofitClient
import com.tastamat.fandomon.data.remote.dto.EventDto
import com.tastamat.fandomon.data.remote.dto.StatusDto
import com.tastamat.fandomon.data.remote.mqtt.MqttClientManager
import com.tastamat.fandomon.data.repository.EventRepository
import kotlinx.coroutines.flow.first

class DataSyncService(private val context: Context) {

    private val TAG = "DataSyncService"
    private val eventRepository = EventRepository(FandomonDatabase.getDatabase(context).eventDao())
    private val preferences = AppPreferences(context)
    private val mqttClient = MqttClientManager(context)
    private val gson = Gson()

    suspend fun syncEvents() {
        try {
            val unsentEvents = eventRepository.getUnsentEvents()
            if (unsentEvents.isEmpty()) {
                Log.d(TAG, "No unsent events")
                return
            }

            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val mqttEnabled = preferences.mqttEnabled.first()
            val restEnabled = preferences.restEnabled.first()

            for (event in unsentEvents) {
                val eventDto = EventDto(
                    id = event.id,
                    eventType = event.eventType.name,
                    timestamp = event.timestamp,
                    message = event.message,
                    deviceId = deviceId,
                    deviceName = deviceName
                )

                var success = false

                // Send via MQTT
                if (mqttEnabled) {
                    success = sendViaMqtt(eventDto, isEvent = true)
                }

                // Send via REST
                if (restEnabled) {
                    val restSuccess = sendViaRest(eventDto)
                    success = success || restSuccess
                }

                // Mark as sent if successful
                if (success) {
                    eventRepository.markEventAsSent(event.id)
                    Log.d(TAG, "Event ${event.id} sent successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing events", e)
        }
    }

    suspend fun sendStatus(fandomatRunning: Boolean, internetConnected: Boolean) {
        try {
            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val statusDto = StatusDto(
                fandomonRunning = true,
                fandomatRunning = fandomatRunning,
                internetConnected = internetConnected,
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                deviceName = deviceName
            )

            val mqttEnabled = preferences.mqttEnabled.first()
            val restEnabled = preferences.restEnabled.first()

            if (mqttEnabled) {
                sendViaMqtt(statusDto, isEvent = false)
            }

            if (restEnabled) {
                sendStatusViaRest(statusDto)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending status", e)
        }
    }

    private suspend fun sendViaMqtt(data: Any, isEvent: Boolean): Boolean {
        return try {
            val brokerUrl = preferences.mqttBrokerUrl.first()
            val port = preferences.mqttPort.first()
            val username = preferences.mqttUsername.first()
            val password = preferences.mqttPassword.first()

            if (brokerUrl.isEmpty()) {
                Log.w(TAG, "MQTT broker URL is not configured")
                return false
            }

            if (!mqttClient.isConnected()) {
                var connected = false
                mqttClient.connect(
                    brokerUrl = brokerUrl,
                    port = port,
                    username = username,
                    password = password,
                    onSuccess = { connected = true },
                    onFailure = { connected = false }
                )
                // Wait for connection (simplified, consider using suspendCoroutine)
                Thread.sleep(2000)
                if (!connected) return false
            }

            val topic = if (isEvent) {
                preferences.mqttTopicEvents.first()
            } else {
                preferences.mqttTopicStatus.first()
            }

            val message = gson.toJson(data)
            var publishSuccess = false

            mqttClient.publish(
                topic = topic,
                message = message,
                onSuccess = { publishSuccess = true },
                onFailure = { publishSuccess = false }
            )

            Thread.sleep(1000) // Wait for publish
            publishSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via MQTT", e)
            false
        }
    }

    private suspend fun sendViaRest(eventDto: EventDto): Boolean {
        return try {
            val baseUrl = preferences.restBaseUrl.first()
            val apiKey = preferences.restApiKey.first()

            if (baseUrl.isEmpty()) {
                Log.w(TAG, "REST base URL is not configured")
                return false
            }

            val api = RetrofitClient.getApi(baseUrl)
            val response = api.sendEvent("Bearer $apiKey", eventDto)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via REST", e)
            false
        }
    }

    private suspend fun sendStatusViaRest(statusDto: StatusDto): Boolean {
        return try {
            val baseUrl = preferences.restBaseUrl.first()
            val apiKey = preferences.restApiKey.first()

            if (baseUrl.isEmpty()) {
                Log.w(TAG, "REST base URL is not configured")
                return false
            }

            val api = RetrofitClient.getApi(baseUrl)
            val response = api.sendStatus("Bearer $apiKey", statusDto)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error sending status via REST", e)
            false
        }
    }

    private suspend fun getDeviceId(): String {
        // Use custom device_id from preferences if set, otherwise use Android ID
        val customDeviceId = preferences.deviceId.first()
        return if (customDeviceId.isNotEmpty()) {
            customDeviceId
        } else {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }

    private suspend fun getDeviceName(): String {
        // Use custom device_name from preferences if set, otherwise use default
        val customDeviceName = preferences.deviceName.first()
        return if (customDeviceName.isNotEmpty()) {
            customDeviceName
        } else {
            android.os.Build.MODEL ?: "Unknown Device"
        }
    }

    fun cleanup() {
        mqttClient.disconnect()
    }
}

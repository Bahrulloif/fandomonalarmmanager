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
    private val commandHandler = CommandHandler(context)

    suspend fun syncEvents() {
        try {
            val unsentEvents = eventRepository.getUnsentEvents()
            if (unsentEvents.isEmpty()) {
                Log.d(TAG, "No unsent events")
                return
            }

            Log.d(TAG, "📤 Syncing ${unsentEvents.size} unsent events in chronological order")

            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val mqttEnabled = preferences.mqttEnabled.first()
            val restEnabled = preferences.restEnabled.first()

            for (event in unsentEvents) {
                Log.d(TAG, "📤 Sending event #${event.id}: ${event.eventType} (timestamp: ${event.timestamp})")
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
                // Wait for connection with proper coroutine delay
                kotlinx.coroutines.delay(2000)
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

            kotlinx.coroutines.delay(1000) // Wait for publish
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

    /**
     * Subscribe to MQTT commands topics for remote control
     *
     * Subscribes to TWO topics:
     * 1. Broadcast topic (general commands for all devices): fandomon/commands
     * 2. Device-specific topic (targeted commands): fandomon/{device_id}/commands
     */
    suspend fun subscribeToCommands() {
        try {
            val mqttEnabled = preferences.mqttEnabled.first()
            if (!mqttEnabled) {
                Log.d(TAG, "MQTT is disabled, skipping command subscription")
                return
            }

            val brokerUrl = preferences.mqttBrokerUrl.first()
            val port = preferences.mqttPort.first()
            val username = preferences.mqttUsername.first()
            val password = preferences.mqttPassword.first()

            if (brokerUrl.isEmpty()) {
                Log.w(TAG, "MQTT broker URL is not configured")
                return
            }

            // Connect to MQTT if not already connected
            if (!mqttClient.isConnected()) {
                var connected = false
                mqttClient.connect(
                    brokerUrl = brokerUrl,
                    port = port,
                    username = username,
                    password = password,
                    onSuccess = {
                        connected = true
                        Log.d(TAG, "✅ Connected to MQTT broker for commands")
                    },
                    onFailure = {
                        connected = false
                        Log.e(TAG, "❌ Failed to connect to MQTT broker")
                    }
                )
                kotlinx.coroutines.delay(2000) // Wait for connection
                if (!connected) return
            }

            val deviceId = getDeviceId()

            // Subscribe to BROADCAST commands topic (all devices)
            val broadcastTopic = preferences.mqttTopicCommands.first()
            mqttClient.subscribe(
                topic = broadcastTopic,
                qos = 1,
                onMessageReceived = { topic, message ->
                    Log.d(TAG, "📥 BROADCAST command received on [$topic]: $message")
                    handleIncomingCommand(message, isBroadcast = true)
                },
                onSuccess = {
                    Log.d(TAG, "✅ Subscribed to BROADCAST commands: $broadcastTopic")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to subscribe to broadcast commands: ${error.message}")
                }
            )

            // Subscribe to DEVICE-SPECIFIC commands topic
            val deviceSpecificTopic = "fandomon/$deviceId/commands"
            mqttClient.subscribe(
                topic = deviceSpecificTopic,
                qos = 1,
                onMessageReceived = { topic, message ->
                    Log.d(TAG, "📥 TARGETED command received on [$topic]: $message")
                    handleIncomingCommand(message, isBroadcast = false)
                },
                onSuccess = {
                    Log.d(TAG, "✅ Subscribed to DEVICE-SPECIFIC commands: $deviceSpecificTopic")
                    Log.d(TAG, "📌 This device will respond to commands sent to: $deviceSpecificTopic")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to subscribe to device-specific commands: ${error.message}")
                }
            )

            Log.d(TAG, "")
            Log.d(TAG, "📡 MQTT Command Subscription Summary:")
            Log.d(TAG, "  • Broadcast topic: $broadcastTopic (for ALL devices)")
            Log.d(TAG, "  • Device-specific topic: $deviceSpecificTopic (for THIS device only)")
            Log.d(TAG, "  • Device ID: $deviceId")
            Log.d(TAG, "")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to commands", e)
        }
    }

    private fun handleIncomingCommand(message: String, isBroadcast: Boolean) {
        try {
            val commandSource = if (isBroadcast) "BROADCAST" else "TARGETED"
            Log.d(TAG, "🔍 Parsing $commandSource command: $message")

            val command = commandHandler.parseCommand(message)

            if (command != null) {
                Log.d(TAG, "✅ Command parsed successfully: ${command.command} (source: $commandSource)")
                commandHandler.executeCommand(command)
            } else {
                Log.w(TAG, "⚠️ Failed to parse $commandSource command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command: ${e.message}", e)
        }
    }

    fun cleanup() {
        mqttClient.disconnect()
    }
}

package com.tastamat.fandomon.data.remote.mqtt

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClientManager private constructor(private val context: Context) {

    private var mqttClient: MqttClient? = null
    private val TAG = "MqttClientManager"

    companion object {
        @Volatile
        private var INSTANCE: MqttClientManager? = null

        fun getInstance(context: Context): MqttClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttClientManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun connect(
        brokerUrl: String,
        port: Int,
        username: String,
        password: String,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        try {
            // If already connected, reuse existing connection
            if (mqttClient?.isConnected == true) {
                Log.d(TAG, "✅ Already connected to MQTT broker - reusing connection")
                onSuccess()
                return
            }

            val serverUri = "tcp://$brokerUrl:$port"
            // Use FIXED client ID based on device
            val clientId = "Fandomon_${android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )}"

            // Only create NEW client if none exists OR if connection is permanently closed
            if (mqttClient == null || mqttClient?.isConnected == false) {
                // Check if we can reuse existing client with auto-reconnect
                if (mqttClient != null) {
                    try {
                        // Try to reconnect existing client (auto-reconnect may be in progress)
                        Log.d(TAG, "🔄 Attempting to reconnect existing MQTT client...")
                        val options = MqttConnectOptions()
                        options.isCleanSession = false  // Preserve subscriptions
                        options.userName = username
                        options.password = password.toCharArray()
                        options.connectionTimeout = 30
                        options.keepAliveInterval = 60
                        options.isAutomaticReconnect = true  // Auto-reconnect on connection loss
                        options.maxReconnectDelay = 30000  // Max 30 seconds between reconnect attempts

                        mqttClient?.connect(options)
                        Log.d(TAG, "✅ Reconnected existing MQTT client")
                        onSuccess()
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to reconnect existing client, creating new one: ${e.message}")
                        // Fall through to create new client
                    }
                }

                // Create new client only if reconnect failed or no client exists
                Log.d(TAG, "🆕 Creating new MQTT client...")
                mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            }

            val options = MqttConnectOptions()
            options.isCleanSession = false  // Preserve subscriptions
            options.userName = username
            options.password = password.toCharArray()
            options.connectionTimeout = 30
            options.keepAliveInterval = 60
            options.isAutomaticReconnect = true  // Auto-reconnect on connection loss
            options.maxReconnectDelay = 30000  // Max 30 seconds between reconnect attempts

            mqttClient?.connect(options)
            Log.d(TAG, "Connected to MQTT broker with client ID: $clientId")
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to MQTT", e)
            onFailure(e)
        }
    }

    fun publish(
        topic: String,
        message: String,
        qos: Int = 1,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        try {
            if (mqttClient?.isConnected != true) {
                Log.w(TAG, "MQTT client is not connected")
                onFailure(Exception("MQTT client is not connected"))
                return
            }

            val mqttMessage = MqttMessage()
            mqttMessage.payload = message.toByteArray()
            mqttMessage.qos = qos
            mqttMessage.isRetained = false

            mqttClient?.publish(topic, mqttMessage)
            Log.d(TAG, "Message published to topic: $topic")
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing message", e)
            onFailure(e)
        }
    }

    fun subscribe(
        topic: String,
        qos: Int = 1,
        onMessageReceived: (String, String) -> Unit,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        try {
            if (mqttClient?.isConnected != true) {
                Log.w(TAG, "MQTT client is not connected")
                onFailure(Exception("MQTT client is not connected"))
                return
            }

            // Set callback for incoming messages
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "⚠️ MQTT connection lost: ${cause?.message}")
                    Log.i(TAG, "🔄 Auto-reconnect is enabled - client will reconnect automatically")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        val payload = String(message.payload)
                        Log.d(TAG, "Message received on topic [$topic]: $payload")
                        onMessageReceived(topic, payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Not used for subscribe
                }
            })

            mqttClient?.subscribe(topic, qos)
            Log.d(TAG, "Subscribed to topic: $topic")
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic", e)
            onFailure(e)
        }
    }

    fun unsubscribe(topic: String) {
        try {
            mqttClient?.unsubscribe(topic)
            Log.d(TAG, "Unsubscribed from topic: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from topic", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            Log.d(TAG, "Disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from MQTT", e)
        }
    }

    fun isConnected(): Boolean = mqttClient?.isConnected == true
}

package com.tastamat.fandomon.data.remote.mqtt

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClientManager(private val context: Context) {

    private var mqttClient: MqttClient? = null
    private val TAG = "MqttClientManager"

    fun connect(
        brokerUrl: String,
        port: Int,
        username: String,
        password: String,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        try {
            val serverUri = "tcp://$brokerUrl:$port"
            val clientId = "Fandomon_${System.currentTimeMillis()}"

            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.userName = username
            options.password = password.toCharArray()
            options.connectionTimeout = 30
            options.keepAliveInterval = 60

            mqttClient?.connect(options)
            Log.d(TAG, "Connected to MQTT broker")
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

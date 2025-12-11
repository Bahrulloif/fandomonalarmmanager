package com.tastamat.fandomon.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tastamat.fandomon.MainActivity
import com.tastamat.fandomon.R
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.receiver.ScreenStateReceiver
import com.tastamat.fandomon.receiver.NetworkChangeReceiver
import com.tastamat.fandomon.receiver.PowerConnectionReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground Service that maintains persistent MQTT connection
 *
 * This service ensures:
 * - MQTT connection stays alive even when screen is off
 * - Remote commands work 24/7
 * - Android doesn't kill the connection due to Doze Mode
 * - App can monitor and control Fandomat in background
 *
 * The service shows a persistent notification (required by Android)
 * and is exempt from battery optimization restrictions.
 */
class MqttForegroundService : Service() {

    private val TAG = "MqttForegroundService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "mqtt_service_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var dataSyncService: DataSyncService? = null
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var networkChangeReceiver: NetworkChangeReceiver? = null
    private var powerConnectionReceiver: PowerConnectionReceiver? = null

    companion object {
        private const val ACTION_START = "com.tastamat.fandomon.START_MQTT_SERVICE"
        private const val ACTION_STOP = "com.tastamat.fandomon.STOP_MQTT_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 MQTT Foreground Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))

        dataSyncService = DataSyncService(applicationContext)

        // Register BroadcastReceivers dynamically (required for Android 7.0+)
        registerScreenStateReceiver()
        registerNetworkChangeReceiver()
        registerPowerConnectionReceiver()
    }

    private fun registerScreenStateReceiver() {
        try {
            screenStateReceiver = ScreenStateReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenStateReceiver, filter)
            Log.d(TAG, "📱 ScreenStateReceiver registered dynamically")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering ScreenStateReceiver: ${e.message}", e)
        }
    }

    private fun registerNetworkChangeReceiver() {
        try {
            networkChangeReceiver = NetworkChangeReceiver()
            val filter = IntentFilter().apply {
                addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            }
            registerReceiver(networkChangeReceiver, filter)
            Log.d(TAG, "🌐 NetworkChangeReceiver registered dynamically")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering NetworkChangeReceiver: ${e.message}", e)
        }
    }

    private fun registerPowerConnectionReceiver() {
        try {
            powerConnectionReceiver = PowerConnectionReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(powerConnectionReceiver, filter)
            Log.d(TAG, "🔌 PowerConnectionReceiver registered dynamically")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering PowerConnectionReceiver: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📡 Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startMqttConnection()
            }
            ACTION_STOP -> {
                stopMqttConnection()
                stopSelf()
            }
            else -> {
                startMqttConnection()
            }
        }

        // If service is killed by system, restart it
        return START_STICKY
    }

    private fun startMqttConnection() {
        serviceScope.launch {
            try {
                val preferences = AppPreferences(applicationContext)
                val mqttEnabled = preferences.mqttEnabled.first()

                if (!mqttEnabled) {
                    Log.d(TAG, "⚠️ MQTT is disabled in settings")
                    updateNotification("MQTT Disabled")
                    return@launch
                }

                Log.d(TAG, "🔌 Connecting to MQTT broker...")
                updateNotification("Connecting to MQTT...")

                // Subscribe to commands - this also handles connection
                dataSyncService?.subscribeToCommands()

                Log.d(TAG, "✅ MQTT connection established")
                updateNotification("MQTT Connected - Remote control active")

                // Keep monitoring connection status
                monitorConnectionHealth()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting MQTT: ${e.message}", e)
                updateNotification("MQTT Error: ${e.message}")
            }
        }
    }

    private fun monitorConnectionHealth() {
        serviceScope.launch {
            while (isActive) {
                delay(60000) // Check every minute

                try {
                    val preferences = AppPreferences(applicationContext)
                    val mqttEnabled = preferences.mqttEnabled.first()

                    if (!mqttEnabled) {
                        Log.d(TAG, "MQTT disabled, stopping health monitor")
                        updateNotification("MQTT Disabled")
                        break
                    }

                    // Check if connection is still alive
                    val mqttManager = com.tastamat.fandomon.data.remote.mqtt.MqttClientManager.getInstance(applicationContext)
                    if (mqttManager.isConnected()) {
                        Log.d(TAG, "💚 MQTT connection healthy")
                        updateNotification("MQTT Connected - Remote control active")
                    } else {
                        Log.w(TAG, "⚠️ MQTT connection lost, attempting reconnect...")
                        updateNotification("MQTT Reconnecting...")
                        dataSyncService?.subscribeToCommands()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring connection: ${e.message}", e)
                }
            }
        }
    }

    private fun stopMqttConnection() {
        Log.d(TAG, "🛑 Stopping MQTT connection")
        dataSyncService?.cleanup()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains MQTT connection for remote control"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fandomon Remote Control")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "🔚 Service destroyed")
        stopMqttConnection()

        // Unregister all BroadcastReceivers
        try {
            if (screenStateReceiver != null) {
                unregisterReceiver(screenStateReceiver)
                Log.d(TAG, "📱 ScreenStateReceiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering ScreenStateReceiver: ${e.message}", e)
        }

        try {
            if (networkChangeReceiver != null) {
                unregisterReceiver(networkChangeReceiver)
                Log.d(TAG, "🌐 NetworkChangeReceiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering NetworkChangeReceiver: ${e.message}", e)
        }

        try {
            if (powerConnectionReceiver != null) {
                unregisterReceiver(powerConnectionReceiver)
                Log.d(TAG, "🔌 PowerConnectionReceiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering PowerConnectionReceiver: ${e.message}", e)
        }

        super.onDestroy()
    }
}

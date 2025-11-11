package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.DataSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver that handles screen on/off events for MQTT reconnection
 *
 * When screen turns ON:
 * - Reconnects MQTT to ensure remote control is available
 * - Resubscribes to command topics
 *
 * This ensures MQTT works reliably after device wakes from sleep,
 * without requiring background network activity while screen is off.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    private val TAG = "ScreenStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "📱 Screen turned ON - reconnecting MQTT for remote control")
                handleScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "📴 Screen turned OFF - MQTT will disconnect naturally")
                // Do nothing - let Android disconnect MQTT to save battery
                // We'll reconnect when screen turns on
            }
        }
    }

    private fun handleScreenOn(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val preferences = AppPreferences(context)
                val mqttEnabled = preferences.mqttEnabled.first()

                if (!mqttEnabled) {
                    Log.d(TAG, "MQTT is disabled, skipping reconnection")
                    return@launch
                }

                Log.d(TAG, "🔄 Initiating MQTT reconnection after screen wake...")

                // Give Android a moment to restore network connectivity
                kotlinx.coroutines.delay(2000)

                // Reconnect and resubscribe to commands
                val syncService = DataSyncService(context)
                syncService.subscribeToCommands()

                Log.d(TAG, "✅ MQTT reconnection initiated - device ready for remote control")
            } catch (e: Exception) {
                Log.e(TAG, "Error reconnecting MQTT on screen wake: ${e.message}", e)
            }
        }
    }
}

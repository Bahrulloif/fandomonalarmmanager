package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver that handles screen on/off events
 *
 * Logs screen state changes to database for monitoring:
 * - ACTION_SCREEN_OFF → SCREEN_OFF event
 * - ACTION_SCREEN_ON → SCREEN_ON event
 *
 * This helps track device usage and debug issues related to sleep mode.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    private val TAG = "ScreenStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "📱 Screen turned ON")
                logScreenEvent(context, EventType.SCREEN_ON, "Screen turned on")
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "📴 Screen turned OFF")
                logScreenEvent(context, EventType.SCREEN_OFF, "Screen turned off")
            }
        }
    }

    private fun logScreenEvent(context: Context, eventType: EventType, message: String) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val database = FandomonDatabase.getDatabase(context)
                val eventDao = database.eventDao()

                val event = MonitorEvent(
                    eventType = eventType,
                    timestamp = System.currentTimeMillis(),
                    message = message,
                    isSent = false
                )

                eventDao.insertEvent(event)
                Log.d(TAG, "✅ Screen event logged: $eventType")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error logging screen event: ${e.message}", e)
            }
        }
    }
}

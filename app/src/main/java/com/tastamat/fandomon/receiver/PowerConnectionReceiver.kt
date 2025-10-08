package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import com.tastamat.fandomon.data.repository.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerConnectionReceiver : BroadcastReceiver() {

    private val TAG = "PowerConnectionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventRepository = EventRepository(
                    FandomonDatabase.getDatabase(context).eventDao()
                )

                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d(TAG, "Power connected")
                        val event = MonitorEvent(
                            eventType = EventType.POWER_RESTORED,
                            message = "Power restored to device"
                        )
                        eventRepository.insertEvent(event)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d(TAG, "Power disconnected")
                        val event = MonitorEvent(
                            eventType = EventType.POWER_OUTAGE,
                            message = "Power disconnected from device"
                        )
                        eventRepository.insertEvent(event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power connection change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import com.tastamat.fandomon.data.repository.EventRepository
import com.tastamat.fandomon.service.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkChangeReceiver : BroadcastReceiver() {

    private val TAG = "NetworkChangeReceiver"

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        // Note: CONNECTIVITY_ACTION is deprecated in API 28+
        // For API 28+ consider using NetworkCallback instead
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val isConnected = NetworkUtils.isInternetAvailable(context)
                    Log.d(TAG, "Network status changed. Connected: $isConnected")

                    val eventRepository = EventRepository(
                        FandomonDatabase.getDatabase(context).eventDao()
                    )

                    val event = if (isConnected) {
                        MonitorEvent(
                            eventType = EventType.INTERNET_CONNECTED,
                            message = "Internet connection restored"
                        )
                    } else {
                        MonitorEvent(
                            eventType = EventType.INTERNET_DISCONNECTED,
                            message = "Internet connection lost"
                        )
                    }

                    eventRepository.insertEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling network change", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

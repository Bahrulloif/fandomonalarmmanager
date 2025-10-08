package com.tastamat.fandomon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitoringReceiver : BroadcastReceiver() {

    private val TAG = "MonitoringReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received alarm: ${intent.action}")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CHECK_FANDOMAT -> {
                        checkFandomat(context)
                    }
                    ACTION_SEND_STATUS -> {
                        sendStatus(context)
                    }
                    ACTION_SYNC_EVENTS -> {
                        syncEvents(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkFandomat(context: Context) {
        Log.d(TAG, "Checking Fandomat status")
        val monitor = FandomatMonitor(context)
        monitor.checkFandomatStatus()
    }

    private suspend fun sendStatus(context: Context) {
        Log.d(TAG, "Sending status")
        val monitor = FandomatMonitor(context)
        val fandomatRunning = monitor.checkFandomatStatus()

        val internetConnected = NetworkUtils.isInternetAvailable(context)

        val syncService = DataSyncService(context)
        syncService.sendStatus(fandomatRunning, internetConnected)
        syncService.cleanup()
    }

    private suspend fun syncEvents(context: Context) {
        Log.d(TAG, "Syncing events")
        val syncService = DataSyncService(context)
        syncService.syncEvents()
        syncService.cleanup()
    }

    companion object {
        const val ACTION_CHECK_FANDOMAT = "com.tastamat.fandomon.CHECK_FANDOMAT"
        const val ACTION_SEND_STATUS = "com.tastamat.fandomon.SEND_STATUS"
        const val ACTION_SYNC_EVENTS = "com.tastamat.fandomon.SYNC_EVENTS"
    }
}

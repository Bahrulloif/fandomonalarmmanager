package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, scheduling monitoring alarms")

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val preferences = AppPreferences(context)
                    val checkInterval = preferences.checkIntervalMinutes.first()
                    val statusInterval = preferences.statusReportIntervalMinutes.first()

                    val scheduler = AlarmScheduler(context)
                    scheduler.scheduleMonitoring(checkInterval, statusInterval)

                    Log.d(TAG, "Monitoring alarms scheduled after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling alarms after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

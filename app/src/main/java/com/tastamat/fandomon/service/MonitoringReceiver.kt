package com.tastamat.fandomon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MonitoringReceiver : BroadcastReceiver() {

    private val TAG = "MonitoringReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 ============================================")
        Log.d(TAG, "🔔 Received alarm: ${intent.action}")
        Log.d(TAG, "🔔 Time: ${System.currentTimeMillis()}")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CHECK_FANDOMAT -> {
                        Log.d(TAG, "🔔 Executing CHECK_FANDOMAT action")
                        checkFandomat(context)
                        // Reschedule next check (because setExactAndAllowWhileIdle is one-time)
                        rescheduleNextAlarm(context, ACTION_CHECK_FANDOMAT)
                    }
                    ACTION_SEND_STATUS -> {
                        Log.d(TAG, "🔔 Executing SEND_STATUS action")
                        sendStatus(context)
                        // Reschedule next status report
                        rescheduleNextAlarm(context, ACTION_SEND_STATUS)
                    }
                    ACTION_SYNC_EVENTS -> {
                        Log.d(TAG, "🔔 Executing SYNC_EVENTS action")
                        syncEvents(context)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Unknown action: ${intent.action}")
                    }
                }
                Log.d(TAG, "🔔 Action completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in receiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
                Log.d(TAG, "🔔 ============================================")
            }
        }
    }

    /**
     * Reschedule the next alarm execution
     * Required because setExactAndAllowWhileIdle() is one-time only
     */
    private suspend fun rescheduleNextAlarm(context: Context, action: String) {
        try {
            val preferences = com.tastamat.fandomon.data.preferences.AppPreferences(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            val (intervalMinutes, requestCode) = when (action) {
                ACTION_CHECK_FANDOMAT -> {
                    Pair(preferences.checkIntervalMinutes.first(), 1001)
                }
                ACTION_SEND_STATUS -> {
                    Pair(preferences.statusReportIntervalMinutes.first(), 1002)
                }
                else -> return
            }

            val intent = Intent(context, MonitoringReceiver::class.java).apply {
                this.action = action
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val intervalMillis = intervalMinutes * 60 * 1000L
            val triggerTime = System.currentTimeMillis() + intervalMillis

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                val nextTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))
                Log.d(TAG, "🔄 Rescheduled $action for $nextTime (in $intervalMinutes minutes)")
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "🔄 Rescheduled $action in $intervalMinutes minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling alarm: ${e.message}", e)
        }
    }

    private suspend fun checkFandomat(context: Context) {
        Log.d(TAG, "Checking Fandomat status")
        val monitor = FandomatMonitor(context)
        monitor.checkFandomatStatus()
    }

    private suspend fun sendStatus(context: Context) {
        Log.d(TAG, "Sending status")

        // Use FandomatMonitor to check status (same logic as monitoring checks)
        val monitor = FandomatMonitor(context)
        val fandomatRunning = monitor.checkFandomatStatus()

        Log.d(TAG, "Status report: Fandomat running in foreground = $fandomatRunning")

        val internetConnected = NetworkUtils.isInternetAvailable(context)

        val syncService = DataSyncService(context)
        syncService.sendStatus(fandomatRunning, internetConnected)
        // DO NOT call cleanup() - it disconnects MQTT and breaks remote command reception
        // syncService.cleanup()
    }

    private suspend fun syncEvents(context: Context) {
        Log.d(TAG, "Syncing events")
        val syncService = DataSyncService(context)
        syncService.syncEvents()
        // DO NOT call cleanup() - it disconnects MQTT and breaks remote command reception
        // syncService.cleanup()
    }

    companion object {
        const val ACTION_CHECK_FANDOMAT = "com.tastamat.fandomon.CHECK_FANDOMAT"
        const val ACTION_SEND_STATUS = "com.tastamat.fandomon.SEND_STATUS"
        const val ACTION_SYNC_EVENTS = "com.tastamat.fandomon.SYNC_EVENTS"
    }
}

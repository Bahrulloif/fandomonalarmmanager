package com.tastamat.fandomon.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmScheduler(private val context: Context) {

    private val TAG = "AlarmScheduler"
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleMonitoring(checkIntervalMinutes: Int, statusIntervalMinutes: Int) {
        scheduleFandomatCheck(checkIntervalMinutes)
        scheduleStatusReport(statusIntervalMinutes)
        scheduleEventSync(5) // Sync events every 5 minutes
    }

    private fun scheduleFandomatCheck(intervalMinutes: Int) {
        val intent = Intent(context, MonitoringReceiver::class.java).apply {
            action = MonitoringReceiver.ACTION_CHECK_FANDOMAT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CHECK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled Fandomat check every $intervalMinutes minutes")
            } else {
                Log.w(TAG, "Cannot schedule exact alarms. Using inexact alarm.")
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled Fandomat check every $intervalMinutes minutes")
        }
    }

    private fun scheduleStatusReport(intervalMinutes: Int) {
        val intent = Intent(context, MonitoringReceiver::class.java).apply {
            action = MonitoringReceiver.ACTION_SEND_STATUS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STATUS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled status report every $intervalMinutes minutes")
            } else {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled status report every $intervalMinutes minutes")
        }
    }

    private fun scheduleEventSync(intervalMinutes: Int) {
        val intent = Intent(context, MonitoringReceiver::class.java).apply {
            action = MonitoringReceiver.ACTION_SYNC_EVENTS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SYNC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            intervalMillis,
            pendingIntent
        )
        Log.d(TAG, "Scheduled event sync every $intervalMinutes minutes")
    }

    fun cancelAllAlarms() {
        cancelAlarm(REQUEST_CODE_CHECK, MonitoringReceiver.ACTION_CHECK_FANDOMAT)
        cancelAlarm(REQUEST_CODE_STATUS, MonitoringReceiver.ACTION_SEND_STATUS)
        cancelAlarm(REQUEST_CODE_SYNC, MonitoringReceiver.ACTION_SYNC_EVENTS)
        Log.d(TAG, "Cancelled all alarms")
    }

    private fun cancelAlarm(requestCode: Int, action: String) {
        val intent = Intent(context, MonitoringReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE_CHECK = 1001
        private const val REQUEST_CODE_STATUS = 1002
        private const val REQUEST_CODE_SYNC = 1003
    }
}

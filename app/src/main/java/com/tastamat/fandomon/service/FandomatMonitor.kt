package com.tastamat.fandomon.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.data.repository.EventRepository
import kotlinx.coroutines.flow.first

class FandomatMonitor(private val context: Context) {

    private val TAG = "FandomatMonitor"
    private val eventRepository = EventRepository(FandomonDatabase.getDatabase(context).eventDao())
    private val preferences = AppPreferences(context)

    suspend fun checkFandomatStatus(): Boolean {
        val packageName = preferences.fandomatPackageName.first()
        val isRunning = isAppRunning(packageName)

        Log.d(TAG, "Fandomat ($packageName) running: $isRunning")

        if (!isRunning) {
            // Log event
            val event = MonitorEvent(
                eventType = EventType.FANDOMAT_STOPPED,
                message = "Fandomat application stopped"
            )
            eventRepository.insertEvent(event)

            // Try to restart
            restartFandomat(packageName)
        }

        return isRunning
    }

    private fun isAppRunning(packageName: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningApps = activityManager.runningAppProcesses ?: return false

        return runningApps.any { it.processName == packageName }
    }

    private suspend fun restartFandomat(packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

                // Log restart event
                val event = MonitorEvent(
                    eventType = EventType.FANDOMAT_RESTARTED,
                    message = "Fandomat application restarted"
                )
                eventRepository.insertEvent(event)

                Log.d(TAG, "Fandomat restarted successfully")
            } else {
                Log.e(TAG, "Cannot find launch intent for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting Fandomat", e)
        }
    }
}

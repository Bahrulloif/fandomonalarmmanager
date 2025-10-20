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

        // Get preferences to check app package
        val preferences = com.tastamat.fandomon.data.preferences.AppPreferences(context)
        val packageName = preferences.fandomatPackageName.first()

        // Check if app is in foreground (not just running in background)
        var fandomatRunning = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val currentTime = System.currentTimeMillis()

                // Method 1: Check recent events (last 1 minute)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val queryTime = currentTime - (60 * 1000)
                    val events = usageStatsManager.queryEvents(queryTime, currentTime)
                    val foregroundHistory = mutableListOf<Pair<String, Long>>()

                    while (events.hasNextEvent()) {
                        val event = android.app.usage.UsageEvents.Event()
                        events.getNextEvent(event)

                        // Track ACTIVITY_RESUMED (app moved to foreground)
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                            foregroundHistory.add(Pair(event.packageName, event.timeStamp))
                        }
                    }

                    if (foregroundHistory.isNotEmpty()) {
                        // Sort by timestamp descending (most recent first)
                        foregroundHistory.sortByDescending { it.second }

                        // Get the most recent foreground app
                        var lastForegroundPackage = foregroundHistory[0].first
                        var lastEventTime = foregroundHistory[0].second

                        // If the last foreground app is Fandomon itself, check the previous one
                        // This prevents false negatives when user opens Fandomon settings
                        if (lastForegroundPackage == context.packageName) {
                            if (foregroundHistory.size > 1) {
                                Log.d(TAG, "⚠️ Last foreground is Fandomon itself, checking previous app")
                                lastForegroundPackage = foregroundHistory[1].first
                                lastEventTime = foregroundHistory[1].second
                            } else {
                                // Only Fandomon in history, need to use UsageStats fallback
                                Log.d(TAG, "⚠️ Only Fandomon in event history, will use UsageStats fallback")
                                // Don't set fandomatRunning here, fall through to UsageStats check
                            }
                        }

                        // If we found a valid app (not just Fandomon), use that result
                        if (lastForegroundPackage != context.packageName) {
                            val secondsAgo = (currentTime - lastEventTime) / 1000
                            Log.d(TAG, "📱 Last foreground app (via events): $lastForegroundPackage (${secondsAgo}s ago)")

                            fandomatRunning = lastForegroundPackage == packageName
                            Log.d(TAG, "📱 Target app ($packageName) is ${if (fandomatRunning) "in FOREGROUND" else "in BACKGROUND or NOT RUNNING"}")
                        }
                    }

                    // If we still haven't determined status (no events or only Fandomon), use UsageStats
                    if (!fandomatRunning && (foregroundHistory.isEmpty() ||
                        (foregroundHistory.isNotEmpty() && foregroundHistory[0].first == context.packageName && foregroundHistory.size == 1))) {
                        // Method 2: If no events found, use UsageStats to find most recently used app
                        Log.d(TAG, "⚠️ Checking UsageStats for current foreground app")
                        val statsQueryTime = currentTime - (5 * 60 * 1000) // Last 5 minutes
                        val usageStats = usageStatsManager.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_BEST,
                            statsQueryTime,
                            currentTime
                        )

                        if (usageStats != null && usageStats.isNotEmpty()) {
                            Log.d(TAG, "📊 Found ${usageStats.size} apps in UsageStats")

                            // Find the app with the most recent lastTimeUsed
                            val sortedStats = usageStats.sortedByDescending { it.lastTimeUsed }

                            // Log top 3 apps for debugging
                            sortedStats.take(3).forEachIndexed { index, stats ->
                                val secondsAgo = (currentTime - stats.lastTimeUsed) / 1000
                                Log.d(TAG, "  #${index + 1}: ${stats.packageName} (${secondsAgo}s ago)")
                            }

                            var mostRecentApp = sortedStats.firstOrNull()

                            // Skip Fandomon itself if it's the most recent
                            if (mostRecentApp?.packageName == context.packageName && sortedStats.size > 1) {
                                Log.d(TAG, "⚠️ Most recent is Fandomon itself, checking next")
                                mostRecentApp = sortedStats[1]
                            }

                            if (mostRecentApp != null) {
                                val secondsAgo = (currentTime - mostRecentApp.lastTimeUsed) / 1000
                                Log.d(TAG, "📱 Most recent app (via stats): ${mostRecentApp.packageName} (${secondsAgo}s ago)")

                                fandomatRunning = mostRecentApp.packageName == packageName
                                Log.d(TAG, "📱 Target app ($packageName) is ${if (fandomatRunning) "in FOREGROUND" else "in BACKGROUND or NOT RUNNING"}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ UsageStats query returned empty/null")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check foreground status: ${e.message}")
            }
        }

        Log.d(TAG, "Status report: Fandomat running in foreground = $fandomatRunning")

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

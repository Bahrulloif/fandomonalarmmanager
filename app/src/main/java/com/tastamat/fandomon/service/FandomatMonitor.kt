package com.tastamat.fandomon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tastamat.fandomon.R
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

    companion object {
        private const val RESTART_NOTIFICATION_CHANNEL_ID = "fandomon_restart_channel"
        private const val RESTART_NOTIFICATION_ID = 9999
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Restart Notifications"
            val descriptionText = "Critical notifications for restarting monitored applications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(RESTART_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Restart notification channel created")
        }
    }

    /**
     * Check if Fandomat is running in foreground.
     * If not running and auto-restart is enabled, attempts to restart the app.
     * Also logs events for remote monitoring.
     */
    suspend fun checkFandomatStatus(): Boolean {
        val packageName = preferences.fandomatPackageName.first()
        val autoRestartEnabled = preferences.autoRestartEnabled.first()

        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting Fandomat status check")
        Log.d(TAG, "Target package: $packageName")
        Log.d(TAG, "Auto-restart: ${if (autoRestartEnabled) "ENABLED ✅" else "DISABLED ❌"}")

        val isRunning = isAppInForeground(packageName)
        val isResponding = if (isRunning) checkIfAppResponding(packageName) else false

        Log.d(TAG, "Fandomat ($packageName) running in foreground: $isRunning")
        if (isRunning) {
            Log.d(TAG, "Fandomat responding: $isResponding")
        }

        // Check if app is frozen/not responding
        if (isRunning && !isResponding) {
            Log.w(TAG, "⚠️ Fandomat is running but NOT RESPONDING (frozen)")

            val event = MonitorEvent(
                eventType = EventType.FANDOMAT_NOT_RESPONDING,
                message = if (autoRestartEnabled) {
                    "Fandomat is frozen/not responding - attempting force restart"
                } else {
                    "Fandomat is frozen/not responding - remote restart required"
                }
            )
            eventRepository.insertEvent(event)
            Log.d(TAG, "Event FANDOMAT_NOT_RESPONDING saved to database")

            if (autoRestartEnabled) {
                Log.d(TAG, "🔄 Force closing and restarting frozen app...")
                forceStopAndRestart(packageName)
            }

            return false
        }

        if (!isRunning) {
            Log.w(TAG, "⚠️ Fandomat is NOT in foreground")

            // Log event
            val event = MonitorEvent(
                eventType = EventType.FANDOMAT_STOPPED,
                message = if (autoRestartEnabled) {
                    "Fandomat not in foreground - attempting automatic restart"
                } else {
                    "Fandomat not in foreground - remote restart required (auto-restart disabled)"
                }
            )
            eventRepository.insertEvent(event)
            Log.d(TAG, "Event FANDOMAT_STOPPED saved to database")

            // Attempt restart if enabled
            if (autoRestartEnabled) {
                Log.d(TAG, "🔄 Auto-restart is ENABLED, attempting restart...")
                val restartSuccess = restartFandomat(packageName)
                if (restartSuccess) {
                    Log.d(TAG, "✅ Restart command executed successfully")
                } else {
                    Log.e(TAG, "❌ Restart command failed")
                }
            } else {
                Log.d(TAG, "⏸️ Auto-restart is DISABLED, waiting for remote command")
            }
        } else {
            Log.d(TAG, "✅ Fandomat is running in foreground and responding")
        }

        Log.d(TAG, "========================================")
        return isRunning && isResponding
    }

    /**
     * Check if app is responding (not frozen)
     * Uses activity launch time to detect if app is stuck
     */
    private fun checkIfAppResponding(packageName: String): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val currentTime = System.currentTimeMillis()
                val queryTime = currentTime - (5 * 60 * 1000) // Last 5 minutes

                val usageStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    queryTime,
                    currentTime
                )

                val appStats = usageStats?.find { it.packageName == packageName }
                if (appStats != null) {
                    // Check if app has been in foreground for too long without any interaction
                    // If lastTimeUsed is more than 30 minutes old, app might be frozen
                    val timeSinceLastUse = currentTime - appStats.lastTimeUsed
                    val thirtyMinutes = 30 * 60 * 1000L

                    if (timeSinceLastUse > thirtyMinutes) {
                        Log.w(TAG, "⚠️ App hasn't been used for ${timeSinceLastUse / 60000} minutes - might be frozen")
                        return false
                    }
                }
            }

            // If we can't determine, assume it's responding
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is responding: ${e.message}", e)
            return true // Assume responding if we can't check
        }
    }

    /**
     * Force stop and restart frozen app
     */
    private suspend fun forceStopAndRestart(packageName: String) {
        try {
            // Log restarting event
            val restartingEvent = MonitorEvent(
                eventType = EventType.FANDOMAT_RESTARTING,
                message = "Force closing frozen Fandomat and attempting restart"
            )
            eventRepository.insertEvent(restartingEvent)

            // Try to force stop via shell command
            Log.d(TAG, "Force stopping $packageName...")
            try {
                val stopCommand = "am force-stop $packageName"
                val stopProcess = Runtime.getRuntime().exec(stopCommand)
                stopProcess.waitFor()
                Log.d(TAG, "✅ Force stop command executed")

                // Wait a bit before restarting
                Thread.sleep(2000)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Force stop via shell failed: ${e.message}")
            }

            // Now restart
            val restartSuccess = restartFandomat(packageName)

            if (restartSuccess) {
                // Wait to verify restart
                Thread.sleep(3000)

                val isNowRunning = isAppInForeground(packageName)
                if (isNowRunning) {
                    val successEvent = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                        message = "Fandomat successfully restarted after being frozen"
                    )
                    eventRepository.insertEvent(successEvent)
                    Log.d(TAG, "✅ Fandomat successfully restarted after being frozen")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in force stop and restart: ${e.message}", e)
        }
    }

    /**
     * Attempts to restart Fandomat application using multiple methods
     *
     * Android 10+ (API 29+) restricts background activity launches for security.
     * We use shell command as the primary method since it bypasses these restrictions.
     */
    private suspend fun restartFandomat(packageName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to restart $packageName...")

            // Log RESTARTING event first
            val restartingEvent = MonitorEvent(
                eventType = EventType.FANDOMAT_RESTARTING,
                message = "Attempting to restart Fandomat - sending notification to server"
            )
            eventRepository.insertEvent(restartingEvent)
            Log.d(TAG, "📤 Event FANDOMAT_RESTARTING saved and will be sent to server")

            // Give time for event to be synced
            Thread.sleep(1000)

            // Method 1: Try shell command (works on Android 10+)
            // This bypasses background activity launch restrictions
            try {
                val command = "am start -n $packageName/.MainActivity"
                Log.d(TAG, "Executing shell command: $command")

                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Log.d(TAG, "✅ Shell command executed successfully")

                    // Wait to verify app actually started
                    Thread.sleep(3000)

                    val isNowRunning = isAppInForeground(packageName)

                    if (isNowRunning) {
                        // Log SUCCESS event
                        val successEvent = MonitorEvent(
                            eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                            message = "Fandomat successfully restarted and now running"
                        )
                        eventRepository.insertEvent(successEvent)
                        Log.d(TAG, "✅ Fandomat is now running - SUCCESS event logged")
                        return true
                    } else {
                        // Command executed but app not running
                        val event = MonitorEvent(
                            eventType = EventType.FANDOMAT_RESTARTED,
                            message = "Restart command executed but app not detected in foreground (may need verification)"
                        )
                        eventRepository.insertEvent(event)
                        Log.w(TAG, "⚠️ Command executed but app not detected in foreground")
                        return true
                    }
                } else {
                    Log.w(TAG, "⚠️ Shell command failed with exit code: $exitCode, trying alternative method...")
                }
            } catch (shellError: Exception) {
                Log.w(TAG, "⚠️ Shell command error: ${shellError.message}, trying alternative method...")
            }

            // Method 2: Use Accessibility Service (FULLY AUTOMATIC on Android 10+)
            // This service can launch apps from background without user interaction
            if (AppLauncherAccessibilityService.isEnabled(context)) {
                Log.d(TAG, "🤖 Trying Accessibility Service for AUTOMATIC restart...")

                AppLauncherAccessibilityService.requestAppLaunch(context, packageName)

                // Give service time to launch the app
                Thread.sleep(3000)

                val isNowRunning = isAppInForeground(packageName)
                if (isNowRunning) {
                    val successEvent = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                        message = "Fandomat AUTO-restarted via Accessibility Service (no user interaction!)"
                    )
                    eventRepository.insertEvent(successEvent)
                    Log.d(TAG, "✅✅✅ Fandomat AUTO-restarted successfully - NO USER INTERACTION NEEDED!")
                    return true
                } else {
                    val event = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTARTED,
                        message = "Accessibility Service launch requested - app should start automatically"
                    )
                    eventRepository.insertEvent(event)
                    Log.d(TAG, "📱 Accessibility Service launch requested")
                    return true
                }
            } else {
                Log.w(TAG, "⚠️ Accessibility Service NOT enabled - CANNOT auto-restart without user tap")
                Log.w(TAG, "⚠️ Enable: Settings → Accessibility → Fandomon Auto Launcher")
            }

            // Method 3: Use high-priority notification with PendingIntent (requires user tap)
            // This is a fallback if Accessibility Service is not enabled
            Log.d(TAG, "Trying notification-based restart (REQUIRES USER TO TAP)...")
            val notificationSuccess = sendRestartNotification(packageName)

            if (notificationSuccess) {
                Log.d(TAG, "✅ Restart notification sent successfully")

                // Wait for user to tap notification
                Thread.sleep(2000)

                val isNowRunning = isAppInForeground(packageName)
                if (isNowRunning) {
                    val successEvent = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                        message = "Fandomat restarted via notification tap"
                    )
                    eventRepository.insertEvent(successEvent)
                    Log.d(TAG, "✅ Fandomat restarted via notification tap")
                    return true
                } else {
                    val event = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTARTED,
                        message = "Restart notification sent - WAITING FOR USER TO TAP"
                    )
                    eventRepository.insertEvent(event)
                    Log.d(TAG, "📱 Notification sent, WAITING FOR USER TO TAP")
                    return true
                }
            }

            // Method 3: Final fallback to startActivity (may not work on Android 10+ from background)
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                Log.d(TAG, "Launch intent found: $intent")
                context.startActivity(intent)

                // Wait and check
                Thread.sleep(3000)
                val isNowRunning = isAppInForeground(packageName)

                if (isNowRunning) {
                    val successEvent = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                        message = "Fandomat successfully restarted via launch intent"
                    )
                    eventRepository.insertEvent(successEvent)
                    Log.d(TAG, "✅ Fandomat restarted successfully via intent")
                    return true
                } else {
                    // Log attempt but uncertain
                    val event = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTARTED,
                        message = "Fandomat restart attempted via launch intent (may be blocked by Android 10+)"
                    )
                    eventRepository.insertEvent(event)
                    Log.d(TAG, "⚠️ startActivity() called but app not confirmed running")
                    return true
                }
            } else {
                Log.e(TAG, "❌ Launch intent not found for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting Fandomat: ${e.message}", e)

            // Log failed restart event
            val event = MonitorEvent(
                eventType = EventType.FANDOMAT_STOPPED,
                message = "Auto-restart failed: ${e.message} - manual intervention required"
            )
            eventRepository.insertEvent(event)
        }

        return false
    }

    /**
     * Send high-priority notification to restart the app
     * On Android 10+, notifications can trigger PendingIntents to start activities
     */
    private fun sendRestartNotification(packageName: String): Boolean {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Log.e(TAG, "❌ Cannot create notification: launch intent not found for $packageName")
                return false
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                pendingIntentFlags
            )

            // Get app name
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()

            val notification = NotificationCompat.Builder(context, RESTART_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("$appName stopped")
                .setContentText("Tap to restart $appName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // This allows launching even when locked
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)

            try {
                notificationManager.notify(RESTART_NOTIFICATION_ID, notification)
                Log.d(TAG, "✅ High-priority restart notification sent for $appName")
                return true
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Notification permission denied: ${e.message}")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending restart notification: ${e.message}", e)
            return false
        }
    }

    private fun isAppInForeground(packageName: String): Boolean {
        Log.d(TAG, "=== Checking if $packageName is in FOREGROUND ===")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val currentTime = System.currentTimeMillis()

                // Check recent events and UsageStats to find current foreground app
                // We check a reasonable time window to ensure we capture recent activity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    // Query events from last 1 minute
                    val eventQueryTime = currentTime - (60 * 1000)
                    val events = usageStatsManager.queryEvents(eventQueryTime, currentTime)
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
                        if (lastForegroundPackage == context.packageName) {
                            if (foregroundHistory.size > 1) {
                                Log.d(TAG, "⚠️ Last foreground is Fandomon itself, checking previous app")
                                lastForegroundPackage = foregroundHistory[1].first
                                lastEventTime = foregroundHistory[1].second
                            } else {
                                // Only Fandomon in recent history, check UsageStats
                                Log.d(TAG, "⚠️ Only Fandomon in recent events, checking UsageStats")
                            }
                        }

                        // If we found a valid app (not just Fandomon), return result
                        if (lastForegroundPackage != context.packageName) {
                            val secondsAgo = (currentTime - lastEventTime) / 1000
                            Log.d(TAG, "📱 Last foreground app (via events): $lastForegroundPackage (${secondsAgo}s ago)")

                            val isForeground = lastForegroundPackage == packageName
                            Log.d(TAG, "📱 Target app ($packageName) is ${if (isForeground) "in FOREGROUND" else "in BACKGROUND"}")
                            return isForeground
                        }
                    }
                }

                // Fallback: Use UsageStats to find most recently used app
                Log.d(TAG, "⚠️ Checking UsageStats for current foreground app")
                val statsQueryTime = currentTime - (5 * 60 * 1000) // Last 5 minutes
                val usageStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
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

                        val isForeground = mostRecentApp.packageName == packageName
                        Log.d(TAG, "📱 Target app ($packageName) is ${if (isForeground) "in FOREGROUND" else "in BACKGROUND"}")
                        return isForeground
                    }
                } else {
                    Log.w(TAG, "⚠️ UsageStats query returned empty/null")
                }

                Log.w(TAG, "⚠️ Could not determine foreground app")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking foreground status: ${e.message}", e)
            }
        }

        return false
    }
}

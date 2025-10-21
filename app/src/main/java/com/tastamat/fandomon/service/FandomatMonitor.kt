package com.tastamat.fandomon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
     * Uses Fandomat's heartbeat mechanism via messages.log file
     *
     * Fandomat writes log entries every N seconds (configured by user).
     * If no new log entry for more than 3 minutes → app is frozen/crashed
     */
    private fun checkIfAppResponding(packageName: String): Boolean {
        try {
            Log.d(TAG, "📊 Checking heartbeat for $packageName...")

            // Path to Fandomat's heartbeat log file
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(downloadDir, "messages.log")

            if (!logFile.exists()) {
                Log.w(TAG, "⚠️ Heartbeat log file not found: ${logFile.absolutePath}")
                // If file doesn't exist, assume app is responding (might be first run)
                return true
            }

            // Read last line from log file
            val lastLine = logFile.readLines().lastOrNull()
            if (lastLine == null || lastLine.isEmpty()) {
                Log.w(TAG, "⚠️ Heartbeat log file is empty")
                return true
            }

            // Parse timestamp from log line format: [2025-10-21 19:36:09] Log entry #2: application running normally
            val timestampRegex = """\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]""".toRegex()
            val matchResult = timestampRegex.find(lastLine)

            if (matchResult == null) {
                Log.w(TAG, "⚠️ Could not parse timestamp from log line: $lastLine")
                return true
            }

            val timestampStr = matchResult.groupValues[1]
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val lastHeartbeat = dateFormat.parse(timestampStr)

            if (lastHeartbeat == null) {
                Log.w(TAG, "⚠️ Could not parse date: $timestampStr")
                return true
            }

            // Calculate time since last heartbeat
            val currentTime = System.currentTimeMillis()
            val timeSinceHeartbeat = currentTime - lastHeartbeat.time
            val threeMinutes = 3 * 60 * 1000L

            val minutesAgo = timeSinceHeartbeat / 60000
            Log.d(TAG, "📊 Last heartbeat: $timestampStr (${minutesAgo} minutes ago)")

            if (timeSinceHeartbeat > threeMinutes) {
                Log.w(TAG, "⚠️ No heartbeat for $minutesAgo minutes - Fandomat might be FROZEN!")
                return false
            }

            Log.d(TAG, "✅ Heartbeat OK - last update ${minutesAgo} minutes ago")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error checking heartbeat: ${e.message}", e)
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
                kotlinx.coroutines.delay(2000)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Force stop via shell failed: ${e.message}")
            }

            // Now restart
            val restartSuccess = restartFandomat(packageName)

            if (restartSuccess) {
                // Wait to verify restart
                kotlinx.coroutines.delay(3000)

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
     * Priority order:
     * 1. Accessibility Service (FULLY AUTOMATIC on Android 10+) - BEST METHOD
     * 2. Shell command (works on some devices)
     * 3. Direct startActivity (may work on older Android)
     * 4. Notification (FALLBACK - requires user tap)
     */
    private suspend fun restartFandomat(packageName: String): Boolean {
        try {
            Log.d(TAG, "==============================================")
            Log.d(TAG, "Attempting to restart $packageName...")
            Log.d(TAG, "==============================================")

            // Log RESTARTING event first
            val restartingEvent = MonitorEvent(
                eventType = EventType.FANDOMAT_RESTARTING,
                message = "Attempting automatic restart of Fandomat"
            )
            eventRepository.insertEvent(restartingEvent)
            Log.d(TAG, "📤 Event FANDOMAT_RESTARTING saved")

            // Give time for event to be synced
            kotlinx.coroutines.delay(1000)

            // Method 1: PRIORITY - Use Accessibility Service (FULLY AUTOMATIC on Android 10+)
            // This is the BEST method - launches apps from background without user interaction
            if (AppLauncherAccessibilityService.isEnabled(context)) {
                Log.d(TAG, "")
                Log.d(TAG, "🤖🤖🤖 PRIORITY METHOD: Trying Accessibility Service...")
                Log.d(TAG, "🤖 This will restart the app AUTOMATICALLY without user interaction!")
                Log.d(TAG, "")

                AppLauncherAccessibilityService.requestAppLaunch(context, packageName)

                // Give service more time to launch the app
                kotlinx.coroutines.delay(5000)

                val isNowRunning = isAppInForeground(packageName)
                if (isNowRunning) {
                    val successEvent = MonitorEvent(
                        eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                        message = "✅ Fandomat AUTO-restarted via Accessibility Service (no user interaction!)"
                    )
                    eventRepository.insertEvent(successEvent)
                    Log.d(TAG, "")
                    Log.d(TAG, "✅✅✅ SUCCESS! Fandomat AUTO-restarted - NO USER INTERACTION NEEDED!")
                    Log.d(TAG, "==============================================")
                    return true
                } else {
                    Log.w(TAG, "⚠️ Accessibility Service launch requested but app not running yet, trying backup methods...")
                }
            } else {
                Log.w(TAG, "")
                Log.w(TAG, "⚠️⚠️⚠️ ACCESSIBILITY SERVICE NOT ENABLED!")
                Log.w(TAG, "⚠️⚠️⚠️ Automatic restart is DISABLED without it!")
                Log.w(TAG, "⚠️ Enable it: Settings → Accessibility → Fandomon Auto Launcher")
                Log.w(TAG, "⚠️ Without Accessibility Service, will try other methods and fallback to notification")
                Log.w(TAG, "")
            }

            // Method 2: Try shell command (works on some devices)
            try {
                val command = "am start -n $packageName/.MainActivity"
                Log.d(TAG, "Trying shell command: $command")

                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Log.d(TAG, "✅ Shell command executed")

                    // Wait to verify app actually started
                    kotlinx.coroutines.delay(3000)

                    val isNowRunning = isAppInForeground(packageName)

                    if (isNowRunning) {
                        val successEvent = MonitorEvent(
                            eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                            message = "Fandomat successfully restarted via shell command"
                        )
                        eventRepository.insertEvent(successEvent)
                        Log.d(TAG, "✅ Fandomat restarted via shell command")
                        Log.d(TAG, "==============================================")
                        return true
                    }
                } else {
                    Log.w(TAG, "⚠️ Shell command failed with exit code: $exitCode")
                }
            } catch (shellError: Exception) {
                Log.w(TAG, "⚠️ Shell command error: ${shellError.message}")
            }

            // Method 3: Try direct startActivity (may work on older Android versions)
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                    Log.d(TAG, "Trying direct startActivity...")
                    context.startActivity(intent)

                    // Wait and check
                    kotlinx.coroutines.delay(3000)
                    val isNowRunning = isAppInForeground(packageName)

                    if (isNowRunning) {
                        val successEvent = MonitorEvent(
                            eventType = EventType.FANDOMAT_RESTART_SUCCESS,
                            message = "Fandomat successfully restarted via launch intent"
                        )
                        eventRepository.insertEvent(successEvent)
                        Log.d(TAG, "✅ Fandomat restarted via launch intent")
                        Log.d(TAG, "==============================================")
                        return true
                    } else {
                        Log.w(TAG, "⚠️ startActivity() called but app not running (blocked by Android 10+)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ startActivity error: ${e.message}")
            }

            // Method 4: FALLBACK - Send notification (REQUIRES USER TO TAP)
            Log.w(TAG, "")
            Log.w(TAG, "⚠️⚠️⚠️ All automatic methods failed!")
            Log.w(TAG, "⚠️⚠️⚠️ Falling back to NOTIFICATION - USER MUST TAP IT!")
            Log.w(TAG, "")

            val notificationSuccess = sendRestartNotification(packageName)

            if (notificationSuccess) {
                val event = MonitorEvent(
                    eventType = EventType.FANDOMAT_RESTARTED,
                    message = "⚠️ Automatic restart failed - Notification sent - USER MUST TAP to restart app"
                )
                eventRepository.insertEvent(event)
                Log.w(TAG, "📱 Notification sent - WAITING FOR USER TO TAP")
                Log.d(TAG, "==============================================")
                return true  // We did what we could
            } else {
                Log.e(TAG, "❌ Failed to send notification!")
                val event = MonitorEvent(
                    eventType = EventType.FANDOMAT_STOPPED,
                    message = "❌ All restart methods failed including notification - manual intervention required"
                )
                eventRepository.insertEvent(event)
                Log.d(TAG, "==============================================")
                return false
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

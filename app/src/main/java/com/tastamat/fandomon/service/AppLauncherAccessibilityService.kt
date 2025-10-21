package com.tastamat.fandomon.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service for automatically launching apps
 * This is a legal way to launch apps from background on Android 10+
 *
 * Requires user to enable in Settings → Accessibility → Fandomon Auto Launcher
 */
class AppLauncherAccessibilityService : AccessibilityService() {

    private var handler: Handler? = null
    private var checkRunnable: Runnable? = null
    private var launchReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "AppLauncherService"
        private const val PREFS_NAME = "app_launcher_prefs"
        private const val KEY_PENDING_LAUNCH = "pending_launch_package"
        private const val ACTION_LAUNCH_APP = "com.tastamat.fandomon.LAUNCH_APP"
        private const val CHECK_INTERVAL_MS = 2000L // Check every 2 seconds

        /**
         * Request to launch an app automatically
         * This will be picked up by the accessibility service
         */
        fun requestAppLaunch(context: Context, packageName: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PENDING_LAUNCH, packageName).apply()
            Log.d(TAG, "📝 Requested launch for: $packageName")

            // Send broadcast to immediately trigger check
            val intent = Intent(ACTION_LAUNCH_APP)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            Log.d(TAG, "📡 Broadcast sent to trigger immediate check")
        }

        /**
         * Check if accessibility service is enabled
         */
        fun isEnabled(context: Context): Boolean {
            // Check both short form (/.service.ClassName) and full form (/com.package.service.ClassName)
            val shortForm = "${context.packageName}/.service.AppLauncherAccessibilityService"
            val fullForm = "${context.packageName}/${AppLauncherAccessibilityService::class.java.name}"

            val settingsValue = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            val isEnabled = settingsValue?.contains(shortForm) == true || settingsValue?.contains(fullForm) == true
            Log.d(TAG, "Accessibility Service enabled: $isEnabled (settings: $settingsValue)")
            return isEnabled
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ AppLauncherAccessibilityService connected")

        // Setup periodic check
        setupPeriodicCheck()

        // Setup broadcast receiver for immediate launch requests
        setupBroadcastReceiver()

        // Check for pending launch request immediately
        checkPendingLaunch()
    }

    private fun setupPeriodicCheck() {
        handler = Handler(Looper.getMainLooper())
        checkRunnable = object : Runnable {
            override fun run() {
                checkPendingLaunch()
                handler?.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        handler?.post(checkRunnable!!)
        Log.d(TAG, "✅ Periodic check started (every ${CHECK_INTERVAL_MS}ms)")
    }

    private fun setupBroadcastReceiver() {
        launchReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_LAUNCH_APP) {
                    Log.d(TAG, "📡 Received broadcast to check pending launch")
                    checkPendingLaunch()
                }
            }
        }

        val filter = IntentFilter(ACTION_LAUNCH_APP)
        registerReceiver(launchReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "✅ Broadcast receiver registered")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for our use case
        // But we can use this to check for pending launches periodically
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkPendingLaunch()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ AppLauncherAccessibilityService interrupted")
    }

    private fun checkPendingLaunch() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val packageToLaunch = prefs.getString(KEY_PENDING_LAUNCH, null)

            if (packageToLaunch != null && packageToLaunch.isNotEmpty()) {
                Log.d(TAG, "🚀 Found pending launch request for: $packageToLaunch")

                // Clear the request first
                prefs.edit().remove(KEY_PENDING_LAUNCH).apply()

                // Launch the app
                launchApp(packageToLaunch)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending launch: ${e.message}", e)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            Log.d(TAG, "🚀 Launching app TO FOREGROUND: $packageName")

            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                // Flags to ensure app comes to FOREGROUND (visible on screen)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)        // Create new task
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)       // Clear activities above
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)      // Don't duplicate if already on top
                intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) // Bring to front if exists
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) // Reset task state

                // Accessibility service CAN launch activities from background!
                // This will make the app VISIBLE on screen (foreground)
                startActivity(intent)

                Log.d(TAG, "✅ Successfully launched TO FOREGROUND: $packageName")
                Log.d(TAG, "📱 App should now be VISIBLE on screen")
            } else {
                Log.e(TAG, "❌ Launch intent not found for: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error launching app: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop periodic check
        checkRunnable?.let { handler?.removeCallbacks(it) }
        handler = null

        // Unregister broadcast receiver
        try {
            launchReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }

        Log.w(TAG, "⚠️ AppLauncherAccessibilityService destroyed")
    }
}

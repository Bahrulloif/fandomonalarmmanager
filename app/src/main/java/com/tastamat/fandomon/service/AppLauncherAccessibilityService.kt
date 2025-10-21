package com.tastamat.fandomon.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service for automatically launching apps
 * This is a legal way to launch apps from background on Android 10+
 *
 * Requires user to enable in Settings → Accessibility → Fandomon Auto Launcher
 */
class AppLauncherAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLauncherService"
        private const val PREFS_NAME = "app_launcher_prefs"
        private const val KEY_PENDING_LAUNCH = "pending_launch_package"

        /**
         * Request to launch an app automatically
         * This will be picked up by the accessibility service
         */
        fun requestAppLaunch(context: Context, packageName: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PENDING_LAUNCH, packageName).apply()
            Log.d(TAG, "📝 Requested launch for: $packageName")
        }

        /**
         * Check if accessibility service is enabled
         */
        fun isEnabled(context: Context): Boolean {
            val service = "${context.packageName}/${AppLauncherAccessibilityService::class.java.name}"
            val settingsValue = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingsValue?.contains(service) == true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ AppLauncherAccessibilityService connected")

        // Check for pending launch request
        checkPendingLaunch()
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
            Log.d(TAG, "🚀 Launching app: $packageName")

            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                // Accessibility service CAN launch activities from background!
                startActivity(intent)

                Log.d(TAG, "✅ Successfully launched: $packageName")
            } else {
                Log.e(TAG, "❌ Launch intent not found for: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error launching app: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "⚠️ AppLauncherAccessibilityService destroyed")
    }
}

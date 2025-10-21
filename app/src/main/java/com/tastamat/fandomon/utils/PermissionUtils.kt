package com.tastamat.fandomon.utils

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    private const val TAG = "PermissionUtils"

    /**
     * Check if the app has UsageStats permission
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            val hasPermission = mode == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "UsageStats permission: $hasPermission")
            hasPermission
        } catch (e: Exception) {
            Log.e(TAG, "Error checking UsageStats permission", e)
            false
        }
    }

    /**
     * Open system settings to grant UsageStats permission
     */
    fun requestUsageStatsPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opened UsageStats settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening UsageStats settings", e)
        }
    }

    /**
     * Check if notifications are enabled for the app
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return try {
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            Log.d(TAG, "Notifications enabled: $enabled")
            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification permission", e)
            true // Assume enabled if can't check
        }
    }

    /**
     * Open notification settings
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened notification settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
        }
    }

    /**
     * Check if app can schedule exact alarms
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val canSchedule = alarmManager.canScheduleExactAlarms()
                Log.d(TAG, "Can schedule exact alarms: $canSchedule")
                canSchedule
            } else {
                // On older versions, exact alarms are always allowed
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking exact alarm permission", e)
            true // Assume allowed if can't check
        }
    }

    /**
     * Open exact alarm settings (Android 12+)
     */
    fun openExactAlarmSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened exact alarm settings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening exact alarm settings", e)
        }
    }

    /**
     * Check if Accessibility Service is enabled for the app
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val expectedComponentName = "${context.packageName}/com.tastamat.fandomon.service.AppLauncherAccessibilityService"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (enabledServicesSetting.isNullOrEmpty()) {
                Log.d(TAG, "Accessibility Service: NOT enabled (no services found)")
                return false
            }

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    Log.d(TAG, "Accessibility Service: ENABLED ✅")
                    return true
                }
            }

            Log.d(TAG, "Accessibility Service: NOT enabled (not in list)")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Accessibility Service", e)
            false
        }
    }

    /**
     * Open Accessibility settings
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Accessibility settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Accessibility settings", e)
        }
    }

    /**
     * Check if app has permission to read external storage (for heartbeat file)
     */
    fun hasStoragePermission(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Check MANAGE_EXTERNAL_STORAGE
                val hasPermission = Environment.isExternalStorageManager()
                Log.d(TAG, "Storage permission (MANAGE_EXTERNAL_STORAGE): $hasPermission")
                hasPermission
            } else {
                // Android 10 and below: Check READ_EXTERNAL_STORAGE
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Storage permission (READ_EXTERNAL_STORAGE): $hasPermission")
                hasPermission
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage permission", e)
            false
        }
    }

    /**
     * Request storage permission (opens settings for MANAGE_EXTERNAL_STORAGE)
     */
    fun requestStoragePermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Open MANAGE_EXTERNAL_STORAGE settings
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened MANAGE_EXTERNAL_STORAGE settings")
            } else {
                // Android 10 and below: Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened app settings for storage permission")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening storage permission settings", e)
        }
    }
}

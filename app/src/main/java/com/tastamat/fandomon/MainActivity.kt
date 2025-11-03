package com.tastamat.fandomon

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.DataSyncService
import kotlinx.coroutines.Dispatchers
import com.tastamat.fandomon.ui.screen.OnboardingScreen
import com.tastamat.fandomon.ui.screen.SettingsScreen
import com.tastamat.fandomon.ui.theme.Fandomon2Theme
import com.tastamat.fandomon.utils.PermissionUtils
import com.tastamat.fandomon.utils.XiaomiUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            android.util.Log.d("MainActivity", "${it.key} = ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Fandomon2Theme {
                var showOnboarding by remember {
                    mutableStateOf(
                        !PermissionUtils.hasUsageStatsPermission(this) ||
                        !PermissionUtils.isAccessibilityServiceEnabled(this) ||
                        !PermissionUtils.hasStoragePermission(this)
                    )
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            showOnboarding = false
                            // Subscribe to MQTT after permissions granted
                            subscribeToMqttCommands()
                        }
                    )
                } else {
                    SettingsScreen()
                    // Subscribe to MQTT if permissions already granted
                    LaunchedEffect(Unit) {
                        subscribeToMqttCommands()
                    }
                }
            }
        }

        // Check Xiaomi autostart (background task)
        checkXiaomiAutostart()

        // Check if we need to restore state after restart_fandomon command
        // Run this with a delay to avoid blocking Activity creation
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch(Dispatchers.IO) {
                checkAndRestoreAfterRestart()
            }
        }, 1000) // Wait 1 second after Activity is created
    }

    /**
     * Check if this is a restart after restart_fandomon command
     * If so, restore monitoring and launch Fandomat
     * This is a suspend function that runs on IO dispatcher
     */
    private suspend fun checkAndRestoreAfterRestart() {
        try {
            val prefs = getSharedPreferences("restart_prefs", Context.MODE_PRIVATE)
            val shouldRestoreMonitoring = prefs.getBoolean("should_restore_monitoring", false)
            val shouldLaunchFandomat = prefs.getBoolean("should_launch_fandomat", false)
            val restartTimestamp = prefs.getLong("restart_timestamp", 0)

            // Check if this is a recent restart (within last 30 seconds)
            val timeSinceRestart = System.currentTimeMillis() - restartTimestamp
            val isRecentRestart = timeSinceRestart < 30000

            if (isRecentRestart && (shouldRestoreMonitoring || shouldLaunchFandomat)) {
                Log.d("MainActivity", "")
                Log.d("MainActivity", "========================================")
                Log.d("MainActivity", "🔄 DETECTED RESTART AFTER restart_fandomon COMMAND")
                Log.d("MainActivity", "========================================")
                Log.d("MainActivity", "📝 Should restore monitoring: $shouldRestoreMonitoring")
                Log.d("MainActivity", "📝 Should launch Fandomat: $shouldLaunchFandomat")
                Log.d("MainActivity", "⏱️  Time since restart: ${timeSinceRestart}ms")
                Log.d("MainActivity", "")

                // Clear the flags
                prefs.edit().clear().apply()

                // Give UI time to initialize
                kotlinx.coroutines.delay(2000)

                // Restore monitoring if it was active
                if (shouldRestoreMonitoring) {
                    Log.d("MainActivity", "🔄 Restoring monitoring state...")
                    val preferences = AppPreferences(applicationContext)
                    val checkInterval = preferences.checkIntervalMinutes.first()
                    val statusInterval = preferences.statusReportIntervalMinutes.first()

                    val scheduler = com.tastamat.fandomon.service.AlarmScheduler(applicationContext)
                    scheduler.scheduleMonitoring(checkInterval, statusInterval)
                    Log.d("MainActivity", "✅ Monitoring restored (check: ${checkInterval}min, status: ${statusInterval}min)")
                }

                // Launch Fandomat back to foreground
                if (shouldLaunchFandomat) {
                    Log.d("MainActivity", "")
                    Log.d("MainActivity", "📱 Launching Fandomat back to FOREGROUND...")
                    Log.d("MainActivity", "")

                    val preferences = AppPreferences(applicationContext)
                    val packageName = preferences.fandomatPackageName.first()

                    // Use Accessibility Service if available
                    if (com.tastamat.fandomon.service.AppLauncherAccessibilityService.isEnabled(applicationContext)) {
                        Log.d("MainActivity", "🤖 Using Accessibility Service to launch Fandomat")
                        com.tastamat.fandomon.service.AppLauncherAccessibilityService.requestAppLaunch(
                            applicationContext,
                            packageName
                        )
                        kotlinx.coroutines.delay(3000)
                        Log.d("MainActivity", "✅ Fandomat should now be visible on screen")
                    } else {
                        Log.w("MainActivity", "⚠️ Accessibility Service not enabled")
                        Log.w("MainActivity", "⚠️ Trying shell command...")

                        // Fallback to shell command
                        try {
                            val command = "am start -W -S -n $packageName/.MainActivity"
                            Runtime.getRuntime().exec(command)
                            kotlinx.coroutines.delay(2000)
                            Log.d("MainActivity", "✅ Fandomat launched via shell")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "❌ Failed to launch Fandomat: ${e.message}")
                        }
                    }

                    Log.d("MainActivity", "")
                    Log.d("MainActivity", "✅ POST-RESTART RESTORATION COMPLETE")
                    Log.d("MainActivity", "📱 Fandomat should be on foreground now")
                    Log.d("MainActivity", "========================================")
                    Log.d("MainActivity", "")
                }
            } else if (restartTimestamp > 0) {
                Log.d("MainActivity", "⏰ Restart was too long ago (${timeSinceRestart}ms) - ignoring")
                prefs.edit().clear().apply()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error restoring state after restart: ${e.message}", e)
        }
    }


    private fun checkXiaomiAutostart() {
        if (XiaomiUtils.isXiaomiDevice()) {
            Log.w("MainActivity", "⚠️ XIAOMI/MIUI DEVICE DETECTED!")

            XiaomiUtils.logDeviceInfo()

            // Check if we already showed the autostart dialog
            lifecycleScope.launch {
                val preferences = AppPreferences(applicationContext)
                val dialogShown = preferences.autostartDialogShown.first()

                if (!dialogShown) {
                    Log.w("MainActivity", "⚠️ First time - showing autostart settings")
                    Log.w("MainActivity", "⚠️ You MUST enable Autostart for this app to work after reboot")

                    // Open autostart settings after a delay
                    window.decorView.postDelayed({
                        try {
                            Log.d("MainActivity", "📱 Opening Xiaomi Autostart settings...")
                            val opened = XiaomiUtils.openAutoStartSettings(this@MainActivity)
                            if (opened) {
                                Log.d("MainActivity", "✅ Autostart settings opened - please enable Fandomon")

                                // Mark as shown
                                lifecycleScope.launch {
                                    preferences.setAutostartDialogShown(true)
                                    Log.d("MainActivity", "✅ Autostart dialog marked as shown")
                                }
                            } else {
                                Log.w("MainActivity", "⚠️ Could not open autostart settings automatically")
                                Log.w("MainActivity", "⚠️ Please manually enable: Security app → Autostart → Enable Fandomon")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error opening Xiaomi autostart settings", e)
                        }
                    }, 4000) // 4 second delay after battery optimization
                } else {
                    Log.d("MainActivity", "✅ Autostart dialog already shown before, skipping")
                }
            }
        }
    }

    private fun subscribeToMqttCommands() {
        lifecycleScope.launch {
            try {
                val dataSyncService = DataSyncService(applicationContext)
                dataSyncService.subscribeToCommands()
                Log.d("MainActivity", "✅ Subscribed to MQTT commands")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error subscribing to commands: ${e.message}", e)
            }
        }
    }
}
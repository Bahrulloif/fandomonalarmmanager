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
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.DataSyncService
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

        requestRequiredPermissions()
        checkAndRequestUsageStatsPermission()

        setContent {
            Fandomon2Theme {
                SettingsScreen()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SCHEDULE_EXACT_ALARM
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkAndRequestUsageStatsPermission() {
        if (!PermissionUtils.hasUsageStatsPermission(this)) {
            Log.d("MainActivity", "⚠️ UsageStats permission not granted")
            Log.d("MainActivity", "📱 Opening settings to grant permission...")

            // Delay to ensure UI is ready
            window.decorView.postDelayed({
                PermissionUtils.requestUsageStatsPermission(this)
            }, 1000) // 1 second delay to show main screen first
        } else {
            Log.d("MainActivity", "✅ UsageStats permission already granted")

            // Check battery optimization
            checkBatteryOptimization()

            // Check Xiaomi autostart permission
            checkXiaomiAutostart()

            // Subscribe to MQTT commands after permissions are granted
            subscribeToMqttCommands()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "⚠️ App is subject to battery optimization")
                Log.d("MainActivity", "📱 Requesting battery optimization exemption...")

                // Show dialog to user explaining why we need this
                window.decorView.postDelayed({
                    try {
                        val intent = Intent().apply {
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error requesting battery optimization exemption", e)
                    }
                }, 2000) // 2 second delay after UsageStats permission
            } else {
                Log.d("MainActivity", "✅ Battery optimization already disabled for this app")
            }
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
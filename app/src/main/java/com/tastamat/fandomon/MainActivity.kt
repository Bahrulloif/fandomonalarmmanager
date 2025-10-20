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
import com.tastamat.fandomon.service.DataSyncService
import com.tastamat.fandomon.ui.screen.SettingsScreen
import com.tastamat.fandomon.ui.theme.Fandomon2Theme
import com.tastamat.fandomon.utils.PermissionUtils
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
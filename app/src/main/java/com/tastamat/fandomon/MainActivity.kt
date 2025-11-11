package com.tastamat.fandomon

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.tastamat.fandomon.receiver.ScreenStateReceiver
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

    private var screenStateReceiver: ScreenStateReceiver? = null

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
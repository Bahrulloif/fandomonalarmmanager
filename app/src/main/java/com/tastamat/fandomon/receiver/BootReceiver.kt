package com.tastamat.fandomon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.service.AlarmScheduler
import com.tastamat.fandomon.utils.XiaomiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "UNKNOWN_ACTION"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        Log.d(TAG, "========================================")
        Log.d(TAG, "🔔 BootReceiver triggered at $timestamp")
        Log.d(TAG, "Action: $action")
        Log.d(TAG, "========================================")

        // Логируем информацию об устройстве
        XiaomiUtils.logDeviceInfo()

        // Обрабатываем различные типы загрузки
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT -> {
                Log.d(TAG, "✅ Boot event detected, processing...")
                handleBootEvent(context)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown action received: $action")
            }
        }
    }

    private fun handleBootEvent(context: Context) {
        Log.d(TAG, "📋 Starting boot event handling...")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 Reading preferences...")
                val preferences = AppPreferences(context)
                val wasMonitoringActive = preferences.monitoringActive.first()

                Log.d(TAG, "📊 Monitoring was active before reboot: $wasMonitoringActive")

                if (wasMonitoringActive) {
                    Log.d(TAG, "✅ Monitoring was active before reboot, restarting...")

                    val checkInterval = preferences.checkIntervalMinutes.first()
                    val statusInterval = preferences.statusReportIntervalMinutes.first()

                    Log.d(TAG, "⏱️ Check interval: ${checkInterval}min")
                    Log.d(TAG, "⏱️ Status interval: ${statusInterval}min")

                    // Даем системе время стабилизироваться (важно для MIUI)
                    kotlinx.coroutines.delay(3000)
                    Log.d(TAG, "⏳ Waited 3 seconds for system stabilization")

                    val scheduler = AlarmScheduler(context)
                    scheduler.scheduleMonitoring(checkInterval, statusInterval)

                    Log.d(TAG, "✅✅✅ Monitoring alarms scheduled successfully after boot")
                    Log.d(TAG, "Check interval: ${checkInterval}min, Status interval: ${statusInterval}min")

                    // Если это Xiaomi, напоминаем о необходимости включения автозапуска
                    if (XiaomiUtils.isXiaomiDevice()) {
                        Log.w(TAG, "⚠️ XIAOMI DEVICE DETECTED!")
                        Log.w(TAG, "⚠️ Make sure Autostart is enabled in Security app")
                        Log.w(TAG, "⚠️ Make sure Battery Saver allows background activity")
                    }

                } else {
                    Log.d(TAG, "⏸️ Monitoring was not active before reboot, skipping auto-start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error scheduling alarms after boot", e)
                Log.e(TAG, "Error message: ${e.message}")
                Log.e(TAG, "Stack trace:")
                e.printStackTrace()
            } finally {
                Log.d(TAG, "🏁 Boot event handling completed")
                Log.d(TAG, "========================================")
                pendingResult.finish()
            }
        }
    }
}

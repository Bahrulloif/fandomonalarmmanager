package com.tastamat.fandomon.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object XiaomiUtils {

    private const val TAG = "XiaomiUtils"

    /**
     * Проверяет, является ли устройство Xiaomi/MIUI
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco"
    }

    /**
     * Открывает настройки автозапуска для Xiaomi/MIUI
     * Необходимо для того, чтобы BootReceiver работал после перезагрузки
     */
    fun openAutoStartSettings(context: Context): Boolean {
        if (!isXiaomiDevice()) {
            Log.d(TAG, "Not a Xiaomi device, skipping autostart settings")
            return false
        }

        return try {
            Log.d(TAG, "Opening Xiaomi autostart settings")

            // Для MIUI 12+ (новые версии)
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            Log.d(TAG, "✅ Successfully opened autostart settings")
            true
        } catch (e1: Exception) {
            Log.w(TAG, "Failed to open MIUI 12+ autostart settings, trying alternative")

            try {
                // Для MIUI 11 и ниже (старые версии)
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.powercenter.PowerSettings"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                context.startActivity(intent)
                Log.d(TAG, "✅ Successfully opened autostart settings (alternative)")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open autostart settings", e2)
                false
            }
        }
    }

    /**
     * Открывает настройки оптимизации батареи
     */
    fun openBatterySettings(context: Context): Boolean {
        return try {
            if (isXiaomiDevice()) {
                Log.d(TAG, "Opening Xiaomi battery settings")

                val intent = Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                context.startActivity(intent)
                Log.d(TAG, "✅ Successfully opened battery settings")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery settings", e)
            false
        }
    }

    /**
     * Получает информацию об устройстве для отладки
     */
    fun getDeviceInfo(): String {
        return buildString {
            appendLine("📱 Device Information:")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Is Xiaomi: ${isXiaomiDevice()}")
        }
    }

    /**
     * Логирует информацию об устройстве
     */
    fun logDeviceInfo() {
        Log.d(TAG, getDeviceInfo())
    }
}

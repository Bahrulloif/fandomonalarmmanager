# Улучшенная проверка статуса приложения

## Проблема с текущей реализацией

`ActivityManager.getRunningAppProcesses()` с Android 5.0+ возвращает только процессы **текущего приложения** из соображений безопасности. Поэтому Fandomon не может видеть, запущен ли Fandomat.

## Решение: UsageStatsManager

UsageStatsManager позволяет проверить, когда приложение было активно в последний раз.

### Добавьте разрешение в AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

### Улучшенный метод isAppRunning()

Замените текущий метод в `FandomatMonitor.kt`:

```kotlin
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

private fun isAppRunning(packageName: String): Boolean {
    Log.d(TAG, "=== Checking if $packageName is running ===")

    // Метод 1: Попытка через ActivityManager (для старых версий или системных привилегий)
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningApps = activityManager.runningAppProcesses

    if (runningApps != null) {
        Log.d(TAG, "Found ${runningApps.size} running processes via ActivityManager:")
        runningApps.forEach {
            Log.d(TAG, "  - ${it.processName} (importance: ${it.importance})")
        }

        val foundViaAM = runningApps.any { it.processName == packageName }
        if (foundViaAM) {
            Log.d(TAG, "✅ Found via ActivityManager: $packageName is RUNNING")
            return true
        }
    } else {
        Log.w(TAG, "ActivityManager.runningAppProcesses returned null")
    }

    // Метод 2: UsageStatsManager (для Android 5.0+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val queryTime = currentTime - (5 * 60 * 1000) // Последние 5 минут

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                queryTime,
                currentTime
            )

            if (usageStats != null && usageStats.isNotEmpty()) {
                // Находим статистику для целевого пакета
                val targetStats = usageStats.firstOrNull { it.packageName == packageName }

                if (targetStats != null) {
                    val lastTimeUsed = targetStats.lastTimeUsed
                    val timeSinceLastUse = currentTime - lastTimeUsed
                    val minutesSinceLastUse = timeSinceLastUse / 60000

                    Log.d(TAG, "UsageStats for $packageName:")
                    Log.d(TAG, "  Last time used: $lastTimeUsed")
                    Log.d(TAG, "  Time since last use: ${minutesSinceLastUse} minutes")

                    // Считаем приложение запущенным, если оно использовалось менее 2 минут назад
                    val isRunning = minutesSinceLastUse < 2
                    Log.d(TAG, "Result via UsageStats: $packageName is ${if (isRunning) "RUNNING" else "NOT RUNNING"}")
                    return isRunning
                } else {
                    Log.w(TAG, "No usage stats found for $packageName (never used or just installed)")
                    return false
                }
            } else {
                Log.w(TAG, "UsageStats is empty - permission may be missing")
                Log.w(TAG, "Enable via: Settings → Apps → Special access → Usage access → Fandomon")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking UsageStats: ${e.message}", e)
        }
    }

    // Метод 3: Проверка через launch intent (fallback)
    Log.d(TAG, "Trying fallback method via PackageManager...")
    val isInstalled = try {
        context.packageManager.getPackageInfo(packageName, 0)
        Log.d(TAG, "$packageName is installed")
        true
    } catch (e: Exception) {
        Log.e(TAG, "$packageName is NOT installed")
        false
    }

    // Если установлено, но не можем определить статус - считаем что не запущено
    Log.d(TAG, "Result (fallback): assuming NOT RUNNING")
    return false
}
```

### Запрос разрешения Usage Access

Добавьте в `MainActivity.kt` или `SettingsScreen.kt`:

```kotlin
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun requestUsageStatsPermission(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
```

### Использование в UI

```kotlin
// В SettingsScreen.kt
if (!hasUsageStatsPermission(context)) {
    Button(onClick = { requestUsageStatsPermission(context) }) {
        Text("Grant Usage Access Permission")
    }
}
```

## Преимущества нового подхода

1. ✅ Работает на всех версиях Android 5.0+
2. ✅ Не требует системных привилегий
3. ✅ Более точная проверка через UsageStats
4. ✅ Fallback на старые методы для совместимости
5. ✅ Подробное логирование для отладки

## Недостатки

1. ⚠️ Требует разрешение PACKAGE_USAGE_STATS (пользователь должен включить вручную)
2. ⚠️ Есть задержка ~1-2 минуты в определении остановки приложения

## Альтернатива: Accessibility Service

Для более точного мониторинга в реальном времени можно использовать AccessibilityService, но это требует дополнительных разрешений и более сложной настройки.

## Тестирование

1. Установите обновленное приложение
2. Откройте Settings → Apps → Special access → Usage access
3. Найдите Fandomon и включите разрешение
4. Перезапустите мониторинг
5. Проверьте логи - должны появиться сообщения "UsageStats for ..."

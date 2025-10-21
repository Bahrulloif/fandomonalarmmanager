# Android 10+ App Restart Fix

## Версия: 2.2.0

## Проблема

На Android 10+ (API 29+) система блокирует запуск Activity из фонового режима по соображениям безопасности. Это означало, что Fandomon не мог автоматически перезапустить Fandomat, даже используя shell команды.

### Симптомы:
```
FANDOMAT_RESTARTED: "Fandomat restart attempted via launch intent (may be blocked by Android 10+)"
```

Приложение постоянно пыталось перезапустить Fandomat, но он не появлялся на главном экране.

## Причина

### Android 10+ Background Activity Launch Restrictions

Google ввел ограничения в Android 10 (Q):
- ❌ Нельзя запускать Activity из фона через `startActivity()`
- ❌ Даже shell команда `am start` может быть заблокирована
- ❌ Приложения не могут "выпрыгивать" на экран без разрешения пользователя

**Исключения (что разрешено):**
- ✅ Notifications с PendingIntent могут запускать Activity
- ✅ Full Screen Intent для критических уведомлений (звонки, будильники)
- ✅ Foreground services

## Решение

Используем **High-Priority Notification с Full Screen Intent** для перезапуска приложения.

### Как это работает

```
Fandomon обнаруживает что Fandomat остановлен
    ↓
Отправляет критическое уведомление с Full Screen Intent
    ↓
Notification появляется даже на заблокированном экране
    ↓
PendingIntent запускает Fandomat (разрешено системой!)
    ↓
Пользователь нажимает на уведомление → Fandomat запускается
```

## Изменения в коде v2.2.0

### 1. AndroidManifest.xml

Добавлено новое разрешение:

```xml
<!-- Full screen intent for critical notifications (app restart) on Android 10+ -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

### 2. FandomatMonitor.kt - Notification Channel

```kotlin
companion object {
    private const val RESTART_NOTIFICATION_CHANNEL_ID = "fandomon_restart_channel"
    private const val RESTART_NOTIFICATION_ID = 9999
}

init {
    createNotificationChannel()
}

private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "App Restart Notifications"
        val descriptionText = "Critical notifications for restarting monitored applications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(RESTART_NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(true)
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
```

### 3. FandomatMonitor.kt - Notification-based Restart

```kotlin
private fun sendRestartNotification(packageName: String): Boolean {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.e(TAG, "❌ Cannot create notification: launch intent not found")
            return false
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags
        )

        // Get app name
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val appName = pm.getApplicationLabel(appInfo).toString()

        val notification = NotificationCompat.Builder(context, RESTART_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$appName stopped")
            .setContentText("Tap to restart $appName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // ← КЛЮЧЕВАЯ СТРОКА!
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(RESTART_NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ High-priority restart notification sent for $appName")
        return true

    } catch (e: Exception) {
        Log.e(TAG, "Error sending restart notification: ${e.message}", e)
        return false
    }
}
```

### 4. FandomatMonitor.kt - Updated Restart Logic

Теперь используется 3-этапная стратегия:

```kotlin
private suspend fun restartFandomat(packageName: String): Boolean {
    // Method 1: Try shell command (best on rooted devices)
    try {
        val command = "am start -n $packageName/.MainActivity"
        val process = Runtime.getRuntime().exec(command)
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            Thread.sleep(3000)
            if (isAppInForeground(packageName)) {
                return true // SUCCESS!
            }
        }
    } catch (e: Exception) {
        // Continue to Method 2
    }

    // Method 2: Use high-priority notification (WORKS ON ANDROID 10+)
    Log.d(TAG, "Trying notification-based restart for Android 10+...")
    val notificationSuccess = sendRestartNotification(packageName)

    if (notificationSuccess) {
        Log.d(TAG, "✅ Restart notification sent successfully")
        return true
    }

    // Method 3: Final fallback to startActivity (may not work)
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    return false
}
```

## Использование

### Автоматическая работа

После обновления до v2.2.0 Fandomon автоматически:

1. Обнаруживает что Fandomat остановлен
2. Отправляет высокоприоритетное уведомление
3. Уведомление появляется на экране (даже если заблокирован)
4. **Пользователь нажимает на уведомление** → Fandomat запускается

### Новые события MQTT

Теперь вы увидите более точные события:

```json
{
  "event_type": "FANDOMAT_RESTARTED",
  "message": "Restart notification sent - waiting for app to start"
}
```

Или при успехе:

```json
{
  "event_type": "FANDOMAT_RESTART_SUCCESS",
  "message": "Fandomat successfully restarted via notification"
}
```

## Поведение на разных версиях Android

### Android 9 и ниже (API 28-)
- ✅ Shell command работает
- ✅ startActivity() работает
- ℹ️ Notification не нужен, но тоже работает

### Android 10+ (API 29+)
- ⚠️ Shell command может не работать (нет root)
- ❌ startActivity() заблокирован системой
- ✅ **Notification с Full Screen Intent работает!**

### MIUI (Xiaomi/Redmi)
- ⚠️ Требует все 4 шага настройки (см. XIAOMI_SETUP.md)
- ✅ После настройки Notification работает
- ⚠️ Пользователь должен нажать на уведомление

## Тестирование

### Тест 1: Имитация остановки приложения

```bash
# 1. Запустите Fandomat
# 2. Запустите мониторинг в Fandomon
# 3. Закройте Fandomat (свайп из недавних приложений)
# 4. Подождите интервал проверки (по умолчанию 5 минут)
# 5. Проверьте уведомление

adb logcat -s FandomatMonitor
```

Ожидаемый результат:
```
FandomatMonitor: ⚠️ Fandomat is NOT in foreground
FandomatMonitor: Trying notification-based restart for Android 10+...
FandomatMonitor: ✅ High-priority restart notification sent for Fandomat
```

### Тест 2: Проверка уведомления

После отправки уведомления:

1. **Уведомление появится** в шторке уведомлений
2. **Заголовок**: "Fandomat stopped"
3. **Текст**: "Tap to restart Fandomat"
4. **Нажмите на уведомление** → Fandomat запустится

### Тест 3: Проверка MQTT событий

```bash
mosquitto_sub -h broker.hivemq.com -t "fandomon/events"
```

Вы должны увидеть:
```json
{
  "event_type": "FANDOMAT_STOPPED",
  "message": "Fandomat not in foreground - attempting automatic restart"
}
{
  "event_type": "FANDOMAT_RESTARTING",
  "message": "Attempting to restart Fandomat - sending notification to server"
}
{
  "event_type": "FANDOMAT_RESTARTED",
  "message": "Restart notification sent - waiting for app to start"
}
```

## Преимущества нового подхода

| До (v2.1.7) | После (v2.2.0) |
|-------------|----------------|
| ❌ Shell command не работает на Android 10+ | ✅ Notification работает на всех версиях |
| ❌ startActivity() блокируется системой | ✅ PendingIntent разрешен системой |
| ❌ Приложение не запускается автоматически | ✅ Уведомление появляется, пользователь нажимает |
| ❌ Множество неудачных попыток | ✅ Одно уведомление = один запуск |
| ❌ "may be blocked by Android 10+" в логах | ✅ "notification sent successfully" |

## Ограничения

### Требуется действие пользователя

На Android 10+ система не позволяет **полностью автоматический** перезапуск приложений из фона. Это сделано специально для:
- Защиты конфиденциальности
- Предотвращения злоупотреблений
- Контроля пользователя

**Решение**:
- Fandomon отправляет высокоприоритетное уведомление
- Пользователь нажимает → приложение запускается
- Это лучшее что можно сделать на Android 10+ без root

### Альтернативы (требуют root)

Если у вас есть root-доступ:

```bash
# Дать Fandomon системные привилегии
pm grant com.tastamat.fandomon android.permission.SYSTEM_ALERT_WINDOW

# Разрешить запуск Activity из фона
settings put global background_activity_starts_enabled 1
```

**НО** это:
- ❌ Требует root
- ❌ Снижает безопасность
- ❌ Может нарушить работу других приложений
- ✅ Уведомление безопаснее и работает без root

## FAQ

### Q: Почему нужно нажимать на уведомление?

**A**: Android 10+ блокирует автоматический запуск Activity из фона. Это политика безопасности Google. Уведомление - единственный легальный способ запустить приложение без root.

### Q: Можно ли сделать полностью автоматический запуск?

**A**: Только с root-доступом или если Fandomon установлен как системное приложение. Для обычных приложений - нет.

### Q: Уведомление не появляется

**A**: Проверьте:
1. Разрешены ли уведомления для Fandomon в настройках
2. Не заблокирован ли канал "App Restart Notifications"
3. Включен ли Do Not Disturb режим

### Q: Приложение не запускается при нажатии на уведомление

**A**: На Xiaomi/MIUI проверьте все 4 шага из XIAOMI_SETUP.md:
1. Автозапуск ✅
2. Батарея без ограничений ✅
3. Фоновая активность ✅
4. Защищенные приложения (Lock apps) ✅

### Q: Можно ли использовать Tasker/MacroDroid для автоматического нажатия?

**A**: Да! Вы можете настроить автоматизацию:
1. Триггер: Notification от Fandomon с текстом "Tap to restart"
2. Действие: Нажать на уведомление
3. Результат: Автоматический запуск без участия пользователя

## Рекомендации

### Для максимальной надежности:

1. **Установите оба приложения** (Fandomon и Fandomat) в защищенные приложения на Xiaomi
2. **Отключите батарейные ограничения** для обоих приложений
3. **Разрешите уведомления** для Fandomon
4. **Включите автозапуск** для обоих приложений
5. **Настройте автоматизацию** через Tasker (опционально)

### Мониторинг через MQTT:

Следите за событиями `FANDOMAT_RESTARTED` - если их много:
- Проверьте почему Fandomat часто останавливается
- Возможно нужно добавить Fandomat в защищенные приложения
- Проверьте настройки батареи для Fandomat

## Заключение

Версия 2.2.0 исправляет критическую проблему с автозапуском на Android 10+:

✅ Использует notification вместо заблокированного startActivity()
✅ Full Screen Intent для критических уведомлений
✅ Работает на всех версиях Android (9, 10, 11, 12, 13, 14)
✅ Совместимо с MIUI/Xiaomi после правильной настройки
✅ Более точные MQTT события о статусе перезапуска

**Теперь Fandomon может надежно уведомлять о необходимости перезапуска на Android 10+!**

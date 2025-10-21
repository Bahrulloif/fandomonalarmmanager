# Настройка для Xiaomi/MIUI (Redmi Note 8 Pro и другие)

## Версия: 2.1.6

## Проблема

На устройствах Xiaomi (MIUI) после перезагрузки приложение Fandomon не запускается автоматически, даже если мониторинг был активен. Это происходит из-за агрессивной политики управления фоновыми приложениями в MIUI.

## Причина

MIUI по умолчанию блокирует автозапуск приложений после перезагрузки для экономии батареи. Это означает, что:

1. ❌ **BootReceiver не получает BOOT_COMPLETED** broadcast
2. ❌ **Приложение не может запуститься в фоне**
3. ❌ **Мониторинг не возобновляется автоматически**

## Решение (3 обязательных шага)

### Шаг 1: Включить Автозапуск (ОБЯЗАТЕЛЬНО!)

#### Автоматический способ:
При первом запуске Fandomon автоматически откроет настройки автозапуска через 4 секунды после разрешений.

#### Ручной способ:
1. Откройте **Безопасность** (Security) приложение
2. Перейдите в **Автозапуск** (Autostart)
3. Найдите **Fandomon** в списке
4. **Включите переключатель** ✅

**Путь в MIUI 12+:**
```
Безопасность → Автозапуск → Fandomon (включить)
```

**Путь в MIUI 11:**
```
Безопасность → Разрешения → Автозапуск → Fandomon (включить)
```

**Альтернативный путь:**
```
Настройки → Приложения → Управление приложениями → Fandomon → Другие разрешения → Автозапуск (включить)
```

### Шаг 2: Отключить экономию батареи

1. Откройте **Настройки**
2. Перейдите в **Приложения** → **Управление приложениями**
3. Найдите **Fandomon**
4. Нажмите **Батарея**
5. Выберите **Без ограничений** (No restrictions)

**ИЛИ через Безопасность:**
```
Безопасность → Батарея → Экономия энергии → Приложения → Fandomon → Без ограничений
```

### Шаг 3: Отключить очистку фоновых приложений

1. Откройте **Настройки**
2. Перейдите в **Приложения** → **Управление приложениями**
3. Найдите **Fandomon**
4. Нажмите **Другие разрешения**
5. Включите:
   - ✅ **Запуск в фоне**
   - ✅ **Всплывающие окна в фоне**
   - ✅ **Постоянное уведомление**

## Проверка настроек

После выполнения всех шагов проверьте:

### Проверка 1: Логи при запуске
```bash
adb logcat -s MainActivity XiaomiUtils
```

Вы должны увидеть:
```
MainActivity: ⚠️ XIAOMI/MIUI DEVICE DETECTED!
XiaomiUtils: 📱 Device Information:
XiaomiUtils: Manufacturer: xiaomi
XiaomiUtils: Brand: redmi
XiaomiUtils: Model: Redmi Note 8 Pro
XiaomiUtils: Is Xiaomi: true
MainActivity: ✅ Autostart settings opened
```

### Проверка 2: Имитация перезагрузки
```bash
# 1. Запустите мониторинг в приложении
# 2. Имитируйте перезагрузку
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.tastamat.fandomon

# 3. Проверьте логи
adb logcat -s BootReceiver AlarmScheduler
```

Вы должны увидеть:
```
BootReceiver: ========================================
BootReceiver: 🔔 BootReceiver triggered at 2024-10-21 10:00:00
BootReceiver: Action: android.intent.action.BOOT_COMPLETED
BootReceiver: 📱 Device Information:
BootReceiver: Manufacturer: xiaomi
BootReceiver: Brand: redmi
BootReceiver: Model: Redmi Note 8 Pro
BootReceiver: Is Xiaomi: true
BootReceiver: ✅ Boot event detected, processing...
BootReceiver: 📋 Starting boot event handling...
BootReceiver: 🔍 Reading preferences...
BootReceiver: 📊 Monitoring was active before reboot: true
BootReceiver: ✅ Monitoring was active before reboot, restarting...
BootReceiver: ⏱️ Check interval: 5min
BootReceiver: ⏱️ Status interval: 15min
BootReceiver: ⏳ Waited 3 seconds for system stabilization
BootReceiver: ✅✅✅ Monitoring alarms scheduled successfully after boot
BootReceiver: ⚠️ XIAOMI DEVICE DETECTED!
BootReceiver: ⚠️ Make sure Autostart is enabled in Security app
BootReceiver: 🏁 Boot event handling completed
```

### Проверка 3: Реальная перезагрузка
```bash
# 1. Запустите мониторинг в приложении
# 2. Перезагрузите планшет
adb reboot

# 3. После загрузки подключите ADB и проверьте логи
adb logcat -s BootReceiver AlarmScheduler
```

## Что было исправлено в версии 2.1.6

### 1. AndroidManifest.xml
```xml
<receiver
    android:name=".receiver.BootReceiver"
    android:enabled="true"
    android:exported="true"
    android:directBootAware="true">
    <intent-filter android:priority="999">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.REBOOT" />
    </intent-filter>
</receiver>
```

**Изменения:**
- ✅ Добавлен `directBootAware="true"` для работы до разблокировки
- ✅ Добавлен `priority="999"` для приоритетной обработки
- ✅ Добавлены дополнительные intent-фильтры для Xiaomi
- ✅ Добавлен `LOCKED_BOOT_COMPLETED` для раннего запуска

### 2. XiaomiUtils.kt (Новый файл)

Утилита для обнаружения Xiaomi и открытия настроек:

```kotlin
object XiaomiUtils {
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer == "xiaomi" || brand == "xiaomi" ||
               brand == "redmi" || brand == "poco"
    }

    fun openAutoStartSettings(context: Context): Boolean {
        // Автоматически открывает настройки автозапуска для MIUI
    }
}
```

### 3. BootReceiver.kt

Улучшенная обработка с детальным логированием:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: "UNKNOWN_ACTION"

    Log.d(TAG, "🔔 BootReceiver triggered")
    Log.d(TAG, "Action: $action")

    // Логируем информацию об устройстве
    XiaomiUtils.logDeviceInfo()

    when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        Intent.ACTION_REBOOT -> {
            handleBootEvent(context)
        }
    }
}

private fun handleBootEvent(context: Context) {
    // Даем системе время стабилизироваться (важно для MIUI)
    delay(3000)

    // Запускаем мониторинг если был активен
    if (wasMonitoringActive) {
        scheduler.scheduleMonitoring(checkInterval, statusInterval)

        if (XiaomiUtils.isXiaomiDevice()) {
            Log.w(TAG, "⚠️ XIAOMI DEVICE DETECTED!")
            Log.w(TAG, "⚠️ Make sure Autostart is enabled")
        }
    }
}
```

### 4. MainActivity.kt

Автоматическое открытие настроек автозапуска при первом запуске:

```kotlin
private fun checkXiaomiAutostart() {
    if (XiaomiUtils.isXiaomiDevice()) {
        Log.w("MainActivity", "⚠️ XIAOMI/MIUI DEVICE DETECTED!")
        Log.w("MainActivity", "⚠️ You MUST enable Autostart")

        // Открываем настройки автозапуска через 4 секунды
        window.decorView.postDelayed({
            XiaomiUtils.openAutoStartSettings(this)
        }, 4000)
    }
}
```

## Частые проблемы и решения

### Проблема 1: BootReceiver не вызывается после перезагрузки

**Решение:**
1. Убедитесь, что **Автозапуск включен** в приложении Безопасность
2. Проверьте через имитацию:
   ```bash
   adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.tastamat.fandomon
   ```
3. Если имитация работает, но реальная перезагрузка нет → Автозапуск выключен!

### Проблема 2: BootReceiver вызывается, но мониторинг не запускается

**Проверьте логи:**
```bash
adb logcat -s BootReceiver AlarmScheduler MonitoringReceiver
```

**Возможные причины:**
- Батарея в режиме экономии → Шаг 2
- Фоновая активность заблокирована → Шаг 3
- MIUI убивает процесс → Все 3 шага обязательны!

### Проблема 3: Мониторинг работает 5-10 минут, потом останавливается

**Причина:** MIUI агрессивно убивает фоновые приложения

**Решение:**
1. **Безопасность** → **Батарея** → **Настройки** → **Фоновая очистка**
2. Выберите **Выключено** или добавьте Fandomon в исключения
3. **Настройки** → **Батарея** → **Экономия энергии** → **Настроить**
4. Отключите экономию для Fandomon

### Проблема 4: Настройки автозапуска не открываются автоматически

**Ручной способ:**
1. **Безопасность** → **Автозапуск**
2. Найдите **Fandomon**
3. Включите переключатель

**Альтернатива:**
```
Настройки → Приложения → Управление приложениями →
Fandomon → Другие разрешения → Автозапуск ✅
```

## Модели Xiaomi/Redmi с известной совместимостью

✅ **Протестировано:**
- Redmi Note 8 Pro (MIUI 12/13)

📱 **Должно работать:**
- Все устройства Xiaomi/Redmi/POCO с MIUI 11+
- Важно выполнить все 3 шага настройки!

## Инструкция для пользователя (русский)

### Как настроить Fandomon на Xiaomi/Redmi

1. **Откройте приложение Fandomon** первый раз
2. Разрешите все запрошенные разрешения
3. Через 4 секунды автоматически откроется **Автозапуск**
4. **Включите Fandomon** в списке автозапуска ✅
5. Вернитесь в Fandomon
6. Нажмите **"Start Monitoring"**
7. Готово! Теперь мониторинг будет работать всегда

### Проверка после перезагрузки

1. Перезагрузите планшет
2. Откройте Fandomon
3. Вы должны увидеть **"● Active"** индикатор
4. Если индикатор неактивен → проверьте Автозапуск в Безопасности

## Отладка (для разработчиков)

### Полное логирование при загрузке
```bash
adb logcat -s BootReceiver AlarmScheduler MonitoringReceiver MainActivity XiaomiUtils FandomatMonitor
```

### Проверка информации об устройстве
```bash
adb logcat -s XiaomiUtils
```

Вывод:
```
XiaomiUtils: 📱 Device Information:
XiaomiUtils: Manufacturer: xiaomi
XiaomiUtils: Brand: redmi
XiaomiUtils: Model: Redmi Note 8 Pro
XiaomiUtils: Device: begonia
XiaomiUtils: Android Version: 10 (API 29)
XiaomiUtils: Is Xiaomi: true
```

### Проверка состояния DataStore
```bash
# Проверить сохраненное состояние мониторинга
adb shell run-as com.tastamat.fandomon ls -la files/datastore/
adb shell run-as com.tastamat.fandomon cat files/datastore/settings.preferences_pb
```

## Заключение

После выполнения всех 3 шагов настройки Fandomon будет работать на Xiaomi/MIUI так же надежно, как на стандартном Android:

✅ Автозапуск после перезагрузки
✅ Непрерывный мониторинг в фоне
✅ Работа в Doze Mode
✅ Автоматическое обнаружение зависаний
✅ Детальное логирование для отладки

**Важно:** Все 3 шага обязательны! MIUI требует явного разрешения для фоновой работы приложений.

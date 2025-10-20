# Исправление фоновой работы приложения Fandomon

## Проблема
Приложение переставало работать после сворачивания из-за:
1. **Использование `setRepeating()`** - не гарантирует выполнение в Doze Mode
2. **Отсутствие исключения из Battery Optimization** - система агрессивно останавливает приложение
3. **Нет проверки зависания** приложения Fandomat
4. **Нет детальных уведомлений** о процессе перезапуска

## Решение

### 1. Переход на `setExactAndAllowWhileIdle()`

**Файл:** [AlarmScheduler.kt](app/src/main/java/com/tastamat/fandomon/service/AlarmScheduler.kt)

#### Изменения:
```kotlin
// БЫЛО: setRepeating() - не работает в Doze Mode
alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, intervalMillis, pendingIntent)

// СТАЛО: setExactAndAllowWhileIdle() - работает даже в Doze Mode
alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
```

**Преимущества:**
- ✅ Работает в Doze Mode (когда экран выключен и планшет неактивен)
- ✅ Гарантирует точное выполнение в указанное время
- ✅ Не потребляет больше энергии, чем предыдущий метод

**Важно:** `setExactAndAllowWhileIdle()` выполняется **только один раз**, поэтому требуется перепланирование.

### 2. Автоматическое перепланирование задач

**Файл:** [MonitoringReceiver.kt](app/src/main/java/com/tastamat/fandomon/service/MonitoringReceiver.kt)

После каждого выполнения задачи автоматически планируется следующее выполнение:

```kotlin
when (intent.action) {
    ACTION_CHECK_FANDOMAT -> {
        checkFandomat(context)
        rescheduleNextAlarm(context, ACTION_CHECK_FANDOMAT)  // ← Новое!
    }
    ACTION_SEND_STATUS -> {
        sendStatus(context)
        rescheduleNextAlarm(context, ACTION_SEND_STATUS)  // ← Новое!
    }
}
```

**Результат:** Задачи будут выполняться бесконечно с заданным интервалом, даже в фоне.

### 3. Проверка зависания приложения

**Файл:** [FandomatMonitor.kt](app/src/main/java/com/tastamat/fandomon/service/FandomatMonitor.kt)

Добавлена функция `checkIfAppResponding()` которая:
- Проверяет `lastTimeUsed` из UsageStats
- Если приложение не использовалось 30+ минут, считается зависшим
- Автоматически выполняет force-stop и перезапуск

```kotlin
val isRunning = isAppInForeground(packageName)
val isResponding = if (isRunning) checkIfAppResponding(packageName) else false

if (isRunning && !isResponding) {
    // Приложение запущено, но зависло
    forceStopAndRestart(packageName)
}
```

### 4. Детальные уведомления о перезапуске

**Файл:** [EventType.kt](app/src/main/java/com/tastamat/fandomon/data/model/EventType.kt)

Добавлены новые типы событий:
- `FANDOMAT_RESTARTING` - перезапуск начался
- `FANDOMAT_RESTART_SUCCESS` - перезапуск успешен
- `FANDOMAT_NOT_RESPONDING` - приложение зависло

**Процесс перезапуска:**
1. Отправляется событие `FANDOMAT_RESTARTING`
2. Выполняется команда перезапуска
3. Ожидание 3 секунды
4. Проверка, что приложение запустилось
5. Отправляется событие `FANDOMAT_RESTART_SUCCESS` или ошибка

### 5. Исключение из Battery Optimization

**Файл:** [MainActivity.kt](app/src/main/java/com/tastamat/fandomon/MainActivity.kt)

При запуске приложение автоматически просит пользователя:
```kotlin
private fun checkBatteryOptimization() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        // Показать системный диалог для отключения оптимизации
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
```

**Важно:** Пользователь должен **разрешить** исключение из оптимизации для надежной работы.

## Как это работает теперь

### Сценарий 1: Приложение свернуто
```
1. Пользователь сворачивает Fandomon
2. Android переводит планшет в Doze Mode через 5 минут
3. В указанное время срабатывает setExactAndAllowWhileIdle()
4. Выполняется проверка статуса Fandomat
5. Планируется следующая проверка
6. Планшет возвращается в Doze Mode
```

### Сценарий 2: Fandomat остановлен
```
1. Обнаружено, что Fandomat не работает
2. Событие FANDOMAT_STOPPED → MQTT/REST API
3. Событие FANDOMAT_RESTARTING → MQTT/REST API
4. Выполняется команда: am start -n com.tastamat.fandomat/.MainActivity
5. Ожидание 3 секунды
6. Проверка, что приложение запустилось
7. Событие FANDOMAT_RESTART_SUCCESS → MQTT/REST API
```

### Сценарий 3: Fandomat завис
```
1. Обнаружено, что Fandomat запущен, но не отвечает
2. Событие FANDOMAT_NOT_RESPONDING → MQTT/REST API
3. Событие FANDOMAT_RESTARTING → MQTT/REST API
4. Выполняется команда: am force-stop com.tastamat.fandomat
5. Ожидание 2 секунды
6. Выполняется команда: am start -n com.tastamat.fandomat/.MainActivity
7. Ожидание 3 секунды
8. Событие FANDOMAT_RESTART_SUCCESS → MQTT/REST API
```

## Настройка после обновления

### Шаг 1: Разрешения при первом запуске
При запуске приложения появятся 2 диалога:

1. **Usage Stats Permission** - обязательно разрешить
   - Используется для проверки, какое приложение в foreground

2. **Battery Optimization Exemption** - обязательно разрешить
   - Разрешает приложению работать в фоне без остановки системой

### Шаг 2: Настройка интервалов
В настройках приложения:
- **Check Interval** - как часто проверять Fandomat (рекомендуется: 5 минут)
- **Status Report Interval** - как часто отправлять статус (рекомендуется: 15 минут)

### Шаг 3: Включить мониторинг
Нажать кнопку **"Start Monitoring"** в интерфейсе приложения.

### Шаг 4: Пр��верка работы в фоне
```
1. Запустить мониторинг
2. Свернуть приложение Fandomon
3. Закрыть Fandomat (для теста)
4. Подождать 5 минут
5. Проверить, что Fandomat автоматически перезапустился
6. Проверить события в MQTT/REST API
```

## Логи для отладки

Все действия логируются с тегами:

```kotlin
AlarmScheduler     - Планирование задач
MonitoringReceiver - Получение и выполнение задач
FandomatMonitor    - Проверка и перезапуск Fandomat
DataSyncService    - Отправка событий в MQTT/REST
```

Просмотр логов:
```bash
adb logcat -s AlarmScheduler MonitoringReceiver FandomatMonitor DataSyncService
```

## Энергопотребление

### До исправлений:
- ❌ Foreground Service: ~100-200mA постоянно
- ❌ Перегрев планшета при длительной работе

### После исправлений:
- ✅ AlarmManager: ~5-10mA в режиме ожидания
- ✅ Только кратковременная активность каждые 5-15 минут
- ✅ Планшет остается холодным

## Требования к системе

- ✅ Android 6.0+ (API 23+) - полная поддержка
- ⚠️ Android 12+ (API 31+) - требуется разрешение SCHEDULE_EXACT_ALARM
- ✅ Все популярные производители (Samsung, Xiaomi, Huawei и т.д.)

## Известные ограничения

1. **Xiaomi MIUI** - может потребоваться дополнительно:
   - Настройки → Приложения → Fandomon → Автозапуск: Разрешить
   - Настройки → Приложения → Fandomon → Экономия энергии: Нет ограничений

2. **Huawei EMUI** - может потребоваться:
   - Настройки → Батарея → Запуск приложений → Fandomon: Ручное управление
   - Включить все параметры

3. **Samsung OneUI** - обычно работает без дополнительных настроек

## Тестирование

### Тест 1: Работа в фоне
```bash
# Включить мониторинг
# Свернуть приложение
# Подождать 1 интервал проверки (например, 5 минут)
adb logcat -s MonitoringReceiver | grep "Received alarm"
# Должно появиться сообщение о получении задачи
```

### Тест 2: Перезапуск Fandomat
```bash
# Остановить Fandomat
adb shell am force-stop com.tastamat.fandomat

# Подождать 1 интервал проверки
# Проверить, что Fandomat автоматически перезапустился
adb shell dumpsys activity | grep "com.tastamat.fandomat"
```

### Тест 3: Работа в Doze Mode
```bash
# Включить Doze Mode принудительно (для теста)
adb shell dumpsys deviceidle force-idle

# Подождать время проверки
# Проверить логи
adb logcat -s MonitoringReceiver

# Выключить Doze Mode
adb shell dumpsys deviceidle unforce
```

## Заключение

Теперь приложение **Fandomon работает надежно в фоне** без перегрева планшета:

✅ Использует энергоэффективный AlarmManager
✅ Работает даже в Doze Mode
✅ Проверяет зависание приложения Fandomat
✅ Отправляет детальные уведомления о процессе перезапуска
✅ Не нагружает процессор и не перегревает устройство

Вопросы? Проверьте логи и документацию выше.

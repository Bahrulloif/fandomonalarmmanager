# Persistent Monitoring - Auto-restart After Reboot

## Version: 2.1.5

## Проблема

После перезагрузки планшета мониторинг останавливался и требовал вручную нажимать кнопку "Start Monitoring" снова.

## Решение

Теперь приложение запоминает состояние мониторинга и автоматически возобновляет его после перезагрузки устройства.

### Как это работает

```
Пользователь нажимает "Start Monitoring"
    ↓
Сохраняется: monitoring_active = true в DataStore
    ↓
Планшет перезагружается
    ↓
BootReceiver получает ACTION_BOOT_COMPLETED
    ↓
Проверяется: monitoring_active == true?
    ↓
Если ДА → Автоматически запускается мониторинг
Если НЕТ → Ничего не происходит
```

## Изменения в коде

### 1. AppPreferences.kt

Добавлен новый ключ для хранения состояния мониторинга:

```kotlin
companion object {
    // ...
    val MONITORING_ACTIVE = booleanPreferencesKey("monitoring_active")
}

val monitoringActive: Flow<Boolean> = context.dataStore.data.map {
    it[MONITORING_ACTIVE] ?: false
}

suspend fun setMonitoringActive(active: Boolean) {
    context.dataStore.edit { it[MONITORING_ACTIVE] = active }
}
```

### 2. SettingsViewModel.kt

Теперь состояние мониторинга сохраняется при каждом изменении:

```kotlin
fun startMonitoring() {
    viewModelScope.launch {
        val checkInterval = preferences.checkIntervalMinutes.first()
        val statusInterval = preferences.statusReportIntervalMinutes.first()
        alarmScheduler.scheduleMonitoring(checkInterval, statusInterval)
        preferences.setMonitoringActive(true)  // ← Сохраняем состояние
        _state.value = _state.value.copy(isMonitoringActive = true)
    }
}

fun stopMonitoring() {
    viewModelScope.launch {
        alarmScheduler.cancelAllAlarms()
        preferences.setMonitoringActive(false)  // ← Сохраняем состояние
        _state.value = _state.value.copy(isMonitoringActive = false)
    }
}
```

И загружается при запуске приложения:

```kotlin
private fun loadSettings() {
    viewModelScope.launch {
        _state.value = SettingsState(
            // ...
            isMonitoringActive = preferences.monitoringActive.first()  // ← Загружаем состояние
        )
    }
}
```

### 3. BootReceiver.kt

Теперь проверяет сохраненное состояние и запускает мониторинг только если он был активен:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
        Log.d(TAG, "Device booted, checking if monitoring should be restarted")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = AppPreferences(context)
                val wasMonitoringActive = preferences.monitoringActive.first()

                if (wasMonitoringActive) {
                    Log.d(TAG, "✅ Monitoring was active before reboot, restarting...")

                    val checkInterval = preferences.checkIntervalMinutes.first()
                    val statusInterval = preferences.statusReportIntervalMinutes.first()

                    val scheduler = AlarmScheduler(context)
                    scheduler.scheduleMonitoring(checkInterval, statusInterval)

                    Log.d(TAG, "✅ Monitoring alarms scheduled after boot")
                } else {
                    Log.d(TAG, "⏸️ Monitoring was not active before reboot, skipping auto-start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling alarms after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

## Использование

### Обычная работа

1. Откройте приложение Fandomon
2. Нажмите **"Start Monitoring"**
3. Состояние автоматически сохраняется
4. Индикатор показывает "● Active"

### После перезагрузки

1. Планшет перезагружается
2. Система запускает BootReceiver
3. Проверяется сохраненное состояние
4. Если мониторинг был активен → автоматически запускается
5. Открыв приложение, вы увидите "● Active" индикатор

### Остановка мониторинга

1. Откройте приложение
2. Нажмите **"Stop Monitoring"**
3. Состояние сохраняется как "неактивен"
4. После перезагрузки мониторинг НЕ запустится автоматически

## Сценарии использования

### Сценарий 1: Включен мониторинг + Перезагрузка

```
1. Пользователь: Нажимает "Start Monitoring"
2. Приложение: Сохраняет monitoring_active = true
3. Приложение: Запускает мониторинг
4. Планшет: Перезагружается
5. BootReceiver: Проверяет monitoring_active = true
6. BootReceiver: Автоматически запускает мониторинг
7. Результат: ✅ Мониторинг работает без вмешательства пользователя
```

### Сценарий 2: Выключен мониторинг + Перезагрузка

```
1. Пользователь: Нажимает "Stop Monitoring" (или никогда не запускал)
2. Приложение: Сохраняет monitoring_active = false
3. Планшет: Перезагружается
4. BootReceiver: Проверяет monitoring_active = false
5. BootReceiver: Пропускает автозапуск
6. Результат: ✅ Мониторинг не запускается (как и ожидалось)
```

### Сценарий 3: Отключение планшета

```
1. Мониторинг активен (monitoring_active = true)
2. Планшет выключается на ночь
3. Утром планшет включается
4. BootReceiver автоматически запускает мониторинг
5. Fandomat проверяется согласно интервалу
6. Результат: ✅ Непрерывный мониторинг 24/7
```

## Логи

При перезагрузке в logcat вы увидите:

### Если мониторинг был активен:
```
BootReceiver: Device booted, checking if monitoring should be restarted
BootReceiver: ✅ Monitoring was active before reboot, restarting...
BootReceiver: ✅ Monitoring alarms scheduled after boot (check: 5min, status: 15min)
AlarmScheduler: ✅ Scheduled exact Fandomat check in 5 minutes (works in Doze Mode)
AlarmScheduler: ✅ Scheduled exact status report in 15 minutes
```

### Если мониторинг был неактивен:
```
BootReceiver: Device booted, checking if monitoring should be restarted
BootReceiver: ⏸️ Monitoring was not active before reboot, skipping auto-start
```

## Проверка логов

```bash
# Просмотр логов после перезагрузки
adb logcat -s BootReceiver AlarmScheduler MonitoringReceiver

# Проверка только BootReceiver
adb logcat -s BootReceiver

# Имитация перезагрузки (для теста)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
```

## Тестирование

### Тест 1: Автозапуск после перезагрузки

```bash
# 1. Запустить мониторинг через UI
# 2. Перезагрузить планшет
adb reboot

# 3. Подождать загрузки планшета
# 4. Проверить логи
adb logcat -s BootReceiver

# Ожидаемый результат: "✅ Monitoring was active before reboot, restarting..."
```

### Тест 2: НЕ запускается если был выключен

```bash
# 1. Убедиться что мониторинг остановлен
# 2. Перезагрузить планшет
adb reboot

# 3. Проверить логи
adb logcat -s BootReceiver

# Ожидаемый результат: "⏸️ Monitoring was not active before reboot, skipping auto-start"
```

### Тест 3: Имитация BOOT_COMPLETED (без перезагрузки)

```bash
# 1. Запустить мониторинг через UI
# 2. Имитировать перезагрузку
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED

# 3. Проверить логи
adb logcat -s BootReceiver

# Ожидаемый результат: Мониторинг перезапущен
```

## Требования

- ✅ **RECEIVE_BOOT_COMPLETED** разрешение в AndroidManifest.xml (уже добавлено)
- ✅ **BootReceiver** зарегистрирован в AndroidManifest.xml (уже добавлено)
- ✅ **Battery Optimization** отключена для Fandomon
- ✅ **DataStore** для хранения состояния (уже используется)

## Преимущества

| До (v2.1.4) | После (v2.1.5) |
|-------------|----------------|
| ❌ После перезагрузки мониторинг останавливался | ✅ Автоматически возобновляется |
| ❌ Нужно вручную запускать снова | ✅ Работает автоматически |
| ❌ Пользователь может забыть запустить | ✅ Непрерывный мониторинг 24/7 |
| ❌ Пропуск событий между перезагрузками | ✅ Никаких пропусков |

## Совместимость с предыдущими версиями

При обновлении с v2.1.4 на v2.1.5:

- Если мониторинг был запущен → `monitoring_active` не установлен (default = false)
- После первого нажатия "Start Monitoring" → `monitoring_active = true`
- С этого момента работает автозапуск после перезагрузки

**Рекомендация:** После обновления один раз нажмите "Stop Monitoring" → "Start Monitoring" чтобы активировать функцию автозапуска.

## Заключение

Теперь приложение **Fandomon полностью автономно**:

✅ Работает в фоне без перегрева
✅ Работает в Doze Mode
✅ Автоматически перезапускается после перезагрузки
✅ Проверяет зависание Fandomat
✅ Отправляет детальные уведомления
✅ Непрерывный мониторинг 24/7

**Один раз нажали "Start Monitoring" → работает всегда, пока не нажмете "Stop Monitoring"**

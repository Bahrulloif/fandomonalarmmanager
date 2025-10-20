# Fandomon - Monitoring Application

Приложение для мониторинга состояния и производительности системы **Fandomat**. Обеспечивает логирование ошибок, сбор логов, мониторинг работоспособности и передачу данных на сервер.

## Возможности

- ✅ Мониторинг приложения Fandomat (логи, состояние, доступность)
- ✅ Логирование критических событий:
  - Остановка приложения
  - Отключение интернета
  - Отсутствующие логи
  - Отключение питания
- ✅ Отслеживание состояния самого Fandomon
- ✅ Автоматический перезапуск Fandomat при остановке
- ✅ Хранение событий в локальной базе данных SQLite
- ✅ Отправка событий и статусов через MQTT или REST API
- ✅ Использование AlarmManager для периодических проверок
- ✅ Удаленное управление через MQTT команды

## Технические характеристики

- **Язык**: Kotlin
- **Архитектура**: MVVM + Repository
- **База данных**: Room (SQLite)
- **UI**: Jetpack Compose
- **Планировщик**: AlarmManager (для минимального потребления батареи)
- **Библиотеки**:
  - Eclipse Paho MQTT
  - Retrofit для REST API
  - Room для базы данных
  - DataStore для настроек
  - Kotlin Coroutines

## Структура проекта

```
app/src/main/java/com/tastamat/fandomon/
├── data/
│   ├── local/              # Room Database и DAO
│   │   ├── FandomonDatabase.kt
│   │   ├── EventDao.kt
│   │   └── Converters.kt
│   ├── model/              # Модели данных
│   │   ├── EventType.kt
│   │   └── MonitorEvent.kt
│   ├── preferences/        # DataStore для настроек
│   │   └── AppPreferences.kt
│   ├── remote/             # API клиенты
│   │   ├── api/
│   │   │   ├── FandomonApi.kt
│   │   │   └── RetrofitClient.kt
│   │   ├── mqtt/
│   │   │   └── MqttClientManager.kt
│   │   └── dto/
│   │       └── EventDto.kt
│   └── repository/
│       └── EventRepository.kt
├── service/                # Сервисы и планировщики
│   ├── AlarmScheduler.kt
│   ├── MonitoringReceiver.kt
│   ├── FandomatMonitor.kt
│   ├── DataSyncService.kt
│   ├── CommandHandler.kt
│   └── NetworkUtils.kt
├── receiver/               # BroadcastReceivers
│   ├── BootReceiver.kt
│   ├── NetworkChangeReceiver.kt
│   └── PowerConnectionReceiver.kt
├── ui/                     # UI компоненты
│   ├── screen/
│   │   └── SettingsScreen.kt
│   ├── viewmodel/
│   │   └── SettingsViewModel.kt
│   └── theme/
└── MainActivity.kt
```

## Настройка

### 1. Настройки устройства

- **Device ID**: Уникальный идентификатор устройства (опционально, по умолчанию используется Android ID)
- **Device Name**: Понятное имя устройства (опционально, по умолчанию используется модель устройства)

### 2. Настройки Fandomat

- **Package Name**: Имя пакета приложения Fandomat (по умолчанию: `com.tastamat.fandomat`)
- **Check Interval**: Интервал проверки состояния Fandomat (в минутах, по умолчанию: 5)
- **Status Report Interval**: Интервал отправки статуса (в минутах, по умолчанию: 15)

### 3. Настройки MQTT

Включите MQTT и настройте:
- **Broker URL**: Адрес MQTT брокера
- **Port**: Порт (по умолчанию: 1883)
- **Username**: Имя пользователя для аутентификации
- **Password**: Пароль
- **Events Topic**: Топик для событий (по умолчанию: `fandomon/events`)
- **Status Topic**: Топик для статусов (по умолчанию: `fandomon/status`)
- **Commands Topic**: Топик для удаленных команд (по умолчанию: `fandomon/commands`)

### 4. Настройки REST API

Включите REST API и настройте:
- **Base URL**: Базовый URL API (например: `https://api.example.com/`)
- **API Key**: Ключ для аутентификации

## Использование

1. Откройте приложение
2. Настройте параметры мониторинга
3. Настройте MQTT или REST API (или оба)
4. Нажмите "Start Monitoring" для начала мониторинга
5. Приложение будет работать в фоновом режиме

## События

Приложение отслеживает следующие типы событий:

- `FANDOMAT_STOPPED` - Приложение Fandomat остановлено
- `FANDOMAT_RESTARTED` - Приложение Fandomat перезапущено
- `INTERNET_DISCONNECTED` - Потеряно подключение к интернету
- `INTERNET_CONNECTED` - Подключение к интернету восстановлено
- `POWER_OUTAGE` - Отключение питания
- `POWER_RESTORED` - Питание восстановлено
- `STATUS_UPDATE` - Обновление статуса
- `COMMAND_RESTART_FANDOMAT` - Удаленная команда перезапуска Fandomat выполнена
- `COMMAND_RESTART_FANDOMON` - Удаленная команда перезапуска Fandomon выполнена
- `COMMAND_UPDATE_SETTINGS` - Настройки обновлены через удаленную команду
- `COMMAND_CLEAR_EVENTS` - События очищены через удаленную команду
- `COMMAND_FORCE_SYNC` - Принудительная синхронизация выполнена
- `COMMAND_GET_STATUS` - Запрошен немедленный статус

## Формат данных

### Event (JSON)
```json
{
  "id": 1,
  "event_type": "FANDOMAT_STOPPED",
  "timestamp": 1699876543210,
  "message": "Fandomat application stopped",
  "device_id": "custom-device-001",
  "device_name": "Tablet Warehouse A"
}
```

### Status (JSON)
```json
{
  "fandomon_running": true,
  "fandomat_running": false,
  "internet_connected": true,
  "timestamp": 1699876543210,
  "device_id": "custom-device-001",
  "device_name": "Tablet Warehouse A"
}
```

## Удаленные команды (MQTT)

Приложение поддерживает следующие удаленные команды через MQTT топик `fandomon/commands`:

### 1. RESTART_FANDOMAT
Перезапуск приложения Fandomat
```json
{
  "command": "RESTART_FANDOMAT",
  "timestamp": 1699876543210
}
```

### 2. RESTART_FANDOMON
Перезапуск самого приложения Fandomon
```json
{
  "command": "RESTART_FANDOMON",
  "timestamp": 1699876543210
}
```

### 3. UPDATE_SETTINGS
Обновление настроек приложения
```json
{
  "command": "UPDATE_SETTINGS",
  "parameters": {
    "check_interval": "5",
    "status_interval": "15",
    "device_name": "Tablet New Name"
  },
  "timestamp": 1699876543210
}
```

Поддерживаемые параметры:
- `check_interval` - интервал проверки Fandomat (в минутах)
- `status_interval` - интервал отправки статуса (в минутах)
- `device_name` - имя устройства

### 4. CLEAR_EVENTS
Очистка всех событий из базы данных
```json
{
  "command": "CLEAR_EVENTS",
  "timestamp": 1699876543210
}
```

### 5. FORCE_SYNC
Принудительная синхронизация событий с сервером
```json
{
  "command": "FORCE_SYNC",
  "timestamp": 1699876543210
}
```

### 6. GET_STATUS
Запрос немедленной отправки статуса
```json
{
  "command": "GET_STATUS",
  "timestamp": 1699876543210
}
```

### Пример отправки команды через mosquitto_pub

```bash
# Перезапуск Fandomat
mosquitto_pub -h broker.example.com -p 1883 \
  -u username -P password \
  -t "fandomon/commands" \
  -m '{"command":"RESTART_FANDOMAT","timestamp":1699876543210}'

# Обновление настроек
mosquitto_pub -h broker.example.com -p 1883 \
  -u username -P password \
  -t "fandomon/commands" \
  -m '{"command":"UPDATE_SETTINGS","parameters":{"check_interval":"10"},"timestamp":1699876543210}'
```

## Разрешения

Приложение требует следующие разрешения:

- `INTERNET` - Для отправки данных
- `ACCESS_NETWORK_STATE` - Для проверки подключения
- `WAKE_LOCK` - Для работы AlarmManager
- `SCHEDULE_EXACT_ALARM` - Для точного расписания
- `RECEIVE_BOOT_COMPLETED` - Для запуска после перезагрузки
- `QUERY_ALL_PACKAGES` - Для мониторинга приложения Fandomat
- `POST_NOTIFICATIONS` - Для уведомлений (Android 13+)

## Оптимизация батареи

Приложение использует AlarmManager вместо ForegroundService для минимизации потребления батареи. Это означает:

- Нет постоянного уведомления в статус-баре
- Минимальная нагрузка на CPU
- Проверки выполняются по расписанию, а не постоянно
- Совместимость с Doze Mode и Battery Saver

## Тестирование

После запуска рекомендуется протестировать:

1. Остановку приложения Fandomat - должен произойти автоматический перезапуск
2. Отключение Wi-Fi/мобильных данных - событие должно быть залогировано
3. Отключение зарядки - событие должно быть залогировано
4. Перезагрузку устройства - мониторинг должен возобновиться автоматически

## Сборка

```bash
./gradlew assembleDebug
```

Или через Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)

## Troubleshooting

### Приложение не перезапускает Fandomat
- Проверьте правильность Package Name в настройках
- Убедитесь, что приложение Fandomat установлено
- Проверьте разрешение QUERY_ALL_PACKAGES

### События не отправляются
- Проверьте настройки MQTT/REST API
- Убедитесь в наличии интернет-подключения
- Проверьте логи приложения через Logcat

### Мониторинг не работает после перезагрузки
- Убедитесь, что разрешение RECEIVE_BOOT_COMPLETED предоставлено
- Проверьте, что приложение не в списке оптимизации батареи

## Лицензия

Для внутреннего использования администраторами системы Tastamat.

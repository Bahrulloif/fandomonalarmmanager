# Тестирование удаленных команд Fandomon

## Статус реализации

✅ **Система удаленных команд полностью реализована и активна**

Приложение подписано на MQTT топик `fandomon/commands` и готово принимать команды.

## Подтверждение работы

Из логов приложения:
```
10-20 15:50:06.970 D MqttClientManager: Connected to MQTT broker
10-20 15:50:06.970 D DataSyncService: ✅ Connected to MQTT broker for commands
10-20 15:50:09.121 D DataSyncService: ✅ Subscribed to commands topic: fandomon/commands
10-20 15:50:09.121 D MainActivity: ✅ Subscribed to MQTT commands
```

## Настройка MQTT клиента для тестирования

### Установка mosquitto_pub (если не установлен)

**macOS:**
```bash
brew install mosquitto
```

**Ubuntu/Debian:**
```bash
sudo apt-get install mosquitto-clients
```

**Windows:**
Скачайте с https://mosquitto.org/download/

## Примеры команд для тестирования

### 1. GET_STATUS - Запрос немедленного статуса
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{"command":"GET_STATUS","timestamp":1729428000000}'
```

**Ожидаемый результат:**
- Приложение немедленно отправит статус на топик `fandomon/status`
- В логах: `COMMAND_GET_STATUS` event logged

### 2. FORCE_SYNC - Принудительная синхронизация
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{"command":"FORCE_SYNC","timestamp":1729428000000}'
```

**Ожидаемый результат:**
- Все несинхронизированные события отправятся на сервер
- В логах: `COMMAND_FORCE_SYNC` event logged

### 3. UPDATE_SETTINGS - Обновление настроек
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{
    "command":"UPDATE_SETTINGS",
    "parameters":{
      "check_interval":"10",
      "status_interval":"30",
      "device_name":"Test Device"
    },
    "timestamp":1729428000000
  }'
```

**Ожидаемый результат:**
- Интервалы проверки обновятся на 10 и 30 минут соответственно
- Имя устройства изменится на "Test Device"
- В логах: `COMMAND_UPDATE_SETTINGS` event logged

### 4. RESTART_FANDOMAT - Перезапуск Fandomat
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{"command":"RESTART_FANDOMAT","timestamp":1729428000000}'
```

**Ожидаемый результат:**
- Выполнится команда `am start -n com.tastamat.fandomat/.MainActivity`
- Приложение Fandomat запустится
- В логах: `COMMAND_RESTART_FANDOMAT` event logged

### 5. CLEAR_EVENTS - Очистка базы событий
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{"command":"CLEAR_EVENTS","timestamp":1729428000000}'
```

**Ожидаемый результат:**
- Все события удалятся из базы данных
- В логах: `COMMAND_CLEAR_EVENTS` event logged с количеством удаленных записей

### 6. RESTART_FANDOMON - Перезапуск самого Fandomon
```bash
mosquitto_pub -h <broker_url> -p 1883 \
  -u <username> -P <password> \
  -t "fandomon/commands" \
  -m '{"command":"RESTART_FANDOMON","timestamp":1729428000000}'
```

**⚠️ ВНИМАНИЕ:** Эта команда перезапустит само приложение Fandomon!

**Ожидаемый результат:**
- Отменятся все AlarmManager задачи
- Приложение перезапустится
- После перезапуска все задачи переустановятся
- В логах: `COMMAND_RESTART_FANDOMON` event logged

## Мониторинг выполнения команд

### Через ADB Logcat
```bash
# Фильтр по CommandHandler
adb logcat | grep CommandHandler

# Фильтр по всем MQTT событиям
adb logcat | grep -E "MQTT|Command|DataSyncService"

# Фильтр только выполненных команд
adb logcat | grep "Executing command"
```

### Ожидаемые логи при получении команды

```
D CommandHandler: 🎯 Executing command: GET_STATUS
D CommandHandler: 📊 Executing GET_STATUS command
D CommandHandler: ✅ GET_STATUS command executed successfully
```

## Проверка настроек MQTT

Убедитесь, что в настройках приложения:
- ✅ MQTT включен (Enable MQTT = ON)
- ✅ Broker URL указан правильно
- ✅ Port = 1883 (или ваш порт)
- ✅ Username и Password заполнены
- ✅ Commands Topic = `fandomon/commands` (или ваш топик)

## Troubleshooting

### Команды не получаются

1. **Проверьте подключение к MQTT:**
   ```bash
   adb logcat | grep "Connected to MQTT broker"
   ```
   Должно быть: `✅ Connected to MQTT broker for commands`

2. **Проверьте подписку на топик:**
   ```bash
   adb logcat | grep "Subscribed to commands"
   ```
   Должно быть: `✅ Subscribed to commands topic: fandomon/commands`

3. **Проверьте, что топик правильный:**
   - В команде mosquitto_pub: `-t "fandomon/commands"`
   - В настройках приложения: Commands Topic должен совпадать

4. **Проверьте формат JSON:**
   - Используйте одинарные кавычки для обертки JSON
   - Внутри JSON используйте двойные кавычки
   - Проверьте валидность JSON через онлайн валидатор

### Команды получаются, но не выполняются

1. **Проверьте логи ошибок:**
   ```bash
   adb logcat | grep -E "Error|Failed|❌"
   ```

2. **Проверьте формат команды:**
   - Поле `command` должно быть в верхнем регистре
   - Timestamp должен быть числом (long)
   - Parameters должны быть объектом, не строкой

### Пример неправильного формата:
```json
{"command":"get_status"}  ❌ (lowercase)
{"command":"GET_STATUS"}  ✅ (uppercase)
```

## Следующие шаги

После успешного тестирования команд:

1. **Интеграция с сервером:**
   - Реализуйте логику на сервере для отправки команд
   - Добавьте UI для администратора

2. **Мониторинг:**
   - Подпишитесь на топики `fandomon/events` и `fandomon/status`
   - Отслеживайте события `COMMAND_*` для подтверждения выполнения

3. **Автоматизация:**
   - Настройте автоматическую отправку команд при определенных условиях
   - Например: `RESTART_FANDOMAT` при получении `FANDOMAT_STOPPED`

## Архитектура системы команд

```
MQTT Broker (mosquitto/HiveMQ/etc)
         ↓
    Topic: fandomon/commands
         ↓
   MqttClientManager.subscribe()
         ↓
   DataSyncService.handleIncomingCommand()
         ↓
   CommandHandler.parseCommand()
         ↓
   CommandHandler.executeCommand()
         ↓
   [Command execution with logging]
         ↓
   Event logged to database
         ↓
   Event sent to fandomon/events topic
```

## Поддерживаемые параметры для UPDATE_SETTINGS

| Параметр | Тип | Описание | Пример |
|----------|-----|----------|--------|
| `check_interval` | String (int) | Интервал проверки Fandomat (минуты) | "5" |
| `status_interval` | String (int) | Интервал отправки статуса (минуты) | "15" |
| `device_name` | String | Имя устройства | "Warehouse Tablet 1" |

## Безопасность

⚠️ **Важно:**
- Используйте MQTT с аутентификацией (username/password)
- Рассмотрите использование TLS/SSL для MQTT (порт 8883)
- Ограничьте доступ к топику команд только для администраторов
- Логируйте все полученные команды для аудита

## Статус

- ✅ MQTT подключение активно
- ✅ Подписка на команды активна
- ✅ CommandHandler готов к приему команд
- ✅ Все 6 типов команд реализованы
- ✅ Логирование событий работает
- 🔄 Ожидает тестирования реальными командами

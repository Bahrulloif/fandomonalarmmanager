# Команды для тестирования через broker.hivemq.com

## 📋 Ваши настройки MQTT

- **Broker:** broker.hivemq.com
- **Port:** 1883
- **Commands Topic:** fandomon/commands
- **Status Topic:** fandomon/status
- **Events Topic:** fandomon/events

## 🎯 Способы отправки команд

### Способ 1: Python скрипт (Рекомендуется)

1. **Установите библиотеку:**
   ```bash
   pip3 install paho-mqtt
   ```

2. **Запустите скрипт:**
   ```bash
   python3 test_commands.py
   ```

3. **Интерактивное меню:**
   - Выбираете команду из списка
   - Вводите параметры (если нужно)
   - Видите ответы от Fandomon в реальном времени

---

### Способ 2: Онлайн MQTT клиент

Используйте веб-клиент HiveMQ: http://www.hivemq.com/demos/websocket-client/

**Шаги:**
1. Откройте http://www.hivemq.com/demos/websocket-client/
2. Нажмите **Connect** (broker.hivemq.com уже настроен)
3. После подключения:
   - В секции **Subscriptions** добавьте топики для мониторинга:
     - `fandomon/status`
     - `fandomon/events`
   - В секции **Publish**:
     - Topic: `fandomon/commands`
     - QoS: 1
     - Message: (смотрите примеры ниже)

---

### Способ 3: Curl (если есть HTTP MQTT bridge)

Некоторые брокеры поддерживают HTTP API, но стандартный HiveMQ через порт 1883 работает только по MQTT протоколу.

---

## 📤 Примеры JSON команд

### 1. GET_STATUS - Запрос статуса
```json
{"command":"GET_STATUS","timestamp":1729500000000}
```

**Что произойдет:**
- Fandomon немедленно отправит статус на топик `fandomon/status`
- В базу добавится событие `COMMAND_GET_STATUS`

---

### 2. FORCE_SYNC - Синхронизация
```json
{"command":"FORCE_SYNC","timestamp":1729500000000}
```

**Что произойдет:**
- Все несинхронизированные события отправятся на сервер
- В базу добавится событие `COMMAND_FORCE_SYNC`

---

### 3. UPDATE_SETTINGS - Изменить настройки
```json
{
  "command":"UPDATE_SETTINGS",
  "parameters":{
    "check_interval":"10",
    "status_interval":"30",
    "device_name":"Тестовый планшет"
  },
  "timestamp":1729500000000
}
```

**Параметры (все опциональны):**
- `check_interval` - интервал проверки Fandomat в минутах
- `status_interval` - интервал отправки статуса в минутах
- `device_name` - имя устройства

**Что произойдет:**
- Настройки обновятся
- AlarmManager пересоздаст задачи с новыми интервалами
- В базу добавится событие `COMMAND_UPDATE_SETTINGS`

---

### 4. RESTART_FANDOMAT - Перезапуск Fandomat
```json
{"command":"RESTART_FANDOMAT","timestamp":1729500000000}
```

**Что произойдет:**
- Выполнится команда: `am start -n com.tastamat.fandomat/.MainActivity`
- Приложение Fandomat запустится
- В базу добавится событие `COMMAND_RESTART_FANDOMAT`

---

### 5. CLEAR_EVENTS - Очистка базы событий
```json
{"command":"CLEAR_EVENTS","timestamp":1729500000000}
```

**Что произойдет:**
- Все события удалятся из таблицы `monitor_events`
- В базу добавится событие `COMMAND_CLEAR_EVENTS` с количеством удаленных записей

---

### 6. RESTART_FANDOMON - Перезапуск Fandomon
```json
{"command":"RESTART_FANDOMON","timestamp":1729500000000}
```

⚠️ **ВНИМАНИЕ:** Эта команда перезапустит само приложение Fandomon!

**Что произойдет:**
- Все AlarmManager задачи отменятся
- Приложение перезапустится
- После перезапуска все задачи и подписки восстановятся
- В базу добавится событие `COMMAND_RESTART_FANDOMON`

---

## 🔍 Проверка результата

### Через ADB Logcat

```bash
# Смотреть выполнение команд
adb logcat | grep CommandHandler

# Смотреть все MQTT события
adb logcat | grep -E "MQTT|Command|DataSyncService"

# Смотреть только успешные команды
adb logcat | grep "Executing command"
```

### Ожидаемые логи при получении команды GET_STATUS:

```
D DataSyncService: 📥 Command received on [fandomon/commands]: {"command":"GET_STATUS","timestamp":1729500000000}
D CommandHandler: 🎯 Parsing command: {"command":"GET_STATUS","timestamp":1729500000000}
D CommandHandler: ✅ Command parsed: GET_STATUS
D CommandHandler: 🎯 Executing command: GET_STATUS
D CommandHandler: 📊 Executing GET_STATUS command
D CommandHandler: ✅ GET_STATUS command executed successfully
```

### Через MQTT подписку

Подпишитесь на топики:
- **fandomon/status** - получите статус после команды GET_STATUS
- **fandomon/events** - получите события о выполнении команд

Пример ответа на GET_STATUS:
```json
{
  "fandomon_running": true,
  "fandomat_running": false,
  "internet_connected": true,
  "timestamp": 1729500123456,
  "device_id": "your-device-id",
  "device_name": "Тестовый планшет"
}
```

Пример события выполнения команды:
```json
{
  "id": 42,
  "event_type": "COMMAND_GET_STATUS",
  "timestamp": 1729500123456,
  "message": "GET_STATUS command executed",
  "device_id": "your-device-id",
  "device_name": "Тестовый планшет"
}
```

---

## 🧪 Быстрый тест

### Самый простой тест через Python (одна команда):

Создайте файл `quick_test.py`:
```python
import paho.mqtt.client as mqtt
import json
import time

client = mqtt.Client()
client.connect("broker.hivemq.com", 1883, 60)
client.loop_start()

# Отправляем команду GET_STATUS
command = {"command": "GET_STATUS", "timestamp": int(time.time() * 1000)}
client.publish("fandomon/commands", json.dumps(command), qos=1)
print(f"✅ Команда отправлена: {command}")

time.sleep(2)
client.loop_stop()
client.disconnect()
```

Запуск:
```bash
python3 quick_test.py
```

---

## ⚠️ Troubleshooting

### Команды не получаются

1. **Проверьте подключение Fandomon к MQTT:**
   ```bash
   adb logcat | grep "Connected to MQTT broker"
   ```
   Должно быть: `✅ Connected to MQTT broker for commands`

2. **Проверьте подписку:**
   ```bash
   adb logcat | grep "Subscribed to commands"
   ```
   Должно быть: `✅ Subscribed to commands topic: fandomon/commands`

3. **Проверьте топик:**
   - Убедитесь что отправляете на `fandomon/commands` (без пробелов)
   - Регистр важен!

4. **Проверьте формат JSON:**
   - Поле `command` должно быть в ВЕРХНЕМ регистре
   - Timestamp - число (не строка)
   - Используйте валидатор JSON

### Команды получаются, но не выполняются

1. **Проверьте логи ошибок:**
   ```bash
   adb logcat | grep -E "CommandHandler.*Error|Failed"
   ```

2. **Проверьте формат:**
   ```json
   {"command":"GET_STATUS"}          ✅ Правильно
   {"command":"get_status"}          ❌ Неправильно (lowercase)
   {"command":"GET_STATUS",}         ❌ Неправильно (лишняя запятая)
   ```

---

## 📊 Мониторинг в реальном времени

### Терминал 1: Логи команд
```bash
adb logcat -c
adb logcat | grep -E "CommandHandler|Command received"
```

### Терминал 2: MQTT мониторинг (через Python)
```python
import paho.mqtt.client as mqtt

def on_message(client, userdata, msg):
    print(f"[{msg.topic}] {msg.payload.decode()}")

client = mqtt.Client()
client.on_message = on_message
client.connect("broker.hivemq.com", 1883, 60)
client.subscribe("fandomon/#")  # Все топики fandomon
client.loop_forever()
```

---

## 🎯 Рекомендуемый порядок тестирования

1. ✅ **GET_STATUS** - проверить что команды работают
2. ✅ **FORCE_SYNC** - проверить синхронизацию
3. ✅ **UPDATE_SETTINGS** - изменить интервал на 10 минут
4. ✅ **RESTART_FANDOMAT** - проверить перезапуск Fandomat
5. ⚠️ **CLEAR_EVENTS** - только если нужно очистить базу
6. ⚠️ **RESTART_FANDOMON** - только если нужен перезапуск

---

## 📝 Примечания

- **HiveMQ публичный брокер** - не требует аутентификации
- **Ваши топики должны быть уникальными** - добавьте префикс с ID устройства если работаете с несколькими планшетами
- **QoS 1** обязателен для команд (гарантия доставки)
- **Timestamp** можно использовать любой (текущее время в миллисекундах)

---

## 🚀 Готово!

Приложение **работает** и **готово принимать команды** через broker.hivemq.com:1883!

Выберите удобный способ и протестируйте команды.

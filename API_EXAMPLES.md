# API Examples for Fandomon

Примеры серверной реализации для приёма данных от Fandomon.

## REST API

### Node.js + Express

```javascript
const express = require('express');
const app = express();

app.use(express.json());

// Middleware для проверки API ключа
const authMiddleware = (req, res, next) => {
  const apiKey = req.headers.authorization?.replace('Bearer ', '');
  if (apiKey !== process.env.API_KEY) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
};

// Endpoint для получения событий
app.post('/events', authMiddleware, (req, res) => {
  const event = req.body;
  console.log('Received event:', event);

  // Сохранение в базу данных
  // await db.events.insert(event);

  res.status(200).send();
});

// Endpoint для получения статуса
app.post('/status', authMiddleware, (req, res) => {
  const status = req.body;
  console.log('Received status:', status);

  // Сохранение в базу данных
  // await db.status.insert(status);

  res.status(200).send();
});

app.listen(3000, () => {
  console.log('Fandomon API listening on port 3000');
});
```

### Python + Flask

```python
from flask import Flask, request, jsonify
import os

app = Flask(__name__)

def check_auth():
    api_key = request.headers.get('Authorization', '').replace('Bearer ', '')
    return api_key == os.getenv('API_KEY')

@app.route('/events', methods=['POST'])
def receive_event():
    if not check_auth():
        return jsonify({'error': 'Unauthorized'}), 401

    event = request.json
    print(f'Received event: {event}')

    # Сохранение в базу данных
    # db.events.insert(event)

    return '', 200

@app.route('/status', methods=['POST'])
def receive_status():
    if not check_auth():
        return jsonify({'error': 'Unauthorized'}), 401

    status = request.json
    print(f'Received status: {status}')

    # Сохранение в базу данных
    # db.status.insert(status)

    return '', 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)
```

## MQTT Broker

### Mosquitto Configuration

```conf
# mosquitto.conf

# Порт
listener 1883

# Аутентификация
allow_anonymous false
password_file /etc/mosquitto/passwd

# Логирование
log_dest file /var/log/mosquitto/mosquitto.log
log_type all

# Персистентность
persistence true
persistence_location /var/lib/mosquitto/
```

Создание пользователя:
```bash
mosquitto_passwd -c /etc/mosquitto/passwd fandomon_user
```

### MQTT Subscriber (Python)

```python
import paho.mqtt.client as mqtt
import json

BROKER = "mqtt.example.com"
PORT = 1883
USERNAME = "fandomon_user"
PASSWORD = "your_password"

def on_connect(client, userdata, flags, rc):
    print(f"Connected with result code {rc}")
    client.subscribe("fandomon/events")
    client.subscribe("fandomon/status")

def on_message(client, userdata, msg):
    topic = msg.topic
    payload = json.loads(msg.payload.decode())

    if topic == "fandomon/events":
        print(f"Event received: {payload}")
        # Обработка события
        # save_event_to_db(payload)

    elif topic == "fandomon/status":
        print(f"Status received: {payload}")
        # Обработка статуса
        # save_status_to_db(payload)

client = mqtt.Client()
client.username_pw_set(USERNAME, PASSWORD)
client.on_connect = on_connect
client.on_message = on_message

client.connect(BROKER, PORT, 60)
client.loop_forever()
```

### MQTT Subscriber (Node.js)

```javascript
const mqtt = require('mqtt');

const options = {
  username: 'fandomon_user',
  password: 'your_password'
};

const client = mqtt.connect('mqtt://mqtt.example.com:1883', options);

client.on('connect', () => {
  console.log('Connected to MQTT broker');
  client.subscribe('fandomon/events');
  client.subscribe('fandomon/status');
});

client.on('message', (topic, message) => {
  const payload = JSON.parse(message.toString());

  if (topic === 'fandomon/events') {
    console.log('Event received:', payload);
    // Обработка события
    // await saveEventToDb(payload);
  } else if (topic === 'fandomon/status') {
    console.log('Status received:', payload);
    // Обработка статуса
    // await saveStatusToDb(payload);
  }
});
```

## Database Schema Examples

### PostgreSQL

```sql
-- Таблица событий
CREATE TABLE fandomon_events (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    timestamp BIGINT NOT NULL,
    message TEXT,
    received_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_device_id (device_id),
    INDEX idx_event_type (event_type),
    INDEX idx_timestamp (timestamp)
);

-- Таблица статусов
CREATE TABLE fandomon_status (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    fandomon_running BOOLEAN NOT NULL,
    fandomat_running BOOLEAN NOT NULL,
    internet_connected BOOLEAN NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_device_id (device_id),
    INDEX idx_timestamp (timestamp)
);
```

### MongoDB

```javascript
// Events collection
db.createCollection("fandomon_events", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["event_id", "device_id", "event_type", "timestamp"],
      properties: {
        event_id: { bsonType: "long" },
        device_id: { bsonType: "string" },
        event_type: { bsonType: "string" },
        timestamp: { bsonType: "long" },
        message: { bsonType: "string" },
        received_at: { bsonType: "date" }
      }
    }
  }
});

// Status collection
db.createCollection("fandomon_status", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["device_id", "fandomon_running", "fandomat_running", "internet_connected", "timestamp"],
      properties: {
        device_id: { bsonType: "string" },
        fandomon_running: { bsonType: "bool" },
        fandomat_running: { bsonType: "bool" },
        internet_connected: { bsonType: "bool" },
        timestamp: { bsonType: "long" },
        received_at: { bsonType: "date" }
      }
    }
  }
});

// Indexes
db.fandomon_events.createIndex({ device_id: 1 });
db.fandomon_events.createIndex({ event_type: 1 });
db.fandomon_events.createIndex({ timestamp: -1 });

db.fandomon_status.createIndex({ device_id: 1 });
db.fandomon_status.createIndex({ timestamp: -1 });
```

## Dashboard Examples

### Simple Web Dashboard (HTML + JavaScript)

```html
<!DOCTYPE html>
<html>
<head>
    <title>Fandomon Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .card { border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .status-ok { color: green; }
        .status-error { color: red; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <h1>Fandomon Monitoring Dashboard</h1>

    <div class="card">
        <h2>Current Status</h2>
        <div id="status"></div>
    </div>

    <div class="card">
        <h2>Recent Events</h2>
        <table id="events">
            <thead>
                <tr>
                    <th>Time</th>
                    <th>Device</th>
                    <th>Event Type</th>
                    <th>Message</th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>

    <script>
        // Загрузка данных каждые 10 секунд
        setInterval(loadData, 10000);
        loadData();

        async function loadData() {
            const status = await fetch('/api/status/latest').then(r => r.json());
            const events = await fetch('/api/events/recent').then(r => r.json());

            displayStatus(status);
            displayEvents(events);
        }

        function displayStatus(status) {
            const statusDiv = document.getElementById('status');
            statusDiv.innerHTML = `
                <p>Device: ${status.device_id}</p>
                <p>Fandomon: <span class="${status.fandomon_running ? 'status-ok' : 'status-error'}">${status.fandomon_running ? 'Running' : 'Stopped'}</span></p>
                <p>Fandomat: <span class="${status.fandomat_running ? 'status-ok' : 'status-error'}">${status.fandomat_running ? 'Running' : 'Stopped'}</span></p>
                <p>Internet: <span class="${status.internet_connected ? 'status-ok' : 'status-error'}">${status.internet_connected ? 'Connected' : 'Disconnected'}</span></p>
                <p>Last Update: ${new Date(status.timestamp).toLocaleString()}</p>
            `;
        }

        function displayEvents(events) {
            const tbody = document.querySelector('#events tbody');
            tbody.innerHTML = events.map(event => `
                <tr>
                    <td>${new Date(event.timestamp).toLocaleString()}</td>
                    <td>${event.device_id}</td>
                    <td>${event.event_type}</td>
                    <td>${event.message || '-'}</td>
                </tr>
            `).join('');
        }
    </script>
</body>
</html>
```

## Telegram Bot Notifications

```python
import requests
from typing import Dict

TELEGRAM_BOT_TOKEN = "your_bot_token"
TELEGRAM_CHAT_ID = "your_chat_id"

def send_telegram_notification(event: Dict):
    message = f"""
⚠️ Fandomon Alert

Device: {event['device_id']}
Event: {event['event_type']}
Message: {event['message']}
Time: {event['timestamp']}
    """

    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {
        "chat_id": TELEGRAM_CHAT_ID,
        "text": message,
        "parse_mode": "Markdown"
    }

    requests.post(url, json=payload)

# Использование
def on_critical_event(event):
    if event['event_type'] in ['FANDOMAT_STOPPED', 'INTERNET_DISCONNECTED', 'POWER_OUTAGE']:
        send_telegram_notification(event)
```

# Targeted MQTT Commands for Multiple Devices

## Overview

Starting from **v2.3.4**, Fandomon supports **device-specific (targeted) commands** in addition to broadcast commands. This allows you to send commands to specific devices when you have multiple Fandomon installations.

## How It Works

Each Fandomon device subscribes to **TWO MQTT topics**:

1. **Broadcast Topic** (all devices receive): `fandomon/commands`
2. **Device-Specific Topic** (only one device receives): `fandomon/{device_id}/commands`

Where `{device_id}` is the unique identifier set in Fandomon Settings → Device ID.

## MQTT Topic Structure

### Broadcast Commands (All Devices)
```
Topic: fandomon/commands
Payload: {"command": "restart_fandomat"}
```
→ **ALL devices** will execute this command

### Targeted Commands (Specific Device)
```
Topic: fandomon/tablet-1/commands
Payload: {"command": "restart_fandomat"}
```
→ **ONLY** the device with `device_id = "tablet-1"` will execute this command

## Example Scenarios

### Scenario 1: Multiple Tablets in Different Locations

You have 3 tablets:
- **Tablet 1** (Warehouse A): `device_id = "warehouse-a"`
- **Tablet 2** (Warehouse B): `device_id = "warehouse-b"`
- **Tablet 3** (Office): `device_id = "office-main"`

#### Send Command to ALL Tablets
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command": "restart_fandomat"}'
```
→ All 3 tablets will restart Fandomat

#### Send Command to ONLY Warehouse A Tablet
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/warehouse-a/commands" \
  -m '{"command": "restart_fandomat"}'
```
→ Only Warehouse A tablet will restart Fandomat

#### Send Different Commands to Different Tablets
```bash
# Restart Fandomat on Warehouse A
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/warehouse-a/commands" \
  -m '{"command": "restart_fandomat"}'

# Stop monitoring on Warehouse B
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/warehouse-b/commands" \
  -m '{"command": "stop_monitoring"}'

# Get status from Office tablet
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/office-main/commands" \
  -m '{"command": "send_status"}'
```

## Available Commands

All commands work with both broadcast and targeted topics:

### 1. Restart Fandomat Application
```json
{"command": "restart_fandomat"}
```

### 2. Start Monitoring
```json
{"command": "start_monitoring"}
```

### 3. Stop Monitoring
```json
{"command": "stop_monitoring"}
```

### 4. Send Status Report
```json
{"command": "send_status"}
```

### 5. Sync Events
```json
{"command": "sync_events"}
```

## Python Example: Sending Targeted Commands

```python
import paho.mqtt.client as mqtt
import json

# MQTT Configuration
BROKER = "broker.hivemq.com"
PORT = 1883
USERNAME = "your_username"
PASSWORD = "your_password"

def send_command_to_device(device_id, command):
    """Send command to specific device"""
    client = mqtt.Client()
    client.username_pw_set(USERNAME, PASSWORD)
    client.connect(BROKER, PORT, 60)

    topic = f"fandomon/{device_id}/commands"
    payload = json.dumps({"command": command})

    client.publish(topic, payload, qos=1)
    print(f"✅ Sent '{command}' to device '{device_id}' on topic '{topic}'")

    client.disconnect()

def send_broadcast_command(command):
    """Send command to ALL devices"""
    client = mqtt.Client()
    client.username_pw_set(USERNAME, PASSWORD)
    client.connect(BROKER, PORT, 60)

    topic = "fandomon/commands"
    payload = json.dumps({"command": command})

    client.publish(topic, payload, qos=1)
    print(f"📢 Broadcast '{command}' to ALL devices on topic '{topic}'")

    client.disconnect()

# Example usage
if __name__ == "__main__":
    # Send command to specific device
    send_command_to_device("warehouse-a", "restart_fandomat")
    send_command_to_device("warehouse-b", "send_status")
    send_command_to_device("office-main", "start_monitoring")

    # Send broadcast command to all devices
    send_broadcast_command("sync_events")
```

## Node.js Example: Targeted Commands

```javascript
const mqtt = require('mqtt');

const BROKER_URL = 'mqtt://broker.hivemq.com:1883';
const USERNAME = 'your_username';
const PASSWORD = 'your_password';

// Connect to MQTT broker
const client = mqtt.connect(BROKER_URL, {
    username: USERNAME,
    password: PASSWORD
});

client.on('connect', () => {
    console.log('✅ Connected to MQTT broker');

    // Send command to specific device
    sendCommandToDevice('warehouse-a', 'restart_fandomat');
    sendCommandToDevice('warehouse-b', 'send_status');

    // Send broadcast command
    sendBroadcastCommand('sync_events');

    // Disconnect after sending
    setTimeout(() => client.end(), 2000);
});

function sendCommandToDevice(deviceId, command) {
    const topic = `fandomon/${deviceId}/commands`;
    const payload = JSON.stringify({ command: command });

    client.publish(topic, payload, { qos: 1 }, (err) => {
        if (err) {
            console.error(`❌ Error sending to ${deviceId}:`, err);
        } else {
            console.log(`✅ Sent '${command}' to device '${deviceId}'`);
        }
    });
}

function sendBroadcastCommand(command) {
    const topic = 'fandomon/commands';
    const payload = JSON.stringify({ command: command });

    client.publish(topic, payload, { qos: 1 }, (err) => {
        if (err) {
            console.error('❌ Error broadcasting:', err);
        } else {
            console.log(`📢 Broadcast '${command}' to ALL devices`);
        }
    });
}
```

## Configuration in Fandomon App

### Setting Up Device ID

1. Open **Fandomon app**
2. Go to **Settings**
3. Find **Device Settings** section
4. Set **Device ID**: Enter a unique identifier (e.g., `warehouse-a`, `tablet-01`, `office-main`)
5. Set **Device Name**: Human-readable name (e.g., `Warehouse A Tablet`)
6. Make sure **MQTT** is enabled with correct broker settings

### Verifying Subscription

After setting Device ID and enabling MQTT, check the logs:

```
📡 MQTT Command Subscription Summary:
  • Broadcast topic: fandomon/commands (for ALL devices)
  • Device-specific topic: fandomon/warehouse-a/commands (for THIS device only)
  • Device ID: warehouse-a
```

## Best Practices

### 1. Use Meaningful Device IDs
❌ Bad: `device1`, `device2`, `abc123`
✅ Good: `warehouse-a`, `office-tablet`, `production-line-1`

### 2. Naming Convention
Recommended format: `{location}-{purpose}` or `{location}-{number}`
- `warehouse-a-main`
- `office-reception-01`
- `factory-line-3`

### 3. When to Use Broadcast vs Targeted

**Use Broadcast** (`fandomon/commands`) when:
- You want to sync events on ALL devices
- Performing maintenance on all tablets
- Testing connectivity

**Use Targeted** (`fandomon/{device_id}/commands`) when:
- Restarting a specific problematic device
- Getting status from one device
- Managing devices individually

### 4. Testing Commands

Before deploying to production:

1. Test with ONE device first using targeted commands
2. Verify the device responds correctly
3. Check MQTT logs for confirmation
4. Then scale to multiple devices

## Monitoring Command Execution

### MQTT Logs on Device

When a device receives a command, you'll see logs like:

**Broadcast Command:**
```
📥 BROADCAST command received on [fandomon/commands]: {"command":"restart_fandomat"}
🔍 Parsing BROADCAST command: {"command":"restart_fandomat"}
✅ Command parsed successfully: restart_fandomat (source: BROADCAST)
```

**Targeted Command:**
```
📥 TARGETED command received on [fandomon/warehouse-a/commands]: {"command":"restart_fandomat"}
🔍 Parsing TARGETED command: {"command":"restart_fandomat"}
✅ Command parsed successfully: restart_fandomat (source: TARGETED)
```

### Event Responses

After executing a command, the device will send events back:

**Events Topic:** `fandomon/events` (or your configured topic)
```json
{
  "id": 123,
  "eventType": "FANDOMAT_RESTARTING",
  "timestamp": 1729516800000,
  "message": "Attempting automatic restart of Fandomat",
  "deviceId": "warehouse-a",
  "deviceName": "Warehouse A Tablet"
}
```

## Troubleshooting

### Device Not Responding to Targeted Commands

1. **Check Device ID in Settings**
   - Make sure Device ID is set correctly
   - Device ID is case-sensitive: `Warehouse-A` ≠ `warehouse-a`

2. **Check MQTT Connection**
   - Verify MQTT is enabled in settings
   - Check broker URL, port, username, password
   - Look for connection logs

3. **Check Topic Format**
   - Correct: `fandomon/warehouse-a/commands`
   - Wrong: `fandomon/warehouse-a/command` (no 's')
   - Wrong: `fandomon/commands/warehouse-a`

4. **Check Subscription Logs**
   - Device should log: "✅ Subscribed to DEVICE-SPECIFIC commands: fandomon/{id}/commands"
   - If missing, MQTT connection might have failed

### All Devices Responding to Targeted Command

- You probably have multiple devices with the **same Device ID**
- Each device MUST have a **unique** Device ID
- Change Device ID in settings to make them unique

## Version Information

- **Feature Added:** v2.3.4
- **Minimum Required Version:** v2.3.4+
- **Backward Compatibility:** Yes, broadcast commands still work on all versions

## Summary

| Feature | Broadcast Topic | Device-Specific Topic |
|---------|----------------|----------------------|
| Topic Format | `fandomon/commands` | `fandomon/{device_id}/commands` |
| Who Receives | ALL devices | ONE device |
| Use Case | Global commands | Individual control |
| Requires Device ID | No | Yes |
| Example | Send status from all | Restart specific tablet |

---

**For more information, see:**
- [КОМАНДЫ_HIVEMQ.md](КОМАНДЫ_HIVEMQ.md) - Command reference in Russian
- [REMOTE_COMMANDS_TESTING.md](REMOTE_COMMANDS_TESTING.md) - Testing guide

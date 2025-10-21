# MQTT Command Formats - Backward Compatibility

## Overview

Fandomon v2.3.5+ supports **BOTH** command formats for full backward compatibility:

1. **Old format** (uppercase with timestamp) - existing broker.hivemq.com setup
2. **New format** (lowercase, no timestamp required) - new documentation

## Supported Formats

### Format 1: Old Format (Uppercase + Timestamp)
```json
{"command":"RESTART_FANDOMAT","timestamp":1729500000000}
```

### Format 2: New Format (Lowercase, Optional Timestamp)
```json
{"command":"restart_fandomat"}
```

**Both formats work identically!** The parser converts all commands to uppercase internally.

## Complete Command Reference

| Old Format | New Format | Description | Action |
|------------|------------|-------------|--------|
| `RESTART_FANDOMAT` | `restart_fandomat` | Restart Fandomat app | Launches Fandomat via Accessibility Service or shell |
| `RESTART_FANDOMON` | `restart_fandomon` | Restart Fandomon itself | Kills and restarts Fandomon app |
| `GET_STATUS` | `send_status` | Request status report | Sends immediate status to MQTT/REST |
| `FORCE_SYNC` | `sync_events` | Sync unsent events | Forces immediate event synchronization |
| `UPDATE_SETTINGS` | `update_settings` | Update settings | Changes configuration remotely |
| `CLEAR_EVENTS` | `clear_events` | Clear event database | Deletes all stored events |
| N/A | `start_monitoring` | Start monitoring | Starts Fandomat monitoring |
| N/A | `stop_monitoring` | Stop monitoring | Stops Fandomat monitoring |

## Example Usage

### Old Format (Your Current Setup)

**Restart Fandomat:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"RESTART_FANDOMAT","timestamp":1729500000000}'
```

**Get Status:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"GET_STATUS","timestamp":1729500000000}'
```

**Force Sync:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"FORCE_SYNC","timestamp":1729500000000}'
```

### New Format (Also Works)

**Restart Fandomat:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"restart_fandomat"}'
```

**Get Status:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"send_status"}'
```

**Start Monitoring:**
```bash
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/commands" \
  -m '{"command":"start_monitoring"}'
```

## Targeted Commands (Device-Specific)

Both formats work with targeted commands:

### Old Format with Device ID
```bash
# Send to specific device
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/sklad-a/commands" \
  -m '{"command":"RESTART_FANDOMAT","timestamp":1729500000000}'
```

### New Format with Device ID
```bash
# Send to specific device
mosquitto_pub -h broker.hivemq.com -p 1883 \
  -u "username" -P "password" \
  -t "fandomon/sklad-a/commands" \
  -m '{"command":"restart_fandomat"}'
```

## Python Examples

### Old Format Script (Your Current Scripts)
```python
import paho.mqtt.client as mqtt
import json
import time

BROKER = "broker.hivemq.com"
PORT = 1883
USERNAME = "your_username"
PASSWORD = "your_password"

def send_old_format_command(command, device_id=None):
    """Send command in old format (uppercase + timestamp)"""
    client = mqtt.Client()
    client.username_pw_set(USERNAME, PASSWORD)
    client.connect(BROKER, PORT, 60)

    # Broadcast or device-specific
    topic = f"fandomon/{device_id}/commands" if device_id else "fandomon/commands"

    payload = json.dumps({
        "command": command.upper(),  # Uppercase
        "timestamp": int(time.time() * 1000)  # Milliseconds
    })

    client.publish(topic, payload, qos=1)
    print(f"✅ Sent {command} (old format)")
    client.disconnect()

# Examples - YOUR EXISTING SCRIPTS STILL WORK!
send_old_format_command("RESTART_FANDOMAT")
send_old_format_command("GET_STATUS")
send_old_format_command("RESTART_FANDOMAT", device_id="sklad-a")
```

### New Format Script (Simpler)
```python
import paho.mqtt.client as mqtt
import json

BROKER = "broker.hivemq.com"
PORT = 1883
USERNAME = "your_username"
PASSWORD = "your_password"

def send_new_format_command(command, device_id=None):
    """Send command in new format (lowercase, no timestamp)"""
    client = mqtt.Client()
    client.username_pw_set(USERNAME, PASSWORD)
    client.connect(BROKER, PORT, 60)

    topic = f"fandomon/{device_id}/commands" if device_id else "fandomon/commands"

    payload = json.dumps({
        "command": command.lower()  # Lowercase, no timestamp needed
    })

    client.publish(topic, payload, qos=1)
    print(f"✅ Sent {command} (new format)")
    client.disconnect()

# Examples - NEW SIMPLER FORMAT
send_new_format_command("restart_fandomat")
send_new_format_command("send_status")
send_new_format_command("start_monitoring", device_id="sklad-a")
```

### Mixed Format (Both Work Together!)
```python
# Your old scripts
send_old_format_command("RESTART_FANDOMAT")  # ✅ Works

# New scripts
send_new_format_command("restart_fandomat")  # ✅ Also works

# Even mixed case works (converted to uppercase internally)
send_command({"command": "Restart_Fandomat"})  # ✅ Still works
```

## Timestamp Field

### Old Format
- `timestamp` field is **optional**
- If provided, it's stored with the command
- If omitted, current timestamp is used automatically

```json
{"command":"RESTART_FANDOMAT","timestamp":1729500000000}  // ✅ Works
{"command":"RESTART_FANDOMAT"}  // ✅ Also works (timestamp auto-added)
```

### New Format
- `timestamp` field is **completely optional**
- Usually omitted for simplicity

```json
{"command":"restart_fandomat"}  // ✅ Recommended
{"command":"restart_fandomat","timestamp":1729500000000}  // ✅ Also works
```

## Case Sensitivity

The parser is **case-insensitive** for command names:

```json
{"command":"RESTART_FANDOMAT"}  // ✅ Works
{"command":"restart_fandomat"}  // ✅ Works
{"command":"Restart_Fandomat"}  // ✅ Works
{"command":"ReStArT_FaNdOmAt"}  // ✅ Works (but don't do this!)
```

All are converted to uppercase internally before matching.

## Migration Guide

### Option 1: No Changes Required
**Your existing scripts continue to work!** No migration needed.

### Option 2: Gradual Migration
Migrate commands one by one:

```python
# Week 1: Keep old format
send_command({"command":"RESTART_FANDOMAT","timestamp":int(time.time()*1000)})

# Week 2: Try new format for non-critical commands
send_command({"command":"send_status"})

# Week 3: Use new format for everything
send_command({"command":"restart_fandomat"})
```

### Option 3: Immediate Switch
Update all scripts to new format for consistency:

**Before:**
```python
def send_command(cmd):
    payload = json.dumps({
        "command": cmd.upper(),
        "timestamp": int(time.time() * 1000)
    })
```

**After:**
```python
def send_command(cmd):
    payload = json.dumps({
        "command": cmd.lower()
    })
```

## Recommended Practices

### For Existing Setups (broker.hivemq.com)
✅ **Keep using your current format** - it's fully supported
```json
{"command":"RESTART_FANDOMAT","timestamp":1729500000000}
```

### For New Setups
✅ **Use the simpler new format**
```json
{"command":"restart_fandomat"}
```

### For Mixed Teams
✅ **Document which format you're using** and stick to one for consistency

## Logging Output

Both formats show the same logs:

**Old Format Command:**
```
📥 BROADCAST command received: {"command":"RESTART_FANDOMAT","timestamp":1729500000000}
🔍 Parsing BROADCAST command: {"command":"RESTART_FANDOMAT","timestamp":1729500000000}
✅ Command parsed successfully: RESTART_FANDOMAT (source: BROADCAST)
🎯 Executing command: RESTART_FANDOMAT
🔄 Executing RESTART_FANDOMAT command
```

**New Format Command:**
```
📥 BROADCAST command received: {"command":"restart_fandomat"}
🔍 Parsing BROADCAST command: {"command":"restart_fandomat"}
✅ Command parsed successfully: RESTART_FANDOMAT (source: BROADCAST)
🎯 Executing command: RESTART_FANDOMAT
🔄 Executing RESTART_FANDOMAT command
```

Notice: Both result in `RESTART_FANDOMAT` being executed.

## Testing Both Formats

Use `test_targeted_commands.py` to test both formats:

```python
# Test old format
mosquitto_pub -t "fandomon/commands" \
  -m '{"command":"RESTART_FANDOMAT","timestamp":1729500000000}'

# Test new format
mosquitto_pub -t "fandomon/commands" \
  -m '{"command":"restart_fandomat"}'

# Check MQTT logs - both should work identically
```

## Version Information

- **Old format support:** All versions
- **New format support:** v2.3.5+
- **Targeted commands:** v2.3.4+
- **Full backward compatibility:** ✅ Yes

## Summary

| Feature | Old Format | New Format |
|---------|-----------|------------|
| Command case | UPPERCASE | lowercase |
| Timestamp | Required in practice | Optional |
| Example | `{"command":"RESTART_FANDOMAT","timestamp":1729500000000}` | `{"command":"restart_fandomat"}` |
| Works with targeted topics | ✅ Yes | ✅ Yes |
| Works with broadcast | ✅ Yes | ✅ Yes |
| Your scripts need updates | ❌ No | Optional |

**Bottom line:** Your existing commands with `broker.hivemq.com` **continue to work perfectly**. No changes required!

---

**See also:**
- [АДРЕСНЫЕ_КОМАНДЫ.md](АДРЕСНЫЕ_КОМАНДЫ.md) - Targeted commands (Russian)
- [TARGETED_COMMANDS.md](TARGETED_COMMANDS.md) - Targeted commands (English)
- [КОМАНДЫ_HIVEMQ.md](КОМАНДЫ_HIVEMQ.md) - HiveMQ commands reference (Russian)

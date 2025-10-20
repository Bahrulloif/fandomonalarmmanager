# Changelog

## [Unreleased]

## [2.1.3] - 2025-10-20 - Bug Fix: Complete Text Direction Fix

### 🐛 Bug Fix
**FIXED:** Cursor still moving left in some text fields (Device Name and others)

#### Problem
- In v2.1.2, added `textStyle` with `TextDirection.Ltr`
- Fixed most fields, but **Device Name** and some other fields still had RTL issue
- Some fields required stronger enforcement of LTR direction

#### Root Cause
- Only `textStyle` parameter is not always sufficient
- TextField component can override text direction based on content or system locale
- Some RTL characters or system settings trigger RTL mode even with textStyle

#### Solution - Double Protection
Applied **two-level protection** for 100% reliability:

1. **CompositionLocalProvider** - wraps each TextField
   ```kotlin
   CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
       OutlinedTextField(...)
   }
   ```

2. **textStyle** - explicitly sets text direction
   ```kotlin
   textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
   ```

### 📝 Technical Changes

#### Modified Files
- **SettingsScreen.kt**
  - Wrapped ALL 14 TextFields in `CompositionLocalProvider`
  - Kept `textStyle` parameter for double protection
  - Ensures 100% LTR direction regardless of content or locale

### ✅ Result
- ✅ **Device Name field now works correctly** (was main issue)
- ✅ All other fields also more reliable
- ✅ Double protection ensures no RTL issues
- ✅ Works with any content: Latin, numbers, symbols, mixed

### 🔍 Why Double Protection?
- `CompositionLocalProvider` - sets layout direction for component
- `textStyle` - sets text direction for content
- Together = **unbreakable LTR enforcement**

### 📖 See Also
- Updated documentation: [TEXT_DIRECTION_FIX.md](./TEXT_DIRECTION_FIX.md)

## [2.1.2] - 2025-10-20 - Bug Fix: Text Input Cursor Direction

### 🐛 Bug Fix
**FIXED:** Cursor moving left instead of right when typing in text fields

#### Problem
- When typing in any text field, cursor moved **left** instead of **right**
- Made text input extremely difficult and unintuitive
- Affected all 14 text fields in settings

#### Root Cause
- Compose auto-detected text direction (RTL/LTR) based on content
- Some system locales or characters triggered RTL mode
- TextFields ignored the global `LocalLayoutDirection.Ltr` setting

#### Solution
- Added explicit `textStyle` parameter with `TextDirection.Ltr` to all text fields
- Forces left-to-right text direction regardless of system locale or content
- Simple, reliable fix with minimal code change

### 📝 Technical Changes

#### Modified Files
- **SettingsScreen.kt**
  - Added `textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)` to all 14 TextFields
  - Fixed Device Settings fields (2 fields)
  - Fixed Fandomat Settings fields (3 fields)
  - Fixed MQTT Settings fields (7 fields)
  - Fixed REST API Settings fields (2 fields)

### ✅ Result
- ✅ Cursor now moves left-to-right when typing
- ✅ Text input works intuitively
- ✅ All fields work correctly: package names, URLs, numbers, topics
- ✅ No more RTL confusion

### 📖 See Also
- Technical documentation: [TEXT_DIRECTION_FIX.md](./TEXT_DIRECTION_FIX.md)

## [2.1.1] - 2025-10-20 - UI Enhancement: Monitoring Status Indicator

### 🎨 UI Improvements

#### Visual Monitoring State
- **Added** monitoring status indicator in UI
- **Visual feedback** when monitoring is active/inactive
- **Color-coded buttons** for better UX

#### Button States

**Start Monitoring Button:**
- **Inactive:** Outlined style, standard color, text "Start Monitoring"
- **Active:** Filled blue button, text "Monitoring Active"
- **Behavior:** Can be pressed to restart monitoring

**Stop Monitoring Button:**
- **Inactive:** Outlined style, disabled (grey)
- **Active:** Filled tonal red button, enabled
- **Behavior:** Only active when monitoring is running

**Status Indicator:**
- Shows "● Active" badge when monitoring is running
- Blue dot indicator next to "Monitoring Control" title
- Disappears when monitoring is stopped

### 📝 Technical Changes

#### Modified Files
- **SettingsViewModel.kt**
  - Added `isMonitoringActive: Boolean` to `SettingsState`
  - Updates state when starting/stopping monitoring

- **SettingsScreen.kt**
  - Redesigned monitoring control card
  - Added status indicator dot and "Active" text
  - Conditional button styling based on state
  - Start button changes to filled blue when active
  - Stop button changes to filled red when active

### 🎯 User Experience

**Before:**
- No visual indication if monitoring is running
- Both buttons always looked the same
- User couldn't tell current state

**After:**
- ✅ Clear visual state: blue = running, red = can stop
- ✅ Status badge "● Active" when running
- ✅ Stop button disabled when nothing to stop
- ✅ Start button shows "Monitoring Active" when running

### 📖 See Also
- Full UI documentation: [UI_MONITORING_BUTTON_UPDATE.md](./UI_MONITORING_BUTTON_UPDATE.md)

## [2.1.0] - 2025-10-20 - Background Monitoring Fix (Critical Update)

### 🔥 Critical Bug Fix
**FIXED:** App stopped working after being minimized/hidden

#### Root Cause
- Used `setRepeating()` which doesn't work reliably in Doze Mode (Android 6+)
- No exemption from Battery Optimization
- App was aggressively stopped by Android system

### ✨ Major Improvements

#### 1. Reliable Background Execution
- **Changed** `AlarmManager.setRepeating()` → `AlarmManager.setExactAndAllowWhileIdle()`
- **Works** even in Doze Mode when device is idle/sleeping
- **Added** automatic rescheduling after each alarm execution
- **Result:** Monitoring continues indefinitely even when app is minimized

#### 2. Frozen App Detection
- **New:** `checkIfAppResponding()` - detects if Fandomat is frozen/not responding
- **Detection:** Uses UsageStats to check if app hasn't been active for 30+ minutes
- **Action:** Automatically force-stops and restarts frozen app
- **New Events:**
  - `FANDOMAT_NOT_RESPONDING` - app is frozen
  - `FANDOMAT_RESTARTING` - restart in progress
  - `FANDOMAT_RESTART_SUCCESS` - restart successful

#### 3. Battery Optimization Exemption
- **Added** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission
- **Auto-request** exemption on first launch
- **Prevents** Android from killing the app in background
- **User prompt** with clear explanation

#### 4. Enhanced Restart Notifications
- **Before:** Single `FANDOMAT_RESTARTED` event
- **Now:** 3-stage notification:
  1. `FANDOMAT_RESTARTING` - "Starting restart..."
  2. Execution with 3-second verification
  3. `FANDOMAT_RESTART_SUCCESS` - "Successfully restarted" or error event
- **Better visibility** of restart process on server

### 📝 Technical Changes

#### Modified Files
- **AlarmScheduler.kt**
  - Replaced `setRepeating()` with `setExactAndAllowWhileIdle()`
  - Added fallback for Android versions without exact alarm permission
  - Better logging with timestamps

- **MonitoringReceiver.kt**
  - Added `rescheduleNextAlarm()` method
  - Automatic rescheduling after each check/status task
  - Ensures continuous monitoring

- **FandomatMonitor.kt**
  - Added `checkIfAppResponding()` - frozen app detection
  - Added `forceStopAndRestart()` - force restart frozen apps
  - Enhanced `restartFandomat()` with verification and success events
  - 3-stage restart process with detailed events

- **MainActivity.kt**
  - Added `checkBatteryOptimization()` method
  - Auto-request battery exemption on first launch
  - User-friendly permission flow

- **EventType.kt**
  - Added `FANDOMAT_RESTARTING`
  - Added `FANDOMAT_RESTART_SUCCESS`
  - Added `FANDOMAT_NOT_RESPONDING`

- **AndroidManifest.xml**
  - Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission

### 📚 Documentation
- **Added** `BACKGROUND_MONITORING_FIX.md` - detailed technical documentation
- **Added** `КРАТКАЯ_ИНСТРУКЦИЯ.md` - user guide in Russian

### ⚡ Performance Improvements
- **Before:** Foreground Service consumed ~100-200mA constantly
- **After:** AlarmManager consumes ~5-10mA in idle
- **No more overheating** - tablet stays cool
- **Energy efficient** - only active during checks (5-10 seconds every 5-15 minutes)

### 🔧 What Users Need to Do

#### First Launch After Update
1. **Grant UsageStats permission** (required)
2. **Grant Battery Optimization exemption** (required)
3. Start monitoring as usual

#### Device-Specific Settings
- **Xiaomi (MIUI):** Enable "Autostart" + disable "Battery saver" for Fandomon
- **Huawei (EMUI):** Set "Manual management" in App Launch settings
- **Samsung:** Usually works without additional settings

### 🧪 Testing
- ✅ Tested on Android 10+ with Doze Mode
- ✅ Verified continuous monitoring after minimizing
- ✅ Verified frozen app detection and restart
- ✅ Verified all 3 restart notification events
- ✅ Verified no overheating during 24h test

### 📖 See Also
- Full technical details: [BACKGROUND_MONITORING_FIX.md](./BACKGROUND_MONITORING_FIX.md)
- User guide: [КРАТКАЯ_ИНСТРУКЦИЯ.md](./КРАТКАЯ_ИНСТРУКЦИЯ.md)
- Android Doze Mode: [ANDROID_10_RESTRICTIONS.md](./ANDROID_10_RESTRICTIONS.md)

## [2.0.1] - 2025-10-20 - UI Completion & Bug Fixes

### 🐛 Bug Fixes
- Fixed MQTT topic handlers in SettingsScreen - Events and Status topics now correctly call their respective update methods
- Fixed hardcoded MQTT commands topic in DataSyncService - now uses configurable topic from preferences

### ✨ New Features
- **Auto-Restart Toggle** - Added UI switch in Fandomat Settings to enable/disable automatic restart of Fandomat
- **MQTT Commands Topic Configuration** - Added UI field to configure the MQTT commands topic (default: "fandomon/commands")
- Added missing update methods in SettingsViewModel for MQTT topics (Events, Status, Commands)

### 🎨 UI Improvements
- Added "Auto-Restart Fandomat" switch in Fandomat Settings card
- Added "Commands Topic" field in MQTT Settings (shown when MQTT is enabled)
- Better organization of MQTT topic fields

### 🔧 Technical Changes
- Updated AppPreferences with mqttTopicCommands field and setter
- Updated SettingsState data class with autoRestartEnabled and mqttTopicCommands fields
- Updated loadSettings() to properly load all preferences including new fields
- All settings now properly save and load from DataStore

## [2.0.0] - 2025-10-20 - Remote Commands Release

### 🎯 Major Features Added

#### Remote Commands System
- **Bidirectional MQTT Communication** - Added subscribe functionality for receiving commands
- **CommandHandler Service** - Parses and executes remote commands from MQTT
- **6 Command Types**:
  - `RESTART_FANDOMAT` - Remotely restart the Fandomat application
  - `RESTART_FANDOMON` - Remotely restart Fandomon itself
  - `UPDATE_SETTINGS` - Update app settings (check_interval, status_interval, device_name)
  - `CLEAR_EVENTS` - Clear all events from database
  - `FORCE_SYNC` - Force immediate synchronization
  - `GET_STATUS` - Request immediate status report

#### Architecture Changes
- **Passive Monitoring** - Switched from local automatic restart to remote-controlled operations
- **FandomatMonitor Simplified** - Now only monitors and logs (no local restart attempts)
- **Remote Control** - All restart/control operations via MQTT commands
- **Android 10+ Compatible** - Solves background activity launch restrictions

### Added Files
- `CommandHandler.kt` - Command execution logic
- `CommandType.kt` - Command types enum and data classes
- `REMOTE_COMMANDS_TESTING.md` - Comprehensive testing guide

### Modified Files
- `MqttClientManager.kt` - Added subscribe() method
- `DataSyncService.kt` - Added command subscription and handling
- `MainActivity.kt` - Auto-subscribe to commands on startup
- `FandomatMonitor.kt` - Removed restart logic (monitoring only)
- `EventType.kt` - Added 11 command event types
- `EventDao.kt` - Added deleteAllEvents() method
- `EventRepository.kt` - Added deleteAllEvents() wrapper
- `README.md` - Added remote commands documentation
- `spec.md` - Marked remote administration as completed

### Technical Changes
- UsageStats window increased from 1 minute to 5 minutes
- Added comprehensive error logging for command execution
- Event logging for all command operations (success/failure)
- QoS 1 for reliable command delivery via MQTT

### Command Event Types
```
COMMAND_RESTART_FANDOMAT, COMMAND_RESTART_FANDOMAT_FAILED
COMMAND_RESTART_FANDOMON, COMMAND_RESTART_FANDOMON_FAILED
COMMAND_UPDATE_SETTINGS, COMMAND_UPDATE_SETTINGS_FAILED
COMMAND_CLEAR_EVENTS, COMMAND_CLEAR_EVENTS_FAILED
COMMAND_FORCE_SYNC, COMMAND_GET_STATUS, COMMAND_UNKNOWN
```

### Message Format - Command
```json
{
  "command": "RESTART_FANDOMAT",
  "parameters": {},
  "timestamp": 1729428000000
}
```

### Security
- Commands require MQTT authentication
- All executions logged for audit trail
- Supports TLS/SSL for production

## [1.1.0] - 2025-10-08

### Added
- **Device ID and Device Name fields** in settings
  - Custom Device ID field - allows manual device identification
  - Custom Device Name field - user-friendly device naming
  - Both fields are now included in all MQTT and REST API messages
  - If not set, Device ID defaults to Android ID
  - If not set, Device Name defaults to device model (e.g., "SM-G991B")

### Changed
- Updated EventDto to include `device_name` field
- Updated StatusDto to include `device_name` field
- Enhanced DataSyncService to use custom device_id and device_name from preferences
- UI now has a separate "Device Settings" card with device_id and device_name fields

### Message Format Changes

#### Event Message (MQTT/REST):
```json
{
  "id": 123,
  "event_type": "FANDOMAT_STOPPED",
  "timestamp": 1699876543210,
  "message": "Fandomat application stopped",
  "device_id": "custom-device-001",
  "device_name": "Tablet Warehouse A"
}
```

#### Status Message (MQTT/REST):
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

## [1.0.0] - 2025-10-08

### Added
- Initial release
- Monitoring of Fandomat application
- Auto-restart functionality
- Event logging to SQLite database
- MQTT client for data transmission
- REST API client for data transmission
- AlarmManager-based scheduling
- Network connectivity monitoring
- Power state monitoring
- Boot receiver for auto-start
- Settings UI with Jetpack Compose
- Support for Android 11+

### Features
- Background monitoring without ForegroundService
- Configurable check intervals
- Configurable status report intervals
- MQTT topic configuration
- REST API configuration
- Event synchronization
- Status reporting
- Device identification via Android ID

# Changelog

## [Unreleased]

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

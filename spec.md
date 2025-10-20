# Specification for Android Application **Fandomon**

## 1. General Information
- **Application Name**: Fandomon  
- **Description**: Monitoring application for tracking the state and performance of **Fandomat**. Provides error logging, log collection, health monitoring, and data transmission to the server.  
- **Target Audience**: Administrators and technical specialists maintaining the Fandomat system.  
- **Platform**: Android 11 (API Level 30).  

## 2. Features
- [x] Monitoring of the Fandomat application (logs, state, availability).  
- [x] Logging of critical events:  
  - application crashes,  
  - internet disconnections,  
  - missing logs,  
  - power outages.  
- [x] Tracking the state of Fandomon itself (background service, status reporting).  
- [x] Auto-restart of the Fandomat application if stopped.  
- [x] Storing events in a local SQLite database.  
- [x] Sending events and statuses via **MQTT** or **REST API**.
- [x] Remote administration via MQTT commands.  

## 3. User Flows
- **Flow 1**: Fandomat stops working → Fandomon detects the event → attempts to restart Fandomat → event saved in SQLite → event sent to server (MQTT/REST).  
- **Flow 2**: Internet connection is lost → event recorded in SQLite → upon recovery, data is sent to the server.  
- **Flow 3**: Power outage occurs → event recorded → transmitted to the server once power is restored.  
- **Flow 4**: Administrator remotely receives the status of Fandomat via Fandomon.  

## 4. UI/UX
- Minimal interface for configuration:  
  - MQTT/REST server,  
  - status update intervals,  
  - logging options.  
- Background service with minimal CPU load.  

## 5. Technical Specifications
- **Programming Language**: Kotlin (Android Studio).  
- **Architecture**: Service + Repository (MVVM if UI is required).  
- **Backend**: MQTT broker / REST API.  
- **Database**: SQLite (via Room).  
- **Task Scheduler**: **AlarmManager** for periodic checks and status reporting (avoids constant wakelocks and high CPU usage).  
- **Service Policy**: Do **not** use **ForegroundService** to minimize CPU and battery consumption on the tablet.  
- **Third-Party Libraries**:  
  - MQTT (Eclipse Paho or equivalent),  
  - Retrofit (for REST).  

## 6. API and Data
- **MQTT**: Topics for events and statuses.  
- **REST**: POST requests for events and statuses.  
- **Message Format**: JSON (id, event type, timestamp, status).  

## 7. Security
- HTTPS for REST communication.  
- Authentication for MQTT connections.  
- Data encryption in transit.  

## 8. Testing
- Unit tests for logic (JUnit).  
- Integration tests (event storage and transmission).  
- UI tests (Espresso, if UI is present).  
- Stress tests for internet/power outage scenarios.  
- Validation of **AlarmManager** operation under Doze Mode and Battery Saver.  

## 9. Deployment
- Build via Gradle.  
- CI/CD pipeline (optional).  
- Release for restricted internal use (administrators).  

## 10. Roadmap
- **MVP** ✅ (COMPLETED): monitoring service for Fandomat, SQLite event storage, data transmission via MQTT/REST, AlarmManager-based scheduling.
- **Phase 2** (Future): remote administration and extended UI.

## 11. Implementation Status

### ✅ Completed Components

1. **Database Layer**
   - Room database with EventDao
   - MonitorEvent entity with type converters
   - Event types enum (FANDOMAT_STOPPED, INTERNET_DISCONNECTED, etc.)

2. **Data Management**
   - EventRepository for database operations
   - AppPreferences using DataStore for settings
   - Support for both MQTT and REST API configurations

3. **Monitoring Services**
   - AlarmScheduler for periodic checks
   - FandomatMonitor for app status checking and auto-restart
   - MonitoringReceiver for handling scheduled alarms
   - DataSyncService for syncing events to server

4. **Network Layer**
   - MqttClientManager for MQTT communication
   - RetrofitClient for REST API calls
   - Event and Status DTOs
   - NetworkUtils for connectivity checks

5. **System Event Receivers**
   - BootReceiver for auto-start after reboot
   - NetworkChangeReceiver for internet connectivity changes
   - PowerConnectionReceiver for power state changes

6. **User Interface**
   - SettingsScreen with Jetpack Compose
   - SettingsViewModel with state management
   - Configuration UI for MQTT, REST, and monitoring intervals

7. **Permissions & Manifest**
   - All required permissions configured
   - Receivers and services registered
   - MQTT service included

### 📝 Documentation
   - README.md with setup instructions
   - API_EXAMPLES.md with server implementation examples
   - Architecture documentation

### 🎯 Recent Additions (Phase 2)

8. **Remote Commands System**
   - CommandHandler for command parsing and execution
   - MQTT subscribe functionality for bidirectional communication
   - 6 command types: RESTART_FANDOMAT, RESTART_FANDOMON, UPDATE_SETTINGS, CLEAR_EVENTS, FORCE_SYNC, GET_STATUS
   - Command event logging and tracking
   - Real-time command execution with error handling

### 🔄 Future Enhancements
   - Web-based remote administration panel
   - Push notifications for critical events
   - Advanced analytics dashboard
   - Log file collection from Fandomat

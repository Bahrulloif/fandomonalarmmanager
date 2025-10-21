package com.tastamat.fandomon.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.CommandType
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import com.tastamat.fandomon.data.model.RemoteCommand
import com.tastamat.fandomon.data.preferences.AppPreferences
import com.tastamat.fandomon.data.repository.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

class CommandHandler(private val context: Context) {

    private val TAG = "CommandHandler"
    private val eventRepository = EventRepository(FandomonDatabase.getDatabase(context).eventDao())
    private val preferences = AppPreferences(context)

    /**
     * Parse incoming MQTT command message
     *
     * Supports BOTH formats:
     * - Old format: {"command":"RESTART_FANDOMAT","timestamp":1729500000000}
     * - New format: {"command":"restart_fandomat"}
     */
    fun parseCommand(jsonString: String): RemoteCommand? {
        return try {
            val json = JSONObject(jsonString)
            val commandStr = json.optString("command", "")
            val commandType = when (commandStr.uppercase()) {
                // Old format commands (uppercase with underscores)
                "RESTART_FANDOMAT" -> CommandType.RESTART_FANDOMAT
                "RESTART_FANDOMON" -> CommandType.RESTART_FANDOMON
                "UPDATE_SETTINGS" -> CommandType.UPDATE_SETTINGS
                "CLEAR_EVENTS" -> CommandType.CLEAR_EVENTS
                "FORCE_SYNC" -> CommandType.FORCE_SYNC
                "GET_STATUS" -> CommandType.GET_STATUS

                // New format commands (lowercase with underscores) - map to existing commands
                "START_MONITORING" -> CommandType.START_MONITORING
                "STOP_MONITORING" -> CommandType.STOP_MONITORING
                "SEND_STATUS" -> CommandType.SEND_STATUS  // Alias for GET_STATUS
                "SYNC_EVENTS" -> CommandType.SYNC_EVENTS  // Alias for FORCE_SYNC

                else -> CommandType.UNKNOWN
            }

            // Parse parameters if present
            val parameters = mutableMapOf<String, String>()
            if (json.has("parameters")) {
                val paramsJson = json.getJSONObject("parameters")
                paramsJson.keys().forEach { key ->
                    parameters[key] = paramsJson.getString(key)
                }
            }

            RemoteCommand(
                command = commandType,
                parameters = parameters,
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing command: ${e.message}", e)
            null
        }
    }

    /**
     * Execute remote command
     */
    fun executeCommand(command: RemoteCommand) {
        Log.d(TAG, "🎯 Executing command: ${command.command}")

        when (command.command) {
            CommandType.RESTART_FANDOMAT -> restartFandomat()
            CommandType.RESTART_FANDOMON -> restartFandomon()
            CommandType.UPDATE_SETTINGS -> updateSettings(command.parameters)
            CommandType.CLEAR_EVENTS -> clearEvents()
            CommandType.FORCE_SYNC -> forceSync()
            CommandType.GET_STATUS -> sendImmediateStatus()

            // New format commands
            CommandType.START_MONITORING -> startMonitoring()
            CommandType.STOP_MONITORING -> stopMonitoring()
            CommandType.SEND_STATUS -> sendImmediateStatus()  // Alias for GET_STATUS
            CommandType.SYNC_EVENTS -> forceSync()  // Alias for FORCE_SYNC

            CommandType.UNKNOWN -> {
                Log.w(TAG, "⚠️ Unknown command received")
                CoroutineScope(Dispatchers.IO).launch {
                    logCommandEvent("COMMAND_UNKNOWN", "Unknown command received")
                }
            }
        }
    }

    private fun restartFandomat() {
        Log.d(TAG, "🔄 Executing RESTART_FANDOMAT command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageName = preferences.fandomatPackageName.first()

                // Method 1: Try ADB shell command
                val command = "am start -n $packageName/.MainActivity"
                Log.d(TAG, "Executing shell command: $command")
                Runtime.getRuntime().exec(command)

                logCommandEvent(
                    "COMMAND_RESTART_FANDOMAT",
                    "Remote restart command executed for $packageName"
                )
                Log.d(TAG, "✅ RESTART_FANDOMAT command executed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing RESTART_FANDOMAT: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_RESTART_FANDOMAT_FAILED",
                    "Failed to restart Fandomat: ${e.message}"
                )
            }
        }
    }

    private fun restartFandomon() {
        Log.d(TAG, "🔄 Executing RESTART_FANDOMON command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log the restart command
                logCommandEvent(
                    "COMMAND_RESTART_FANDOMON",
                    "Remote restart command received - restarting Fandomon"
                )

                // Cancel all alarms
                AlarmScheduler(context).cancelAllAlarms()

                // Restart the app by launching MainActivity with NEW_TASK flag
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                // Small delay to ensure event is logged
                kotlinx.coroutines.delay(500)

                if (intent != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "✅ RESTART_FANDOMON command executed - app restarting")

                    // Exit current process to force restart
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing RESTART_FANDOMON: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_RESTART_FANDOMON_FAILED",
                    "Failed to restart Fandomon: ${e.message}"
                )
            }
        }
    }

    private fun updateSettings(parameters: Map<String, String>) {
        Log.d(TAG, "⚙️ Executing UPDATE_SETTINGS command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update settings based on parameters
                parameters.forEach { (key, value) ->
                    when (key) {
                        "check_interval" -> {
                            val interval = value.toIntOrNull() ?: return@forEach
                            preferences.setCheckIntervalMinutes(interval)
                            Log.d(TAG, "Updated check_interval to $interval")
                        }
                        "status_interval" -> {
                            val interval = value.toIntOrNull() ?: return@forEach
                            preferences.setStatusReportIntervalMinutes(interval)
                            Log.d(TAG, "Updated status_interval to $interval")
                        }
                        "device_name" -> {
                            preferences.setDeviceName(value)
                            Log.d(TAG, "Updated device_name to $value")
                        }
                        else -> Log.w(TAG, "Unknown setting: $key")
                    }
                }

                logCommandEvent(
                    "COMMAND_UPDATE_SETTINGS",
                    "Settings updated via remote command: ${parameters.keys.joinToString()}"
                )
                Log.d(TAG, "✅ UPDATE_SETTINGS command executed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing UPDATE_SETTINGS: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_UPDATE_SETTINGS_FAILED",
                    "Failed to update settings: ${e.message}"
                )
            }
        }
    }

    private fun clearEvents() {
        Log.d(TAG, "🗑️ Executing CLEAR_EVENTS command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deletedCount = eventRepository.deleteAllEvents()
                logCommandEvent(
                    "COMMAND_CLEAR_EVENTS",
                    "Cleared $deletedCount events from database"
                )
                Log.d(TAG, "✅ CLEAR_EVENTS command executed - deleted $deletedCount events")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing CLEAR_EVENTS: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_CLEAR_EVENTS_FAILED",
                    "Failed to clear events: ${e.message}"
                )
            }
        }
    }

    private fun forceSync() {
        Log.d(TAG, "🔄 Executing FORCE_SYNC command")
        try {
            // Trigger immediate sync by sending broadcast
            val intent = Intent(context, MonitoringReceiver::class.java).apply {
                action = MonitoringReceiver.ACTION_SYNC_EVENTS
            }
            context.sendBroadcast(intent)

            CoroutineScope(Dispatchers.IO).launch {
                logCommandEvent(
                    "COMMAND_FORCE_SYNC",
                    "Force sync triggered via remote command"
                )
            }
            Log.d(TAG, "✅ FORCE_SYNC command executed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing FORCE_SYNC: ${e.message}", e)
        }
    }

    private fun sendImmediateStatus() {
        Log.d(TAG, "📊 Executing GET_STATUS command")
        try {
            // Trigger immediate status report
            val intent = Intent(context, MonitoringReceiver::class.java).apply {
                action = MonitoringReceiver.ACTION_SEND_STATUS
            }
            context.sendBroadcast(intent)

            CoroutineScope(Dispatchers.IO).launch {
                logCommandEvent(
                    "COMMAND_GET_STATUS",
                    "Immediate status report requested via remote command"
                )
            }
            Log.d(TAG, "✅ GET_STATUS command executed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing GET_STATUS: ${e.message}", e)
        }
    }

    private fun startMonitoring() {
        Log.d(TAG, "▶️ Executing START_MONITORING command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Set monitoring active flag
                preferences.setMonitoringActive(true)

                // Get current intervals
                val checkInterval = preferences.checkIntervalMinutes.first()
                val statusInterval = preferences.statusReportIntervalMinutes.first()

                // Schedule monitoring alarms
                val scheduler = AlarmScheduler(context)
                scheduler.scheduleMonitoring(checkInterval, statusInterval)

                logCommandEvent(
                    "COMMAND_START_MONITORING",
                    "Monitoring started via remote command (check: ${checkInterval}min, status: ${statusInterval}min)"
                )
                Log.d(TAG, "✅ START_MONITORING command executed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing START_MONITORING: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_START_MONITORING_FAILED",
                    "Failed to start monitoring: ${e.message}"
                )
            }
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "⏹️ Executing STOP_MONITORING command")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Set monitoring inactive flag
                preferences.setMonitoringActive(false)

                // Cancel all monitoring alarms
                val scheduler = AlarmScheduler(context)
                scheduler.cancelAllAlarms()

                logCommandEvent(
                    "COMMAND_STOP_MONITORING",
                    "Monitoring stopped via remote command"
                )
                Log.d(TAG, "✅ STOP_MONITORING command executed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing STOP_MONITORING: ${e.message}", e)
                logCommandEvent(
                    "COMMAND_STOP_MONITORING_FAILED",
                    "Failed to stop monitoring: ${e.message}"
                )
            }
        }
    }

    private suspend fun logCommandEvent(eventType: String, message: String) {
        try {
            val event = MonitorEvent(
                eventType = EventType.valueOf(eventType),
                message = message
            )
            eventRepository.insertEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging command event: ${e.message}", e)
        }
    }
}

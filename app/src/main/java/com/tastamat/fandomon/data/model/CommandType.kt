package com.tastamat.fandomon.data.model

enum class CommandType {
    RESTART_FANDOMAT,      // Restart Fandomat app (via ADB or other method)
    RESTART_FANDOMON,      // Restart Fandomon itself
    UPDATE_SETTINGS,       // Update configuration settings
    CLEAR_EVENTS,          // Clear local event database
    FORCE_SYNC,            // Force immediate event sync
    GET_STATUS,            // Request immediate status report
    UNKNOWN                // Unknown command
}

data class RemoteCommand(
    val command: CommandType,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

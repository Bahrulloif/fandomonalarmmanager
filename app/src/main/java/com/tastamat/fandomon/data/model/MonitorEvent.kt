package com.tastamat.fandomon.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitor_events")
data class MonitorEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: EventType,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null,
    val isSent: Boolean = false
)

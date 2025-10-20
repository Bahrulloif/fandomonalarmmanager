package com.tastamat.fandomon.data.local

import androidx.room.*
import com.tastamat.fandomon.data.model.MonitorEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: MonitorEvent): Long

    @Update
    suspend fun updateEvent(event: MonitorEvent)

    @Query("SELECT * FROM monitor_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<MonitorEvent>>

    @Query("SELECT * FROM monitor_events WHERE isSent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentEvents(): List<MonitorEvent>

    @Query("UPDATE monitor_events SET isSent = 1 WHERE id = :eventId")
    suspend fun markEventAsSent(eventId: Long)

    @Query("DELETE FROM monitor_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEvents(beforeTimestamp: Long)

    @Query("DELETE FROM monitor_events")
    suspend fun deleteAllEvents(): Int

    @Query("SELECT * FROM monitor_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): MonitorEvent?
}

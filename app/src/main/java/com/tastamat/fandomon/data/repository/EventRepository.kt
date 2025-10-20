package com.tastamat.fandomon.data.repository

import com.tastamat.fandomon.data.local.EventDao
import com.tastamat.fandomon.data.model.MonitorEvent
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {

    fun getAllEvents(): Flow<List<MonitorEvent>> = eventDao.getAllEvents()

    suspend fun insertEvent(event: MonitorEvent): Long = eventDao.insertEvent(event)

    suspend fun getUnsentEvents(): List<MonitorEvent> = eventDao.getUnsentEvents()

    suspend fun markEventAsSent(eventId: Long) = eventDao.markEventAsSent(eventId)

    suspend fun deleteOldEvents(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        eventDao.deleteOldEvents(cutoffTime)
    }

    suspend fun deleteAllEvents(): Int = eventDao.deleteAllEvents()

    suspend fun getEventById(eventId: Long): MonitorEvent? = eventDao.getEventById(eventId)
}

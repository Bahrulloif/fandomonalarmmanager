package com.tastamat.fandomon.data.local

import androidx.room.TypeConverter
import com.tastamat.fandomon.data.model.EventType

class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String {
        return value.name
    }

    @TypeConverter
    fun toEventType(value: String): EventType {
        return EventType.valueOf(value)
    }
}

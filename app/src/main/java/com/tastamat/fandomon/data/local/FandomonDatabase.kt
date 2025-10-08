package com.tastamat.fandomon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tastamat.fandomon.data.model.MonitorEvent

@Database(entities = [MonitorEvent::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FandomonDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: FandomonDatabase? = null

        fun getDatabase(context: Context): FandomonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FandomonDatabase::class.java,
                    "fandomon_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

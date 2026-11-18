package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubstanceLogEntity::class,
        SleepSessionEntity::class,
        AiImpactReportEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CircadiaDatabase : RoomDatabase() {
    abstract fun circadiaDao(): CircadiaDao

    companion object {
        @Volatile
        private var INSTANCE: CircadiaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE substance_logs ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): CircadiaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CircadiaDatabase::class.java,
                    "circadia_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

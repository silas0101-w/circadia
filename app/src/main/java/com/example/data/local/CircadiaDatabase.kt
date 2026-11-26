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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE substance_logs ADD COLUMN syncId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE substance_logs SET syncId = 'log_' || id WHERE syncId = ''"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

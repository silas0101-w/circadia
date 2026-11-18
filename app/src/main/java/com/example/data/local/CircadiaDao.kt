package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CircadiaDao {

    // Substance Logs
    @Query("SELECT * FROM substance_logs WHERE dateIso = :dateIso ORDER BY takenAtMillis ASC")
    fun observeLogsForDate(dateIso: String): Flow<List<SubstanceLogEntity>>

    @Query("SELECT * FROM substance_logs ORDER BY takenAtMillis DESC")
    fun observeAllLogs(): Flow<List<SubstanceLogEntity>>

    @Query("SELECT * FROM substance_logs ORDER BY takenAtMillis ASC")
    suspend fun getAllLogsOnce(): List<SubstanceLogEntity>

    @Query("SELECT * FROM substance_logs WHERE dateIso = :dateIso ORDER BY takenAtMillis ASC")
    suspend fun getLogsForDateOnce(dateIso: String): List<SubstanceLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SubstanceLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<SubstanceLogEntity>)

    @Update
    suspend fun updateLog(log: SubstanceLogEntity)

    @Query("DELETE FROM substance_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    // Sleep Sessions
    @Query("SELECT * FROM sleep_sessions ORDER BY dateIso DESC")
    fun observeAllSleepSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE dateIso = :dateIso LIMIT 1")
    fun observeSleepSessionForDate(dateIso: String): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions ORDER BY dateIso DESC")
    suspend fun getAllSleepSessionsOnce(): List<SleepSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepSessions(sessions: List<SleepSessionEntity>)

    @Query("DELETE FROM sleep_sessions WHERE dateIso = :dateIso")
    suspend fun deleteSleepSession(dateIso: String)

    // AI Impact Reports
    @Query("SELECT * FROM ai_impact_reports WHERE scopeKey = :scopeKey LIMIT 1")
    fun observeReport(scopeKey: String): Flow<AiImpactReportEntity?>

    @Query("SELECT * FROM ai_impact_reports WHERE dateIso = :dateIso ORDER BY createdAtMillis DESC")
    fun observeReportsForDate(dateIso: String): Flow<List<AiImpactReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: AiImpactReportEntity)

    @Query("DELETE FROM ai_impact_reports WHERE scopeKey = :scopeKey")
    suspend fun deleteReport(scopeKey: String)
}

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "substance_logs")
data class SubstanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String,
    val substanceId: String,
    val substanceName: String,
    val category: String,
    val dosageAmount: Double,
    val dosageUnit: String,
    val takenAtMillis: Long,
    val halfLifeHours: Double,
    val disruptionWeight: Double,
    val notes: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val dateIso: String,
    val source: String,
    val totalDurationMinutes: Int,
    val remMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val restingHeartRateBpm: Int?,
    val bedtimeMillis: Long,
    val wakeTimeMillis: Long,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_impact_reports")
data class AiImpactReportEntity(
    @PrimaryKey val scopeKey: String, // "day:2026-11-17" or "entry:42"
    val dateIso: String,
    val providerUsed: String,
    val modelUsed: String,
    val severityRating: String,
    val biologicalMechanism: String,
    val mitigationTipsJson: String, // JSON array of strings
    val createdAtMillis: Long = System.currentTimeMillis()
)

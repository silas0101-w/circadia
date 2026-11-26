package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale
import java.util.UUID
import kotlin.math.round

fun generateLogSyncId(): String =
    "log_" + UUID.randomUUID().toString().replace("-", "").take(24)

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
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncId: String = generateLogSyncId()
) {
    fun resolvedSyncId(): String {
        val cleaned = syncId.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(128)
        return when {
            cleaned.isNotBlank() -> cleaned
            id > 0L -> "log_$id"
            else -> generateLogSyncId()
        }
    }

    fun contentFingerprint(): String {
        val doseCents = if (dosageAmount.isFinite()) round(dosageAmount * 100.0).toLong() else 0L
        return buildString {
            append(dateIso.trim())
            append('|')
            append(substanceId.trim().lowercase(Locale.US))
            append('|')
            append(takenAtMillis)
            append('|')
            append(doseCents)
            append('|')
            append(dosageUnit.trim().lowercase(Locale.US))
        }
    }
}

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

package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.CircadiaDao
import com.example.data.local.SafeFileReader
import com.example.data.local.SleepSessionEntity
import com.example.data.local.SubstanceLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SubstanceRepository(private val dao: CircadiaDao) {

    private val _dateMutations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dateMutations: StateFlow<Map<String, Long>> = _dateMutations.asStateFlow()

    fun markDateMutated(dateIso: String, timestampMillis: Long = System.currentTimeMillis()) {
        _dateMutations.update { current ->
            current + (dateIso to maxOf(current[dateIso] ?: 0L, timestampMillis))
        }
    }

    fun observeLogsForDate(dateIso: String): Flow<List<SubstanceLogEntity>> =
        dao.observeLogsForDate(dateIso)

    fun observeAllLogs(): Flow<List<SubstanceLogEntity>> =
        dao.observeAllLogs()

    suspend fun addLog(log: SubstanceLogEntity): Long {
        val id = dao.insertLog(log)
        markDateMutated(log.dateIso, log.updatedAtMillis)
        return id
    }

    suspend fun updateLog(log: SubstanceLogEntity) {
        dao.updateLog(log)
        markDateMutated(log.dateIso, log.updatedAtMillis)
    }

    suspend fun deleteLog(id: Long, dateIso: String? = null) {
        dao.deleteLogById(id)
        dao.deleteReport("entry:$id")
        if (!dateIso.isNullOrBlank()) {
            markDateMutated(dateIso)
        }
    }

    suspend fun deleteEntryReport(entryId: Long) {
        dao.deleteReport("entry:$entryId")
    }

    suspend fun exportHistoryToUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val logs = dao.getAllLogsOnce()
        val sessions = dao.getAllSleepSessionsOnce()

        val logsJson = JSONArray().apply {
            logs.forEach { log ->
                put(JSONObject().apply {
                    put("dateIso", log.dateIso)
                    put("substanceId", log.substanceId)
                    put("substanceName", log.substanceName)
                    put("category", log.category)
                    put("dosageAmount", log.dosageAmount)
                    put("dosageUnit", log.dosageUnit)
                    put("takenAtMillis", log.takenAtMillis)
                    put("halfLifeHours", log.halfLifeHours)
                    put("disruptionWeight", log.disruptionWeight)
                    put("notes", log.notes)
                    put("updatedAtMillis", log.updatedAtMillis)
                })
            }
        }

        val sessionsJson = JSONArray().apply {
            sessions.forEach { s ->
                put(JSONObject().apply {
                    put("dateIso", s.dateIso)
                    put("source", s.source)
                    put("totalDurationMinutes", s.totalDurationMinutes)
                    put("remMinutes", s.remMinutes)
                    put("deepMinutes", s.deepMinutes)
                    put("lightMinutes", s.lightMinutes)
                    put("awakeMinutes", s.awakeMinutes)
                    if (s.restingHeartRateBpm != null) put("restingHeartRateBpm", s.restingHeartRateBpm)
                    put("bedtimeMillis", s.bedtimeMillis)
                    put("wakeTimeMillis", s.wakeTimeMillis)
                })
            }
        }

        val root = JSONObject().apply {
            put("app", "Circadia")
            put("version", 2)
            put("exportedAtMillis", System.currentTimeMillis())
            put("substanceLogs", logsJson)
            put("sleepSessions", sessionsJson)
        }

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Unable to open destination file for writing.")

        logs.size
    }

    suspend fun importHistoryFromUri(context: Context, uri: Uri): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            val content = SafeFileReader.readUtf8Bounded(context, uri)
            val root = runCatching { JSONObject(content) }
                .getOrElse { throw IllegalArgumentException("Selected file does not contain valid JSON.") }

            val logsArr = root.optJSONArray("substanceLogs") ?: JSONArray()
            val importedLogs = mutableListOf<SubstanceLogEntity>()
            val now = System.currentTimeMillis()
            val maxLogs = minOf(logsArr.length(), SafeFileReader.MAX_IMPORT_RECORDS)
            for (i in 0 until maxLogs) {
                val obj = logsArr.optJSONObject(i) ?: continue
                val dateIso = obj.optString("dateIso").trim()
                if (!DATE_ISO_REGEX.matches(dateIso)) continue

                val name = SafeFileReader.sanitizeText(obj.optString("substanceName"), maxLen = 80)
                if (name.isBlank()) continue

                val substanceId = SafeFileReader.sanitizeText(obj.optString("substanceId", "custom"), maxLen = 64)
                    .ifBlank { "custom" }
                val category = SafeFileReader.sanitizeText(obj.optString("category", "Custom"), maxLen = 32)
                    .ifBlank { "Custom" }
                val dosageUnit = SafeFileReader.sanitizeText(obj.optString("dosageUnit", "mg"), maxLen = 16)
                    .ifBlank { "mg" }
                val notes = SafeFileReader.sanitizeText(obj.optString("notes", ""), maxLen = 300)

                val dosageAmount = obj.optFiniteDouble("dosageAmount", 1.0, 0.01, 10_000.0)
                val halfLifeHours = obj.optFiniteDouble("halfLifeHours", 4.0, 0.25, 200.0)
                val disruptionWeight = obj.optFiniteDouble("disruptionWeight", 1.0, 0.05, 10.0)

                val takenAt = obj.optLong("takenAtMillis", now).let { if (it > 0L) it else now }
                val updatedAt = obj.optLong("updatedAtMillis", now).let { if (it > 0L) it else now }

                importedLogs.add(
                    SubstanceLogEntity(
                        dateIso = dateIso,
                        substanceId = substanceId,
                        substanceName = name,
                        category = category,
                        dosageAmount = dosageAmount,
                        dosageUnit = dosageUnit,
                        takenAtMillis = takenAt,
                        halfLifeHours = halfLifeHours,
                        disruptionWeight = disruptionWeight,
                        notes = notes,
                        updatedAtMillis = updatedAt
                    )
                )
            }
            if (importedLogs.isNotEmpty()) {
                dao.insertLogs(importedLogs)
            }

            val sessionsArr = root.optJSONArray("sleepSessions") ?: JSONArray()
            val importedSessions = mutableListOf<SleepSessionEntity>()
            val maxSessions = minOf(sessionsArr.length(), SafeFileReader.MAX_IMPORT_RECORDS)
            for (i in 0 until maxSessions) {
                val obj = sessionsArr.optJSONObject(i) ?: continue
                val dateIso = obj.optString("dateIso").trim()
                if (!DATE_ISO_REGEX.matches(dateIso)) continue

                val source = SafeFileReader.sanitizeText(obj.optString("source", "JSON Backup"), maxLen = 48)
                    .ifBlank { "JSON Backup" }
                val totalMin = obj.optInt("totalDurationMinutes", 0).coerceIn(1, 1440)
                val remMin = obj.optInt("remMinutes", 0).coerceIn(0, 1440)
                val deepMin = obj.optInt("deepMinutes", 0).coerceIn(0, 1440)
                val lightMin = obj.optInt("lightMinutes", 0).coerceIn(0, 1440)
                val awakeMin = obj.optInt("awakeMinutes", 0).coerceIn(0, 1440)
                val rhr = if (obj.has("restingHeartRateBpm") && !obj.isNull("restingHeartRateBpm")) {
                    obj.optInt("restingHeartRateBpm", -1).takeIf { it in 25..220 }
                } else null

                importedSessions.add(
                    SleepSessionEntity(
                        dateIso = dateIso,
                        source = source,
                        totalDurationMinutes = totalMin,
                        remMinutes = remMin,
                        deepMinutes = deepMin,
                        lightMinutes = lightMin,
                        awakeMinutes = awakeMin,
                        restingHeartRateBpm = rhr,
                        bedtimeMillis = obj.optLong("bedtimeMillis", 0L).coerceAtLeast(0L),
                        wakeTimeMillis = obj.optLong("wakeTimeMillis", 0L).coerceAtLeast(0L)
                    )
                )
            }
            if (importedSessions.isNotEmpty()) {
                dao.upsertSleepSessions(importedSessions)
            }

            importedLogs.size to importedSessions.size
        }

    private fun JSONObject.optFiniteDouble(key: String, fallback: Double, min: Double, max: Double): Double {
        val raw = optDouble(key, fallback)
        return if (raw.isFinite()) raw.coerceIn(min, max) else fallback
    }

    private companion object {
        val DATE_ISO_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

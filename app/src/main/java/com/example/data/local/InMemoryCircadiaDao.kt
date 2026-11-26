package com.example.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

data class SessionDataSnapshot(
    val logs: List<SubstanceLogEntity>,
    val sleepSessions: List<SleepSessionEntity>,
    val reports: List<AiImpactReportEntity>
)

/**
 * Process-scoped in-memory CircadiaDao used when the app is in Session-Only Mode
 * (persistentDbEnabled = false, the default). Never writes to disk.
 */
class InMemoryCircadiaDao : CircadiaDao {

    private val mutex = Mutex()
    private val nextId = AtomicLong(1L)

    private val logsState = MutableStateFlow<List<SubstanceLogEntity>>(emptyList())
    private val sleepSessionsState = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    private val reportsState = MutableStateFlow<List<AiImpactReportEntity>>(emptyList())

    override fun observeLogsForDate(dateIso: String): Flow<List<SubstanceLogEntity>> =
        logsState.map { list ->
            list.filter { it.dateIso == dateIso }.sortedBy { it.takenAtMillis }
        }

    override fun observeAllLogs(): Flow<List<SubstanceLogEntity>> =
        logsState.map { list -> list.sortedByDescending { it.takenAtMillis } }

    override suspend fun getAllLogsOnce(): List<SubstanceLogEntity> =
        logsState.value.sortedBy { it.takenAtMillis }

    override suspend fun getLogsForDateOnce(dateIso: String): List<SubstanceLogEntity> =
        logsState.value.filter { it.dateIso == dateIso }.sortedBy { it.takenAtMillis }

    override suspend fun insertLog(log: SubstanceLogEntity): Long = mutex.withLock {
        val assignedId = if (log.id > 0L) {
            nextId.updateAndGet { cur -> maxOf(cur, log.id + 1L) }
            log.id
        } else {
            nextId.getAndIncrement()
        }
        val stored = log.copy(
            id = assignedId,
            syncId = log.syncId.takeIf { it.isNotBlank() } ?: generateLogSyncId()
        )
        val updated = logsState.value.filterNot { it.id == assignedId } + stored
        logsState.value = updated
        assignedId
    }

    override suspend fun insertLogs(logs: List<SubstanceLogEntity>) {
        if (logs.isEmpty()) return
        mutex.withLock {
            val byId = logsState.value.associateBy { it.id }.toMutableMap()
            logs.forEach { raw ->
                val assignedId = if (raw.id > 0L) {
                    nextId.updateAndGet { cur -> maxOf(cur, raw.id + 1L) }
                    raw.id
                } else {
                    nextId.getAndIncrement()
                }
                byId[assignedId] = raw.copy(
                    id = assignedId,
                    syncId = raw.syncId.takeIf { it.isNotBlank() } ?: generateLogSyncId()
                )
            }
            logsState.value = byId.values.toList()
        }
    }

    override suspend fun updateLog(log: SubstanceLogEntity) {
        mutex.withLock {
            val current = logsState.value
            val idx = current.indexOfFirst { it.id == log.id }
            if (idx >= 0) {
                logsState.value = current.toMutableList().apply { set(idx, log) }
            }
        }
    }

    override suspend fun deleteLogById(id: Long) {
        mutex.withLock {
            logsState.value = logsState.value.filterNot { it.id == id }
        }
    }

    override fun observeAllSleepSessions(): Flow<List<SleepSessionEntity>> =
        sleepSessionsState.map { list -> list.sortedByDescending { it.dateIso } }

    override fun observeSleepSessionForDate(dateIso: String): Flow<SleepSessionEntity?> =
        sleepSessionsState.map { list -> list.firstOrNull { it.dateIso == dateIso } }

    override suspend fun getAllSleepSessionsOnce(): List<SleepSessionEntity> =
        sleepSessionsState.value.sortedByDescending { it.dateIso }

    override suspend fun upsertSleepSessions(sessions: List<SleepSessionEntity>) {
        if (sessions.isEmpty()) return
        mutex.withLock {
            val byDate = sleepSessionsState.value.associateBy { it.dateIso }.toMutableMap()
            sessions.forEach { byDate[it.dateIso] = it }
            sleepSessionsState.value = byDate.values.toList()
        }
    }

    override suspend fun deleteSleepSession(dateIso: String) {
        mutex.withLock {
            sleepSessionsState.value = sleepSessionsState.value.filterNot { it.dateIso == dateIso }
        }
    }

    override fun observeReport(scopeKey: String): Flow<AiImpactReportEntity?> =
        reportsState.map { list -> list.firstOrNull { it.scopeKey == scopeKey } }

    override fun observeReportsForDate(dateIso: String): Flow<List<AiImpactReportEntity>> =
        reportsState.map { list ->
            list.filter { it.dateIso == dateIso }.sortedByDescending { it.createdAtMillis }
        }

    override fun observeAllReports(): Flow<List<AiImpactReportEntity>> =
        reportsState.map { list -> list.sortedByDescending { it.createdAtMillis } }

    override suspend fun getAllReportsOnce(): List<AiImpactReportEntity> =
        reportsState.value.sortedByDescending { it.createdAtMillis }

    override suspend fun upsertReport(report: AiImpactReportEntity) {
        mutex.withLock {
            val byScope = reportsState.value.associateBy { it.scopeKey }.toMutableMap()
            byScope[report.scopeKey] = report
            reportsState.value = byScope.values.toList()
        }
    }

    override suspend fun upsertReports(reports: List<AiImpactReportEntity>) {
        if (reports.isEmpty()) return
        mutex.withLock {
            val byScope = reportsState.value.associateBy { it.scopeKey }.toMutableMap()
            reports.forEach { byScope[it.scopeKey] = it }
            reportsState.value = byScope.values.toList()
        }
    }

    override suspend fun deleteReport(scopeKey: String) {
        mutex.withLock {
            reportsState.value = reportsState.value.filterNot { it.scopeKey == scopeKey }
        }
    }

    override suspend fun clearAllSubstanceLogs() {
        mutex.withLock {
            logsState.value = emptyList()
        }
    }

    override suspend fun clearAllSleepSessions() {
        mutex.withLock {
            sleepSessionsState.value = emptyList()
        }
    }

    override suspend fun clearAllAiReports() {
        mutex.withLock {
            reportsState.value = emptyList()
        }
    }

    override suspend fun clearAllTablesData() {
        mutex.withLock {
            logsState.value = emptyList()
            sleepSessionsState.value = emptyList()
            reportsState.value = emptyList()
        }
    }

    suspend fun snapshotAll(): SessionDataSnapshot = mutex.withLock {
        SessionDataSnapshot(
            logs = logsState.value.sortedBy { it.takenAtMillis },
            sleepSessions = sleepSessionsState.value.sortedByDescending { it.dateIso },
            reports = reportsState.value.sortedByDescending { it.createdAtMillis }
        )
    }

    suspend fun replaceAll(
        logs: List<SubstanceLogEntity>,
        sleepSessions: List<SleepSessionEntity>,
        reports: List<AiImpactReportEntity>
    ) {
        mutex.withLock {
            val maxId = logs.maxOfOrNull { it.id } ?: 0L
            nextId.updateAndGet { cur -> maxOf(cur, maxId + 1L) }
            logsState.value = logs
            sleepSessionsState.value = sleepSessions
            reportsState.value = reports
        }
    }
}

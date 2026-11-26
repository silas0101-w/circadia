package com.example.data.local

import com.example.data.remote.CloudDatabaseSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dynamic CircadiaDao router that keeps all reads and writes purely in memory when
 * [UserSettings.persistentDbEnabled] is false (the default), and switches seamlessly
 * to the on-device Room SQLite DAO (with optional Firestore cloud mirror) when enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableCircadiaDao(
    private val inMemoryDao: InMemoryCircadiaDao,
    private val roomDao: CircadiaDao,
    private val prefsRepo: UserPreferencesRepository,
    private val cloudSyncService: CloudDatabaseSyncService
) : CircadiaDao {

    private val cloudMirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val persistentEnabledFlow: Flow<Boolean> = prefsRepo.settingsFlow
        .map { it.persistentDbEnabled }
        .distinctUntilChanged()

    private suspend fun activeDaoAndSettings(): Pair<CircadiaDao, UserSettings> {
        val settings = prefsRepo.getCurrentSettings()
        val dao = if (settings.persistentDbEnabled) roomDao else inMemoryDao
        return dao to settings
    }

    private fun shouldMirrorToCloud(settings: UserSettings): Boolean =
        settings.persistentDbEnabled &&
            settings.cloudSyncEnabled &&
            cloudSyncService.currentUser() != null

    override fun observeLogsForDate(dateIso: String): Flow<List<SubstanceLogEntity>> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeLogsForDate(dateIso)
            else inMemoryDao.observeLogsForDate(dateIso)
        }

    override fun observeAllLogs(): Flow<List<SubstanceLogEntity>> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeAllLogs()
            else inMemoryDao.observeAllLogs()
        }

    override suspend fun getAllLogsOnce(): List<SubstanceLogEntity> {
        val (dao, _) = activeDaoAndSettings()
        return dao.getAllLogsOnce()
    }

    override suspend fun getLogsForDateOnce(dateIso: String): List<SubstanceLogEntity> {
        val (dao, _) = activeDaoAndSettings()
        return dao.getLogsForDateOnce(dateIso)
    }

    override suspend fun insertLog(log: SubstanceLogEntity): Long {
        val (dao, settings) = activeDaoAndSettings()
        val ensuredLog = if (log.syncId.isBlank()) {
            log.copy(syncId = generateLogSyncId())
        } else {
            log
        }
        val insertedId = dao.insertLog(ensuredLog)
        val savedLog = ensuredLog.copy(id = insertedId)
        if (settings.persistentDbEnabled) {
            prefsRepo.removePendingDeletedLogs(setOf(savedLog.resolvedSyncId()))
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                cloudSyncService.pushLog(savedLog)
            }
        }
        return insertedId
    }

    override suspend fun insertLogs(logs: List<SubstanceLogEntity>) {
        if (logs.isEmpty()) return
        val (dao, settings) = activeDaoAndSettings()
        val ensuredLogs = logs.map { raw ->
            if (raw.syncId.isBlank()) raw.copy(syncId = generateLogSyncId()) else raw
        }
        dao.insertLogs(ensuredLogs)
        if (settings.persistentDbEnabled) {
            prefsRepo.removePendingDeletedLogs(ensuredLogs.map { it.resolvedSyncId() }.toSet())
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                cloudSyncService.syncBidirectional(roomDao)
            }
        }
    }

    override suspend fun updateLog(log: SubstanceLogEntity) {
        val (dao, settings) = activeDaoAndSettings()
        val ensuredLog = if (log.syncId.isBlank()) {
            val current = dao.getAllLogsOnce().firstOrNull { it.id == log.id }
            log.copy(syncId = current?.resolvedSyncId() ?: "log_${log.id}")
        } else {
            log
        }
        dao.updateLog(ensuredLog)
        if (settings.persistentDbEnabled) {
            prefsRepo.removePendingDeletedLogs(setOf(ensuredLog.resolvedSyncId()))
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                cloudSyncService.pushLog(ensuredLog)
            }
        }
    }

    override suspend fun deleteLogById(id: Long) {
        val (dao, settings) = activeDaoAndSettings()
        val existing = dao.getAllLogsOnce().firstOrNull { it.id == id }
        val docId = existing?.resolvedSyncId() ?: "log_$id"
        dao.deleteLogById(id)
        if (settings.persistentDbEnabled) {
            prefsRepo.addPendingDeletedLog(docId)
            if (shouldMirrorToCloud(settings)) {
                cloudMirrorScope.launch {
                    cloudSyncService.deleteLogBySyncId(docId)
                }
            }
        }
    }

    override fun observeAllSleepSessions(): Flow<List<SleepSessionEntity>> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeAllSleepSessions()
            else inMemoryDao.observeAllSleepSessions()
        }

    override fun observeSleepSessionForDate(dateIso: String): Flow<SleepSessionEntity?> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeSleepSessionForDate(dateIso)
            else inMemoryDao.observeSleepSessionForDate(dateIso)
        }

    override suspend fun getAllSleepSessionsOnce(): List<SleepSessionEntity> {
        val (dao, _) = activeDaoAndSettings()
        return dao.getAllSleepSessionsOnce()
    }

    override suspend fun upsertSleepSessions(sessions: List<SleepSessionEntity>) {
        if (sessions.isEmpty()) return
        val (dao, settings) = activeDaoAndSettings()
        dao.upsertSleepSessions(sessions)
        if (settings.persistentDbEnabled) {
            prefsRepo.removePendingDeletedSleep(sessions.map { "session_${it.dateIso}" }.toSet())
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                cloudSyncService.pushSleepSessions(sessions)
            }
        }
    }

    override suspend fun deleteSleepSession(dateIso: String) {
        val (dao, settings) = activeDaoAndSettings()
        dao.deleteSleepSession(dateIso)
        if (settings.persistentDbEnabled) {
            val docId = "session_$dateIso"
            prefsRepo.addPendingDeletedSleep(docId)
            if (shouldMirrorToCloud(settings)) {
                cloudMirrorScope.launch {
                    cloudSyncService.deleteSleepSession(dateIso)
                }
            }
        }
    }

    override fun observeReport(scopeKey: String): Flow<AiImpactReportEntity?> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeReport(scopeKey)
            else inMemoryDao.observeReport(scopeKey)
        }

    override fun observeReportsForDate(dateIso: String): Flow<List<AiImpactReportEntity>> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeReportsForDate(dateIso)
            else inMemoryDao.observeReportsForDate(dateIso)
        }

    override fun observeAllReports(): Flow<List<AiImpactReportEntity>> =
        persistentEnabledFlow.flatMapLatest { persistent ->
            if (persistent) roomDao.observeAllReports()
            else inMemoryDao.observeAllReports()
        }

    override suspend fun getAllReportsOnce(): List<AiImpactReportEntity> {
        val (dao, _) = activeDaoAndSettings()
        return dao.getAllReportsOnce()
    }

    override suspend fun upsertReport(report: AiImpactReportEntity) {
        val (dao, settings) = activeDaoAndSettings()
        dao.upsertReport(report)
        if (settings.persistentDbEnabled) {
            val docId = CloudDatabaseSyncService.sanitizeReportDocId(report.scopeKey)
            prefsRepo.removePendingDeletedReports(setOf(docId))
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                cloudSyncService.pushReport(report)
            }
        }
    }

    override suspend fun upsertReports(reports: List<AiImpactReportEntity>) {
        if (reports.isEmpty()) return
        val (dao, settings) = activeDaoAndSettings()
        dao.upsertReports(reports)
        if (settings.persistentDbEnabled) {
            val docIds = reports.map { CloudDatabaseSyncService.sanitizeReportDocId(it.scopeKey) }.toSet()
            prefsRepo.removePendingDeletedReports(docIds)
        }
        if (shouldMirrorToCloud(settings)) {
            cloudMirrorScope.launch {
                reports.forEach { report ->
                    cloudSyncService.pushReport(report)
                }
            }
        }
    }

    override suspend fun deleteReport(scopeKey: String) {
        val (dao, settings) = activeDaoAndSettings()
        dao.deleteReport(scopeKey)
        if (settings.persistentDbEnabled) {
            val docId = CloudDatabaseSyncService.sanitizeReportDocId(scopeKey)
            prefsRepo.addPendingDeletedReport(docId)
            if (shouldMirrorToCloud(settings)) {
                cloudMirrorScope.launch {
                    cloudSyncService.deleteReport(scopeKey)
                }
            }
        }
    }

    override suspend fun clearAllSubstanceLogs() {
        val (dao, _) = activeDaoAndSettings()
        dao.clearAllSubstanceLogs()
    }

    override suspend fun clearAllSleepSessions() {
        val (dao, _) = activeDaoAndSettings()
        dao.clearAllSleepSessions()
    }

    override suspend fun clearAllAiReports() {
        val (dao, _) = activeDaoAndSettings()
        dao.clearAllAiReports()
    }

    override suspend fun clearAllTablesData() {
        val (dao, _) = activeDaoAndSettings()
        dao.clearAllTablesData()
    }
}

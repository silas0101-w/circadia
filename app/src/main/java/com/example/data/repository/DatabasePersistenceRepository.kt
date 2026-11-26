package com.example.data.repository

import com.example.data.local.CircadiaDao
import com.example.data.local.InMemoryCircadiaDao
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.CloudDatabaseSyncService
import com.example.data.remote.CloudSyncSummary
import com.example.domain.SleepSessionMerger
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DatabaseStats(
    val logCount: Int = 0,
    val sleepSessionCount: Int = 0,
    val reportCount: Int = 0
)

data class MigrationResult(
    val migratedLogs: Int,
    val migratedSleepSessions: Int,
    val migratedReports: Int
)

data class DisablePersistenceResult(
    val clearedSavedData: Boolean,
    val cloudClearError: String? = null
)

/**
 * Coordinates switching between Session-Only in-memory storage and Persistent SQLite (+ optional
 * Cloud Firestore sync), including deduplicated session-to-disk migration and Keep vs Clear teardown.
 */
class DatabasePersistenceRepository(
    private val inMemoryDao: InMemoryCircadiaDao,
    private val roomDao: CircadiaDao,
    private val prefsRepo: UserPreferencesRepository,
    private val cloudSyncService: CloudDatabaseSyncService
) {
    private val transitionMutex = Mutex()

    fun observeFirebaseUser(): Flow<FirebaseUser?> = cloudSyncService.authStateFlow()

    fun currentFirebaseUser(): FirebaseUser? = cloudSyncService.currentUser()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveDatabaseStats(): Flow<DatabaseStats> =
        prefsRepo.settingsFlow
            .map { it.persistentDbEnabled }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                val dao = if (enabled) roomDao else inMemoryDao
                combine(
                    dao.observeAllLogs(),
                    dao.observeAllSleepSessions(),
                    dao.observeAllReports()
                ) { logs, sessions, reports ->
                    DatabaseStats(
                        logCount = logs.size,
                        sleepSessionCount = sessions.size,
                        reportCount = reports.size
                    )
                }
            }

    fun observeDiskDatabaseStats(): Flow<DatabaseStats> =
        combine(
            roomDao.observeAllLogs(),
            roomDao.observeAllSleepSessions(),
            roomDao.observeAllReports()
        ) { logs, sessions, reports ->
            DatabaseStats(
                logCount = logs.size,
                sleepSessionCount = sessions.size,
                reportCount = reports.size
            )
        }

    suspend fun enablePersistentDatabase(): MigrationResult = transitionMutex.withLock {
        val snapshot = inMemoryDao.snapshotAll()
        val existingLogs = roomDao.getAllLogsOnce()
        val existingBySyncId = existingLogs.associateBy { it.resolvedSyncId() }.toMutableMap()
        val existingByFingerprint = existingLogs.associateBy { it.contentFingerprint() }.toMutableMap()

        val idMap = mutableMapOf<Long, Long>()
        var migratedLogCount = 0

        snapshot.logs.forEach { sessionLog ->
            val syncKey = sessionLog.resolvedSyncId()
            val match = existingBySyncId[syncKey] ?: existingByFingerprint[sessionLog.contentFingerprint()]
            if (match != null) {
                if (sessionLog.id > 0L) {
                    idMap[sessionLog.id] = match.id
                }
                if (sessionLog.updatedAtMillis > match.updatedAtMillis) {
                    val updated = sessionLog.copy(id = match.id, syncId = match.resolvedSyncId())
                    roomDao.updateLog(updated)
                    existingBySyncId[updated.resolvedSyncId()] = updated
                    existingByFingerprint[updated.contentFingerprint()] = updated
                    migratedLogCount++
                }
            } else {
                val toInsert = sessionLog.copy(id = 0L, syncId = syncKey)
                val newId = roomDao.insertLog(toInsert)
                val saved = toInsert.copy(id = newId)
                existingBySyncId[saved.resolvedSyncId()] = saved
                existingByFingerprint[saved.contentFingerprint()] = saved
                if (sessionLog.id > 0L) {
                    idMap[sessionLog.id] = newId
                }
                migratedLogCount++
            }
        }

        var migratedSleepCount = 0
        if (snapshot.sleepSessions.isNotEmpty()) {
            val existingSleep = roomDao.getAllSleepSessionsOnce()
            val existingSleepByDate = existingSleep.associateBy { it.dateIso }
            val mergedSleep = SleepSessionMerger.mergeWithExisting(existingSleep, snapshot.sleepSessions)
            val changedSleep = mergedSleep.filter { merged ->
                val cur = existingSleepByDate[merged.dateIso]
                cur == null || cur != merged
            }
            if (changedSleep.isNotEmpty()) {
                roomDao.upsertSleepSessions(changedSleep)
                migratedSleepCount = changedSleep.size
            }
        }

        val existingReportsByScope = roomDao.getAllReportsOnce().associateBy { it.scopeKey }
        val remappedReports = snapshot.reports.map { report ->
            if (report.scopeKey.startsWith("entry:")) {
                val oldId = report.scopeKey.removePrefix("entry:").toLongOrNull()
                val mappedId = oldId?.let { idMap[it] }
                if (mappedId != null) {
                    report.copy(scopeKey = "entry:$mappedId")
                } else {
                    report
                }
            } else {
                report
            }
        }
        val reportsToUpsert = remappedReports.filter { candidate ->
            val cur = existingReportsByScope[candidate.scopeKey]
            cur == null || candidate.createdAtMillis > cur.createdAtMillis
        }
        if (reportsToUpsert.isNotEmpty()) {
            roomDao.upsertReports(reportsToUpsert)
        }

        inMemoryDao.clearAllTablesData()
        prefsRepo.setPersistentDbEnabled(true)

        MigrationResult(
            migratedLogs = migratedLogCount,
            migratedSleepSessions = migratedSleepCount,
            migratedReports = reportsToUpsert.size
        )
    }

    suspend fun disablePersistentDatabase(
        clearSavedData: Boolean,
        clearCloudData: Boolean = false
    ): DisablePersistenceResult = transitionMutex.withLock {
        if (!clearSavedData) {
            val diskLogs = roomDao.getAllLogsOnce()
            val diskSessions = roomDao.getAllSleepSessionsOnce()
            val diskReports = roomDao.getAllReportsOnce()
            inMemoryDao.replaceAll(
                logs = diskLogs,
                sleepSessions = diskSessions,
                reports = diskReports
            )
            prefsRepo.setPersistentDbEnabled(false)
            DisablePersistenceResult(clearedSavedData = false)
        } else {
            var cloudErr: String? = null
            if (clearCloudData && cloudSyncService.currentUser() != null) {
                val cloudRes = cloudSyncService.clearAllCloudData()
                if (cloudRes.isFailure) {
                    cloudErr = cloudRes.exceptionOrNull()?.localizedMessage
                        ?: "Cloud records could not be deleted."
                }
            }
            roomDao.clearAllTablesData()
            inMemoryDao.clearAllTablesData()
            prefsRepo.clearAllPendingCloudDeletions()
            prefsRepo.setPersistentDbEnabled(false)
            prefsRepo.updateLastCloudDbSync(0L)
            DisablePersistenceResult(
                clearedSavedData = true,
                cloudClearError = cloudErr
            )
        }
    }

    suspend fun clearSavedDatabaseOnly(clearCloudData: Boolean): Result<Unit> = transitionMutex.withLock {
        var cloudFailure: Throwable? = null
        if (clearCloudData && cloudSyncService.currentUser() != null) {
            val res = cloudSyncService.clearAllCloudData()
            if (res.isFailure) {
                cloudFailure = res.exceptionOrNull()
            }
        }
        roomDao.clearAllTablesData()
        inMemoryDao.clearAllTablesData()
        prefsRepo.clearAllPendingCloudDeletions()
        prefsRepo.updateLastCloudDbSync(0L)
        if (cloudFailure != null) {
            Result.failure(cloudFailure)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun syncCloudNow(): Result<CloudSyncSummary> {
        val settings = prefsRepo.getCurrentSettings()
        if (!settings.persistentDbEnabled) {
            return Result.failure(
                IllegalStateException("Enable Persistent Database before syncing to Cloud.")
            )
        }
        val user = cloudSyncService.currentUser()
            ?: return Result.failure(
                IllegalStateException("Sign in with Google to enable Cloud Database Sync.")
            )
        val result = cloudSyncService.syncBidirectional(roomDao)
        result.onSuccess { summary ->
            prefsRepo.updateLastCloudDbSync(summary.syncedAtMillis)
            prefsRepo.updateFirebaseUserEmail(user.email.orEmpty())
        }
        return result
    }

    suspend fun onCloudSyncUserSignedIn(email: String) {
        prefsRepo.updateFirebaseUserEmail(email)
    }

    suspend fun onCloudSyncUserSignedOut() {
        prefsRepo.setCloudSyncEnabled(false)
        prefsRepo.updateFirebaseUserEmail("")
    }
}

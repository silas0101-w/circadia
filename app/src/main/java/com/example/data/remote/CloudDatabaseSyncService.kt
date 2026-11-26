package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AiImpactReportEntity
import com.example.data.local.CircadiaDao
import com.example.data.local.PendingCloudDeletions
import com.example.data.local.SafeFileReader
import com.example.data.local.SleepSessionEntity
import com.example.data.local.SubstanceLogEntity
import com.example.data.local.UserPreferencesRepository
import com.example.domain.SleepSessionMerger
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

data class CloudSyncSummary(
    val pushedLogs: Int,
    val pulledLogs: Int,
    val pushedSleepSessions: Int,
    val pulledSleepSessions: Int,
    val syncedReports: Int,
    val syncedAtMillis: Long
)

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

/**
 * Optional Cloud Firestore sync service scoped strictly to the signed-in Google user's UID
 * under /users/{uid}/(substance_logs|sleep_sessions|ai_reports).
 * Always initialized with the named database from R.string.firestore_database_id.
 */
class CloudDatabaseSyncService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val prefsRepo: UserPreferencesRepository? = null
) {
    constructor(
        context: Context,
        prefsRepo: UserPreferencesRepository? = null
    ) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        prefsRepo = prefsRepo
    )

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in with Google to enable Cloud Database Sync.")
    }

    suspend fun pushLog(log: SubstanceLogEntity): Result<Unit> {
        val docId = sanitizeDocId(log.resolvedSyncId(), "log_${log.id}")
        var path = "users/unauthenticated/substance_logs/$docId"
        return try {
            val uid = requireUserId()
            if (log.id <= 0L && log.syncId.isBlank()) return Result.success(Unit)
            val docRef = db.collection("users").document(uid)
                .collection("substance_logs").document(docId)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(log.toCreateMap(uid)).await()
            } else {
                docRef.update(log.toUpdateMap()).await()
            }
            prefsRepo?.removePendingDeletedLogs(setOf(docId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLogBySyncId(syncId: String): Result<Unit> {
        val docId = sanitizeDocId(syncId, "log_unknown")
        var path = "users/unauthenticated/substance_logs/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("substance_logs").document(docId)
            path = docRef.path
            docRef.delete().await()
            prefsRepo?.removePendingDeletedLogs(setOf(docId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefsRepo?.addPendingDeletedLog(docId)
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLog(localId: Long): Result<Unit> = deleteLogBySyncId("log_$localId")

    suspend fun pushSleepSessions(sessions: List<SleepSessionEntity>): Result<Unit> {
        var path = "users/unauthenticated/sleep_sessions"
        return try {
            val uid = requireUserId()
            val pushedDocIds = mutableSetOf<String>()
            for (session in sessions) {
                if (!DATE_ISO_REGEX.matches(session.dateIso)) continue
                val docId = "session_${session.dateIso}"
                val docRef = db.collection("users").document(uid)
                    .collection("sleep_sessions").document(docId)
                path = docRef.path
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(session.toCreateMap(uid)).await()
                } else {
                    docRef.update(session.toUpdateMap()).await()
                }
                pushedDocIds.add(docId)
            }
            prefsRepo?.removePendingDeletedSleep(pushedDocIds)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSleepSession(dateIso: String): Result<Unit> {
        if (!DATE_ISO_REGEX.matches(dateIso)) return Result.success(Unit)
        val docId = "session_$dateIso"
        var path = "users/unauthenticated/sleep_sessions/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("sleep_sessions").document(docId)
            path = docRef.path
            docRef.delete().await()
            prefsRepo?.removePendingDeletedSleep(setOf(docId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefsRepo?.addPendingDeletedSleep(docId)
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun pushReport(report: AiImpactReportEntity): Result<Unit> {
        val docId = sanitizeReportDocId(report.scopeKey)
        var path = "users/unauthenticated/ai_reports/$docId"
        return try {
            val uid = requireUserId()
            if (!DATE_ISO_REGEX.matches(report.dateIso)) return Result.success(Unit)
            val docRef = db.collection("users").document(uid)
                .collection("ai_reports").document(docId)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(report.toCreateMap(uid)).await()
            } else {
                docRef.update(report.toUpdateMap()).await()
            }
            prefsRepo?.removePendingDeletedReports(setOf(docId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReport(scopeKey: String): Result<Unit> {
        val docId = sanitizeReportDocId(scopeKey)
        var path = "users/unauthenticated/ai_reports/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("ai_reports").document(docId)
            path = docRef.path
            docRef.delete().await()
            prefsRepo?.removePendingDeletedReports(setOf(docId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefsRepo?.addPendingDeletedReport(docId)
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Bidirectional sync between local Room database and authenticated user's Firestore collections.
     * Replays queued offline deletions first and deduplicates logs across devices by syncId and intake fingerprint.
     */
    suspend fun syncBidirectional(roomDao: CircadiaDao): Result<CloudSyncSummary> {
        var currentPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            val logsCol = userDoc.collection("substance_logs")
            val sleepCol = userDoc.collection("sleep_sessions")
            val reportsCol = userDoc.collection("ai_reports")

            // 0. Replay pending offline deletions before reading remote snapshots
            val pending = prefsRepo?.getPendingCloudDeletions() ?: PendingCloudDeletions()
            val flushedLogDeletes = mutableSetOf<String>()
            for (docId in pending.logDocIds) {
                val safeId = sanitizeDocId(docId, "")
                if (safeId.isNotEmpty()) {
                    val ref = logsCol.document(safeId)
                    currentPath = ref.path
                    ref.delete().await()
                }
                flushedLogDeletes.add(docId)
            }
            if (flushedLogDeletes.isNotEmpty()) {
                prefsRepo?.removePendingDeletedLogs(flushedLogDeletes)
            }

            val flushedSleepDeletes = mutableSetOf<String>()
            for (docId in pending.sleepDocIds) {
                val safeId = sanitizeDocId(docId, "")
                if (safeId.isNotEmpty()) {
                    val ref = sleepCol.document(safeId)
                    currentPath = ref.path
                    ref.delete().await()
                }
                flushedSleepDeletes.add(docId)
            }
            if (flushedSleepDeletes.isNotEmpty()) {
                prefsRepo?.removePendingDeletedSleep(flushedSleepDeletes)
            }

            val flushedReportDeletes = mutableSetOf<String>()
            for (docId in pending.reportDocIds) {
                val safeId = sanitizeDocId(docId, "")
                if (safeId.isNotEmpty()) {
                    val ref = reportsCol.document(safeId)
                    currentPath = ref.path
                    ref.delete().await()
                }
                flushedReportDeletes.add(docId)
            }
            if (flushedReportDeletes.isNotEmpty()) {
                prefsRepo?.removePendingDeletedReports(flushedReportDeletes)
            }

            // 1. Pull remote collections
            currentPath = logsCol.path
            val remoteLogsSnap = logsCol.get().await()
            val rawRemoteLogs = remoteLogsSnap.documents.mapNotNull { doc ->
                if (doc.id in pending.logDocIds) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                val dateIso = (data["dateIso"] as? String)?.takeIf { DATE_ISO_REGEX.matches(it) }
                    ?: return@mapNotNull null
                val name = SafeFileReader.sanitizeText(data["substanceName"] as? String ?: "", 80)
                if (name.isBlank()) return@mapNotNull null
                SubstanceLogEntity(
                    id = 0L,
                    dateIso = dateIso,
                    substanceId = SafeFileReader.sanitizeText(data["substanceId"] as? String ?: "custom", 64)
                        .ifBlank { "custom" },
                    substanceName = name,
                    category = SafeFileReader.sanitizeText(data["category"] as? String ?: "Custom", 32)
                        .ifBlank { "Custom" },
                    dosageAmount = ((data["dosageAmount"] as? Number)?.toDouble() ?: 1.0)
                        .takeIf { it.isFinite() }?.coerceIn(0.01, 10_000.0) ?: 1.0,
                    dosageUnit = SafeFileReader.sanitizeText(data["dosageUnit"] as? String ?: "mg", 16)
                        .ifBlank { "mg" },
                    takenAtMillis = ((data["takenAtMillis"] as? Number)?.toLong() ?: 1L).coerceAtLeast(1L),
                    halfLifeHours = ((data["halfLifeHours"] as? Number)?.toDouble() ?: 4.0)
                        .takeIf { it.isFinite() }?.coerceIn(0.25, 200.0) ?: 4.0,
                    disruptionWeight = ((data["disruptionWeight"] as? Number)?.toDouble() ?: 1.0)
                        .takeIf { it.isFinite() }?.coerceIn(0.05, 10.0) ?: 1.0,
                    notes = SafeFileReader.sanitizeText(data["notes"] as? String ?: "", 300),
                    updatedAtMillis = ((data["updatedAtMillis"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
                    syncId = sanitizeDocId(doc.id, "")
                )
            }

            // Deduplicate remote logs across devices by contentFingerprint, preferring newer updatedAtMillis
            val remoteLogs = rawRemoteLogs
                .groupBy { it.contentFingerprint() }
                .map { (_, dupes) -> dupes.maxByOrNull { it.updatedAtMillis } ?: dupes.first() }

            currentPath = sleepCol.path
            val remoteSleepSnap = sleepCol.get().await()
            val remoteSleep = remoteSleepSnap.documents.mapNotNull { doc ->
                if (doc.id in pending.sleepDocIds) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                val dateIso = (data["dateIso"] as? String)?.takeIf { DATE_ISO_REGEX.matches(it) }
                    ?: return@mapNotNull null
                val totalMin = ((data["totalDurationMinutes"] as? Number)?.toInt() ?: 1).coerceIn(1, 1440)
                val rhr = (data["restingHeartRateBpm"] as? Number)?.toInt()?.takeIf { it in 25..220 }
                SleepSessionEntity(
                    dateIso = dateIso,
                    source = SafeFileReader.sanitizeText(data["source"] as? String ?: "Cloud Sync", 48)
                        .ifBlank { "Cloud Sync" },
                    totalDurationMinutes = totalMin,
                    remMinutes = ((data["remMinutes"] as? Number)?.toInt() ?: 0).coerceIn(0, 1440),
                    deepMinutes = ((data["deepMinutes"] as? Number)?.toInt() ?: 0).coerceIn(0, 1440),
                    lightMinutes = ((data["lightMinutes"] as? Number)?.toInt() ?: 0).coerceIn(0, 1440),
                    awakeMinutes = ((data["awakeMinutes"] as? Number)?.toInt() ?: 0).coerceIn(0, 1440),
                    restingHeartRateBpm = rhr,
                    bedtimeMillis = ((data["bedtimeMillis"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
                    wakeTimeMillis = ((data["wakeTimeMillis"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
                    syncedAtMillis = ((data["syncedAtMillis"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
                )
            }

            currentPath = reportsCol.path
            val remoteReportsSnap = reportsCol.get().await()
            val remoteReports = remoteReportsSnap.documents.mapNotNull { doc ->
                if (doc.id in pending.reportDocIds) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                val scopeKey = SafeFileReader.sanitizeText(data["scopeKey"] as? String ?: "", 64)
                val dateIso = (data["dateIso"] as? String)?.takeIf { DATE_ISO_REGEX.matches(it) }
                if (scopeKey.isBlank() || dateIso == null) return@mapNotNull null
                AiImpactReportEntity(
                    scopeKey = scopeKey,
                    dateIso = dateIso,
                    providerUsed = SafeFileReader.sanitizeText(data["providerUsed"] as? String ?: "AI", 64)
                        .ifBlank { "AI" },
                    modelUsed = SafeFileReader.sanitizeText(data["modelUsed"] as? String ?: "model", 128)
                        .ifBlank { "model" },
                    severityRating = SafeFileReader.sanitizeText(data["severityRating"] as? String ?: "Moderate", 32)
                        .ifBlank { "Moderate" },
                    biologicalMechanism = (data["biologicalMechanism"] as? String ?: "").take(4000),
                    mitigationTipsJson = (data["mitigationTipsJson"] as? String ?: "[]").take(4000),
                    createdAtMillis = ((data["createdAtMillis"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
                )
            }

            // 2. Merge remote -> local Room (matched by syncId or contentFingerprint, never overwriting unrelated local PKs)
            val initialLocalLogs = roomDao.getAllLogsOnce()
            val localBySyncId = initialLocalLogs.associateBy { it.resolvedSyncId() }.toMutableMap()
            val localByFingerprint = initialLocalLogs.associateBy { it.contentFingerprint() }.toMutableMap()
            var pulledLogsCount = 0

            for (remote in remoteLogs) {
                val remoteKey = remote.resolvedSyncId()
                val matchedLocal = localBySyncId[remoteKey] ?: localByFingerprint[remote.contentFingerprint()]
                if (matchedLocal == null) {
                    val toInsert = remote.copy(id = 0L, syncId = remoteKey)
                    val newLocalId = roomDao.insertLog(toInsert)
                    val saved = toInsert.copy(id = newLocalId)
                    localBySyncId[remoteKey] = saved
                    localByFingerprint[saved.contentFingerprint()] = saved
                    pulledLogsCount++
                } else if (remote.updatedAtMillis > matchedLocal.updatedAtMillis) {
                    val updated = remote.copy(id = matchedLocal.id, syncId = remoteKey)
                    roomDao.updateLog(updated)
                    localBySyncId[remoteKey] = updated
                    localByFingerprint[updated.contentFingerprint()] = updated
                    pulledLogsCount++
                } else if (matchedLocal.syncId != remoteKey) {
                    val aligned = matchedLocal.copy(syncId = remoteKey)
                    roomDao.updateLog(aligned)
                    localBySyncId[remoteKey] = aligned
                }
            }

            val localSleepList = roomDao.getAllSleepSessionsOnce()
            val localSleepByDate = localSleepList.associateBy { it.dateIso }
            val candidatesToPull = remoteSleep.filter { remote ->
                val local = localSleepByDate[remote.dateIso]
                local == null || remote.syncedAtMillis > local.syncedAtMillis
            }
            val sleepToPull = SleepSessionMerger.mergeWithExisting(localSleepList, candidatesToPull)
            if (sleepToPull.isNotEmpty()) {
                roomDao.upsertSleepSessions(sleepToPull)
            }

            val localReports = roomDao.getAllReportsOnce().associateBy { it.scopeKey }
            val reportsToPull = remoteReports.filter { remote ->
                val local = localReports[remote.scopeKey]
                local == null || remote.createdAtMillis > local.createdAtMillis
            }
            if (reportsToPull.isNotEmpty()) {
                roomDao.upsertReports(reportsToPull)
            }

            // 3. Push newer/missing local -> remote Firestore
            val remoteLogsBySyncId = rawRemoteLogs.associateBy { it.resolvedSyncId() }
            val logsToPush = roomDao.getAllLogsOnce().filter { local ->
                val docId = sanitizeDocId(local.resolvedSyncId(), "log_${local.id}")
                val remote = remoteLogsBySyncId[docId]
                remote == null || local.updatedAtMillis > remote.updatedAtMillis
            }
            logsToPush.forEach { log ->
                val docId = sanitizeDocId(log.resolvedSyncId(), "log_${log.id}")
                val docRef = logsCol.document(docId)
                currentPath = docRef.path
                if (remoteLogsBySyncId.containsKey(docId)) {
                    docRef.update(log.toUpdateMap()).await()
                } else {
                    docRef.set(log.toCreateMap(uid)).await()
                }
            }

            val remoteSleepByDate = remoteSleep.associateBy { it.dateIso }
            val sleepToPush = roomDao.getAllSleepSessionsOnce().filter { local ->
                val remote = remoteSleepByDate[local.dateIso]
                remote == null || local.syncedAtMillis > remote.syncedAtMillis
            }
            sleepToPush.forEach { session ->
                val docRef = sleepCol.document("session_${session.dateIso}")
                currentPath = docRef.path
                if (remoteSleepByDate.containsKey(session.dateIso)) {
                    docRef.update(session.toUpdateMap()).await()
                } else {
                    docRef.set(session.toCreateMap(uid)).await()
                }
            }

            val remoteReportsByScope = remoteReports.associateBy { it.scopeKey }
            val reportsToPush = roomDao.getAllReportsOnce().filter { local ->
                val remote = remoteReportsByScope[local.scopeKey]
                remote == null || local.createdAtMillis > remote.createdAtMillis
            }
            reportsToPush.forEach { report ->
                val docRef = reportsCol.document(sanitizeReportDocId(report.scopeKey))
                currentPath = docRef.path
                if (remoteReportsByScope.containsKey(report.scopeKey)) {
                    docRef.update(report.toUpdateMap()).await()
                } else {
                    docRef.set(report.toCreateMap(uid)).await()
                }
            }

            val now = System.currentTimeMillis()
            Result.success(
                CloudSyncSummary(
                    pushedLogs = logsToPush.size,
                    pulledLogs = pulledLogsCount,
                    pushedSleepSessions = sleepToPush.size,
                    pulledSleepSessions = sleepToPull.size,
                    syncedReports = reportsToPush.size + reportsToPull.size,
                    syncedAtMillis = now
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Cloud DB bidirectional sync failed at $currentPath", e)
            }
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllCloudData(): Result<Unit> {
        var currentPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            listOf("substance_logs", "sleep_sessions", "ai_reports").forEach { sub ->
                val col = userDoc.collection(sub)
                currentPath = col.path
                val snap = col.get().await()
                snap.documents.forEach { doc ->
                    currentPath = doc.reference.path
                    doc.reference.delete().await()
                }
            }
            prefsRepo?.clearAllPendingCloudDeletions()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, currentPath)
            Result.failure(e)
        }
    }

    private fun SubstanceLogEntity.toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "localId" to id.coerceAtLeast(0L),
        "dateIso" to dateIso,
        "substanceId" to SafeFileReader.sanitizeText(substanceId, 64).ifBlank { "custom" },
        "substanceName" to SafeFileReader.sanitizeText(substanceName, 80).ifBlank { "Substance" },
        "category" to SafeFileReader.sanitizeText(category, 32).ifBlank { "Custom" },
        "dosageAmount" to (dosageAmount.takeIf { it.isFinite() }?.coerceIn(0.01, 10_000.0) ?: 1.0),
        "dosageUnit" to SafeFileReader.sanitizeText(dosageUnit, 16).ifBlank { "mg" },
        "takenAtMillis" to takenAtMillis.coerceAtLeast(1L),
        "halfLifeHours" to (halfLifeHours.takeIf { it.isFinite() }?.coerceIn(0.25, 200.0) ?: 4.0),
        "disruptionWeight" to (disruptionWeight.takeIf { it.isFinite() }?.coerceIn(0.05, 10.0) ?: 1.0),
        "notes" to SafeFileReader.sanitizeText(notes, 300),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun SubstanceLogEntity.toUpdateMap(): Map<String, Any> = mapOf(
        "localId" to id.coerceAtLeast(0L),
        "dateIso" to dateIso,
        "substanceId" to SafeFileReader.sanitizeText(substanceId, 64).ifBlank { "custom" },
        "substanceName" to SafeFileReader.sanitizeText(substanceName, 80).ifBlank { "Substance" },
        "category" to SafeFileReader.sanitizeText(category, 32).ifBlank { "Custom" },
        "dosageAmount" to (dosageAmount.takeIf { it.isFinite() }?.coerceIn(0.01, 10_000.0) ?: 1.0),
        "dosageUnit" to SafeFileReader.sanitizeText(dosageUnit, 16).ifBlank { "mg" },
        "takenAtMillis" to takenAtMillis.coerceAtLeast(1L),
        "halfLifeHours" to (halfLifeHours.takeIf { it.isFinite() }?.coerceIn(0.25, 200.0) ?: 4.0),
        "disruptionWeight" to (disruptionWeight.takeIf { it.isFinite() }?.coerceIn(0.05, 10.0) ?: 1.0),
        "notes" to SafeFileReader.sanitizeText(notes, 300),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun SleepSessionEntity.toCreateMap(uid: String): Map<String, Any> = mapOf<String, Any?>(
        "userId" to uid,
        "dateIso" to dateIso,
        "source" to SafeFileReader.sanitizeText(source, 48).ifBlank { "Imported" },
        "totalDurationMinutes" to totalDurationMinutes.coerceIn(1, 1440),
        "remMinutes" to remMinutes.coerceIn(0, 1440),
        "deepMinutes" to deepMinutes.coerceIn(0, 1440),
        "lightMinutes" to lightMinutes.coerceIn(0, 1440),
        "awakeMinutes" to awakeMinutes.coerceIn(0, 1440),
        "restingHeartRateBpm" to restingHeartRateBpm?.takeIf { it in 25..220 },
        "bedtimeMillis" to bedtimeMillis.coerceAtLeast(0L),
        "wakeTimeMillis" to wakeTimeMillis.coerceAtLeast(0L),
        "syncedAtMillis" to syncedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.let {
        @Suppress("UNCHECKED_CAST")
        it as Map<String, Any>
    }

    private fun SleepSessionEntity.toUpdateMap(): Map<String, Any> = mapOf<String, Any>(
        "dateIso" to dateIso,
        "source" to SafeFileReader.sanitizeText(source, 48).ifBlank { "Imported" },
        "totalDurationMinutes" to totalDurationMinutes.coerceIn(1, 1440),
        "remMinutes" to remMinutes.coerceIn(0, 1440),
        "deepMinutes" to deepMinutes.coerceIn(0, 1440),
        "lightMinutes" to lightMinutes.coerceIn(0, 1440),
        "awakeMinutes" to awakeMinutes.coerceIn(0, 1440),
        "restingHeartRateBpm" to (restingHeartRateBpm?.takeIf { it in 25..220 } ?: FieldValue.delete()),
        "bedtimeMillis" to bedtimeMillis.coerceAtLeast(0L),
        "wakeTimeMillis" to wakeTimeMillis.coerceAtLeast(0L),
        "syncedAtMillis" to syncedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun AiImpactReportEntity.toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "scopeKey" to SafeFileReader.sanitizeText(scopeKey, 64).ifBlank { "day_$dateIso" },
        "dateIso" to dateIso,
        "providerUsed" to SafeFileReader.sanitizeText(providerUsed, 64).ifBlank { "AI" },
        "modelUsed" to SafeFileReader.sanitizeText(modelUsed, 128).ifBlank { "model" },
        "severityRating" to SafeFileReader.sanitizeText(severityRating, 32).ifBlank { "Moderate" },
        "biologicalMechanism" to biologicalMechanism.take(4000),
        "mitigationTipsJson" to mitigationTipsJson.take(4000),
        "createdAtMillis" to createdAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun AiImpactReportEntity.toUpdateMap(): Map<String, Any> = mapOf(
        "scopeKey" to SafeFileReader.sanitizeText(scopeKey, 64).ifBlank { "day_$dateIso" },
        "dateIso" to dateIso,
        "providerUsed" to SafeFileReader.sanitizeText(providerUsed, 64).ifBlank { "AI" },
        "modelUsed" to SafeFileReader.sanitizeText(modelUsed, 128).ifBlank { "model" },
        "severityRating" to SafeFileReader.sanitizeText(severityRating, 32).ifBlank { "Moderate" },
        "biologicalMechanism" to biologicalMechanism.take(4000),
        "mitigationTipsJson" to mitigationTipsJson.take(4000),
        "createdAtMillis" to createdAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        private const val TAG = "CloudDbSyncService"
        private val DATE_ISO_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun sanitizeDocId(raw: String, fallback: String): String {
            val cleaned = raw.trim()
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .take(128)
            return cleaned.ifBlank { fallback }
        }

        fun sanitizeReportDocId(scopeKey: String): String {
            val cleaned = scopeKey.replace(":", "_")
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .take(128)
            return cleaned.ifBlank { "report_unknown" }
        }
    }
}

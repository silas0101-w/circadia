package com.example.domain

import com.example.data.local.SleepSessionEntity
import kotlin.math.max
import kotlin.math.min

/**
 * Merges and deduplicates sleep sessions sharing the same wake date (`dateIso`):
 * - Identical or substantially overlapping `[bedtimeMillis, wakeTimeMillis]` windows are deduplicated
 *   (keeping the fresher/richer record so repeated Google Fit syncs or CSV re-imports stay idempotent).
 * - Distinct non-overlapping sessions on the same day (e.g., an afternoon nap + nighttime sleep)
 *   preserve the longest sleep window (`bedtimeMillis..wakeTimeMillis`) for circadian anchoring
 *   while combining stage totals (`totalDurationMinutes`, `remMinutes`, `deepMinutes`, `lightMinutes`, `awakeMinutes`).
 */
object SleepSessionMerger {

    fun mergeBatch(sessions: List<SleepSessionEntity>): List<SleepSessionEntity> {
        if (sessions.isEmpty()) return emptyList()
        return sessions
            .groupBy { it.dateIso }
            .map { (_, daySessions) -> collapseSameDayBatch(daySessions) }
            .sortedByDescending { it.dateIso }
    }

    fun mergeWithExisting(
        existing: List<SleepSessionEntity>,
        incoming: List<SleepSessionEntity>
    ): List<SleepSessionEntity> {
        if (incoming.isEmpty()) return emptyList()
        val collapsedIncoming = mergeBatch(incoming)
        val existingByDate = existing.associateBy { it.dateIso }

        return collapsedIncoming.map { inc ->
            val cur = existingByDate[inc.dateIso]
            if (cur == null) {
                inc
            } else {
                mergeExistingAndIncomingDay(cur, inc)
            }
        }
    }

    private fun collapseSameDayBatch(daySessions: List<SleepSessionEntity>): SleepSessionEntity {
        if (daySessions.size == 1) return clampSession(daySessions.first())

        val distinctClusters = mutableListOf<SleepSessionEntity>()
        for (candidate in daySessions) {
            val matchIdx = distinctClusters.indexOfFirst { existing ->
                windowsOverlapSubstantially(existing, candidate)
            }
            if (matchIdx >= 0) {
                distinctClusters[matchIdx] = pickFresherOrRicher(distinctClusters[matchIdx], candidate)
            } else {
                distinctClusters.add(candidate)
            }
        }

        if (distinctClusters.size == 1) {
            return clampSession(distinctClusters.first())
        }

        return combineDistinctSessions(distinctClusters)
    }

    private fun mergeExistingAndIncomingDay(
        existing: SleepSessionEntity,
        incoming: SleepSessionEntity
    ): SleepSessionEntity {
        val existingHasWindow = existing.bedtimeMillis > 0L && existing.wakeTimeMillis > existing.bedtimeMillis
        val incomingHasWindow = incoming.bedtimeMillis > 0L && incoming.wakeTimeMillis > incoming.bedtimeMillis

        if (existingHasWindow && incomingHasWindow && !windowsOverlapSubstantially(existing, incoming)) {
            val existingSpanMin = ((existing.wakeTimeMillis - existing.bedtimeMillis) / 60_000L).toInt()
            val alreadyAbsorbedExtra = existing.totalDurationMinutes > existingSpanMin + 10
            if (!alreadyAbsorbedExtra) {
                return combineDistinctSessions(listOf(existing, incoming))
            }
            if (existing.totalDurationMinutes >= incoming.totalDurationMinutes) {
                return clampSession(
                    existing.copy(
                        restingHeartRateBpm = existing.restingHeartRateBpm ?: incoming.restingHeartRateBpm,
                        syncedAtMillis = max(existing.syncedAtMillis, incoming.syncedAtMillis)
                    )
                )
            }
        }

        val mergedRhr = incoming.restingHeartRateBpm ?: existing.restingHeartRateBpm
        return clampSession(
            incoming.copy(
                restingHeartRateBpm = mergedRhr,
                syncedAtMillis = max(existing.syncedAtMillis, incoming.syncedAtMillis)
            )
        )
    }

    private fun combineDistinctSessions(distinct: List<SleepSessionEntity>): SleepSessionEntity {
        val anchor = distinct.maxByOrNull { windowDurationMinutes(it) } ?: distinct.first()
        val rhr = anchor.restingHeartRateBpm
            ?: distinct.firstNotNullOfOrNull { it.restingHeartRateBpm?.takeIf { bpm -> bpm in 25..220 } }

        val totalSum = distinct.sumOf { it.totalDurationMinutes }.coerceIn(1, 1440)
        val remSum = distinct.sumOf { it.remMinutes }.coerceIn(0, 1440)
        val deepSum = distinct.sumOf { it.deepMinutes }.coerceIn(0, 1440)
        val lightSum = distinct.sumOf { it.lightMinutes }.coerceIn(0, 1440)
        val awakeSum = distinct.sumOf { it.awakeMinutes }.coerceIn(0, 1440)
        val latestSync = distinct.maxOf { it.syncedAtMillis }

        return SleepSessionEntity(
            dateIso = anchor.dateIso,
            source = anchor.source,
            totalDurationMinutes = totalSum,
            remMinutes = remSum,
            deepMinutes = deepSum,
            lightMinutes = lightSum,
            awakeMinutes = awakeSum,
            restingHeartRateBpm = rhr?.takeIf { it in 25..220 },
            bedtimeMillis = anchor.bedtimeMillis.coerceAtLeast(0L),
            wakeTimeMillis = anchor.wakeTimeMillis.coerceAtLeast(0L),
            syncedAtMillis = latestSync.coerceAtLeast(0L)
        )
    }

    private fun windowsOverlapSubstantially(a: SleepSessionEntity, b: SleepSessionEntity): Boolean {
        val aValid = a.bedtimeMillis > 0L && a.wakeTimeMillis > a.bedtimeMillis
        val bValid = b.bedtimeMillis > 0L && b.wakeTimeMillis > b.bedtimeMillis
        if (!aValid || !bValid) {
            return true
        }
        val overlapStart = max(a.bedtimeMillis, b.bedtimeMillis)
        val overlapEnd = min(a.wakeTimeMillis, b.wakeTimeMillis)
        val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0L)
        val shorterMs = min(a.wakeTimeMillis - a.bedtimeMillis, b.wakeTimeMillis - b.bedtimeMillis)
            .coerceAtLeast(1L)
        return overlapMs.toDouble() / shorterMs.toDouble() >= 0.5
    }

    private fun pickFresherOrRicher(a: SleepSessionEntity, b: SleepSessionEntity): SleepSessionEntity {
        val preferred = if (b.syncedAtMillis >= a.syncedAtMillis) b else a
        val fallback = if (preferred === b) a else b
        return clampSession(
            preferred.copy(
                restingHeartRateBpm = preferred.restingHeartRateBpm ?: fallback.restingHeartRateBpm,
                syncedAtMillis = max(a.syncedAtMillis, b.syncedAtMillis)
            )
        )
    }

    private fun windowDurationMinutes(s: SleepSessionEntity): Long {
        return if (s.bedtimeMillis > 0L && s.wakeTimeMillis > s.bedtimeMillis) {
            (s.wakeTimeMillis - s.bedtimeMillis) / 60_000L
        } else {
            s.totalDurationMinutes.toLong()
        }
    }

    private fun clampSession(s: SleepSessionEntity): SleepSessionEntity {
        return s.copy(
            totalDurationMinutes = s.totalDurationMinutes.coerceIn(1, 1440),
            remMinutes = s.remMinutes.coerceIn(0, 1440),
            deepMinutes = s.deepMinutes.coerceIn(0, 1440),
            lightMinutes = s.lightMinutes.coerceIn(0, 1440),
            awakeMinutes = s.awakeMinutes.coerceIn(0, 1440),
            restingHeartRateBpm = s.restingHeartRateBpm?.takeIf { it in 25..220 },
            bedtimeMillis = s.bedtimeMillis.coerceAtLeast(0L),
            wakeTimeMillis = s.wakeTimeMillis.coerceAtLeast(0L),
            syncedAtMillis = s.syncedAtMillis.coerceAtLeast(0L)
        )
    }
}

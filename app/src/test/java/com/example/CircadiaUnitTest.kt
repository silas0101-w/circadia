package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AiImpactReportEntity
import com.example.data.local.CircadiaDatabase
import com.example.data.local.InMemoryCircadiaDao
import com.example.data.local.SafeFileReader
import com.example.data.local.SleepSessionEntity
import com.example.data.local.SubstanceLogEntity
import com.example.data.local.SwitchableCircadiaDao
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.remote.AI_STUDIO_BILLING_URL
import com.example.data.remote.AiFailureKind
import com.example.data.remote.CloudDatabaseSyncService
import com.example.data.remote.GeminiImpactService
import com.example.data.remote.GoogleFitRestService
import com.example.data.remote.HalfLifeSource
import com.example.data.remote.MedicationLookupService
import com.example.data.remote.NvidiaNimService
import com.example.data.remote.OpenFdaPharmacokinetics
import com.example.data.remote.RemoteMedicationItem
import com.example.data.repository.DatabasePersistenceRepository
import com.example.data.repository.MedicationCatalogRepository
import com.example.data.repository.SubstanceRepository
import com.example.domain.CannabinoidEntry
import com.example.domain.CannabinoidType
import com.example.domain.CannabisIntakeMethod
import com.example.domain.CannabisQuantityMode
import com.example.domain.DateTimeUtils
import com.example.domain.DecayModelMode
import com.example.domain.DoseOption
import com.example.domain.InhaledIntakeCalculator
import com.example.domain.NicotineDeliveryMethod
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SeverityTier
import com.example.domain.SleepSessionMerger
import com.example.domain.SmokedTobaccoType
import com.example.domain.StructuredSessionMetadata
import com.example.domain.SubstanceCatalog
import com.example.domain.SubstanceCategory
import com.example.domain.VapeStrengthUnit
import com.example.ui.components.formatCompactAmount
import com.example.ui.components.isAllowedGoogleAuthUrl
import com.example.ui.components.parseDecimalInput
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CircadiaUnitTest {

    @Test
    fun halfLifeDecay_afterOneHalfLife_isFiftyPercent() {
        val takenAt = 1_700_000_000_000L
        val onsetMinutes = 30
        val halfLifeHours = 5.0
        val targetMillis = takenAt + (30 * 60_000L) + (5 * 3_600_000L)

        val remaining = PharmacokineticsEngine.activeAmountAt(
            dosageAmount = 100.0,
            takenAtMillis = takenAt,
            halfLifeHours = halfLifeHours,
            peakOnsetMinutes = onsetMinutes,
            targetMillis = targetMillis
        )
        assertEquals(50.0, remaining, 0.5)
    }

    @Test
    fun analyzeDay_lateCaffeineScoresHigherThanEarlyCaffeine() {
        val dateIso = "2026-11-17"
        val bedtimeMin = 22 * 60 + 30
        val morningCoffee = SubstanceLogEntity(
            id = 1,
            dateIso = dateIso,
            substanceId = "caffeine_coffee",
            substanceName = "Caffeine (Coffee)",
            category = "Stimulant",
            dosageAmount = 150.0,
            dosageUnit = "mg",
            takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 8, 0),
            halfLifeHours = 5.0,
            disruptionWeight = 1.0
        )
        val eveningCoffee = morningCoffee.copy(
            id = 2,
            takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 20, 0)
        )

        val morningSummary = PharmacokineticsEngine.analyzeDay(
            dateIso = dateIso,
            entries = listOf(morningCoffee),
            targetBedtimeMinutes = bedtimeMin
        )
        val eveningSummary = PharmacokineticsEngine.analyzeDay(
            dateIso = dateIso,
            entries = listOf(eveningCoffee),
            targetBedtimeMinutes = bedtimeMin
        )

        assertTrue(eveningSummary.disruptionScore > morningSummary.disruptionScore)
        assertTrue(eveningSummary.severityTier != SeverityTier.LOW)
    }

    @Test
    fun nvidiaJsonParser_handlesMarkdownFenceAndValidJson() {
        val raw = """
            ```json
            {
              "severityRating": "Severe",
              "biologicalMechanism": "Adenosine receptor blockade persists past bedtime.",
              "mitigationTips": ["Hydrate with electrolytes", "Dim overhead lights"]
            }
            ```
        """.trimIndent()

        val parsed = NvidiaNimService.parseStructuredImpactJson(
            rawContent = raw,
            providerUsed = "NVIDIA NIM",
            modelUsed = "meta/llama-3.3-70b-instruct"
        )
        assertEquals("Severe", parsed.severityRating)
        assertEquals(2, parsed.mitigationTips.size)
        assertTrue(parsed.biologicalMechanism.contains("Adenosine"))
    }

    @Test
    fun substanceCatalog_loadsBundledReferenceJsonResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SubstanceCatalog.initializeFromResources(context)

        val items = SubstanceCatalog.items
        assertTrue("Expected >= 15 bundled substances", items.size >= 15)
        assertTrue(items.any { it.id == "caffeine_coffee" && it.halfLifeHours == 5.0 })
        assertTrue(items.any { it.id == "alcohol" })
        assertTrue(items.any { it.id == "melatonin" })
        assertTrue(items.any { it.id == "adderall" })
    }

    @Test
    fun previewEditImpact_sameDayReduction_producesNegativeScoreDelta() {
        val dateIso = "2026-11-17"
        val bedtimeMin = 22 * 60 + 30
        val original = SubstanceLogEntity(
            id = 10L,
            dateIso = dateIso,
            substanceId = "caffeine_coffee",
            substanceName = "Caffeine (Coffee)",
            category = "Stimulant",
            dosageAmount = 200.0,
            dosageUnit = "mg",
            takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 18, 0),
            halfLifeHours = 5.0,
            disruptionWeight = 1.0
        )
        val reducedCandidate = original.copy(
            dosageAmount = 50.0,
            takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 8, 0)
        )

        val preview = PharmacokineticsEngine.previewEditImpact(
            originalDateIso = dateIso,
            originalDayEntries = listOf(original),
            targetDayEntries = listOf(original),
            editingEntry = original,
            candidateEntry = reducedCandidate,
            targetBedtimeMinutes = bedtimeMin
        )

        assertTrue(preview.scoreDelta < 0)
        assertTrue(preview.afterDayScore < preview.beforeDayScore)
        assertTrue(!preview.isMovedToDifferentDate)
    }

    @Test
    fun previewEditImpact_crossDateMove_updatesTargetDayScoreAndFlagsMove() {
        val oldDate = "2026-11-16"
        val newDate = "2026-11-17"
        val bedtimeMin = 22 * 60 + 30
        val original = SubstanceLogEntity(
            id = 20L,
            dateIso = oldDate,
            substanceId = "caffeine_coffee",
            substanceName = "Caffeine (Coffee)",
            category = "Stimulant",
            dosageAmount = 150.0,
            dosageUnit = "mg",
            takenAtMillis = DateTimeUtils.combineDateAndTime(oldDate, 17, 0),
            halfLifeHours = 5.0,
            disruptionWeight = 1.0
        )
        val movedCandidate = original.copy(
            dateIso = newDate,
            takenAtMillis = DateTimeUtils.combineDateAndTime(newDate, 17, 0)
        )

        val preview = PharmacokineticsEngine.previewEditImpact(
            originalDateIso = oldDate,
            originalDayEntries = listOf(original),
            targetDayEntries = emptyList(),
            editingEntry = original,
            candidateEntry = movedCandidate,
            targetBedtimeMinutes = bedtimeMin
        )

        assertTrue(preview.isMovedToDifferentDate)
        assertEquals(0, preview.beforeDayScore)
        assertTrue(preview.afterDayScore > 0)
        assertEquals(0, preview.movedTargetScoreWithoutMove)
    }

    @Test
    fun computeSmartDoseStep_adaptsToUnitAndMagnitude() {
        assertEquals(25.0, PharmacokineticsEngine.computeSmartDoseStep("mg", 100.0), 0.001)
        assertEquals(5.0, PharmacokineticsEngine.computeSmartDoseStep("mg", 20.0), 0.001)
        assertEquals(1.0, PharmacokineticsEngine.computeSmartDoseStep("mg", 5.0), 0.001)
        assertEquals(0.5, PharmacokineticsEngine.computeSmartDoseStep("drinks", 2.0), 0.001)
    }

    @Test
    fun datePickerUtcRoundTrip_preservesExactIsoDate() {
        val sampleDates = listOf("2026-01-01", "2026-06-15", "2026-11-17", "2026-12-31")
        sampleDates.forEach { dateIso ->
            val utcMillis = DateTimeUtils.dateIsoToUtcMidnightMillis(dateIso)
            val roundTripped = DateTimeUtils.utcMidnightMillisToDateIso(utcMillis)
            assertEquals(dateIso, roundTripped)
        }
    }

    @Test
    fun nvidiaEndpointValidator_rejectsHttpPrivateAndUntrustedHosts() {
        val valid = NvidiaNimService.validateEndpointUrl(
            "https://integrate.api.nvidia.com/v1/chat/completions"
        )
        assertEquals("https", valid.scheme)
        assertEquals("integrate.api.nvidia.com", valid.host)

        val rejected = listOf(
            "http://integrate.api.nvidia.com/v1/chat/completions",
            "https://127.0.0.1/v1/chat/completions",
            "https://169.254.169.254/latest/meta-data",
            "https://evil.example.com/v1/chat/completions",
            "ftp://integrate.api.nvidia.com/file"
        )
        rejected.forEach { badUrl ->
            try {
                NvidiaNimService.validateEndpointUrl(badUrl)
                fail("Expected IllegalArgumentException for $badUrl")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun nvidiaJsonParser_rejectsNonJsonMalformedResponse() {
        try {
            NvidiaNimService.parseStructuredImpactJson(
                rawContent = "Sorry, I cannot comply with that request.",
                providerUsed = "NVIDIA NIM",
                modelUsed = "meta/llama-3.3-70b-instruct"
            )
            fail("Expected IllegalStateException for malformed non-JSON LLM output")
        } catch (_: IllegalStateException) {
            // expected fail-closed behavior
        }
    }

    @Test
    fun pharmacokineticsEngine_handlesNaNAndInfiniteInputsWithoutCrashing() {
        val dateIso = "2026-11-17"
        val corruptedEntry = SubstanceLogEntity(
            id = 99L,
            dateIso = dateIso,
            substanceId = "custom",
            substanceName = "Corrupted Import",
            category = "Custom",
            dosageAmount = Double.NaN,
            dosageUnit = "mg",
            takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 14, 0),
            halfLifeHours = Double.POSITIVE_INFINITY,
            disruptionWeight = Double.NaN
        )

        val summary = PharmacokineticsEngine.analyzeDay(
            dateIso = dateIso,
            entries = listOf(corruptedEntry),
            targetBedtimeMinutes = 22 * 60 + 30
        )

        assertEquals(0, summary.disruptionScore)
        assertEquals(0.0, summary.entryInfos.first().remainingAtBedtimeAmount, 0.001)
        assertTrue(summary.decayCurve.all { it.normalizedLoad.isFinite() })
    }

    @Test
    fun safeFileReader_sanitizeTextStripsControlCharsAndCapsLength() {
        val raw = "Coffee\u0000\n\r\tIgnore previous\u0007instructions" + "x".repeat(100)
        val clean = SafeFileReader.sanitizeText(raw, maxLen = 30)
        assertTrue(!clean.contains('\u0000'))
        assertTrue(!clean.contains('\n'))
        assertTrue(clean.length <= 30)
    }

    @Test
    fun googleSignInAllowlist_permitsGoogleDomainsAndBlocksUntrustedSchemesAndHosts() {
        assertTrue(isAllowedGoogleAuthUrl(Uri.parse("https://accounts.google.com/ServiceLogin")))
        assertTrue(isAllowedGoogleAuthUrl(Uri.parse("https://myaccount.google.com/")))
        assertTrue(isAllowedGoogleAuthUrl(Uri.parse("https://ssl.gstatic.com/script.js")))

        assertFalse(isAllowedGoogleAuthUrl(Uri.parse("http://accounts.google.com/ServiceLogin")))
        assertFalse(isAllowedGoogleAuthUrl(Uri.parse("file:///android_asset/index.html")))
        assertFalse(isAllowedGoogleAuthUrl(Uri.parse("javascript:alert(1)")))
        assertFalse(isAllowedGoogleAuthUrl(Uri.parse("https://accounts.google.com.evil.example/login")))
        assertFalse(isAllowedGoogleAuthUrl(Uri.parse("https://evil.example.com/")))
    }

    @Test
    fun googleFitRestService_buildsVerifiedHttpsGoogleLoginUri() {
        val uri = GoogleFitRestService().buildGoogleLoginPageUri()
        assertEquals("https", uri.scheme)
        assertEquals("accounts.google.com", uri.host)
        assertEquals("/ServiceLogin", uri.path)
    }

    @Test
    fun userSettings_persistenceAndCloudSyncAreOffByDefault() = runTest {
        val defaults = UserSettings()
        assertFalse(defaults.persistentDbEnabled)
        assertFalse(defaults.cloudSyncEnabled)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = UserPreferencesRepository(context)
        val initial = prefsRepo.getCurrentSettings()
        assertFalse(initial.persistentDbEnabled)
        assertFalse(initial.cloudSyncEnabled)
    }

    @Test
    fun persistenceLifecycle_defaultsToMemoryMigratesToRoomAndSupportsKeepOrClear() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val db = Room.inMemoryDatabaseBuilder(context, CircadiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val roomDao = db.circadiaDao()
            val memoryDao = InMemoryCircadiaDao()
            val prefsRepo = UserPreferencesRepository(context)
            prefsRepo.setPersistentDbEnabled(false)

            val dbId = context.getString(R.string.firestore_database_id)
            val cloudService = CloudDatabaseSyncService(
                db = FirebaseFirestore.getInstance(dbId),
                auth = FirebaseAuth.getInstance()
            )
            val switchableDao = SwitchableCircadiaDao(memoryDao, roomDao, prefsRepo, cloudService)
            val persistenceRepo = DatabasePersistenceRepository(memoryDao, roomDao, prefsRepo, cloudService)

            // 1. Default OFF: writes stay in memory only, Room stays empty
            val dateIso = "2026-11-17"
            val sessionLogId = switchableDao.insertLog(
                SubstanceLogEntity(
                    dateIso = dateIso,
                    substanceId = "caffeine_coffee",
                    substanceName = "Caffeine (Coffee)",
                    category = "Stimulant",
                    dosageAmount = 120.0,
                    dosageUnit = "mg",
                    takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 9, 0),
                    halfLifeHours = 5.0,
                    disruptionWeight = 1.0
                )
            )
            switchableDao.upsertSleepSessions(
                listOf(
                    SleepSessionEntity(
                        dateIso = dateIso,
                        source = "CSV Import",
                        totalDurationMinutes = 430,
                        remMinutes = 90,
                        deepMinutes = 80,
                        lightMinutes = 240,
                        awakeMinutes = 20,
                        restingHeartRateBpm = 58,
                        bedtimeMillis = 1000L,
                        wakeTimeMillis = 2000L
                    )
                )
            )
            switchableDao.upsertReport(
                AiImpactReportEntity(
                    scopeKey = "entry:$sessionLogId",
                    dateIso = dateIso,
                    providerUsed = "Gemini",
                    modelUsed = "gemini-3.8-flash",
                    severityRating = "Moderate",
                    biologicalMechanism = "Caffeine half-life overlap.",
                    mitigationTipsJson = "[\"Hydrate\"]"
                )
            )

            assertEquals(1, memoryDao.getAllLogsOnce().size)
            assertEquals(0, roomDao.getAllLogsOnce().size)
            assertEquals(1, switchableDao.observeLogsForDate(dateIso).first().size)

            // 2. Enable persistence: migrates session records into Room & remaps entry report scopeKey
            val migration = persistenceRepo.enablePersistentDatabase()
            assertEquals(1, migration.migratedLogs)
            assertEquals(1, migration.migratedSleepSessions)
            assertEquals(1, migration.migratedReports)
            assertTrue(prefsRepo.getCurrentSettings().persistentDbEnabled)
            assertEquals(0, memoryDao.getAllLogsOnce().size)

            val roomLogs = roomDao.getAllLogsOnce()
            assertEquals(1, roomLogs.size)
            val newRoomId = roomLogs.first().id
            val roomReports = roomDao.getAllReportsOnce()
            assertEquals(1, roomReports.size)
            assertEquals("entry:$newRoomId", roomReports.first().scopeKey)
            assertEquals(1, roomDao.getAllSleepSessionsOnce().size)

            // 3. Disable persistence with KEEP: restores records into memory while keeping Room intact
            val keepResult = persistenceRepo.disablePersistentDatabase(
                clearSavedData = false,
                clearCloudData = false
            )
            assertFalse(keepResult.clearedSavedData)
            assertFalse(prefsRepo.getCurrentSettings().persistentDbEnabled)
            assertEquals(1, memoryDao.getAllLogsOnce().size)
            assertEquals(1, roomDao.getAllLogsOnce().size)

            // 4. Re-enable and then disable with CLEAR: wipes Room & memory
            persistenceRepo.enablePersistentDatabase()
            val clearResult = persistenceRepo.disablePersistentDatabase(
                clearSavedData = true,
                clearCloudData = false
            )
            assertTrue(clearResult.clearedSavedData)
            assertFalse(prefsRepo.getCurrentSettings().persistentDbEnabled)
            assertEquals(0, roomDao.getAllLogsOnce().size)
            assertEquals(0, roomDao.getAllSleepSessionsOnce().size)
            assertEquals(0, roomDao.getAllReportsOnce().size)
            assertEquals(0, memoryDao.getAllLogsOnce().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun calculateRemainingConcentration_pureHalfLife_matchesExponentialDecay() {
        val takenAt = 1_700_000_000_000L
        val halfLife = 4.0
        val hourMs = 3_600_000L

        val atIntake = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 200.0,
            takenAtMillis = takenAt,
            halfLifeHours = halfLife,
            targetMillis = takenAt,
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertEquals(200.0, atIntake.remainingAmount, 0.01)
        assertEquals(100.0, atIntake.percentRemaining, 0.01)

        val atOneHalfLife = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 200.0,
            takenAtMillis = takenAt,
            halfLifeHours = halfLife,
            targetMillis = takenAt + (4 * hourMs),
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertEquals(100.0, atOneHalfLife.remainingAmount, 0.01)
        assertEquals(50.0, atOneHalfLife.percentRemaining, 0.01)

        val atTwoHalfLives = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 200.0,
            takenAtMillis = takenAt,
            halfLifeHours = halfLife,
            targetMillis = takenAt + (8 * hourMs),
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertEquals(50.0, atTwoHalfLives.remainingAmount, 0.01)
        assertEquals(25.0, atTwoHalfLives.percentRemaining, 0.01)

        val atThreeHalfLives = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 200.0,
            takenAtMillis = takenAt,
            halfLifeHours = halfLife,
            targetMillis = takenAt + (12 * hourMs),
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertEquals(25.0, atThreeHalfLives.remainingAmount, 0.01)
        assertEquals(12.5, atThreeHalfLives.percentRemaining, 0.01)
    }

    @Test
    fun calculateRemainingConcentration_onsetRamp_rampsThenDecaysAndHandlesEdgeCases() {
        val takenAt = 1_700_000_000_000L
        val minMs = 60_000L

        val beforeIntake = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 100.0,
            takenAtMillis = takenAt,
            halfLifeHours = 5.0,
            targetMillis = takenAt - 10 * minMs,
            peakOnsetMinutes = 40,
            model = DecayModelMode.ONSET_RAMP
        )
        assertEquals(0.0, beforeIntake.remainingAmount, 0.001)
        assertEquals(0.0, beforeIntake.percentRemaining, 0.001)

        val midOnset = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 100.0,
            takenAtMillis = takenAt,
            halfLifeHours = 5.0,
            targetMillis = takenAt + 20 * minMs,
            peakOnsetMinutes = 40,
            model = DecayModelMode.ONSET_RAMP
        )
        assertEquals(50.0, midOnset.remainingAmount, 0.01)
        assertEquals(50.0, midOnset.percentRemaining, 0.01)

        val atPeak = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = 100.0,
            takenAtMillis = takenAt,
            halfLifeHours = 5.0,
            targetMillis = takenAt + 40 * minMs,
            peakOnsetMinutes = 40,
            model = DecayModelMode.ONSET_RAMP
        )
        assertEquals(100.0, atPeak.remainingAmount, 0.01)
        assertEquals(100.0, atPeak.percentRemaining, 0.01)

        val invalidDose = PharmacokineticsEngine.calculateRemainingConcentration(
            dosageAmount = Double.NaN,
            takenAtMillis = takenAt,
            halfLifeHours = 5.0,
            targetMillis = takenAt + 60 * minMs,
            model = DecayModelMode.ONSET_RAMP
        )
        assertEquals(0.0, invalidDose.remainingAmount, 0.001)
        assertEquals(0.0, invalidDose.percentRemaining, 0.001)
    }

    @Test
    fun generateEntryConcentrationCurve_and_buildDayModel_handleMultiSeriesAndUnits() {
        val dateIso = "2026-11-17"
        val bedtimeMin = 22 * 60 + 30
        val bedtimeMillis = DateTimeUtils.bedtimeMillisForDate(dateIso, bedtimeMin)
        val morningMillis = DateTimeUtils.combineDateAndTime(dateIso, 8, 30)
        val eveningMillis = DateTimeUtils.combineDateAndTime(dateIso, 19, 0)

        val coffee = SubstanceLogEntity(
            id = 101L,
            dateIso = dateIso,
            substanceId = "caffeine_coffee",
            substanceName = "Coffee",
            category = "Stimulant",
            dosageAmount = 120.0,
            dosageUnit = "mg",
            takenAtMillis = morningMillis,
            halfLifeHours = 5.0,
            disruptionWeight = 1.0
        )
        val wine = SubstanceLogEntity(
            id = 102L,
            dateIso = dateIso,
            substanceId = "alcohol",
            substanceName = "Alcohol",
            category = "Depressant",
            dosageAmount = 2.0,
            dosageUnit = "drinks",
            takenAtMillis = eveningMillis,
            halfLifeHours = 1.5,
            disruptionWeight = 1.0
        )

        val singleSeries = PharmacokineticsEngine.generateEntryConcentrationCurve(
            entry = coffee,
            bedtimeMillis = bedtimeMillis,
            nowMillis = morningMillis + 5 * 3_600_000L,
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertTrue(singleSeries.points.any { it.timestampMillis == bedtimeMillis })
        assertNotNull(singleSeries.nowSnapshot)
        assertEquals(60.0, singleSeries.nowSnapshot!!.remainingAmount, 0.2)

        val sharedUnitModel = PharmacokineticsEngine.buildDayConcentrationChartModel(
            dateIso = dateIso,
            entries = listOf(coffee),
            targetBedtimeMinutes = bedtimeMin,
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertEquals("mg", sharedUnitModel.sharedUnitLabel)
        assertEquals(120.0, sharedUnitModel.combinedMaxSingleUnit!!, 0.01)

        val mixedUnitModel = PharmacokineticsEngine.buildDayConcentrationChartModel(
            dateIso = dateIso,
            entries = listOf(coffee, wine),
            targetBedtimeMinutes = bedtimeMin,
            model = DecayModelMode.PURE_HALF_LIFE
        )
        assertNull(mixedUnitModel.sharedUnitLabel)
        assertNull(mixedUnitModel.combinedMaxSingleUnit)
        val wineSeries = mixedUnitModel.series.first { it.entry.id == 102L }
        val beforeWine = wineSeries.points.first { it.timestampMillis < eveningMillis }
        val atWine = wineSeries.points.first { it.timestampMillis == eveningMillis }
        assertEquals(0.0, beforeWine.remainingAmount, 0.001)
        assertEquals(2.0, atWine.remainingAmount, 0.01)
    }

    @Test
    fun geminiFlexibleParser_handlesRawFencedAndProseWrappedJson() {
        val raw = """{"severityRating":"Moderate","biologicalMechanism":"Adenosine antagonism delays sleep onset.","mitigationTips":["Dim lights","Hydrate"]}"""
        val parsedRaw = GeminiImpactService.parseFlexibleImpactJson(raw, "gemini-3.8-flash")
        assertEquals("Moderate", parsedRaw.severityRating)
        assertEquals(2, parsedRaw.mitigationTips.size)
        assertEquals("gemini-3.8-flash", parsedRaw.modelUsed)

        val fenced = """
            Here is your analysis:
            ```json
            {
              "severityRating": "Critical",
              "biologicalMechanism": "Late stimulant overlap compresses REM.",
              "mitigationTips": ["Avoid extra caffeine", "Cool bedroom"]
            }
            ```
        """.trimIndent()
        val parsedFenced = GeminiImpactService.parseFlexibleImpactJson(fenced, "gemini-3.5-flash-lite")
        assertEquals("Critical", parsedFenced.severityRating)
        assertTrue(parsedFenced.biologicalMechanism.contains("REM"))
        assertEquals(2, parsedFenced.mitigationTips.size)

        val proseWrapped = """Sure! {"severityRating":"Low","biologicalMechanism":"Cleared before bed.","mitigationTips":["Keep routine"]} Hope this helps."""
        val parsedProse = GeminiImpactService.parseFlexibleImpactJson(proseWrapped, "gemini-3.8-flash")
        assertEquals("Low", parsedProse.severityRating)
        assertEquals("Cleared before bed.", parsedProse.biologicalMechanism)
    }

    @Test
    fun geminiClassifyFailureReason_identifiesAppCheckQuotaNetworkAndTimeout() {
        val (appCheckMsg, appCheckDetails) = GeminiImpactService.classifyFailureReason(
            error = IllegalStateException("HTTP 403 App Check token invalid"),
            tierDiagnostics = listOf("Tier 1 failed -> HTTP 403"),
            appCheckStatus = "Debug App Check active"
        )
        assertTrue(appCheckMsg.contains("403"))
        assertTrue(appCheckDetails.contains("Debug App Check active"))
        assertTrue(appCheckDetails.contains("Tier 1 failed"))

        val (quotaMsg, _) = GeminiImpactService.classifyFailureReason(
            error = IllegalStateException("429 RESOURCE_EXHAUSTED quota exceeded"),
            tierDiagnostics = emptyList(),
            appCheckStatus = "ok"
        )
        assertTrue(quotaMsg.lowercase().contains("quota") || quotaMsg.lowercase().contains("rate limit"))

        val (networkMsg, _) = GeminiImpactService.classifyFailureReason(
            error = java.net.UnknownHostException("Unable to resolve host firebasevertexai.googleapis.com"),
            tierDiagnostics = emptyList(),
            appCheckStatus = "ok"
        )
        assertTrue(networkMsg.lowercase().contains("internet") || networkMsg.lowercase().contains("servers"))

        val (timeoutMsg, _) = GeminiImpactService.classifyFailureReason(
            error = IllegalStateException("Request timed out after 60s"),
            tierDiagnostics = listOf("Tier 1 timed out after 60s"),
            appCheckStatus = "ok"
        )
        assertTrue(timeoutMsg.lowercase().contains("timed out"))
    }

    @Test
    fun geminiFailFast_and_classifyFailure_handlePrepaymentBillingDepleted() {
        val depletedError = IllegalStateException(
            "Your prepayment credits are depleted. Please go to AI Studio at https://ai.studio/projects to manage your project and billing."
        )
        assertTrue(GeminiImpactService.isFatalNonRetryableError(depletedError))
        assertTrue(GeminiImpactService.isFatalNonRetryableError(IllegalStateException("HTTP 403 App Check token invalid")))
        assertFalse(GeminiImpactService.isFatalNonRetryableError(IllegalStateException("429 RESOURCE_EXHAUSTED quota exceeded")))
        assertFalse(GeminiImpactService.isFatalNonRetryableError(java.net.UnknownHostException("offline")))

        val classified = GeminiImpactService.classifyFailure(
            error = depletedError,
            tierDiagnostics = listOf("Tier 1 (gemini-3.8-flash structured) failed -> ServerException: Your prepayment credits are depleted."),
            appCheckStatus = "Debug App Check active (intent token committed=true, project=ai-using-python)"
        )
        assertEquals(AiFailureKind.BILLING_DEPLETED, classified.failureKind)
        assertEquals(AI_STUDIO_BILLING_URL, classified.billingUrl)
        assertTrue(classified.userMessage.lowercase().contains("prepayment") || classified.userMessage.lowercase().contains("billing"))
        assertTrue(classified.technicalDetails.contains("project=ai-using-python"))
    }

    @Test
    fun decimalAndCompactAmountHelpers_preserveFractionalValues() {
        assertEquals(5.5, parseDecimalInput("5,5")!!, 0.001)
        assertEquals(120.25, parseDecimalInput(" 120.25 ")!!, 0.001)
        assertNull(parseDecimalInput("abc"))

        assertEquals("0.5", formatCompactAmount(0.5))
        assertEquals("100", formatCompactAmount(100.0))
        assertEquals("2.25", formatCompactAmount(2.25))
    }

    @Test
    fun substanceRepository_importHistoryMarksImportedDatesMutated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryDao = InMemoryCircadiaDao()
        val repo = SubstanceRepository(memoryDao)
        val tempFile = java.io.File(context.cacheDir, "circadia_test_import.json")
        tempFile.writeText(
            """
            {
              "app": "Circadia",
              "version": 2,
              "substanceLogs": [
                {
                  "dateIso": "2026-11-16",
                  "substanceId": "melatonin",
                  "substanceName": "Melatonin",
                  "category": "Medication",
                  "dosageAmount": 0.5,
                  "dosageUnit": "mg",
                  "takenAtMillis": 1700000000000,
                  "halfLifeHours": 1.0,
                  "disruptionWeight": 0.2
                }
              ],
              "sleepSessions": []
            }
            """.trimIndent()
        )
        try {
            val (logsCount, sessionsCount) = repo.importHistoryFromUri(context, Uri.fromFile(tempFile))
            assertEquals(1, logsCount)
            assertEquals(0, sessionsCount)
            val mutations = repo.dateMutations.value
            assertTrue((mutations["2026-11-16"] ?: 0L) > 0L)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun expandedSubstanceCatalog_hasSpecificFormulationsDosesAndLegacyAliases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SubstanceCatalog.initializeFromResources(context)

        val items = SubstanceCatalog.items
        assertTrue("Expected >= 60 curated formulations, got ${items.size}", items.size >= 60)

        val adderallIr = SubstanceCatalog.findById("adderall")
        val adderallXr = SubstanceCatalog.findById("adderall_xr")
        val vyvanse = SubstanceCatalog.findById("vyvanse")
        val concerta = SubstanceCatalog.findById("concerta")

        assertNotNull(adderallIr)
        assertNotNull(adderallXr)
        assertNotNull(vyvanse)
        assertNotNull(concerta)

        assertEquals("IR Tablet", adderallIr!!.formulation)
        assertTrue(adderallIr.doseOptions.any { it.amount == 10.0 && it.unit == "mg" })
        assertEquals("XR Capsule", adderallXr!!.formulation)
        assertTrue(adderallXr.doseOptions.any { it.amount == 20.0 && it.unit == "mg" })
        assertTrue(vyvanse!!.doseOptions.any { it.amount == 70.0 && it.unit == "mg" })
        assertTrue(concerta!!.doseOptions.any { it.amount == 36.0 && it.unit == "mg" })

        // Legacy ID aliases remain resolvable for historical logs
        assertEquals("prednisone", SubstanceCatalog.findById("corticosteroids")?.id)
        assertEquals("propranolol_ir", SubstanceCatalog.findById("beta_blockers")?.id)
    }

    @Test
    fun rxTermsParser_extractsFormulationsAndExactStrengthOptions() {
        val sampleRxTermsJson = """
            [
              2,
              ["ADDERALL XR (Oral Pill)", "CONCERTA (Oral Pill)"],
              {
                "STRENGTHS_AND_FORMS": [
                  ["5 mg Cap ER", "10 mg Cap ER", "20 mg Cap ER", "30 mg Cap ER"],
                  ["18 mg Tab ER", "27 mg Tab ER", "36 mg Tab ER", "54 mg Tab ER"]
                ],
                "RXCUIS": [
                  ["541872", "541875", "541878", "541881"],
                  ["311698", "351245", "311699", "311700"]
                ]
              },
              [
                ["ADDERALL XR (Oral Pill)"],
                ["CONCERTA (Oral Pill)"]
              ]
            ]
        """.trimIndent()

        val parsed = MedicationLookupService.parseRxTermsResponse(sampleRxTermsJson)
        assertEquals(2, parsed.size)

        val adderallXr = parsed.first()
        assertEquals("Adderall XR", adderallXr.cleanName)
        assertEquals("Oral Pill", adderallXr.routeForm)
        assertEquals(SubstanceCategory.STIMULANT, adderallXr.inferredCategory)
        assertEquals(4, adderallXr.strengthOptions.size)
        assertEquals(5.0, adderallXr.strengthOptions[0].amount, 0.001)
        assertEquals("mg", adderallXr.strengthOptions[0].unit)
        assertEquals("20 mg Cap ER", adderallXr.strengthOptions[2].label)

        val mcgParsed = MedicationLookupService.parseStrengthOption("500 mcg Tab")
        assertNotNull(mcgParsed)
        assertEquals(500.0, mcgParsed!!.amount, 0.001)
        assertEquals("mcg", mcgParsed.unit)
    }

    @Test
    fun openFdaExtractor_parsesHalfLifeRangesSingleMinutesDaysAndTmax() {
        val rangeText = "Following oral administration, the mean elimination half-life is 10 to 13 hours and peak plasma concentrations occur at 3 hours."
        val rangePk = MedicationLookupService.extractPharmacokineticsFromLabelText(rangeText)
        assertNotNull(rangePk)
        assertEquals(11.5, rangePk!!.halfLifeHours!!, 0.05)
        assertEquals(180, rangePk.peakOnsetMinutes)
        assertEquals(HalfLifeSource.OPEN_FDA, rangePk.source)

        val minutesText = "The terminal half-life of the drug is approximately 45 minutes in healthy adults."
        val minutesPk = MedicationLookupService.extractPharmacokineticsFromLabelText(minutesText)
        assertNotNull(minutesPk)
        assertEquals(0.8, minutesPk!!.halfLifeHours!!, 0.1)

        val daysText = "The apparent plasma half-life averages 2 days after chronic dosing."
        val daysPk = MedicationLookupService.extractPharmacokineticsFromLabelText(daysText)
        assertNotNull(daysPk)
        assertEquals(48.0, daysPk!!.halfLifeHours!!, 0.1)

        val unrelated = MedicationLookupService.extractPharmacokineticsFromLabelText("Store at controlled room temperature 20 to 25 C.")
        assertNull(unrelated)
    }

    @Test
    fun medicationCatalogRepository_persistsAndReloadsOfflineCache() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempCacheFile = java.io.File(context.cacheDir, "test_medication_cache_${System.nanoTime()}.json")
        try {
            val sampleRemote = RemoteMedicationItem(
                id = "rxterms_jornay_pm_oral_pill",
                displayName = "Jornay PM (Oral Pill)",
                cleanName = "Jornay PM",
                routeForm = "Oral Pill",
                rxcuis = listOf("2059224"),
                strengthOptions = listOf(
                    DoseOption(20.0, "mg", "20 mg Cap ER", "Cap ER"),
                    DoseOption(40.0, "mg", "40 mg Cap ER", "Cap ER"),
                    DoseOption(60.0, "mg", "60 mg Cap ER", "Cap ER")
                ),
                inferredCategory = SubstanceCategory.STIMULANT
            )
            val writerRepo = MedicationCatalogRepository(context = context, cacheFile = tempCacheFile)
            writerRepo.saveCacheSnapshotForTesting(
                query = "jornay",
                items = listOf(sampleRemote),
                pkEntryKey = "jornay pm",
                pkEntry = OpenFdaPharmacokinetics(
                    halfLifeHours = 5.9,
                    peakOnsetMinutes = 120,
                    excerpt = "Mean terminal half-life is 5.9 hours.",
                    source = HalfLifeSource.OPEN_FDA
                )
            )

            // Reload from disk in a fresh repository instance
            val readerRepo = MedicationCatalogRepository(context = context, cacheFile = tempCacheFile)
            val searchResult = readerRepo.searchCatalog("jornay", null)
            assertTrue(searchResult.fromOfflineCache)
            assertEquals(1, searchResult.remoteMatches.size)
            assertEquals("Jornay PM", searchResult.remoteMatches.first().cleanName)
            assertEquals(3, searchResult.remoteMatches.first().strengthOptions.size)

            val resolvedPk = readerRepo.resolvePharmacokinetics(sampleRemote)
            assertEquals(5.9, resolvedPk.halfLifeHours!!, 0.05)
            assertEquals(HalfLifeSource.CACHED_OPEN_FDA, resolvedPk.source)
        } finally {
            tempCacheFile.delete()
        }
    }

    @Test
    fun inhaledCannabisCalculator_scalesWithPuffLengthMultiCannabinoidsAndThcaDecarb() {
        val singleThc = listOf(
            CannabinoidEntry(rowId = 1L, type = CannabinoidType.DELTA_9_THC, percentText = "80")
        )
        val est3s = InhaledIntakeCalculator.estimateCannabisSession(
            method = CannabisIntakeMethod.VAPED,
            quantityMode = CannabisQuantityMode.PUFFS,
            quantityValue = 4.0,
            puffLengthSeconds = 3.0,
            cannabinoids = singleThc
        )
        val est6s = InhaledIntakeCalculator.estimateCannabisSession(
            method = CannabisIntakeMethod.VAPED,
            quantityMode = CannabisQuantityMode.PUFFS,
            quantityValue = 4.0,
            puffLengthSeconds = 6.0,
            cannabinoids = singleThc
        )
        // Doubling puff length from 3s to 6s doubles estimated active mg
        assertEquals(est3s.totalActiveMg * 2.0, est6s.totalActiveMg, 0.05)
        assertEquals(4.5, est3s.weightedHalfLifeHours, 0.05)
        assertEquals(1.15, est3s.weightedDisruptionWeight, 0.05)

        // THCA applies 0.877 decarboxylation conversion compared to Delta-9 THC
        val thcaOnly = listOf(
            CannabinoidEntry(rowId = 1L, type = CannabinoidType.THCA, percentText = "80")
        )
        val estThca = InhaledIntakeCalculator.estimateCannabisSession(
            method = CannabisIntakeMethod.VAPED,
            quantityMode = CannabisQuantityMode.PUFFS,
            quantityValue = 4.0,
            puffLengthSeconds = 3.0,
            cannabinoids = thcaOnly
        )
        assertEquals(est3s.totalActiveMg * 0.877, estThca.totalActiveMg, 0.05)

        // Multi-cannabinoid blend with CBD lowers weighted sleep disruption and raises weighted half-life
        val blend = listOf(
            CannabinoidEntry(rowId = 1L, type = CannabinoidType.DELTA_9_THC, percentText = "40"),
            CannabinoidEntry(rowId = 2L, type = CannabinoidType.CBD, percentText = "40")
        )
        val estBlend = InhaledIntakeCalculator.estimateCannabisSession(
            method = CannabisIntakeMethod.SMOKED,
            quantityMode = CannabisQuantityMode.GRAMS,
            quantityValue = 0.5,
            puffLengthSeconds = 3.0,
            cannabinoids = blend
        )
        assertEquals(2, estBlend.breakdown.size)
        assertEquals(100.0, estBlend.totalActiveMg, 0.05) // 0.5g * 1000 * 0.25 efficiency * 80% total
        assertTrue(estBlend.weightedDisruptionWeight < 0.8)
        assertTrue(estBlend.weightedHalfLifeHours > 5.0)
    }

    @Test
    fun inhaledNicotineCalculator_handlesSmokedTobaccoAndVapePuffLengthScaling() {
        val smoked = InhaledIntakeCalculator.estimateSmokedTobacco(
            type = SmokedTobaccoType.FULL_FLAVOR_CIG,
            count = 2.0
        )
        assertEquals(2.6, smoked.absorbedMg, 0.01)
        assertEquals(2.0, smoked.halfLifeHours, 0.01)

        val vapedPct3s = InhaledIntakeCalculator.estimateVapedNicotine(
            strengthValue = 5.0,
            strengthUnit = VapeStrengthUnit.PERCENT,
            puffs = 10.0,
            puffLengthSeconds = 3.0
        )
        val vapedMgMl3s = InhaledIntakeCalculator.estimateVapedNicotine(
            strengthValue = 50.0,
            strengthUnit = VapeStrengthUnit.MG_PER_ML,
            puffs = 10.0,
            puffLengthSeconds = 3.0
        )
        val vapedPct6s = InhaledIntakeCalculator.estimateVapedNicotine(
            strengthValue = 5.0,
            strengthUnit = VapeStrengthUnit.PERCENT,
            puffs = 10.0,
            puffLengthSeconds = 6.0
        )
        assertEquals(1.2, vapedPct3s.absorbedMg, 0.01)
        assertEquals(vapedPct3s.absorbedMg, vapedMgMl3s.absorbedMg, 0.01)
        assertEquals(2.4, vapedPct6s.absorbedMg, 0.01)
    }

    @Test
    fun structuredSessionMetadata_roundTripsCannabisAndNicotineWithPuffLength() {
        val rows = listOf(
            CannabinoidEntry(rowId = 1L, type = CannabinoidType.DELTA_9_THC, percentText = "78"),
            CannabinoidEntry(rowId = 2L, type = CannabinoidType.CBD, percentText = "6"),
            CannabinoidEntry(rowId = 3L, type = CannabinoidType.CUSTOM, customName = "THCV", percentText = "3")
        )
        val cannabisTag = StructuredSessionMetadata.formatCannabisTag(
            method = CannabisIntakeMethod.VAPED,
            quantityMode = CannabisQuantityMode.PUFFS,
            quantityText = "5",
            puffLengthSecondsText = "5",
            cannabinoids = rows,
            oralDoseText = "10"
        )
        val mergedCannabis = StructuredSessionMetadata.mergeTagWithUserNotes(cannabisTag, "Live resin cart")
        val (cannPills, cleanCannNotes) = StructuredSessionMetadata.extractBreakdownPillsAndUserNotes(mergedCannabis)
        assertEquals("Live resin cart", cleanCannNotes)
        assertEquals(4, cannPills.size)
        assertTrue(cannPills[0].contains("5 puffs (5s)"))
        assertEquals("Δ9-THC 78%", cannPills[1])
        assertEquals("CBD 6%", cannPills[2])
        assertEquals("THCV 3%", cannPills[3])

        val parsedCann = StructuredSessionMetadata.parseCannabisStateFromNotes("cannabis_thc_vaped", mergedCannabis)
        assertNotNull(parsedCann)
        assertEquals(CannabisIntakeMethod.VAPED, parsedCann!!.method)
        assertEquals(CannabisQuantityMode.PUFFS, parsedCann.quantityMode)
        assertEquals("5", parsedCann.quantityText)
        assertEquals("5", parsedCann.puffLengthSecondsText)
        assertEquals(3, parsedCann.cannabinoids.size)
        assertEquals(CannabinoidType.CUSTOM, parsedCann.cannabinoids[2].type)
        assertEquals("THCV", parsedCann.cannabinoids[2].customName)
        assertEquals("3", parsedCann.cannabinoids[2].percentText)

        // Vaped Nicotine round-trip
        val nicTag = StructuredSessionMetadata.formatNicotineTag(
            method = NicotineDeliveryMethod.VAPED_NICOTINE,
            smokedType = SmokedTobaccoType.FULL_FLAVOR_CIG,
            smokedCountText = "1",
            vapeStrengthUnit = VapeStrengthUnit.PERCENT,
            vapeStrengthText = "5",
            vapePuffsText = "12",
            vapePuffLengthSecondsText = "4"
        )
        val mergedNic = StructuredSessionMetadata.mergeTagWithUserNotes(nicTag, "After lunch")
        val (nicPills, cleanNicNotes) = StructuredSessionMetadata.extractBreakdownPillsAndUserNotes(mergedNic)
        assertEquals("After lunch", cleanNicNotes)
        assertEquals(3, nicPills.size)
        assertTrue(nicPills[2].contains("12 puffs (4s)"))

        val parsedNic = StructuredSessionMetadata.parseNicotineStateFromNotes("nicotine_vape", mergedNic)
        assertNotNull(parsedNic)
        assertEquals(NicotineDeliveryMethod.VAPED_NICOTINE, parsedNic!!.method)
        assertEquals(VapeStrengthUnit.PERCENT, parsedNic.vapeStrengthUnit)
        assertEquals("5", parsedNic.vapeStrengthText)
        assertEquals("12", parsedNic.vapePuffsText)
        assertEquals("4", parsedNic.vapePuffLengthSecondsText)

        // Legacy cannabis_thc ID resolves cleanly via SubstanceCatalog
        val context = ApplicationProvider.getApplicationContext<Context>()
        SubstanceCatalog.initializeFromResources(context)
        assertEquals("cannabis_thc_vaped", SubstanceCatalog.findById("cannabis_thc")?.id)
        assertNotNull(SubstanceCatalog.findById("cannabis_thc_smoked"))
        assertNotNull(SubstanceCatalog.findById("cannabis_thc_edible"))
        assertNotNull(SubstanceCatalog.findById("nicotine_smoked_tobacco"))
        assertNotNull(SubstanceCatalog.findById("nicotine_vape"))
    }

    @Test
    fun sleepSessionMerger_mergesNapAndNightSleepAndDeduplicatesReimports() {
        val dateIso = "2026-11-18"
        val nightStart = DateTimeUtils.combineDateAndTime("2026-11-17", 23, 0)
        val nightEnd = DateTimeUtils.combineDateAndTime("2026-11-18", 6, 30)
        val napStart = DateTimeUtils.combineDateAndTime("2026-11-18", 14, 0)
        val napEnd = DateTimeUtils.combineDateAndTime("2026-11-18", 14, 45)

        val nightSleep = SleepSessionEntity(
            dateIso = dateIso,
            source = "Google Fit",
            totalDurationMinutes = 450,
            remMinutes = 95,
            deepMinutes = 85,
            lightMinutes = 250,
            awakeMinutes = 20,
            restingHeartRateBpm = 56,
            bedtimeMillis = nightStart,
            wakeTimeMillis = nightEnd,
            syncedAtMillis = 1000L
        )
        val afternoonNap = SleepSessionEntity(
            dateIso = dateIso,
            source = "Google Fit",
            totalDurationMinutes = 45,
            remMinutes = 10,
            deepMinutes = 5,
            lightMinutes = 28,
            awakeMinutes = 2,
            restingHeartRateBpm = null,
            bedtimeMillis = napStart,
            wakeTimeMillis = napEnd,
            syncedAtMillis = 2000L
        )

        // 1. Batch with night sleep + distinct afternoon nap keeps longest window and combines stage totals
        val mergedBatch = SleepSessionMerger.mergeBatch(listOf(nightSleep, afternoonNap))
        assertEquals(1, mergedBatch.size)
        val combined = mergedBatch.first()
        assertEquals(nightStart, combined.bedtimeMillis)
        assertEquals(nightEnd, combined.wakeTimeMillis)
        assertEquals(495, combined.totalDurationMinutes)
        assertEquals(105, combined.remMinutes)
        assertEquals(90, combined.deepMinutes)
        assertEquals(278, combined.lightMinutes)
        assertEquals(22, combined.awakeMinutes)
        assertEquals(56, combined.restingHeartRateBpm)

        // 2. Re-importing the exact same batch into existing merged state is idempotent (no double-counting)
        val reimported = SleepSessionMerger.mergeWithExisting(
            existing = mergedBatch,
            incoming = listOf(nightSleep, afternoonNap)
        )
        assertEquals(1, reimported.size)
        assertEquals(495, reimported.first().totalDurationMinutes)
        assertEquals(105, reimported.first().remMinutes)

        // 3. Re-importing just the afternoon nap into an already-merged day preserves the full merged day without clobbering or double-counting
        val reimportedNapOnly = SleepSessionMerger.mergeWithExisting(
            existing = mergedBatch,
            incoming = listOf(afternoonNap)
        )
        assertEquals(1, reimportedNapOnly.size)
        assertEquals(495, reimportedNapOnly.first().totalDurationMinutes)
        assertEquals(nightStart, reimportedNapOnly.first().bedtimeMillis)
        assertEquals(56, reimportedNapOnly.first().restingHeartRateBpm)
    }

    @Test
    fun structuredSessionMetadata_sanitizesCustomNameCapsAt300AndScalesDoseRatio() {
        // 1. Custom cannabinoid name strips bracket/separator syntax characters and caps at 20 chars
        val naughtyCustom = CannabinoidEntry(
            rowId = 1L,
            type = CannabinoidType.CUSTOM,
            customName = "THCV] · [Evil,NameThatIsWayTooLong12345",
            percentText = "12"
        )
        val sanitizedName = naughtyCustom.resolvedDisplayName()
        assertFalse(sanitizedName.contains(']'))
        assertFalse(sanitizedName.contains('['))
        assertFalse(sanitizedName.contains('·'))
        assertFalse(sanitizedName.contains(','))
        assertTrue(sanitizedName.length <= 20)

        // 2. Huge list of cannabinoids + long user notes stays <= 300 chars and preserves closing ']'
        val manyRows = (1..25).map { idx ->
            CannabinoidEntry(
                rowId = idx.toLong(),
                type = CannabinoidType.CUSTOM,
                customName = "Cannabinoid$idx",
                percentText = "3"
            )
        }
        val tag = StructuredSessionMetadata.formatCannabisTag(
            method = CannabisIntakeMethod.VAPED,
            quantityMode = CannabisQuantityMode.PUFFS,
            quantityText = "4",
            puffLengthSecondsText = "3",
            cannabinoids = manyRows,
            oralDoseText = "10"
        )
        assertTrue(tag.endsWith("]"))
        val merged = StructuredSessionMetadata.mergeTagWithUserNotes(tag, "x".repeat(400))
        assertTrue("Merged notes length ${merged.length} exceeded 300", merged.length <= 300)
        assertTrue(merged.contains("]"))
        assertNotNull(StructuredSessionMetadata.parseCannabisStateFromNotes("cannabis_thc_vaped", merged))

        // 3. Proportional scaling updates puffs/grams/cigarettes when inline +/- dose is used
        val cannNotes = StructuredSessionMetadata.mergeTagWithUserNotes(
            StructuredSessionMetadata.formatCannabisTag(
                method = CannabisIntakeMethod.VAPED,
                quantityMode = CannabisQuantityMode.PUFFS,
                quantityText = "4",
                puffLengthSecondsText = "3",
                cannabinoids = listOf(CannabinoidEntry(1L, CannabinoidType.DELTA_9_THC, percentText = "80")),
                oralDoseText = "10"
            ),
            "Before movie"
        )
        val scaledCannNotes = StructuredSessionMetadata.scaleQuantityForDoseRatio(
            substanceId = "cannabis_thc_vaped",
            fullNotes = cannNotes,
            oldDose = 10.0,
            newDose = 15.0
        )
        val parsedScaledCann = StructuredSessionMetadata.parseCannabisStateFromNotes("cannabis_thc_vaped", scaledCannNotes)
        assertNotNull(parsedScaledCann)
        assertEquals("6", parsedScaledCann!!.quantityText)
        assertEquals("3", parsedScaledCann.puffLengthSecondsText)
        assertEquals("Before movie", StructuredSessionMetadata.stripStructuredTag(scaledCannNotes))

        val nicNotes = StructuredSessionMetadata.formatNicotineTag(
            method = NicotineDeliveryMethod.VAPED_NICOTINE,
            smokedType = SmokedTobaccoType.FULL_FLAVOR_CIG,
            smokedCountText = "1",
            vapeStrengthUnit = VapeStrengthUnit.PERCENT,
            vapeStrengthText = "5",
            vapePuffsText = "10",
            vapePuffLengthSecondsText = "4"
        )
        val scaledNicNotes = StructuredSessionMetadata.scaleQuantityForDoseRatio(
            substanceId = "nicotine_vape",
            fullNotes = nicNotes,
            oldDose = 2.0,
            newDose = 3.0
        )
        val parsedScaledNic = StructuredSessionMetadata.parseNicotineStateFromNotes("nicotine_vape", scaledNicNotes)
        assertNotNull(parsedScaledNic)
        assertEquals("15", parsedScaledNic!!.vapePuffsText)
        assertEquals("4", parsedScaledNic.vapePuffLengthSecondsText)
    }

    @Test
    fun persistenceLifecycle_keepThenReEnableDoesNotDuplicateLogs() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val db = Room.inMemoryDatabaseBuilder(context, CircadiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val roomDao = db.circadiaDao()
            val memoryDao = InMemoryCircadiaDao()
            val prefsRepo = UserPreferencesRepository(context)
            prefsRepo.setPersistentDbEnabled(false)

            val dbId = context.getString(R.string.firestore_database_id)
            val cloudService = CloudDatabaseSyncService(
                db = FirebaseFirestore.getInstance(dbId),
                auth = FirebaseAuth.getInstance(),
                prefsRepo = prefsRepo
            )
            val switchableDao = SwitchableCircadiaDao(memoryDao, roomDao, prefsRepo, cloudService)
            val persistenceRepo = DatabasePersistenceRepository(memoryDao, roomDao, prefsRepo, cloudService)

            val dateIso = "2026-11-18"
            switchableDao.insertLog(
                SubstanceLogEntity(
                    dateIso = dateIso,
                    substanceId = "caffeine_coffee",
                    substanceName = "Caffeine (Coffee)",
                    category = "Stimulant",
                    dosageAmount = 100.0,
                    dosageUnit = "mg",
                    takenAtMillis = DateTimeUtils.combineDateAndTime(dateIso, 8, 30),
                    halfLifeHours = 5.0,
                    disruptionWeight = 1.0,
                    syncId = "log_keep_cycle_test"
                )
            )

            persistenceRepo.enablePersistentDatabase()
            assertEquals(1, roomDao.getAllLogsOnce().size)

            // Disable with KEEP (copies Room back into memory without clearing Room)
            persistenceRepo.disablePersistentDatabase(clearSavedData = false, clearCloudData = false)
            assertEquals(1, memoryDao.getAllLogsOnce().size)
            assertEquals(1, roomDao.getAllLogsOnce().size)

            // Re-enable persistence: should deduplicate instead of duplicating the kept record
            persistenceRepo.enablePersistentDatabase()
            assertEquals(1, roomDao.getAllLogsOnce().size)
            assertEquals("log_keep_cycle_test", roomDao.getAllLogsOnce().first().syncId)
        } finally {
            UserPreferencesRepository(context).setPersistentDbEnabled(false)
            UserPreferencesRepository(context).clearAllPendingCloudDeletions()
            db.close()
        }
    }

    @Test
    fun substanceRepository_reimportBackupDeduplicatesLogsAndMergesSleep() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryDao = InMemoryCircadiaDao()
        val repo = SubstanceRepository(memoryDao)

        val dateIso = "2026-11-18"
        val nightStart = DateTimeUtils.combineDateAndTime("2026-11-17", 23, 0)
        val nightEnd = DateTimeUtils.combineDateAndTime("2026-11-18", 6, 30)
        val napStart = DateTimeUtils.combineDateAndTime("2026-11-18", 14, 0)
        val napEnd = DateTimeUtils.combineDateAndTime("2026-11-18", 14, 30)

        val tempFile = java.io.File(context.cacheDir, "circadia_dedup_test_${System.nanoTime()}.json")
        tempFile.writeText(
            """
            {
              "app": "Circadia",
              "version": 2,
              "substanceLogs": [
                {
                  "syncId": "log_backup_001",
                  "dateIso": "$dateIso",
                  "substanceId": "caffeine_coffee",
                  "substanceName": "Coffee",
                  "category": "Stimulant",
                  "dosageAmount": 120.0,
                  "dosageUnit": "mg",
                  "takenAtMillis": 1700000000000,
                  "halfLifeHours": 5.0,
                  "disruptionWeight": 1.0,
                  "updatedAtMillis": 1700000001000
                }
              ],
              "sleepSessions": [
                {
                  "dateIso": "$dateIso",
                  "source": "JSON Backup",
                  "totalDurationMinutes": 450,
                  "remMinutes": 90,
                  "deepMinutes": 80,
                  "lightMinutes": 260,
                  "awakeMinutes": 20,
                  "restingHeartRateBpm": 58,
                  "bedtimeMillis": $nightStart,
                  "wakeTimeMillis": $nightEnd
                },
                {
                  "dateIso": "$dateIso",
                  "source": "JSON Backup",
                  "totalDurationMinutes": 30,
                  "remMinutes": 5,
                  "deepMinutes": 5,
                  "lightMinutes": 18,
                  "awakeMinutes": 2,
                  "bedtimeMillis": $napStart,
                  "wakeTimeMillis": $napEnd
                }
              ]
            }
            """.trimIndent()
        )
        try {
            val (firstLogs, firstSessions) = repo.importHistoryFromUri(context, Uri.fromFile(tempFile))
            assertEquals(1, firstLogs)
            assertEquals(1, firstSessions)
            assertEquals(1, memoryDao.getAllLogsOnce().size)
            assertEquals(480, memoryDao.getAllSleepSessionsOnce().first().totalDurationMinutes)

            // Re-importing the exact same backup must not create duplicate logs or double sleep totals
            val (secondLogs, _) = repo.importHistoryFromUri(context, Uri.fromFile(tempFile))
            assertEquals(0, secondLogs)
            assertEquals(1, memoryDao.getAllLogsOnce().size)
            assertEquals(480, memoryDao.getAllSleepSessionsOnce().first().totalDurationMinutes)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun switchableDao_queuesPendingOfflineDeletionsAndClearsOnReinsert() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val db = Room.inMemoryDatabaseBuilder(context, CircadiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        try {
            val roomDao = db.circadiaDao()
            val memoryDao = InMemoryCircadiaDao()
            prefsRepo.clearAllPendingCloudDeletions()
            prefsRepo.setPersistentDbEnabled(true)

            val dbId = context.getString(R.string.firestore_database_id)
            val cloudService = CloudDatabaseSyncService(
                db = FirebaseFirestore.getInstance(dbId),
                auth = FirebaseAuth.getInstance(),
                prefsRepo = prefsRepo
            )
            val switchableDao = SwitchableCircadiaDao(memoryDao, roomDao, prefsRepo, cloudService)

            val log = SubstanceLogEntity(
                dateIso = "2026-11-18",
                substanceId = "melatonin",
                substanceName = "Melatonin",
                category = "Medication",
                dosageAmount = 1.0,
                dosageUnit = "mg",
                takenAtMillis = DateTimeUtils.combineDateAndTime("2026-11-18", 21, 30),
                halfLifeHours = 1.0,
                disruptionWeight = 0.2,
                syncId = "log_offline_tombstone_1"
            )
            val insertedId = switchableDao.insertLog(log)
            switchableDao.deleteLogById(insertedId)

            val pendingAfterDelete = prefsRepo.getPendingCloudDeletions()
            assertTrue(pendingAfterDelete.logDocIds.contains("log_offline_tombstone_1"))

            // Restoring the deleted entry (e.g., Undo Delete) clears its pending tombstone
            switchableDao.insertLog(log.copy(id = 0L))
            val pendingAfterRestore = prefsRepo.getPendingCloudDeletions()
            assertFalse(pendingAfterRestore.logDocIds.contains("log_offline_tombstone_1"))

            // Sleep session & report deletions also queue and clear cleanly
            val sleep = SleepSessionEntity(
                dateIso = "2026-11-18",
                source = "CSV",
                totalDurationMinutes = 420,
                remMinutes = 80,
                deepMinutes = 75,
                lightMinutes = 245,
                awakeMinutes = 20,
                restingHeartRateBpm = 57,
                bedtimeMillis = 1000L,
                wakeTimeMillis = 2000L
            )
            switchableDao.upsertSleepSessions(listOf(sleep))
            switchableDao.deleteSleepSession("2026-11-18")
            assertTrue(prefsRepo.getPendingCloudDeletions().sleepDocIds.contains("session_2026-11-18"))
            switchableDao.upsertSleepSessions(listOf(sleep))
            assertFalse(prefsRepo.getPendingCloudDeletions().sleepDocIds.contains("session_2026-11-18"))
        } finally {
            prefsRepo.setPersistentDbEnabled(false)
            prefsRepo.clearAllPendingCloudDeletions()
            db.close()
        }
    }
}

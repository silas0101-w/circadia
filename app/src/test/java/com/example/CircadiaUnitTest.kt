package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SafeFileReader
import com.example.data.local.SubstanceLogEntity
import com.example.data.remote.GoogleFitRestService
import com.example.data.remote.NvidiaNimService
import com.example.domain.DateTimeUtils
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SeverityTier
import com.example.domain.SubstanceCatalog
import com.example.ui.components.isAllowedGoogleAuthUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

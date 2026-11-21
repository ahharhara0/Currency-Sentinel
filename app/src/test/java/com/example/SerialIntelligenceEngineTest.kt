package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.catalog.CurrencyCatalogRepository
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.serial.CheckStatus
import com.example.engine.serial.DuplicateClassification
import com.example.engine.serial.SerialAuditInput
import com.example.engine.serial.SerialIntelligenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SerialIntelligenceEngineTest {

    private lateinit var catalogRepo: CurrencyCatalogRepository
    private lateinit var sie: SerialIntelligenceEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        catalogRepo = CurrencyCatalogRepository(context)
        sie = SerialIntelligenceEngine(catalogRepo.getCatalog())
    }

    @Test
    fun `catalog contains only SAR and USD and verifies SHA256 integrity manifest`() {
        val catalog = catalogRepo.getCatalog()
        assertEquals(setOf(CurrencyCode.SAR, CurrencyCode.USD), catalog.supportedCurrencies.toSet())
        val integrity = catalogRepo.getIntegrityReport()
        assertTrue("Manifest SHA-256 integrity must pass", integrity.allVerified)
        assertEquals(3, integrity.verifiedAssets.size)

        val sar5Cotton = catalogRepo.findEntries(CurrencyCode.SAR, 5, SubstrateMaterial.COTTON_LINEN)
        val sar5Polymer = catalogRepo.findEntries(CurrencyCode.SAR, 5, SubstrateMaterial.POLYMER)
        assertEquals(1, sar5Cotton.size)
        assertEquals(1, sar5Polymer.size)
        assertEquals(145f, sar5Polymer.first().widthMm)
        assertEquals(66f, sar5Polymer.first().heightMm)
    }

    @Test
    fun `USD 100 series R 2025 passes consistent check and flags FRB or series mismatch`() {
        val valid = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.USD,
                denomination = 100,
                primarySerial = "RB12345678C",
                printedSeriesYear = "2025",
                federalReserveIndicator = "B2"
            )
        )
        assertFalse(valid.hasCriticalContradiction)
        assertEquals("2025", valid.detectedSeriesYear)
        assertEquals(DuplicateClassification.UNIQUE, valid.fivePartReport.duplicateStatus)

        val frbMismatch = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.USD,
                denomination = 100,
                primarySerial = "RB12345678C",
                printedSeriesYear = "2025",
                federalReserveIndicator = "G7"
            )
        )
        assertTrue(frbMismatch.hasCriticalContradiction)
        assertTrue(frbMismatch.checks.any { it.id == "frb_match" && it.status == CheckStatus.CONTRADICTION })

        val yearMismatch = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.USD,
                denomination = 100,
                primarySerial = "QB12345678C",
                printedSeriesYear = "2013",
                federalReserveIndicator = "B2"
            )
        )
        assertTrue(yearMismatch.hasCriticalContradiction)
        assertTrue(yearMismatch.checks.any { it.id == "series_cross" && it.status == CheckStatus.CONTRADICTION })
    }

    @Test
    fun `USD 1 and 2 enforce 10-char legacy rule and flag glyph ambiguities`() {
        val validOne = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.USD,
                denomination = 1,
                primarySerial = "B10585678A",
                federalReserveIndicator = "B2"
            )
        )
        assertFalse(validOne.hasCriticalContradiction)
        assertTrue(validOne.fivePartReport.ocrAmbiguityCount >= 3) // 1, 0, 5, 8

        val invalidModernOnOne = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.USD,
                denomination = 1,
                primarySerial = "QB12345678C"
            )
        )
        assertTrue(invalidModernOnOne.hasCriticalContradiction)
    }

    @Test
    fun `SAR analyzer normalizes Arabic digits and detects dual-serial char diff`() {
        val matched = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.SAR,
                denomination = 500,
                primarySerial = "١٢٣٤٥٦٧٨",
                secondarySerial = "12345678"
            )
        )
        assertFalse(matched.hasCriticalContradiction)
        assertEquals("12345678", matched.normalizedSerial)

        val mismatched = sie.analyze(
            SerialAuditInput(
                currency = CurrencyCode.SAR,
                denomination = 500,
                primarySerial = "12345678",
                secondarySerial = "12345679"
            )
        )
        assertTrue(mismatched.hasCriticalContradiction)
        assertTrue(mismatched.anomaliesEn.first().contains("#8:8≠9"))
    }
}

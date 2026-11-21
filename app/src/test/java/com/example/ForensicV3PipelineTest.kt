package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.catalog.CurrencyCatalogRepository
import com.example.catalog.CurrencyCode
import com.example.data.SentinelDatabase
import com.example.data.VaultCrypto
import com.example.data.VaultRepository
import com.example.engine.cv.LiteRtForensicModels
import com.example.engine.cv.OpenCvBridge
import com.example.engine.identity.BanknoteIdentityEngine
import com.example.engine.identity.BieInput
import com.example.engine.serial.DuplicateClassification
import com.example.engine.vision.EvidenceFusionEngine
import com.example.engine.vision.ForensicVerdict
import com.example.engine.vision.InspectionMode
import com.example.engine.vision.PhotoInspectionRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForensicV3PipelineTest {

    private lateinit var context: Context
    private lateinit var catalogRepo: CurrencyCatalogRepository
    private lateinit var db: SentinelDatabase
    private lateinit var vaultRepo: VaultRepository
    private lateinit var liteRtModels: LiteRtForensicModels
    private lateinit var fusionEngine: EvidenceFusionEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        catalogRepo = CurrencyCatalogRepository(context)
        db = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vaultRepo = VaultRepository(context, db.vaultDao(), db.catalogProfileDao())
        liteRtModels = LiteRtForensicModels(context)
        fusionEngine = EvidenceFusionEngine(catalogRepo, liteRtModels)
    }

    @After
    fun tearDown() {
        liteRtModels.close()
        db.close()
    }

    @Test
    fun `BIE 2 classifies empty unclassified note and separates USD vs SAR`() = runBlocking {
        val bie = BanknoteIdentityEngine(catalogRepo, liteRtModels)

        val (unclassified, _) = bie.identify(
            BieInput(
                allOcrText = "",
                tokenStrings = emptyList(),
                serialCandidates = emptyList(),
                seriesYearHint = null,
                currencyHint = null,
                denominationHint = null,
                materialHint = null,
                featureVector12 = FloatArray(12) { 0f },
                dominantHueDeg = 0f,
                polymerWindowRatio = 0f
            )
        )
        assertTrue(unclassified.isUnclassified)

        val (sarId, sarEntry) = bie.identify(
            BieInput(
                allOcrText = "SAUDI CENTRAL BANK FIVE HUNDRED RIYALS 500",
                tokenStrings = listOf("500", "SAUDI"),
                serialCandidates = listOf("A12345678"),
                seriesYearHint = null,
                currencyHint = null,
                denominationHint = null,
                materialHint = null,
                featureVector12 = FloatArray(12) { 0.4f },
                dominantHueDeg = 205f,
                polymerWindowRatio = 0.1f
            )
        )
        assertFalse(sarId.isUnclassified)
        assertEquals(CurrencyCode.SAR, sarId.detectedCurrency)
        assertEquals(500, sarId.detectedDenomination)
        assertEquals("SAR_ISSUE6_500", sarEntry?.id)
    }

    @Test
    fun `VaultCrypto encrypts and decrypts payloads without exposing plaintext`() {
        val secret = "Report: USD 100 RB12345678C CONSISTENT"
        val encrypted = VaultCrypto.encryptString(secret)
        assertTrue(encrypted.startsWith("ENC_V3:"))
        assertFalse(encrypted.contains("RB12345678C"))
        assertEquals(secret, VaultCrypto.decryptString(encrypted))
    }

    @Test
    fun `Duplicate Intelligence distinguishes same note rescanned vs different note image`() = runBlocking {
        val bmp1 = Bitmap.createBitmap(300, 140, Bitmap.Config.ARGB_8888)
        Canvas(bmp1).apply {
            drawColor(Color.rgb(40, 120, 80))
        }
        val hash1 = OpenCvBridge.computePerceptualDHash(bmp1)

        val firstReport = fusionEngine.inspectPhoto(
            PhotoInspectionRequest(
                frontBitmap = bmp1,
                mode = InspectionMode.GALLERY_PHOTO,
                currencyHint = CurrencyCode.USD,
                denominationHint = 100,
                manualSerialOverride = "RB12345678C",
                manualFrIndicator = "B2",
                manualSeriesYear = "2025",
                duplicateClassifierFn = { c, d, s, h -> vaultRepo.classifyDuplicate(c, d, s, h) }
            )
        )
        vaultRepo.saveReport(firstReport)
        assertEquals(DuplicateClassification.UNIQUE, firstReport.duplicateClassification)

        // Rescanning exact same image => SAME_NOTE_RESCANNED
        val sameNoteDup = vaultRepo.classifyDuplicate(CurrencyCode.USD, 100, "RB12345678C", hash1)
        assertEquals(DuplicateClassification.SAME_NOTE_RESCANNED, sameNoteDup)

        // Opposite gradient / very different hash => SAME_SERIAL_DIFFERENT_IMAGE
        val oppositeHash = hash1.map { if (it == '0') 'f' else '0' }.joinToString("")
        val diffImageDup = vaultRepo.classifyDuplicate(CurrencyCode.USD, 100, "RB12345678C", oppositeHash)
        assertEquals(DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE, diffImageDup)

        bmp1.recycle()
    }

    @Test
    fun `Manual re-audit in Serial Workbench logs audit trail and updates verdict`() = runBlocking {
        val initial = fusionEngine.inspectManualAudit(
            currency = CurrencyCode.USD,
            denomination = 100,
            materialHint = null,
            primarySerial = "QB12345678C",
            secondarySerial = null,
            frIndicator = "G7", // Mismatch vs B
            seriesYear = "2021", // Series Q = 2021
            facePlate = null,
            backPlate = null,
            notePosition = null,
            starToggle = false,
            duplicateClassifierFn = { _, _, _, _ -> DuplicateClassification.UNIQUE }
        )
        assertEquals(ForensicVerdict.ANOMALIES_DETECTED, initial.verdict)

        val corrected = fusionEngine.reAuditWithManualSerial(
            existing = initial,
            correctedPrimary = "QG12345678C",
            correctedSecondary = null,
            frIndicator = "G7",
            seriesYear = "2021",
            starToggle = false,
            auditNote = "Fixed misread B -> G",
            duplicateClassifierFn = { _, _, _, _ -> DuplicateClassification.UNIQUE }
        )
        assertEquals(ForensicVerdict.CONSISTENT_WITH_SPECS, corrected.verdict)
        assertEquals(1, corrected.manualAuditTrail.size)
        assertNotEquals(initial.serialAnalysis.normalizedSerial, corrected.serialAnalysis.normalizedSerial)
    }
}

package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCode
import com.example.engine.cv.OpenCvBridge
import com.example.engine.serial.DuplicateClassification
import com.example.engine.vision.ForensicInspectionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File

class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val profileDao: CatalogProfileDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val evidenceDir: File by lazy {
        File(context.filesDir, "evidence").apply { mkdirs() }
    }

    val reportsFlow: Flow<List<ForensicInspectionReport>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row ->
            runCatching {
                val plainJson = VaultCrypto.decryptString(row.reportJson)
                json.decodeFromString<ForensicInspectionReport>(plainJson)
            }.getOrNull()
        }
    }

    suspend fun syncCatalogProfiles(catalogVersion: String, entries: List<BanknoteCatalogEntry>) {
        if (profileDao.countForVersion(catalogVersion) >= entries.size) return
        val entities = entries.map {
            CatalogProfileEntity(
                entryId = it.id,
                currencyCode = it.currency.name,
                denominationValue = it.denominationValue,
                issueYear = it.issueYear,
                material = it.material.name,
                provenanceTier = it.provenance.tier.name,
                catalogVersion = catalogVersion
            )
        }
        profileDao.insertAll(entities)
    }

    suspend fun classifyDuplicate(
        currency: CurrencyCode,
        denomination: Int,
        normalizedSerial: String,
        perceptualDHash: String,
        excludeReportId: String = ""
    ): DuplicateClassification {
        if (normalizedSerial.isBlank()) return DuplicateClassification.UNIQUE
        val matches = dao.findMatchingSerials(currency.name, denomination, normalizedSerial, excludeReportId)
        if (matches.isEmpty()) return DuplicateClassification.UNIQUE

        if (perceptualDHash.isBlank() || matches.all { it.perceptualDHash.isBlank() }) {
            return DuplicateClassification.INSUFFICIENT_COMPARISON
        }

        val minDist = matches
            .filter { it.perceptualDHash.isNotBlank() }
            .minOf { OpenCvBridge.computeHammingDistance(perceptualDHash, it.perceptualDHash) }

        return when {
            minDist <= 8 -> DuplicateClassification.SAME_NOTE_RESCANNED
            minDist <= 16 -> DuplicateClassification.POSSIBLE_DUPLICATE
            else -> DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE
        }
    }

    suspend fun archiveConsentImage(bitmap: Bitmap, reportId: String, side: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val baos = ByteArrayOutputStream()
                val scaled = Bitmap.createScaledBitmap(bitmap, 900, 400, true)
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                if (scaled != bitmap) scaled.recycle()
                val encrypted = VaultCrypto.encryptBytes(baos.toByteArray())
                val file = File(evidenceDir, "${reportId}_${side}.enc")
                file.writeBytes(encrypted)
                file.absolutePath
            }.getOrNull()
        }

    suspend fun loadArchivedImage(path: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null
            val plain = VaultCrypto.decryptBytes(file.readBytes())
            BitmapFactory.decodeByteArray(plain, 0, plain.size)
        }.getOrNull()
    }

    suspend fun saveReport(report: ForensicInspectionReport) {
        val rawJson = json.encodeToString(report)
        val encryptedJson = VaultCrypto.encryptString(rawJson)
        dao.insert(
            VaultEntity(
                reportId = report.reportId,
                timestampMillis = report.timestampMillis,
                currencyCode = report.currency.name,
                denominationValue = report.denominationValue,
                normalizedSerial = report.serialAnalysis.normalizedSerial,
                perceptualDHash = report.perceptualDHash,
                conditionSignature = report.conditionReport.conditionSignature,
                duplicateClassificationCode = report.duplicateClassification.name,
                verdictCode = report.verdict.name,
                anomalyCount = report.anomalies.size,
                isDuplicateFlagged = report.isLocalDuplicate,
                usableCoveragePercent = report.usableCoveragePercent,
                confidencePercent = report.confidencePercent,
                archivedFrontPath = report.archivedFrontPath,
                archivedBackPath = report.archivedBackPath,
                reportJson = encryptedJson
            )
        )
    }

    suspend fun getReport(reportId: String): ForensicInspectionReport? {
        val row = dao.getById(reportId) ?: return null
        return runCatching {
            val plainJson = VaultCrypto.decryptString(row.reportJson)
            json.decodeFromString<ForensicInspectionReport>(plainJson)
        }.getOrNull()
    }

    suspend fun deleteReport(reportId: String) {
        dao.getById(reportId)?.let { row ->
            row.archivedFrontPath?.let { runCatching { File(it).delete() } }
            row.archivedBackPath?.let { runCatching { File(it).delete() } }
        }
        dao.deleteById(reportId)
    }

    suspend fun clearAll() {
        runCatching {
            evidenceDir.listFiles()?.forEach { it.delete() }
        }
        dao.deleteAll()
    }
}

package com.example.engine.serial

import com.example.catalog.CurrencyCode
import kotlinx.serialization.Serializable

@Serializable
enum class CheckStatus {
    PASS,
    WARNING,
    CONTRADICTION,
    NEEDS_MATCH,
    UNAVAILABLE
}

@Serializable
enum class DuplicateClassification {
    UNIQUE,
    SAME_NOTE_RESCANNED,
    SAME_SERIAL_DIFFERENT_IMAGE,
    POSSIBLE_DUPLICATE,
    INSUFFICIENT_COMPARISON
}

@Serializable
data class SerialCharGlyph(
    val index: Int,
    val char: Char,
    val isAmbiguous: Boolean,
    val ambiguityHint: String? = null,
    val status: CheckStatus = CheckStatus.PASS
)

@Serializable
data class SerialTokenSegment(
    val value: String,
    val roleEn: String,
    val roleAr: String,
    val status: CheckStatus
)

@Serializable
data class SerialCheckRow(
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val resultEn: String,
    val resultAr: String,
    val status: CheckStatus
)

@Serializable
data class SerialPrintQuality(
    val strokeUniformity: Float,
    val baselineAlignment: Float,
    val characterSpacing: Float,
    val inkClarity: Float,
    val wearToleranceApplied: Boolean
)

@Serializable
data class SerialFivePartReport(
    val structuralValidityScore: Float,
    val structuralValiditySummaryEn: String,
    val structuralValiditySummaryAr: String,
    val ocrReliabilityScore: Float,
    val ocrAmbiguityCount: Int,
    val ocrReliabilitySummaryEn: String,
    val ocrReliabilitySummaryAr: String,
    val seriesCompatibilityScore: Float,
    val seriesCompatibilitySummaryEn: String,
    val seriesCompatibilitySummaryAr: String,
    val visualConsistencyScore: Float,
    val visualConsistencySummaryEn: String,
    val visualConsistencySummaryAr: String,
    val duplicateStatus: DuplicateClassification,
    val duplicateSummaryEn: String,
    val duplicateSummaryAr: String
)

@Serializable
data class SerialAnalysisResult(
    val rawExtracted: String,
    val normalizedSerial: String,
    val secondarySerial: String? = null,
    val currency: CurrencyCode,
    val denomination: Int,
    val isStarNote: Boolean,
    val detectedSeriesYear: String?,
    val detectedReserveBank: String?,
    val detectedSignatureOfficials: String? = null,
    val glyphs: List<SerialCharGlyph> = emptyList(),
    val segments: List<SerialTokenSegment>,
    val checks: List<SerialCheckRow>,
    val anomaliesEn: List<String>,
    val anomaliesAr: List<String>,
    val printQuality: SerialPrintQuality,
    val fivePartReport: SerialFivePartReport,
    val overallScore: Float,
    val hasCriticalContradiction: Boolean
)

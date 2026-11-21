package com.example.engine.identity

import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import kotlinx.serialization.Serializable

@Serializable
enum class NoteSideOrientation {
    FRONT,
    BACK,
    ROTATED_180,
    UNCERTAIN
}

@Serializable
data class IdentityClassificationResult(
    val detectedCurrency: CurrencyCode?,
    val currencyConfidence: Float,
    val detectedDenomination: Int?,
    val denominationConfidence: Float,
    val issueLabel: String,
    val issueConfidence: Float,
    val designFamilyId: String,
    val designFamilyConfidence: Float,
    val predictedMaterial: SubstrateMaterial,
    val materialConfidence: Float,
    val materialOpticalLimitationEn: String,
    val materialOpticalLimitationAr: String,
    val orientation: NoteSideOrientation,
    val orientationConfidence: Float,
    val matchedEntryId: String?,
    val isUnclassified: Boolean,
    val liteRtClassifierLabel: String,
    val liteRtConfidence: Float,
    val ranOnNativeLiteRt: Boolean
) {
    val selectedEntryIdOrNull: String? get() = matchedEntryId
}

data class BieInput(
    val allOcrText: String,
    val tokenStrings: List<String>,
    val serialCandidates: List<String>,
    val seriesYearHint: String?,
    val currencyHint: CurrencyCode?,
    val denominationHint: Int?,
    val materialHint: SubstrateMaterial?,
    val featureVector12: FloatArray,
    val dominantHueDeg: Float,
    val polymerWindowRatio: Float
)

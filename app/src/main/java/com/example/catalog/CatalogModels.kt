package com.example.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class CurrencyCode {
    SAR,
    USD
}

@Serializable
enum class SubstrateMaterial {
    COTTON_LINEN,
    POLYMER,
    UNCERTAIN
}

@Serializable
enum class SerialRuleType {
    USD_LEGACY_10,
    USD_MODERN_11,
    SAR_DOCUMENTED
}

@Serializable
enum class ProvenanceTier {
    OFFICIALLY_DOCUMENTED,
    OFFICIALLY_ILLUSTRATED,
    INDEPENDENTLY_MEASURED,
    RESEARCH_REFERENCE,
    UNVERIFIED,
    UNKNOWN
}

@Serializable
enum class RoiCategory {
    PORTRAIT,
    SERIAL,
    WATERMARK,
    SECURITY_THREAD,
    SEAL,
    MICROPRINT,
    DENOMINATION
}

@Serializable
data class ProvenanceRecord(
    val source: String,
    val tier: ProvenanceTier,
    val lastReviewed: String,
    val confidenceNoteEn: String,
    val confidenceNoteAr: String
)

@Serializable
data class UsdSeriesYearRule(
    val letter: String,
    val seriesYear: String,
    val denominations: List<Int>
)

@Serializable
data class FederalReserveDistrict(
    val letter: String,
    val number: Int,
    val code: String,
    val cityEn: String,
    val cityAr: String
)

@Serializable
data class DenominationSerialRule(
    val ruleId: String,
    val currency: CurrencyCode,
    val denominations: List<Int>,
    val expectedLength: Int?,
    val patternRegex: String,
    val descriptionEn: String,
    val descriptionAr: String
)

@Serializable
data class SignatureCombinationRule(
    val currency: CurrencyCode,
    val seriesOrIssue: String,
    val officials: String,
    val applicableDenominations: List<Int>
)

@Serializable
data class StarNoteRule(
    val currency: CurrencyCode,
    val symbol: String,
    val meaningEn: String,
    val meaningAr: String,
    val positionRule: String
)

@Serializable
data class SerialExceptionRule(
    val code: String,
    val currency: CurrencyCode,
    val summaryEn: String,
    val summaryAr: String
)

@Serializable
data class SecurityFeatureSpec(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val descriptionEn: String,
    val descriptionAr: String,
    val opticalCameraInspectable: Boolean = true,
    val provenanceTier: ProvenanceTier = ProvenanceTier.OFFICIALLY_DOCUMENTED
)

@Serializable
data class RoiCoordinate(
    val id: String,
    val category: RoiCategory = RoiCategory.DENOMINATION,
    val labelEn: String,
    val labelAr: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val side: String = "FRONT",
    val required: Boolean = true
)

@Serializable
data class BanknoteCatalogEntry(
    val id: String,
    val currency: CurrencyCode,
    val denominationValue: Int,
    val denominationLabelEn: String,
    val denominationLabelAr: String,
    val familyId: String,
    val familyNameEn: String,
    val familyNameAr: String,
    val issueYear: String,
    val designYear: String,
    val printYearRange: String,
    val signatureYears: String,
    val material: SubstrateMaterial,
    val widthMm: Float,
    val heightMm: Float,
    val dominantHueMin: Float,
    val dominantHueMax: Float,
    val serialRuleType: SerialRuleType,
    val serialFormatHint: String,
    val sourceCitation: String,
    val verificationStatus: String,
    val provenance: ProvenanceRecord,
    val changelogNote: String,
    val securityFeatures: List<SecurityFeatureSpec>,
    val roiZones: List<RoiCoordinate>
) {
    val officialAspectRatio: Float
        get() = widthMm / heightMm
}

@Serializable
data class CurrencyMasterCatalog(
    val catalogVersion: String,
    val lastUpdated: String,
    val supportedCurrencies: List<CurrencyCode>,
    val usdSeriesYears: List<UsdSeriesYearRule>,
    val usdFederalReserveDistricts: List<FederalReserveDistrict>,
    val denominationSerialRules: List<DenominationSerialRule>,
    val signatureCombinations: List<SignatureCombinationRule>,
    val starNoteRules: List<StarNoteRule>,
    val serialExceptions: List<SerialExceptionRule>,
    val entries: List<BanknoteCatalogEntry>
)

@Serializable
data class CatalogAssetDigest(
    val assetPath: String,
    val sha256: String,
    val role: String
)

@Serializable
data class CatalogManifest(
    val manifestVersion: String,
    val catalogVersion: String,
    val engineSuiteVersion: String,
    val assets: List<CatalogAssetDigest>
)

data class IntegrityReport(
    val allVerified: Boolean,
    val catalogVersion: String,
    val verifiedAssets: List<Pair<String, Boolean>>
)

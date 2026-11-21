package com.example.engine.vision

import com.example.catalog.CurrencyCode
import com.example.catalog.RoiCategory
import com.example.catalog.SubstrateMaterial
import com.example.engine.identity.IdentityClassificationResult
import com.example.engine.identity.NoteSideOrientation
import com.example.engine.serial.DuplicateClassification
import com.example.engine.serial.SerialAnalysisResult
import kotlinx.serialization.Serializable

@Serializable
enum class InspectionMode {
    CAMERA_CAPTURE,
    GALLERY_PHOTO,
    MANUAL_AUDIT
}

@Serializable
enum class ScanDepthMode {
    QUICK_SCAN,
    DEEP_FORENSIC_SCAN
}

@Serializable
enum class ForensicVerdict {
    CONSISTENT_WITH_SPECS,
    ANOMALIES_DETECTED,
    INCONCLUSIVE_INSUFFICIENT_EVIDENCE,
    UNCLASSIFIED_NOTE
}

@Serializable
enum class ContradictionType {
    HARD_CONTRADICTION,
    SOFT_ANOMALY,
    CAPTURE_ARTIFACT,
    UNKNOWN
}

@Serializable
enum class AnomalySeverity {
    CRITICAL,
    WARNING,
    INFO
}

@Serializable
enum class CellCondition {
    CLEAN,
    FOLD_CREASE,
    GLARE_OVEREXPOSED,
    BLUR_DEFOCUS,
    WORN_FADED,
    STAIN_DAMAGE,
    DARK_OCCLUDED
}

@Serializable
data class ConditionCell(
    val col: Int,
    val row: Int,
    val condition: CellCondition,
    val edgeDensity: Float,
    val meanBrightness: Float
)

@Serializable
data class ConditionReport(
    val gridCols: Int = 8,
    val gridRows: Int = 4,
    val cells: List<ConditionCell>,
    val blurSharpnessScore: Float,
    val glarePercent: Float,
    val foldPercent: Float,
    val stainPercent: Float = 0f,
    val blurCellPercent: Float = 0f,
    val wearScore: Float,
    val captureQualityScore: Float,
    val usableCoveragePercent: Float,
    val evidenceCompletenessPercent: Float,
    val occludedRoiIds: List<String>,
    val liteRtCleanProb: Float = 0f,
    val liteRtWornProb: Float = 0f,
    val liteRtCompromisedProb: Float = 0f,
    val conditionSignature: String = ""
)

@Serializable
data class RoiEvaluation(
    val roiId: String,
    val category: RoiCategory = RoiCategory.DENOMINATION,
    val labelEn: String,
    val labelAr: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val isOccluded: Boolean,
    val isPositionShifted: Boolean,
    val featureMatchScore: Float,
    val alignmentErrorPercent: Float,
    val edgeConsistencyScore: Float,
    val textureSimilarityScore: Float,
    val occlusionPercent: Float,
    val inspectionAvailable: Boolean,
    val confidence: Float
)

@Serializable
data class AnomalyItem(
    val id: String,
    val contradictionType: ContradictionType,
    val severity: AnomalySeverity,
    val categoryEn: String,
    val categoryAr: String,
    val titleEn: String,
    val titleAr: String,
    val detailEn: String,
    val detailAr: String
)

@Serializable
data class EngineScores(
    val geometryScore: Float,
    val layoutScore: Float,
    val colorScore: Float,
    val textureScore: Float,
    val printStructureScore: Float,
    val securityFeatureScore: Float,
    val serialScore: Float,
    val conditionScore: Float,
    val tiltDeltaMeasured: Boolean = false,
    val tiltOpticalShiftDelta: Float = 0f
)

@Serializable
data class EfeFourOutputs(
    val classificationConfidencePercent: Int,
    val structuralConsistencyPercent: Int,
    val evidenceSufficiencyPercent: Int,
    val forensicSuspicionRiskPercent: Int
)

@Serializable
data class ThreeQuestionAnswers(
    val q1IdentityEn: String,
    val q1IdentityAr: String,
    val q2SpecMatchEn: String,
    val q2SpecMatchAr: String,
    val q3AuthenticityEn: String,
    val q3AuthenticityAr: String
)

@Serializable
data class ManualAuditEdit(
    val timestampMillis: Long,
    val field: String,
    val previousValue: String,
    val newValue: String,
    val note: String
)

@Serializable
data class ForensicInspectionReport(
    val reportId: String,
    val timestampMillis: Long,
    val mode: InspectionMode,
    val scanDepthMode: ScanDepthMode = ScanDepthMode.QUICK_SCAN,
    val currency: CurrencyCode,
    val denominationValue: Int,
    val catalogEntryId: String,
    val designFamilyEn: String,
    val designFamilyAr: String,
    val observedMaterial: SubstrateMaterial,
    val seriesOrIssueYear: String,
    val designYear: String,
    val printYearRange: String,
    val orientation: NoteSideOrientation = NoteSideOrientation.FRONT,
    val primaryImageUri: String? = null,
    val backImageUri: String? = null,
    val archivedFrontPath: String? = null,
    val archivedBackPath: String? = null,
    val perceptualDHash: String = "",
    val verdict: ForensicVerdict,
    val efeOutputs: EfeFourOutputs,
    val identityResult: IdentityClassificationResult? = null,
    val confidencePercent: Int,
    val usableCoveragePercent: Int,
    val evidenceCompletenessPercent: Int = 100,
    val threeQuestions: ThreeQuestionAnswers,
    val engineScores: EngineScores,
    val serialAnalysis: SerialAnalysisResult,
    val conditionReport: ConditionReport,
    val roiEvaluations: List<RoiEvaluation>,
    val anomalies: List<AnomalyItem>,
    val duplicateClassification: DuplicateClassification = DuplicateClassification.UNIQUE,
    val isLocalDuplicate: Boolean,
    val catalogVersion: String = "3.0.0",
    val engineSuiteVersion: String = "V3-BIE2-VFE2-SIE2-EFE2",
    val calibrationWarningsEn: List<String> = emptyList(),
    val calibrationWarningsAr: List<String> = emptyList(),
    val manualAuditTrail: List<ManualAuditEdit> = emptyList(),
    val multiResolutionSummaryEn: String,
    val multiResolutionSummaryAr: String
)

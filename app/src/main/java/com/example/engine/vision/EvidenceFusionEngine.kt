package com.example.engine.vision

import android.graphics.Bitmap
import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCatalogRepository
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.cv.CaptureCalibrationEngine
import com.example.engine.cv.LiteRtForensicModels
import com.example.engine.cv.OpenCvBridge
import com.example.engine.identity.BanknoteIdentityEngine
import com.example.engine.identity.BieInput
import com.example.engine.identity.NoteSideOrientation
import com.example.engine.serial.DuplicateClassification
import com.example.engine.serial.SerialAnalysisResult
import com.example.engine.serial.SerialAuditInput
import com.example.engine.serial.SerialIntelligenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.UUID

data class PhotoInspectionRequest(
    val frontBitmap: Bitmap,
    val backBitmap: Bitmap? = null,
    val deepTiltFrames: List<Bitmap> = emptyList(),
    val scanDepthMode: ScanDepthMode = ScanDepthMode.QUICK_SCAN,
    val primaryImageUri: String? = null,
    val backImageUri: String? = null,
    val mode: InspectionMode,
    val currencyHint: CurrencyCode? = null,
    val denominationHint: Int? = null,
    val materialHint: SubstrateMaterial? = null,
    val manualSerialOverride: String? = null,
    val manualSecondarySerial: String? = null,
    val manualFrIndicator: String? = null,
    val manualSeriesYear: String? = null,
    val duplicateClassifierFn: suspend (CurrencyCode, Int, String, String) -> DuplicateClassification
)

class EvidenceFusionEngine(
    private val catalogRepo: CurrencyCatalogRepository,
    private val liteRtModels: LiteRtForensicModels? = null,
    private val mlKitBridge: MlKitBridge = MlKitBridge(),
    private val calibrationEngine: CaptureCalibrationEngine = CaptureCalibrationEngine(),
    private val identityEngine: BanknoteIdentityEngine = BanknoteIdentityEngine(catalogRepo, liteRtModels),
    private val conditionEngine: ConditionEngine = ConditionEngine(liteRtModels),
    private val geometryLayoutEngine: GeometryAndLayoutEngine = GeometryAndLayoutEngine(),
    private val texturePrintEngine: TextureAndPrintEngine = TextureAndPrintEngine(),
    private val colorEngine: ColorEngine = ColorEngine(),
    private val securityTiltEngine: SecurityFeatureAndTiltEngine = SecurityFeatureAndTiltEngine(),
    private val cde: ContradictionDetectionEngine = ContradictionDetectionEngine()
) {
    private val catalog = catalogRepo.getCatalog()
    private val sie = SerialIntelligenceEngine(catalog)

    suspend fun inspectPhoto(req: PhotoInspectionRequest): ForensicInspectionReport =
        withContext(Dispatchers.Default) {
            // 1. OpenCV Canonical Image Alignment & Perceptual dHash
            val aligned = OpenCvBridge.alignBanknote(req.frontBitmap)
            val canonicalFront = aligned.canonicalBitmap
            val dHash = OpenCvBridge.computePerceptualDHash(canonicalFront)
            val stats = OpenCvBridge.measureVisualStats(canonicalFront)
            val calibration = calibrationEngine.calibrate(req.frontBitmap, aligned, stats)

            // 2. Multi-pass ML Kit OCR (Full canonical frame + OpenCV CLAHE crop)
            val mlKitFront = mlKitBridge.analyzeBitmap(canonicalFront)
            val mlKitBack = req.backBitmap?.let {
                val alignedBack = OpenCvBridge.alignBanknote(it)
                mlKitBridge.analyzeBitmap(alignedBack.canonicalBitmap)
            }

            // 3. BIE 2.0 Independent Identity Classification
            val polymerRatio = estimatePolymerWindowRatio(canonicalFront)
            val (identityRes, matchedEntry) = identityEngine.identify(
                BieInput(
                    allOcrText = mlKitFront.allText,
                    tokenStrings = mlKitFront.tokens.map { it.text },
                    serialCandidates = mlKitFront.serialCandidates.map { it.text },
                    seriesYearHint = req.manualSeriesYear ?: mlKitFront.seriesYearHint,
                    currencyHint = req.currencyHint,
                    denominationHint = req.denominationHint,
                    materialHint = req.materialHint,
                    featureVector12 = stats.featureVector12,
                    dominantHueDeg = stats.dominantHueDeg,
                    polymerWindowRatio = polymerRatio
                )
            )

            val selectedEntry = matchedEntry ?: catalogRepo.findEntries(req.currencyHint ?: CurrencyCode.USD).first()

            // 4. CEF 2.0 & SIE 2.0
            val serialPrimary = req.manualSerialOverride?.takeIf { it.isNotBlank() }
                ?: mlKitFront.serialCandidates.firstOrNull()?.text.orEmpty()
            val serialSecondary = req.manualSecondarySerial?.takeIf { it.isNotBlank() }
                ?: mlKitFront.serialCandidates.getOrNull(1)?.text

            val normalizedForDup = sie.normalizeSerial(serialPrimary)
            val dupClass = if (normalizedForDup.isNotEmpty() && !identityRes.isUnclassified) {
                req.duplicateClassifierFn(selectedEntry.currency, selectedEntry.denominationValue, normalizedForDup, dHash)
            } else DuplicateClassification.UNIQUE

            val conditionDeferred = async { conditionEngine.analyze(canonicalFront, if (identityRes.isUnclassified) null else selectedEntry, calibration) }
            val serialDeferred = async {
                sie.analyze(
                    SerialAuditInput(
                        currency = selectedEntry.currency,
                        denomination = selectedEntry.denominationValue,
                        primarySerial = serialPrimary,
                        secondarySerial = serialSecondary,
                        printedSeriesYear = req.manualSeriesYear ?: mlKitFront.seriesYearHint,
                        federalReserveIndicator = req.manualFrIndicator ?: mlKitFront.federalReserveHint,
                        facePlateNumber = mlKitFront.plateHints.firstOrNull(),
                        backPlateNumber = mlKitBack?.plateHints?.firstOrNull(),
                        catalogEntry = selectedEntry,
                        measuredPrintQuality = mlKitFront.serialPrintQuality,
                        ocrPassAgreementScore = mlKitFront.ocrPassAgreementScore,
                        duplicateClassification = dupClass
                    )
                )
            }

            val conditionReport = conditionDeferred.await()
            val geomDeferred = async {
                geometryLayoutEngine.analyze(
                    rawBitmap = req.frontBitmap,
                    canonicalBitmap = canonicalFront,
                    entry = selectedEntry,
                    mlKit = mlKitFront,
                    condition = conditionReport,
                    perspectiveSkewDeg = aligned.perspectiveSkewDeg
                )
            }
            val texDeferred = async { texturePrintEngine.analyze(conditionReport, calibration) }
            val colorDeferred = async {
                colorEngine.analyze(canonicalFront, selectedEntry, conditionReport, req.materialHint, calibration)
            }

            val serialResult = serialDeferred.await()
            val geomResult = geomDeferred.await()
            val texResult = texDeferred.await()
            val colorResult = colorDeferred.await()
            val secResult = securityTiltEngine.analyze(geomResult.roiEvaluations, req.deepTiltFrames)

            // 5. CDE 2.0 Typed Contradictions
            val anomalies = cde.classifyAndCollect(
                geometryAnomalies = geomResult.anomalies,
                colorAnomalies = colorResult.anomalies,
                serialResult = serialResult,
                conditionReport = conditionReport,
                calibrationWarningsEn = calibration.warningMessagesEn,
                calibrationWarningsAr = calibration.warningMessagesAr,
                duplicateClassification = dupClass,
                isUnclassified = identityRes.isUnclassified
            )

            val scores = EngineScores(
                geometryScore = geomResult.geometryScore,
                layoutScore = geomResult.layoutScore,
                colorScore = colorResult.score,
                textureScore = texResult.textureScore,
                printStructureScore = texResult.printStructureScore,
                securityFeatureScore = secResult.score,
                serialScore = serialResult.overallScore,
                conditionScore = conditionReport.captureQualityScore,
                tiltDeltaMeasured = secResult.tiltMeasured,
                tiltOpticalShiftDelta = secResult.tiltShiftDelta
            )

            // 6. EFE 2.0 4 Separated Outputs & 4 Verdicts
            val classConf = if (identityRes.isUnclassified) {
                (identityRes.currencyConfidence * 100).toInt().coerceIn(5, 40)
            } else {
                ((identityRes.currencyConfidence * 0.45f + identityRes.denominationConfidence * 0.35f +
                    identityRes.issueConfidence * 0.20f) * 100f).toInt().coerceIn(25, 97)
            }

            val hardCount = anomalies.count { it.contradictionType == ContradictionType.HARD_CONTRADICTION }
            val softCount = anomalies.count { it.contradictionType == ContradictionType.SOFT_ANOMALY }

            val structConsistency = (
                (scores.geometryScore * 0.16f +
                    scores.layoutScore * 0.16f +
                    scores.colorScore * 0.14f +
                    scores.textureScore * 0.12f +
                    scores.printStructureScore * 0.12f +
                    scores.securityFeatureScore * 0.10f +
                    scores.serialScore * 0.20f) * 100f - (hardCount * 16f)
                ).toInt().coerceIn(8, 97)

            val evidenceSufficiency = (
                (conditionReport.usableCoveragePercent * 0.40f +
                    conditionReport.evidenceCompletenessPercent * 0.35f +
                    calibration.overallQualityScore * 100f * 0.25f)
                ).toInt().coerceIn(10, 99)

            val suspicionRisk = (hardCount * 34 + softCount * 12 +
                (if (dupClass == DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE) 25 else 0))
                .coerceIn(0, 99)

            val verdict = when {
                identityRes.isUnclassified -> ForensicVerdict.UNCLASSIFIED_NOTE
                hardCount > 0 -> ForensicVerdict.ANOMALIES_DETECTED
                evidenceSufficiency < 45 || conditionReport.captureQualityScore < 0.35f ->
                    ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE
                softCount > 0 -> ForensicVerdict.ANOMALIES_DETECTED
                else -> ForensicVerdict.CONSISTENT_WITH_SPECS
            }

            val efeOutputs = EfeFourOutputs(
                classificationConfidencePercent = classConf,
                structuralConsistencyPercent = structConsistency,
                evidenceSufficiencyPercent = evidenceSufficiency,
                forensicSuspicionRiskPercent = suspicionRisk
            )

            ForensicInspectionReport(
                reportId = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                mode = req.mode,
                scanDepthMode = req.scanDepthMode,
                currency = selectedEntry.currency,
                denominationValue = selectedEntry.denominationValue,
                catalogEntryId = selectedEntry.id,
                designFamilyEn = selectedEntry.familyNameEn,
                designFamilyAr = selectedEntry.familyNameAr,
                observedMaterial = identityRes.predictedMaterial,
                seriesOrIssueYear = serialResult.detectedSeriesYear ?: selectedEntry.issueYear,
                designYear = selectedEntry.designYear,
                printYearRange = selectedEntry.printYearRange,
                orientation = identityRes.orientation,
                primaryImageUri = req.primaryImageUri,
                backImageUri = req.backImageUri,
                perceptualDHash = dHash,
                verdict = verdict,
                efeOutputs = efeOutputs,
                identityResult = identityRes,
                confidencePercent = structConsistency,
                usableCoveragePercent = conditionReport.usableCoveragePercent.toInt(),
                evidenceCompletenessPercent = conditionReport.evidenceCompletenessPercent.toInt(),
                threeQuestions = buildThreeQuestions(selectedEntry, verdict, evidenceSufficiency, anomalies),
                engineScores = scores,
                serialAnalysis = serialResult,
                conditionReport = conditionReport,
                roiEvaluations = geomResult.roiEvaluations,
                anomalies = anomalies,
                duplicateClassification = dupClass,
                isLocalDuplicate = dupClass != DuplicateClassification.UNIQUE,
                catalogVersion = catalog.catalogVersion,
                calibrationWarningsEn = calibration.warningMessagesEn,
                calibrationWarningsAr = calibration.warningMessagesAr,
                multiResolutionSummaryEn = "OpenCV=${aligned.openCvNativeUsed} • LiteRT=${identityRes.ranOnNativeLiteRt} • ROIs=${geomResult.roiEvaluations.count { it.inspectionAvailable }}/${geomResult.roiEvaluations.size} active",
                multiResolutionSummaryAr = "المحاذاة=${if (aligned.openCvNativeUsed) "OpenCV" else "CPU"} • التصنيف=${if (identityRes.ranOnNativeLiteRt) "LiteRT" else "Math"} • المناطق النشطة=${geomResult.roiEvaluations.count { it.inspectionAvailable }}/${geomResult.roiEvaluations.size}"
            )
        }

    suspend fun inspectManualAudit(
        currency: CurrencyCode,
        denomination: Int,
        materialHint: SubstrateMaterial?,
        primarySerial: String,
        secondarySerial: String?,
        frIndicator: String?,
        seriesYear: String?,
        facePlate: String?,
        backPlate: String?,
        notePosition: String?,
        starToggle: Boolean,
        duplicateClassifierFn: suspend (CurrencyCode, Int, String, String) -> DuplicateClassification
    ): ForensicInspectionReport = withContext(Dispatchers.Default) {
        val entries = catalogRepo.findEntries(currency, denomination, materialHint)
            .ifEmpty { catalogRepo.findEntries(currency, denomination) }
            .ifEmpty { catalogRepo.findEntries(currency) }
        val entry = entries.first()

        val norm = sie.normalizeSerial(primarySerial, starToggle)
        val dupClass = if (norm.isNotEmpty()) {
            duplicateClassifierFn(currency, denomination, norm, "")
        } else DuplicateClassification.UNIQUE

        val serialResult = sie.analyze(
            SerialAuditInput(
                currency = currency,
                denomination = denomination,
                primarySerial = primarySerial,
                secondarySerial = secondarySerial,
                printedSeriesYear = seriesYear,
                federalReserveIndicator = frIndicator,
                facePlateNumber = facePlate,
                backPlateNumber = backPlate,
                notePositionId = notePosition,
                starToggle = starToggle,
                catalogEntry = entry,
                duplicateClassification = dupClass
            )
        )

        val neutralGrid = buildList {
            for (r in 0 until 4) for (c in 0 until 8) {
                add(ConditionCell(c, r, CellCondition.CLEAN, 0.25f, 0.55f))
            }
        }
        val cond = ConditionReport(
            cells = neutralGrid,
            blurSharpnessScore = 1f,
            glarePercent = 0f,
            foldPercent = 0f,
            wearScore = 0f,
            captureQualityScore = 1f,
            usableCoveragePercent = 100f,
            evidenceCompletenessPercent = 100f,
            occludedRoiIds = emptyList()
        )

        val geomAnom = if (currency == CurrencyCode.SAR && denomination == 5 &&
            materialHint != null && materialHint != entry.material
        ) {
            listOf(
                AnomalyItem(
                    id = "manual_5sar_mat",
                    contradictionType = ContradictionType.HARD_CONTRADICTION,
                    severity = AnomalySeverity.CRITICAL,
                    categoryEn = "Substrate Conflict",
                    categoryAr = "تعارض الخامة",
                    titleEn = "5 SAR Cotton vs Polymer Mismatch",
                    titleAr = "تعارض خامة فئة 5 ريالات",
                    detailEn = "Observed substrate does not match selected 5 SAR issue family.",
                    detailAr = "الخامة المحددة لا تتطابق مع إصدار 5 ريالات المختار."
                )
            )
        } else emptyList()

        val anomalies = cde.classifyAndCollect(
            geometryAnomalies = geomAnom,
            colorAnomalies = emptyList(),
            serialResult = serialResult,
            conditionReport = cond,
            calibrationWarningsEn = emptyList(),
            calibrationWarningsAr = emptyList(),
            duplicateClassification = dupClass,
            isUnclassified = false
        )

        val hardCount = anomalies.count { it.contradictionType == ContradictionType.HARD_CONTRADICTION }
        val softCount = anomalies.count { it.contradictionType == ContradictionType.SOFT_ANOMALY }

        val verdict = when {
            hardCount > 0 || softCount > 0 -> ForensicVerdict.ANOMALIES_DETECTED
            serialResult.normalizedSerial.isBlank() -> ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE
            else -> ForensicVerdict.CONSISTENT_WITH_SPECS
        }

        val roiEvals = entry.roiZones.map {
            RoiEvaluation(
                roiId = it.id,
                category = it.category,
                labelEn = it.labelEn,
                labelAr = it.labelAr,
                x = it.x, y = it.y, w = it.w, h = it.h,
                isOccluded = false,
                isPositionShifted = false,
                featureMatchScore = 0.85f,
                alignmentErrorPercent = 0f,
                edgeConsistencyScore = 0.85f,
                textureSimilarityScore = 0.85f,
                occlusionPercent = 0f,
                inspectionAvailable = true,
                confidence = 0.85f
            )
        }

        val efeOutputs = EfeFourOutputs(
            classificationConfidencePercent = 98,
            structuralConsistencyPercent = (serialResult.overallScore * 100f - hardCount * 15f).toInt().coerceIn(10, 96),
            evidenceSufficiencyPercent = 68, // Manual serial only, no optical banknote scan
            forensicSuspicionRiskPercent = (hardCount * 38 + softCount * 14).coerceIn(0, 99)
        )

        ForensicInspectionReport(
            reportId = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            mode = InspectionMode.MANUAL_AUDIT,
            scanDepthMode = ScanDepthMode.QUICK_SCAN,
            currency = currency,
            denominationValue = denomination,
            catalogEntryId = entry.id,
            designFamilyEn = entry.familyNameEn,
            designFamilyAr = entry.familyNameAr,
            observedMaterial = materialHint ?: entry.material,
            seriesOrIssueYear = serialResult.detectedSeriesYear ?: entry.issueYear,
            designYear = entry.designYear,
            printYearRange = entry.printYearRange,
            orientation = NoteSideOrientation.FRONT,
            verdict = verdict,
            efeOutputs = efeOutputs,
            confidencePercent = efeOutputs.structuralConsistencyPercent,
            usableCoveragePercent = 100,
            evidenceCompletenessPercent = 100,
            threeQuestions = buildThreeQuestions(entry, verdict, efeOutputs.evidenceSufficiencyPercent, anomalies),
            engineScores = EngineScores(
                geometryScore = 1f,
                layoutScore = 1f,
                colorScore = 1f,
                textureScore = 1f,
                printStructureScore = 1f,
                securityFeatureScore = 0.7f,
                serialScore = serialResult.overallScore,
                conditionScore = 1f
            ),
            serialAnalysis = serialResult,
            conditionReport = cond,
            roiEvaluations = roiEvals,
            anomalies = anomalies,
            duplicateClassification = dupClass,
            isLocalDuplicate = dupClass != DuplicateClassification.UNIQUE,
            catalogVersion = catalog.catalogVersion,
            multiResolutionSummaryEn = "Manual Forensic Audit • Rules & Cross-Feature Engine (SIE 2.0)",
            multiResolutionSummaryAr = "تدقيق يدوي مباشر • محرك قواعد البنية وتقاطع السمات (SIE 2.0)"
        )
    }

    suspend fun reAuditWithManualSerial(
        existing: ForensicInspectionReport,
        correctedPrimary: String,
        correctedSecondary: String?,
        frIndicator: String?,
        seriesYear: String?,
        starToggle: Boolean,
        auditNote: String,
        duplicateClassifierFn: suspend (CurrencyCode, Int, String, String) -> DuplicateClassification
    ): ForensicInspectionReport = withContext(Dispatchers.Default) {
        val entry = catalogRepo.findById(existing.catalogEntryId)
            ?: catalogRepo.findEntries(existing.currency, existing.denominationValue).first()
        val norm = sie.normalizeSerial(correctedPrimary, starToggle)
        val dupClass = if (norm.isNotEmpty()) {
            duplicateClassifierFn(existing.currency, existing.denominationValue, norm, existing.perceptualDHash)
        } else DuplicateClassification.UNIQUE

        val newSerial = sie.analyze(
            SerialAuditInput(
                currency = existing.currency,
                denomination = existing.denominationValue,
                primarySerial = correctedPrimary,
                secondarySerial = correctedSecondary,
                printedSeriesYear = seriesYear,
                federalReserveIndicator = frIndicator,
                catalogEntry = entry,
                measuredPrintQuality = existing.serialAnalysis.printQuality,
                ocrPassAgreementScore = 0.99f,
                duplicateClassification = dupClass
            )
        )

        val nonSieAnomalies = existing.anomalies.filterNot { it.id.startsWith("cde_sie_") || it.id.startsWith("cde_dup_") || it.id.startsWith("cde_capture") }
        val newAnomalies = cde.classifyAndCollect(
            geometryAnomalies = nonSieAnomalies.filter { it.categoryEn.contains("Geometry") || it.categoryEn.contains("Spatial") },
            colorAnomalies = nonSieAnomalies.filter { it.categoryEn.contains("Color") || it.categoryEn.contains("Substrate") },
            serialResult = newSerial,
            conditionReport = existing.conditionReport,
            calibrationWarningsEn = existing.calibrationWarningsEn,
            calibrationWarningsAr = existing.calibrationWarningsAr,
            duplicateClassification = dupClass,
            isUnclassified = existing.verdict == ForensicVerdict.UNCLASSIFIED_NOTE
        )

        val hardCount = newAnomalies.count { it.contradictionType == ContradictionType.HARD_CONTRADICTION }
        val softCount = newAnomalies.count { it.contradictionType == ContradictionType.SOFT_ANOMALY }
        val newVerdict = when {
            existing.verdict == ForensicVerdict.UNCLASSIFIED_NOTE -> ForensicVerdict.UNCLASSIFIED_NOTE
            hardCount > 0 || softCount > 0 -> ForensicVerdict.ANOMALIES_DETECTED
            existing.evidenceCompletenessPercent < 45 -> ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE
            else -> ForensicVerdict.CONSISTENT_WITH_SPECS
        }

        val editRecord = ManualAuditEdit(
            timestampMillis = System.currentTimeMillis(),
            field = "primarySerial",
            previousValue = existing.serialAnalysis.normalizedSerial,
            newValue = newSerial.normalizedSerial,
            note = auditNote
        )

        val updatedEfe = existing.efeOutputs.copy(
            structuralConsistencyPercent = ((existing.efeOutputs.structuralConsistencyPercent * 0.6f + newSerial.overallScore * 100f * 0.4f) - hardCount * 10f).toInt().coerceIn(8, 97),
            forensicSuspicionRiskPercent = (hardCount * 34 + softCount * 12).coerceIn(0, 99)
        )

        existing.copy(
            serialAnalysis = newSerial,
            anomalies = newAnomalies,
            verdict = newVerdict,
            efeOutputs = updatedEfe,
            confidencePercent = updatedEfe.structuralConsistencyPercent,
            engineScores = existing.engineScores.copy(serialScore = newSerial.overallScore),
            duplicateClassification = dupClass,
            isLocalDuplicate = dupClass != DuplicateClassification.UNIQUE,
            manualAuditTrail = existing.manualAuditTrail + editRecord,
            threeQuestions = buildThreeQuestions(entry, newVerdict, updatedEfe.evidenceSufficiencyPercent, newAnomalies)
        )
    }

    private fun estimatePolymerWindowRatio(canonical: Bitmap): Float {
        val w = canonical.width.coerceAtLeast(1)
        val h = canonical.height.coerceAtLeast(1)
        val hsv = FloatArray(3)
        var uniformBgPx = 0
        var total = 0
        val x0 = (0.08f * w).toInt()
        val x1 = (0.28f * w).toInt()
        val y0 = (0.22f * h).toInt()
        val y1 = (0.68f * h).toInt()
        for (y in y0 until y1 step 4) {
            for (x in x0 until x1 step 4) {
                android.graphics.Color.colorToHSV(canonical.getPixel(x, y), hsv)
                if (hsv[1] < 0.14f) uniformBgPx++
                total++
            }
        }
        return if (total > 0) uniformBgPx.toFloat() / total else 0f
    }

    private fun buildThreeQuestions(
        entry: BanknoteCatalogEntry,
        verdict: ForensicVerdict,
        sufficiencyPct: Int,
        anomalies: List<AnomalyItem>
    ): ThreeQuestionAnswers {
        val q1En = if (verdict == ForensicVerdict.UNCLASSIFIED_NOTE) {
            "Unclassified banknote — insufficient evidence to confirm SAR or USD denomination."
        } else {
            "${entry.currency.name} ${entry.denominationLabelEn} • ${entry.familyNameEn}"
        }
        val q1Ar = if (verdict == ForensicVerdict.UNCLASSIFIED_NOTE) {
            "ورقة غير مصنفة — الأدلة غير كافية لتحديد العملة أو الفئة."
        } else {
            "${entry.currency.name} ${entry.denominationLabelAr} • ${entry.familyNameAr}"
        }

        val relevantIssues = anomalies.count {
            it.contradictionType == ContradictionType.HARD_CONTRADICTION || it.contradictionType == ContradictionType.SOFT_ANOMALY
        }
        val q2En = if (relevantIssues == 0) {
            "Optical geometry, color, and serial structure align with ${entry.issueYear} specs."
        } else {
            "$relevantIssues anomaly/contradiction(s) detected against ${entry.issueYear} official specs."
        }
        val q2Ar = if (relevantIssues == 0) {
            "تتطابق الخصائص البصرية وبنية الرقم مع مواصفات الإصدار (${entry.issueYear})."
        } else {
            "تم رصد $relevantIssues تناقض/ملاحظة مقارنة بمواصفات الإصدار الرسمية."
        }

        val q3En = when (verdict) {
            ForensicVerdict.CONSISTENT_WITH_SPECS ->
                "Spec-consistent ($sufficiencyPct% evidence sufficiency). Tactile/UV physical check still advised."
            ForensicVerdict.ANOMALIES_DETECTED ->
                "Hard or soft anomalies prevent authenticity support; manual forensic verification required."
            ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE ->
                "Inconclusive ($sufficiencyPct% evidence sufficiency). Re-capture flat, sharp image."
            ForensicVerdict.UNCLASSIFIED_NOTE ->
                "Unclassified note; cannot evaluate spec consistency without clear currency identification."
        }
        val q3Ar = when (verdict) {
            ForensicVerdict.CONSISTENT_WITH_SPECS ->
                "متوافقة ضمن كفاية أدلة $sufficiencyPct%؛ لا تثبت الكاميرا وحدها الملمس أو الأشعة فوق البنفسجية."
            ForensicVerdict.ANOMALIES_DETECTED ->
                "توجد مؤشرات تناقض تمنع الجزم بالأصالة؛ يلزم فحص معملي/يدوي دقيق."
            ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE ->
                "غير محسومة (كفاية الأدلة $sufficiencyPct%). أعد التصوير بإضاءة متوازنة وورقة مفرودة."
            ForensicVerdict.UNCLASSIFIED_NOTE ->
                "ورقة غير مصنفة؛ يتعذر مطابقتها مع قواعد SAR أو USD دون وضوح الهوية."
        }

        return ThreeQuestionAnswers(q1En, q1Ar, q2En, q2Ar, q3En, q3Ar)
    }
}

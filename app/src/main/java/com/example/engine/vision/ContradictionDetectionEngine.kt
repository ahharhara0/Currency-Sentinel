package com.example.engine.vision

import com.example.engine.serial.DuplicateClassification
import com.example.engine.serial.SerialAnalysisResult

class ContradictionDetectionEngine {

    fun classifyAndCollect(
        geometryAnomalies: List<AnomalyItem>,
        colorAnomalies: List<AnomalyItem>,
        serialResult: SerialAnalysisResult,
        conditionReport: ConditionReport,
        calibrationWarningsEn: List<String>,
        calibrationWarningsAr: List<String>,
        duplicateClassification: DuplicateClassification,
        isUnclassified: Boolean
    ): List<AnomalyItem> {
        val out = mutableListOf<AnomalyItem>()
        out += geometryAnomalies
        out += colorAnomalies

        if (isUnclassified) {
            out += AnomalyItem(
                id = "cde_unclassified",
                contradictionType = ContradictionType.UNKNOWN,
                severity = AnomalySeverity.WARNING,
                categoryEn = "Identity Uncertainty",
                categoryAr = "هوية غير محددة",
                titleEn = "Banknote Identity Unclassified",
                titleAr = "تعذر تصنيف الورقة بدقة كافية",
                detailEn = "Evidence did not cross classification threshold for documented SAR or USD profiles.",
                detailAr = "الأدلة البصرية لم تتجاوز حد التصنيف الموثق لفئات SAR أو USD."
            )
        }

        serialResult.anomaliesEn.forEachIndexed { idx, en ->
            val ar = serialResult.anomaliesAr.getOrElse(idx) { en }
            val isCritical = serialResult.hasCriticalContradiction || en.contains("Duplicate Intelligence", ignoreCase = true)
            out += AnomalyItem(
                id = "cde_sie_$idx",
                contradictionType = if (isCritical) ContradictionType.HARD_CONTRADICTION else ContradictionType.SOFT_ANOMALY,
                severity = if (isCritical) AnomalySeverity.CRITICAL else AnomalySeverity.WARNING,
                categoryEn = "Serial Intelligence (SIE 2.0)",
                categoryAr = "ذكاء الأرقام التسلسلية",
                titleEn = if (isCritical) "Hard Rule / Cross-Check Contradiction" else "Serial Structure Warning",
                titleAr = if (isCritical) "تناقض حاد في القواعد أو المطابقة" else "تنبيه في بنية الرقم التسلسلي",
                detailEn = en,
                detailAr = ar
            )
        }

        when (duplicateClassification) {
            DuplicateClassification.SAME_NOTE_RESCANNED -> {
                out += AnomalyItem(
                    id = "cde_dup_same_note",
                    contradictionType = ContradictionType.SOFT_ANOMALY,
                    severity = AnomalySeverity.INFO,
                    categoryEn = "Duplicate Intelligence",
                    categoryAr = "ذكاء التكرار المحلي",
                    titleEn = "Same Note Re-Scanned",
                    titleAr = "إعادة فحص لنفس الورقة",
                    detailEn = "Serial & perceptual image hash match a prior scan in your Vault (likely re-inspection).",
                    detailAr = "يتطابق الرقم التسلسلي مع البصمة البصرية لفحص سابق (إعادة فحص لنفس الورقة)."
                )
            }
            DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE -> {
                if (out.none { it.detailEn.contains("Duplicate Intelligence") }) {
                    out += AnomalyItem(
                        id = "cde_dup_cloned",
                        contradictionType = ContradictionType.HARD_CONTRADICTION,
                        severity = AnomalySeverity.CRITICAL,
                        categoryEn = "Duplicate Intelligence",
                        categoryAr = "ذكاء التكرار المحلي",
                        titleEn = "Same Serial on Different Note Image",
                        titleAr = "نفس الرقم على صورة ورقة مختلفة!",
                        detailEn = "Vault contains this serial under a materially different visual perceptual hash.",
                        detailAr = "يحتوي السجل على نفس الرقم التسلسلي مرتبطاً ببصمة بصرية مختلفة."
                    )
                }
            }
            DuplicateClassification.POSSIBLE_DUPLICATE,
            DuplicateClassification.INSUFFICIENT_COMPARISON -> {
                out += AnomalyItem(
                    id = "cde_dup_possible",
                    contradictionType = ContradictionType.SOFT_ANOMALY,
                    severity = AnomalySeverity.WARNING,
                    categoryEn = "Duplicate Intelligence",
                    categoryAr = "ذكاء التكرار المحلي",
                    titleEn = "Prior Serial Match in Vault",
                    titleAr = "تطابق رقم سابق في السجل",
                    detailEn = "This serial exists in Local Vault; visual comparison inconclusive or partially different.",
                    detailAr = "الرقم مسجل مسبقاً في السجل وتتعذر مطابقته البصرية بالكامل."
                )
            }
            DuplicateClassification.UNIQUE -> Unit
        }

        if (conditionReport.usableCoveragePercent < 55f) {
            out += AnomalyItem(
                id = "cde_capture_coverage",
                contradictionType = ContradictionType.CAPTURE_ARTIFACT,
                severity = AnomalySeverity.INFO,
                categoryEn = "Condition & Coverage (CEF 2.0)",
                categoryAr = "تغطية وجودة الفحص",
                titleEn = "Reduced Unoccluded Area (${conditionReport.usableCoveragePercent.toInt()}%)",
                titleAr = "انخفاض المساحة الصالحة للفحص (${conditionReport.usableCoveragePercent.toInt()}%)",
                detailEn = "Folds, glare, blur, or dark zones hide ${conditionReport.occludedRoiIds.size} security zones; occluded zones excluded from forgery penalties.",
                detailAr = "تحجب الطيات أو الوهج ${conditionReport.occludedRoiIds.size} منطقة أمنية؛ وتم استبعادها حتى لا تُفسَّر كأخطاء تزوير."
            )
        }

        calibrationWarningsEn.forEachIndexed { idx, wEn ->
            val wAr = calibrationWarningsAr.getOrElse(idx) { wEn }
            out += AnomalyItem(
                id = "cde_calib_$idx",
                contradictionType = ContradictionType.CAPTURE_ARTIFACT,
                severity = AnomalySeverity.INFO,
                categoryEn = "Capture Calibration",
                categoryAr = "معايرة التصوير",
                titleEn = "Optical Capture Advisory",
                titleAr = "تنبيه ظروف التصوير",
                detailEn = wEn,
                detailAr = wAr
            )
        }

        return out
    }
}

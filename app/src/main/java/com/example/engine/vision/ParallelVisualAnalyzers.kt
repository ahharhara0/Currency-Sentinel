package com.example.engine.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.cv.CaptureCalibrationReport
import com.example.engine.cv.LiteRtForensicModels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ConditionEngine(private val liteRtModels: LiteRtForensicModels? = null) {

    suspend fun analyze(
        canonicalBitmap: Bitmap,
        entry: BanknoteCatalogEntry?,
        calibration: CaptureCalibrationReport?
    ): ConditionReport {
        val scaled = Bitmap.createScaledBitmap(canonicalBitmap, 240, 120, true)
        val w = scaled.width
        val h = scaled.height
        val cols = 8
        val rows = 4
        val cellW = w / cols
        val cellH = h / rows

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val cells = mutableListOf<ConditionCell>()
        var glareCells = 0
        var foldCells = 0
        var wornCells = 0
        var stainCells = 0
        var blurCells = 0
        var darkCells = 0
        var totalLaplacian = 0.0
        var lapSamples = 0

        val hsv = FloatArray(3)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * cellW
                val y0 = r * cellH
                var brightSum = 0f
                var satSum = 0f
                var glarePx = 0
                var darkPx = 0
                var stainPx = 0
                var sharpEdges = 0
                var directionalFoldHits = 0
                var count = 0

                for (y in y0 + 1 until (y0 + cellH - 1)) {
                    for (x in x0 + 1 until (x0 + cellW - 1)) {
                        val p = pixels[y * w + x]
                        Color.colorToHSV(p, hsv)
                        val hue = hsv[0]
                        val s = hsv[1]
                        val v = hsv[2]
                        brightSum += v
                        satSum += s
                        if (v > 0.94f && s < 0.10f) glarePx++
                        if (v < 0.13f) darkPx++
                        if (s > 0.45f && v < 0.35f && (hue in 18f..45f)) stainPx++

                        val lum = luminance(p)
                        val lumR = luminance(pixels[y * w + x + 1])
                        val lumL = luminance(pixels[y * w + x - 1])
                        val lumD = luminance(pixels[(y + 1) * w + x])
                        val lumU = luminance(pixels[(y - 1) * w + x])
                        val lap = abs(4 * lum - lumR - lumL - lumD - lumU)
                        totalLaplacian += lap
                        lapSamples++

                        if (lap > 36) sharpEdges++
                        if (abs(lumL - lumR) > 48 && abs(lumU - lumD) < 12) directionalFoldHits++
                        count++
                    }
                }

                val safeCount = count.coerceAtLeast(1)
                val meanV = brightSum / safeCount
                val meanS = satSum / safeCount
                val glareRatio = glarePx.toFloat() / safeCount
                val darkRatio = darkPx.toFloat() / safeCount
                val stainRatio = stainPx.toFloat() / safeCount
                val edgeDensity = sharpEdges.toFloat() / safeCount
                val foldRatio = directionalFoldHits.toFloat() / safeCount

                val cellCond = when {
                    glareRatio > 0.28f -> {
                        glareCells++
                        CellCondition.GLARE_OVEREXPOSED
                    }
                    darkRatio > 0.45f -> {
                        darkCells++
                        CellCondition.DARK_OCCLUDED
                    }
                    foldRatio > 0.14f -> {
                        foldCells++
                        CellCondition.FOLD_CREASE
                    }
                    stainRatio > 0.36f -> {
                        stainCells++
                        CellCondition.STAIN_DAMAGE
                    }
                    edgeDensity < 0.045f && meanV in 0.20f..0.88f -> {
                        blurCells++
                        CellCondition.BLUR_DEFOCUS
                    }
                    meanS < 0.11f && edgeDensity < 0.09f -> {
                        wornCells++
                        CellCondition.WORN_FADED
                    }
                    else -> CellCondition.CLEAN
                }

                cells += ConditionCell(c, r, cellCond, edgeDensity, meanV)
            }
        }

        val totalCells = (cols * rows).toFloat()
        val usableCells = cells.count {
            it.condition == CellCondition.CLEAN || it.condition == CellCondition.WORN_FADED
        }
        val usableCoverage = ((usableCells / totalCells) * 100f).coerceIn(0f, 100f)
        val glarePct = (glareCells / totalCells) * 100f
        val foldPct = (foldCells / totalCells) * 100f
        val stainPct = (stainCells / totalCells) * 100f
        val blurCellPct = (blurCells / totalCells) * 100f
        val wearScore = (wornCells / totalCells).coerceIn(0f, 1f)
        val avgLap = (totalLaplacian / lapSamples.coerceAtLeast(1)).toFloat()
        val blurScore = calibration?.blurSharpnessScore ?: (avgLap / 26f).coerceIn(0.15f, 1f)
        val captureQuality = calibration?.overallQualityScore
            ?: ((blurScore * 0.55f) + ((1f - glareCells / totalCells) * 0.25f) +
                ((1f - darkCells / totalCells) * 0.20f)).coerceIn(0.1f, 1f)

        val occludedIds = mutableListOf<String>()
        val totalRois = entry?.roiZones?.size ?: 0
        entry?.roiZones?.forEach { roi ->
            val roiCol = ((roi.x + roi.w / 2f) * cols).toInt().coerceIn(0, cols - 1)
            val roiRow = ((roi.y + roi.h / 2f) * rows).toInt().coerceIn(0, rows - 1)
            val cell = cells.firstOrNull { it.col == roiCol && it.row == roiRow }
            if (cell != null && (cell.condition == CellCondition.FOLD_CREASE ||
                    cell.condition == CellCondition.GLARE_OVEREXPOSED ||
                    cell.condition == CellCondition.BLUR_DEFOCUS ||
                    cell.condition == CellCondition.STAIN_DAMAGE ||
                    cell.condition == CellCondition.DARK_OCCLUDED)
            ) {
                occludedIds += roi.id
            }
        }
        val evidenceCompleteness = if (totalRois > 0) {
            (((totalRois - occludedIds.size).toFloat() / totalRois) * 100f).coerceIn(0f, 100f)
        } else usableCoverage

        // LiteRT quality MLP inference
        val vec8 = floatArrayOf(
            blurScore,
            glarePct / 100f,
            foldPct / 100f,
            wearScore,
            calibration?.exposureScore ?: 0.7f,
            calibration?.whiteBalanceScore ?: 0.75f,
            usableCoverage / 100f,
            1f - (darkCells / totalCells)
        )
        val qPred = liteRtModels?.predictConditionQuality(vec8)
        val sig = cells.joinToString("") { it.condition.ordinal.toString() }

        if (scaled != canonicalBitmap) scaled.recycle()

        return ConditionReport(
            gridCols = cols,
            gridRows = rows,
            cells = cells,
            blurSharpnessScore = blurScore,
            glarePercent = glarePct,
            foldPercent = foldPct,
            stainPercent = stainPct,
            blurCellPercent = blurCellPct,
            wearScore = wearScore,
            captureQualityScore = captureQuality,
            usableCoveragePercent = usableCoverage,
            evidenceCompletenessPercent = evidenceCompleteness,
            occludedRoiIds = occludedIds,
            liteRtCleanProb = qPred?.cleanUsableProb ?: 0.7f,
            liteRtWornProb = qPred?.wornTolerableProb ?: 0.2f,
            liteRtCompromisedProb = qPred?.compromisedOccludedProb ?: 0.1f,
            conditionSignature = sig
        )
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}

class GeometryAndLayoutEngine {

    data class GeometryLayoutResult(
        val geometryScore: Float,
        val layoutScore: Float,
        val observedAspectRatio: Float,
        val expectedAspectRatio: Float,
        val aspectDeviationPercent: Float,
        val roiEvaluations: List<RoiEvaluation>,
        val anomalies: List<AnomalyItem>
    )

    fun analyze(
        rawBitmap: Bitmap,
        canonicalBitmap: Bitmap,
        entry: BanknoteCatalogEntry,
        mlKit: MlKitExtractionResult,
        condition: ConditionReport,
        perspectiveSkewDeg: Float
    ): GeometryLayoutResult {
        val w = max(rawBitmap.width, rawBitmap.height).toFloat()
        val h = min(rawBitmap.width, rawBitmap.height).toFloat().coerceAtLeast(1f)
        val observedRatio = w / h
        val expectedRatio = entry.officialAspectRatio
        val deviationPct = (abs(observedRatio - expectedRatio) / expectedRatio) * 100f

        val anomalies = mutableListOf<AnomalyItem>()
        var geomScore = 1f
        var layoutScore = 1f

        if (perspectiveSkewDeg > 20f) {
            anomalies += AnomalyItem(
                id = "capture_skew",
                contradictionType = ContradictionType.CAPTURE_ARTIFACT,
                severity = AnomalySeverity.INFO,
                categoryEn = "Capture Perspective",
                categoryAr = "زاوية التصوير",
                titleEn = "Oblique Capture Angle (${perspectiveSkewDeg.toInt()}°)",
                titleAr = "زاوية تصوير مائلة (${perspectiveSkewDeg.toInt()}°)",
                detailEn = "OpenCV perspective warp corrected framing; minor edge tolerance applied.",
                detailAr = "صحّح المحرك المنظور هندسياً؛ مع مراعاة زاوية الميل في قياس الأبعاد."
            )
            geomScore -= 0.08f
        } else if (deviationPct > 28f) {
            geomScore -= 0.35f
            anomalies += AnomalyItem(
                id = "geom_aspect",
                contradictionType = ContradictionType.SOFT_ANOMALY,
                severity = AnomalySeverity.WARNING,
                categoryEn = "Geometry & Proportions",
                categoryAr = "الهندسة والأبعاد",
                titleEn = "Aspect Ratio Deviation (${deviationPct.toInt()}%)",
                titleAr = "انحراف في نسبة الأبعاد (${deviationPct.toInt()}%)",
                detailEn = "Observed frame ratio ${"%.2f".format(observedRatio)} differs from ${entry.widthMm}×${entry.heightMm}mm (${"%.2f".format(expectedRatio)}).",
                detailAr = "النسبة المرصودة ${"%.2f".format(observedRatio)} تختلف عن الأبعاد الرسمية ${entry.widthMm}×${entry.heightMm} مم."
            )
        } else if (deviationPct > 14f) {
            geomScore -= 0.14f
        }

        val roiEvals = entry.roiZones.map { roi ->
            val roiCol = ((roi.x + roi.w / 2f) * condition.gridCols).toInt().coerceIn(0, condition.gridCols - 1)
            val roiRow = ((roi.y + roi.h / 2f) * condition.gridRows).toInt().coerceIn(0, condition.gridRows - 1)
            val cell = condition.cells.firstOrNull { it.col == roiCol && it.row == roiRow }
            val occluded = roi.id in condition.occludedRoiIds
            val occlusionPct = when (cell?.condition) {
                CellCondition.DARK_OCCLUDED, CellCondition.GLARE_OVEREXPOSED -> 88f
                CellCondition.FOLD_CREASE, CellCondition.BLUR_DEFOCUS, CellCondition.STAIN_DAMAGE -> 64f
                CellCondition.WORN_FADED -> 22f
                else -> 4f
            }

            var shifted = false
            var alignErrPct = 4.5f
            if (!occluded && roi.id.startsWith("serial") && mlKit.serialCandidates.isNotEmpty()) {
                val nearest = mlKit.serialCandidates.minByOrNull { cand ->
                    val cx = cand.normX + cand.normW / 2f
                    val cy = cand.normY + cand.normH / 2f
                    abs(cx - (roi.x + roi.w / 2f)) + abs(cy - (roi.y + roi.h / 2f))
                }
                if (nearest != null) {
                    val dist = abs((nearest.normX + nearest.normW / 2f) - (roi.x + roi.w / 2f)) +
                        abs((nearest.normY + nearest.normH / 2f) - (roi.y + roi.h / 2f))
                    alignErrPct = (dist * 100f).coerceIn(1f, 95f)
                    if (dist > 0.42f) shifted = true
                }
            }

            if (shifted) {
                layoutScore -= 0.30f
                anomalies += AnomalyItem(
                    id = "roi_shift_${roi.id}",
                    contradictionType = ContradictionType.HARD_CONTRADICTION,
                    severity = AnomalySeverity.CRITICAL,
                    categoryEn = "Spatial Element Shift",
                    categoryAr = "تغير موضع عنصر أمني",
                    titleEn = "Shifted Region: ${roi.labelEn}",
                    titleAr = "انزياح موضع: ${roi.labelAr}",
                    detailEn = "Element coordinates deviate from ${entry.familyNameEn} official template (${alignErrPct.toInt()}% drift).",
                    detailAr = "انحرف موضع العنصر بنسبة ${alignErrPct.toInt()}% مقارنة بالقالب الرسمي (${entry.familyNameAr})."
                )
            }

            val edgeCons = ((cell?.edgeDensity ?: 0.15f) / 0.22f).coerceIn(0.25f, 0.98f)
            val texSim = if (occluded) 0f else ((edgeCons * 0.7f) + (1f - alignErrPct / 150f) * 0.3f).coerceIn(0.2f, 0.97f)
            val featMatch = if (occluded) 0f else if (shifted) 0.28f else ((1f - alignErrPct / 120f) * 0.6f + edgeCons * 0.4f).coerceIn(0.3f, 0.96f)

            RoiEvaluation(
                roiId = roi.id,
                category = roi.category,
                labelEn = roi.labelEn,
                labelAr = roi.labelAr,
                x = roi.x,
                y = roi.y,
                w = roi.w,
                h = roi.h,
                isOccluded = occluded,
                isPositionShifted = shifted,
                featureMatchScore = featMatch,
                alignmentErrorPercent = alignErrPct,
                edgeConsistencyScore = edgeCons,
                textureSimilarityScore = texSim,
                occlusionPercent = occlusionPct,
                inspectionAvailable = !occluded,
                confidence = if (occluded) 0f else featMatch
            )
        }

        return GeometryLayoutResult(
            geometryScore = geomScore.coerceIn(0.15f, 1f),
            layoutScore = layoutScore.coerceIn(0.15f, 1f),
            observedAspectRatio = observedRatio,
            expectedAspectRatio = expectedRatio,
            aspectDeviationPercent = deviationPct,
            roiEvaluations = roiEvals,
            anomalies = anomalies
        )
    }
}

class TextureAndPrintEngine {

    data class TexturePrintResult(
        val textureScore: Float,
        val printStructureScore: Float
    )

    fun analyze(condition: ConditionReport, calibration: CaptureCalibrationReport?): TexturePrintResult {
        val cleanCells = condition.cells.filter { it.condition == CellCondition.CLEAN }
        if (cleanCells.isEmpty()) return TexturePrintResult(0.50f, 0.45f)
        val meanEdgeDensity = cleanCells.map { it.edgeDensity }.average().toFloat()
        val normalized = (meanEdgeDensity / 0.22f).coerceIn(0.35f, 0.98f)
        val texture = ((normalized * 0.7f) + (condition.blurSharpnessScore * 0.3f)).coerceIn(0.25f, 0.98f)
        val detailFactor = if (calibration?.isDetailSafe == false) 0.72f else 1.0f
        val printStructure = (condition.blurSharpnessScore * 0.65f + normalized * 0.35f) * detailFactor
        return TexturePrintResult(texture, printStructure.coerceIn(0.25f, 0.97f))
    }
}

class ColorEngine {

    data class ColorResult(
        val score: Float,
        val dominantHue: Float,
        val meanSaturation: Float,
        val polymerWindowDetected: Boolean,
        val anomalies: List<AnomalyItem>
    )

    fun analyze(
        canonicalBitmap: Bitmap,
        entry: BanknoteCatalogEntry,
        condition: ConditionReport,
        observedMaterialHint: SubstrateMaterial?,
        calibration: CaptureCalibrationReport?
    ): ColorResult {
        val scaled = Bitmap.createScaledBitmap(canonicalBitmap, 160, 80, true)
        val cols = condition.gridCols
        val rows = condition.gridRows
        val cellW = scaled.width / cols
        val cellH = scaled.height / rows

        val hueBins = IntArray(36)
        var satSum = 0f
        var validPixels = 0
        val hsv = FloatArray(3)

        for (cell in condition.cells) {
            if (cell.condition != CellCondition.CLEAN && cell.condition != CellCondition.WORN_FADED) {
                continue
            }
            val x0 = cell.col * cellW
            val y0 = cell.row * cellH
            for (y in y0 until (y0 + cellH)) {
                for (x in x0 until (x0 + cellW)) {
                    Color.colorToHSV(scaled.getPixel(x, y), hsv)
                    if (hsv[1] > 0.08f && hsv[2] in 0.15f..0.92f) {
                        val bin = ((hsv[0] / 10f).toInt()).coerceIn(0, 35)
                        hueBins[bin]++
                        satSum += hsv[1]
                        validPixels++
                    }
                }
            }
        }

        val topBin = hueBins.indices.maxByOrNull { hueBins[it] } ?: 0
        val dominantHue = topBin * 10f + 5f
        val meanSat = if (validPixels > 0) satSum / validPixels else 0.1f

        val hueMatched = isHueInRange(dominantHue, entry.dominantHueMin, entry.dominantHueMax)
        val colorSafe = calibration?.isColorSafe ?: true
        val anomalies = mutableListOf<AnomalyItem>()
        var score = when {
            hueMatched -> 0.92f
            !colorSafe -> 0.72f // WB drift or glare present -> widen tolerance
            else -> 0.56f
        }

        if (!hueMatched && validPixels > 120) {
            if (colorSafe) {
                anomalies += AnomalyItem(
                    id = "color_hue_mismatch",
                    contradictionType = ContradictionType.SOFT_ANOMALY,
                    severity = AnomalySeverity.WARNING,
                    categoryEn = "Color & Ink Profile",
                    categoryAr = "الألوان والصبغة",
                    titleEn = "Chromatic Mismatch for ${entry.denominationLabelEn}",
                    titleAr = "اختلاف لوني عن مواصفات ${entry.denominationLabelAr}",
                    detailEn = "Unoccluded ink hue (${dominantHue.toInt()}°) diverges from documented palette (${entry.dominantHueMin.toInt()}°–${entry.dominantHueMax.toInt()}°).",
                    detailAr = "تدرج اللون المرصود (${dominantHue.toInt()}°) يختلف عن النطاق الرسمي للإصدار."
                )
            } else {
                anomalies += AnomalyItem(
                    id = "color_wb_cast",
                    contradictionType = ContradictionType.CAPTURE_ARTIFACT,
                    severity = AnomalySeverity.INFO,
                    categoryEn = "Lighting / White Balance",
                    categoryAr = "توازن الإضاءة",
                    titleEn = "Ambient Light Color Cast",
                    titleAr = "انحراف لوني بسبب الإضاءة",
                    detailEn = "Observed hue shift (${dominantHue.toInt()}°) may reflect warm/cool lighting rather than counterfeit ink.",
                    detailAr = "قد يعود الاختلاف اللوني (${dominantHue.toInt()}°) إلى إضاءة المكان لا إلى حبر الورقة."
                )
            }
        }

        var polymerWindowFound = false
        if (entry.currency == CurrencyCode.SAR && entry.denominationValue == 5) {
            var uniformBgPx = 0
            var totalWinPx = 0
            val wx0 = (0.08f * scaled.width).toInt()
            val wx1 = (0.28f * scaled.width).toInt()
            val wy0 = (0.22f * scaled.height).toInt()
            val wy1 = (0.68f * scaled.height).toInt()
            for (y in wy0 until wy1) {
                for (x in wx0 until wx1) {
                    Color.colorToHSV(scaled.getPixel(x, y), hsv)
                    if (hsv[1] < 0.14f) uniformBgPx++
                    totalWinPx++
                }
            }
            polymerWindowFound = totalWinPx > 0 && (uniformBgPx.toFloat() / totalWinPx) > 0.52f

            if (observedMaterialHint == SubstrateMaterial.POLYMER &&
                entry.material == SubstrateMaterial.COTTON_LINEN
            ) {
                score -= 0.35f
                anomalies += AnomalyItem(
                    id = "sar5_material_mismatch",
                    contradictionType = ContradictionType.HARD_CONTRADICTION,
                    severity = AnomalySeverity.CRITICAL,
                    categoryEn = "Substrate Contradiction",
                    categoryAr = "تناقض مادة التصنيع",
                    titleEn = "5 SAR Polymer vs Cotton Issue Mismatch",
                    titleAr = "تناقض بين ورقة 5 ريالات القطنية والبوليمر",
                    detailEn = "Selected/detected Polymer substrate conflicts with 2016 Cotton 5 Riyal catalog profile.",
                    detailAr = "تم تحديد خامة بوليمر بينما الملف المرجعي يعود لإصدار القطن 2016."
                )
            }
        }

        if (scaled != canonicalBitmap) scaled.recycle()

        return ColorResult(
            score = score.coerceIn(0.2f, 0.98f),
            dominantHue = dominantHue,
            meanSaturation = meanSat,
            polymerWindowDetected = polymerWindowFound,
            anomalies = anomalies
        )
    }

    private fun isHueInRange(hue: Float, minH: Float, maxH: Float): Boolean {
        return if (minH <= maxH) {
            hue in (minH - 25f)..(maxH + 25f)
        } else {
            hue >= (minH - 25f) || hue <= (maxH + 25f)
        }
    }
}

class SecurityFeatureAndTiltEngine {

    data class SecurityResult(
        val score: Float,
        val tiltMeasured: Boolean,
        val tiltShiftDelta: Float
    )

    fun analyze(
        roiEvals: List<RoiEvaluation>,
        deepTiltFrames: List<Bitmap>
    ): SecurityResult {
        val securityRois = roiEvals.filter {
            it.category == com.example.catalog.RoiCategory.SECURITY_THREAD ||
                it.category == com.example.catalog.RoiCategory.WATERMARK ||
                it.category == com.example.catalog.RoiCategory.SEAL
        }
        val activeRois = securityRois.filter { it.inspectionAvailable }
        val baseScore = if (activeRois.isNotEmpty()) {
            activeRois.map { it.featureMatchScore }.average().toFloat()
        } else {
            0.55f
        }

        if (deepTiltFrames.size >= 2) {
            // Compute optical reflectance / hue shift across Deep Scan burst frames
            val meanHues = deepTiltFrames.map { computeCenterRoiHue(it) }
            val delta = ((meanHues.maxOrNull() ?: 0f) - (meanHues.minOrNull() ?: 0f))
            val normDelta = (delta / 45f).coerceIn(0.05f, 1f)
            val bonusScore = (baseScore * 0.75f + normDelta * 0.25f).coerceIn(0.35f, 0.97f)
            return SecurityResult(bonusScore, tiltMeasured = true, tiltShiftDelta = delta)
        }

        return SecurityResult(baseScore.coerceIn(0.30f, 0.94f), tiltMeasured = false, tiltShiftDelta = 0f)
    }

    private fun computeCenterRoiHue(bmp: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bmp, 64, 32, true)
        val hsv = FloatArray(3)
        var hueSum = 0f
        var count = 0
        for (y in 8 until 24) {
            for (x in 16 until 48) {
                Color.colorToHSV(small.getPixel(x, y), hsv)
                hueSum += hsv[0]
                count++
            }
        }
        if (small != bmp) small.recycle()
        return if (count > 0) hueSum / count else 0f
    }
}

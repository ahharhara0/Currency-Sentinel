package com.example.engine.cv

import android.graphics.Bitmap
import kotlin.math.abs

data class CaptureCalibrationReport(
    val exposureScore: Float,
    val whiteBalanceScore: Float,
    val blurSharpnessScore: Float,
    val glareRatio: Float,
    val perspectiveSkewDeg: Float,
    val resolutionSufficient: Boolean,
    val effectiveLongSidePx: Int,
    val isColorSafe: Boolean,
    val isDetailSafe: Boolean,
    val overallQualityScore: Float,
    val warningMessagesEn: List<String>,
    val warningMessagesAr: List<String>
)

class CaptureCalibrationEngine {

    fun calibrate(
        rawBitmap: Bitmap,
        alignedResult: AlignedNoteResult,
        stats: OpenCvVisualFeatures
    ): CaptureCalibrationReport {
        val longSide = maxOf(rawBitmap.width, rawBitmap.height)
        val resolutionSufficient = longSide >= 640

        // Ideal luminance is around 0.48..0.72
        val lumDist = abs(stats.meanLuminance - 0.56f)
        val exposureScore = (1f - lumDist * 1.9f).coerceIn(0.15f, 1f)

        val blurScore = when {
            stats.laplacianVariance >= 420f -> 0.96f
            stats.laplacianVariance >= 200f -> 0.80f
            stats.laplacianVariance >= 90f -> 0.62f
            else -> (stats.laplacianVariance / 180f).coerceIn(0.20f, 0.55f)
        }

        val perspScore = (1f - (alignedResult.perspectiveSkewDeg / 35f)).coerceIn(0.25f, 1f)
        val wbScore = stats.whiteBalanceCastScore
        val glareSafe = stats.glareRatio < 0.16f

        val isColorSafe = exposureScore >= 0.42f && wbScore >= 0.48f && stats.glareRatio < 0.22f
        val isDetailSafe = blurScore >= 0.48f && resolutionSufficient && alignedResult.perspectiveSkewDeg <= 22f

        val overall = (
            exposureScore * 0.24f +
                wbScore * 0.20f +
                blurScore * 0.28f +
                perspScore * 0.16f +
                (1f - stats.glareRatio).coerceIn(0f, 1f) * 0.12f
            ).coerceIn(0.12f, 0.99f)

        val warnEn = mutableListOf<String>()
        val warnAr = mutableListOf<String>()
        if (!isColorSafe) {
            warnEn += "Strong lighting tint or glare detected; color profile scoring is tolerance-weighted."
            warnAr += "تم رصد انحراف في توازن اللون الأبيض أو وهج قوي؛ تم توسيع سماحية تقييم اللون."
        }
        if (!isDetailSafe) {
            warnEn += "Low sharpness or high perspective skew (${alignedResult.perspectiveSkewDeg.toInt()}°); micro-print checks suppressed."
            warnAr += "انخفاض الحدة أو ميلان الزاوية (${alignedResult.perspectiveSkewDeg.toInt()}°)؛ تم تعليق تقييم التفاصيل الدقيقة."
        }
        if (!glareSafe) {
            warnEn += "Specular glare covers ${(stats.glareRatio * 100).toInt()}% of frame."
            warnAr += "يغطي الوهج الضوئي ${(stats.glareRatio * 100).toInt()}% من الإطار."
        }

        return CaptureCalibrationReport(
            exposureScore = exposureScore,
            whiteBalanceScore = wbScore,
            blurSharpnessScore = blurScore,
            glareRatio = stats.glareRatio,
            perspectiveSkewDeg = alignedResult.perspectiveSkewDeg,
            resolutionSufficient = resolutionSufficient,
            effectiveLongSidePx = longSide,
            isColorSafe = isColorSafe,
            isDetailSafe = isDetailSafe,
            overallQualityScore = overall,
            warningMessagesEn = warnEn,
            warningMessagesAr = warnAr
        )
    }
}

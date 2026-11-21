package com.example.engine.cv

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AlignedNoteResult(
    val canonicalBitmap: Bitmap,
    val perspectiveAdjusted: Boolean,
    val perspectiveSkewDeg: Float,
    val perspectiveConfidence: Float,
    val detectedQuadAreaRatio: Float,
    val openCvNativeUsed: Boolean
)

data class OpenCvVisualFeatures(
    val meanLuminance: Float,
    val meanSaturation: Float,
    val laplacianVariance: Float,
    val glareRatio: Float,
    val darkRatio: Float,
    val edgeDensity: Float,
    val whiteBalanceCastScore: Float,
    val dominantHueDeg: Float,
    val hueHistogram12: FloatArray,
    val featureVector12: FloatArray,
    val openCvNativeUsed: Boolean
)

object OpenCvBridge {

    @Volatile
    private var initAttempted = false

    @Volatile
    private var nativeReady = false

    fun ensureInitialized(): Boolean {
        if (initAttempted) return nativeReady
        synchronized(this) {
            if (!initAttempted) {
                nativeReady = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
                initAttempted = true
            }
        }
        return nativeReady
    }

    fun isNativeActive(): Boolean = ensureInitialized()

    fun alignBanknote(source: Bitmap): AlignedNoteResult {
        val targetW = 900
        val targetH = 400
        val landscape = if (source.height > source.width * 1.05f) {
            val m = android.graphics.Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        } else {
            source
        }

        if (ensureInitialized()) {
            val cvResult = runCatching { alignWithOpenCv(landscape, targetW, targetH) }.getOrNull()
            if (cvResult != null) {
                if (landscape != source) landscape.recycle()
                return cvResult
            }
        }

        val fallback = Bitmap.createScaledBitmap(landscape, targetW, targetH, true)
        if (landscape != source) landscape.recycle()
        return AlignedNoteResult(
            canonicalBitmap = fallback,
            perspectiveAdjusted = false,
            perspectiveSkewDeg = 0f,
            perspectiveConfidence = 0.70f,
            detectedQuadAreaRatio = 1f,
            openCvNativeUsed = false
        )
    }

    private fun alignWithOpenCv(source: Bitmap, targetW: Int, targetH: Int): AlignedNoteResult {
        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 45.0, 145.0)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            val imgArea = (rgba.cols() * rgba.rows()).toDouble().coerceAtLeast(1.0)
            var bestQuad: Array<Point>? = null
            var bestArea = 0.0

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < imgArea * 0.20) {
                    c.release()
                    continue
                }
                val pts2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(pts2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(pts2f, approx, 0.03 * peri, true)
                val pts = approx.toArray()
                if (pts.size == 4 && area > bestArea) {
                    bestArea = area
                    bestQuad = pts
                }
                pts2f.release()
                approx.release()
                c.release()
            }

            if (bestQuad != null) {
                val ordered = orderCorners(bestQuad)
                val topAngle = Math.toDegrees(atan2(ordered[1].y - ordered[0].y, ordered[1].x - ordered[0].x)).toFloat()
                val leftSkew = Math.toDegrees(atan2(ordered[3].x - ordered[0].x, ordered[3].y - ordered[0].y)).toFloat()
                val skewDeg = max(abs(topAngle), abs(leftSkew)).coerceIn(0f, 45f)

                val srcMat = MatOfPoint2f(*ordered)
                val dstMat = MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point((targetW - 1).toDouble(), 0.0),
                    Point((targetW - 1).toDouble(), (targetH - 1).toDouble()),
                    Point(0.0, (targetH - 1).toDouble())
                )
                val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
                val warped = Mat()
                Imgproc.warpPerspective(rgba, warped, transform, Size(targetW.toDouble(), targetH.toDouble()))

                val outBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(warped, outBmp)

                srcMat.release()
                dstMat.release()
                transform.release()
                warped.release()

                val areaRatio = (bestArea / imgArea).toFloat().coerceIn(0f, 1f)
                val perspConf = (1f - (skewDeg / 35f)).coerceIn(0.35f, 0.99f)
                return AlignedNoteResult(
                    canonicalBitmap = outBmp,
                    perspectiveAdjusted = true,
                    perspectiveSkewDeg = skewDeg,
                    perspectiveConfidence = perspConf,
                    detectedQuadAreaRatio = areaRatio,
                    openCvNativeUsed = true
                )
            }

            val resized = Mat()
            Imgproc.resize(rgba, resized, Size(targetW.toDouble(), targetH.toDouble()))
            val outBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resized, outBmp)
            resized.release()

            return AlignedNoteResult(
                canonicalBitmap = outBmp,
                perspectiveAdjusted = false,
                perspectiveSkewDeg = 3.5f,
                perspectiveConfidence = 0.78f,
                detectedQuadAreaRatio = 1f,
                openCvNativeUsed = true
            )
        } finally {
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
        }
    }

    private fun orderCorners(pts: Array<Point>): Array<Point> {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()
        val sortedByDiff = pts.sortedBy { it.y - it.x }
        val tr = sortedByDiff.first()
        val bl = sortedByDiff.last()
        return arrayOf(tl, tr, br, bl)
    }

    fun enhanceSerialCropClahe(crop: Bitmap): Bitmap {
        if (ensureInitialized()) {
            val enhanced = runCatching {
                val src = Mat()
                val gray = Mat()
                val claheOut = Mat()
                val rgbaOut = Mat()
                try {
                    Utils.bitmapToMat(crop, src)
                    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                    val clahe = Imgproc.createCLAHE(2.6, Size(8.0, 8.0))
                    clahe.apply(gray, claheOut)
                    Imgproc.cvtColor(claheOut, rgbaOut, Imgproc.COLOR_GRAY2RGBA)
                    val out = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(rgbaOut, out)
                    out
                } finally {
                    src.release()
                    gray.release()
                    claheOut.release()
                    rgbaOut.release()
                }
            }.getOrNull()
            if (enhanced != null) return enhanced
        }

        // Pure Kotlin high-contrast grayscale fallback for JVM tests
        val w = crop.width.coerceAtLeast(1)
        val h = crop.height.coerceAtLeast(1)
        val px = IntArray(w * h)
        crop.getPixels(px, 0, w, 0, 0, w, h)
        var minL = 255
        var maxL = 0
        val lums = IntArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val l = (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000
            lums[i] = l
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        val span = (maxL - minL).coerceAtLeast(1)
        for (i in px.indices) {
            val stretched = (((lums[i] - minL) * 255) / span).coerceIn(0, 255)
            px[i] = Color.rgb(stretched, stretched, stretched)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun measureVisualStats(bitmap: Bitmap): OpenCvVisualFeatures {
        val scaled = Bitmap.createScaledBitmap(bitmap, 180, 80, true)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val nativeLapVar = if (ensureInitialized()) {
            runCatching { computeOpenCvLaplacianVariance(scaled) }.getOrNull()
        } else null

        var lumSum = 0.0
        var satSum = 0.0
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var glarePx = 0
        var darkPx = 0
        var edgePx = 0
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapSamples = 0
        val hueBins = FloatArray(12)
        val hsv = FloatArray(3)
        var chromaticPx = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val p = pixels[y * w + x]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                rSum += r
                gSum += g
                bSum += b
                Color.colorToHSV(p, hsv)
                lumSum += hsv[2]
                satSum += hsv[1]
                if (hsv[2] > 0.94f && hsv[1] < 0.11f) glarePx++
                if (hsv[2] < 0.13f) darkPx++
                if (hsv[1] > 0.10f && hsv[2] in 0.14f..0.92f) {
                    val bin = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
                    hueBins[bin] += 1f
                    chromaticPx++
                }

                val lC = lum(p)
                val lap = abs(
                    4 * lC - lum(pixels[y * w + x + 1]) - lum(pixels[y * w + x - 1]) -
                        lum(pixels[(y + 1) * w + x]) - lum(pixels[(y - 1) * w + x])
                )
                lapSum += lap
                lapSqSum += lap * lap
                lapSamples++
                if (lap > 34) edgePx++
            }
        }

        val count = ((w - 2) * (h - 2)).coerceAtLeast(1)
        val meanLum = (lumSum / count).toFloat()
        val meanSat = (satSum / count).toFloat()
        val glareRatio = glarePx.toFloat() / count
        val darkRatio = darkPx.toFloat() / count
        val edgeDensity = edgePx.toFloat() / count

        val lapVar = nativeLapVar ?: run {
            val meanLap = lapSum / lapSamples.coerceAtLeast(1)
            ((lapSqSum / lapSamples.coerceAtLeast(1)) - (meanLap * meanLap)).toFloat().coerceAtLeast(0f)
        }

        val avgR = rSum / count
        val avgG = gSum / count
        val avgB = bSum / count
        val rgbMean = ((avgR + avgG + avgB) / 3.0).coerceAtLeast(1.0)
        val castDeviation = (max(abs(avgR - rgbMean), max(abs(avgG - rgbMean), abs(avgB - rgbMean))) / 255.0).toFloat()
        val wbCastScore = (1f - castDeviation * 3.2f).coerceIn(0.2f, 1f)

        var maxBinIdx = 0
        var maxBinVal = 0f
        if (chromaticPx > 0) {
            for (i in hueBins.indices) {
                hueBins[i] = hueBins[i] / chromaticPx
                if (hueBins[i] > maxBinVal) {
                    maxBinVal = hueBins[i]
                    maxBinIdx = i
                }
            }
        }
        val dominantHueDeg = maxBinIdx * 30f + 15f

        val vec12 = floatArrayOf(
            meanLum,
            meanSat,
            edgeDensity,
            hueBins[0] + hueBins[11], // reds/pinks
            hueBins[1] + hueBins[2],  // warm/brown/amber (SAR 500/100/classic)
            hueBins[3] + hueBins[4],  // greens (SAR 1/USD)
            hueBins[5] + hueBins[6],  // cyan/teal (SAR 100/USD)
            hueBins[7] + hueBins[8],  // blues (SAR 500/USD modern)
            hueBins[9] + hueBins[10], // purples (SAR 5)
            (1f - glareRatio),
            wbCastScore,
            min(1f, sqrt(lapVar) / 28f)
        )

        if (scaled != bitmap) scaled.recycle()

        return OpenCvVisualFeatures(
            meanLuminance = meanLum,
            meanSaturation = meanSat,
            laplacianVariance = lapVar,
            glareRatio = glareRatio,
            darkRatio = darkRatio,
            edgeDensity = edgeDensity,
            whiteBalanceCastScore = wbCastScore,
            dominantHueDeg = dominantHueDeg,
            hueHistogram12 = hueBins,
            featureVector12 = vec12,
            openCvNativeUsed = nativeLapVar != null
        )
    }

    private fun computeOpenCvLaplacianVariance(bmp: Bitmap): Float {
        val rgba = Mat()
        val gray = Mat()
        val lap = Mat()
        val mean = org.opencv.core.MatOfDouble()
        val std = org.opencv.core.MatOfDouble()
        try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            org.opencv.core.Core.meanStdDev(lap, mean, std)
            val s = std.toArray().firstOrNull() ?: 0.0
            return (s * s).toFloat()
        } finally {
            rgba.release()
            gray.release()
            lap.release()
            mean.release()
            std.release()
        }
    }

    fun computePerceptualDHash(bitmap: Bitmap): String {
        val tiny = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0UL
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = lum(tiny.getPixel(x, y))
                val right = lum(tiny.getPixel(x + 1, y))
                if (left > right) {
                    val bit = y * 8 + x
                    hash = hash or (1UL shl bit)
                }
            }
        }
        if (tiny != bitmap) tiny.recycle()
        return hash.toString(16).padStart(16, '0')
    }

    fun computeHammingDistance(hashA: String, hashB: String): Int {
        if (hashA.length != 16 || hashB.length != 16) return 64
        val a = hashA.toULongOrNull(16) ?: return 64
        val b = hashB.toULongOrNull(16) ?: return 64
        return (a xor b).countOneBits()
    }

    private fun lum(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}

package com.example.engine.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.engine.cv.OpenCvBridge
import com.example.engine.serial.SerialPrintQuality
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

data class OcrTokenBox(
    val text: String,
    val normX: Float,
    val normY: Float,
    val normW: Float,
    val normH: Float,
    val baselineSlope: Float
)

data class MlKitExtractionResult(
    val allText: String,
    val tokens: List<OcrTokenBox>,
    val serialCandidates: List<OcrTokenBox>,
    val claheSerialPassText: List<String>,
    val ocrPassAgreementScore: Float,
    val federalReserveHint: String?,
    val seriesYearHint: String?,
    val plateHints: List<String>,
    val serialPrintQuality: SerialPrintQuality
)

class MlKitBridge {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyzeBitmap(canonicalBitmap: Bitmap): MlKitExtractionResult {
        val visionText = runCatching {
            val image = InputImage.fromBitmap(canonicalBitmap, 0)
            recognizeText(image)
        }.getOrNull()

        val width = canonicalBitmap.width.coerceAtLeast(1).toFloat()
        val height = canonicalBitmap.height.coerceAtLeast(1).toFloat()
        val tokens = mutableListOf<OcrTokenBox>()
        val serialCandidates = mutableListOf<OcrTokenBox>()

        val rawLines = visionText?.textBlocks?.flatMap { it.lines }.orEmpty()
        for (line in rawLines) {
            val box = line.boundingBox ?: continue
            val text = line.text.trim()
            if (text.isEmpty()) continue
            val slope = computeLineSlope(line)
            val token = OcrTokenBox(
                text = text,
                normX = (box.left / width).coerceIn(0f, 1f),
                normY = (box.top / height).coerceIn(0f, 1f),
                normW = (box.width() / width).coerceIn(0f, 1f),
                normH = (box.height() / height).coerceIn(0f, 1f),
                baselineSlope = slope
            )
            tokens += token
            if (looksLikeSerial(text)) {
                serialCandidates += token
            }
        }

        // Pass 2: OpenCV CLAHE enhanced zoomed serial ROI re-read for higher precision
        val claheTexts = mutableListOf<String>()
        val firstCand = serialCandidates.firstOrNull()
        if (firstCand != null && canonicalBitmap.width >= 100 && canonicalBitmap.height >= 50) {
            val cropLeft = ((firstCand.normX - 0.03f) * canonicalBitmap.width).toInt().coerceIn(0, canonicalBitmap.width - 2)
            val cropTop = ((firstCand.normY - 0.03f) * canonicalBitmap.height).toInt().coerceIn(0, canonicalBitmap.height - 2)
            val cropW = ((firstCand.normW + 0.06f) * canonicalBitmap.width).toInt().coerceIn(10, canonicalBitmap.width - cropLeft)
            val cropH = ((firstCand.normH + 0.06f) * canonicalBitmap.height).toInt().coerceIn(10, canonicalBitmap.height - cropTop)
            runCatching {
                val roiBmp = Bitmap.createBitmap(canonicalBitmap, cropLeft, cropTop, cropW, cropH)
                val claheBmp = OpenCvBridge.enhanceSerialCropClahe(roiBmp)
                val passLines = recognizeZoomedCrop(claheBmp)
                claheTexts += passLines
                if (claheBmp != roiBmp) claheBmp.recycle()
                roiBmp.recycle()
            }
        }

        val agreement = when {
            serialCandidates.isEmpty() -> 0.50f
            claheTexts.isEmpty() -> 0.80f
            claheTexts.any {
                it.replace(" ", "").equals(serialCandidates.first().text.replace(" ", ""), ignoreCase = true)
            } -> 0.96f
            else -> 0.72f
        }

        val fullText = visionText?.text.orEmpty()
        val frHint = extractFederalReserveCode(fullText)
        val seriesHint = extractSeriesYear(fullText)
        val plates = extractPlateCandidates(tokens)
        val quality = estimateSerialPrintQuality(serialCandidates, rawLines)

        return MlKitExtractionResult(
            allText = fullText,
            tokens = tokens,
            serialCandidates = serialCandidates,
            claheSerialPassText = claheTexts,
            ocrPassAgreementScore = agreement,
            federalReserveHint = frHint,
            seriesYearHint = seriesHint,
            plateHints = plates,
            serialPrintQuality = quality
        )
    }

    suspend fun recognizeZoomedCrop(crop: Bitmap): List<String> {
        val visionText = runCatching {
            val image = InputImage.fromBitmap(crop, 0)
            recognizeText(image)
        }.getOrNull() ?: return emptyList()
        return visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotEmpty() }
    }

    private suspend fun recognizeText(image: InputImage): Text? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                textRecognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun looksLikeSerial(raw: String): Boolean {
        val compact = raw.replace(" ", "").replace("-", "").uppercase()
        if (compact.length !in 7..14) return false
        val digitCount = compact.count { it.isDigit() }
        return digitCount >= 6
    }

    private fun extractFederalReserveCode(text: String): String? {
        val match = Regex("\\b([A-L])\\s?(1[0-2]|[1-9])\\b").find(text.uppercase())
        return match?.let { "${it.groupValues[1]}${it.groupValues[2]}" }
    }

    private fun extractSeriesYear(text: String): String? {
        val match = Regex("SERIES\\s+(19\\d{2}[A-Z]?|20\\d{2}[A-Z]?)", RegexOption.IGNORE_CASE)
            .find(text)
        return match?.groupValues?.getOrNull(1)?.uppercase()
    }

    private fun extractPlateCandidates(tokens: List<OcrTokenBox>): List<String> {
        return tokens
            .map { it.text.trim().uppercase() }
            .filter { Regex("^[A-H]?\\d{1,3}$").matches(it) }
            .distinct()
            .take(4)
    }

    private fun computeLineSlope(line: Text.Line): Float {
        val elements = line.elements
        if (elements.size < 2) return 0f
        val first: Rect = elements.first().boundingBox ?: return 0f
        val last: Rect = elements.last().boundingBox ?: return 0f
        val dx = (last.centerX() - first.centerX()).toFloat()
        if (abs(dx) < 1f) return 0f
        return (last.centerY() - first.centerY()) / dx
    }

    private fun estimateSerialPrintQuality(
        candidates: List<OcrTokenBox>,
        lines: List<Text.Line>
    ): SerialPrintQuality {
        if (candidates.isEmpty()) {
            return SerialPrintQuality(
                strokeUniformity = 0.55f,
                baselineAlignment = 0.55f,
                characterSpacing = 0.55f,
                inkClarity = 0.50f,
                wearToleranceApplied = true
            )
        }
        val avgSlope = candidates.map { abs(it.baselineSlope) }.average().toFloat()
        val baselineScore = (1f - (avgSlope * 4f)).coerceIn(0.4f, 0.99f)

        val matchingLines = lines.filter { line ->
            candidates.any { it.text == line.text.trim() }
        }
        val heights = matchingLines.flatMap { it.elements }.mapNotNull { it.boundingBox?.height()?.toFloat() }
        val strokeScore = if (heights.size >= 3) {
            val mean = heights.average().toFloat().coerceAtLeast(1f)
            val stdDev = heights.map { abs(it - mean) }.average().toFloat()
            (1f - (stdDev / mean) * 1.4f).coerceIn(0.45f, 0.98f)
        } else 0.82f

        return SerialPrintQuality(
            strokeUniformity = strokeScore,
            baselineAlignment = baselineScore,
            characterSpacing = ((strokeScore + baselineScore) / 2f).coerceIn(0.5f, 0.98f),
            inkClarity = (strokeScore * 0.95f).coerceIn(0.45f, 0.97f),
            wearToleranceApplied = true
        )
    }
}

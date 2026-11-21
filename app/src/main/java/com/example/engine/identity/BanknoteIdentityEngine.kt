package com.example.engine.identity

import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCatalogRepository
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.cv.LiteRtForensicModels

class BanknoteIdentityEngine(
    private val catalogRepo: CurrencyCatalogRepository,
    private val liteRtModels: LiteRtForensicModels? = null
) {

    suspend fun identify(input: BieInput): Pair<IdentityClassificationResult, BanknoteCatalogEntry?> {
        val liteRtPred = liteRtModels?.predictIdentity(input.featureVector12)
        val upperText = input.allOcrText.uppercase()

        // 1. Currency Classifier
        val usdSignals = listOf("FEDERAL RESERVE", "UNITED STATES", "DOLLAR", "TREASURY", "SERIES", "THE UNITED STATES", "RESERVE NOTE")
            .count { upperText.contains(it) }
        val sarSignals = listOf("SAUDI", "RIYAL", "CENTRAL BANK", "MONETARY AUTHORITY", "KINGDOM OF SAUDI", "السعودي", "ريال", "المملكة", "مؤسسة النقد", "البنك المركزي")
            .count { input.allOcrText.contains(it, ignoreCase = true) }

        val liteUsdProb = (liteRtPred?.probabilities?.get("USD_MODERN") ?: 0f) +
            (liteRtPred?.probabilities?.get("USD_CLASSIC") ?: 0f)
        val liteSarProb = (liteRtPred?.probabilities?.get("SAR_COTTON") ?: 0f) +
            (liteRtPred?.probabilities?.get("SAR_POLYMER") ?: 0f)

        val usdScore = usdSignals * 0.28f + liteUsdProb * 0.35f +
            if (input.serialCandidates.any { Regex("^[A-Z]{1,2}\\d{8}[A-Z★*]?$").matches(it.replace(" ", "").uppercase()) }) 0.25f else 0f
        val sarScore = sarSignals * 0.28f + liteSarProb * 0.35f +
            if (input.allOcrText.any { it in '\u0600'..'\u06FF' }) 0.22f else 0f

        val currency: CurrencyCode?
        val currencyConf: Float
        if (input.currencyHint != null) {
            currency = input.currencyHint
            currencyConf = 0.96f
        } else if (usdScore < 0.28f && sarScore < 0.28f && input.tokenStrings.isEmpty()) {
            currency = null
            currencyConf = maxOf(usdScore, sarScore)
        } else if (sarScore > usdScore) {
            currency = CurrencyCode.SAR
            currencyConf = (0.45f + sarScore).coerceIn(0.45f, 0.96f)
        } else if (usdScore > 0.28f) {
            currency = CurrencyCode.USD
            currencyConf = (0.45f + usdScore).coerceIn(0.45f, 0.96f)
        } else {
            currency = null
            currencyConf = 0.25f
        }

        if (currency == null) {
            return IdentityClassificationResult(
                detectedCurrency = null,
                currencyConfidence = currencyConf,
                detectedDenomination = null,
                denominationConfidence = 0f,
                issueLabel = "UNCLASSIFIED",
                issueConfidence = 0f,
                designFamilyId = "UNCLASSIFIED",
                designFamilyConfidence = 0f,
                predictedMaterial = SubstrateMaterial.UNCERTAIN,
                materialConfidence = 0.2f,
                materialOpticalLimitationEn = "Smartphone camera optics cannot conclusively confirm fiber or polymer composition without physical inspection.",
                materialOpticalLimitationAr = "كاميرا الهاتف لا تثبت خامة الورقة معملياً؛ التصنيف يستند للمؤشرات البصرية فقط.",
                orientation = NoteSideOrientation.UNCERTAIN,
                orientationConfidence = 0.25f,
                matchedEntryId = null,
                isUnclassified = true,
                liteRtClassifierLabel = liteRtPred?.topLabel ?: "NONE",
                liteRtConfidence = liteRtPred?.confidence ?: 0f,
                ranOnNativeLiteRt = liteRtPred?.ranOnLiteRt ?: false
            ) to null
        }

        // 2. Denomination Classifier
        val validDenoms = if (currency == CurrencyCode.SAR) {
            listOf(500, 200, 100, 50, 10, 5, 1)
        } else {
            listOf(100, 50, 20, 10, 5, 2, 1)
        }
        val textNums = Regex("\\b(500|200|100|50|20|10|5|2|1)\\b").findAll(input.allOcrText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        val denomWordsUsd = mapOf(
            "HUNDRED" to 100,
            "FIFTY" to 50,
            "TWENTY" to 20,
            "TEN" to 10,
            "FIVE" to 5,
            "TWO" to 2,
            "ONE" to 1
        )
        val wordDenom = if (currency == CurrencyCode.USD) {
            denomWordsUsd.entries.firstOrNull { upperText.contains(it.key) }?.value
        } else null

        val detectedDenom: Int?
        val denomConf: Float
        if (input.denominationHint != null) {
            detectedDenom = input.denominationHint
            denomConf = 0.96f
        } else {
            val matchedFromText = validDenoms.firstOrNull { it in textNums } ?: wordDenom
            if (matchedFromText != null) {
                detectedDenom = matchedFromText
                denomConf = 0.86f
            } else {
                // Closest hue match from catalog for this currency
                val hueMatched = catalogRepo.findEntries(currency).minByOrNull { entry ->
                    val midHue = (entry.dominantHueMin + entry.dominantHueMax) / 2f
                    kotlin.math.abs(midHue - input.dominantHueDeg)
                }
                detectedDenom = hueMatched?.denominationValue ?: 100
                denomConf = 0.52f
            }
        }

        // 3. Material Classifier (camera limitation aware)
        val isPolymerSar5 = currency == CurrencyCode.SAR && detectedDenom == 5 &&
            (input.materialHint == SubstrateMaterial.POLYMER ||
                input.polymerWindowRatio > 0.52f ||
                (liteRtPred?.topLabel == "SAR_POLYMER" && (liteRtPred.confidence) > 0.42f))
        val predictedMaterial = when {
            input.materialHint != null -> input.materialHint
            isPolymerSar5 -> SubstrateMaterial.POLYMER
            currency == CurrencyCode.SAR && detectedDenom == 5 -> SubstrateMaterial.COTTON_LINEN
            else -> SubstrateMaterial.COTTON_LINEN
        }
        val materialConf = if (input.materialHint != null) 0.88f else if (isPolymerSar5) 0.78f else 0.64f

        // 4. Issue & Design Family Classifier
        val candidates = catalogRepo.findEntries(currency, detectedDenom, predictedMaterial)
            .ifEmpty { catalogRepo.findEntries(currency, detectedDenom) }
            .ifEmpty { catalogRepo.findEntries(currency) }

        val selected = pickIssueEntry(candidates, input, currency)
        val issueConf = when {
            input.seriesYearHint != null && selected != null -> 0.88f
            candidates.size == 1 -> 0.85f
            else -> 0.68f
        }

        // 5. Side / Orientation Classifier
        val (orientation, orientConf) = classifyOrientation(currency, input)

        val result = IdentityClassificationResult(
            detectedCurrency = currency,
            currencyConfidence = currencyConf,
            detectedDenomination = detectedDenom,
            denominationConfidence = denomConf,
            issueLabel = selected?.issueYear ?: "UNKNOWN",
            issueConfidence = issueConf,
            designFamilyId = selected?.familyId ?: "UNKNOWN",
            designFamilyConfidence = issueConf,
            predictedMaterial = predictedMaterial,
            materialConfidence = materialConf,
            materialOpticalLimitationEn = if (predictedMaterial == SubstrateMaterial.POLYMER) {
                "Polymer inferred from transparent window optics; tactile substrate thickness not measurable by camera."
            } else {
                "Cotton/linen estimated from issue profile & surface grain; tactile fiber feel cannot be verified optically."
            },
            materialOpticalLimitationAr = if (predictedMaterial == SubstrateMaterial.POLYMER) {
                "تم استنتاج البوليمر من نافذة الشفافية البصرية؛ الكاميرا لا تقيس سماكة أو مرونة الخامة فيزيائياً."
            } else {
                "تم تقدير القطن/الكتان من الإصدار وملمس السطح البصري؛ الكاميرا لا تغني عن فحص الملمس الفعلي."
            },
            orientation = orientation,
            orientationConfidence = orientConf,
            matchedEntryId = selected?.id,
            isUnclassified = false,
            liteRtClassifierLabel = liteRtPred?.topLabel ?: "MATH_FALLBACK",
            liteRtConfidence = liteRtPred?.confidence ?: 0f,
            ranOnNativeLiteRt = liteRtPred?.ranOnLiteRt ?: false
        )
        return result to selected
    }

    private fun pickIssueEntry(
        candidates: List<BanknoteCatalogEntry>,
        input: BieInput,
        currency: CurrencyCode
    ): BanknoteCatalogEntry? {
        if (candidates.isEmpty()) return null
        if (input.materialHint != null) {
            candidates.firstOrNull { it.material == input.materialHint }?.let { return it }
        }
        val year = input.seriesYearHint?.trim()
        if (currency == CurrencyCode.USD && year != null && candidates.size > 1) {
            if (year.startsWith("199") || year.startsWith("2001") || year.startsWith("2003")) {
                candidates.firstOrNull { it.id.contains("1996") }?.let { return it }
            }
            if (year.startsWith("192") || year.startsWith("193") || year.startsWith("195")) {
                candidates.firstOrNull { it.id.contains("historical") }?.let { return it }
            }
        }
        if (currency == CurrencyCode.SAR && candidates.size > 1) {
            if (input.polymerWindowRatio > 0.52f) {
                candidates.firstOrNull { it.material == SubstrateMaterial.POLYMER }?.let { return it }
            }
        }
        return candidates.first()
    }

    private fun classifyOrientation(currency: CurrencyCode, input: BieInput): Pair<NoteSideOrientation, Float> {
        val upper = input.allOcrText.uppercase()
        if (input.serialCandidates.isNotEmpty()) {
            return NoteSideOrientation.FRONT to 0.90f
        }
        if (currency == CurrencyCode.USD && (upper.contains("IN GOD WE TRUST") || upper.contains("INDEPENDENCE HALL"))) {
            return NoteSideOrientation.BACK to 0.78f
        }
        if (input.tokenStrings.isNotEmpty()) {
            return NoteSideOrientation.FRONT to 0.65f
        }
        return NoteSideOrientation.UNCERTAIN to 0.40f
    }
}

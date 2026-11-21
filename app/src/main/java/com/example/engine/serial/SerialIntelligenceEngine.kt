package com.example.engine.serial

import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCode
import com.example.catalog.CurrencyMasterCatalog
import com.example.catalog.SerialRuleType

data class SerialAuditInput(
    val currency: CurrencyCode,
    val denomination: Int,
    val primarySerial: String,
    val secondarySerial: String? = null,
    val printedSeriesYear: String? = null,
    val federalReserveIndicator: String? = null,
    val facePlateNumber: String? = null,
    val backPlateNumber: String? = null,
    val notePositionId: String? = null,
    val starToggle: Boolean = false,
    val catalogEntry: BanknoteCatalogEntry? = null,
    val measuredPrintQuality: SerialPrintQuality? = null,
    val ocrPassAgreementScore: Float = 0.90f,
    val duplicateClassification: DuplicateClassification = DuplicateClassification.UNIQUE
)

class SerialIntelligenceEngine(private val catalog: CurrencyMasterCatalog) {

    fun analyze(input: SerialAuditInput): SerialAnalysisResult {
        val raw = input.primarySerial.trim()
        val normalized = normalizeSerial(raw, input.starToggle)
        val secondaryNorm = input.secondarySerial?.trim()?.takeIf { it.isNotEmpty() }?.let {
            normalizeSerial(it, input.starToggle)
        }
        return when (input.currency) {
            CurrencyCode.USD -> analyzeUsd(input, raw, normalized, secondaryNorm)
            CurrencyCode.SAR -> analyzeSar(input, raw, normalized, secondaryNorm)
        }
    }

    private fun analyzeUsd(
        input: SerialAuditInput,
        raw: String,
        normalized: String,
        secondaryNorm: String?
    ): SerialAnalysisResult {
        val checks = mutableListOf<SerialCheckRow>()
        val segments = mutableListOf<SerialTokenSegment>()
        val anomaliesEn = mutableListOf<String>()
        val anomaliesAr = mutableListOf<String>()

        val isLegacyDenom = input.denomination == 1 || input.denomination == 2
        val ruleType = input.catalogEntry?.serialRuleType ?: if (isLegacyDenom) {
            SerialRuleType.USD_LEGACY_10
        } else {
            SerialRuleType.USD_MODERN_11
        }

        val hasStar = normalized.endsWith("★") || normalized.endsWith("*") || input.starToggle
        val cleanBody = normalized.replace("★", "").replace("*", "")
        val glyphs = buildGlyphList(normalized, CurrencyCode.USD)
        val ambiguityCount = glyphs.count { it.isAmbiguous }

        if (raw.isBlank()) {
            checks += SerialCheckRow("read", "Serial Read", "قراءة الرقم", "Not readable / missing", "غير مقروء / مفقود", CheckStatus.UNAVAILABLE)
            val pq = input.measuredPrintQuality ?: defaultQuality(0.45f)
            return SerialAnalysisResult(
                rawExtracted = raw,
                normalizedSerial = "",
                secondarySerial = secondaryNorm,
                currency = CurrencyCode.USD,
                denomination = input.denomination,
                isStarNote = hasStar,
                detectedSeriesYear = null,
                detectedReserveBank = null,
                glyphs = emptyList(),
                segments = emptyList(),
                checks = checks,
                anomaliesEn = anomaliesEn,
                anomaliesAr = anomaliesAr,
                printQuality = pq,
                fivePartReport = buildFivePart(
                    structScore = 0.2f,
                    structEn = "No serial characters extracted",
                    structAr = "لم تُقرأ خانات الرقم التسلسلي",
                    ocrScore = 0.2f,
                    ambiguities = 0,
                    seriesScore = 0.4f,
                    seriesEn = "Series check pending serial input",
                    seriesAr = "مطابقة السلسلة تتطلب قراءة الرقم",
                    visualScore = pq.strokeUniformity,
                    visualEn = "Print uniformity unavailable without serial ROI",
                    visualAr = "تعذر قياس انتظام الطباعة لعدم وضوح الرقم",
                    dupClass = input.duplicateClassification
                ),
                overallScore = 0.25f,
                hasCriticalContradiction = false
            )
        }

        checks += SerialCheckRow("read", "Serial Read", "قراءة الرقم", "Extracted ($normalized)", "مكتمل ($normalized)", CheckStatus.PASS)

        var detectedSeriesYear: String? = null
        var detectedBankDesc: String? = null
        var detectedSignatures: String? = null
        var criticalContradiction = false
        var structuralScore = 1.0f
        var seriesCompatScore = 1.0f
        var visualScore = input.measuredPrintQuality?.let {
            (it.strokeUniformity + it.baselineAlignment + it.characterSpacing + it.inkClarity) / 4f
        } ?: 0.90f

        // Dual serial match check (left vs right on front)
        if (!secondaryNorm.isNullOrBlank()) {
            if (secondaryNorm != normalized) {
                criticalContradiction = true
                visualScore -= 0.45f
                checks += SerialCheckRow(
                    id = "dual_match",
                    labelEn = "Dual Serial Match",
                    labelAr = "تطابق الرقمين على الوجه",
                    resultEn = "MISMATCH ($normalized vs $secondaryNorm)",
                    resultAr = "تناقض ($normalized مقابل $secondaryNorm)",
                    status = CheckStatus.CONTRADICTION
                )
                anomaliesEn += "Left and right USD serial numbers differ ($normalized vs $secondaryNorm)."
                anomaliesAr += "الرقمان التسلسليان على وجه الدولار غير متطابقين ($normalized مقابل $secondaryNorm)."
            } else {
                checks += SerialCheckRow("dual_match", "Dual Serial Match", "تطابق الرقمين", "Matched", "متطابقان", CheckStatus.PASS)
            }
        }

        // Decoupled denomination serial rule from MCC 2.0
        val denomRule = catalog.denominationSerialRules.firstOrNull {
            it.currency == CurrencyCode.USD && input.denomination in it.denominations
        }

        if (ruleType == SerialRuleType.USD_LEGACY_10 || isLegacyDenom) {
            val candidate = if (hasStar && cleanBody.length == 9) "${cleanBody}★" else normalized
            val legacyRegex = Regex("^([A-Z])(\\d{8})([A-Z★*])$")
            val match = legacyRegex.matchEntire(candidate)

            if (cleanBody.length == 11 && cleanBody.take(2).all { it.isLetter() }) {
                criticalContradiction = true
                structuralScore -= 0.55f
                seriesCompatScore -= 0.40f
                checks += SerialCheckRow(
                    id = "digit_count",
                    labelEn = "$1/$2 Structure",
                    labelAr = "بنية فئة 1 و 2 دولار",
                    resultEn = "CONTRADICTION: 11-char modern prefix used on $${input.denomination}",
                    resultAr = "تناقض: استخدام بادئة حرفين (11 خانة) على فئة $${input.denomination}",
                    status = CheckStatus.CONTRADICTION
                )
                anomaliesEn += "Denomination $${input.denomination} uses 10-character Reserve-first serials, never an 11-character series prefix."
                anomaliesAr += "فئتا 1 و 2 دولار تستخدمان نظام 10 خانات (حرف البنك أولاً)، وليس نظام 11 خانة."
            } else if (match == null) {
                criticalContradiction = true
                structuralScore -= 0.45f
                checks += SerialCheckRow(
                    id = "digit_count",
                    labelEn = "$1/$2 Structure",
                    labelAr = "بنية فئة 1 و 2 دولار",
                    resultEn = "Invalid length/pattern (${denomRule?.descriptionEn ?: "expected 10 chars"})",
                    resultAr = "بنية غير مطابقة (المتوقع 10 خانات)",
                    status = CheckStatus.CONTRADICTION
                )
                anomaliesEn += "Serial '$normalized' violates the 10-character $${input.denomination} structure [Bank][8 Digits][Suffix/★]."
                anomaliesAr += "الرقم '$normalized' يخالف بنية فئة $${input.denomination} المكونة من 10 خانات."
            } else {
                val bankChar = match.groupValues[1]
                val digits = match.groupValues[2]
                val suffix = match.groupValues[3]
                val bankDistrict = catalog.usdFederalReserveDistricts.firstOrNull { it.letter == bankChar }
                val bankStatus = if (bankDistrict != null) CheckStatus.PASS else CheckStatus.CONTRADICTION
                if (bankDistrict == null) {
                    criticalContradiction = true
                    structuralScore -= 0.35f
                    anomaliesEn += "Invalid Federal Reserve Bank letter '$bankChar' (valid range A–L)."
                    anomaliesAr += "حرف بنك الاحتياطي الفيدرالي '$bankChar' غير صالح (المسموح A–L)."
                } else {
                    detectedBankDesc = "${bankDistrict.code} (${bankDistrict.cityEn})"
                }

                val suffixForbidden = suffix == "O" || suffix == "Z"
                val suffixStatus = if (suffixForbidden) CheckStatus.CONTRADICTION else CheckStatus.PASS
                if (suffixForbidden) {
                    criticalContradiction = true
                    structuralScore -= 0.30f
                    anomaliesEn += "Forbidden suffix letter '$suffix' (BEP excludes O and Z)."
                    anomaliesAr += "اللاحقة '$suffix' مستبعدة رسمياً في قواعد مكتب النقش والطباعة (O و Z)."
                }

                segments += SerialTokenSegment(bankChar, "Reserve Bank", "بنك الاحتياطي", bankStatus)
                segments += SerialTokenSegment(digits, "8-Digit Serial", "الرقم الفردي", CheckStatus.PASS)
                segments += SerialTokenSegment(suffix, if (hasStar) "Star Note ★" else "Suffix", if (hasStar) "ورقة بديلة ★" else "اللاحقة", suffixStatus)

                checks += SerialCheckRow("digit_count", "Character Count", "عدد الخانات", "Valid 10-char ($1/$2 Rule)", "متوافق (10 خانات)", CheckStatus.PASS)
                checks += SerialCheckRow(
                    "reserve_bank",
                    "Reserve Bank",
                    "بنك الاحتياطي",
                    detectedBankDesc ?: "Invalid ($bankChar)",
                    bankDistrict?.let { "${it.code} (${it.cityAr})" } ?: "غير صالح ($bankChar)",
                    bankStatus
                )

                verifyReserveIndicator(bankChar, input.federalReserveIndicator, checks, anomaliesEn, anomaliesAr) {
                    criticalContradiction = true
                    seriesCompatScore -= 0.40f
                }
            }
        } else {
            val candidate = if (hasStar && cleanBody.length == 10) "${cleanBody}★" else normalized
            val modernRegex = Regex("^([A-Z])([A-Z])(\\d{8})([A-Z★*])$")
            val match = modernRegex.matchEntire(candidate)

            if (match == null) {
                criticalContradiction = true
                structuralScore -= 0.45f
                checks += SerialCheckRow(
                    id = "digit_count",
                    labelEn = "Character Count",
                    labelAr = "عدد الخانات",
                    resultEn = "Invalid structure (${denomRule?.descriptionEn ?: "expected 11 chars"})",
                    resultAr = "بنية غير متوافقة (المتوقع 11 خانة لفئة $${input.denomination})",
                    status = CheckStatus.CONTRADICTION
                )
                anomaliesEn += "USD $${input.denomination} requires 11 characters: [Series][Reserve][8 Digits][Suffix/★]."
                anomaliesAr += "فئة $${input.denomination} تتطلب 11 خانة: [حرف السلسلة][بنك الاحتياطي][8 أرقام][اللاحقة/★]."
            } else {
                val seriesChar = match.groupValues[1]
                val bankChar = match.groupValues[2]
                val digits = match.groupValues[3]
                val suffix = match.groupValues[4]

                val seriesRule = catalog.usdSeriesYears.firstOrNull { it.letter == seriesChar }
                val seriesForbidden = seriesChar == "O" || seriesChar == "Z"
                val seriesStatus = when {
                    seriesForbidden || seriesRule == null -> CheckStatus.CONTRADICTION
                    input.denomination !in seriesRule.denominations -> CheckStatus.WARNING
                    else -> CheckStatus.PASS
                }

                if (seriesForbidden || seriesRule == null) {
                    criticalContradiction = true
                    seriesCompatScore -= 0.45f
                    anomaliesEn += "Series prefix '$seriesChar' is not mapped to any documented USD series (1996–2025)."
                    anomaliesAr += "حرف السلسلة '$seriesChar' غير موجود في جدول سنوات السلسلة الرسمي (1996–2025)."
                } else {
                    detectedSeriesYear = seriesRule.seriesYear
                    detectedSignatures = catalog.signatureCombinations.firstOrNull {
                        it.currency == CurrencyCode.USD &&
                            input.denomination in it.applicableDenominations &&
                            seriesRule.seriesYear.contains(it.seriesOrIssue, ignoreCase = true)
                    }?.officials
                    if (input.denomination !in seriesRule.denominations) {
                        seriesCompatScore -= 0.25f
                        anomaliesEn += "Series '$seriesChar' (${seriesRule.seriesYear}) is not listed for $${input.denomination} in BEP release tables."
                        anomaliesAr += "السلسلة '$seriesChar' (${seriesRule.seriesYear}) غير مدرجة لفئة $${input.denomination} في جدول الإصدار."
                    }
                }

                val bankDistrict = catalog.usdFederalReserveDistricts.firstOrNull { it.letter == bankChar }
                val bankStatus = if (bankDistrict != null) CheckStatus.PASS else CheckStatus.CONTRADICTION
                if (bankDistrict == null) {
                    criticalContradiction = true
                    structuralScore -= 0.35f
                    anomaliesEn += "2nd character '$bankChar' is not a valid Federal Reserve Bank (A–L)."
                    anomaliesAr += "الحرف الثاني '$bankChar' لا يمثل أي بنك احتياطي فيدرالي (A–L)."
                } else {
                    detectedBankDesc = "${bankDistrict.code} (${bankDistrict.cityEn})"
                }

                val suffixForbidden = suffix == "O" || suffix == "Z"
                val suffixStatus = if (suffixForbidden) CheckStatus.CONTRADICTION else CheckStatus.PASS
                if (suffixForbidden) {
                    criticalContradiction = true
                    structuralScore -= 0.25f
                    anomaliesEn += "Suffix '$suffix' violates BEP exclusion of O and Z."
                    anomaliesAr += "اللاحقة '$suffix' تخالف قاعدة استبعاد الحرفين O و Z."
                }

                segments += SerialTokenSegment(seriesChar, "Series Year", "سنة السلسلة", seriesStatus)
                segments += SerialTokenSegment(bankChar, "Reserve Bank", "بنك الاحتياطي", bankStatus)
                segments += SerialTokenSegment(digits, "8-Digit Serial", "الرقم الفردي", CheckStatus.PASS)
                segments += SerialTokenSegment(suffix, if (hasStar) "Star Note ★" else "Suffix", if (hasStar) "ورقة بديلة ★" else "اللاحقة", suffixStatus)

                checks += SerialCheckRow("digit_count", "Character Count", "عدد الخانات", "Valid 11-char (Modern)", "متوافق (11 خانة حديثة)", CheckStatus.PASS)
                checks += SerialCheckRow(
                    "series_prefix",
                    "Series Prefix",
                    "بادئة السلسلة",
                    seriesRule?.let { "$seriesChar → Series ${it.seriesYear}" } ?: "Unknown ($seriesChar)",
                    seriesRule?.let { "$seriesChar ← سلسلة ${it.seriesYear}" } ?: "غير معروف ($seriesChar)",
                    seriesStatus
                )

                if (detectedSignatures != null) {
                    checks += SerialCheckRow(
                        id = "signatures",
                        labelEn = "Expected Signatures",
                        labelAr = "التوقيعات الموثقة",
                        resultEn = detectedSignatures,
                        resultAr = detectedSignatures,
                        status = CheckStatus.PASS
                    )
                }

                if (!input.printedSeriesYear.isNullOrBlank() && seriesRule != null) {
                    val printedClean = input.printedSeriesYear.trim().uppercase()
                    if (printedClean != seriesRule.seriesYear.uppercase()) {
                        criticalContradiction = true
                        seriesCompatScore -= 0.45f
                        checks += SerialCheckRow(
                            "series_cross",
                            "Printed Series Match",
                            "تطابق سنة السلسلة",
                            "CONTRADICTION: Prefix=$seriesChar (${seriesRule.seriesYear}) vs Printed=$printedClean",
                            "تناقض: البادئة $seriesChar (${seriesRule.seriesYear}) مقابل المطبوع $printedClean",
                            CheckStatus.CONTRADICTION
                        )
                        anomaliesEn += "Critical contradiction: Serial prefix '$seriesChar' denotes Series ${seriesRule.seriesYear}, but note reads Series $printedClean."
                        anomaliesAr += "تناقض حرج: بادئة الرقم '$seriesChar' تشير لسلسلة ${seriesRule.seriesYear} بينما المطبوع $printedClean."
                    } else {
                        checks += SerialCheckRow(
                            "series_cross",
                            "Printed Series Match",
                            "تطابق سنة السلسلة",
                            "Consistent (${seriesRule.seriesYear})",
                            "متطابق (${seriesRule.seriesYear})",
                            CheckStatus.PASS
                        )
                    }
                } else {
                    checks += SerialCheckRow(
                        "series_cross",
                        "Printed Series Match",
                        "تطابق سنة السلسلة",
                        "Needs visual/manual series check",
                        "تحتاج مطابقة السنة المطبوعة",
                        CheckStatus.NEEDS_MATCH
                    )
                }

                verifyReserveIndicator(bankChar, input.federalReserveIndicator, checks, anomaliesEn, anomaliesAr) {
                    criticalContradiction = true
                    seriesCompatScore -= 0.40f
                }
            }
        }

        verifyUsdPlateAndPosition(input, checks, anomaliesEn, anomaliesAr) {
            seriesCompatScore -= 0.12f
        }

        val starRule = catalog.starNoteRules.firstOrNull { it.currency == CurrencyCode.USD }
        checks += SerialCheckRow(
            "star",
            "Star Replacement ★",
            "علامة النجمة ★",
            if (hasStar) (starRule?.meaningEn ?: "Replacement Star Note") else "Standard issue run",
            if (hasStar) (starRule?.meaningAr ?: "ورقة بديلة ★") else "إصدار قياسي بدون نجمة",
            CheckStatus.PASS
        )

        val dupPenalty = handleDuplicateCheckRow(input, normalized, checks, anomaliesEn, anomaliesAr)
        val ocrScore = (input.ocrPassAgreementScore - ambiguityCount * 0.04f).coerceIn(0.35f, 0.99f)
        val pq = input.measuredPrintQuality ?: defaultQuality(0.92f)
        val totalScore = (
            structuralScore.coerceIn(0f, 1f) * 0.32f +
                ocrScore * 0.16f +
                seriesCompatScore.coerceIn(0f, 1f) * 0.28f +
                visualScore.coerceIn(0f, 1f) * 0.24f -
                dupPenalty
            ).coerceIn(0f, 1f)

        val fivePart = buildFivePart(
            structScore = structuralScore.coerceIn(0f, 1f),
            structEn = if (structuralScore >= 0.8f) "All character counts, Bank & BEP suffix exclusions valid." else "Structural violation detected against denomination rules.",
            structAr = if (structuralScore >= 0.8f) "بنية الخانات وبنك الاحتياطي وقواعد الاستبعاد متوافقة." else "تم رصد مخالفة لبنية الفئة النقدية.",
            ocrScore = ocrScore,
            ambiguities = ambiguityCount,
            seriesScore = seriesCompatScore.coerceIn(0f, 1f),
            seriesEn = detectedSeriesYear?.let { "Mapped to Series $it" } ?: "Series compatibility partially unverified",
            seriesAr = detectedSeriesYear?.let { "متطابق مع سلسلة $it" } ?: "التوافق مع السلسلة جزئي",
            visualScore = visualScore.coerceIn(0f, 1f),
            visualEn = "Baseline ${(pq.baselineAlignment * 100).toInt()}% • Stroke ${(pq.strokeUniformity * 100).toInt()}%",
            visualAr = "استقامة السطر ${(pq.baselineAlignment * 100).toInt()}% • انتظام الطباعة ${(pq.strokeUniformity * 100).toInt()}%",
            dupClass = input.duplicateClassification
        )

        return SerialAnalysisResult(
            rawExtracted = raw,
            normalizedSerial = normalized,
            secondarySerial = secondaryNorm,
            currency = CurrencyCode.USD,
            denomination = input.denomination,
            isStarNote = hasStar,
            detectedSeriesYear = detectedSeriesYear,
            detectedReserveBank = detectedBankDesc,
            detectedSignatureOfficials = detectedSignatures,
            glyphs = glyphs,
            segments = segments,
            checks = checks,
            anomaliesEn = anomaliesEn,
            anomaliesAr = anomaliesAr,
            printQuality = pq,
            fivePartReport = fivePart,
            overallScore = totalScore,
            hasCriticalContradiction = criticalContradiction
        )
    }

    private fun analyzeSar(
        input: SerialAuditInput,
        raw: String,
        normalized: String,
        secondaryNorm: String?
    ): SerialAnalysisResult {
        val checks = mutableListOf<SerialCheckRow>()
        val segments = mutableListOf<SerialTokenSegment>()
        val anomaliesEn = mutableListOf<String>()
        val anomaliesAr = mutableListOf<String>()
        val glyphs = buildGlyphList(normalized, CurrencyCode.SAR)
        val ambiguityCount = glyphs.count { it.isAmbiguous }
        val pq = input.measuredPrintQuality ?: defaultQuality(0.90f)

        if (raw.isBlank()) {
            checks += SerialCheckRow("read", "Serial Read", "قراءة الرقم", "Not readable / missing", "غير مقروء / مفقود", CheckStatus.UNAVAILABLE)
            return SerialAnalysisResult(
                rawExtracted = raw,
                normalizedSerial = "",
                secondarySerial = secondaryNorm,
                currency = CurrencyCode.SAR,
                denomination = input.denomination,
                isStarNote = false,
                detectedSeriesYear = input.catalogEntry?.issueYear,
                detectedReserveBank = null,
                glyphs = emptyList(),
                segments = emptyList(),
                checks = checks,
                anomaliesEn = anomaliesEn,
                anomaliesAr = anomaliesAr,
                printQuality = pq,
                fivePartReport = buildFivePart(
                    structScore = 0.25f,
                    structEn = "SAR serial unreadable or missing",
                    structAr = "الرقم التسلسلي السعودي غير مقروء",
                    ocrScore = 0.25f,
                    ambiguities = 0,
                    seriesScore = 0.7f,
                    seriesEn = "SAMA prefix meanings unpublished; rule engine deferred",
                    seriesAr = "دلالات البادئة غير منشورة من البنك المركزي السعودي",
                    visualScore = 0.5f,
                    visualEn = "Visual serial metrics unavailable",
                    visualAr = "تعذر القياس البصري للرقم",
                    dupClass = input.duplicateClassification
                ),
                overallScore = 0.25f,
                hasCriticalContradiction = false
            )
        }

        var structuralScore = 1.0f
        var visualScore = (pq.strokeUniformity + pq.baselineAlignment + pq.characterSpacing + pq.inkClarity) / 4f
        var criticalContradiction = false

        val hasArabicDigits = raw.any { it in '\u0660'..'\u0669' }
        val hasWesternDigits = raw.any { it in '0'..'9' }
        val scriptMixed = hasArabicDigits && hasWesternDigits
        val scriptStatus = if (scriptMixed) {
            structuralScore -= 0.35f
            criticalContradiction = true
            anomaliesEn += "Mixed Eastern Arabic and Western numerals inside a single SAR serial string."
            anomaliesAr += "خلط غير طبيعي بين الأرقام العربية الشرقية والغربية داخل نفس الرقم التسلسلي السعودي."
            CheckStatus.CONTRADICTION
        } else CheckStatus.PASS

        checks += SerialCheckRow(
            "read",
            "Serial Read & Script",
            "قراءة الرقم ونوع الخط",
            if (scriptMixed) "$normalized (Mixed Numerals)" else "$normalized (${if (hasArabicDigits) "Eastern Arabic" else "Western"})",
            if (scriptMixed) "$normalized (أرقام مختلطة)" else "$normalized (${if (hasArabicDigits) "أرقام عربية" else "أرقام غربية"})",
            scriptStatus
        )

        val digitCount = normalized.count { it.isDigit() }
        val alphaTokens = normalized.filter { it.isLetter() }
        val lengthValid = digitCount in 6..10
        if (!lengthValid) {
            structuralScore -= 0.30f
            anomaliesEn += "SAR digit count ($digitCount) outside documented range (6–10 digits)."
            anomaliesAr += "عدد الأرقام ($digitCount) خارج النطاق الموثق المعتاد للأوراق السعودية (6–10 أرقام)."
        }

        if (alphaTokens.isNotEmpty()) {
            segments += SerialTokenSegment(alphaTokens, "Prefix Block (Unpublished Meanings)", "بادئة الحروف (غير منشورة الدلالة)", CheckStatus.PASS)
        }
        val digitBlock = normalized.filter { it.isDigit() || it == '/' }
        if (digitBlock.isNotEmpty()) {
            segments += SerialTokenSegment(digitBlock, "Serial Digits", "الرقم التسلسلي", if (lengthValid) CheckStatus.PASS else CheckStatus.WARNING)
        }

        checks += SerialCheckRow(
            "sar_structure",
            "Documented Structure",
            "بنية الإصدار الموثقة",
            if (lengthValid) "Consistent ($digitCount digits)" else "Unusual digit count ($digitCount)",
            if (lengthValid) "متوافقة ($digitCount أرقام)" else "عدد أرقام غير معتاد ($digitCount)",
            if (lengthValid) CheckStatus.PASS else CheckStatus.WARNING
        )

        // Char-by-char dual-serial match (top-right vs bottom-left on SAR)
        if (!secondaryNorm.isNullOrBlank()) {
            if (secondaryNorm != normalized) {
                criticalContradiction = true
                visualScore -= 0.45f
                val diffDesc = buildCharDiffSummary(normalized, secondaryNorm)
                checks += SerialCheckRow(
                    "sar_dual",
                    "Opposite Serial Match",
                    "تطابق الرقم المقابل",
                    "CONTRADICTION: $normalized vs $secondaryNorm ($diffDesc)",
                    "تناقض: العلوي $normalized مقابل السفلي $secondaryNorm ($diffDesc)",
                    CheckStatus.CONTRADICTION
                )
                anomaliesEn += "SAR top-right and bottom-left serials do not match ($diffDesc)."
                anomaliesAr += "تناقض خطير بين الرقم العلوي والسفلي على الورقة السعودية ($diffDesc)."
            } else {
                checks += SerialCheckRow("sar_dual", "Opposite Serial Match", "تطابق الرقم المقابل", "Matched ($normalized)", "متطابقان ($normalized)", CheckStatus.PASS)
            }
        } else {
            checks += SerialCheckRow(
                "sar_dual",
                "Opposite Serial Match",
                "تطابق الرقم المقابل",
                "Single serial read (verify opposite corner)",
                "تمت قراءة رقم واحد (يوصى بمطابقة الزاوية المقابلة)",
                CheckStatus.NEEDS_MATCH
            )
        }

        checks += SerialCheckRow(
            "sama_epistemic",
            "Prefix Year / Mint",
            "دلالة أحرف البادئة",
            "Not inferred (unpublished in SAMA public docs)",
            "لا يُفترض معناها (غير منشورة بوثائق البنك المركزي السعودي)",
            CheckStatus.PASS
        )

        val dupPenalty = handleDuplicateCheckRow(input, normalized, checks, anomaliesEn, anomaliesAr)
        val ocrScore = (input.ocrPassAgreementScore - ambiguityCount * 0.04f).coerceIn(0.35f, 0.99f)
        val totalScore = (
            structuralScore.coerceIn(0f, 1f) * 0.35f +
                ocrScore * 0.18f +
                0.85f * 0.17f +
                visualScore.coerceIn(0f, 1f) * 0.30f -
                dupPenalty
            ).coerceIn(0f, 1f)

        val fivePart = buildFivePart(
            structScore = structuralScore.coerceIn(0f, 1f),
            structEn = if (lengthValid && !scriptMixed) "Valid SAR numeral script and digit count ($digitCount)." else "Script mix or atypical SAR digit count.",
            structAr = if (lengthValid && !scriptMixed) "الخط وعدد الخانات ($digitCount) متوافقان مع التوثيق." else "خلط في الأرقام أو طول غير معتاد.",
            ocrScore = ocrScore,
            ambiguities = ambiguityCount,
            seriesScore = 0.85f,
            seriesEn = "SAMA public docs do not publish prefix-to-year tables; epistemic guard active.",
            seriesAr = "البنك المركزي السعودي لا ينشر جدول دلالات الحروف؛ لا توجد تخمينات غير موثقة.",
            visualScore = visualScore.coerceIn(0f, 1f),
            visualEn = if (secondaryNorm == normalized) "Top-right & bottom-left serials verified equal." else "Print uniformity ${(pq.strokeUniformity * 100).toInt()}%",
            visualAr = if (secondaryNorm == normalized) "تم التحقق من تطابق الرقمين العلوي والسفلي." else "انتظام الطباعة ${(pq.strokeUniformity * 100).toInt()}%",
            dupClass = input.duplicateClassification
        )

        return SerialAnalysisResult(
            rawExtracted = raw,
            normalizedSerial = normalized,
            secondarySerial = secondaryNorm,
            currency = CurrencyCode.SAR,
            denomination = input.denomination,
            isStarNote = false,
            detectedSeriesYear = input.catalogEntry?.issueYear,
            detectedReserveBank = null,
            glyphs = glyphs,
            segments = segments,
            checks = checks,
            anomaliesEn = anomaliesEn,
            anomaliesAr = anomaliesAr,
            printQuality = pq,
            fivePartReport = fivePart,
            overallScore = totalScore,
            hasCriticalContradiction = criticalContradiction
        )
    }

    private fun handleDuplicateCheckRow(
        input: SerialAuditInput,
        normalized: String,
        checks: MutableList<SerialCheckRow>,
        anomaliesEn: MutableList<String>,
        anomaliesAr: MutableList<String>
    ): Float {
        return when (input.duplicateClassification) {
            DuplicateClassification.SAME_NOTE_RESCANNED -> {
                checks += SerialCheckRow(
                    "duplicate",
                    "Local Duplicate Intelligence",
                    "ذكاء التكرار المحلي",
                    "Same Note Rescanned (matching dHash)",
                    "إعادة فحص لنفس الورقة (تطابق البصمة البصرية)",
                    CheckStatus.WARNING
                )
                0.04f
            }
            DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE -> {
                checks += SerialCheckRow(
                    "duplicate",
                    "Local Duplicate Intelligence",
                    "ذكاء التكرار المحلي",
                    "CRITICAL: Same Serial on Different Note Image!",
                    "تحذير حرج: نفس الرقم التسلسلي على ورقة/صورة مختلفة!",
                    CheckStatus.CONTRADICTION
                )
                anomaliesEn += "Duplicate Intelligence: Serial $normalized already exists in Vault with a DIFFERENT perceptual image hash (possible counterfeit cloning)."
                anomaliesAr += "ذكاء التكرار: الرقم $normalized مسجل مسبقاً في السجل لكن ببصمة بصرية مختلفة (احتمال استنساخ/تزوير)."
                0.35f
            }
            DuplicateClassification.POSSIBLE_DUPLICATE -> {
                checks += SerialCheckRow(
                    "duplicate",
                    "Local Duplicate Intelligence",
                    "ذكاء التكرار المحلي",
                    "Possible Duplicate (partial hash/angle drift)",
                    "تكرار محتمل (اختلاف جزئي في زاوية التصوير)",
                    CheckStatus.WARNING
                )
                anomaliesEn += "Serial $normalized matches an existing Vault entry with moderate visual difference."
                anomaliesAr += "الرقم $normalized مطابق لسجل سابق مع اختلاف بصري متوسط."
                0.15f
            }
            DuplicateClassification.INSUFFICIENT_COMPARISON -> {
                checks += SerialCheckRow(
                    "duplicate",
                    "Local Duplicate Intelligence",
                    "ذكاء التكرار المحلي",
                    "Same Serial (Insufficient image comparison data)",
                    "رقم مكرر (بيانات المقارنة البصرية غير كافية)",
                    CheckStatus.WARNING
                )
                0.10f
            }
            DuplicateClassification.UNIQUE -> 0f
        }
    }

    private fun buildCharDiffSummary(a: String, b: String): String {
        val diffs = mutableListOf<String>()
        val maxLen = maxOf(a.length, b.length)
        for (i in 0 until maxLen) {
            val ca = a.getOrNull(i) ?: '∅'
            val cb = b.getOrNull(i) ?: '∅'
            if (ca != cb) diffs += "#${i + 1}:$ca≠$cb"
        }
        return diffs.take(4).joinToString(", ")
    }

    private fun buildGlyphList(serial: String, currency: CurrencyCode): List<SerialCharGlyph> {
        return serial.mapIndexed { idx, ch ->
            val hint = when (ch) {
                '0', 'O' -> "0/O"
                '1', 'I' -> "1/I"
                '5', 'S' -> "5/S"
                '8', 'B' -> "8/B"
                else -> null
            }
            val unexpectedNonDigit = currency == CurrencyCode.USD && idx in 2..9 && !ch.isDigit() && serial.length == 11
            SerialCharGlyph(
                index = idx,
                char = ch,
                isAmbiguous = hint != null,
                ambiguityHint = hint,
                status = if (unexpectedNonDigit) CheckStatus.CONTRADICTION else if (hint != null) CheckStatus.WARNING else CheckStatus.PASS
            )
        }
    }

    private fun verifyReserveIndicator(
        bankChar: String,
        indicator: String?,
        checks: MutableList<SerialCheckRow>,
        anomaliesEn: MutableList<String>,
        anomaliesAr: MutableList<String>,
        onContradiction: () -> Unit
    ) {
        val districtFromSerial = catalog.usdFederalReserveDistricts.firstOrNull { it.letter == bankChar }
        if (indicator.isNullOrBlank()) {
            checks += SerialCheckRow(
                "frb_match",
                "Reserve Bank Seal",
                "مؤشر الاحتياطي الفيدرالي",
                districtFromSerial?.let { "Serial=${it.code} (${it.cityEn}) — verify seal" } ?: "Needs verification",
                districtFromSerial?.let { "الرقم=${it.code} (${it.cityAr}) — يحتاج تحقق المؤشر" } ?: "يحتاج تحقق",
                CheckStatus.NEEDS_MATCH
            )
            return
        }

        val cleanInd = indicator.trim().uppercase()
        val matchedDistrict = catalog.usdFederalReserveDistricts.firstOrNull {
            cleanInd == it.code || cleanInd == it.letter || cleanInd == it.number.toString() ||
                cleanInd.contains(it.cityEn.uppercase())
        }
        if (matchedDistrict != null && matchedDistrict.letter != bankChar) {
            onContradiction()
            checks += SerialCheckRow(
                "frb_match",
                "Reserve Bank Seal",
                "مؤشر الاحتياطي الفيدرالي",
                "CONTRADICTION: Serial=$bankChar vs Seal=${matchedDistrict.code}",
                "تناقض: حرف الرقم $bankChar يخالف المؤشر ${matchedDistrict.code}",
                CheckStatus.CONTRADICTION
            )
            anomaliesEn += "Federal Reserve mismatch: serial bank letter '$bankChar' contradicts seal '${matchedDistrict.code} ${matchedDistrict.cityEn}'."
            anomaliesAr += "تناقض بنك الاحتياطي: حرف الرقم '$bankChar' يخالف المؤشر '${matchedDistrict.code} ${matchedDistrict.cityAr}'."
        } else {
            checks += SerialCheckRow(
                "frb_match",
                "Reserve Bank Seal",
                "مؤشر الاحتياطي الفيدرالي",
                "Consistent (${districtFromSerial?.code ?: cleanInd})",
                "متطابق (${districtFromSerial?.code ?: cleanInd})",
                CheckStatus.PASS
            )
        }
    }

    private fun verifyUsdPlateAndPosition(
        input: SerialAuditInput,
        checks: MutableList<SerialCheckRow>,
        anomaliesEn: MutableList<String>,
        anomaliesAr: MutableList<String>,
        onWarning: () -> Unit
    ) {
        val pos = input.notePositionId?.trim()?.uppercase()
        if (!pos.isNullOrBlank()) {
            val validPos = Regex("^[A-H][1-6]$").matches(pos)
            if (!validPos) {
                onWarning()
                checks += SerialCheckRow("note_pos", "Note Position ID", "موضع الورقة باللوحة", "Unusual format '$pos' (expected A1–H6)", "تنسيق غير معتاد '$pos' (المتوقع A1–H6)", CheckStatus.WARNING)
                anomaliesEn += "Note Position Identifier '$pos' falls outside standard BEP sheet grid (A1–H6)."
                anomaliesAr += "مؤشر موضع الورقة '$pos' خارج شبكة لوحة الطباعة القياسية (A1–H6)."
            } else {
                checks += SerialCheckRow("note_pos", "Note Position ID", "موضع الورقة باللوحة", "Valid Sheet Coord ($pos)", "إحداثي لوحة صالح ($pos)", CheckStatus.PASS)
            }
        }
        val face = input.facePlateNumber?.trim()
        val back = input.backPlateNumber?.trim()
        if (!face.isNullOrBlank() || !back.isNullOrBlank()) {
            checks += SerialCheckRow("plates", "Face / Back Plates", "رقما اللوحة", "Face=${face ?: "—"} | Back=${back ?: "—"}", "الأمامية=${face ?: "—"} | الخلفية=${back ?: "—"}", CheckStatus.PASS)
        }
    }

    private fun buildFivePart(
        structScore: Float,
        structEn: String,
        structAr: String,
        ocrScore: Float,
        ambiguities: Int,
        seriesScore: Float,
        seriesEn: String,
        seriesAr: String,
        visualScore: Float,
        visualEn: String,
        visualAr: String,
        dupClass: DuplicateClassification
    ): SerialFivePartReport {
        val (dupEn, dupAr) = when (dupClass) {
            DuplicateClassification.UNIQUE -> "No duplicate serial in Local Vault" to "رقم فريد غير مسجل مسبقاً في السجل"
            DuplicateClassification.SAME_NOTE_RESCANNED -> "Same physical note re-scanned (matching visual hash)" to "إعادة فحص لنفس الورقة المسجلة"
            DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE -> "Same serial on DIFFERENT note image!" to "نفس الرقم التسلسلي على صورة ورقة مختلفة!"
            DuplicateClassification.POSSIBLE_DUPLICATE -> "Possible duplicate (verify visually)" to "تكرار محتمل يستوجب المراجعة"
            DuplicateClassification.INSUFFICIENT_COMPARISON -> "Same serial; optical comparison limited" to "نفس الرقم مع تعذر المقارنة البصرية"
        }
        return SerialFivePartReport(
            structuralValidityScore = structScore,
            structuralValiditySummaryEn = structEn,
            structuralValiditySummaryAr = structAr,
            ocrReliabilityScore = ocrScore,
            ocrAmbiguityCount = ambiguities,
            ocrReliabilitySummaryEn = "OCR Confidence ${(ocrScore * 100).toInt()}% • $ambiguities ambiguous glyph(s)",
            ocrReliabilitySummaryAr = "موثوقية القراءة ${(ocrScore * 100).toInt()}% • $ambiguities خانة متشابهة بصرياً",
            seriesCompatibilityScore = seriesScore,
            seriesCompatibilitySummaryEn = seriesEn,
            seriesCompatibilitySummaryAr = seriesAr,
            visualConsistencyScore = visualScore,
            visualConsistencySummaryEn = visualEn,
            visualConsistencySummaryAr = visualAr,
            duplicateStatus = dupClass,
            duplicateSummaryEn = dupEn,
            duplicateSummaryAr = dupAr
        )
    }

    fun normalizeSerial(input: String, starToggle: Boolean = false): String {
        val mapped = buildString {
            for (ch in input.trim()) {
                when (ch) {
                    in '\u0660'..'\u0669' -> append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                    in '\u06F0'..'\u06F9' -> append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                    ' ', '-', '_' -> Unit
                    '*' -> append('★')
                    else -> append(ch.uppercaseChar())
                }
            }
        }
        return if (starToggle && !mapped.endsWith("★") && mapped.isNotEmpty()) "$mapped★" else mapped
    }

    private fun defaultQuality(base: Float) = SerialPrintQuality(
        strokeUniformity = base,
        baselineAlignment = base,
        characterSpacing = base,
        inkClarity = base,
        wearToleranceApplied = false
    )
}

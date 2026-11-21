package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.serial.CheckStatus
import com.example.engine.vision.AnomalySeverity
import com.example.engine.vision.ForensicInspectionReport
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.components.ContradictionTypeChip
import com.example.ui.components.EfeFourOutputsGrid
import com.example.ui.components.ForensicOverlayDiagram
import com.example.ui.components.ScoreProgressRow
import com.example.ui.components.VerdictBanner
import com.example.ui.state.SentinelViewModel
import com.example.ui.theme.CalibratedEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.CrimsonSignal
import com.example.ui.theme.CrimsonSignalBg
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForensicReportScreen(
    reportId: String,
    viewModel: SentinelViewModel,
    report: ForensicInspectionReport?,
    language: AppLanguage,
    strings: AppStrings,
    onOpenSerialWorkbench: (String) -> Unit,
    onOpenComparisonLab: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val archivedBitmap by viewModel.archivedFrontBitmap.collectAsStateWithLifecycle()

    LaunchedEffect(reportId) {
        viewModel.loadReport(reportId)
    }

    BackHandler(onBack = onBack)

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.analyzingPipeline, color = MutedSteel)
        }
        return
    }

    val isAr = language == AppLanguage.AR
    var showRoiOverlay by remember { mutableStateOf(true) }
    var showFoldOverlay by remember { mutableStateOf(true) }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val currencyLabel = if (report.currency == CurrencyCode.SAR) strings.currencySar else strings.currencyUsd
    val materialLabel = when (report.observedMaterial) {
        SubstrateMaterial.POLYMER -> strings.materialPolymer
        SubstrateMaterial.COTTON_LINEN -> strings.materialCotton
        SubstrateMaterial.UNCERTAIN -> strings.statusUnclearShort
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Friendly Header Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.reportHeaderSubtitle, style = MaterialTheme.typography.labelSmall, color = OpticalCyan)
                    Text(
                        text = "$currencyLabel ${if (report.currency == CurrencyCode.USD) "$" else ""}${report.denominationValue}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CoolWhite
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.exportPdf(context, report) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_pdf_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.exportPdfLabel)
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("report_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.backToScan)
                    }
                }
            }

            // Plain Verdict Banner
            VerdictBanner(
                verdict = report.verdict,
                strings = strings,
                confidencePercent = report.confidencePercent,
                coveragePercent = report.usableCoveragePercent
            )

            // Quick Everyday Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.quickSummaryTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    KeyValueRow(strings.designFamily, if (isAr) report.designFamilyAr else report.designFamilyEn)
                    KeyValueRow(strings.seriesYear, report.seriesOrIssueYear)
                    KeyValueRow(strings.materialHeader, materialLabel)
                    KeyValueRow(strings.primarySerialLabel, report.serialAnalysis.normalizedSerial.ifBlank { "—" })
                    HorizontalDivider(color = HairlineSlate.copy(alpha = 0.6f))
                    EfeFourOutputsGrid(outputs = report.efeOutputs, strings = strings)
                }
            }

            if (report.isLocalDuplicate) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CrimsonSignalBg,
                    border = BorderStroke(1.dp, CrimsonSignal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = CrimsonSignal)
                        Text(
                            text = if (isAr) report.serialAnalysis.fivePartReport.duplicateSummaryAr else report.serialAnalysis.fivePartReport.duplicateSummaryEn,
                            color = CoolWhite,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Plain-language Findings & Notes
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (report.anomalies.any { it.severity == AnomalySeverity.CRITICAL }) CrimsonSignal.copy(alpha = 0.6f) else HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.contradictionCardTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    if (report.anomalies.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CalibratedEmerald)
                            Text(strings.noContradictionsFound, style = MaterialTheme.typography.bodyMedium, color = CalibratedEmerald)
                        }
                    } else {
                        report.anomalies.forEach { anomaly ->
                            val (icon, tint) = when (anomaly.severity) {
                                AnomalySeverity.CRITICAL -> Icons.Filled.Error to CrimsonSignal
                                AnomalySeverity.WARNING -> Icons.Filled.Warning to ForensicAmber
                                AnomalySeverity.INFO -> Icons.Filled.Info to OpticalCyan
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RecessedPanel,
                                border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            ContradictionTypeChip(type = anomaly.contradictionType, strings = strings)
                                            Text(
                                                text = if (isAr) anomaly.titleAr else anomaly.titleEn,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = tint
                                            )
                                        }
                                        Text(
                                            text = if (isAr) anomaly.detailAr else anomaly.detailEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CoolWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Helpful Follow-up Actions: Fix Serial + Compare Specs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { onOpenSerialWorkbench(report.reportId) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("open_workbench_btn")
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.openSerialWorkbench)
                }
                FilledTonalButton(
                    onClick = { onOpenComparisonLab(report.reportId) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("open_compare_btn")
                ) {
                    Icon(Icons.Filled.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.openInCompareLab)
                }
            }

            // Collapsible Detailed Breakdown Toggle
            OutlinedButton(
                onClick = { showAdvancedDetails = !showAdvancedDetails },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_advanced_details_btn")
            ) {
                Icon(
                    imageVector = if (showAdvancedDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (showAdvancedDetails) strings.advancedDetailsHide else strings.advancedDetailsShow)
            }

            AnimatedVisibility(visible = showAdvancedDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 3 Key Questions
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuestionItem(strings.q1Title, if (isAr) report.threeQuestions.q1IdentityAr else report.threeQuestions.q1IdentityEn, OpticalCyan)
                            HorizontalDivider(color = HairlineSlate)
                            QuestionItem(strings.q2Title, if (isAr) report.threeQuestions.q2SpecMatchAr else report.threeQuestions.q2SpecMatchEn, if (report.anomalies.isEmpty()) CalibratedEmerald else ForensicAmber)
                            HorizontalDivider(color = HairlineSlate)
                            QuestionItem(strings.q3Title, if (isAr) report.threeQuestions.q3AuthenticityAr else report.threeQuestions.q3AuthenticityEn, if (report.anomalies.any { it.severity == AnomalySeverity.CRITICAL }) CrimsonSignal else OpticalCyan)
                        }
                    }

                    // Detailed Bill Dates & Optical Note
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.identificationCardTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                            KeyValueRow(strings.designYearLabel, report.designYear)
                            KeyValueRow(strings.printYearLabel, report.printYearRange)
                            report.identityResult?.let { idRes ->
                                Text(
                                    text = if (isAr) idRes.materialOpticalLimitationAr else idRes.materialOpticalLimitationEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForensicAmber
                                )
                            }
                            Text(
                                text = if (isAr) report.multiResolutionSummaryAr else report.multiResolutionSummaryEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = OpticalCyan
                            )
                        }
                    }

                    // Serial Checks Table
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.sieCardTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                            if (report.serialAnalysis.segments.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    report.serialAnalysis.segments.forEach { seg ->
                                        val segTint = when (seg.status) {
                                            CheckStatus.PASS -> CalibratedEmerald
                                            CheckStatus.CONTRADICTION -> CrimsonSignal
                                            CheckStatus.WARNING -> ForensicAmber
                                            else -> MutedSteel
                                        }
                                        Surface(shape = RoundedCornerShape(8.dp), color = RecessedPanel, border = BorderStroke(1.dp, segTint.copy(alpha = 0.6f))) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(seg.value, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = CoolWhite)
                                                Text(if (isAr) seg.roleAr else seg.roleEn, style = MaterialTheme.typography.labelSmall, color = segTint)
                                            }
                                        }
                                    }
                                }
                            }

                            report.serialAnalysis.checks.forEach { row ->
                                val statusColor = when (row.status) {
                                    CheckStatus.PASS -> CalibratedEmerald
                                    CheckStatus.CONTRADICTION -> CrimsonSignal
                                    CheckStatus.WARNING, CheckStatus.NEEDS_MATCH -> ForensicAmber
                                    CheckStatus.UNAVAILABLE -> MutedSteel
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isAr) row.labelAr else row.labelEn, style = MaterialTheme.typography.bodyMedium, color = MutedSteel, modifier = Modifier.weight(0.42f))
                                    Text(if (isAr) row.resultAr else row.resultEn, style = MaterialTheme.typography.labelLarge, color = statusColor, modifier = Modifier.weight(0.58f))
                                }
                                HorizontalDivider(color = HairlineSlate.copy(alpha = 0.5f))
                            }
                        }
                    }

                    // Overlay Diagram & Coverage Meters
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.foldHeatmapTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = showRoiOverlay, onClick = { showRoiOverlay = !showRoiOverlay }, label = { Text(strings.showRoiOverlay) })
                                FilterChip(selected = showFoldOverlay, onClick = { showFoldOverlay = !showFoldOverlay }, label = { Text(strings.showFoldOverlay) })
                            }
                            ForensicOverlayDiagram(
                                imageUri = report.primaryImageUri,
                                archivedBitmap = archivedBitmap,
                                roiEvaluations = report.roiEvaluations,
                                conditionReport = report.conditionReport,
                                showRois = showRoiOverlay,
                                showFoldMask = showFoldOverlay
                            )
                            ScoreProgressRow(strings.captureQualityScore, report.conditionReport.captureQualityScore)
                            ScoreProgressRow(strings.usableCoverageLabel, report.usableCoveragePercent / 100f)
                            ScoreProgressRow(strings.evidenceCompletenessLabel, report.evidenceCompletenessPercent / 100f)

                            if (report.conditionReport.occludedRoiIds.isNotEmpty()) {
                                Text(
                                    text = "${strings.occludedRegionsWarning} ${report.conditionReport.occludedRoiIds.joinToString()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ForensicAmber
                                )
                            }
                        }
                    }

                    // Detailed Visual Score Breakdown
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.multiEvidenceBreakdown, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                            ScoreProgressRow(strings.geometryScore, report.engineScores.geometryScore)
                            ScoreProgressRow(strings.layoutScore, report.engineScores.layoutScore)
                            ScoreProgressRow(strings.colorScore, report.engineScores.colorScore)
                            ScoreProgressRow(strings.textureScore, report.engineScores.textureScore)
                            ScoreProgressRow(strings.printStructureScore, report.engineScores.printStructureScore)
                            ScoreProgressRow(strings.securityFeatureScore, report.engineScores.securityFeatureScore)
                            ScoreProgressRow(strings.serialScore, report.engineScores.serialScore)
                            ScoreProgressRow(strings.conditionScore, report.engineScores.conditionScore)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuestionItem(title: String, body: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = accent)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = CoolWhite)
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = CoolWhite
        )
    }
}

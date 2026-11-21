package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.catalog.CurrencyCode
import com.example.engine.serial.CheckStatus
import com.example.engine.vision.ForensicInspectionReport
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.components.ScoreProgressRow
import com.example.ui.state.SentinelViewModel
import com.example.ui.theme.CalibratedEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.CrimsonSignal
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SerialWorkbenchScreen(
    reportId: String,
    viewModel: SentinelViewModel,
    report: ForensicInspectionReport?,
    language: AppLanguage,
    strings: AppStrings,
    onBack: () -> Unit
) {
    LaunchedEffect(reportId) { viewModel.loadReport(reportId) }
    BackHandler(onBack = onBack)

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.analyzingPipeline, color = MutedSteel)
        }
        return
    }

    val isAr = language == AppLanguage.AR
    var editPrimary by remember(report.serialAnalysis.normalizedSerial) {
        mutableStateOf(report.serialAnalysis.normalizedSerial)
    }
    var editSecondary by remember(report.serialAnalysis.secondarySerial) {
        mutableStateOf(report.serialAnalysis.secondarySerial.orEmpty())
    }
    var editFrb by remember(report.serialAnalysis.detectedReserveBank) { mutableStateOf("") }
    var editSeriesYear by remember(report.serialAnalysis.detectedSeriesYear) {
        mutableStateOf(report.serialAnalysis.detectedSeriesYear.orEmpty())
    }
    var editStar by remember(report.serialAnalysis.isStarNote) {
        mutableStateOf(report.serialAnalysis.isStarNote)
    }
    var editReason by remember { mutableStateOf("") }

    val scroll = rememberScrollState()
    val serial = report.serialAnalysis
    val five = serial.fivePartReport
    val ambiguousGlyphs = serial.glyphs.filter { it.isAmbiguous }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.workbenchTitle, style = MaterialTheme.typography.titleLarge, color = CoolWhite)
                    Text(strings.workbenchSubtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("workbench_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.backToScan)
                }
            }

            // Glyph-by-Glyph OCR Segmentation & Ambiguity Inspector
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = serial.normalizedSerial.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = OpticalCyan
                    )
                    if (serial.glyphs.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            serial.glyphs.forEach { glyph ->
                                val tint = when {
                                    glyph.status == CheckStatus.CONTRADICTION -> CrimsonSignal
                                    glyph.isAmbiguous -> ForensicAmber
                                    else -> CalibratedEmerald
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = RecessedPanel,
                                    border = BorderStroke(1.dp, tint.copy(alpha = 0.65f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = glyph.char.toString(),
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = CoolWhite
                                        )
                                        Text(
                                            text = glyph.ambiguityHint ?: "#${glyph.index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tint
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (ambiguousGlyphs.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = ForensicAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${strings.ocrAmbiguityWarning} ${ambiguousGlyphs.joinToString { "${it.char} (${it.ambiguityHint})" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForensicAmber
                            )
                        }
                    }
                }
            }

            // 5-Part Serial Evidence Breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.fivePartBreakdownTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    ScoreProgressRow(if (isAr) "1. تطابق شكل الرقم" else "1. Serial Format", five.structuralValidityScore)
                    Text(if (isAr) five.structuralValiditySummaryAr else five.structuralValiditySummaryEn, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                    ScoreProgressRow(if (isAr) "2. وضوح القراءة" else "2. Reading Clarity", five.ocrReliabilityScore)
                    Text(if (isAr) five.ocrReliabilitySummaryAr else five.ocrReliabilitySummaryEn, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                    ScoreProgressRow(if (isAr) "3. توافق سنة الإصدار" else "3. Series Match", five.seriesCompatibilityScore)
                    Text(if (isAr) five.seriesCompatibilitySummaryAr else five.seriesCompatibilitySummaryEn, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                    ScoreProgressRow(if (isAr) "4. جودة الطباعة والتطابق" else "4. Print & Match", five.visualConsistencyScore)
                    Text(if (isAr) five.visualConsistencySummaryAr else five.visualConsistencySummaryEn, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                    HorizontalDivider(color = HairlineSlate)
                    Text(
                        text = "5. ${if (isAr) five.duplicateSummaryAr else five.duplicateSummaryEn}",
                        style = MaterialTheme.typography.labelLarge,
                        color = OpticalCyan
                    )
                }
            }

            // Manual Correction & Audit Override Form
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = OpticalCyan)
                        Text(strings.applyCorrectionAndReaudit, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    }

                    OutlinedTextField(
                        value = editPrimary,
                        onValueChange = { editPrimary = it },
                        label = { Text(strings.primarySerialLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workbench_primary_input")
                    )

                    OutlinedTextField(
                        value = editSecondary,
                        onValueChange = { editSecondary = it },
                        label = { Text(strings.secondarySerialLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workbench_secondary_input")
                    )

                    if (report.currency == CurrencyCode.USD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editFrb,
                                onValueChange = { editFrb = it },
                                label = { Text(strings.frIndicatorLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editSeriesYear,
                                onValueChange = { editSeriesYear = it },
                                label = { Text(strings.seriesYearLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = ForensicAmber)
                                Text(strings.starNoteToggle, color = CoolWhite)
                            }
                            Switch(checked = editStar, onCheckedChange = { editStar = it })
                        }
                    }

                    OutlinedTextField(
                        value = editReason,
                        onValueChange = { editReason = it },
                        label = { Text(strings.correctionNoteLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workbench_reason_input")
                    )

                    Button(
                        onClick = {
                            viewModel.reAuditReportSerial(
                                correctedPrimary = editPrimary,
                                correctedSecondary = editSecondary.takeIf { it.isNotBlank() },
                                frIndicator = editFrb.takeIf { it.isNotBlank() },
                                seriesYear = editSeriesYear.takeIf { it.isNotBlank() },
                                starToggle = editStar,
                                auditNote = editReason
                            )
                            editReason = ""
                        },
                        enabled = editPrimary.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = OpticalCyan, contentColor = ObsidianBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("workbench_save_btn")
                    ) {
                        Text(strings.applyCorrectionAndReaudit, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Audit Trail History
            if (report.manualAuditTrail.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, HairlineSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = CalibratedEmerald)
                            Text(strings.auditTrailTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                        }
                        report.manualAuditTrail.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RecessedPanel,
                                border = BorderStroke(1.dp, HairlineSlate),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${entry.previousValue} → ${entry.newValue}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = CalibratedEmerald,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(entry.note, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

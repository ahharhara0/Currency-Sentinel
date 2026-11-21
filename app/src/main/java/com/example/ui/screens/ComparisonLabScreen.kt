package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.catalog.CurrencyMasterCatalog
import com.example.engine.vision.ForensicInspectionReport
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.state.SentinelViewModel
import com.example.ui.theme.CalibratedEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.CrimsonSignal
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel

@Composable
fun ComparisonLabScreen(
    reportId: String?,
    viewModel: SentinelViewModel,
    report: ForensicInspectionReport?,
    catalog: CurrencyMasterCatalog,
    language: AppLanguage,
    strings: AppStrings,
    onBack: () -> Unit
) {
    LaunchedEffect(reportId) {
        if (!reportId.isNullOrBlank()) viewModel.loadReport(reportId)
    }
    BackHandler(onBack = onBack)

    val isAr = language == AppLanguage.AR
    val scroll = rememberScrollState()
    val refEntry = report?.let { rep ->
        catalog.entries.firstOrNull { it.id == rep.catalogEntryId }
    } ?: catalog.entries.first()

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Compare, contentDescription = null, tint = OpticalCyan)
                        Text(strings.comparisonTitle, style = MaterialTheme.typography.titleLarge, color = CoolWhite)
                    }
                    Text(strings.comparisonSubtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("compare_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.backToScan)
                }
            }

            // Side-by-Side Spec Comparison Table
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.inspectedNoteColumn, style = MaterialTheme.typography.labelLarge, color = OpticalCyan, modifier = Modifier.weight(1f))
                        Text(strings.referenceSpecColumn, style = MaterialTheme.typography.labelLarge, color = CalibratedEmerald, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = HairlineSlate)

                    val refCurr = if (refEntry.currency == com.example.catalog.CurrencyCode.SAR) strings.currencySar else strings.currencyUsd
                    val repCurr = report?.let { if (it.currency == com.example.catalog.CurrencyCode.SAR) strings.currencySar else strings.currencyUsd }
                    val refMat = when (refEntry.material) {
                        com.example.catalog.SubstrateMaterial.POLYMER -> strings.materialPolymer
                        com.example.catalog.SubstrateMaterial.COTTON_LINEN -> strings.materialCotton
                        com.example.catalog.SubstrateMaterial.UNCERTAIN -> strings.statusUnclearShort
                    }
                    val repMat = report?.let {
                        when (it.observedMaterial) {
                            com.example.catalog.SubstrateMaterial.POLYMER -> strings.materialPolymer
                            com.example.catalog.SubstrateMaterial.COTTON_LINEN -> strings.materialCotton
                            com.example.catalog.SubstrateMaterial.UNCERTAIN -> strings.statusUnclearShort
                        }
                    }
                    val provLabel = when (refEntry.provenance.tier) {
                        com.example.catalog.ProvenanceTier.OFFICIALLY_DOCUMENTED -> if (isAr) "موثق رسمياً" else "Official Spec"
                        com.example.catalog.ProvenanceTier.OFFICIALLY_ILLUSTRATED -> if (isAr) "مرجع مصوّر" else "Official Guide"
                        else -> if (isAr) "مرجع موثوق" else "Reference Data"
                    }

                    ComparePairRow(
                        label = strings.detectedCurrency,
                        observed = report?.let { "$repCurr ${it.denominationValue}" } ?: "—",
                        reference = "$refCurr ${refEntry.denominationValue}"
                    )
                    ComparePairRow(
                        label = strings.materialHeader,
                        observed = repMat ?: "—",
                        reference = refMat
                    )
                    ComparePairRow(
                        label = strings.dimensionsLabel,
                        observed = report?.let {
                            val pct = (it.engineScores.geometryScore * 100).toInt()
                            "${if (pct >= 78) strings.statusOkShort else strings.statusWarningShort} ($pct%)"
                        } ?: "—",
                        reference = "${refEntry.widthMm} × ${refEntry.heightMm} mm"
                    )
                    ComparePairRow(
                        label = strings.seriesYear,
                        observed = report?.seriesOrIssueYear ?: "—",
                        reference = "${refEntry.issueYear} (${refEntry.printYearRange})"
                    )
                    ComparePairRow(
                        label = strings.serialRulesHeader,
                        observed = report?.serialAnalysis?.normalizedSerial?.ifBlank { "—" } ?: "—",
                        reference = refEntry.serialFormatHint
                    )
                    ComparePairRow(
                        label = strings.provenanceHeader,
                        observed = if (isAr) "محدّث محلياً" else "Verified Offline",
                        reference = "$provLabel (${refEntry.provenance.source})"
                    )
                }
            }

            // Key Security Areas
            if (report != null && report.roiEvaluations.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, HairlineSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.compareRoiBreakdownTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                        report.roiEvaluations.forEach { roi ->
                            val tint = when {
                                roi.isPositionShifted -> CrimsonSignal
                                !roi.inspectionAvailable -> ForensicAmber
                                else -> CalibratedEmerald
                            }
                            val roiStatus = when {
                                roi.isPositionShifted -> strings.statusWarningShort
                                !roi.inspectionAvailable -> strings.statusUnclearShort
                                else -> strings.statusOkShort
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RecessedPanel,
                                border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = if (isAr) roi.labelAr else roi.labelEn,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CoolWhite
                                        )
                                        Text(
                                            text = roiStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = tint
                                        )
                                    }
                                    val matchPct = (roi.featureMatchScore * 100).toInt()
                                    val clarityPct = (100 - roi.occlusionPercent.toInt()).coerceIn(0, 100)
                                    Text(
                                        text = if (isAr) "التطابق: $matchPct% • الوضوح: $clarityPct%" else "Match: $matchPct% • Clarity: $clarityPct%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedSteel
                                    )
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

@Composable
private fun ComparePairRow(label: String, observed: String, reference: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = observed,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium,
                color = CoolWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = reference,
                style = MaterialTheme.typography.bodyMedium,
                color = CalibratedEmerald,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = HairlineSlate.copy(alpha = 0.5f))
    }
}

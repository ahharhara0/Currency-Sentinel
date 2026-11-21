package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.catalog.CurrencyCode
import com.example.catalog.IntegrityReport
import com.example.engine.vision.ForensicInspectionReport
import com.example.engine.vision.ForensicVerdict
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
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
fun DashboardScreen(
    language: AppLanguage,
    strings: AppStrings,
    integrity: IntegrityReport,
    isOpenCvActive: Boolean,
    evidenceConsent: Boolean,
    recentReports: List<ForensicInspectionReport>,
    onToggleConsent: (Boolean) -> Unit,
    onLaunchQuickScan: () -> Unit,
    onLaunchDeepScan: () -> Unit,
    onLaunchSerialAuditor: () -> Unit,
    onOpenComparisonLab: (String?) -> Unit,
    onOpenReport: (String) -> Unit
) {
    val isAr = language == AppLanguage.AR
    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Friendly Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_forensic_banner_1795239941225),
                    contentDescription = strings.dashboardTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ObsidianBg.copy(alpha = 0.92f), ObsidianBg.copy(alpha = 0.52f))
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.dashboardTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = CoolWhite
                        )
                        Text(
                            text = strings.dashboardSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OpticalCyan
                        )
                    }
                }
            }

            // Primary Hero Action
            Button(
                onClick = onLaunchQuickScan,
                colors = ButtonDefaults.buttonColors(containerColor = OpticalCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("dash_quick_scan_btn")
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(strings.launchQuickScan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Secondary Everyday Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onLaunchDeepScan,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RecessedPanel,
                        contentColor = CalibratedEmerald
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("dash_deep_scan_btn")
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.launchDeepScan, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onLaunchSerialAuditor,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("dash_serial_auditor_btn")
                ) {
                    Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.openSerialAuditor)
                }
            }

            OutlinedButton(
                onClick = { onOpenComparisonLab(recentReports.firstOrNull()?.reportId) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("dash_compare_lab_btn")
            ) {
                Icon(Icons.Filled.Compare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(strings.openComparisonLab)
            }

            // Friendly Readiness Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (integrity.allVerified) CalibratedEmerald.copy(alpha = 0.4f) else CrimsonSignal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (integrity.allVerified) Icons.Filled.Verified else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (integrity.allVerified) CalibratedEmerald else CrimsonSignal
                        )
                        Text(strings.engineIntegrityTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    }
                    Text(
                        text = if (integrity.allVerified) strings.catalogVerifiedLabel else strings.catalogWarningLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (integrity.allVerified) CalibratedEmerald else CrimsonSignal
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(strings.offlineBadge, OpticalCyan)
                        StatusChip(strings.vaultEncryptedBadge, CalibratedEmerald)
                    }
                }
            }

            // 3-Step Friendly Guide when History is Empty
            if (recentReports.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, HairlineSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.howItWorksTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                        Text(strings.howItWorksStep1, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        Text(strings.howItWorksStep2, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        Text(strings.howItWorksStep3, style = MaterialTheme.typography.bodyMedium, color = OpticalCyan)
                    }
                }
            }

            // Consent-Gated Photo Saving Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HairlineSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = OpticalCyan, modifier = Modifier.size(16.dp))
                            Text(strings.evidenceConsentTitle, style = MaterialTheme.typography.titleSmall, color = CoolWhite)
                        }
                        Text(strings.evidenceConsentSubtitle, style = MaterialTheme.typography.bodySmall, color = MutedSteel)
                    }
                    Switch(
                        checked = evidenceConsent,
                        onCheckedChange = onToggleConsent,
                        modifier = Modifier.testTag("evidence_consent_switch")
                    )
                }
            }

            // Recent Inspections Feed
            Text(strings.recentInspectionsTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
            if (recentReports.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = RecessedPanel,
                    border = BorderStroke(1.dp, HairlineSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.vaultEmptyBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                recentReports.take(5).forEach { rep ->
                    val (vColor, vText) = when (rep.verdict) {
                        ForensicVerdict.CONSISTENT_WITH_SPECS -> CalibratedEmerald to strings.statusOkShort
                        ForensicVerdict.ANOMALIES_DETECTED -> CrimsonSignal to strings.statusWarningShort
                        ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE -> ForensicAmber to strings.statusUnclearShort
                        ForensicVerdict.UNCLASSIFIED_NOTE -> MutedSteel to strings.statusUnclearShort
                    }
                    val currLabel = if (rep.currency == CurrencyCode.SAR) strings.currencySar else strings.currencyUsd
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReport(rep.reportId) }
                            .testTag("dash_recent_${rep.reportId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "$currLabel ${if (rep.currency == CurrencyCode.USD) "$" else ""}${rep.denominationValue} • ${if (isAr) rep.designFamilyAr else rep.designFamilyEn}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CoolWhite
                                )
                                Text(
                                    text = rep.serialAnalysis.normalizedSerial.ifBlank { "—" },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = OpticalCyan,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = vColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = vText,
                                    fontWeight = FontWeight.Bold,
                                    color = vColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
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
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = RecessedPanel,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

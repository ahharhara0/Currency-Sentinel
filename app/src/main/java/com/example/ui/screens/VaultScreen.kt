package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.example.engine.serial.DuplicateClassification
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
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(
    reports: List<ForensicInspectionReport>,
    language: AppLanguage,
    strings: AppStrings,
    onOpenReport: (String) -> Unit,
    onDeleteReport: (String) -> Unit,
    onClearVault: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(reports, query) {
        if (query.isBlank()) reports
        else reports.filter {
            it.serialAnalysis.normalizedSerial.contains(query, ignoreCase = true) ||
                it.currency.name.contains(query, ignoreCase = true) ||
                it.denominationValue.toString().contains(query)
        }
    }

    val totalCount = reports.size
    val anomalyCount = reports.count { it.verdict == ForensicVerdict.ANOMALIES_DETECTED }
    val duplicateCount = reports.count { it.isLocalDuplicate }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.vaultTitle, style = MaterialTheme.typography.headlineMedium, color = CoolWhite)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = CalibratedEmerald, modifier = Modifier.size(14.dp))
                            Text(strings.vaultEncryptedBadge, style = MaterialTheme.typography.labelSmall, color = CalibratedEmerald)
                        }
                    }
                    if (reports.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearVault,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_vault_btn")
                        ) {
                            Text(strings.clearVault)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultStatTile(strings.totalScansLabel, totalCount.toString(), OpticalCyan, Modifier.weight(1f))
                    VaultStatTile(strings.anomaliesCountLabel, anomalyCount.toString(), ForensicAmber, Modifier.weight(1f))
                    VaultStatTile(strings.duplicatesCountLabel, duplicateCount.toString(), CrimsonSignal, Modifier.weight(1f))
                }

                if (reports.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(strings.searchSerialPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        border = BorderStroke(1.dp, HairlineSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = OpticalCyan, modifier = Modifier.size(44.dp))
                            Text(strings.vaultEmptyTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                            Text(strings.vaultEmptyBody, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.reportId }) { report ->
                    VaultRecordCard(
                        report = report,
                        isAr = language == AppLanguage.AR,
                        strings = strings,
                        onClick = { onOpenReport(report.reportId) },
                        onDelete = { onDeleteReport(report.reportId) }
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun VaultStatTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = RecessedPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
        }
    }
}

@Composable
private fun VaultRecordCard(
    report: ForensicInspectionReport,
    isAr: Boolean,
    strings: AppStrings,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (verdictColor, verdictShort) = when (report.verdict) {
        ForensicVerdict.CONSISTENT_WITH_SPECS -> CalibratedEmerald to strings.statusOkShort
        ForensicVerdict.ANOMALIES_DETECTED -> CrimsonSignal to strings.statusWarningShort
        ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE -> ForensicAmber to strings.statusUnclearShort
        ForensicVerdict.UNCLASSIFIED_NOTE -> MutedSteel to strings.statusUnclearShort
    }
    val currLabel = if (report.currency == CurrencyCode.SAR) strings.currencySar else strings.currencyUsd
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HairlineSlate),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_card_${report.reportId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$currLabel ${if (report.currency == CurrencyCode.USD) "$" else ""}${report.denominationValue}",
                        style = MaterialTheme.typography.titleMedium,
                        color = CoolWhite
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = verdictColor.copy(alpha = 0.16f)) {
                        Text(
                            text = verdictShort,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = verdictColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (report.duplicateClassification != DuplicateClassification.UNIQUE) {
                    val (dupText, dupColor) = when (report.duplicateClassification) {
                        DuplicateClassification.SAME_NOTE_RESCANNED -> strings.dupSameNoteLabel to ForensicAmber
                        DuplicateClassification.SAME_SERIAL_DIFFERENT_IMAGE -> strings.dupDiffImageLabel to CrimsonSignal
                        DuplicateClassification.POSSIBLE_DUPLICATE -> strings.dupPossibleLabel to ForensicAmber
                        DuplicateClassification.INSUFFICIENT_COMPARISON -> strings.dupInsufficientLabel to MutedSteel
                        DuplicateClassification.UNIQUE -> "" to MutedSteel
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = dupColor.copy(alpha = 0.2f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = dupColor, modifier = Modifier.size(12.dp))
                            Text(dupText, style = MaterialTheme.typography.labelSmall, color = dupColor)
                        }
                    }
                }

                Text(
                    text = report.serialAnalysis.normalizedSerial.ifBlank { "—" },
                    fontFamily = JetBrainsMonoFontFamily,
                    color = OpticalCyan,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${if (isAr) report.designFamilyAr else report.designFamilyEn} • ${formatter.format(Date(report.timestampMillis))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Delete, contentDescription = strings.deleteRecord, tint = MutedSteel)
            }
        }
    }
}

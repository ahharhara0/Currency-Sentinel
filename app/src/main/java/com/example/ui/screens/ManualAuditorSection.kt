package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.catalog.SubstrateMaterial
import com.example.i18n.AppStrings
import com.example.ui.state.ScanLabUiState
import com.example.ui.state.SentinelViewModel
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OpticalCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualSerialAuditorSection(
    viewModel: SentinelViewModel,
    state: ScanLabUiState,
    strings: AppStrings,
    isAnalyzing: Boolean,
    onReportReady: (String) -> Unit
) {
    var showOptionalFields by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HairlineSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.manualAuditorTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
            Text(strings.manualAuditorSubtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.manualCurrency == CurrencyCode.USD,
                    onClick = {
                        viewModel.updateManualState {
                            it.copy(manualCurrency = CurrencyCode.USD, manualDenomination = 100, manualMaterial = null)
                        }
                    },
                    label = { Text(strings.currencyUsd) },
                    modifier = Modifier.testTag("manual_usd_chip")
                )
                FilterChip(
                    selected = state.manualCurrency == CurrencyCode.SAR,
                    onClick = {
                        viewModel.updateManualState {
                            it.copy(manualCurrency = CurrencyCode.SAR, manualDenomination = 100, manualMaterial = null)
                        }
                    },
                    label = { Text(strings.currencySar) },
                    modifier = Modifier.testTag("manual_sar_chip")
                )
            }

            Text(strings.denominationLabel, style = MaterialTheme.typography.labelLarge, color = MutedSteel)
            val denoms = if (state.manualCurrency == CurrencyCode.USD) {
                listOf(1, 2, 5, 10, 20, 50, 100)
            } else {
                listOf(5, 10, 50, 100, 200, 500, 1)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                denoms.forEach { d ->
                    FilterChip(
                        selected = state.manualDenomination == d,
                        onClick = { viewModel.updateManualState { it.copy(manualDenomination = d) } },
                        label = {
                            Text(
                                text = if (state.manualCurrency == CurrencyCode.USD) "$$d" else "$d SAR",
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    )
                }
            }

            if (state.manualCurrency == CurrencyCode.SAR && state.manualDenomination == 5) {
                Text(strings.materialLabel, style = MaterialTheme.typography.labelLarge, color = MutedSteel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SubstrateMaterial.COTTON_LINEN to strings.materialCotton,
                        SubstrateMaterial.POLYMER to strings.materialPolymer
                    ).forEach { (mat, lbl) ->
                        FilterChip(
                            selected = state.manualMaterial == mat,
                            onClick = { viewModel.updateManualState { it.copy(manualMaterial = mat) } },
                            label = { Text(lbl) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.manualPrimarySerial,
                onValueChange = { v -> viewModel.updateManualState { it.copy(manualPrimarySerial = v) } },
                label = { Text(strings.primarySerialLabel) },
                placeholder = {
                    Text(
                        if (state.manualCurrency == CurrencyCode.USD) "QB12345678C"
                        else "A12345678",
                        fontFamily = JetBrainsMonoFontFamily
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_primary_serial_input")
            )

            OutlinedTextField(
                value = state.manualSecondarySerial,
                onValueChange = { v -> viewModel.updateManualState { it.copy(manualSecondarySerial = v) } },
                label = { Text(strings.secondarySerialLabel) },
                supportingText = { Text(strings.secondarySerialHintSar) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_secondary_serial_input")
            )

            if (state.manualCurrency == CurrencyCode.USD) {
                OutlinedButton(
                    onClick = { showOptionalFields = !showOptionalFields },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (showOptionalFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.optionalFieldsLabel)
                }

                AnimatedVisibility(visible = showOptionalFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.manualFrIndicator,
                                onValueChange = { v -> viewModel.updateManualState { it.copy(manualFrIndicator = v) } },
                                label = { Text(strings.frIndicatorLabel) },
                                placeholder = { Text("B2") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("manual_fr_input")
                            )
                            OutlinedTextField(
                                value = state.manualSeriesYear,
                                onValueChange = { v -> viewModel.updateManualState { it.copy(manualSeriesYear = v) } },
                                label = { Text(strings.seriesYearLabel) },
                                placeholder = { Text("2021") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("manual_series_input")
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.manualFacePlate,
                                onValueChange = { v -> viewModel.updateManualState { it.copy(manualFacePlate = v) } },
                                label = { Text(strings.facePlateLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.manualBackPlate,
                                onValueChange = { v -> viewModel.updateManualState { it.copy(manualBackPlate = v) } },
                                label = { Text(strings.backPlateLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = state.manualNotePosition,
                            onValueChange = { v -> viewModel.updateManualState { it.copy(manualNotePosition = v) } },
                            label = { Text(strings.notePositionLabel) },
                            placeholder = { Text("B3") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = ForensicAmber)
                                Text(strings.starNoteToggle, color = CoolWhite)
                            }
                            Switch(
                                checked = state.manualStarNote,
                                onCheckedChange = { checked -> viewModel.updateManualState { it.copy(manualStarNote = checked) } }
                            )
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = { viewModel.runManualAudit(onReportReady) },
        enabled = state.manualPrimarySerial.isNotBlank() && !isAnalyzing,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OpticalCyan, contentColor = ObsidianBg),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("run_manual_audit_btn")
    ) {
        Text(strings.runSerialAudit, fontWeight = FontWeight.Bold)
    }
}

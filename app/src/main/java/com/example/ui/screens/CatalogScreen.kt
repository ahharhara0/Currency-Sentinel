package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.catalog.BanknoteCatalogEntry
import com.example.catalog.CurrencyCode
import com.example.catalog.CurrencyMasterCatalog
import com.example.catalog.ProvenanceTier
import com.example.catalog.SubstrateMaterial
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.theme.CalibratedEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel

private enum class CatalogFilter {
    ALL,
    SAR,
    USD,
    POLYMER
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    catalog: CurrencyMasterCatalog,
    language: AppLanguage,
    strings: AppStrings
) {
    val isAr = language == AppLanguage.AR
    var filter by remember { mutableStateOf(CatalogFilter.ALL) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filteredEntries = remember(catalog, filter) {
        catalog.entries.filter { entry ->
            when (filter) {
                CatalogFilter.ALL -> true
                CatalogFilter.SAR -> entry.currency == CurrencyCode.SAR
                CatalogFilter.USD -> entry.currency == CurrencyCode.USD
                CatalogFilter.POLYMER -> entry.material == SubstrateMaterial.POLYMER
            }
        }
    }

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
                Text(strings.catalogTitle, style = MaterialTheme.typography.headlineMedium, color = CoolWhite)
                Text(strings.catalogSubtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CatalogFilter.ALL to strings.filterAll,
                        CatalogFilter.SAR to strings.currencySar,
                        CatalogFilter.USD to strings.currencyUsd,
                        CatalogFilter.POLYMER to strings.materialPolymer
                    ).forEach { (itemFilter, label) ->
                        FilterChip(
                            selected = filter == itemFilter,
                            onClick = { filter = itemFilter },
                            label = { Text(label) },
                            modifier = Modifier.testTag("catalog_filter_${itemFilter.name.lowercase()}")
                        )
                    }
                }
            }

            if (filter == CatalogFilter.ALL || filter == CatalogFilter.SAR) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = RecessedPanel,
                        border = BorderStroke(1.dp, ForensicAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.samaEpistemicNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ForensicAmber,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(filteredEntries, key = { it.id }) { entry ->
                CatalogEntryCard(
                    entry = entry,
                    isExpanded = expandedId == entry.id,
                    isAr = isAr,
                    strings = strings,
                    onToggle = { expandedId = if (expandedId == entry.id) null else entry.id }
                )
            }

            if (filter == CatalogFilter.ALL || filter == CatalogFilter.USD) {
                item {
                    UsdReferenceTablesCard(catalog = catalog, isAr = isAr, strings = strings)
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun CatalogEntryCard(
    entry: BanknoteCatalogEntry,
    isExpanded: Boolean,
    isAr: Boolean,
    strings: AppStrings,
    onToggle: () -> Unit
) {
    val badgeColor = if (entry.currency == CurrencyCode.SAR) CalibratedEmerald else OpticalCyan
    val (provColor, provLabel) = when (entry.provenance.tier) {
        ProvenanceTier.OFFICIALLY_DOCUMENTED -> CalibratedEmerald to (if (isAr) "موثق رسمياً" else "Official Spec")
        ProvenanceTier.OFFICIALLY_ILLUSTRATED -> OpticalCyan to (if (isAr) "مرجع مصوّر" else "Official Guide")
        ProvenanceTier.INDEPENDENTLY_MEASURED, ProvenanceTier.RESEARCH_REFERENCE -> ForensicAmber to (if (isAr) "مرجع بحثي" else "Reference Data")
        else -> MutedSteel to (if (isAr) "مرجع" else "Reference")
    }
    val currBadge = if (entry.currency == CurrencyCode.SAR) strings.currencySar else strings.currencyUsd

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isExpanded) badgeColor.copy(alpha = 0.6f) else HairlineSlate),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("catalog_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = badgeColor.copy(alpha = 0.16f)) {
                            Text(currBadge, style = MaterialTheme.typography.labelSmall, color = badgeColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = provColor.copy(alpha = 0.16f)) {
                            Text(provLabel, style = MaterialTheme.typography.labelSmall, color = provColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        if (entry.material == SubstrateMaterial.POLYMER) {
                            Surface(shape = RoundedCornerShape(8.dp), color = ForensicAmber.copy(alpha = 0.18f)) {
                                Text(strings.materialPolymer, style = MaterialTheme.typography.labelSmall, color = ForensicAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (isAr) entry.denominationLabelAr else entry.denominationLabelEn, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                    Text(if (isAr) entry.familyNameAr else entry.familyNameEn, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                }
                Icon(imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MutedSteel)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${entry.widthMm.toInt()} × ${entry.heightMm} mm", style = MaterialTheme.typography.labelSmall, color = OpticalCyan)
                Text("${strings.issueYearLabel}: ${entry.issueYear}", style = MaterialTheme.typography.labelSmall, color = MutedSteel)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = HairlineSlate)
                    CatalogMetaLine(strings.provenanceHeader, "${entry.provenance.source} • ${if (isAr) entry.provenance.confidenceNoteAr else entry.provenance.confidenceNoteEn}")
                    CatalogMetaLine(strings.designYearLabel, entry.designYear)
                    CatalogMetaLine(strings.printYearLabel, entry.printYearRange)
                    CatalogMetaLine(strings.signatureLabel, entry.signatureYears)
                    CatalogMetaLine(strings.serialRulesHeader, entry.serialFormatHint)
                    CatalogMetaLine(strings.sourceLabel, entry.sourceCitation)

                    Text(strings.securityFeaturesHeader, style = MaterialTheme.typography.labelLarge, color = OpticalCyan)
                    entry.securityFeatures.forEach { feat ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = CalibratedEmerald, modifier = Modifier.padding(top = 2.dp))
                            Column {
                                Text(if (isAr) feat.nameAr else feat.nameEn, style = MaterialTheme.typography.labelLarge, color = CoolWhite)
                                Text(if (isAr) feat.descriptionAr else feat.descriptionEn, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                            }
                        }
                    }

                    Text(strings.roiBlueprintHeader, style = MaterialTheme.typography.labelLarge, color = OpticalCyan)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(entry.officialAspectRatio.coerceIn(1.9f, 2.45f))
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBg)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(color = HairlineSlate, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f), style = Stroke(width = 2f))
                            entry.roiZones.forEach { roi ->
                                drawRect(color = OpticalCyan.copy(alpha = 0.16f), topLeft = Offset(roi.x * size.width, roi.y * size.height), size = Size(roi.w * size.width, roi.h * size.height))
                                drawRect(color = OpticalCyan, topLeft = Offset(roi.x * size.width, roi.y * size.height), size = Size(roi.w * size.width, roi.h * size.height), style = Stroke(width = 2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsdReferenceTablesCard(
    catalog: CurrencyMasterCatalog,
    isAr: Boolean,
    strings: AppStrings
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, HairlineSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.usdSeriesTableTitle, style = MaterialTheme.typography.titleMedium, color = OpticalCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                catalog.usdSeriesYears.forEach { rule ->
                    Surface(shape = RoundedCornerShape(8.dp), color = RecessedPanel, border = BorderStroke(1.dp, HairlineSlate)) {
                        Text(
                            text = "${rule.letter}=${rule.seriesYear}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = HairlineSlate)
            Text(strings.usdFrbTableTitle, style = MaterialTheme.typography.titleMedium, color = OpticalCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                catalog.usdFederalReserveDistricts.forEach { frb ->
                    Surface(shape = RoundedCornerShape(8.dp), color = RecessedPanel, border = BorderStroke(1.dp, HairlineSlate)) {
                        Text(
                            text = "${frb.code} ${if (isAr) frb.cityAr else frb.cityEn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMetaLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = CoolWhite)
    }
}

package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.engine.vision.CellCondition
import com.example.engine.vision.ConditionReport
import com.example.engine.vision.ContradictionType
import com.example.engine.vision.EfeFourOutputs
import com.example.engine.vision.ForensicVerdict
import com.example.engine.vision.RoiEvaluation
import com.example.i18n.AppStrings
import com.example.ui.theme.CalibratedEmerald
import com.example.ui.theme.CalibratedEmeraldBg
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoolWhite
import com.example.ui.theme.CrimsonSignal
import com.example.ui.theme.CrimsonSignalBg
import com.example.ui.theme.ForensicAmber
import com.example.ui.theme.ForensicAmberBg
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelTopBar(
    strings: AppStrings,
    onToggleLanguage: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ObsidianBg,
            titleContentColor = CoolWhite
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OpticalCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = strings.appTitle,
                        tint = OpticalCyan,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = CoolWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = CalibratedEmerald,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = strings.offlineBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalibratedEmerald
                        )
                    }
                }
            }
        },
        actions = {
            FilledTonalButton(
                onClick = onToggleLanguage,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = RecessedPanel,
                    contentColor = OpticalCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("lang_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = strings.langToggleLabel,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.langToggleLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun VerdictBanner(
    verdict: ForensicVerdict,
    strings: AppStrings,
    confidencePercent: Int,
    coveragePercent: Int
) {
    val (bg, accent, icon, label, shortStatus) = when (verdict) {
        ForensicVerdict.CONSISTENT_WITH_SPECS -> Quint(
            CalibratedEmeraldBg.copy(alpha = 0.55f),
            CalibratedEmerald,
            Icons.Filled.CheckCircle,
            strings.verdictConsistent,
            strings.statusOkShort
        )
        ForensicVerdict.ANOMALIES_DETECTED -> Quint(
            CrimsonSignalBg.copy(alpha = 0.55f),
            CrimsonSignal,
            Icons.Filled.Error,
            strings.verdictAnomalies,
            strings.statusWarningShort
        )
        ForensicVerdict.INCONCLUSIVE_INSUFFICIENT_EVIDENCE -> Quint(
            ForensicAmberBg.copy(alpha = 0.55f),
            ForensicAmber,
            Icons.Filled.Help,
            strings.verdictInconclusive,
            strings.statusUnclearShort
        )
        ForensicVerdict.UNCLASSIFIED_NOTE -> Quint(
            RecessedPanel,
            MutedSteel,
            Icons.Filled.QuestionMark,
            strings.verdictUnclassified,
            strings.statusUnclearShort
        )
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoolWhite,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = shortStatus,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricChip(label = "${confidencePercent}%", sub = strings.efeStructCons, accent = accent)
                MetricChip(label = "${coveragePercent}%", sub = strings.captureQualityScore, accent = OpticalCyan)
            }
            Text(
                text = strings.verdictDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = CoolWhite.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun EfeFourOutputsGrid(
    outputs: EfeFourOutputs,
    strings: AppStrings
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EfeMetricBox(
                title = strings.efeClassConf,
                value = "${outputs.classificationConfidencePercent}%",
                accent = OpticalCyan,
                modifier = Modifier.weight(1f)
            )
            EfeMetricBox(
                title = strings.efeStructCons,
                value = "${outputs.structuralConsistencyPercent}%",
                accent = if (outputs.structuralConsistencyPercent >= 75) CalibratedEmerald else ForensicAmber,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EfeMetricBox(
                title = strings.efeEvidenceSuff,
                value = "${outputs.evidenceSufficiencyPercent}%",
                accent = if (outputs.evidenceSufficiencyPercent >= 65) CalibratedEmerald else ForensicAmber,
                modifier = Modifier.weight(1f)
            )
            EfeMetricBox(
                title = strings.efeSuspicionRisk,
                value = "${outputs.forensicSuspicionRiskPercent}%",
                accent = if (outputs.forensicSuspicionRiskPercent >= 30) CrimsonSignal else CalibratedEmerald,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EfeMetricBox(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = RecessedPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MutedSteel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
fun ContradictionTypeChip(type: ContradictionType, strings: AppStrings) {
    val (label, color) = when (type) {
        ContradictionType.HARD_CONTRADICTION -> strings.typeHardContradiction to CrimsonSignal
        ContradictionType.SOFT_ANOMALY -> strings.typeSoftAnomaly to ForensicAmber
        ContradictionType.CAPTURE_ARTIFACT -> strings.typeCaptureArtifact to OpticalCyan
        ContradictionType.UNKNOWN -> strings.typeUnknown to MutedSteel
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MetricChip(label: String, sub: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianBg.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = "$sub $label",
                style = MaterialTheme.typography.labelSmall,
                color = CoolWhite
            )
        }
    }
}

@Composable
fun ScoreProgressRow(
    title: String,
    score: Float,
    accent: Color = OpticalCyan
) {
    val pct = (score * 100).toInt().coerceIn(0, 100)
    val barColor = when {
        pct >= 78 -> CalibratedEmerald
        pct >= 52 -> ForensicAmber
        else -> CrimsonSignal
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = CoolWhite)
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall,
                color = if (accent == OpticalCyan) barColor else accent
            )
        }
        LinearProgressIndicator(
            progress = { score.coerceIn(0f, 1f) },
            color = barColor,
            trackColor = HairlineSlate,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun ForensicOverlayDiagram(
    imageUri: String?,
    archivedBitmap: Bitmap? = null,
    roiEvaluations: List<RoiEvaluation>,
    conditionReport: ConditionReport,
    showRois: Boolean,
    showFoldMask: Boolean,
    aspectRatio: Float = 2.25f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(1.8f, 2.5f))
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface)
    ) {
        if (archivedBitmap != null) {
            Image(
                bitmap = archivedBitmap.asImageBitmap(),
                contentDescription = "Archived encrypted banknote",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!imageUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(model = imageUri),
                contentDescription = "Inspected banknote",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (imageUri.isNullOrBlank() && archivedBitmap == null) {
                drawRect(
                    color = HairlineSlate,
                    topLeft = Offset(4f, 4f),
                    size = Size(w - 8f, h - 8f),
                    style = Stroke(width = 2f)
                )
            }

            if (showFoldMask) {
                val cellW = w / conditionReport.gridCols
                val cellH = h / conditionReport.gridRows
                conditionReport.cells.forEach { cell ->
                    val fill = when (cell.condition) {
                        CellCondition.CLEAN -> Color.Transparent
                        CellCondition.WORN_FADED -> ForensicAmber.copy(alpha = 0.14f)
                        CellCondition.FOLD_CREASE -> CrimsonSignal.copy(alpha = 0.28f)
                        CellCondition.GLARE_OVEREXPOSED -> Color.White.copy(alpha = 0.28f)
                        CellCondition.BLUR_DEFOCUS -> OpticalCyan.copy(alpha = 0.22f)
                        CellCondition.STAIN_DAMAGE -> Color(0xFF92400E).copy(alpha = 0.34f)
                        CellCondition.DARK_OCCLUDED -> Color.Black.copy(alpha = 0.48f)
                    }
                    if (fill != Color.Transparent) {
                        drawRect(
                            color = fill,
                            topLeft = Offset(cell.col * cellW, cell.row * cellH),
                            size = Size(cellW, cellH)
                        )
                    }
                    drawRect(
                        color = HairlineSlate.copy(alpha = 0.35f),
                        topLeft = Offset(cell.col * cellW, cell.row * cellH),
                        size = Size(cellW, cellH),
                        style = Stroke(width = 1f)
                    )
                }
            }

            if (showRois) {
                roiEvaluations.forEach { roi ->
                    val boxColor = when {
                        roi.isPositionShifted -> CrimsonSignal
                        roi.isOccluded -> ForensicAmber
                        else -> OpticalCyan
                    }
                    val left = roi.x * w
                    val top = roi.y * h
                    val bw = roi.w * w
                    val bh = roi.h * h
                    drawRect(
                        color = boxColor.copy(alpha = 0.15f),
                        topLeft = Offset(left, top),
                        size = Size(bw, bh)
                    )
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(bw, bh),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.engine.vision.ForensicInspectionReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ForensicPdfExporter {

    fun exportReportPdf(context: Context, report: ForensicInspectionReport): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "sentinel_${report.reportId.take(8)}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(14, 116, 144)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.5f
            typeface = Typeface.DEFAULT
        }
        val monoPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
        }

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        var y = 44f
        val left = 40f

        canvas.drawText("CURRENCY SENTINEL AI — V3 OFFLINE FORENSIC REPORT", left, y, titlePaint)
        y += 20f
        canvas.drawText("Report ID: ${report.reportId} | Date: ${fmt.format(Date(report.timestampMillis))}", left, y, bodyPaint)
        y += 16f
        canvas.drawText("Engine Suite: ${report.engineSuiteVersion} | Catalog: v${report.catalogVersion}", left, y, bodyPaint)
        y += 24f

        canvas.drawText("1. IDENTITY & CLASSIFICATION", left, y, sectionPaint)
        y += 16f
        canvas.drawText("Currency: ${report.currency.name} | Denomination: ${report.denominationValue} | Family: ${report.designFamilyEn}", left, y, bodyPaint)
        y += 14f
        canvas.drawText("Issue: ${report.seriesOrIssueYear} | Design Year: ${report.designYear} | Substrate: ${report.observedMaterial.name}", left, y, bodyPaint)
        y += 14f
        canvas.drawText("Verdict: ${report.verdict.name} | Scan Depth: ${report.scanDepthMode.name}", left, y, monoPaint)
        y += 24f

        canvas.drawText("2. EVIDENCE FUSION (EFE 2.0) 4-DIMENSION METRICS", left, y, sectionPaint)
        y += 16f
        canvas.drawText(
            "Class Conf: ${report.efeOutputs.classificationConfidencePercent}% | Structural: ${report.efeOutputs.structuralConsistencyPercent}% | Sufficiency: ${report.efeOutputs.evidenceSufficiencyPercent}% | Suspicion Risk: ${report.efeOutputs.forensicSuspicionRiskPercent}%",
            left, y, monoPaint
        )
        y += 14f
        canvas.drawText(
            "CEF Coverage: Capture Quality ${(report.conditionReport.captureQualityScore * 100).toInt()}% | Usable Area ${report.usableCoveragePercent}% | Evidence Completeness ${report.evidenceCompletenessPercent}%",
            left, y, bodyPaint
        )
        y += 24f

        canvas.drawText("3. SERIAL INTELLIGENCE (SIE 2.0)", left, y, sectionPaint)
        y += 16f
        canvas.drawText("Normalized Serial: ${report.serialAnalysis.normalizedSerial.ifBlank { "NONE" }} | Secondary: ${report.serialAnalysis.secondarySerial ?: "—"}", left, y, monoPaint)
        y += 14f
        canvas.drawText("Duplicate Intelligence: ${report.duplicateClassification.name} | dHash: ${report.perceptualDHash.ifBlank { "N/A" }}", left, y, bodyPaint)
        y += 14f
        canvas.drawText("5-Part Summary: ${report.serialAnalysis.fivePartReport.structuralValiditySummaryEn}", left, y, bodyPaint)
        y += 24f

        canvas.drawText("4. TYPED CONTRADICTIONS & OBSERVATIONS (${report.anomalies.size})", left, y, sectionPaint)
        y += 16f
        if (report.anomalies.isEmpty()) {
            canvas.drawText("No structural contradictions or hard anomalies detected in inspected areas.", left, y, bodyPaint)
            y += 16f
        } else {
            report.anomalies.take(10).forEach { anom ->
                canvas.drawText("[${anom.contradictionType.name}] ${anom.titleEn}", left, y, monoPaint)
                y += 13f
                canvas.drawText("   ${anom.detailEn.take(92)}", left, y, bodyPaint)
                y += 15f
            }
        }

        if (report.manualAuditTrail.isNotEmpty()) {
            y += 10f
            canvas.drawText("5. MANUAL AUDIT LOG (${report.manualAuditTrail.size})", left, y, sectionPaint)
            y += 16f
            report.manualAuditTrail.take(4).forEach { edit ->
                canvas.drawText("${edit.field}: ${edit.previousValue} -> ${edit.newValue} (${edit.note})", left, y, bodyPaint)
                y += 14f
            }
        }

        y = 790f
        canvas.drawText("EPISTEMIC NOTICE: Optical smartphone inspection evaluates structural & digital consistency only.", left, y, bodyPaint)
        y += 13f
        canvas.drawText("Physical intaglio relief, polymer tactile feel, magnetic ink, and UV features require lab tools.", left, y, bodyPaint)

        doc.finishPage(page)
        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sharePdf(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Forensic Report PDF"))
    }
}

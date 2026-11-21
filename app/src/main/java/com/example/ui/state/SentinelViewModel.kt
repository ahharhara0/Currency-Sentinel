package com.example.ui.state

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalog.CurrencyCatalogRepository
import com.example.catalog.CurrencyCode
import com.example.catalog.CurrencyMasterCatalog
import com.example.catalog.IntegrityReport
import com.example.catalog.SubstrateMaterial
import com.example.data.BitmapLoader
import com.example.data.ForensicPdfExporter
import com.example.data.LanguagePreferences
import com.example.data.SentinelDatabase
import com.example.data.VaultRepository
import com.example.engine.cv.LiteRtForensicModels
import com.example.engine.cv.OpenCvBridge
import com.example.engine.vision.EvidenceFusionEngine
import com.example.engine.vision.ForensicInspectionReport
import com.example.engine.vision.InspectionMode
import com.example.engine.vision.PhotoInspectionRequest
import com.example.engine.vision.ScanDepthMode
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanInputTab {
    CAMERA,
    GALLERY,
    MANUAL
}

data class ScanLabUiState(
    val activeTab: ScanInputTab = ScanInputTab.CAMERA,
    val scanDepthMode: ScanDepthMode = ScanDepthMode.QUICK_SCAN,
    val deepBurstCount: Int = 0,
    val currencyHint: CurrencyCode? = null,
    val denominationHint: Int? = null,
    val materialHint: SubstrateMaterial? = null,
    val frontPhotoUri: Uri? = null,
    val backPhotoUri: Uri? = null,

    // Manual & Hybrid Auditor fields
    val manualCurrency: CurrencyCode = CurrencyCode.USD,
    val manualDenomination: Int = 100,
    val manualMaterial: SubstrateMaterial? = null,
    val manualPrimarySerial: String = "",
    val manualSecondarySerial: String = "",
    val manualFrIndicator: String = "",
    val manualSeriesYear: String = "",
    val manualFacePlate: String = "",
    val manualBackPlate: String = "",
    val manualNotePosition: String = "",
    val manualStarNote: Boolean = false
)

class SentinelViewModel(application: Application) : AndroidViewModel(application) {

    private val catalogRepo = CurrencyCatalogRepository(application)
    private val db = SentinelDatabase.getInstance(application)
    private val vaultRepo = VaultRepository(application, db.vaultDao(), db.catalogProfileDao())
    private val langPrefs = LanguagePreferences(application)
    private val liteRtModels = LiteRtForensicModels(application)
    private val fusionEngine = EvidenceFusionEngine(catalogRepo, liteRtModels)

    val catalog: CurrencyMasterCatalog = catalogRepo.getCatalog()
    val integrityReport: IntegrityReport = catalogRepo.getIntegrityReport()
    val isOpenCvActive: Boolean = OpenCvBridge.ensureInitialized()

    val language: StateFlow<AppLanguage> = langPrefs.languageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.AR)

    val evidenceConsent: StateFlow<Boolean> = langPrefs.evidenceConsentFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val vaultReports: StateFlow<List<ForensicInspectionReport>> = vaultRepo.reportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanLabState = MutableStateFlow(ScanLabUiState())
    val scanLabState: StateFlow<ScanLabUiState> = _scanLabState.asStateFlow()

    private val _activeReport = MutableStateFlow<ForensicInspectionReport?>(null)
    val activeReport: StateFlow<ForensicInspectionReport?> = _activeReport.asStateFlow()

    private val _archivedFrontBitmap = MutableStateFlow<Bitmap?>(null)
    val archivedFrontBitmap: StateFlow<Bitmap?> = _archivedFrontBitmap.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val deepTiltBuffer = mutableListOf<Bitmap>()

    init {
        viewModelScope.launch {
            runCatching { vaultRepo.syncCatalogProfiles(catalog.catalogVersion, catalog.entries) }
        }
    }

    fun toggleLanguage() {
        val next = language.value.toggle()
        viewModelScope.launch { langPrefs.setLanguage(next) }
    }

    fun setEvidenceConsent(enabled: Boolean) {
        viewModelScope.launch { langPrefs.setEvidenceConsent(enabled) }
    }

    fun selectScanTab(tab: ScanInputTab) {
        _scanLabState.update { it.copy(activeTab = tab) }
    }

    fun setScanDepthMode(mode: ScanDepthMode) {
        if (mode == ScanDepthMode.QUICK_SCAN) clearDeepBurstFrames()
        _scanLabState.update { it.copy(scanDepthMode = mode) }
    }

    fun addDeepBurstFrame(bitmap: Bitmap) {
        if (deepTiltBuffer.size >= 3) {
            deepTiltBuffer.removeAt(0).recycle()
        }
        deepTiltBuffer.add(bitmap)
        _scanLabState.update { it.copy(deepBurstCount = deepTiltBuffer.size) }
    }

    fun clearDeepBurstFrames() {
        deepTiltBuffer.forEach { if (!it.isRecycled) it.recycle() }
        deepTiltBuffer.clear()
        _scanLabState.update { it.copy(deepBurstCount = 0) }
    }

    fun updateCurrencyHint(currency: CurrencyCode?) {
        _scanLabState.update { it.copy(currencyHint = currency, denominationHint = null) }
    }

    fun updateDenominationHint(denom: Int?) {
        _scanLabState.update { it.copy(denominationHint = denom) }
    }

    fun updateMaterialHint(material: SubstrateMaterial?) {
        _scanLabState.update { it.copy(materialHint = material) }
    }

    fun setFrontPhotoUri(uri: Uri?) {
        _scanLabState.update { it.copy(frontPhotoUri = uri) }
    }

    fun setBackPhotoUri(uri: Uri?) {
        _scanLabState.update { it.copy(backPhotoUri = uri) }
    }

    fun updateManualState(transform: (ScanLabUiState) -> ScanLabUiState) {
        _scanLabState.update(transform)
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun inspectGalleryPhotos(onReportReady: (String) -> Unit) {
        val state = _scanLabState.value
        val frontUri = state.frontPhotoUri ?: return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorBanner.value = null
            try {
                val context = getApplication<Application>()
                val frontBmp = BitmapLoader.decodeBounded(context, frontUri)
                if (frontBmp == null) {
                    _errorBanner.value = if (language.value == AppLanguage.AR) {
                        "تعذر فك ترميز الصورة المحددة."
                    } else {
                        "Unable to decode selected front image."
                    }
                    return@launch
                }
                val backBmp = state.backPhotoUri?.let { BitmapLoader.decodeBounded(context, it) }
                val rawReport = fusionEngine.inspectPhoto(
                    PhotoInspectionRequest(
                        frontBitmap = frontBmp,
                        backBitmap = backBmp,
                        scanDepthMode = state.scanDepthMode,
                        primaryImageUri = frontUri.toString(),
                        backImageUri = state.backPhotoUri?.toString(),
                        mode = InspectionMode.GALLERY_PHOTO,
                        currencyHint = state.currencyHint,
                        denominationHint = state.denominationHint,
                        materialHint = state.materialHint,
                        manualSerialOverride = state.manualPrimarySerial.takeIf { it.isNotBlank() },
                        manualSecondarySerial = state.manualSecondarySerial.takeIf { it.isNotBlank() },
                        manualFrIndicator = state.manualFrIndicator.takeIf { it.isNotBlank() },
                        manualSeriesYear = state.manualSeriesYear.takeIf { it.isNotBlank() },
                        duplicateClassifierFn = { cur, den, ser, hash ->
                            vaultRepo.classifyDuplicate(cur, den, ser, hash)
                        }
                    )
                )
                val finalReport = maybeArchiveImages(rawReport, frontBmp, backBmp)
                vaultRepo.saveReport(finalReport)
                _activeReport.value = finalReport
                onReportReady(finalReport.reportId)
            } catch (t: Throwable) {
                _errorBanner.value = t.localizedMessage ?: "Analysis error"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun inspectCapturedBitmap(bitmap: Bitmap, onReportReady: (String) -> Unit) {
        val state = _scanLabState.value
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorBanner.value = null
            try {
                val rawReport = fusionEngine.inspectPhoto(
                    PhotoInspectionRequest(
                        frontBitmap = bitmap,
                        scanDepthMode = ScanDepthMode.QUICK_SCAN,
                        mode = InspectionMode.CAMERA_CAPTURE,
                        currencyHint = state.currencyHint,
                        denominationHint = state.denominationHint,
                        materialHint = state.materialHint,
                        duplicateClassifierFn = { cur, den, ser, hash ->
                            vaultRepo.classifyDuplicate(cur, den, ser, hash)
                        }
                    )
                )
                val finalReport = maybeArchiveImages(rawReport, bitmap, null)
                vaultRepo.saveReport(finalReport)
                _activeReport.value = finalReport
                onReportReady(finalReport.reportId)
            } catch (t: Throwable) {
                _errorBanner.value = t.localizedMessage ?: "Camera inspection error"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun runDeepBurstScan(onReportReady: (String) -> Unit) {
        if (deepTiltBuffer.isEmpty()) return
        val state = _scanLabState.value
        val frames = deepTiltBuffer.toList()
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorBanner.value = null
            try {
                val primary = frames.first()
                val rawReport = fusionEngine.inspectPhoto(
                    PhotoInspectionRequest(
                        frontBitmap = primary,
                        deepTiltFrames = frames,
                        scanDepthMode = ScanDepthMode.DEEP_FORENSIC_SCAN,
                        mode = InspectionMode.CAMERA_CAPTURE,
                        currencyHint = state.currencyHint,
                        denominationHint = state.denominationHint,
                        materialHint = state.materialHint,
                        duplicateClassifierFn = { cur, den, ser, hash ->
                            vaultRepo.classifyDuplicate(cur, den, ser, hash)
                        }
                    )
                )
                val finalReport = maybeArchiveImages(rawReport, primary, null)
                vaultRepo.saveReport(finalReport)
                _activeReport.value = finalReport
                clearDeepBurstFrames()
                onReportReady(finalReport.reportId)
            } catch (t: Throwable) {
                _errorBanner.value = t.localizedMessage ?: "Deep burst analysis error"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun runManualAudit(onReportReady: (String) -> Unit) {
        val st = _scanLabState.value
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorBanner.value = null
            try {
                val report = fusionEngine.inspectManualAudit(
                    currency = st.manualCurrency,
                    denomination = st.manualDenomination,
                    materialHint = st.manualMaterial,
                    primarySerial = st.manualPrimarySerial,
                    secondarySerial = st.manualSecondarySerial.takeIf { it.isNotBlank() },
                    frIndicator = st.manualFrIndicator.takeIf { it.isNotBlank() },
                    seriesYear = st.manualSeriesYear.takeIf { it.isNotBlank() },
                    facePlate = st.manualFacePlate.takeIf { it.isNotBlank() },
                    backPlate = st.manualBackPlate.takeIf { it.isNotBlank() },
                    notePosition = st.manualNotePosition.takeIf { it.isNotBlank() },
                    starToggle = st.manualStarNote,
                    duplicateClassifierFn = { cur, den, ser, hash ->
                        vaultRepo.classifyDuplicate(cur, den, ser, hash)
                    }
                )
                vaultRepo.saveReport(report)
                _activeReport.value = report
                onReportReady(report.reportId)
            } catch (t: Throwable) {
                _errorBanner.value = t.localizedMessage ?: "Manual audit error"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun reAuditReportSerial(
        correctedPrimary: String,
        correctedSecondary: String?,
        frIndicator: String?,
        seriesYear: String?,
        starToggle: Boolean,
        auditNote: String
    ) {
        val current = _activeReport.value ?: return
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val updated = fusionEngine.reAuditWithManualSerial(
                    existing = current,
                    correctedPrimary = correctedPrimary,
                    correctedSecondary = correctedSecondary,
                    frIndicator = frIndicator,
                    seriesYear = seriesYear,
                    starToggle = starToggle,
                    auditNote = auditNote.ifBlank { "Manual glyph review in Serial Workbench" },
                    duplicateClassifierFn = { cur, den, ser, hash ->
                        vaultRepo.classifyDuplicate(cur, den, ser, hash, excludeReportId = current.reportId)
                    }
                )
                vaultRepo.saveReport(updated)
                _activeReport.value = updated
            } catch (t: Throwable) {
                _errorBanner.value = t.localizedMessage ?: "Re-audit error"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun exportPdf(context: Context, report: ForensicInspectionReport) {
        viewModelScope.launch {
            runCatching {
                val uri = ForensicPdfExporter.exportReportPdf(context, report)
                ForensicPdfExporter.sharePdf(context, uri)
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "PDF export failed"
            }
        }
    }

    private suspend fun maybeArchiveImages(
        report: ForensicInspectionReport,
        frontBmp: Bitmap,
        backBmp: Bitmap?
    ): ForensicInspectionReport {
        if (!evidenceConsent.value) return report
        val frontPath = vaultRepo.archiveConsentImage(frontBmp, report.reportId, "front")
        val backPath = backBmp?.let { vaultRepo.archiveConsentImage(it, report.reportId, "back") }
        return report.copy(archivedFrontPath = frontPath, archivedBackPath = backPath)
    }

    fun loadReport(reportId: String) {
        viewModelScope.launch {
            val loaded = if (_activeReport.value?.reportId == reportId) {
                _activeReport.value
            } else {
                vaultRepo.getReport(reportId).also { _activeReport.value = it }
            }
            _archivedFrontBitmap.value = loaded?.archivedFrontPath?.let { vaultRepo.loadArchivedImage(it) }
        }
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch { vaultRepo.deleteReport(reportId) }
    }

    fun clearVault() {
        viewModelScope.launch { vaultRepo.clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        clearDeepBurstFrames()
        liteRtModels.close()
    }
}

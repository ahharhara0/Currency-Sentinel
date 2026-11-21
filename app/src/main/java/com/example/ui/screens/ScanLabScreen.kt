package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.catalog.CurrencyCode
import com.example.catalog.SubstrateMaterial
import com.example.engine.vision.ScanDepthMode
import com.example.i18n.AppStrings
import com.example.ui.components.MetricChip
import com.example.ui.state.ScanInputTab
import com.example.ui.state.ScanLabUiState
import com.example.ui.state.SentinelViewModel
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
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanLabScreen(
    viewModel: SentinelViewModel,
    state: ScanLabUiState,
    strings: AppStrings,
    isAnalyzing: Boolean,
    errorBanner: String?,
    onNavigateToReport: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3-Mode Input Switcher (Camera / Gallery / Serial Auditor)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    Triple(ScanInputTab.CAMERA, strings.modeCamera, Icons.Filled.CameraAlt),
                    Triple(ScanInputTab.GALLERY, strings.modeGallery, Icons.Filled.PhotoLibrary),
                    Triple(ScanInputTab.MANUAL, strings.modeManual, Icons.Filled.FactCheck)
                )
                tabs.forEachIndexed { idx, (tab, label, icon) ->
                    SegmentedButton(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectScanTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = OpticalCyan.copy(alpha = 0.18f),
                            activeContentColor = OpticalCyan,
                            inactiveContainerColor = CarbonSurface,
                            inactiveContentColor = MutedSteel
                        ),
                        modifier = Modifier.testTag("mode_tab_${tab.name.lowercase()}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            if (errorBanner != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorBanner,
                        color = CoolWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isAnalyzing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, OpticalCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = OpticalCyan, modifier = Modifier.size(28.dp))
                        Text(text = strings.analyzingPipeline, color = CoolWhite)
                    }
                }
            }

            when (state.activeTab) {
                ScanInputTab.CAMERA -> LiveCameraInspectionSection(
                    viewModel = viewModel,
                    state = state,
                    strings = strings,
                    isAnalyzing = isAnalyzing,
                    onReportReady = onNavigateToReport
                )
                ScanInputTab.GALLERY -> GalleryInspectionSection(
                    viewModel = viewModel,
                    state = state,
                    strings = strings,
                    isAnalyzing = isAnalyzing,
                    onReportReady = onNavigateToReport
                )
                ScanInputTab.MANUAL -> ManualSerialAuditorSection(
                    viewModel = viewModel,
                    state = state,
                    strings = strings,
                    isAnalyzing = isAnalyzing,
                    onReportReady = onNavigateToReport
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyHintSelector(
    state: ScanLabUiState,
    strings: AppStrings,
    onSelectCurrency: (CurrencyCode?) -> Unit,
    onSelectDenom: (Int?) -> Unit,
    onSelectMaterial: (SubstrateMaterial?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = strings.currencyHintLabel, style = MaterialTheme.typography.labelLarge, color = MutedSteel)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                null to strings.currencyAuto,
                CurrencyCode.SAR to strings.currencySar,
                CurrencyCode.USD to strings.currencyUsd
            ).forEach { (code, label) ->
                FilterChip(
                    selected = state.currencyHint == code,
                    onClick = { onSelectCurrency(code) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OpticalCyan.copy(alpha = 0.2f),
                        selectedLabelColor = OpticalCyan
                    )
                )
            }
        }

        if (state.currencyHint != null) {
            val denoms = if (state.currencyHint == CurrencyCode.SAR) {
                listOf(5, 10, 50, 100, 200, 500, 1)
            } else {
                listOf(1, 2, 5, 10, 20, 50, 100)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                denoms.forEach { d ->
                    FilterChip(
                        selected = state.denominationHint == d,
                        onClick = { onSelectDenom(if (state.denominationHint == d) null else d) },
                        label = {
                            Text(
                                text = if (state.currencyHint == CurrencyCode.USD) "$$d" else "$d SAR",
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    )
                }
            }
        }

        if (state.currencyHint == CurrencyCode.SAR && (state.denominationHint == 5 || state.denominationHint == null)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to strings.materialAuto,
                    SubstrateMaterial.COTTON_LINEN to strings.materialCotton,
                    SubstrateMaterial.POLYMER to strings.materialPolymer
                ).forEach { (mat, label) ->
                    FilterChip(
                        selected = state.materialHint == mat,
                        onClick = { onSelectMaterial(mat) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCameraInspectionSection(
    viewModel: SentinelViewModel,
    state: ScanLabUiState,
    strings: AppStrings,
    isAnalyzing: Boolean,
    onReportReady: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var liveSharpness by remember { mutableFloatStateOf(0.75f) }
    var liveExposure by remember { mutableFloatStateOf(0.60f) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    // Quick Scan vs Deep Forensic Scan Toggle
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val modes = listOf(
            Triple(ScanDepthMode.QUICK_SCAN, strings.modeQuickScan, Icons.Filled.Bolt),
            Triple(ScanDepthMode.DEEP_FORENSIC_SCAN, strings.modeDeepScan, Icons.Filled.Layers)
        )
        modes.forEachIndexed { idx, (depth, label, icon) ->
            SegmentedButton(
                selected = state.scanDepthMode == depth,
                onClick = { viewModel.setScanDepthMode(depth) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = CalibratedEmerald.copy(alpha = 0.2f),
                    activeContentColor = CalibratedEmerald,
                    inactiveContainerColor = CarbonSurface,
                    inactiveContentColor = MutedSteel
                ),
                modifier = Modifier.testTag("depth_mode_${depth.name.lowercase()}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(label)
                }
            }
        }
    }

    if (state.scanDepthMode == ScanDepthMode.DEEP_FORENSIC_SCAN) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = RecessedPanel,
            border = BorderStroke(1.dp, CalibratedEmerald.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${strings.deepScanHint} (${strings.deepBurstFramesCount}: ${state.deepBurstCount}/3)",
                style = MaterialTheme.typography.bodySmall,
                color = CalibratedEmerald,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    CurrencyHintSelector(
        state = state,
        strings = strings,
        onSelectCurrency = viewModel::updateCurrencyHint,
        onSelectDenom = viewModel::updateDenominationHint,
        onSelectMaterial = viewModel::updateMaterialHint
    )

    if (!hasCameraPermission) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, HairlineSlate),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = OpticalCyan, modifier = Modifier.size(44.dp))
                Text(strings.cameraPermissionTitle, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
                Text(strings.cameraPermissionBody, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_camera_btn")
                ) {
                    Text(strings.grantCameraPermission)
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianBg)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { proxy ->
                                    val stats = measureYPlaneTelemetry(proxy)
                                    liveSharpness = stats.first
                                    liveExposure = stats.second
                                    proxy.close()
                                }
                            }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                analyzer
                            )
                        } catch (e: Exception) {
                            Log.e("ScanLabCamera", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val frameW = size.width * 0.88f
                val targetRatio = if (state.currencyHint == CurrencyCode.SAR) 2.21f else 2.35f
                val frameH = (frameW / targetRatio).coerceAtMost(size.height * 0.72f)
                val left = (size.width - frameW) / 2f
                val top = (size.height - frameH) / 2f
                drawRect(color = OpticalCyan.copy(alpha = 0.85f), topLeft = Offset(left, top), size = Size(frameW, frameH), style = Stroke(width = 3f))
                drawRect(color = CalibratedEmerald.copy(alpha = 0.75f), topLeft = Offset(left + frameW * 0.10f, top + frameH * 0.16f), size = Size(frameW * 0.25f, frameH * 0.15f), style = Stroke(width = 2f))
                drawRect(color = CalibratedEmerald.copy(alpha = 0.75f), topLeft = Offset(left + frameW * 0.65f, top + frameH * 0.16f), size = Size(frameW * 0.25f, frameH * 0.15f), style = Stroke(width = 2f))
            }

            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("${(liveSharpness * 100).toInt()}%", strings.liveSharpness, if (liveSharpness >= 0.55f) CalibratedEmerald else ForensicAmber)
                MetricChip("${(liveExposure * 100).toInt()}%", strings.liveExposure, if (liveExposure in 0.30f..0.80f) OpticalCyan else ForensicAmber)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianBg.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
            ) {
                Text(
                    text = strings.alignNoteGuide,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoolWhite,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (state.scanDepthMode == ScanDepthMode.QUICK_SCAN) {
            Button(
                onClick = {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = imageProxyToLandscapeBitmap(image)
                                image.close()
                                if (bmp != null) viewModel.inspectCapturedBitmap(bmp, onReportReady)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("ScanLabCamera", "Capture failed", exception)
                            }
                        }
                    )
                },
                enabled = !isAnalyzing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OpticalCyan, contentColor = ObsidianBg),
                modifier = Modifier.fillMaxWidth().height(52.dp).minimumInteractiveComponentSize().testTag("capture_note_button")
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.captureNoteButton, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = imageProxyToLandscapeBitmap(image)
                                    image.close()
                                    if (bmp != null) viewModel.addDeepBurstFrame(bmp)
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ScanLabCamera", "Burst capture failed", exception)
                                }
                            }
                        )
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("burst_capture_btn")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("${strings.deepBurstCaptureBtn} (${state.deepBurstCount}/3)")
                }

                Button(
                    onClick = { viewModel.runDeepBurstScan(onReportReady) },
                    enabled = state.deepBurstCount >= 1 && !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = CalibratedEmerald, contentColor = ObsidianBg),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("run_deep_burst_btn")
                ) {
                    Text(strings.deepBurstAnalyzeBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GalleryInspectionSection(
    viewModel: SentinelViewModel,
    state: ScanLabUiState,
    strings: AppStrings,
    isAnalyzing: Boolean,
    onReportReady: (String) -> Unit
) {
    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setFrontPhotoUri(uri)
    }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setBackPhotoUri(uri)
    }

    CurrencyHintSelector(
        state = state,
        strings = strings,
        onSelectCurrency = viewModel::updateCurrencyHint,
        onSelectDenom = viewModel::updateDenominationHint,
        onSelectMaterial = viewModel::updateMaterialHint
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, HairlineSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.frontPhotoLabel, style = MaterialTheme.typography.titleMedium, color = CoolWhite)
            if (state.frontPhotoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(state.frontPhotoUri),
                    contentDescription = strings.frontPhotoLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(10.dp))
                )
            }
            OutlinedButton(
                onClick = { frontPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_front_photo_btn")
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.frontPhotoUri == null) strings.selectFrontPhoto else strings.changePhoto)
            }

            Text(strings.backPhotoLabel, style = MaterialTheme.typography.labelLarge, color = MutedSteel)
            Text(strings.optionalBackHint, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
            if (state.backPhotoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(state.backPhotoUri),
                    contentDescription = strings.backPhotoLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(10.dp))
                )
            }
            OutlinedButton(
                onClick = { backPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_back_photo_btn")
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.backPhotoUri == null) strings.selectBackPhoto else strings.changePhoto)
            }
        }
    }

    Button(
        onClick = { viewModel.inspectGalleryPhotos(onReportReady) },
        enabled = state.frontPhotoUri != null && !isAnalyzing,
        colors = ButtonDefaults.buttonColors(containerColor = OpticalCyan, contentColor = ObsidianBg),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("run_gallery_scan_btn")
    ) {
        Text(strings.runForensicAnalysis, fontWeight = FontWeight.Bold)
    }
}

private fun measureYPlaneTelemetry(image: ImageProxy): Pair<Float, Float> {
    val plane = image.planes.firstOrNull() ?: return 0.6f to 0.5f
    val buffer = plane.buffer
    val w = image.width
    val h = image.height
    val rowStride = plane.rowStride
    var sumLum = 0L
    var edgeSum = 0L
    var samples = 0
    val step = 12
    for (y in step until h - step step step) {
        for (x in step until w - step step step) {
            val idx = y * rowStride + x
            if (idx + 1 < buffer.limit() && idx - 1 >= 0) {
                val c = buffer.get(idx).toInt() and 0xFF
                val r = buffer.get(idx + 1).toInt() and 0xFF
                val l = buffer.get(idx - 1).toInt() and 0xFF
                sumLum += c
                edgeSum += abs(2 * c - r - l)
                samples++
            }
        }
    }
    if (samples == 0) return 0.6f to 0.5f
    val avgLum = (sumLum.toFloat() / samples) / 255f
    val avgEdge = (edgeSum.toFloat() / samples) / 24f
    return avgEdge.coerceIn(0.1f, 1f) to avgLum.coerceIn(0.05f, 1f)
}

private fun imageProxyToLandscapeBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it != raw) raw.recycle()
        }
    } else raw

    return if (rotated.height > rotated.width * 1.1f) {
        val m = Matrix().apply { postRotate(90f) }
        Bitmap.createBitmap(rotated, 0, 0, rotated.width, rotated.height, m, true).also {
            if (it != rotated) rotated.recycle()
        }
    } else rotated
}

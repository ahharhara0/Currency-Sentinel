package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.engine.vision.ScanDepthMode
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.components.SentinelTopBar
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.ComparisonLabScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ForensicReportScreen
import com.example.ui.screens.ScanLabScreen
import com.example.ui.screens.SerialWorkbenchScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.state.ScanInputTab
import com.example.ui.state.SentinelViewModel
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CurrencySentinelTheme
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.RecessedPanel
import kotlinx.serialization.Serializable

@Serializable
object DashboardRoute

@Serializable
object ScanLabRoute

@Serializable
object CatalogRoute

@Serializable
object VaultRoute

@Serializable
data class ReportRoute(val reportId: String)

@Serializable
data class SerialWorkbenchRoute(val reportId: String)

@Serializable
data class ComparisonLabRoute(val reportId: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurrencySentinelApp()
        }
    }
}

@Composable
fun CurrencySentinelApp(viewModel: SentinelViewModel = viewModel()) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val evidenceConsent by viewModel.evidenceConsent.collectAsStateWithLifecycle()
    val scanState by viewModel.scanLabState.collectAsStateWithLifecycle()
    val vaultReports by viewModel.vaultReports.collectAsStateWithLifecycle()
    val activeReport by viewModel.activeReport.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()

    val isArabic = language == AppLanguage.AR
    val strings = stringsFor(language)
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isDetailRoute = currentDest?.hasRoute(ReportRoute::class) == true ||
        currentDest?.hasRoute(SerialWorkbenchRoute::class) == true ||
        currentDest?.hasRoute(ComparisonLabRoute::class) == true

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        CurrencySentinelTheme(isArabic = isArabic) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = ObsidianBg,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        SentinelTopBar(
                            strings = strings,
                            onToggleLanguage = viewModel::toggleLanguage
                        )
                    },
                    bottomBar = {
                        if (!useRail && !isDetailRoute) {
                            NavigationBar(containerColor = CarbonSurface) {
                                val navItems = listOf(
                                    Triple(DashboardRoute, strings.tabDashboard, Icons.Filled.Dashboard to "nav_dashboard"),
                                    Triple(ScanLabRoute, strings.tabScanLab, Icons.Filled.DocumentScanner to "nav_scan_lab"),
                                    Triple(CatalogRoute, strings.tabCatalog, Icons.Filled.MenuBook to "nav_catalog"),
                                    Triple(VaultRoute, strings.tabVault, Icons.Filled.History to "nav_vault")
                                )
                                navItems.forEach { (route, label, iconTag) ->
                                    val selected = when (route) {
                                        DashboardRoute -> currentDest?.hasRoute(DashboardRoute::class) == true
                                        ScanLabRoute -> currentDest?.hasRoute(ScanLabRoute::class) == true
                                        CatalogRoute -> currentDest?.hasRoute(CatalogRoute::class) == true
                                        VaultRoute -> currentDest?.hasRoute(VaultRoute::class) == true
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(DashboardRoute) { saveState = true }
                                            }
                                        },
                                        icon = { Icon(iconTag.first, contentDescription = label) },
                                        label = { Text(label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = OpticalCyan,
                                            selectedTextColor = OpticalCyan,
                                            indicatorColor = RecessedPanel,
                                            unselectedIconColor = MutedSteel,
                                            unselectedTextColor = MutedSteel
                                        ),
                                        modifier = Modifier.testTag(iconTag.second)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail && !isDetailRoute) {
                            NavigationRail(containerColor = CarbonSurface) {
                                val navItems = listOf(
                                    Triple(DashboardRoute, strings.tabDashboard, Icons.Filled.Dashboard),
                                    Triple(ScanLabRoute, strings.tabScanLab, Icons.Filled.DocumentScanner),
                                    Triple(CatalogRoute, strings.tabCatalog, Icons.Filled.MenuBook),
                                    Triple(VaultRoute, strings.tabVault, Icons.Filled.History)
                                )
                                navItems.forEach { (route, label, icon) ->
                                    val selected = when (route) {
                                        DashboardRoute -> currentDest?.hasRoute(DashboardRoute::class) == true
                                        ScanLabRoute -> currentDest?.hasRoute(ScanLabRoute::class) == true
                                        CatalogRoute -> currentDest?.hasRoute(CatalogRoute::class) == true
                                        VaultRoute -> currentDest?.hasRoute(VaultRoute::class) == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) { launchSingleTop = true }
                                        },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = OpticalCyan,
                                            selectedTextColor = OpticalCyan,
                                            indicatorColor = RecessedPanel
                                        )
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = DashboardRoute,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable<DashboardRoute> {
                                DashboardScreen(
                                    language = language,
                                    strings = strings,
                                    integrity = viewModel.integrityReport,
                                    isOpenCvActive = viewModel.isOpenCvActive,
                                    evidenceConsent = evidenceConsent,
                                    recentReports = vaultReports,
                                    onToggleConsent = viewModel::setEvidenceConsent,
                                    onLaunchQuickScan = {
                                        viewModel.setScanDepthMode(ScanDepthMode.QUICK_SCAN)
                                        viewModel.selectScanTab(ScanInputTab.CAMERA)
                                        navController.navigate(ScanLabRoute)
                                    },
                                    onLaunchDeepScan = {
                                        viewModel.setScanDepthMode(ScanDepthMode.DEEP_FORENSIC_SCAN)
                                        viewModel.selectScanTab(ScanInputTab.CAMERA)
                                        navController.navigate(ScanLabRoute)
                                    },
                                    onLaunchSerialAuditor = {
                                        viewModel.selectScanTab(ScanInputTab.MANUAL)
                                        navController.navigate(ScanLabRoute)
                                    },
                                    onOpenComparisonLab = { repId ->
                                        navController.navigate(ComparisonLabRoute(repId))
                                    },
                                    onOpenReport = { repId ->
                                        navController.navigate(ReportRoute(repId))
                                    }
                                )
                            }
                            composable<ScanLabRoute> {
                                ScanLabScreen(
                                    viewModel = viewModel,
                                    state = scanState,
                                    strings = strings,
                                    isAnalyzing = isAnalyzing,
                                    errorBanner = errorBanner,
                                    onNavigateToReport = { reportId ->
                                        navController.navigate(ReportRoute(reportId))
                                    }
                                )
                            }
                            composable<CatalogRoute> {
                                CatalogScreen(
                                    catalog = viewModel.catalog,
                                    language = language,
                                    strings = strings
                                )
                            }
                            composable<VaultRoute> {
                                VaultScreen(
                                    reports = vaultReports,
                                    language = language,
                                    strings = strings,
                                    onOpenReport = { reportId ->
                                        navController.navigate(ReportRoute(reportId))
                                    },
                                    onDeleteReport = viewModel::deleteReport,
                                    onClearVault = viewModel::clearVault
                                )
                            }
                            composable<ReportRoute> { entry ->
                                val route = entry.toRoute<ReportRoute>()
                                ForensicReportScreen(
                                    reportId = route.reportId,
                                    viewModel = viewModel,
                                    report = activeReport,
                                    language = language,
                                    strings = strings,
                                    onOpenSerialWorkbench = { repId ->
                                        navController.navigate(SerialWorkbenchRoute(repId))
                                    },
                                    onOpenComparisonLab = { repId ->
                                        navController.navigate(ComparisonLabRoute(repId))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<SerialWorkbenchRoute> { entry ->
                                val route = entry.toRoute<SerialWorkbenchRoute>()
                                SerialWorkbenchScreen(
                                    reportId = route.reportId,
                                    viewModel = viewModel,
                                    report = activeReport,
                                    language = language,
                                    strings = strings,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<ComparisonLabRoute> { entry ->
                                val route = entry.toRoute<ComparisonLabRoute>()
                                ComparisonLabScreen(
                                    reportId = route.reportId,
                                    viewModel = viewModel,
                                    report = activeReport,
                                    catalog = viewModel.catalog,
                                    language = language,
                                    strings = strings,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

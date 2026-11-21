package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.catalog.CurrencyCatalogRepository
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.CurrencySentinelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Currency Sentinel", appName)
  }

  @Test
  fun `DashboardScreen renders FlowRow and primary action buttons without runtime errors`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val integrity = CurrencyCatalogRepository(context).getIntegrityReport()
    val strings = stringsFor(AppLanguage.AR)

    composeTestRule.setContent {
      CurrencySentinelTheme(isArabic = true) {
        DashboardScreen(
          language = AppLanguage.AR,
          strings = strings,
          integrity = integrity,
          isOpenCvActive = false,
          evidenceConsent = false,
          recentReports = emptyList(),
          onToggleConsent = {},
          onLaunchQuickScan = {},
          onLaunchDeepScan = {},
          onLaunchSerialAuditor = {},
          onOpenComparisonLab = {},
          onOpenReport = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dash_quick_scan_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dash_deep_scan_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("evidence_consent_switch").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `CatalogScreen renders FlowRow filter chips without runtime errors`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val catalog = CurrencyCatalogRepository(context).getCatalog()
    val strings = stringsFor(AppLanguage.EN)

    composeTestRule.setContent {
      CurrencySentinelTheme(isArabic = false) {
        CatalogScreen(
          catalog = catalog,
          language = AppLanguage.EN,
          strings = strings
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("catalog_filter_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("catalog_filter_sar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("catalog_filter_usd").assertIsDisplayed()
  }
}

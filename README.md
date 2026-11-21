# Currency Sentinel

**Offline Bilingual Banknote Checker for Saudi Riyal (SAR) & US Dollar (USD)**

> **Created by:** **ahmed ban harhara**

Currency Sentinel is a 100% offline Android application built with Kotlin and Jetpack Compose that helps everyday users inspect **Saudi Riyal (SAR)** and **US Dollar (USD)** banknotes directly on their device—without internet access or cloud uploads.

It combines on-device camera analysis, optical character recognition (OCR), visual/structural checks, serial-number verification, a local encrypted scan history, and an official banknote reference guide in both **Arabic (RTL)** and **English (LTR)**.

---

## Key Features

- **100% Offline & Private**
  - Runs completely on-device with no network calls or remote servers.
  - Scan history and optional banknote photos are stored locally in an encrypted Room database (`VaultCrypto`).
- **Two Specialized Currencies**
  - **Saudi Riyal (SAR):** Covers official paper and polymer issues (including 5 SAR cotton-linen vs. polymer editions) with clear epistemic transparency regarding unpublished central bank prefix tables.
  - **US Dollar (USD):** Supports historical and modern series, Federal Reserve Bank district codes (`A1–L12`), series year letters (`1996–2025`), plate/position fields, and star (`★`) replacement notes.
- **Simple, Everyday Interface with On-Demand Details**
  - **Home Dashboard:** Large one-tap **Quick Camera Scan** launch, 3-step guide for first-time users, consent toggle for saving scanned photos on-device, and recent check summaries.
  - **3 Ways to Inspect (`Scan`):**
    1. **Camera:** Quick single-shot scan or Detailed 3-angle scan with live sharpness/exposure meters and frame alignment guide.
    2. **Photos:** Select front and optional back photos via Android's zero-permission Photo Picker.
    3. **Serial Check:** Verify serial format, matching corner serials, and optional USD metadata manually.
  - **Clear Results & Collapsible Breakdown:** Displays plain-language status badges (*OK / Check / Unclear*), a quick summary, and actionable notes upfront, while tucking technical scores, occlusion heatmaps, and segment breakdowns inside an expandable **"Show Detailed Breakdown"** section.
  - **Follow-Up Tools:** **Fix Serial** workbench (correct look-alike OCR characters such as `0`/`O` and recheck immediately), **Compare** view against official specifications, and **PDF export**.
  - **Currency Guide (`Guide`):** Filterable reference catalog of SAR & USD denominations, dimensions, substrate materials, security features, and USD lookup tables.
- **Transparent Camera Limitations**
  - Every report includes a clear notice explaining that a smartphone camera checks visible design, dimensions, colors, and serial numbers only—it cannot test physical paper/polymer texture, magnetic ink, or UV/micro-security features.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI & Navigation** | Jetpack Compose, Material Design 3, Navigation Compose (`@Serializable` type-safe routes), Adaptive `NavigationBar` / `NavigationRail` |
| **On-Device Vision & AI** | CameraX (`camera2`, `core`, `lifecycle`, `view`), Google ML Kit Text Recognition (`16.0.1`), OpenCV Android (`4.11.0`), LiteRT (`2.3.2`) |
| **Persistence & Security** | Room + KSP (`2.7.0`), DataStore Preferences (`1.1.7`), Local AES-GCM Encryption (`VaultCrypto`), Android PDF Document Export |
| **Image Loading & Serialization** | Coil Compose (`2.7.0`), `kotlinx.serialization-json`, Kotlin Coroutines |
| **Testing** | JUnit 4, Robolectric (`4.16.1`), Jetpack Compose UI Test, Kotlinx Coroutines Test |
| **Build System** | Android Gradle Plugin `9.1.1`, Gradle `9.3.1`, Min SDK `24`, Compile & Target SDK `36` |

---

## Project Architecture

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App shell, adaptive nav bar/rail, RTL/LTR locale switching
├── catalog/
│   ├── CatalogModels.kt             # SAR & USD banknote catalog & provenance models
│   ├── CurrencyCatalogRepository.kt # Built-in SAR/USD specifications & USD lookup tables
│   └── CatalogIntegrityVerifier.kt  # Startup integrity verification of catalog assets
├── data/
│   ├── BitmapLoader.kt              # URI/camera image decoding & normalization
│   ├── ForensicPdfExporter.kt       # Offline PDF inspection report generator
│   ├── LanguagePreferences.kt       # DataStore user language & photo-saving consent
│   ├── SentinelDatabase.kt          # Room database & DAO wiring
│   ├── VaultCrypto.kt               # Local encryption for archived history/photos
│   └── VaultRepository.kt           # Encrypted history persistence & duplicate lookup
├── engine/
│   ├── cv/                          # OpenCV bridge, LiteRT classifiers, capture calibration
│   ├── identity/                    # Currency, denomination, issue & substrate identification
│   ├── serial/                      # Serial parsing, rule validation & duplicate checks
│   └── vision/                      # Parallel visual analyzers, contradiction & fusion engines
├── i18n/
│   └── AppStrings.kt                # Full Arabic & English localization strings
└── ui/
    ├── components/                  # Top bar, verdict banner, metric grids & overlays
    ├── screens/                     # Dashboard, Scan Lab, Report, Workbench, Compare, Guide, History
    ├── state/                       # SentinelViewModel & reactive UI state
    └── theme/                       # Color palette, typography & Material 3 shapes
```

---

## Automatic GitHub Build (CI/CD)

This repository is pre-configured to **build and test automatically on GitHub** as soon as you push or upload its files:

- **`.github/workflows/android-ci.yml`**
  - Runs automatically on every `push` (`main` / `master` or any branch) and `pull_request` (plus manual runs from the **Actions** tab).
  - Decodes `debug.keystore.base64` into `debug.keystore` so debug signing works immediately in CI.
  - Runs all unit and Robolectric UI tests (`:app:testDebugUnitTest`).
  - Builds the installable Debug APK (`:app:assembleDebug`).
  - Uploads **`app-debug-apk`** and **`unit-test-reports`** as downloadable GitHub Actions artifacts.

- **`.github/workflows/android-release.yml`**
  - Triggers on version tags (e.g. `v1.0.0`) or manual workflow dispatch to package build outputs and optionally sign release APK/AAB builds when repository keystore secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) are configured.

---

## Building Locally

### Prerequisites
- **JDK 21** (Temurin / OpenJDK 21)
- **Android SDK** (Platform 36)

### Steps
1. Clone the repository:
   ```bash
   git clone <your-repository-url>
   cd <repository-folder>
   ```
2. Prepare the debug keystore (included as Base64 for local/CI signing):
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
3. Make the Gradle wrapper executable (Linux/macOS):
   ```bash
   chmod +x gradlew
   ```
4. Run unit & Robolectric UI tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
5. Build the Debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Author

- **ahmed ban harhara** — Creator & Developer of Currency Sentinel

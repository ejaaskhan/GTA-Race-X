# GTA RACE X — DRIVE BEYOND LIMITS.

![Version](https://img.shields.io/badge/Version-2.0.0-E50914)
![Android](https://img.shields.io/badge/Platform-Android%20(API%2024--36)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?logo=jetpackcompose&logoColor=white)
![OpenGL ES 3.0](https://img.shields.io/badge/3D%20Engine-OpenGL%20ES%203.0-5586A4?logo=opengl&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A?logo=gradle&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)

> **Tip:** After pushing to your GitHub repository, you can replace the generic CI badge above with live workflow status badges using your own `<OWNER>/<REPO>`:
> `![Build & Release](https://github.com/<OWNER>/<REPO>/actions/workflows/android-build.yml/badge.svg)`
> `![PR CI](https://github.com/<OWNER>/<REPO>/actions/workflows/ci-tests.yml/badge.svg)`

**GTA Race X** (*Game Created by Dev. E*) is a native Android 3D open-world street racing game built with **Kotlin**, **Jetpack Compose**, and a custom hardware-accelerated **OpenGL ES 3.0** rendering engine—optimized for smooth 30–60 FPS gameplay on 6–8 GB RAM Android devices.

---

## Key Features

- **Splash Screen & Creator Credit:** Animated neon **GTA RACE X — DRIVE BEYOND LIMITS.** launch splash displaying **Game Created by Dev. E** with a seamless 3D camera fly-in into the open world.
- **Expanded 10-Zone Realistic Open-World Map:** Explore **Downtown**, **Residential**, **Industrial**, **Elevated Highway**, **Suspension Bridge**, **Neon Tunnel**, **Racing District**, plus the expanded **Coastal Highway** (oceanfront beach, palms, guardrails), **Mountain Hills** (continuous elevated ridge roads, pines, boulders, overlooks), **City Parks** (turf lawns, walkways, fountains), and **4 Glowing Neon Emerald Drive-Through Repair Bays**.
- **Realistic 3D Car Visuals & Neon Showroom Garage:** 5 original vehicle classes (Sports Car, Supercar, Muscle Car, Drift Car, Hypercar) featuring slatted radiator grilles, side brake-cooling ducts, mirror stalks, wheel-well liners, interior bucket seats & rollbar silhouettes, dual/quad exhausts with dark inner bores, and ventilated brake rotors with crimson calipers inside alloy rims—plus 360° drag orbit preview, 6 visual customizations, and 6 physics-backed mechanical upgrades.
- **Car Accident & Progressive Damage System:** Directional 4-zone chassis damage (Front/Rear/Left/Right), crash sparks & tumbling road-bouncing debris, rising hood engine smoke below 55% integrity (dark oil soot + hot embers below 28%), dynamic paint wear/scuff shaders, front-impact headlight flicker, progressive top-speed/acceleration loss and asymmetric steering pull, crimson impact screen flash, live HUD Chassis Integrity bar, and instant 100% restoration via **Drive-Through Repair Bays** or the HUD **Quick Repair** button.
- **5 Playable Game Modes:** Free Drive, Street Racing (vs. 3 overtaking AI rivals), Time Trial, Checkpoint Challenge, and Drift Challenge with live countdowns, expanded mini-map radar with emerald Repair Bay `+` markers, rewards, and mission completion screens.
- **Dynamic Weather, Lighting & Pooled VFX:** Automatic or manual Day/Night cycle, Rain (reduced wet-road grip + rain streaks + tire mist), Fog, procedural clearcoat reflections, feathered elliptical contact shadows, and 144-particle zero-allocation tire smoke, engine smoke, crash debris, Nitro flame trails, and collision sparks.
- **Procedural Synthwave & Vehicle Audio Engine:** Zero-allocation real-time DSP synthesis for V10 engine RPM, throttle growl, ceramic brakes, tire screech, Nitro spool/afterburner, impact collisions, UI chimes, and a 120 BPM Neon Synthwave soundtrack with independent **Music** and **SFX** toggles.
- **Graphics Optimization & Player Progression:** Low / Medium / High LOD presets, landscape immersive fullscreen, XP/Levels/10 Achievements, and hybrid **Jetpack DataStore + Cloud Firestore** sync via Google Sign-In.

---

## Automated GitHub Actions Workflows

This repository ships with two turnkey workflows inside `.github/workflows/`:

| Workflow File | Triggers | Permissions | Outputs |
| --- | --- | --- | --- |
| `.github/workflows/ci-tests.yml` | Pull requests to `main` / `master`, manual dispatch | `contents: read` | Debug Kotlin compilation, all unit & Robolectric tests, HTML test report artifact (`pr-unit-test-report`) |
| `.github/workflows/android-build.yml` | Push to `main` / `master`, `v*` version tags, manual dispatch | `contents: write` | Versioned **Debug APK** (`GTA-Race-X-v2.0.0-debug.apk`), **Debug AAB**, optional **Signed Release APK & AAB**, plus **automatic GitHub Releases** matching `versionName` on every push |

---

## Downloading & Installing the APK Directly from GitHub (No PC Required)

### Option 1: Direct One-Tap APK Download via GitHub Releases (Automatic on Every Push!)
1. Push your updates to `main` or `master` (or click **Run workflow** under **Actions → Android APK & AAB Build and Release**).
2. Wait for the build job to turn green (~3–6 minutes on first run, faster with Gradle caching).
3. Open your repository's **Releases** tab on GitHub — **GTA Race X v2.0.0** is automatically created/updated with standalone **`GTA-Race-X-v2.0.0-debug.apk`** and **`.aab`** files ready for one-tap download (no zip extraction required on Android!).

### Option 2: From GitHub Actions Artifacts or Custom Version Tags
- Every run also uploads **`GTA-Race-X-v2.0.0-debug-apk`** and **`GTA-Race-X-v2.0.0-debug-aab`** (plus signed Release artifacts when signing secrets are set) under the workflow run's **Artifacts** section.
- You can also push any custom `v*` tag (e.g., `git tag v2.0.0 && git push origin v2.0.0`) to publish a matching release tag.

---

## Optional: Configuring Signed Release APK & Play Store AAB Builds

Debug APKs and AABs build and sign automatically out of the box using the tracked `debug.keystore.base64` archive.

To enable automatic **signed Release APK and Play Store AAB** builds in GitHub Actions:

1. **Create a release upload keystore** (if you don't already have one—note the required alias `upload` matching `app/build.gradle.kts`):
   ```bash
   keytool -genkeypair -v \
     -keystore my-upload-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias upload
   ```
2. **Base64-encode the keystore** (single line, no line wraps):
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
   *(On macOS: `base64 -i my-upload-key.jks | tr -d '\n'`)*
3. **Add Repository Secrets** under **GitHub Repository → Settings → Secrets and variables → Actions → New repository secret**:

| Secret Name | Required For | Value |
| --- | --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Signed Release APK & AAB | Single-line Base64 string of your `.jks` keystore (alias `upload`) |
| `STORE_PASSWORD` | Signed Release APK & AAB | Keystore password |
| `KEY_PASSWORD` | Signed Release APK & AAB | Key password for alias `upload` |
| `GOOGLE_SERVICES_JSON_BASE64` | *(Optional)* Public forks | Base64-encoded `app/google-services.json` if omitted from git |

> **Security Note:** Never commit `.jks` or `.keystore` files to Git. `.gitignore` automatically ignores `*.jks`, `*.keystore`, `debug.keystore`, `.env`, and `/dist`.

---

## Building Locally from a Fresh GitHub Clone

### Prerequisites
- **JDK 21** (required for Gradle 9.3.1 & AGP 9.1.1)
- **Android SDK** with API 36.1 (or Android Studio Ladybug+ / Narwhal+)

### Commands
```bash
# 1. Clone the repository
git clone https://github.com/<OWNER>/<REPO>.git
cd <REPO>

# 2. Restore the debug keystore from the tracked base64 archive
base64 -d debug.keystore.base64 > debug.keystore

# 3. Run all unit & Robolectric tests
./gradlew :app:testDebugUnitTest

# 4. Build Debug APK (outputs app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# 5. Build Debug AAB (outputs app/build/outputs/bundle/debug/app-debug.aab)
./gradlew :app:bundleDebug
```

*(Optional)* To build a signed Release APK or AAB locally, export `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`, then run `./gradlew :app:assembleRelease :app:bundleRelease`.

---

## Project Architecture Overview

- **`:app` (Android Application Module):**
  - `engine/` — OpenGL ES 3.0 renderer, procedural shaders (including dynamic damage wear) & 512×512 textures, detailed 3D vehicle/wheel & expanded open-world meshes (Coastal Highway, Mountain Hills, City Parks, 4 Repair Bays), 4-wheel suspension/drift/accident-damage physics, AI traffic & racing rivals, pooled VFX particle system.
  - `audio/` — Real-time procedural DSP synthesizer (engine, acceleration, brakes, tire screech, Nitro, collisions, UI SFX, 120 BPM synthwave BGM).
  - `data/` — Vehicle catalog, career events, XP/level/achievement rules, Jetpack DataStore local persistence, and Cloud Firestore sync repositories.
  - `ui/` — Jetpack Compose Material 3 overlays (Animated Splash with *Game Created by Dev. E*, Google Sign-In, Main Menu, Play Mode Selector, 3D Garage, Career, Driver Profile, Settings, Racing HUD with Chassis Integrity & Quick Repair).
  - `src/test/` — Automated unit & Robolectric test suite covering physics, expanded world zones, accident damage & repair, VFX pooling, progression, and Compose UI.
- **`firestore.rules` & `firestore.test.js`:** Zero-trust Cloud Firestore security rules and standalone Node.js emulator test suite.

---

## Troubleshooting

- **"App not installed" / Blocked on Android phone:** Enable **Install unknown apps** for your browser or Files app in Android Settings. If you previously installed a differently signed build, uninstall the old build first.
- **GitHub Actions skipped Release APK:** Confirm all three secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) are populated in Repository Settings → Secrets and that your `.jks` key alias is `upload`.
- **Local `./gradlew` fails with `debug.keystore not found`:** Run `base64 -d debug.keystore.base64 > debug.keystore` in the repository root.
- **Local Gradle JVM version error:** Ensure `JAVA_HOME` points to **JDK 21** (`java -version`).

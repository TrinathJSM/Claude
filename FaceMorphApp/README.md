# FaceMorph — 100% Offline Android Face Morphing App

> **This app makes zero network requests.**
> Verified with Android Network Profiler — no traffic observed.

---

## Overview

FaceMorph is a production-grade Android face morphing application written in Kotlin.
It is architecturally and technically equivalent to apps like Reface, with one
fundamental difference: **every single operation runs on the device, forever,
with no internet required.**

No photos leave the phone. No analytics are collected. No remote flags are fetched.
No models are downloaded after install. The network socket is sealed shut at the
TLS layer via `network_security_config.xml`.

---

## Network Isolation Proof

### `res/xml/network_security_config.xml`
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors/>   <!-- empty = no trusted CAs = TLS always fails -->
    </base-config>
</network-security-config>
```

- `cleartextTrafficPermitted="false"` → blocks all HTTP
- Empty `<trust-anchors/>` → no CA is trusted → every TLS handshake fails
- Combined: the app **cannot** make any outbound connection even if a
  dependency tries

### Verification steps
1. Build the app in release mode
2. Connect a device, open **Android Studio → Profiler → Network**
3. Use every feature of the app (morph a face, save, share)
4. **Observed network traffic: 0 bytes**

---

## Architecture

```
com.facemorphapp/
├── data/
│   ├── local/
│   │   ├── db/           Room SQLite — MorphResultEntity, DAO, Database
│   │   └── datastore/    DataStore<Preferences> — settings & privacy flag
│   └── repository/       Concrete implementations of domain interfaces
│
├── domain/
│   ├── model/            Pure Kotlin data classes (no Android imports)
│   ├── repository/       Interfaces (inversion of control)
│   └── usecase/          Single-responsibility use-case classes
│
├── engine/               Face morphing pipeline (all on-device)
│   ├── FaceMorphEngine       Orchestrates 5-step pipeline
│   ├── FaceDetectorWrapper   ML Kit face detection (local model)
│   ├── SelfieSegmentorWrapper ML Kit segmentation (local model)
│   ├── AffineTransformer     Similarity matrix (Umeyama, closed-form)
│   ├── DelaunayTriangulator  Bowyer-Watson triangulation
│   ├── PoissonBlender        Jacobi solver (~20 iterations) + fallback
│   └── BilateralFilter       Pure Kotlin bilateral filter + histogram EQ
│
├── presentation/
│   ├── navigation/       Jetpack NavHost + NavGraph
│   ├── screens/          MVVM: ViewModel + Composable per screen
│   │   ├── home/
│   │   ├── targetpicker/
│   │   ├── upload/
│   │   ├── processing/
│   │   ├── result/
│   │   ├── privacy/
│   │   ├── settings/
│   │   └── crashlog/
│   └── theme/            Material 3 colour/type/theme
│
├── di/                   Hilt modules (DatabaseModule, RepositoryModule)
└── util/                 LocalCrashLogger, Extensions
```

---

## Morphing Pipeline

| Step | Component | Method | Fallback |
|------|-----------|--------|---------|
| 1 – Segmentation | `SelfieSegmentorWrapper` | ML Kit Selfie Segmentation (TFLite, local) | Full image |
| 2 – Alignment | `AffineTransformer` | Umeyama least-squares similarity | N/A |
| 3 – Warp | `FaceMorphEngine.warpDelaunay` | Delaunay piece-wise affine via `Canvas` + `Matrix.setPolyToPoly` (CPU); OpenGL ES 2.0 vertex shader (GPU on non-low-RAM) | CPU |
| 4 – Blend | `PoissonBlender` | Jacobi Poisson solver, ~20 iters, downsampled ROI | Feathered alpha + histogram EQ |
| 5 – Post-process | `BilateralFilter` | Pure Kotlin bilateral filter, σ_s=3, σ_r=25 | N/A |

---

## Performance Targets

| Operation | Target | Implementation Notes |
|-----------|--------|---------------------|
| Face detection | < 300 ms | Accurate mode, LANDMARK_MODE_ALL |
| Full morph (static) | < 3 s | 512×512 cap; Jacobi at 256×256 ROI |
| GIF (10 frames) | < 15 s | Per-frame pipeline, MediaCodec output |
| Peak heap | < 150 MB | `MAX_EDGE=1024`, bitmap recycle discipline |
| Cold start | < 2 s | Models bundled in APK — no download wait |

---

## Key Dependencies

All dependencies are offline-safe. None make network calls.

| Library | Version | Purpose |
|---------|---------|---------|
| ML Kit face-detection | 16.1.7 | Local landmark detection |
| ML Kit segmentation-selfie | 16.0.0-beta6 | Local segmentation |
| Hilt | 2.51.1 | Dependency injection |
| Compose BOM | 2024.06.00 | UI |
| Room | 2.6.1 | Local SQLite |
| DataStore | 1.1.1 | Local preferences |
| Coil | 2.7.0 | Local-only image loading |
| Lottie | 6.4.1 | Bundled animation assets |
| android-gif-drawable | 1.2.28 | Local GIF encode/decode |
| Timber | 5.0.1 | Logcat only — no remote transport |

**Explicitly excluded:** Firebase, OkHttp, Retrofit, Ktor, Volley, Play Services
(except ML Kit local models), any A/B testing SDK.

---

## Privacy

- First-launch `PrivacyOnboardingScreen` explains the offline-only guarantee
- Acknowledgement stored in DataStore (device-local, never synced to cloud)
- `data_extraction_rules.xml` + `backup_rules.xml` disable all cloud backup
- `LocalCrashLogger` writes crashes to `filesDir/crash_log.txt` — not Firebase
- Share crash log via `CrashLogScreen` (Settings → About → tap 7×) using the
  system mail Intent; sharing is always user-initiated

---

## Permissions Used

```xml
<uses-permission android:name="android.permission.INTERNET" />         <!-- declared; blocked by NSC -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" /> <!-- API 33+ -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.CAMERA" />            <!-- optional, local only -->
```

Not included: `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED`.

---

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest
```

Min SDK: 26 (Android 8.0 Oreo)  
Target SDK: 34 (Android 14)

---

## Bundling ML Kit Models

The following lines in `app/build.gradle.kts` force model bundling at build time:

```kotlin
manifestPlaceholders["mlkitFaceDetectionModuleType"]     = "local_model"
manifestPlaceholders["mlkitSelfieSegmentationModuleType"] = "local_model"
```

And in `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="face,selfie_segmentation" />
```

Without these, ML Kit attempts to download models on first use — incompatible
with the zero-network constraint.

---

## Adding Target Face Images

Place PNG / JPG / GIF files in `app/src/main/assets/targets/`.
They are loaded at runtime by `TargetFaceRepositoryImpl` via `AssetManager` —
no server involved.

---

*Built with ❤️ for privacy. Zero network. Zero compromise.*

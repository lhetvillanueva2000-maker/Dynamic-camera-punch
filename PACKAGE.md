# What's in this package

Two things ship here: the **APK** and everything that produces it.

## The built app

| File | Size | Notes |
|---|---|---|
| `dist/dcp-release.apk` | ~83 KB | Minified, resource-shrunk, signed. Install this one. |
| `dist/dcp-debug.apk` | ~100 KB | Debug-signed, `applicationId` suffixed `.debug` so it installs alongside the release build. |

```bash
adb install -r dist/dcp-release.apk
```

Package `com.dcp.punch` · minSdk 24 (Android 7.0) · targetSdk 34 · no permissions,
no network access, nothing but a `WebView` over bundled assets.

## What the APK is made of

An APK is a zip. Unzip either one and you will find exactly these pieces, each
produced from a source file in this package:

| Inside the APK | Comes from |
|---|---|
| `assets/web/**` — the whole demo: `index.html`, 4 CSS files, 6 JS files, `logo.svg` | `web/` — copied in verbatim by the `syncWebAssets` Gradle task |
| `classes.dex` | `android/app/src/main/java/com/dcp/punch/MainActivity.java` |
| `AndroidManifest.xml` (binary) | `android/app/src/main/AndroidManifest.xml` |
| `resources.arsc` + `res/**` — launcher icons, themes, colours, strings | `android/app/src/main/res/**` |
| `META-INF/*` — v2 signature block | `android/keystore/dcp-demo.jks` via `android/keystore.properties` |

The APK carries **no libraries at all** — no AndroidX, no Kotlin runtime, no
third-party code. That is why 130 KB of web app compresses into an 83 KB app.

Verify any of this yourself:

```bash
unzip -l dist/dcp-release.apk
apksigner verify --print-certs dist/dcp-release.apk
```

## The sources

```
web/         The actual product. Open web/index.html in any browser — no build,
             no server, no dependencies. This same folder is what the APK ships.
android/     The WebView wrapper: Gradle build, one Activity, resources, icons.
tools/       Playwright/Pillow scripts that regenerate everything in docs/.
docs/        Generated screenshots and the preview GIF.
README.md    Full documentation: features, gestures, architecture, embedding API.
```

## Rebuilding

```bash
cd android
./gradlew assembleRelease        # needs JDK 17+, Android SDK platform 34 + build-tools 34.0.0
```

`syncWebAssets` re-mirrors `web/` into the APK on every build, so there is only ever
one copy of the demo to edit.

## About the signing key

`android/keystore/dcp-demo.jks` (password `dcpdemo`) is a **throwaway demo key**,
included so anyone can reproduce an identical release build. It is public and
therefore worthless as a trust anchor — generate your own before distributing
anything:

```bash
keytool -genkeypair -keystore my.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10950
# then point android/keystore.properties at it
```

## Verified before packaging

- Full `clean` release + debug build from scratch: **BUILD SUCCESSFUL**, all 12 web
  asset files present in both APKs, v2 signature verifies.
- All 14 alerts and 9 activities driven through compact **and** expanded states, in
  both cutout variants and the side-by-side view: no console or page errors.
- Gestures: tap, touch-and-hold to expand, tap-outside to collapse, swipe-to-swap.
- Geometry invariant: the camera lens sits **0.00 px** from the screen's centre line
  in every state of both variants — idle, compact, two-activity, expanded.

The APK was **not** launched on a physical device or emulator (no KVM in the build
environment), so this is a structural and content verification, not a runtime one.
The UI itself was exercised in Chromium, which is the same engine Android WebView
uses.

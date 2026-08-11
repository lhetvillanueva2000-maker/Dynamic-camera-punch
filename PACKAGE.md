# What's in this package

Two things ship here: the **APK** and everything that produces it.

## The built app

| File | Size | Notes |
|---|---|---|
| `dist/dcp-v2.0.0-release.apk` | ~120 KB | Minified, resource-shrunk, signed. Install this one. |
| `dist/dcp-v2.0.0-debug.apk` | ~160 KB | Debug-signed, `applicationId` suffixed `.debug` so it installs alongside the release build. |

```bash
adb install -r dist/dcp-v2.0.0-release.apk
```

Package `com.dcp.punch` · minSdk 24 (Android 7.0) · targetSdk 34 · no network access.

This is a **system overlay theme**: it draws the island over every other app once
you grant "Display over other apps". See the README for the full permission list
and the reasoning behind each one.

## What the APK is made of

An APK is a zip. Unzip either one and you will find exactly these pieces, each
produced from a source file in this package:

| Inside the APK | Comes from |
|---|---|
| `assets/web/**` — the live demonstration: `dynamic-camera-punch-v2.0.0.html`, 4 CSS files, 6 JS files, `logo.svg` | `web/` — copied in verbatim by the `syncWebAssets` Gradle task |
| `classes.dex` — the overlay service, the Canvas-drawn island, the RAM dial, the notification/media readers | `android/app/src/main/java/com/dcp/punch/**` (13 classes across `data/`, `overlay/`, `mem/`, `ui/`) |
| `AndroidManifest.xml` (binary) | `android/app/src/main/AndroidManifest.xml` |
| `resources.arsc` + `res/**` — launcher icons, themes, colours, strings | `android/app/src/main/res/**` |
| `META-INF/*` — v2 signature block | `android/keystore/dcp-demo.jks` via `android/keystore.properties` |

The APK carries **no libraries at all** — no AndroidX, no Kotlin runtime, no
third-party code. The island is framework `View` + `Canvas` drawing, which is why
a system-wide overlay plus a full web demo fits in 120 KB.

Verify any of this yourself:

```bash
unzip -l dist/dcp-v2.0.0-release.apk
apksigner verify --print-certs dist/dcp-v2.0.0-release.apk
```

## The sources

```
android/     The theme itself: overlay service, Canvas island, RAM dial,
             notification + media readers, control panel.
web/         The live demonstration. Also opens standalone in any browser —
             no build, no server, no dependencies.
tools/       Playwright/Pillow scripts that regenerate everything in docs/.
docs/        Generated screenshots and the preview GIF.
README.md    Full documentation: what it shows, permissions, the RAM dial,
             architecture, build instructions.
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

- Full `clean` release + debug build from scratch: **BUILD SUCCESSFUL**.
- **Android Lint: zero errors.** It caught two genuine crashers on the way —
  a `registerReceiver` overload that does not exist below API 26, and a display-cutout
  mode constant that does not exist below API 30 — plus per-frame object
  allocations in both custom views, which matter a great deal for a view that
  draws for as long as the theme is enabled.
- APK contents verified: exactly six permissions (no speculative extras), all
  components present, all 12 web asset files bundled, v2 signature verifies.
- Web demo: all 14 alerts and 9 activities driven through compact **and** expanded
  states in both cutout variants — no console or page errors.
- Geometry invariant in the web build: the camera lens sits **0.00 px** from the
  screen's centre line in every state of both variants. The overlay uses the same
  centre-anchored model.

**The APK was not launched on a device or emulator** — the build environment has no
KVM, so no emulator can boot. Everything above is static and structural
verification. The overlay's runtime behaviour — window flags, permission flows,
service lifecycle on a real manufacturer skin — has not been exercised, and the
first install is the real smoke test.

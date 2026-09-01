# What's in this package

Two things ship here: the **APK** and everything that produces it.

## The built app

| File | Size | Notes |
|---|---|---|
| `dist/dcp-v2.7.1-release.apk` | 181,455 B | Minified, resource-shrunk, signed with the project key. Install this one. |
| `dist/dcp-v2.7.1-debug.apk` | 257,642 B | Unoptimised and debug-signed. `applicationId` is suffixed `.debug` and the custom permission is named after it, so it genuinely installs alongside the release build instead of colliding with it. Labelled **DCP (debug)** on the launcher. |

```
sha256  release  c64dbd7ca86a757f24ac563613d1347498e4709987f9d3cb626fd7e8013a5eb1
sha256  debug    091650ec3a1b4e495ce91fb0e2198c19be2152cd65d8a9c683695861896869e8
```

```bash
adb install -r dist/dcp-v2.7.1-release.apk
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
| `assets/web/**` — the live demonstration: `dynamic-camera-punch-v2.7.1.html`, 4 CSS files, 6 JS files, `logo.svg` | `web/` — copied in verbatim by the `syncWebAssets` Gradle task |
| `assets/support/index.html` — the offline support page, opened from the Settings tab. Makes no network requests of any kind. | `android/app/src/main/assets/support/` |
| `classes.dex` — the overlay service, the Canvas-drawn island, the three-tab control panel, the memory gauge, the notification/media readers | `android/app/src/main/java/com/dcp/punch/**` (29 classes across `data/`, `overlay/`, `mem/`, `ui/`) |
| `AndroidManifest.xml` (binary) | `android/app/src/main/AndroidManifest.xml` |
| `resources.arsc` + `res/**` — launcher icons, themes, colours, strings | `android/app/src/main/res/**` |
| The APK Signing Block — a v2 signature, sitting between the entries and the central directory rather than in `META-INF/` (there is no v1 JAR signature) | `android/keystore/dcp-demo.jks` via `android/keystore.properties` |

The APK carries **no libraries at all** — no AndroidX, no Kotlin runtime, no
third-party code. The island is framework `View` + `Canvas` drawing, and so are
the floating tab bar, the switches, every icon, the sliders, the gauge and the live preview — which is why a
system-wide overlay, a three-tab control panel, a support page and a full web
demo fit in 177 KB.

Verify any of this yourself:

```bash
unzip -l dist/dcp-v2.7.1-release.apk
apksigner verify --print-certs dist/dcp-v2.7.1-release.apk
```

## The sources

```
android/     The theme itself: overlay service, Canvas island, memory gauge,
             notification + media readers, the three-tab control panel and the
             bundled support page.
web/         The live demonstration. Also opens standalone in any browser —
             no build, no server, no dependencies.
tools/       Playwright/Pillow scripts that regenerate everything in docs/.
docs/        Generated screenshots and the preview GIF.
README.md    Full documentation: what it shows, permissions, the memory model,
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
- APK contents verified: exactly six permissions (no speculative extras), no
  `QUERY_ALL_PACKAGES` and no `INTERNET`, all four activities present, all 12 web
  asset files plus the support page bundled, v2 signature verifies across API
  24–34.
- Web demo: 31 browser assertions, including every one of the 23 presentations
  driven through compact **and** expanded states in both cutout variants with its
  content measured for clipping, and both edge-swipe directions — no console or
  page errors.
- Support page: 9 assertions in a real browser, the first of which is that it
  makes **no network requests at all**.
- Both suites run from `npm test`.
- Every `findViewById` in the control panel audited against the layouts that are
  actually inflated. v2.7.0 rewrote most of the UI, and a stale id after a
  rewrite that size is a null at runtime rather than a compile error.
- Geometry invariant in the web build: the camera lens sits **0.00 px** from the
  screen's centre line in every state of both variants. The overlay uses the same
  centre-anchored model.

**The APK was not launched on a device or emulator** — the build environment has no
KVM, so no emulator can boot. Everything above is static and structural
verification. The overlay's runtime behaviour — window flags, permission flows,
service lifecycle on a real manufacturer skin — has not been exercised, and the
first install is the real smoke test.

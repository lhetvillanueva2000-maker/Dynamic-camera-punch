<div align="center">

<img src="docs/icon-512.png" width="112" alt="dcp">

# Dynamic Camera Punch

### v2.3.0 — the theme

**A Dynamic Island for your Android punch-hole — running over every app on the phone.**

Not a mock-up and not a wallpaper: a system overlay that turns your camera cutout
into a live pill showing what is actually playing, ringing and charging.

<img src="docs/preview.gif" width="330" alt="The island morphing between idle, music, a timer, an alert and navigation">

</div>

---

## What it does

Switch the theme on and the island sits above every app on the device — home
screen, launcher, games, anything. At rest it is a circle around your camera
hole, indistinguishable from the cutout itself. Something happens, it grows out
of the hole to show you; the moment passes, it shrinks back into the circle and
is gone.

The content is real:

| Source | What the island shows | Needs |
|---|---|---|
| **Messages** | The conversation and **what was actually said** — "Family GC" over "Mum · Did you get the toolbox?" — with the sender's avatar. Tap to open the thread. | Notification access |
| **Media sessions** | The track that is actually playing, with working play/pause and skip | Notification access |
| **Calls** | The incoming caller, from the dialer's own notification | Notification access |
| **Timers & alarms** | Live countdown from the clock app | Notification access |
| **Navigation** | Turn-by-turn from your maps app | Notification access |
| **Any notification** | App icon, title, and its own actions in the expanded view | Notification access |
| **Charging** | Bolt and battery level the moment you plug in | *nothing* |
| **Battery low** | Level and warning | *nothing* |
| **Ringer** | Silent / vibrate / ring changes | *nothing* |
| **Headphones** | Connect and disconnect | *nothing* |

Grant nothing at all beyond the overlay permission and the last four still work.

### The two cutout shapes

| Variant A | Variant B |
|---|---|
| Fused to the top bezel — flat shoulders against the edge, rounded chin, grows down out of the edge | Free-floating lens, clear of the bezels, fully rounded in every state |

Pick whichever matches your phone. **The camera never moves**: the overlay window
is centre-anchored and the lens is drawn at a fixed offset inside the island, so
no state change can slide the hole off the physical sensor.

### Gestures

| Gesture | Result |
|---|---|
| **Tap** | Opens the app that posted it — a message opens that conversation, chat head and all. On music, play/pause. |
| **Touch & hold** | Expand into the detail view, with the screen dimmed behind |
| **Tap outside** | Collapse |
| **Swipe ← / →** | Swap the two concurrent activities |
| **Swipe ↓ / ↑** | Expand / collapse |

## Memory

**The app now stays out of your way instead of filling a quota.** Its real
working set is about 30–45 MB and it stays there. Open the app to find the
gauge — the dial used to be a pull tab welded to the right edge of your screen
as a second permanent overlay, which nobody asked for and everybody had to look
at. A control panel belongs in the app.

Two mechanisms, and they work together:

| | |
|---|---|
| **Manage memory automatically** (on by default) | The app watches its own footprint and releases what it can — cached avatars and artwork for anything not currently on the pill — and acts on every system memory warning, not just the critical one. |
| **The ceiling** (the gauge) | A hard limit you set by dragging the needle. Crossing 80% of it triggers the same trim early. It is a limit, not a target: the app will not grow to fill it. |

The range runs from **762 MB** up to **the RAM Android reports for your device,
minus 1.5 GB reserved for the system**. Note that Android reports rather less
than the number on the box — a "4 GB" phone typically reports about 3.5 GB, so
its ceiling tops out near 2.0 GB. A device too small to give up 762 MB after the
reserve gets its real ceiling reported instead of a nominal minimum it would be
killed for honouring.

The gauge draws the ceiling and the *measured* footprint on the same scale, read
from the same source `adb shell dumpsys meminfo` uses, so the two can always be
compared. On a healthy install the needle sits far above a very short green band.

> **What changed, and why the old design was wrong.** Until v2.2.0 the dial set a
> *target*, and the app allocated an off-heap "ballast" — hundreds of megabytes of
> real, resident, deliberately wasted memory — so the number on the dial would be a
> measurement rather than a decoration. It was honest about being waste and it was
> still waste. On a low-end phone it is indefensible: it makes this process the
> fattest thing on the device and the first one the low-memory killer reaches for,
> and it evicts your actual apps so they cold-start instead of resuming. The
> ballast is gone. Nothing replaced it.

## Choosing what it shows

The island is opt-out, not opt-in: everything is on to begin with, and the app's
**What the island shows** card has a switch for each kind of content — messages,
music, calls, timers, navigation, downloads, other notifications, and the four
permission-free system events.

Underneath it, **Apps** lists every app the island has actually heard from, each
with its own switch. Turn one off and it will never reach the cutout again. The
list is *learned from arriving notifications* rather than enumerated, which is why
this needs no `QUERY_ALL_PACKAGES`: an app that has never sent anything is not a
decision worth putting in front of you.

## Keeping the island on screen

By default, **with nothing to show there is no overlay at all**. The island
collapses back into your camera hole on the same curve it grew out of, and then
the window is removed — not left resting on top of your launcher.

If you would rather it stayed, the app has a **Keep the island on screen** switch.
On, it sits as a circle around your camera whatever is happening. Off is the
default and the behaviour most people want.

Two kinds of notification never appear, whatever the switches say:

- **"App is running" notices.** Every foreground service posts one — file sync, a
  VPN, a launcher, this app's own overlay — and none is an event worth announcing.
- **Ongoing status notices.** A keyboard, a sync adapter, USB mode, storage. These
  are permanent by nature, and until v2.3.0 any of them would take up residence on
  the island and keep the overlay on screen forever. Only calls, navigation,
  timers, alarms and downloads are allowed to persist; everything else passes
  through and is gone.

## The live demonstration

The original sandbox is still in the app — every alert and activity, both cutout
shapes, driven by hand. **The home screen works too:** tap any icon and it opens,
growing out of the icon into a full app and shrinking back into it when you tap
the home bar. It is a WebView worth roughly 80 MB, so:

**It will not open while the theme is running,** and starting the theme closes it.
It lives in its own `:demo` process, so shutting it down returns every byte to the
system instead of leaving a fattened heap inside the process that hosts the overlay.

## Install

```bash
adb install -r dist/dcp-v2.3.0-release.apk
```

Every launchable file names its own version, so there is never any doubt about
which build is in front of you:

| File | What it is | Size |
|---|---|---|
| `dcp-v2.3.0-release.apk` | The theme. **Install this.** | 132,944 bytes |
| `dcp-v2.3.0-debug.apk` | Same app built unoptimised, signed with the Android debug key and installed as `com.dcp.punch.debug` under the name **DCP (debug)**. Only useful if you are attaching a debugger; it sits alongside the release build rather than replacing it. | 177,754 bytes |
| `web/dynamic-camera-punch-v2.3.0.html` | The demonstration, openable in any browser. | — |

### Check the download before you install it

A phone browser that lands on a GitHub *page* rather than the file saves an HTML
error page under the `.apk` name, and the installer then reports the APK as
corrupt. The size column above is the quickest tell — a few KB means you got a
web page. To be certain:

```
sha256  release  cecedbe366288a4085dd3fc39be25a0f0c06608efed077bfc73c919709abba1c
sha256  debug    88dcf1ef686654b83ddb6dc0821ca2c6af6c33963f76c77d5ef6efe87f3757fb
```

Both are signed with APK Signature Scheme v2 and verify with `apksigner verify`
across the whole supported range, API 24 to 34.

The version also appears in the app's own header, and in Android's app info as
version 2.3.0 (code 6). See [CHANGELOG.md](CHANGELOG.md) for what changed.

Then open the app and work down the setup list:

1. **Display over other apps** — required. This is the permission that lets the
   island draw on top of everything, and it is granted in Settings rather than by
   a dialog.
2. **Notification access** — optional but recommended; this is what makes the
   content real rather than just charging and ringer events.
3. **Show notifications** — Android requires an ongoing notification for a service
   that runs indefinitely. That notice is also how you turn the theme off.

Minimum Android 7.0 (API 24). ~133 KB on disk, ~30–45 MB resident. No network access.

## Permissions, and why each one is there

Nothing is requested speculatively — every entry is read by code in this app.

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the island over other apps. This *is* the product. |
| `FOREGROUND_SERVICE` + `…_SPECIAL_USE` | The island must survive you leaving the app. Android 14 requires a declared service type; a persistent UI overlay is not media or location, so `specialUse` with a written justification is the honest classification. |
| `POST_NOTIFICATIONS` | The ongoing notification the OS requires the service to have. |
| `RECEIVE_BOOT_COMPLETED` | Optional "start on boot", off by default. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read notifications and media sessions. User-granted in Settings, revocable at any time. |
| `${applicationId}.permission.INTERNAL` | Our own signature-level permission, guarding one private broadcast between our two processes. Named after the application id rather than hard-coded, so the debug build declares its own and the two can coexist on one device. |

**Deliberately not requested:** `QUERY_ALL_PACKAGES` (icons come from the
notification itself), `READ_PHONE_STATE` (call state is read from the dialer's
notification instead), and `INTERNET` — the app has never made a network request
and cannot.

Notifications are read only. Nothing is dismissed, modified, stored or sent anywhere.

## How it is built

```
android/app/src/main/java/com/dcp/punch/
  DcpApp.java                 process singletons, memory-pressure handling
  BootReceiver.java           optional restart-on-boot
  data/
    Presentation.java         one thing the island can show
    Sources.java              what the island is allowed to show, by kind and by app
    IslandStore.java          state: an alert over a stack of ≤2 activities
    DcpNotificationListener.java   real notifications → presentations
    MediaMonitor.java         MediaSession → live track + transport controls
    SystemMonitor.java        charging / battery / ringer / headset broadcasts
  overlay/
    IslandService.java        foreground service, owns the two overlay windows
    IslandView.java           the island, drawn on Canvas
  mem/
    MemoryBudget.java         the ceiling, the reserve, the trimmer, the measurement
    Prefs.java                persisted settings
  ui/
    MainActivity.java         control panel
    MemoryGaugeView.java      the speedometer, in the app rather than on the edge
    DemoActivity.java         the WebView sandbox, in its own process

web/                          the demonstration — also opens in any browser
tools/                        Playwright/Pillow scripts that regenerate docs/
```

### Why the overlay is not a WebView

An overlay window has to be **exactly the size of its touchable content**, because
every pixel it covers is a pixel the app underneath stops receiving touches on. The
island changes size constantly, so its window is re-measured on every animation
frame. That is cheap for a custom `View` and ruinous for a WebView, which would
relayout its whole document 60 times a second — and cost ~80 MB besides.

So the overlay is native Canvas drawing, and the WebView survives only as the
demonstration. The geometry model is identical to the web build: measure the
content, animate `width`/`height`/`radius` between two numbers, and let one
interpolator — the same `cubic-bezier(.32,.72,0,1)` — carry the whole morph.

### The compact slots are symmetric on purpose

The camera gap has to land on the pill's horizontal centre, because that is where
the hole is drilled. A gap between two *unequal* slots does not sit in the middle,
so a long label would slide underneath the lens. Both slots are padded out to
`max(lead, trail)`, which puts the gap dead centre and gives the pill its balanced
look. When that would overflow the screen, the slots are capped and labels
ellipsise instead.

## Building

```bash
cd android
./gradlew assembleRelease      # JDK 17+, Android SDK platform 34 + build-tools 34.0.0
./gradlew lintDebug            # clean: zero errors
```

`syncWebAssets` mirrors `web/` into the APK on every build, so there is never a
second copy of the demo to keep in sync.

The release build is signed with the throwaway key in `android/keystore/`.
**Generate your own before distributing anything:**

```bash
keytool -genkeypair -keystore my.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10950
# then point android/keystore.properties at it
```

## Compatibility and honest limits

- Android 7.0+ (API 24). Overlay windows above other apps require the user to
  grant "Display over other apps" — there is no way around that, by design.
- Some manufacturer skins (notably Xiaomi/MIUI and some Huawei builds) add their
  own extra "show on top" toggle and aggressively kill background services. If the
  island vanishes after a while, that is the vendor's battery manager, and the app
  has to be exempted there.
- **The APK has not been launched on a physical device or emulator** — the build
  environment has no KVM. It compiles, passes Android Lint with zero errors, and
  its structure and signature verify, but that is not the same as a runtime test.
  Treat the first install as the real smoke test.

## Licence

MIT — see [LICENSE](LICENSE).

Not affiliated with or endorsed by Apple. "Dynamic Island" and other Apple product
names are trademarks of Apple Inc., referenced only to describe the interaction
patterns this project reproduces. All artwork is original.

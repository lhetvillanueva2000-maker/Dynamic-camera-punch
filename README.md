<div align="center">

<img src="docs/icon-512.png" width="112" alt="dcp">

# Dynamic Camera Punch

### v2.8.0 — the theme

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

Five gestures, and **you decide what each one does**. These are the defaults:

| Gesture | Default |
|---|---|
| **Tap** | Opens the app that posted it — a message opens that conversation, chat head and all. On music, play/pause. |
| **Double tap** | Nothing (so a single tap never has to wait for it) |
| **Touch & hold** | Expand into the detail view, with the screen dimmed behind |
| **Swipe ← / →** | Swap the two concurrent activities |

Any of them can be reassigned in the app to open the app, expand, collapse,
dismiss, swap, play/pause, next track, previous track, or nothing at all.
Tapping outside always collapses.

> Assignments are stored **by name**, not by position in a menu. A settings file
> that silently remaps "open the app" to "play/pause" because a later version
> inserted an action into the middle of a list is the kind of bug nobody thinks
> to look for.

In the demonstration, a drag that starts inside the phone belongs to the phone
rather than scrolling the page. Swipe up from the bottom, or inward from either
side edge, to close an open app.

## The app

Three tabs, reached from a **floating pill** above the content rather than a bar
welded to the bottom edge. The active icon rides in a coloured circle that slides
between them — and stretches slightly while it travels, so it reads as something
with weight rather than a circle teleporting. Lists run *under* the pill instead
of being cut off by it.

| Tab | What lives there |
|---|---|
| **Cards** | What the island shows, and what your gestures do. |
| **Island** | How it looks and moves, over a live preview, with named presets. |
| **Settings** | You, the island's own behaviour, this device, and the extras. |

### Categories, not one long scroll

The first screen is short on purpose: a header, the master switch, setup, and
four category rows — **system events**, **live cards**, **gestures**,
**notifications**. Each shows a strip of the icons it contains and opens its own
screen, sliding in from the right with a back arrow and a **Guide** button that
says what the category is actually for.

The top level answers "what can this thing do". Only the level below it asks you
to make forty decisions. Before v2.7.0 all forty switches were on one scroll, in
the order they happened to be written.

### The switches carry a mark

Every toggle shows a **tick when it is on and a cross when it is off**, on a
thumb that slides between them. Colour alone is the one channel a colour-blind
user does not have and a bright screen washes out; a mark survives both.

Row badges are tinted by *category*, never by state, so a screenful can be
scanned by colour while the glyph inside still says what each row is. All ~40
icons are drawn on a Canvas in a single class rather than loaded as drawables —
no resource lookups, nothing held for rows that are off screen, and nothing
allocated per frame.

### Island: the dials

**Size and position** — `X` and `Y` offset, resting size, compact corner radius,
expanded radius and width.

**Surface** — **background opacity (0–100)**, **blur behind (0–80 dp)**, opacity
at rest, border width, background and outline colour, shadow strength.

**Content** — text size (80–130%), artwork size, artwork corner radius,
transport button size.

**Media** — show the progress bar, show elapsed and remaining, and a progress
style: plain bar or **wave**.

**Movement** — animation speed, bounce, reduce-animation, the glow.

**Feel** — haptics on open and hold.

Each dial commits on release rather than on every pixel of the drag, so dragging
one is cheap even while the overlay is live and redrawing behind the app.

#### Translucency

Background opacity fades the island's whole surface, in every state — at 0 the
panel is gone and only the content is left floating. It *multiplies* whatever
alpha your background colour already carries rather than replacing it, so a
deliberately translucent custom colour is not forced back to solid by a dial
sitting at 100.

Blur is what makes translucency readable: an alpha-blended panel over a busy
wallpaper is not. It needs **Android 12 or later**, and the platform switches all
window blurs off in battery saver and when "reduce transparency" is on. The app
asks the system whether blurs are enabled and draws flat when they are not — the
card says so, rather than leaving you wondering why a slider does nothing.

### The transport row is always three buttons

A media session declares which transports it supports, and building the row from
that declaration is the obvious thing to do — until the first track of a queue
drops "previous", the row becomes two buttons, and everything shifts sideways.
A control row that changes shape reads as broken rather than as unavailable.

So the row is fixed at previous / play-pause / next, and the declared actions
only decide what is drawn **dimmed**. A session that declares nothing at all —
plenty never populate the field — is treated as supporting everything, because
greying out a control that would have worked is the worse mistake.

### Presets, with your own names

Save the whole Island tab under a name — "Night", "Discreet", "Big and loud" — and
switch between them in one tap. A preset stores the settings as JSON, so adding a
new dial in a later version needs no migration: an unknown key is ignored and a
missing one falls back to its default. Saving over an existing name replaces it
instead of stacking a second copy.

### Settings: the device readout

RAM, storage and CPU, on the same card:

- **RAM** — total, available, and what this app is actually using.
- **Storage** — used and free on the data partition.
- **CPU** — the chipset name, core count, and the share of wall-clock time this
  process has spent on a core.

That last figure is the app's own CPU use, not the system's. **A live
system-wide CPU percentage is not readable by a normal app on Android 8 or
later** — `/proc/stat` was restricted precisely so apps could not fingerprint
the device that way. Anything showing you a system CPU meter without root is
either a system app or making it up, so this shows the number it can actually
measure and says which one it is.

### Settings: support

An offline page bundled inside the APK, opened in the demo process so it costs
nothing while it is closed. It makes **no network requests at all** — no web
fonts, no remote images — which is checked by a test, and matters for an app
that declares no `INTERNET` permission and so could not load them anyway.

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

The range runs from **45 MB** — the top of the island's real working set, and
the point below which a limit stops limiting and starts handicapping — up to
**the RAM Android reports for your device, minus 1.5 GB reserved for the
system**. Android reports rather less than the number on the box: a "4 GB" phone
typically reports about 3.5 GB, so its ceiling tops out near 2.0 GB.

Tap **Capacity** at the bottom right of the app to see what the ceiling you have
chosen actually buys — how many apps can be on the list, how many notifications
can be held with their artwork, how many phone functions. Even at the 45 MB
floor that is over a hundred notifications; the numbers come from real costs (a
decoded avatar is 192×192 at 4 bytes a pixel, an app on the list is a package
name) rather than from invented ones.

The island still shows at most two activities and one alert at a time whatever
the ceiling says. That is a design limit, not a memory one, and the readout says
so rather than implying the dial can raise it.

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

Two lists you build up, both in the app, both with a **+** button.

**What the island shows** — the kinds of content: messages, music and media,
calls, timers and alarms, navigation, downloads, other notifications, and the
four permission-free system events.

**Apps** — the picker lists **every app on your device with a launcher icon**,
searchable. Add one and its notifications reach the island.

An **empty list means no restriction**, and the app says so on the card. That
matters: two empty allow-lists on a fresh install would be an island that never
appears, which anyone would reasonably read as broken. The moment you add your
first entry the list becomes a filter, and only what you have picked gets
through.

> The app picker still does **not** use `QUERY_ALL_PACKAGES`. It uses a
> `<queries>` declaration for the launcher intent, which grants sight of exactly
> the apps that have a launcher icon — the ones you would recognise — and nothing
> else. Services, providers and headless packages stay invisible to it.

## Show only on the island

**Yes, an app can do this, with two limits worth knowing.** The switch is in the
control panel and it is **off by default**.

On, a notification is cleared from the shade once the island has shown it, so one
event does not appear in two places. But:

- **The brief banner at the top still appears.** By the time any app is told
  about a notification the system has already posted it. There is no API for an
  app to suppress that, and anything claiming otherwise is either a system app or
  wrong. What this removes is the copy that would otherwise sit in the shade.
- **Cleared is gone, not hidden.** It is the same dismissal as swiping it away,
  so the island becomes the only place that event was ever shown. Miss the
  island and you have missed it. That is why it is off by default.

Ongoing notifications are never touched — they are not clearable, and an app's
own state display is not ours to delete.

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
adb install -r dist/dcp-v2.8.0-release.apk
```

Every launchable file names its own version, so there is never any doubt about
which build is in front of you:

| File | What it is | Size |
|---|---|---|
| `dcp-v2.8.0-release.apk` | The theme. **Install this.** | 185,307 bytes |
| `dcp-v2.8.0-debug.apk` | Same app built unoptimised, signed with the Android debug key and installed as `com.dcp.punch.debug` under the name **DCP (debug)**. Only useful if you are attaching a debugger; it sits alongside the release build rather than replacing it. | 263,686 bytes |
| `web/dynamic-camera-punch-v2.8.0.html` | The demonstration, openable in any browser. | — |

### Check the download before you install it

A phone browser that lands on a GitHub *page* rather than the file saves an HTML
error page under the `.apk` name, and the installer then reports the APK as
corrupt. The size column above is the quickest tell — a few KB means you got a
web page. To be certain:

```
sha256  release  013ebe01c86d598d684cfd4fa90e6aeec9d619c65bf653f7120f31971c4bc4b6
sha256  debug    31c6556c6f2f6c534891dbcf8181304efb9b64d201466ec50e780241e90ef790
```

Both are signed with APK Signature Scheme v2 and verify with `apksigner verify`
across the whole supported range, API 24 to 34.

The version also appears in the app's own header, and in Android's app info as
version 2.8.0 (code 12). See [CHANGELOG.md](CHANGELOG.md) for what changed.

Then open the app and work down the setup list:

1. **Display over other apps** — required. This is the permission that lets the
   island draw on top of everything, and it is granted in Settings rather than by
   a dialog.
2. **Notification access** — optional but recommended; this is what makes the
   content real rather than just charging and ringer events.
3. **Show notifications** — Android requires an ongoing notification for a service
   that runs indefinitely. That notice is also how you turn the theme off.

Minimum Android 7.0 (API 24). ~181 KB on disk, ~30–45 MB resident. No network access.

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
| `<queries>` (not a permission) | Lets the app picker list apps that have a launcher icon. This is the sanctioned alternative to `QUERY_ALL_PACKAGES` and grants far less. |

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
    Sources.java              the two allow-lists: kinds, and apps
    Gestures.java             gesture → action, stored by name
    IslandStore.java          state: an alert over a stack of ≤2 activities
    DcpNotificationListener.java   real notifications → presentations
    MediaMonitor.java         MediaSession → live track + transport controls
    SystemMonitor.java        charging / battery / ringer / headset broadcasts
  overlay/
    IslandService.java        foreground service, owns the two overlay windows
    IslandView.java           the island, drawn on Canvas
  mem/
    MemoryBudget.java         the ceiling, the reserve, the trimmer, the measurement
    Appearance.java           every look/motion dial, and the named presets
    DeviceStats.java          RAM, storage, CPU — and what each can honestly report
    Prefs.java                persisted settings
  ui/
    MainActivity.java         control panel: three panes, cross-faded
    Rows.java                 the six shapes every screen is built from
    Glyphs.java               ~40 icons, drawn rather than loaded
    IconBadge.java            a glyph in a tinted circle
    CheckSwitch.java          the switch that carries a tick or a cross
    FloatingTabBar.java       the floating pill and its sliding selector
    SubScreen.java            a detail screen, pushed over a tab
    SliderRow.java            one dial: label, value, track, grab halo
    IslandPreview.java        the live idle → compact → expanded loop
    MemoryGaugeView.java      the speedometer, in the app rather than on the edge
    StatBar.java              a device-stat row
    PickerActivity.java       the "+" picker: installed apps, and kinds of content
    SupportActivity.java      the bundled offline support page
    DemoActivity.java         the WebView sandbox, in its own process

android/app/src/main/assets/support/   the support page, offline, no requests
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

```bash
npm test                       # 31 demo assertions + 9 support-page assertions
```

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

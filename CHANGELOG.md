# Changelog

## v2.7.1 — two bugs from the rebuild

**The demonstration opened to a black screen.** The demo page carries the
version in its filename and `DemoActivity` named that file directly, so when
2.7.0 renamed it the loader was still asking for the 2.6.0 one: `ERR_FILE_NOT_FOUND`
behind an empty WebView. The page is now found rather than named — the activity
lists its own `web` assets and opens what it finds — so a rename cannot break it
again, and a genuinely missing page says so instead of showing black.

The same latent bug was in `tools/test-demo.js` and `tools/capture.js`, which
both pointed at the file by name. A test naming a file that no longer exists is
worse than no test, so both discover it now too.

**The app list showed a placeholder instead of app icons.** Apps on the
Notifications list were badged with the generic squares glyph, which identifies
nothing — the point of listing apps is recognising them, and people recognise
icons faster than package names. `IconBadge` now takes a real `Drawable` and the
rows pass the app's own launcher icon, drawn through its bounds rather than
rasterised into a bitmap, so a list of them costs nothing beyond the drawables
the package manager already returned. An app uninstalled since it was added
falls back to the placeholder rather than leaving a gap.

Both were reported from a device, which is the only place either could have been
caught: the first needs the APK's asset tree at runtime, and the second needs
real installed apps.

## v2.7.0 — the control panel, rebuilt

v2.6.0 gave the app three tabs. It did not give it a way of looking at forty
switches, so the first tab was still one long scroll with all of them on it.
This release fixes the organisation, not the features.

**The tab bar floats.** A rounded pill above the content instead of a full-width
bar welded to the bottom edge, with the active icon riding in a coloured circle
that slides between the three. The circle stretches slightly while it travels
and settles round, which reads as weight rather than as a circle teleporting.
Labels are gone — three destinations with distinct shapes do not need them, and
dropping them buys the height that makes it look like a control rather than a
toolbar. Lists now run under the pill rather than being cut off by it.

**Categories instead of one scroll.** The Cards tab is now a header, the master
switch, setup, and four category rows — system events, live cards, gestures,
notifications — each showing a strip of the icons it contains and opening its
own screen that slides in from the right with a back arrow and a **Guide**
button. The top level says what the island can do; only the level below asks for
decisions.

**Switches say it twice.** Every toggle carries a tick when on and a cross when
off. Colour alone is the one channel a colour-blind user does not have and a
bright screen washes out; a mark survives both.

**Icons on every row, drawn rather than loaded.** Around forty of them in one
class — no resource lookups, no `Drawable` objects held for off-screen rows, and
nothing allocated per frame. They are tinted by category, never by state, so a
screen can be scanned by colour while the glyph still says what each row is.

**A new palette.** The old one was near-black with a hard iOS blue: card edges
vanished into the background and every accent shouted. Cards now sit a clear
step above the background, and the accent is a periwinkle quiet enough to head
every section without dominating the page.

**Island and Settings were rebuilt to match**, because leaving them in the old
card style would have made the app look like two apps stitched together. All
three tabs are now built from the same six shapes — section header, card, toggle
row, value row, nav row, note — defined once in `Rows` rather than agreed by
hand across a dozen layout files. Notification mode, auto-hide delay and "show
only on the island" moved into the Notifications screen where they belong.

One correctness point worth recording: the content allow-list stores *empty
means everything*, so the first switch turned off could not simply be removed —
the list would still be empty and the switch would spring back on. Turning one
off while the list is empty now writes every other kind in first, which is the
state the user was already looking at, and only then removes the one they
touched.

Nothing about the island itself changed: same overlay, same geometry, same
behaviour on your cutout. Memory is unchanged at 30–45 MB — the rebuild replaced
roughly forty vector drawables with one class and hand-draws only the pieces
that actually animate.

Verified: `clean assembleRelease assembleDebug lintDebug` → **zero lint errors**
and zero unused resources; both APKs signature-verified across API 24–34; still
exactly six permissions with no `QUERY_ALL_PACKAGES` and no `INTERNET`; every
`findViewById` audited against the layouts actually inflated, because a stale id
after a rewrite this size is a null at runtime rather than a compile error; 31
demo assertions and 9 support-page assertions pass, the latter now committed as
`tools/test-support.js` and wired into `npm test`. Release 180,643 B, debug
256,410 B. Still not launched on a device — this environment has no KVM, and
this release rewrote most of the UI, so the first install is a real test.

## v2.6.0 — three tabs, and a theme you can actually theme

The control panel was one long scroll that did everything. It is now three panes
with a sliding indicator: **Cards** (what the island shows), **Look** (how it
looks and moves), **Settings** (you, your device, support). Tabs press and lift
under a finger, panes cross-fade and rise 14dp — the hover/press feedback the
request asked for, drawn on Canvas like everything else here.

**Look is the real addition.** Seventeen dials: position on both axes, compact
corner radius, expanded radius and width, idle scale, opacity at rest, border
width, background and outline colour, shadow strength, animation speed, bounce,
reduce-animation, glow, whether an arriving notification lands minimised or
already open, and how long an alert stays up. Above them, a **live preview** that
loops idle → compact → expanded with your current settings, so a dial's effect is
visible without leaving the screen. Each slider fires cheap updates while
dragging and commits on release, so dragging one does not thrash the overlay
redrawing behind the app. The preview stops itself the moment its window is
invisible; an animation nobody can see is pure battery.

**Presets with your own names.** Save the whole Look tab as "Night" or "Discreet"
and switch in one tap. Stored as JSON, so a dial added later needs no migration —
unknown keys are ignored, missing ones fall back. Saving over a name replaces it.
One bug caught while writing it: the preset was storing the cutout variant into
the appearance file, where nothing reads it. A preset that silently forgets half
of what it promised is worse than one that never offered.

**Gestures are assignable.** Tap, double tap, long press, swipe left, swipe right
→ open the app, expand, collapse, dismiss, swap, play/pause, next, previous, or
nothing. Defaults are unchanged, and double tap defaults to *nothing* on purpose:
if nothing is bound to it, a single tap fires immediately instead of waiting
300 ms to find out whether a second one is coming. Assignments are stored by
name, not by menu position, so inserting an action in a later version cannot
silently remap "open the app" to "play/pause".

**CPU and storage, next to RAM.** Total/available/used RAM, used and free
storage, and the chipset with its core count. The CPU figure is **this app's own
share of wall-clock time**, and the card says so, because a live system-wide CPU
percentage has not been readable by an ordinary app since Android 8 — `/proc/stat`
was restricted to stop apps fingerprinting the device. Showing a made-up system
meter would have been easy and dishonest.

**A support page, bundled and offline.** Opened from Settings, running in the
`:demo` process so it costs nothing while closed. It makes no network requests at
all — the supplied page's web font was replaced with a system stack and its
remote QR image with a local one — which is the only version that could work
anyway in an app that declares no `INTERNET` permission. Nine browser assertions
check it, starting with "issues zero requests".

> The page ships **without a QR image**. `donate-qr.png` was not supplied and one
> was deliberately not generated: an invented payment QR could route a real
> payment to the wrong place. Drop your own into
> `android/app/src/main/assets/support/` and rebuild. The account details you
> gave are on the page and copyable as they are.

None of Material Capsule's code, assets or resources were copied. The features
were reimplemented from scratch — which is also the only way they fit a build
with no libraries in it at all.

Verified: `clean assembleRelease assembleDebug lintDebug` → **zero lint errors**;
both APKs signature-verified across API 24–34; still exactly six permissions, no
`QUERY_ALL_PACKAGES`, no `INTERNET`; 31 demo assertions and 9 support-page
assertions pass. Release 172,023 B, debug 238,413 B. Still not launched on a
device here — this environment has no KVM.

## v2.5.0 — one axis, one copy, fewer bitmaps

**The expanded view is centred as a whole.** The art and the text are measured
as one block and that block is centred on the island's axis, instead of the art
being pinned to the left padding with the text trailing off it. With short
content the old layout put the header off to one side while the action row
underneath was centred, and the two rows disagreed. Content wider than the
island simply fills the padded span, so nothing is squeezed to achieve it.

On the reported off-centre buttons: the action row was already centred in code
(`box.centerX() - totalW / 2`), and three details of the screenshot say the panel
in it was the system's own media notification rather than the island — it showed
two transport buttons where this app always draws three for media, it had a seek
thumb on the progress line that this app does not draw, and its controls were
left-aligned in a way nothing here produces. Which is exactly the duplicate the
next item removes.

**Show only on the island.** A new switch, off by default: once the island has
shown a notification, it is cleared from the shade so one event does not appear
in two places. Two honest limits, both stated in the app and the README — the
brief heads-up banner still appears, because by the time any listener is told
about a notification the system has already posted it and there is no API to
suppress that; and clearing is the same dismissal as swiping it away, so the
notification is gone rather than hidden. Ongoing notifications are never touched.

**The picker loads icons lazily.** It was decoding every installed app's icon up
front — around 11 MB of bitmaps for a list showing a dozen rows, and the largest
allocation this app would ever make. Now only rows within a screen of the
viewport are decoded, each rasterised straight into a bitmap the size it is drawn
at, with rows that scroll well clear releasing theirs. Peak is roughly a
megabyte, and everything is handed back in `onDestroy` rather than waiting for
the activity to be collected.

Verified: 31 browser assertions still pass; both APKs signature-verified across
API 24–34; the permission list is still exactly six with no `QUERY_ALL_PACKAGES`;
Lint zero errors. Still not launched on a device here — no KVM in this
environment.

## v2.4.0 — pick what shows, and see what it costs

**The memory floor is 45 MB.** The dial used to bottom out at 762 MB, which made
no sense once the ballast was gone: the app's real working set is 30–45 MB and it
never grows toward the ceiling. 45 MB is the point below which a limit stops
limiting and starts handicapping. The top is unchanged — the RAM Android reports
for the device, less the 1.5 GB system reserve.

**A Capacity button, bottom right of the app.** Collapsed it is a small pill;
tapped it expands into what the current ceiling actually buys — apps on the list,
notifications held with their artwork, phone functions, and the headroom above
the island's own footprint. Every figure derives from a real cost (a decoded
avatar is 192×192 at four bytes a pixel; an app on the list is a package name)
rather than an invented one. Even at the 45 MB floor that is over a hundred
notifications. The panel also states plainly that the island shows at most two
activities and one alert at once whatever the ceiling says — a design limit, not
a memory one.

**The two lists are additive now, with a + button each.** The apps picker lists
**every app on the device with a launcher icon**, searchable, and adding one lets
its notifications reach the island. The functions picker does the same for the
kinds of content. An empty list means no restriction, and the card says so —
two empty allow-lists on a fresh install would be an island that never appears,
which anyone would read as broken.

This still does **not** use `QUERY_ALL_PACKAGES`. It uses a `<queries>`
declaration for the launcher intent, which grants sight of exactly the apps a
person would recognise and nothing else — no services, no providers, no headless
packages. Verified in the built APK: the `<queries>` element is present and the
permission list is the same six as before.

**Expanded views no longer get clipped.** `.island` is `overflow:hidden`, and the
expanded height was measured with `getBoundingClientRect()` — which returns the
*transformed* box. Freshly mounted content carries `.di-enter`, which animates
from `scale(.97)`, so the island was measured up to 3% short of its own content
and then cut the bottom off it. Measuring `offsetHeight` instead fixes every
case: all 23 presentations now fit with nothing cut off, at both a desktop and a
phone-width viewport, and that is a permanent check.

**A drag inside the phone belongs to the phone.** `touch-action: none` on the
screen stops the page scrolling out from under a gesture, while the home screen
keeps `pan-y` so its own app grid still scrolls. Swipe up from the bottom strip,
or inward from either side edge, to close an open app — inward being the
operative word: from the left edge the finger travels right, from the right edge
it travels left, and an outward flick is ignored.

Tapping the body of an open app no longer closes it. An app you cannot touch
without dismissing is not an app, and with the gestures in place there is no
reason to overload a plain tap.

**A signature at the bottom of both the app and the demo.**

Verified: 31 browser assertions now, including the expanded-fit sweep over all 23
presentations and eight gesture checks; both APKs signature-verified across
API 24–34; Lint zero errors; `<queries>` and `PickerActivity` confirmed present in
the built manifest; the capacity arithmetic checked against the compiled
constants. Still not launched on a device here — no KVM in this environment.

## v2.3.0 — you choose what it shows

**The overlay really does come down now.** v2.2.0 removed the window when the
store was empty, and on a real device the store was never empty — so the island
stayed up regardless. The cause was one line: any notification carrying
`FLAG_ONGOING_EVENT` became a persistent activity, and an activity holds the
island until its notification is removed. But most ongoing notifications are not
events at all. They are status notices that never go away: the keyboard, a sync
adapter, USB mode, storage, a VPN. A phone or tablet always has several.

Only the five categories the island genuinely has something to say about are
allowed to persist — `call`, `navigation`, `alarm`, `stopwatch`, `progress`.
Everything else is a passing alert that expires on its own. The catch-up pass
that runs when notification access is granted now replays only those live
activities too, instead of firing a burst of stale alerts.

**A new control over everything the island shows.** Two levels, both in the app:

- **What the island shows** — a switch per kind: messages, music and media,
  calls, timers and alarms, navigation, downloads, other notifications, charging,
  battery low, ringer changes, headphones. All on by default.
- **Apps** — every app the island has heard from, each with its own switch. The
  list is learned from arriving notifications rather than enumerated, so it still
  needs no `QUERY_ALL_PACKAGES`, and an app that has never sent anything is never
  offered as a decision. App names come from the platform where package
  visibility allows and fall back to the package name where it does not, which is
  a worse label but an honest one.

Switching a source or an app off clears whatever it had on the island rather than
leaving it there until something else displaces it.

Also fixed: a race in the close animation. A notification arriving while the
island was on its way out would be drawn by a view whose fade-out was still
running, and the two fought to alpha 0 — the island would appear and then
silently vanish. Every part of the close is cancellable now, and the animator's
completion callback no longer fires on cancel, so an abandoned close can never
tear down a window that has just been given something to show.

Verified: the classification table exercised directly against the compiled class
for all twelve notification categories — the five activity ones persist, `status`,
`service` and `sys` do not, and anything carrying MessagingStyle routes to
MESSAGES whatever its category; 23 browser assertions over the demo; both APKs
signature-verified across API 24–34; Lint zero errors. Still not launched on a
device here — no KVM in this environment.

## v2.2.0 — efficient, and out of the way

Everything here came from running v2.1.0 on a real phone — a 3.5 GB device,
which is exactly the case the app should be good at and was not.

**The ballast is gone, and with it the whole idea that the dial sets a target.**
The gauge measured 568 MB in use against a 1.6 GB "budget" on a 3.5 GB phone.
That was working as designed, and the design was wrong: the app was allocating
real, resident, deliberately wasted memory so the number on the dial would be a
measurement rather than a decoration. It was honest about being waste and it was
still waste — it made this process the fattest thing on the device, the first one
the low-memory killer reaches for, and it evicted the user's actual apps.

The dial is now a **ceiling**, which is what everyone assumed it always was. The
app allocates nothing to reach it and sits at its genuine working set of about
30–45 MB. Two mechanisms run together: automatic management (on by default)
watches the footprint, releases cached artwork for anything not on the pill, and
acts on *every* system memory warning rather than only the critical one; and the
manual ceiling, which triggers the same trim at 80% and is a limit rather than an
operating point.

**No overlay when there is nothing to show.** The island collapses back into the
camera hole on the same curve it grew out of, and then the window is removed
entirely rather than left resting on top of the launcher. A new **Keep the island
on screen** switch in the app restores the old behaviour for anyone who wants it;
it is off by default. Toggling it applies immediately to a running theme.

**"App is running" notices are filtered out.** Any notification carrying
`FLAG_FOREGROUND_SERVICE` is ignored — file sync, a VPN, a launcher, and this
app's own ongoing notice, which is what used to put "Dynamic Camera Punch" on the
island and leave it there.

**The memory dial moved into the app.** It was a pull tab welded to the right
edge of the screen as a second permanent overlay window. Nobody asked for a
handle on the side of their display. It is now a speedometer on the control
panel, drawing the ceiling and the measured footprint on the same scale so the
two can be compared, and the second overlay window is gone — there is exactly one
now, and only while it has something to say.

**The demo's phones are phones again.** Every responsive rule shrank the frame's
width without its height, so "Both" produced a narrow, tall device whose home
screen clipped its own fourth column of icons and half the dock. The frame now
keeps its 390×820 design size at every viewport and is scaled as a unit, verified
across six widths from 1400 px down to 360 px: constant aspect ratio, nothing
clipped, no horizontal page scroll.

Also fixed: a `Trimmable` registered with the process-wide memory guard and never
unregistered, which leaked the service and its view across a theme restart; and
the gauge's touch mapping, whose dead-zone split sat 50° off the bisector so a
touch below and to the left of the hub snapped to maximum instead of minimum.

Verified: 23 browser assertions, now scale-relative rather than pixel-absolute;
gauge angle round-trip exact across all 220° with the dead zone splitting at 90°;
both APKs signature-verified across API 24–34; Android Lint zero errors; exactly
one `addView` call in the overlay service. Still not launched on a device here —
this environment has no KVM.

## v2.1.0 — it says what the message says

**Messages now show the message.** The pill used to read a chat notification's
`EXTRA_TEXT`, which for every mainstream messaging app is a summary, a sender
name, or nothing at all — so a message arrived and the island had nothing to
say. It now reads `EXTRA_MESSAGES`, the structured record of the conversation
itself, and shows the newest line:

```
Family GC
Mum · Did you get the toolbox?
```

A group chat puts the conversation on the headline and `sender · message`
underneath; a direct message puts the sender on top and what they said below.
Either way the compact pill carries **the message**, not the name — the name is
already the avatar next to it, pulled from the notification's large icon.
`BigTextStyle` and `InboxStyle` are read properly too, so a long mail or a
stacked notification gives up its real content instead of a teaser.

Long messages are cut at a sentence boundary rather than a character count, but
only once there is a sentence's worth of text in front of the break — cutting at
the first one unconditionally turned *"Hey! Are you home yet?"* into *"Hey!"*.

**Tapping the island opens the app again.** A notification's actions were being
wired to a plain tap, so tapping a message fired whatever the app listed first —
for most chat apps, "Mark as read". Tap now sends the notification's own content
intent, which opens the conversation, chat head and all. The actions are still
there, one tap deeper, in the expanded view.

**Idle is a circle.** With nothing to show, the island collapses onto the camera
hole and reads as part of the hardware, in both variants and in both the app and
the demo. Variant A used to rest as an 86 px bar across the top.

**The RAM dial is a speedometer.** Graduated ticks, a labelled scale and a needle
on the half-disc. The range is now **762 MB minimum** — the figure the theme
wants to run smoothly — up to device RAM minus the 1.5 GB Android reserve: on an
8 GB phone, 762 MB to 6.5 GB. A device that cannot spare 762 MB after the reserve
gets its real ceiling reported rather than a nominal minimum it would be killed
for honouring.

**The demonstration's home screen works.** Tap any app icon and it opens, growing
out of the icon into a full app on the island's own morph curve, and shrinking
back into it when you tap the home bar. The status bar, home bar and island all
stay above it, as they do on a real phone.

Verified: 19 browser assertions over the demo (launch, close, re-open, z-order,
the idle circle in both variants, the lens still 0.00 px off centre, no console
errors), the message-trimming logic exercised directly against the compiled
class, both APKs signature-verified across API 24–34, Android Lint zero errors.
Still not launched on a physical device.

## v2.0.1 — installable again

**Fixes an "App not installed" failure that made the two v2.0.0 APKs mutually
exclusive.** Both declared the same custom permission name,
`com.dcp.punch.permission.INTERNAL`, because it was hard-coded rather than
derived from the application id. A custom permission name is global to the
device: when two packages signed with different keys declare the same one, the
second to be installed is rejected with `INSTALL_FAILED_DUPLICATE_PERMISSION`,
which Android surfaces as a bare "App not installed" with no explanation. Since
debug is signed with the Android debug key and release with the project key,
installing either one blocked the other — exactly the side-by-side install the
README advertised.

- The permission, and the two private broadcast actions beside it, are now scoped
  to `${applicationId}`. The debug build owns
  `com.dcp.punch.debug.permission.INTERNAL` and its own IPC surface.
- The debug build is labelled **DCP (debug)** on the launcher, so two installed
  copies are tellable apart.
- Both APKs verify under `apksigner` for the full API 24–34 range, and the
  README now publishes their SHA-256 sums so a truncated or HTML-error-page
  download can be spotted before the installer refuses it.

No change to the island, the overlay, the RAM dial or the demonstration.

## v2.0.0 — the theme

The island moved out of the sandbox and onto the phone.

**It is now a system-wide overlay.** A foreground service draws the island above
every other app — home screen, launcher, games, anything — once you grant
"Display over other apps". v1 could only show the island inside its own window.

- **Native rendering.** The overlay is Canvas drawing, not a WebView. An overlay
  window has to be exactly the size of its touchable content, and it is
  re-measured every animation frame; a WebView doing that would be unusable and
  cost ~80 MB. Same geometry model and same morph curve as before.
- **Real content.** Media sessions give the track actually playing with working
  transport controls; the notification listener supplies calls, timers,
  navigation and each notification's own actions; charging, battery, ringer and
  headphones come from broadcasts that need no permission at all.
- **Two cutout shapes** kept from v1, now applied to the real screen. The camera
  never moves in any state.
- **RAM dial** on the right edge. Default 728 MB, capped at device RAM minus a
  1.5 GB reserve for Android, backed by a real off-heap ballast that is dropped
  instantly under memory pressure. See the README before turning it up.
- **The live demonstration** is still here, in its own `:demo` process. It will
  not open while the theme is running, and starting the theme closes it.
- **Six permissions**, each justified in the manifest. `QUERY_ALL_PACKAGES`,
  `READ_PHONE_STATE` and `INTERNET` are deliberately not requested.

Android Lint is clean at zero errors. Not yet launched on a physical device.

## v1.0.0 — the demonstration

Dynamic Island clone for the web with two Android punch-hole variants: 14 system
alerts, 9 live activities, two concurrent activities with a gooey minimal
indicator, full gesture set, zero dependencies. Shipped as a browser page and as
an Android app that hosted it in a WebView.

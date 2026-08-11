# Changelog

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

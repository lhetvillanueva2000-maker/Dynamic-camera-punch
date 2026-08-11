# Changelog

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

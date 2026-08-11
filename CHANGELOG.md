# Changelog

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

/* ============================================================================
   icons.js — inline SVG library.

   Every glyph is a 24×24 path set that inherits `currentColor`, so an icon
   picks up whatever colour its container sets. Keeping them inline (rather
   than as a font or sprite sheet) means the whole demo stays a single
   dependency-free bundle that also runs from file:// inside a WebView.
   ========================================================================== */
window.DI = window.DI || {};

DI.Icons = (function () {
  'use strict';

  /** Stroke-based glyph. */
  function s(body, w) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' +
      (w || 1.8) + '" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      body + '</svg>';
  }
  /** Solid glyph. */
  function f(body) {
    return '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">' + body + '</svg>';
  }

  var I = {
    /* ── System alerts ─────────────────────────────────────────────── */
    faceId: s('<path d="M4 9V7a3 3 0 0 1 3-3h2"/><path d="M15 4h2a3 3 0 0 1 3 3v2"/>' +
              '<path d="M20 15v2a3 3 0 0 1-3 3h-2"/><path d="M9 20H7a3 3 0 0 1-3-3v-2"/>' +
              '<path d="M9 10v1.6"/><path d="M15 10v1.6"/><path d="M12 9.6v4h-1.1"/>' +
              '<path d="M9.4 15.6c1.5 1.1 3.7 1.1 5.2 0"/>'),
    faceIdOk: s('<circle cx="12" cy="12" r="9"/><path d="m7.7 12.4 3 3 5.7-5.9"/>', 2),
    wallet: s('<rect x="2.6" y="5.4" width="18.8" height="13.2" rx="3.2"/>' +
              '<path d="M2.6 10.2h18.8"/><path d="M6.2 14.6h3.4"/>'),
    camera: s('<path d="M3 8.6a2 2 0 0 1 2-2h1.7l1.2-1.9h8.2l1.2 1.9H19a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>' +
              '<circle cx="12" cy="12.6" r="3.4"/>'),
    mic: s('<rect x="9" y="2.6" width="6" height="11" rx="3"/>' +
           '<path d="M5.6 11.4a6.4 6.4 0 0 0 12.8 0"/><path d="M12 17.9v3.5"/>'),
    airdrop: s('<circle cx="12" cy="12" r="9"/><path d="M9.3 14.4a4 4 0 0 1 5.4 0"/>' +
               '<path d="M7.1 11.7a7.4 7.4 0 0 1 9.8 0"/><circle cx="12" cy="16.8" r="1" fill="currentColor" stroke="none"/>'),
    airpods: s('<rect x="4.6" y="3.4" width="5" height="12" rx="2.5"/><path d="M7.1 15.4v3a2 2 0 0 0 2 2"/>' +
               '<rect x="14.4" y="3.4" width="5" height="12" rx="2.5"/><path d="M16.9 15.4v3a2 2 0 0 1-2 2"/>'),
    bolt: f('<path d="M13.4 2 4.6 13.6h5.5L9 22l9-11.7h-5.4L13.4 2Z"/>'),
    bellSlash: s('<path d="M18 9v4.6l1.6 3.4H7.4"/><path d="M6 13.6V9a6 6 0 0 1 8.4-5.5"/>' +
                 '<path d="M10 20.6a2.3 2.3 0 0 0 4 0"/><path d="M3.4 3.4 20.6 20.6"/>'),
    moon: s('<path d="M20.6 14.4A8.7 8.7 0 0 1 9.6 3.4 8.7 8.7 0 1 0 20.6 14.4Z"/>'),
    nfc: s('<path d="M6.4 4.4a10.5 10.5 0 0 1 0 15.2"/><path d="M11 7.2a6.2 6.2 0 0 1 0 9.6"/>' +
           '<path d="M15.6 9.8a2.6 2.6 0 0 1 0 4.4"/>'),
    airplay: s('<path d="M6.4 16.6H4.6a2 2 0 0 1-2-2V6.4a2 2 0 0 1 2-2h14.8a2 2 0 0 1 2 2v8.2a2 2 0 0 1-2 2h-1.8"/>' +
               '<path d="M12 13.4 17.2 21H6.8L12 13.4Z"/>'),
    sim: s('<path d="M6 3.4h7.6L19 8.4v12.2a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4.4a1 1 0 0 1 1-1Z"/>' +
           '<rect x="8.4" y="11" width="7.2" height="7" rx="1.6"/>'),
    findmy: s('<circle cx="12" cy="12" r="8.6"/><path d="M15.8 8.2 13.9 14 8.2 15.8 10.1 10l5.7-1.8Z"/>'),
    flashlight: s('<path d="M9 2.6h6l-.7 4.3a2 2 0 0 1-.5 1l-.8.8v10.8a1.5 1.5 0 0 1-3 0V8.7l-.8-.8a2 2 0 0 1-.5-1L9 2.6Z"/>' +
                  '<path d="M9.3 6.4h5.4"/>'),

    /* ── Live activities ───────────────────────────────────────────── */
    music: s('<path d="M9 17.6V5.4l10-2v12"/><circle cx="6.4" cy="17.6" r="2.6"/><circle cx="16.4" cy="15.4" r="2.6"/>'),
    phone: f('<path d="M7 2.6a2 2 0 0 0-2.6.7L3.3 5A2.8 2.8 0 0 0 3 7.1c1 6.6 7.3 12.9 13.9 13.9a2.8 2.8 0 0 0 2.1-.3l1.7-1.1a2 2 0 0 0 .7-2.6l-1.5-2.7a2 2 0 0 0-2.3-1l-2.2.6a13.9 13.9 0 0 1-4.3-4.3l.6-2.2a2 2 0 0 0-1-2.3L7 2.6Z"/>'),
    phoneDown: f('<g transform="rotate(134 12 12)"><path d="M7 2.6a2 2 0 0 0-2.6.7L3.3 5A2.8 2.8 0 0 0 3 7.1c1 6.6 7.3 12.9 13.9 13.9a2.8 2.8 0 0 0 2.1-.3l1.7-1.1a2 2 0 0 0 .7-2.6l-1.5-2.7a2 2 0 0 0-2.3-1l-2.2.6a13.9 13.9 0 0 1-4.3-4.3l.6-2.2a2 2 0 0 0-1-2.3L7 2.6Z"/></g>'),
    turnRight: s('<path d="M10.6 21v-8.4a4.4 4.4 0 0 1 4.4-4.4h3.2"/><path d="m14.8 4.4 4 3.8-4 3.8"/>', 2.2),
    turnLeft: s('<path d="M13.4 21v-8.4A4.4 4.4 0 0 0 9 8.2H5.8"/><path d="m9.2 4.4-4 3.8 4 3.8"/>', 2.2),
    timer: s('<circle cx="12" cy="13.2" r="8.2"/><path d="M12 8.8v4.6l2.8 1.7"/><path d="M9.4 2.6h5.2"/>'),
    trophy: s('<path d="M7 3.6h10v5.2a5 5 0 0 1-10 0V3.6Z"/><path d="M7 5.4H4.4v1.2a3.6 3.6 0 0 0 3 3.5"/>' +
              '<path d="M17 5.4h2.6v1.2a3.6 3.6 0 0 1-3 3.5"/><path d="M12 13.9v3.7"/><path d="M8.4 20.4h7.2"/>'),
    screenRecord: s('<rect x="2.6" y="4" width="18.8" height="13.2" rx="2.8"/><path d="M8.4 20.6h7.2"/>' +
                    '<circle cx="12" cy="10.6" r="3" fill="currentColor" stroke="none"/>'),
    waveform: s('<path d="M4 10.2v3.6"/><path d="M8 7v10"/><path d="M12 4.4v15.2"/><path d="M16 8v8"/><path d="M20 10.4v3.2"/>', 2),
    hotspot: s('<circle cx="12" cy="12" r="2.4" fill="currentColor" stroke="none"/>' +
               '<path d="M8.4 15.6a5.1 5.1 0 0 1 0-7.2"/><path d="M15.6 8.4a5.1 5.1 0 0 1 0 7.2"/>' +
               '<path d="M5.6 18.4a9.1 9.1 0 0 1 0-12.8"/><path d="M18.4 5.6a9.1 9.1 0 0 1 0 12.8"/>'),
    shareplay: s('<rect x="2.6" y="4.4" width="18.8" height="13" rx="3"/><circle cx="12" cy="9.8" r="2.2"/>' +
                 '<path d="M8.2 15.2a4.3 4.3 0 0 1 7.6 0"/><path d="M8.4 20.6h7.2"/>'),

    /* ── Transport / controls ──────────────────────────────────────── */
    play: f('<path d="M8 5.1 19.2 12 8 18.9V5.1Z"/>'),
    pause: f('<rect x="7.4" y="4.8" width="3.6" height="14.4" rx="1.3"/><rect x="13" y="4.8" width="3.6" height="14.4" rx="1.3"/>'),
    next: f('<path d="M5.6 5.3 15 12l-9.4 6.7V5.3Z"/><rect x="16.4" y="5" width="2.8" height="14" rx="1.2"/>'),
    prev: f('<path d="M18.4 5.3 9 12l9.4 6.7V5.3Z"/><rect x="4.8" y="5" width="2.8" height="14" rx="1.2"/>'),
    stop: f('<rect x="6.6" y="6.6" width="10.8" height="10.8" rx="2.4"/>'),
    record: f('<circle cx="12" cy="12" r="7"/>'),
    plus: s('<path d="M12 5.5v13M5.5 12h13"/>', 2),
    check: s('<path d="m5.2 12.6 4.6 4.6L18.8 7.4"/>', 2.4),
    xmark: s('<path d="M6 6l12 12M18 6 6 18"/>', 2.2),
    chevron: s('<path d="m9.2 5 7 7-7 7"/>', 2),
    volume: s('<path d="M4 9.4h3.4L12 5.2v13.6l-4.6-4.2H4Z"/><path d="M15.6 9.4a3.6 3.6 0 0 1 0 5.2"/><path d="M18.2 6.8a7.2 7.2 0 0 1 0 10.4"/>'),
    lock: s('<rect x="4.8" y="10" width="14.4" height="11.2" rx="3.4"/><path d="M8.4 10V7a3.6 3.6 0 0 1 7.2 0v3"/>'),
    lockOpen: s('<rect x="4.8" y="10" width="14.4" height="11.2" rx="3.4"/><path d="M8.4 10V7a3.6 3.6 0 0 1 7.05-.9"/>'),
    location: s('<path d="M12 21.2s7-6.4 7-11.2a7 7 0 1 0-14 0c0 4.8 7 11.2 7 11.2Z"/><circle cx="12" cy="10" r="2.6"/>'),
    person: s('<circle cx="12" cy="8" r="3.6"/><path d="M4.6 20.4a7.4 7.4 0 0 1 14.8 0"/>'),
    users: s('<circle cx="9.4" cy="8.4" r="3.2"/><path d="M3.4 20a6 6 0 0 1 12 0"/><path d="M16 5.6a3.2 3.2 0 0 1 0 5.6"/><path d="M17.6 14.6a6 6 0 0 1 3 5.4"/>'),
    sun: s('<circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.6M12 19.4V22M2 12h2.6M19.4 12H22M4.9 4.9l1.9 1.9M17.2 17.2l1.9 1.9M19.1 4.9l-1.9 1.9M6.8 17.2l-1.9 1.9"/>'),

    /* ── Status bar ────────────────────────────────────────────────── */
    cellular: f('<rect x="2.6" y="14.6" width="2.6" height="5.4" rx="1"/><rect x="7.4" y="11.4" width="2.6" height="8.6" rx="1"/>' +
                '<rect x="12.2" y="8" width="2.6" height="12" rx="1"/><rect x="17" y="4.6" width="2.6" height="15.4" rx="1"/>'),
    wifi: s('<path d="M2.6 9a14 14 0 0 1 18.8 0"/><path d="M6 12.6a9 9 0 0 1 12 0"/>' +
            '<path d="M9.4 16.2a4.2 4.2 0 0 1 5.2 0"/><circle cx="12" cy="19.4" r="1.2" fill="currentColor" stroke="none"/>', 2),

    /* ── Home-screen apps ──────────────────────────────────────────── */
    messages: f('<path d="M12 3.4c-5.2 0-9.4 3.5-9.4 7.9 0 2.5 1.4 4.7 3.5 6.1-.2 1.3-.9 2.6-1.9 3.5 1.9-.2 3.7-1 5.1-2.1.9.2 1.8.3 2.7.3 5.2 0 9.4-3.5 9.4-7.8S17.2 3.4 12 3.4Z"/>'),
    safari: s('<circle cx="12" cy="12" r="8.8"/><path d="m15.8 8.2-2 5.6-5.6 2 2-5.6 5.6-2Z"/>'),
    photos: s('<rect x="3" y="4.6" width="18" height="14.8" rx="3.2"/><circle cx="8.6" cy="9.6" r="1.9"/>' +
              '<path d="m3.6 17.4 4.8-5 3.4 3.6 3-3.2 5.6 6"/>'),
    settings: s('<circle cx="12" cy="12" r="3.3"/><path d="M12 2.4v2.8M12 18.8v2.8M2.4 12h2.8M18.8 12h2.8M5.1 5.1l2 2M16.9 16.9l2 2M18.9 5.1l-2 2M7.1 16.9l-2 2"/>'),
    calendar: s('<rect x="3.4" y="5" width="17.2" height="15.6" rx="3.2"/><path d="M3.4 9.8h17.2M8 2.8v4.2M16 2.8v4.2"/>'),
    mail: s('<rect x="2.6" y="5" width="18.8" height="14" rx="3.2"/><path d="m3.6 7.4 8.4 6 8.4-6"/>'),
    notes: s('<rect x="4.6" y="3" width="14.8" height="18" rx="3.2"/><path d="M8.4 8.2h7.2M8.4 12h7.2M8.4 15.8h4.6"/>'),
    appstore: s('<circle cx="12" cy="12" r="8.8"/><path d="m7.6 16.4 4.4-8.4 4.4 8.4"/><path d="M9.6 13.6h4.8"/>'),
    clock: s('<circle cx="12" cy="12" r="8.8"/><path d="M12 6.6V12l3.6 2.2"/>')
  };

  /* Aliases so registry.js can read semantically. */
  I.applepay = I.wallet;
  I.focus = I.moon;
  I.silent = I.bellSlash;
  I.charging = I.bolt;
  I.maps = I.turnRight;
  I.voice = I.waveform;
  I.sports = I.trophy;

  return I;
})();

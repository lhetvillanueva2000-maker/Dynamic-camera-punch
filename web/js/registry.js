/* ============================================================================
   registry.js — every presentation the island can show.

   A presentation is a plain object. island.js knows nothing about music or
   Face ID; it only knows this contract:

     id        unique key
     kind      'alert'    → brief, self-dismissing, interrupts the stack
               'activity' → persistent, interactive, max two at a time
     accent    colour used by the icon chip and the minimal indicator
     duration  alerts only: ms before the island hands the screen back
     data()    factory for the presentation's *mutable* state
     compact(d)→ { lead, trail }  markup for the two sides of the pill
     minimal(d)→ markup for the detached satellite (2-activity mode)
     expanded(d)→ markup for the detail view
     sync(el,d) patch live values in place — never re-render on a tick, or
                the CSS animations restart and the transitions stutter
     tick(d,c)  advance the model ~5×/s; call c.rerender() only on a real
                phase change (e.g. Face ID scanning → unlocked)
     tap(d,c)   primary action
     action(d,c,name) expanded-view button, from [data-act]
     status(d)  one-line summary for the demo readout
   ========================================================================== */
window.DI = window.DI || {};

DI.Registry = (function () {
  'use strict';

  var Icons = DI.Icons;

  /* ── markup helpers ───────────────────────────────────────────────── */

  function ico(name, color, cls) {
    return '<span class="di-ico ' + (cls || '') + '" style="--c:' + color + '">' + Icons[name] + '</span>';
  }
  function glyph(name, color, cls) {
    return '<span class="di-ico di-ico--plain ' + (cls || '') + '" style="--c:' + color + '">' + Icons[name] + '</span>';
  }
  function label(text, cls, field) {
    return '<span class="di-text ' + (cls || '') + '"' + (field ? ' data-f="' + field + '"' : '') + '>' + esc(text) + '</span>';
  }
  function eq(color, paused) {
    return '<span class="di-eq' + (paused ? ' is-paused' : '') + '" data-f="eq" style="--c:' + color +
      '"><i></i><i></i><i></i><i></i></span>';
  }
  function wave(color, n) {
    var out = '<span class="di-wave" style="--c:' + color + '">';
    for (var i = 0; i < (n || 7); i++) {
      // Prime-ish offsets keep the bars from visibly looping in lockstep.
      out += '<i style="animation-delay:' + (-(i * 137) % 1100) + 'ms;animation-duration:' + (860 + i * 61) + 'ms"></i>';
    }
    return out + '</span>';
  }
  function ripple(color) {
    return '<span class="di-ripple" style="--c:' + color + '"><i></i><i></i><i></i></span>';
  }
  function ring(pct, color, inner, cls) {
    return '<span class="di-ring ' + (cls || '') + '" data-f="ring" style="--p:' + pct + ';--c:' + color + '">' +
      (inner != null ? '<span data-f="ringtext">' + inner + '</span>' : '') + '</span>';
  }
  function bar(pct, color, cls) {
    return '<span class="di-bar ' + (cls || '') + '" style="--c:' + color + '"><i data-f="bar" style="--p:' + pct + '"></i></span>';
  }
  function battery(pct, color) {
    return '<span class="di-batt"><i data-f="batt" style="--p:' + pct + ';--c:' + color + '"></i></span>';
  }
  function art(color, iconName, cls) {
    return '<span class="di-art ' + (cls || '') + '" style="--c:' + color + '">' + Icons[iconName] + '</span>';
  }
  function btn(act, iconName, cls, aria) {
    return '<button class="di-btn ' + (cls || '') + '" data-act="' + act + '" type="button" aria-label="' +
      esc(aria || act) + '">' + Icons[iconName] + '</button>';
  }
  function checkMark(color) {
    return '<span class="di-ico di-ico--plain" style="--c:' + color + '">' +
      '<svg class="di-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" ' +
      'stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9.5"/>' +
      '<path d="m7.7 12.4 3 3 5.7-5.9"/></svg></span>';
  }

  /** Standard expanded header: icon, titles, a gap for the lens, a trailing slot. */
  function head(leadHtml, title, sub, trailHtml) {
    return '<div class="di-exp__head">' + leadHtml +
      '<div class="di-exp__titles">' +
        '<div class="di-exp__title" data-f="title">' + esc(title) + '</div>' +
        '<div class="di-exp__sub" data-f="sub">' + esc(sub) + '</div>' +
      '</div><span class="di-exp__headgap"></span>' + (trailHtml || '') + '</div>';
  }

  function esc(v) {
    return String(v == null ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  /* ── value helpers ────────────────────────────────────────────────── */

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function mmss(s) {
    s = Math.max(0, Math.round(s));
    return Math.floor(s / 60) + ':' + pad(s % 60);
  }
  function hms(s) {
    s = Math.max(0, Math.round(s));
    var h = Math.floor(s / 3600);
    return (h ? h + ':' + pad(Math.floor(s / 60) % 60) : Math.floor(s / 60)) + ':' + pad(s % 60);
  }
  function dist(m) {
    return m >= 1000 ? (m / 1000).toFixed(1) + ' km' : Math.max(0, Math.round(m / 10) * 10) + ' m';
  }
  /** Patch one live field without touching the rest of the tree. */
  function setF(el, field, html) {
    var n = el && el.querySelector('[data-f="' + field + '"]');
    if (n && n.innerHTML !== html) n.innerHTML = html;
  }
  function setVar(el, field, name, value) {
    var n = el && el.querySelector('[data-f="' + field + '"]');
    if (n) n.style.setProperty(name, value);
  }

  var C = {
    green: '#30d158', red: '#ff453a', blue: '#0a84ff', orange: '#ff9f0a',
    yellow: '#ffd60a', purple: '#bf5af2', pink: '#ff375f', teal: '#40c8e0',
    white: '#ffffff', gray: '#8e8e93'
  };

  /* ══════════════════════════════════════════════════════════════════
     SYSTEM ALERTS
     ═════════════════════════════════════════════════════════════════ */

  var alerts = [

    /* ── Face ID ─────────────────────────────────────────────────── */
    {
      id: 'faceid', label: 'Face ID', icon: 'faceId', accent: C.white,
      kind: 'alert', duration: 3000,
      data: function () { return { phase: 'scan' }; },
      tick: function (d, c) {
        if (d.phase === 'scan' && c.elapsed > 1500) { d.phase = 'ok'; c.rerender(); }
      },
      compact: function (d) {
        return d.phase === 'scan'
          ? { lead: '<span class="di-scan" style="--c:' + C.white + '">' + Icons.faceId + '</span>',
              trail: label('Face ID', 'di-text--dim') }
          : { lead: checkMark(C.white), trail: label('Unlocked') };
      },
      minimal: function (d) { return glyph(d.phase === 'scan' ? 'faceId' : 'lockOpen', C.white); },
      expanded: function (d) {
        var scanning = d.phase === 'scan';
        return '<div class="di-expanded di-enter">' +
          head(scanning
              ? '<span class="di-ico di-ico--lg di-ico--plain" style="--c:#fff">' + Icons.faceId + '</span>'
              : '<span class="di-ico di-ico--lg di-ico--plain" style="--c:#fff">' + Icons.lockOpen + '</span>',
            scanning ? 'Face ID' : 'iPhone Unlocked',
            scanning ? 'Looking for you…' : 'Swipe up to open', '') +
          '</div>';
      },
      status: function (d) { return d.phase === 'scan' ? 'Face ID scanning…' : 'Face ID — unlocked'; }
    },

    /* ── Apple Pay ───────────────────────────────────────────────── */
    {
      id: 'applepay', label: 'Apple Pay', icon: 'wallet', accent: C.white,
      kind: 'alert', duration: 4400,
      data: function () { return { phase: 'hold' }; },
      tick: function (d, c) {
        if (d.phase === 'hold' && c.elapsed > 1400) { d.phase = 'auth'; c.rerender(); }
        else if (d.phase === 'auth' && c.elapsed > 2900) { d.phase = 'done'; c.rerender(); }
      },
      compact: function (d) {
        if (d.phase === 'hold') return { lead: ripple(C.white) + glyph('wallet', C.white), trail: label('Hold Near Reader', 'di-text--dim') };
        if (d.phase === 'auth') return { lead: '<span class="di-scan" style="--c:#fff">' + Icons.faceId + '</span>', trail: label('Confirm with Face ID', 'di-text--dim') };
        return { lead: checkMark(C.green), trail: label('Done') };
      },
      minimal: function () { return glyph('wallet', C.white); },
      expanded: function (d) {
        var map = { hold: ['Apple Pay', 'Hold near reader'], auth: ['Confirm Payment', 'Double-click to pay'], done: ['Done', 'Blue Bottle Coffee · $6.40'] };
        var t = map[d.phase];
        return '<div class="di-expanded di-enter">' +
          head('<span class="di-art di-art--lg" style="--c:linear-gradient(150deg,#3a3a3c,#1c1c1e)">' + Icons.wallet + '</span>',
               t[0], t[1],
               d.phase === 'done' ? '<span class="di-text di-num" style="font-size:17px">$6.40</span>' : '') +
          (d.phase === 'done' ? '' : '<div class="di-exp__body">' + bar(d.phase === 'hold' ? 35 : 78, C.white, 'di-bar--slim') + '</div>') +
          '</div>';
      },
      status: function (d) { return 'Apple Pay — ' + d.phase; }
    },

    /* ── Privacy indicators ──────────────────────────────────────── */
    {
      id: 'privacy-camera', label: 'Camera active', icon: 'camera', accent: C.green,
      kind: 'alert', duration: 2800, privacy: 'camera',
      data: function () { return {}; },
      compact: function () { return { lead: '<span class="di-dot" style="--c:' + C.green + '"></span>', trail: glyph('camera', C.green) }; },
      minimal: function () { return '<span class="di-dot" style="--c:' + C.green + '"></span>'; },
      expanded: function () {
        return '<div class="di-expanded di-enter">' +
          head(ico('camera', C.green, 'di-ico--lg'), 'Camera In Use', 'Instagram',
               '<span class="di-dot" style="--c:' + C.green + '"></span>') + '</div>';
      },
      status: function () { return 'Privacy — camera in use'; }
    },
    {
      id: 'privacy-mic', label: 'Microphone active', icon: 'mic', accent: C.orange,
      kind: 'alert', duration: 2800, privacy: 'mic',
      data: function () { return {}; },
      compact: function () { return { lead: '<span class="di-dot" style="--c:' + C.orange + '"></span>', trail: glyph('mic', C.orange) }; },
      minimal: function () { return '<span class="di-dot" style="--c:' + C.orange + '"></span>'; },
      expanded: function () {
        return '<div class="di-expanded di-enter">' +
          head(ico('mic', C.orange, 'di-ico--lg'), 'Microphone In Use', 'Voice Memos',
               '<span class="di-dot" style="--c:' + C.orange + '"></span>') + '</div>';
      },
      status: function () { return 'Privacy — microphone in use'; }
    },

    /* ── AirDrop ─────────────────────────────────────────────────── */
    {
      id: 'airdrop', label: 'AirDrop', icon: 'airdrop', accent: C.blue,
      kind: 'alert', duration: 5600,
      data: function () { return { phase: 'wait', p: 0 }; },
      tick: function (d, c) {
        if (d.phase === 'wait' && c.elapsed > 1200) { d.phase = 'send'; c.rerender(); }
        else if (d.phase === 'send') {
          d.p = Math.min(100, ((c.elapsed - 1200) / 3200) * 100);
          if (d.p >= 100) { d.phase = 'done'; c.rerender(); }
        }
      },
      compact: function (d) {
        if (d.phase === 'wait') return { lead: ripple(C.blue) , trail: label('AirDrop', 'di-text--dim') };
        if (d.phase === 'send') return { lead: ring(d.p, C.blue), trail: label('IMG_4021.HEIC', 'di-text--dim') };
        return { lead: checkMark(C.blue), trail: label('Sent') };
      },
      minimal: function () { return glyph('airdrop', C.blue); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('airdrop', C.blue, 'di-ico--lg'),
               d.phase === 'done' ? 'Sent' : 'AirDrop to Maya',
               d.phase === 'wait' ? 'Waiting…' : 'IMG_4021.HEIC · 4.2 MB',
               d.phase === 'send' ? '<span class="di-text di-num" data-f="pct">' + Math.round(d.p) + '%</span>' : '') +
          (d.phase === 'send' ? '<div class="di-exp__body">' + bar(d.p, C.blue) + '</div>' : '') +
          '</div>';
      },
      sync: function (el, d) {
        setVar(el, 'ring', '--p', Math.round(d.p));
        setVar(el, 'bar', '--p', Math.round(d.p));
        setF(el, 'pct', Math.round(d.p) + '%');
      },
      status: function (d) { return 'AirDrop — ' + (d.phase === 'send' ? Math.round(d.p) + '%' : d.phase); }
    },

    /* ── AirPods ─────────────────────────────────────────────────── */
    {
      id: 'airpods', label: 'AirPods connected', icon: 'airpods', accent: C.white,
      kind: 'alert', duration: 3800,
      data: function () { return { buds: 78, box: 96 }; },
      compact: function (d) {
        return { lead: glyph('airpods', C.white), trail: battery(d.buds, C.green) + label(d.buds + '%', 'di-num') };
      },
      minimal: function () { return glyph('airpods', C.white); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head('<span class="di-art di-art--lg" style="--c:#2c2c2e">' + Icons.airpods + '</span>',
               'AirPods Pro', 'Connected', '') +
          '<div class="di-exp__body"><div class="di-chips">' +
            '<span class="di-chip">' + Icons.airpods + ' Buds ' + d.buds + '%</span>' +
            '<span class="di-chip">' + Icons.bolt + ' Case ' + d.box + '%</span>' +
            '<span class="di-chip">' + Icons.volume + ' Noise Cancellation</span>' +
          '</div></div></div>';
      },
      status: function (d) { return 'AirPods Pro — ' + d.buds + '%'; }
    },

    /* ── Charging ────────────────────────────────────────────────── */
    {
      id: 'charging', label: 'Charging', icon: 'bolt', accent: C.green,
      kind: 'alert', duration: 3200,
      data: function () { return { pct: 62 }; },
      tick: function (d, c) { d.pct = Math.min(100, 62 + c.elapsed / 120); },
      compact: function (d) {
        return { lead: ico('bolt', C.green), trail: battery(d.pct, C.green) + label(Math.round(d.pct) + '%', 'di-num', 'pct') };
      },
      minimal: function () { return glyph('bolt', C.green); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('bolt', C.green, 'di-ico--lg'), 'Charging', '20 W adapter · 34 min to full',
               '<span class="di-text di-num" style="font-size:19px" data-f="big">' + Math.round(d.pct) + '%</span>') +
          '<div class="di-exp__body">' + bar(d.pct, C.green) + '</div></div>';
      },
      sync: function (el, d) {
        var p = Math.round(d.pct);
        setF(el, 'pct', p + '%'); setF(el, 'big', p + '%');
        setVar(el, 'batt', '--p', p); setVar(el, 'bar', '--p', p);
      },
      status: function (d) { return 'Charging — ' + Math.round(d.pct) + '%'; }
    },

    /* ── Ringer / Focus ──────────────────────────────────────────── */
    {
      id: 'silent', label: 'Silent mode', icon: 'bellSlash', accent: C.orange,
      kind: 'alert', duration: 1900,
      data: function () { return {}; },
      compact: function () { return { lead: glyph('bellSlash', C.orange), trail: label('Silent', 'di-text--dim') }; },
      minimal: function () { return glyph('bellSlash', C.orange); },
      expanded: function () {
        return '<div class="di-expanded di-enter">' + head(ico('bellSlash', C.orange, 'di-ico--lg'), 'Silent Mode', 'Ringer off') + '</div>';
      },
      status: function () { return 'Ringer — silent'; }
    },
    {
      id: 'focus', label: 'Focus / DND', icon: 'moon', accent: C.purple,
      kind: 'alert', duration: 2600,
      data: function () { return {}; },
      compact: function () { return { lead: glyph('moon', C.purple), trail: label('Do Not Disturb', 'di-text--dim') }; },
      minimal: function () { return glyph('moon', C.purple); },
      expanded: function () {
        return '<div class="di-expanded di-enter">' +
          head(ico('moon', C.purple, 'di-ico--lg'), 'Do Not Disturb', 'Notifications silenced until 8:00 AM') +
          '<div class="di-exp__body"><div class="di-chips"><span class="di-chip">' + Icons.person + ' Allow from Favourites</span></div></div></div>';
      },
      status: function () { return 'Focus — Do Not Disturb on'; }
    },

    /* ── NFC ─────────────────────────────────────────────────────── */
    {
      id: 'nfc', label: 'NFC scan', icon: 'nfc', accent: C.white,
      kind: 'alert', duration: 3600,
      data: function () { return { phase: 'scan' }; },
      tick: function (d, c) { if (d.phase === 'scan' && c.elapsed > 2000) { d.phase = 'done'; c.rerender(); } },
      compact: function (d) {
        return d.phase === 'scan'
          ? { lead: ripple(C.white) + glyph('nfc', C.white), trail: label('Ready to Scan', 'di-text--dim') }
          : { lead: checkMark(C.green), trail: label('Tag Read') };
      },
      minimal: function () { return glyph('nfc', C.white); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('nfc', '#3a3a3c', 'di-ico--lg'), d.phase === 'scan' ? 'Ready to Scan' : 'NFC Tag Read',
               d.phase === 'scan' ? 'Hold near the tag' : 'Opening Shortcut “Desk Mode”') + '</div>';
      },
      status: function (d) { return 'NFC — ' + (d.phase === 'scan' ? 'ready to scan' : 'tag read'); }
    },

    /* ── AirPlay ─────────────────────────────────────────────────── */
    {
      id: 'airplay', label: 'AirPlay', icon: 'airplay', accent: C.blue,
      kind: 'alert', duration: 3800,
      data: function () { return { phase: 'connecting' }; },
      tick: function (d, c) { if (d.phase === 'connecting' && c.elapsed > 1700) { d.phase = 'on'; c.rerender(); } },
      compact: function (d) {
        return d.phase === 'connecting'
          ? { lead: '<span class="di-ico di-ico--plain di-spin" style="--c:' + C.blue + '">' + Icons.airplay + '</span>',
              trail: label('Connecting…', 'di-text--dim') }
          : { lead: glyph('airplay', C.blue), trail: label('Living Room') };
      },
      minimal: function () { return glyph('airplay', C.blue); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('airplay', C.blue, 'di-ico--lg'), 'Living Room',
               d.phase === 'connecting' ? 'Connecting to Apple TV 4K…' : 'AirPlay Mirroring on') +
          (d.phase === 'on' ? '<div class="di-exp__body"><div class="di-chips">' +
            '<span class="di-chip">' + Icons.volume + ' Volume synced</span>' +
            '<span class="di-chip">' + Icons.wifi + ' 5 GHz</span></div></div>' : '') + '</div>';
      },
      status: function (d) { return 'AirPlay — ' + (d.phase === 'on' ? 'Living Room' : 'connecting'); }
    },

    /* ── SIM / accessory ─────────────────────────────────────────── */
    {
      id: 'sim', label: 'SIM / accessory', icon: 'sim', accent: C.orange,
      kind: 'alert', duration: 3400,
      data: function () { return {}; },
      compact: function () { return { lead: glyph('sim', C.orange), trail: label('eSIM Activated', 'di-text--dim') }; },
      minimal: function () { return glyph('sim', C.orange); },
      expanded: function () {
        return '<div class="di-expanded di-enter">' +
          head(ico('sim', C.orange, 'di-ico--lg'), 'eSIM Activated', 'Carrier · Vodafone UK') +
          '<div class="di-exp__body"><div class="di-chips">' +
          '<span class="di-chip">' + Icons.cellular + ' 5G</span>' +
          '<span class="di-chip">' + Icons.check + ' Data roaming off</span></div></div></div>';
      },
      status: function () { return 'SIM — eSIM activated'; }
    },

    /* ── Find My ─────────────────────────────────────────────────── */
    {
      id: 'findmy', label: 'Find My', icon: 'findmy', accent: C.green,
      kind: 'alert', duration: 4000,
      data: function () { return { m: 12 }; },
      tick: function (d) { d.m = Math.max(1, d.m - 0.06); },
      compact: function (d) {
        return { lead: glyph('findmy', C.green), trail: label(Math.round(d.m) + ' m away', 'di-text--dim', 'away') };
      },
      minimal: function () { return glyph('findmy', C.green); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('location', C.green, 'di-ico--lg'), 'Keys · AirTag', 'Found nearby',
               '<span class="di-text di-num" data-f="big">' + Math.round(d.m) + ' m</span>') +
          '<div class="di-exp__body">' + bar(100 - d.m * 5, C.green, 'di-bar--slim') + '</div></div>';
      },
      sync: function (el, d) {
        setF(el, 'away', Math.round(d.m) + ' m away');
        setF(el, 'big', Math.round(d.m) + ' m');
        setVar(el, 'bar', '--p', Math.round(100 - d.m * 5));
      },
      status: function (d) { return 'Find My — AirTag ' + Math.round(d.m) + ' m away'; }
    },

    /* ── Flashlight ──────────────────────────────────────────────── */
    {
      id: 'flashlight', label: 'Flashlight', icon: 'flashlight', accent: C.yellow,
      kind: 'alert', duration: 2200,
      data: function () { return { on: true }; },
      compact: function (d) {
        return { lead: ico('flashlight', d.on ? C.yellow : '#48484a'), trail: label(d.on ? 'Flashlight On' : 'Flashlight Off', 'di-text--dim') };
      },
      minimal: function () { return glyph('flashlight', C.yellow); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('flashlight', d.on ? C.yellow : '#48484a', 'di-ico--lg'), 'Flashlight', d.on ? 'On · Brightness 3 of 4' : 'Off') + '</div>';
      },
      tap: function (d, c) { d.on = !d.on; c.rerender(); },
      status: function (d) { return 'Flashlight ' + (d.on ? 'on' : 'off'); }
    }
  ];

  /* ══════════════════════════════════════════════════════════════════
     BACKGROUND ACTIVITIES
     ═════════════════════════════════════════════════════════════════ */

  var TRACKS = [
    { title: 'Midnight City', artist: 'M83', dur: 243, color: '#fa233b' },
    { title: 'Weightless', artist: 'Marconi Union', dur: 486, color: '#7b61ff' },
    { title: 'Nightcall', artist: 'Kavinsky', dur: 258, color: '#ff5f6d' }
  ];

  var activities = [

    /* ── Music ───────────────────────────────────────────────────── */
    {
      id: 'music', label: 'Music playback', icon: 'music', accent: '#fa233b', kind: 'activity',
      data: function () {
        var t = TRACKS[Math.floor(Math.random() * TRACKS.length)];
        return { i: TRACKS.indexOf(t), title: t.title, artist: t.artist, dur: t.dur, color: t.color, pos: 42, playing: true };
      },
      tick: function (d, c) {
        if (!d.playing) return;
        d.pos += c.dt / 1000;
        if (d.pos >= d.dur) { d.pos = 0; this.action(d, c, 'next'); }
      },
      compact: function (d) {
        return { lead: art(d.color, 'music'), trail: eq(d.color, !d.playing) };
      },
      minimal: function (d) { return eq(d.color, !d.playing); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(art(d.color, 'music', 'di-art--lg'), d.title, d.artist,
               '<span class="di-ico di-ico--plain" style="--c:rgba(255,255,255,.5)">' + Icons.airplay + '</span>') +
          '<div class="di-exp__body">' + bar(d.pos / d.dur * 100, '#fff', 'di-bar--slim') +
            '<div class="di-exp__times"><span data-f="t1">' + mmss(d.pos) + '</span>' +
            '<span data-f="t2">-' + mmss(d.dur - d.pos) + '</span></div></div>' +
          '<div class="di-exp__actions di-exp__actions--wide">' +
            btn('prev', 'prev', 'di-btn--ghost', 'Previous track') +
            btn('toggle', d.playing ? 'pause' : 'play', 'di-btn--lg di-btn--ghost', d.playing ? 'Pause' : 'Play') +
            btn('next', 'next', 'di-btn--ghost', 'Next track') +
          '</div></div>';
      },
      sync: function (el, d) {
        setVar(el, 'bar', '--p', (d.pos / d.dur * 100).toFixed(2));
        setF(el, 't1', mmss(d.pos));
        setF(el, 't2', '-' + mmss(d.dur - d.pos));
      },
      tap: function (d, c) { this.action(d, c, 'toggle'); },
      action: function (d, c, act) {
        if (act === 'toggle') { d.playing = !d.playing; c.rerender(); }
        if (act === 'next' || act === 'prev') {
          d.i = (d.i + (act === 'next' ? 1 : TRACKS.length - 1)) % TRACKS.length;
          var t = TRACKS[d.i];
          d.title = t.title; d.artist = t.artist; d.dur = t.dur; d.color = t.color; d.pos = 0; d.playing = true;
          c.rerender();
        }
      },
      status: function (d) { return (d.playing ? 'Playing' : 'Paused') + ' — ' + d.title + ' · ' + d.artist; }
    },

    /* ── Phone call ──────────────────────────────────────────────── */
    {
      id: 'call', label: 'Phone call', icon: 'phone', accent: C.green, kind: 'activity',
      data: function () { return { name: 'Maya Alvarez', sub: 'mobile', phase: 'incoming', sec: 0, muted: false }; },
      tick: function (d, c) { if (d.phase === 'active') d.sec += c.dt / 1000; },
      compact: function (d) {
        if (d.phase === 'incoming') {
          return { lead: '<span class="di-ico" style="--c:' + C.green + '">' + Icons.phone + '</span>',
                   trail: label(d.name.split(' ')[0], 'di-text--accent') };
        }
        return { lead: glyph('phone', C.green),
                 trail: wave(C.green, 4) + label(mmss(d.sec), 'di-num', 'dur') };
      },
      minimal: function (d) { return glyph('phone', d.phase === 'incoming' ? C.green : 'rgba(255,255,255,.8)'); },
      expanded: function (d) {
        var initials = d.name.split(' ').map(function (w) { return w[0]; }).join('');
        var avatar = '<span class="di-art di-art--lg" style="--c:linear-gradient(150deg,#5e5ce6,#bf5af2);' +
          'font-size:19px;font-weight:600">' + esc(initials) + '</span>';
        if (d.phase === 'incoming') {
          return '<div class="di-expanded di-enter">' +
            head(avatar, d.name, 'incoming · ' + d.sub) +
            '<div class="di-exp__actions di-exp__actions--wide">' +
              '<span class="di-btn-label">' + btn('decline', 'phoneDown', 'di-btn--lg di-btn--red', 'Decline') + 'Decline</span>' +
              '<span class="di-btn-label">' + btn('accept', 'phone', 'di-btn--lg di-btn--green', 'Accept') + 'Accept</span>' +
            '</div></div>';
        }
        return '<div class="di-expanded di-enter">' +
          head(avatar, d.name, 'iPhone · ' + d.sub,
               '<span class="di-text di-num" style="font-size:15px" data-f="big">' + mmss(d.sec) + '</span>') +
          '<div class="di-exp__actions">' +
            btn('mute', 'mic', d.muted ? 'di-btn--solid' : '', 'Mute') +
            btn('speaker', 'volume', '', 'Speaker') +
            btn('end', 'phoneDown', 'di-btn--red', 'End call') +
          '</div></div>';
      },
      sync: function (el, d) {
        if (d.phase !== 'active') return;
        setF(el, 'dur', mmss(d.sec));
        setF(el, 'big', mmss(d.sec));
      },
      tap: function (d, c) {
        if (d.phase === 'incoming') { d.phase = 'active'; d.sec = 0; c.rerender(); }
        else { d.muted = !d.muted; c.rerender(); }
      },
      action: function (d, c, act) {
        if (act === 'accept') { d.phase = 'active'; d.sec = 0; c.rerender(); }
        else if (act === 'decline' || act === 'end') { c.dismiss(); }
        else if (act === 'mute') { d.muted = !d.muted; c.rerender(); }
      },
      status: function (d) {
        return d.phase === 'incoming' ? 'Incoming call — ' + d.name : 'On call — ' + d.name + ' · ' + mmss(d.sec);
      }
    },

    /* ── Maps navigation ─────────────────────────────────────────── */
    {
      id: 'maps', label: 'Navigation', icon: 'turnRight', accent: C.green, kind: 'activity',
      data: function () {
        return {
          step: 0, m: 420, eta: 14,
          legs: [
            { icon: 'turnRight', road: 'Market St',    m: 420 },
            { icon: 'turnLeft',  road: 'Valencia St',  m: 950 },
            { icon: 'turnRight', road: '18th St',      m: 240 },
            { icon: 'location',  road: 'Dolores Park', m: 120 }
          ]
        };
      },
      tick: function (d, c) {
        d.m -= 22 * (c.dt / 1000);          // ≈ 80 km/h
        if (d.m <= 0) {
          d.step = (d.step + 1) % d.legs.length;
          d.m = d.legs[d.step].m;
          d.eta = Math.max(1, d.eta - 3);
          c.rerender();
        }
      },
      compact: function (d) {
        var leg = d.legs[d.step];
        return { lead: '<span class="di-ico" style="--c:' + C.green + '">' + Icons[leg.icon] + '</span>',
                 trail: label(dist(d.m), 'di-num', 'dist') };
      },
      minimal: function (d) { return glyph(d.legs[d.step].icon, C.green); },
      expanded: function (d) {
        var leg = d.legs[d.step];
        return '<div class="di-expanded di-enter">' +
          head('<span class="di-maneuver">' + Icons[leg.icon] + '</span>',
               dist(d.m), (leg.icon === 'location' ? 'Arrive at ' : 'Turn onto ') + leg.road,
               '<span class="di-text di-num" style="font-size:15px" data-f="eta">' + d.eta + ' min</span>') +
          '<div class="di-exp__body">' + bar(100 - (d.m / leg.m) * 100, C.green, 'di-bar--slim') +
            '<div class="di-exp__times"><span>' + esc(leg.road) + '</span>' +
            '<span data-f="arrive">Arrive ' + arriveAt(d.eta) + '</span></div></div>' +
          '<div class="di-exp__actions">' +
            '<span class="di-btn-label">' + btn('mute', 'volume', 'di-btn--ghost', 'Mute guidance') + 'Audio</span>' +
            '<span class="di-btn-label">' + btn('end', 'xmark', 'di-btn--red', 'End route') + 'End</span>' +
          '</div></div>';
      },
      sync: function (el, d) {
        setF(el, 'dist', dist(d.m));
        setF(el, 'eta', d.eta + ' min');
        setF(el, 'title', dist(d.m));
        setVar(el, 'bar', '--p', (100 - (d.m / d.legs[d.step].m) * 100).toFixed(1));
      },
      action: function (d, c, act) { if (act === 'end') c.dismiss(); },
      status: function (d) { return 'Navigating — ' + dist(d.m) + ' to ' + d.legs[d.step].road; }
    },

    /* ── Timer ───────────────────────────────────────────────────── */
    {
      id: 'timer', label: 'Timer', icon: 'timer', accent: C.orange, kind: 'activity',
      data: function () { return { total: 300, left: 300, running: true }; },
      tick: function (d, c) {
        if (!d.running) return;
        d.left = Math.max(0, d.left - c.dt / 1000);
        if (d.left === 0) { d.running = false; c.rerender(); }
      },
      compact: function (d) {
        var p = (1 - d.left / d.total) * 100;
        return { lead: ring(p, C.orange, '', 'di-ring'),
                 trail: label(mmss(d.left), 'di-num di-text--accent', 'left') };
      },
      minimal: function (d) { return glyph('timer', C.orange); },
      expanded: function (d) {
        var p = (1 - d.left / d.total) * 100;
        return '<div class="di-expanded di-enter">' +
          head(ico('timer', C.orange, 'di-ico--lg'), d.left === 0 ? 'Timer Done' : 'Timer',
               d.running ? 'Running' : (d.left === 0 ? 'Tap to dismiss' : 'Paused'),
               '<span class="di-text di-num" style="font-size:22px;color:' + C.orange + '" data-f="big">' + mmss(d.left) + '</span>') +
          '<div class="di-exp__body">' + bar(p, C.orange, 'di-bar--slim') + '</div>' +
          '<div class="di-exp__actions">' +
            btn('toggle', d.running ? 'pause' : 'play', 'di-btn--lg di-btn--ghost', d.running ? 'Pause timer' : 'Resume timer') +
            btn('stop', 'stop', 'di-btn--red', 'Stop timer') +
          '</div></div>';
      },
      sync: function (el, d) {
        var p = (1 - d.left / d.total) * 100;
        setF(el, 'left', mmss(d.left));
        setF(el, 'big', mmss(d.left));
        setVar(el, 'ring', '--p', p.toFixed(1));
        setVar(el, 'bar', '--p', p.toFixed(1));
      },
      tap: function (d, c) { this.action(d, c, 'toggle'); },
      action: function (d, c, act) {
        if (act === 'toggle') { if (d.left === 0) return c.dismiss(); d.running = !d.running; c.rerender(); }
        if (act === 'stop') c.dismiss();
      },
      status: function (d) { return 'Timer — ' + mmss(d.left) + (d.running ? ' left' : ' (paused)'); }
    },

    /* ── Live sports ─────────────────────────────────────────────── */
    {
      id: 'sports', label: 'Live score', icon: 'trophy', accent: '#ff9f0a', kind: 'activity',
      data: function () {
        return { home: { k: 'ARS', n: 'Arsenal', c: '#ef0107', s: 1 },
                 away: { k: 'MCI', n: 'Man City', c: '#6cabdd', s: 1 },
                 min: 63, event: '' };
      },
      tick: function (d, c) {
        d.acc = (d.acc || 0) + c.dt;
        if (d.acc < 2000) return;              // one football minute ≈ 2 s
        d.acc = 0;
        d.min = Math.min(90, d.min + 1);
        if (Math.random() < 0.06 && d.min < 90) {
          var side = Math.random() < 0.5 ? d.home : d.away;
          side.s++;
          d.event = 'GOAL · ' + side.k;
          c.rerender();
          setTimeout(function () { d.event = ''; }, 4000);
        }
      },
      compact: function (d) {
        return { lead: '<span class="di-team__badge" style="background:' + d.home.c + ';width:22px;height:22px;font-size:9px">' + d.home.k + '</span>',
                 trail: label(d.home.s + ' – ' + d.away.s, 'di-num', 'score') +
                        '<span class="di-team__badge" style="background:' + d.away.c + ';width:22px;height:22px;font-size:9px">' + d.away.k + '</span>' };
      },
      minimal: function (d) { return '<span class="di-text di-num" style="font-size:11px" data-f="score">' + d.home.s + '–' + d.away.s + '</span>'; },
      expanded: function (d) {
        function team(t) {
          return '<span class="di-team"><span class="di-team__badge" style="background:' + t.c + '">' + t.k + '</span>' +
            '<span class="di-team__name">' + esc(t.n) + '</span></span>';
        }
        return '<div class="di-expanded di-enter">' +
          '<div class="di-exp__head di-exp__head--split"><span class="di-text di-text--dim">Premier League</span>' +
          '<span class="di-exp__headgap"></span>' +
          '<span class="di-text di-text--accent" style="--c:' + C.green + '" data-f="min">' + d.min + '&rsquo;</span></div>' +
          '<div class="di-score">' + team(d.home) +
            '<span class="di-score__nums" data-f="score">' + d.home.s + ' – ' + d.away.s + '</span>' +
            team(d.away) + '</div>' +
          '<div class="di-score__clock" data-f="event">' + (d.event || 'Second half') + '</div>' +
          '</div>';
      },
      sync: function (el, d) {
        setF(el, 'score', d.home.s + ' – ' + d.away.s);
        setF(el, 'min', d.min + '&rsquo;');
        setF(el, 'event', d.event || 'Second half');
      },
      status: function (d) { return 'Live — ' + d.home.k + ' ' + d.home.s + '–' + d.away.s + ' ' + d.away.k + ' (' + d.min + "')"; }
    },

    /* ── Screen recording ────────────────────────────────────────── */
    {
      id: 'screenrec', label: 'Screen recording', icon: 'screenRecord', accent: C.red, kind: 'activity',
      data: function () { return { sec: 0 }; },
      tick: function (d, c) { d.sec += c.dt / 1000; },
      compact: function (d) {
        return { lead: '<span class="di-dot" style="--c:' + C.red + '"></span>',
                 trail: label(mmss(d.sec), 'di-num', 'dur') };
      },
      minimal: function () { return '<span class="di-dot" style="--c:' + C.red + '"></span>'; },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('screenRecord', C.red, 'di-ico--lg'), 'Screen Recording', 'Microphone on',
               '<span class="di-text di-num" style="font-size:17px" data-f="big">' + mmss(d.sec) + '</span>') +
          '<div class="di-exp__actions">' + btn('stop', 'stop', 'di-btn--lg di-btn--red', 'Stop recording') + '</div></div>';
      },
      sync: function (el, d) { setF(el, 'dur', mmss(d.sec)); setF(el, 'big', mmss(d.sec)); },
      tap: function (d, c) { c.dismiss(); },
      action: function (d, c, act) { if (act === 'stop') c.dismiss(); },
      status: function (d) { return 'Screen recording — ' + mmss(d.sec); }
    },

    /* ── Voice memo ──────────────────────────────────────────────── */
    {
      id: 'voicememo', label: 'Voice memo', icon: 'waveform', accent: C.red, kind: 'activity',
      privacy: 'mic',
      data: function () { return { sec: 0, paused: false }; },
      tick: function (d, c) { if (!d.paused) d.sec += c.dt / 1000; },
      compact: function (d) {
        return { lead: glyph('waveform', C.red), trail: label(hms(d.sec), 'di-num', 'dur') };
      },
      minimal: function () { return wave(C.red, 3); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('waveform', C.red, 'di-ico--lg'), 'New Recording ' + (new Date().getHours() > 11 ? 'PM' : 'AM'),
               d.paused ? 'Paused' : 'Recording…',
               '<span class="di-text di-num" style="font-size:17px" data-f="big">' + hms(d.sec) + '</span>') +
          '<div class="di-exp__body" style="align-items:center">' + wave(C.red, 22) + '</div>' +
          '<div class="di-exp__actions">' +
            btn('toggle', d.paused ? 'record' : 'pause', 'di-btn--ghost', d.paused ? 'Resume' : 'Pause') +
            btn('stop', 'stop', 'di-btn--red', 'Stop recording') +
          '</div></div>';
      },
      sync: function (el, d) { setF(el, 'dur', hms(d.sec)); setF(el, 'big', hms(d.sec)); },
      tap: function (d, c) { d.paused = !d.paused; c.rerender(); },
      action: function (d, c, act) {
        if (act === 'toggle') { d.paused = !d.paused; c.rerender(); }
        if (act === 'stop') c.dismiss();
      },
      status: function (d) { return 'Voice memo — ' + hms(d.sec) + (d.paused ? ' (paused)' : ''); }
    },

    /* ── Personal hotspot ────────────────────────────────────────── */
    {
      id: 'hotspot', label: 'Personal Hotspot', icon: 'hotspot', accent: C.blue, kind: 'activity',
      data: function () { return { devices: 1, mb: 0 }; },
      tick: function (d, c) { d.mb += (0.4 + Math.random() * 0.9) * (c.dt / 1000); },
      compact: function (d) {
        return { lead: glyph('hotspot', C.blue),
                 trail: label(d.devices + ' Device' + (d.devices === 1 ? '' : 's'), 'di-text--dim', 'dev') };
      },
      minimal: function () { return glyph('hotspot', C.blue); },
      expanded: function (d) {
        return '<div class="di-expanded di-enter">' +
          head(ico('hotspot', C.blue, 'di-ico--lg'), 'Personal Hotspot',
               d.devices + ' device connected',
               '<span class="di-text di-num" data-f="big">' + d.mb.toFixed(1) + ' MB</span>') +
          '<div class="di-exp__body"><div class="di-chips">' +
            '<span class="di-chip">' + Icons.wifi + ' Maximise Compatibility</span>' +
            '<span class="di-chip">' + Icons.cellular + ' 5G · 62 Mbps</span>' +
          '</div></div>' +
          '<div class="di-exp__actions">' + btn('off', 'xmark', 'di-btn--red', 'Turn off hotspot') + '</div></div>';
      },
      sync: function (el, d) {
        setF(el, 'dev', d.devices + ' Device' + (d.devices === 1 ? '' : 's'));
        setF(el, 'big', d.mb.toFixed(1) + ' MB');
      },
      tap: function (d, c) { d.devices = d.devices % 3 + 1; c.rerender(); },
      action: function (d, c, act) { if (act === 'off') c.dismiss(); },
      status: function (d) { return 'Hotspot — ' + d.devices + ' device(s), ' + d.mb.toFixed(1) + ' MB'; }
    },

    /* ── SharePlay ───────────────────────────────────────────────── */
    {
      id: 'shareplay', label: 'SharePlay', icon: 'shareplay', accent: '#5e5ce6', kind: 'activity',
      data: function () { return { title: 'Dune: Part Two', people: ['MA', 'JR', 'TK'], pos: 1284, dur: 9360 }; },
      tick: function (d, c) { d.pos = Math.min(d.dur, d.pos + c.dt / 1000); },
      compact: function (d) {
        return { lead: glyph('shareplay', '#5e5ce6'), trail: label(d.people.length + ' watching', 'di-text--dim') };
      },
      minimal: function () { return glyph('shareplay', '#5e5ce6'); },
      expanded: function (d) {
        var chips = d.people.map(function (p) {
          return '<span class="di-chip"><span class="di-team__badge" style="background:#5e5ce6;width:18px;height:18px;font-size:8px">' + p + '</span></span>';
        }).join('');
        return '<div class="di-expanded di-enter">' +
          head(art('#5e5ce6', 'shareplay', 'di-art--lg'), d.title, 'SharePlay · ' + d.people.length + ' people') +
          '<div class="di-exp__body">' + bar(d.pos / d.dur * 100, '#fff', 'di-bar--slim') +
            '<div class="di-exp__times"><span data-f="t1">' + hms(d.pos) + '</span><span>' + hms(d.dur) + '</span></div>' +
            '<div class="di-chips">' + chips + '</div></div>' +
          '<div class="di-exp__actions">' + btn('leave', 'xmark', 'di-btn--red', 'Leave SharePlay') + '</div></div>';
      },
      sync: function (el, d) {
        setF(el, 't1', hms(d.pos));
        setVar(el, 'bar', '--p', (d.pos / d.dur * 100).toFixed(2));
      },
      action: function (d, c, act) { if (act === 'leave') c.dismiss(); },
      status: function (d) { return 'SharePlay — ' + d.title + ' · ' + d.people.length + ' people'; }
    }
  ];

  function arriveAt(minutes) {
    var t = new Date(Date.now() + minutes * 60000);
    var h = t.getHours() % 12 || 12;
    return h + ':' + pad(t.getMinutes()) + (t.getHours() < 12 ? ' AM' : ' PM');
  }

  /* ── index ────────────────────────────────────────────────────────── */
  var byId = {};
  alerts.concat(activities).forEach(function (def) {
    // Every definition gets safe no-op defaults so island.js can call blindly.
    if (!def.minimal) def.minimal = function () { return glyph(def.icon, def.accent); };
    if (!def.sync) def.sync = function () {};
    if (!def.tick) def.tick = function () {};
    if (!def.tap) def.tap = function () {};
    if (!def.action) def.action = function () {};
    if (!def.status) def.status = function () { return def.label; };
    byId[def.id] = def;
  });

  return { alerts: alerts, activities: activities, byId: byId, helpers: { mmss: mmss, hms: hms, esc: esc } };
})();

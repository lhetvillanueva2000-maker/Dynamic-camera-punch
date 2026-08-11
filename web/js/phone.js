/* ============================================================================
   phone.js — builds a device frame for an island to live in.

   The markup contract lives here rather than in a page, so the demo page, the
   screenshot gallery and anything else you embed the island in all construct
   the exact same DOM. DI.Island only ever queries the [data-*] hooks below.
   ========================================================================== */
window.DI = window.DI || {};

DI.Phone = (function () {
  'use strict';

  var Icons = DI.Icons;

  var VARIANT_COPY = {
    a: '<b>Variant A</b> — punch-hole fused to the top bezel. Square shoulders, ' +
       'rounded chin; the island grows down out of the edge.',
    b: '<b>Variant B</b> — punch-hole floating clear of the bezels. Fully ' +
       'rounded in every state.'
  };

  var APPS = [
    { name: 'Messages', icon: 'messages', bg: 'linear-gradient(160deg,#5ff07a,#0fbc3c)' },
    { name: 'Safari',   icon: 'safari',   bg: 'linear-gradient(160deg,#6ec8ff,#0a6bff)' },
    { name: 'Photos',   icon: 'photos',   bg: 'linear-gradient(160deg,#fff08a,#ff5f8f)' },
    { name: 'Camera',   icon: 'camera',   bg: 'linear-gradient(160deg,#8e8e93,#3a3a3c)' },
    { name: 'Maps',     icon: 'location', bg: 'linear-gradient(160deg,#7ce0a0,#1a9e5c)' },
    { name: 'Clock',    icon: 'clock',    bg: 'linear-gradient(160deg,#3a3a3c,#0b0b0d)' },
    { name: 'Notes',    icon: 'notes',    bg: 'linear-gradient(160deg,#ffe98a,#f5c518)' },
    { name: 'Wallet',   icon: 'wallet',   bg: 'linear-gradient(160deg,#4a4a4f,#111114)' },
    { name: 'Calendar', icon: 'calendar', bg: 'linear-gradient(160deg,#ffffff,#e9e9ee)', ink: true },
    { name: 'Mail',     icon: 'mail',     bg: 'linear-gradient(160deg,#67c9ff,#0a6bff)' },
    { name: 'Store',    icon: 'appstore', bg: 'linear-gradient(160deg,#4facff,#0a4bff)' },
    { name: 'Settings', icon: 'settings', bg: 'linear-gradient(160deg,#9a9aa0,#54545a)' }
  ];

  var DOCK = [
    { name: 'Phone',  icon: 'phone',  bg: 'linear-gradient(160deg,#5ff07a,#0fbc3c)' },
    { name: 'Music',  icon: 'music',  bg: 'linear-gradient(160deg,#ff6a8a,#fa233b)' },
    { name: 'Safari', icon: 'safari', bg: 'linear-gradient(160deg,#6ec8ff,#0a6bff)' },
    { name: 'Camera', icon: 'camera', bg: 'linear-gradient(160deg,#8e8e93,#3a3a3c)' }
  ];

  var BARS = [46, 62, 88, 74, 55, 40, 68];

  function tile(a) {
    return '<div class="app" data-app="' + a.name + '" data-icon="' + a.icon + '" ' +
      'role="button" tabindex="0" aria-label="Open ' + a.name + '">' +
      '<span class="app__icon" style="background:' + a.bg +
      (a.ink ? ';color:#1c1c1e' : '') + '">' + Icons[a.icon] + '</span>' +
      '<span class="app__name">' + a.name + '</span></div>';
  }

  /* The app a tile opens into. One per frame, reused: the launch animation is a
     FLIP from the tapped icon's rect to the full screen, so the window only ever
     needs to exist once and be re-dressed for whichever app was tapped. */
  var APP_WINDOW_HTML =
    '<div class="app-window" data-app-window aria-hidden="true">' +
      '<div class="app-window__chrome">' +
        '<span class="app-window__icon" data-app-window-icon></span>' +
        '<span class="app-window__title" data-app-window-title></span>' +
      '</div>' +
      '<div class="app-window__body">' +
        '<div class="app-window__hero" data-app-window-hero></div>' +
        '<div class="app-window__rows">' +
          '<i style="width:82%"></i><i style="width:64%"></i><i style="width:91%"></i>' +
          '<i style="width:47%"></i><i style="width:75%"></i><i style="width:58%"></i>' +
        '</div>' +
      '</div>' +
      '<div class="app-window__hint">swipe up or tap the bar to close</div>' +
    '</div>';

  /* The island's own markup: a gooey blob layer and a matching surface layer.
     See css/island.css for why it is drawn twice. */
  var ISLAND_HTML =
    '<div class="island-root" data-island-root>' +
      '<div class="island-layer island-layer--blobs" aria-hidden="true">' +
        '<div class="blob blob--main" data-blob-main></div>' +
        '<div class="blob blob--sat" data-blob-sat></div>' +
      '</div>' +
      '<div class="island-layer island-layer--surface">' +
        '<div class="island" data-island tabindex="0" role="button" ' +
             'aria-label="Dynamic Island" aria-expanded="false">' +
          '<div class="island__specular" aria-hidden="true"></div>' +
          '<div class="island__lens" aria-hidden="true"><i></i></div>' +
          '<div class="island__content" data-island-content></div>' +
        '</div>' +
        '<div class="island-sat" data-island-sat aria-hidden="true">' +
          '<div class="island-sat__inner" data-island-sat-content></div>' +
        '</div>' +
      '</div>' +
    '</div>';

  function html(variant, opts) {
    opts = opts || {};
    return '' +
    '<figure class="phone" data-variant="' + variant + '">' +
      (opts.label === false ? '' :
        '<figcaption class="phone__label">' + (VARIANT_COPY[variant] || '') + '</figcaption>') +
      /* The stage owns the layout box; the frame inside it keeps its full
         390×820 design size at every screen width and is scaled as a unit. A
         phone that is narrowed without being shortened stops being a phone —
         its home screen clips and its dock loses icons. */
      '<div class="phone__stage">' +
      '<div class="phone__frame">' +
        '<div class="phone__buttons" aria-hidden="true">' +
          '<i class="phone__btn phone__btn--silent"></i>' +
          '<i class="phone__btn phone__btn--up"></i>' +
          '<i class="phone__btn phone__btn--down"></i>' +
          '<i class="phone__btn phone__btn--power"></i>' +
        '</div>' +
        '<div class="phone__screen">' +
          '<div class="wallpaper" aria-hidden="true"></div>' +

          /* A fixed-width spacer keeps the status glyphs clear of the cutout. */
          '<div class="statusbar">' +
            '<span class="statusbar__time" data-clock>9:41</span>' +
            '<span class="statusbar__reserve" aria-hidden="true"></span>' +
            '<span class="statusbar__glyphs">' +
              '<span style="width:17px">' + Icons.cellular + '</span>' +
              '<span style="width:16px">' + Icons.wifi + '</span>' +
              '<span class="di-batt"><i style="--p:82;--c:#fff"></i></span>' +
            '</span>' +
          '</div>' +

          /* Home screen; scrolls underneath the island (z-index 1 vs 30). */
          '<div class="screen-content" data-content>' +
            '<div class="widget">' +
              '<div class="widget__head"><span class="widget__title">Cupertino</span>' +
              '<span class="widget__temp">21°</span></div>' +
              '<div class="widget__sub">Mostly Clear · H:24° L:14°</div>' +
              '<div class="widget__bars" aria-hidden="true">' +
                BARS.map(function (h) { return '<i style="height:' + h + '%"></i>'; }).join('') +
              '</div>' +
            '</div>' +
            '<div class="app-grid">' + APPS.map(tile).join('') + '</div>' +
          '</div>' +

          '<div class="dock">' + DOCK.map(tile).join('') + '</div>' +
          APP_WINDOW_HTML +
          '<div class="scrim" data-scrim aria-hidden="true"></div>' +
          ISLAND_HTML +
          '<div class="homebar" data-homebar></div>' +
        '</div>' +
      '</div>' +
      '</div>' +
    '</figure>';
  }

  /* ── Launching an app ─────────────────────────────────────────────── */

  var ALL_APPS = APPS.concat(DOCK);

  function appByName(name) {
    for (var i = 0; i < ALL_APPS.length; i++) {
      if (ALL_APPS[i].name === name) return ALL_APPS[i];
    }
    return null;
  }

  /**
   * Wire the home screen so tapping an icon opens it.
   *
   * The launch is a FLIP: measure the tapped icon, start the window at exactly
   * that rect, then animate to the full screen on the same curve the island
   * uses. Because both rects are measured rather than assumed, the icon and the
   * window corner stay welded together for the whole transition however the
   * frame is scaled on the page.
   */
  function wireApps(figure) {
    var screen = figure.querySelector('.phone__screen');
    var win = figure.querySelector('[data-app-window]');
    var homebar = figure.querySelector('[data-homebar]');
    if (!screen || !win) return;

    var iconEl = win.querySelector('[data-app-window-icon]');
    var titleEl = win.querySelector('[data-app-window-title]');
    var heroEl = win.querySelector('[data-app-window-hero]');
    var openName = null;

    /** An element's box in the screen's own coordinates, plus the screen's size. */
    function rectIn(el) {
      var a = el.getBoundingClientRect(), b = screen.getBoundingClientRect();
      return { x: a.left - b.left, y: a.top - b.top,
               w: a.width, h: a.height, sw: b.width, sh: b.height };
    }

    function open(tileEl) {
      var def = appByName(tileEl.getAttribute('data-app'));
      if (!def || openName) return;
      openName = def.name;

      titleEl.textContent = def.name;
      iconEl.style.background = def.bg;
      iconEl.style.color = def.ink ? '#1c1c1e' : '#fff';
      iconEl.innerHTML = Icons[def.icon] || '';
      heroEl.style.background = def.bg;

      var r = rectIn(tileEl.querySelector('.app__icon'));
      // Start welded to the icon…
      win.style.transition = 'none';
      win.style.transformOrigin = '0 0';
      win.style.transform = 'translate(' + r.x + 'px,' + r.y + 'px) scale(' +
        (r.w / r.sw) + ',' + (r.h / r.sh) + ')';
      win.style.opacity = '0';
      win.style.borderRadius = '90px';
      win.classList.add('is-open');
      win.setAttribute('aria-hidden', 'false');

      // …then let the browser flush that before starting the transition, or the
      // two style writes coalesce into one frame and nothing animates.
      void win.offsetWidth;
      win.style.transition = '';
      win.style.transform = 'translate(0,0) scale(1,1)';
      win.style.opacity = '1';
      win.style.borderRadius = '';
      figure.classList.add('has-app-open');
    }

    function close() {
      if (!openName) return;
      var tileEl = figure.querySelector('.app[data-app="' + cssEscape(openName) + '"] .app__icon');
      openName = null;
      figure.classList.remove('has-app-open');

      if (tileEl) {
        var r = rectIn(tileEl);
        win.style.transform = 'translate(' + r.x + 'px,' + r.y + 'px) scale(' +
          (r.w / r.sw) + ',' + (r.h / r.sh) + ')';
        win.style.borderRadius = '90px';
      }
      win.style.opacity = '0';
      win.setAttribute('aria-hidden', 'true');
      // Hold .is-open until the shrink has finished — it is what keeps the
      // window visible — then drop it. Slightly longer than the .52s transition
      // so the last frame is never cut off.
      setTimeout(function () {
        if (!openName) win.classList.remove('is-open');
      }, 560);
    }

    /* Attribute selectors need quoting for names with spaces or quotes. Every
       app here is a plain word, but building a selector from data is exactly
       where that stops being true one edit later. */
    function cssEscape(s) { return String(s).replace(/["\\]/g, '\\$&'); }

    figure.addEventListener('click', function (e) {
      var t = e.target.closest('.app[data-app]');
      if (t && !openName) { open(t); return; }
      if (openName && (e.target.closest('[data-homebar]') || e.target.closest('.app-window'))) {
        close();
      }
    });

    figure.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && openName) { close(); return; }
      var t = e.target.closest('.app[data-app]');
      if (t && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); open(t); }
    });

    if (homebar) homebar.setAttribute('title', 'Close the open app');
    return { open: open, close: close, isOpen: function () { return !!openName; } };
  }

  /** Build a frame and append it to `parent`. Returns the <figure>. */
  function create(variant, parent, opts) {
    var wrap = document.createElement('div');
    wrap.innerHTML = html(variant, opts);
    var el = wrap.firstElementChild;
    if (parent) parent.appendChild(el);
    // Must be attached before wiring: the FLIP measures real rects.
    wireApps(el);
    return el;
  }

  return { html: html, create: create, wireApps: wireApps,
           copy: VARIANT_COPY, apps: APPS, dock: DOCK };
})();

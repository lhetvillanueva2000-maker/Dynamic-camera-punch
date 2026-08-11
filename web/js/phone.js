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
    return '<div class="app"><span class="app__icon" style="background:' + a.bg +
      (a.ink ? ';color:#1c1c1e' : '') + '">' + Icons[a.icon] + '</span>' +
      '<span class="app__name">' + a.name + '</span></div>';
  }

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
          '<div class="scrim" data-scrim aria-hidden="true"></div>' +
          ISLAND_HTML +
          '<div class="homebar" aria-hidden="true"></div>' +
        '</div>' +
      '</div>' +
    '</figure>';
  }

  /** Build a frame and append it to `parent`. Returns the <figure>. */
  function create(variant, parent, opts) {
    var wrap = document.createElement('div');
    wrap.innerHTML = html(variant, opts);
    var el = wrap.firstElementChild;
    if (parent) parent.appendChild(el);
    return el;
  }

  return { html: html, create: create, copy: VARIANT_COPY, apps: APPS, dock: DOCK };
})();

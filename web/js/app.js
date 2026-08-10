/* ============================================================================
   app.js — the demo harness.

   Builds the phone frames, wires one DI.Island per frame, and hooks up the
   control panel. Nothing here is required by the island itself: drop
   island.js + registry.js into a page, hand them a frame built from the same
   markup contract, and you have a working Dynamic Island.
   ========================================================================== */
(function () {
  'use strict';

  var Icons = DI.Icons;
  var Registry = DI.Registry;

  var screensEl = document.getElementById('screens');
  var statusText = document.getElementById('status-text');
  var statusDot = document.querySelector('.panel__dot');

  /** Live islands, one per visible phone frame. */
  var islands = [];

  /* ── Screen mounting ──────────────────────────────────────────────── */

  function mountScreens(mode) {
    islands.forEach(function (i) { i.destroy(); });
    islands = [];
    screensEl.innerHTML = '';
    screensEl.classList.toggle('is-both', mode === 'both');

    (mode === 'both' ? ['a', 'b'] : [mode]).forEach(function (v) {
      islands.push(new DI.Island(DI.Phone.create(v, screensEl), { onChange: updateReadout }));
    });
    updateReadout();
    syncButtonStates();
  }

  /** Fan a command out to every island on screen so both variants stay in sync. */
  function all(fn) { islands.forEach(fn); syncButtonStates(); }

  /* ── Control panel ────────────────────────────────────────────────── */

  /** A near-white accent makes a white glyph invisible, so flip it to ink. */
  function chipFg(accent) {
    var m = /^#([0-9a-f]{6})$/i.exec(accent);
    if (!m) return '';
    var n = parseInt(m[1], 16);
    // Rec. 601 luma is plenty for a yes/no contrast decision.
    var luma = (0.299 * (n >> 16 & 255) + 0.587 * (n >> 8 & 255) + 0.114 * (n & 255)) / 255;
    return luma > 0.7 ? ';color:#101014' : '';
  }

  function triggerButton(def) {
    return '<button class="btn" data-present="' + def.id + '" type="button">' +
      '<span class="btn__ico" style="--c:' + def.accent + chipFg(def.accent) + '">' + Icons[def.icon] + '</span>' +
      '<span class="btn__txt">' + Registry.helpers.esc(def.label) + '</span></button>';
  }

  document.getElementById('alert-buttons').innerHTML = Registry.alerts.map(triggerButton).join('');
  document.getElementById('activity-buttons').innerHTML = Registry.activities.map(triggerButton).join('');

  document.body.addEventListener('click', function (e) {
    var b = e.target.closest('[data-present]');
    if (!b) return;
    var id = b.getAttribute('data-present');
    var def = Registry.byId[id];
    // Clicking a live activity again dismisses it — makes the grid a toggle board.
    var live = def.kind === 'activity' && islands.length && islands[0]._indexOf(id) >= 0;
    all(function (i) { live ? i.dismiss(id) : i.present(id); });
  });

  document.getElementById('btn-swap').addEventListener('click', function () { all(function (i) { i.swap('left'); }); });
  document.getElementById('btn-expand').addEventListener('click', function () { all(function (i) { i.toggle(); }); });
  document.getElementById('btn-clear').addEventListener('click', function () { all(function (i) { i.clear(); }); });

  function syncButtonStates() {
    var live = {};
    if (islands.length) islands[0].stack.forEach(function (e) { live[e.def.id] = true; });
    document.querySelectorAll('[data-present]').forEach(function (b) {
      b.classList.toggle('is-live', !!live[b.getAttribute('data-present')]);
    });
  }

  function updateReadout() {
    var isl = islands[0];
    var p = isl && isl.current();
    if (!p) {
      statusText.textContent = 'Idle — nothing presented';
      statusDot.setAttribute('data-live', 'off');
      return;
    }
    var text = p.def.status(p.data);
    var sec = isl.secondary();
    if (sec) text += '  ·  + ' + sec.def.label;
    if (isl.isExpanded) text += '  ·  expanded';
    statusText.innerHTML = '<b>' + Registry.helpers.esc(text) + '</b>';
    statusDot.setAttribute('data-live', 'on');
  }

  /* ── Tabs, screens, theme ─────────────────────────────────────────── */

  document.querySelectorAll('.tabs__btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.querySelectorAll('.tabs__btn').forEach(function (b) {
        b.classList.toggle('is-active', b === btn);
        b.setAttribute('aria-selected', b === btn ? 'true' : 'false');
      });
      document.querySelectorAll('.tabpanel').forEach(function (p) {
        p.classList.toggle('is-active', p.getAttribute('data-panel') === btn.getAttribute('data-tab'));
      });
    });
  });

  document.querySelectorAll('[data-screen]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.querySelectorAll('[data-screen]').forEach(function (b) {
        b.classList.toggle('is-active', b === btn);
        b.setAttribute('aria-selected', b === btn ? 'true' : 'false');
      });
      mountScreens(btn.getAttribute('data-screen'));
    });
  });

  var themeBtn = document.getElementById('theme-toggle');
  function paintThemeIcon() {
    var dark = document.documentElement.getAttribute('data-theme') === 'dark';
    themeBtn.querySelector('[data-theme-icon]').innerHTML = dark ? Icons.sun : Icons.moon;
  }
  themeBtn.addEventListener('click', function () {
    var dark = document.documentElement.getAttribute('data-theme') === 'dark';
    document.documentElement.setAttribute('data-theme', dark ? 'light' : 'dark');
    try { localStorage.setItem('dcp-theme', dark ? 'light' : 'dark'); } catch (e) {}
    paintThemeIcon();
  });
  try {
    var saved = localStorage.getItem('dcp-theme');
    if (saved) document.documentElement.setAttribute('data-theme', saved);
  } catch (e) {}
  paintThemeIcon();

  /* ── Status-bar clock ─────────────────────────────────────────────── */
  function paintClocks() {
    var d = new Date();
    var t = (d.getHours() % 12 || 12) + ':' + (d.getMinutes() < 10 ? '0' : '') + d.getMinutes();
    document.querySelectorAll('[data-clock]').forEach(function (n) { n.textContent = t; });
  }
  setInterval(paintClocks, 10000);

  /* ── Deep links: ?screen=b&present=music,timer&expand=1 ───────────── */
  function applyQuery() {
    var q = new URLSearchParams(location.search);
    var screen = q.get('screen');
    if (screen && ['a', 'b', 'both'].indexOf(screen) >= 0) {
      var b = document.querySelector('[data-screen="' + screen + '"]');
      if (b) b.click();
    }
    var present = q.get('present');
    if (present) {
      // Reverse: present() unshifts, so the last id listed ends up primary.
      present.split(',').reverse().forEach(function (id) {
        all(function (i) { i.present(id.trim()); });
      });
    }
    if (q.get('expand') === '1') all(function (i) { i.expand(); });
  }

  /* ── Boot ─────────────────────────────────────────────────────────── */
  mountScreens('a');
  paintClocks();
  applyQuery();

  // Expose for console tinkering / automated screenshots.
  window.dcp = {
    islands: function () { return islands; },
    present: function (id) { all(function (i) { i.present(id); }); },
    dismiss: function (id) { all(function (i) { i.dismiss(id); }); },
    expand: function () { all(function (i) { i.expand(); }); },
    collapse: function () { all(function (i) { i.collapse(); }); },
    screen: function (m) { document.querySelector('[data-screen="' + m + '"]').click(); }
  };
})();

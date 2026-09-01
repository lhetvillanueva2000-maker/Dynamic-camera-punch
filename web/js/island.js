/* ============================================================================
   island.js — the Dynamic Island engine.

   One instance drives one phone frame. It owns four things:

     1. STATE      an alert (transient) layered over a stack of at most two
                   background activities, plus an expanded flag.
     2. GEOMETRY   measure the rendered content, write px width/height back on
                   the element, let one CSS transition carry the morph.
     3. GESTURES   tap / long-press / horizontal swipe, pointer-events based,
                   with a spring for the drag-follow.
     4. TICK       a single 200 ms heartbeat that advances every presentation's
                   model and patches live values in place.

   It knows nothing about music, timers or Face ID — see registry.js.
   ========================================================================== */
window.DI = window.DI || {};

DI.Island = (function () {
  'use strict';

  var TICK_MS      = 200;   // model heartbeat
  var FADE_MS      = 150;   // content cross-fade half
  var HOLD_MS      = 420;   // touch-and-hold threshold
  var MOVE_SLOP    = 9;     // px of travel that cancels a hold / tap
  var SWIPE_MIN    = 44;    // px needed to commit a swap
  var PAD_X        = 14;    // horizontal breathing room around compact content
  var PAD_TOP_EXP  = 30;    // expanded content starts below the camera lens
  var PAD_BOT_EXP  = 16;
  var MAX_ACTIVITIES = 2;

  function Island(phoneEl, options) {
    var q = function (sel) { return phoneEl.querySelector(sel); };

    this.phone       = phoneEl;
    this.screen      = q('.phone__screen');
    this.root        = q('[data-island-root]');
    this.el          = q('[data-island]');
    this.content     = q('[data-island-content]');
    this.sat         = q('[data-island-sat]');
    this.satContent  = q('[data-island-sat-content]');
    this.blobMain    = q('[data-blob-main]');
    this.blobSat     = q('[data-blob-sat]');
    this.scrim       = q('[data-scrim]');

    this.opts        = options || {};
    this.onChange    = this.opts.onChange || function () {};

    this.stack       = [];      // background activities, [0] is primary
    this.alert       = null;    // transient presentation on top of the stack
    this.isExpanded  = false;
    this.dragX       = 0;

    // Screen-reader channel: every presentation change is announced once.
    this.live = document.createElement('div');
    this.live.className = 'sr-only';
    this.live.setAttribute('role', 'status');
    this.live.setAttribute('aria-live', 'polite');
    this.root.appendChild(this.live);

    this.spring = new DI.Spring({
      stiffness: 260, damping: 24,
      onUpdate: this._applyDrag.bind(this)
    });

    this._bindGestures();
    this._paint(false);

    var self = this;
    this._timer = setInterval(function () { self._tick(); }, TICK_MS);
    this._lastTick = Date.now();
  }

  /* ══════════════════════════════════════════════════════════════════
     PUBLIC API
     ═════════════════════════════════════════════════════════════════ */

  /** Show a presentation by registry id. Alerts interrupt; activities stack. */
  Island.prototype.present = function (id) {
    var def = DI.Registry.byId[id];
    if (!def) return null;

    var entry = { def: def, data: def.data(), t0: Date.now(), alive: 0 };

    if (def.kind === 'alert') {
      this.alert = entry;
      this.isExpanded = false;
      this._repaint(true);
      this._announce(def.status(entry.data));
      return entry;
    }

    // Re-presenting a live activity just brings it to the front.
    var existing = this._indexOf(id);
    if (existing >= 0) {
      this.stack.unshift(this.stack.splice(existing, 1)[0]);
    } else {
      this.stack.unshift(entry);
      if (this.stack.length > MAX_ACTIVITIES) this.stack.pop();
    }
    this._repaint(true);
    this._announce(def.status(this.stack[0].data));
    return this.stack[0];
  };

  /** Remove a presentation. With no id, removes whatever is on screen now. */
  Island.prototype.dismiss = function (id) {
    if (id == null) {
      if (this.alert) { this.alert = null; }
      else if (this.stack.length) { this.stack.shift(); }
    } else if (this.alert && this.alert.def.id === id) {
      this.alert = null;
    } else {
      var i = this._indexOf(id);
      if (i >= 0) this.stack.splice(i, 1);
    }
    if (!this.current()) this.collapse();
    this._repaint(true);
    return this;
  };

  Island.prototype.clear = function () {
    this.alert = null;
    this.stack.length = 0;
    this.collapse();
    this._repaint(true);
    return this;
  };

  Island.prototype.expand = function () {
    if (this.isExpanded || !this.current()) return this;
    this.isExpanded = true;
    this.el.setAttribute('aria-expanded', 'true');
    this.el.classList.add('is-expanded');
    this.screen.classList.add('is-expanded');
    this._repaint(true);
    return this;
  };

  Island.prototype.collapse = function () {
    if (!this.isExpanded) return this;
    this.isExpanded = false;
    this.el.setAttribute('aria-expanded', 'false');
    this.el.classList.remove('is-expanded');
    this.screen.classList.remove('is-expanded');
    this._repaint(true);
    return this;
  };

  Island.prototype.toggle = function () {
    return this.isExpanded ? this.collapse() : this.expand();
  };

  /** Promote the secondary activity. Direction only affects the animation. */
  Island.prototype.swap = function (direction) {
    if (this.stack.length < 2 || this.alert) return this;
    this.stack.push(this.stack.shift());
    var from = (direction === 'left' ? 1 : -1) * 46;
    this.spring.set(from);
    this.spring.to(0);
    this._repaint(true);
    this._announce(this.stack[0].def.status(this.stack[0].data));
    return this;
  };

  /** The presentation currently occupying the pill. */
  Island.prototype.current = function () {
    return this.alert || this.stack[0] || null;
  };

  /** The activity riding in the detached minimal indicator, if any. */
  Island.prototype.secondary = function () {
    return (!this.alert && this.stack.length > 1) ? this.stack[1] : null;
  };

  Island.prototype.destroy = function () {
    clearInterval(this._timer);
    clearTimeout(this._fadeTimer);
    this.spring.stop();
  };

  Island.prototype._indexOf = function (id) {
    for (var i = 0; i < this.stack.length; i++) if (this.stack[i].def.id === id) return i;
    return -1;
  };

  /* ══════════════════════════════════════════════════════════════════
     RENDER + GEOMETRY
     ═════════════════════════════════════════════════════════════════ */

  /**
   * Cross-fade to the new presentation.
   * Order matters: fade the old content OUT first, then swap the markup and
   * remeasure, then start the shape morph and fade IN. Measuring while the old
   * content is still on screen would size the island to the wrong content.
   */
  Island.prototype._repaint = function (animate) {
    var self = this;

    if (!animate || !this.content.innerHTML) {
      clearTimeout(this._fadeTimer);
      this._fadeTimer = 0;
      this.content.classList.remove('is-out');
      this._paint(true);
      return;
    }

    // Coalesce. Restarting the fade on every request would let a burst of
    // updates starve the swap and leave the island sitting there invisible;
    // the already-scheduled paint reads whatever the newest state is anyway.
    if (this._fadeTimer) return;

    this.content.classList.add('is-out');
    this._fadeTimer = setTimeout(function () {
      self._fadeTimer = 0;
      self._paint(true);
      self.content.classList.remove('is-out');
    }, FADE_MS);
  };

  Island.prototype._paint = function (notify) {
    var p = this.current();

    // Build markup for the pill.
    if (!p) {
      this.content.innerHTML = '';
    } else if (this.isExpanded) {
      this.content.innerHTML = p.def.expanded(p.data);
    } else {
      var c = p.def.compact(p.data);
      this.content.innerHTML =
        '<div class="di-compact">' +
          '<span class="di-compact__lead">'  + c.lead  + '</span>' +
          '<span class="di-compact__gap"></span>' +
          '<span class="di-compact__trail">' + c.trail + '</span>' +
        '</div>';
    }

    // Minimal indicator for the second concurrent activity.
    var sec = this.secondary();
    this.root.classList.toggle('has-satellite', !!sec && !this.isExpanded);
    this.satContent.innerHTML = sec && !this.isExpanded ? sec.def.minimal(sec.data) : '';
    this.sat.setAttribute('aria-hidden', sec ? 'false' : 'true');

    // Camera/mic privacy glow rides on the lens itself.
    if (p && p.def.privacy) this.el.setAttribute('data-privacy', p.def.privacy);
    else this.el.removeAttribute('data-privacy');

    this._layout();
    if (notify) this.onChange(this);
  };

  /**
   * Content-driven sizing.
   *
   *   idle     → the physical cutout, straight from the CSS custom properties
   *   compact  → natural content width + padding (content is `width:max-content`,
   *              so offsetWidth is its intrinsic size regardless of the island)
   *   expanded → fixed width, measured height
   *
   * Writing plain px onto width/height is what lets a single CSS transition
   * animate the whole morph, blobs included.
   */
  Island.prototype._layout = function () {
    var cs = getComputedStyle(this.phone);
    var variant = this.phone.getAttribute('data-variant') || 'b';
    var idleW = parseFloat(cs.getPropertyValue('--cut-' + variant + '-w')) || 30;
    var idleH = parseFloat(cs.getPropertyValue('--cut-' + variant + '-h')) || 30;
    var p = this.current();

    var w, h, radius;

    if (!p) {
      this.content.style.width = '';
      this.content.classList.remove('is-expanded-plate');
      w = idleW;
      h = idleH;
      radius = variant === 'a' ? Math.min(idleH * 0.62, 18) : idleH / 2;

    } else if (this.isExpanded) {
      var rootW = this.root.clientWidth;
      w = Math.min(340, rootW - 28);
      this.content.classList.add('is-expanded-plate');
      this.content.style.width = (w - PAD_X * 2) + 'px';
      // offsetHeight, NOT getBoundingClientRect().height. Freshly mounted
      // expanded content carries .di-enter, which animates from scale(.97), and
      // getBoundingClientRect reports the *transformed* box — so the island was
      // measured up to 3% short of its own content and then clipped it, because
      // .island is overflow:hidden. offsetHeight is the layout box and ignores
      // transforms entirely. scrollHeight covers anything that still overflows.
      h = Math.max(this.content.offsetHeight, this.content.scrollHeight)
          + PAD_TOP_EXP + PAD_BOT_EXP;
      radius = 34;

    } else {
      this.content.style.width = '';
      this.content.classList.remove('is-expanded-plate');
      h = parseFloat(cs.getPropertyValue('--island-compact-h')) || 37;
      w = this._balanceCompact(idleW);
      radius = h / 2;
    }

    this.el.style.width  = w + 'px';
    this.el.style.height = h + 'px';
    this.el.style.borderRadius = radius + 'px';

    // The gooey blob mirrors the surface exactly; the filter supplies the
    // liquid bridge to the satellite for free.
    this.blobMain.style.width  = w + 'px';
    this.blobMain.style.height = h + 'px';
    this.blobMain.style.borderRadius = radius + 'px';

    // The satellite anchors itself to the pill's edge off these two, which is
    // how the pill stays centred (and the camera stays put) when it appears.
    this.root.style.setProperty('--island-w', w + 'px');
    this.root.style.setProperty('--island-h', h + 'px');
  };

  /**
   * Give the leading and trailing slots identical widths and return the pill
   * width that follows.
   *
   * This is the whole trick behind the compact state. The camera gap has to
   * land on the pill's horizontal centre, because that is where the lens is
   * physically drilled — and a gap between two *unequal* slots does not sit in
   * the middle, so a long trailing label would slide underneath the lens.
   * Padding both slots out to max(lead, trail) puts the gap dead centre and
   * incidentally gives the pill the balanced look iOS has.
   *
   * When the balanced width would run past the screen edge, the halves are
   * capped instead and the labels ellipsise (see .di-text in island.css).
   */
  Island.prototype._balanceCompact = function (idleW) {
    var lead  = this.content.querySelector('.di-compact__lead');
    var trail = this.content.querySelector('.di-compact__trail');
    var gapEl = this.content.querySelector('.di-compact__gap');
    if (!lead || !trail) return Math.max(idleW, this.content.offsetWidth + PAD_X * 2);

    lead.style.width = trail.style.width = '';        // measure intrinsic first

    // getBoundingClientRect, not offsetWidth: offsetWidth is an integer and
    // rounds *down*, so a 44.6px label would be pinned to 44px and ellipsise
    // itself for no reason. Round up and add a hairline instead.
    var gapW = gapEl ? gapEl.getBoundingClientRect().width : 0;
    var maxW = Math.max(idleW, this.root.clientWidth - 28);
    var maxHalf = Math.max(18, (maxW - PAD_X * 2 - gapW) / 2);
    var natural = Math.max(lead.getBoundingClientRect().width,
                           trail.getBoundingClientRect().width);
    var half = Math.min(maxHalf, Math.ceil(natural) + 1);

    lead.style.width = trail.style.width = half + 'px';
    return Math.max(idleW, Math.ceil(half * 2 + gapW + PAD_X * 2));
  };

  /* ══════════════════════════════════════════════════════════════════
     HEARTBEAT
     ═════════════════════════════════════════════════════════════════ */

  Island.prototype._tick = function () {
    var now = Date.now();
    var dt = now - this._lastTick;
    this._lastTick = now;

    var self = this;
    var needsRepaint = false;
    var visible = this.current();

    function advance(entry, isVisible) {
      var ctx = {
        dt: dt,
        elapsed: entry.alive,
        island: self,
        rerender: function () { if (isVisible) needsRepaint = true; },
        dismiss: function () { self.dismiss(entry.def.id); }
      };
      entry.def.tick.call(entry.def, entry.data, ctx);
    }

    // Alert lifetime only burns down while it is actually on screen —
    // holding the island open pauses the countdown, exactly like iOS.
    // `holdAlerts` freezes it outright, which is what the screenshot gallery
    // uses to hold every alert in its entry phase.
    if (this.alert) {
      if (!this.isExpanded && !this.opts.holdAlerts) this.alert.alive += dt;
      advance(this.alert, true);
      if (!this.isExpanded && !this.opts.holdAlerts &&
          this.alert.alive >= (this.alert.def.duration || 3000)) {
        this.alert = null;
        this._repaint(true);
        return;
      }
    }

    // Background activities keep running even while something else is shown.
    for (var i = 0; i < this.stack.length; i++) {
      this.stack[i].alive += dt;
      advance(this.stack[i], !this.alert && i === 0);
    }

    if (needsRepaint) { this._repaint(true); return; }

    // Patch live values without rebuilding the DOM.
    if (visible) {
      visible.def.sync(this.content, visible.data);
      var before = this.el.style.width;
      if (!this.isExpanded) this._layout();          // text can change width
      if (before !== this.el.style.width) { /* transition handles it */ }
      this.onChange(this);
    }
    var sec = this.secondary();
    if (sec) sec.def.sync(this.satContent, sec.data);
  };

  Island.prototype._announce = function (text) { this.live.textContent = text; };

  /* ══════════════════════════════════════════════════════════════════
     GESTURES
     ═════════════════════════════════════════════════════════════════ */

  Island.prototype._applyDrag = function (x) {
    this.dragX = x;
    this.root.style.setProperty('--drag', x.toFixed(2) + 'px');
  };

  Island.prototype._bindGestures = function () {
    var self = this;
    var g = null;   // active gesture record

    function onDown(e) {
      // Expanded-view buttons handle themselves.
      if (e.target.closest && e.target.closest('[data-act]')) return;
      if (!self.current()) return;

      g = {
        id: e.pointerId,
        x0: e.clientX, y0: e.clientY,
        t0: performance.now(),
        lastX: e.clientX, lastT: performance.now(), vx: 0,
        moved: false, held: false, axis: null
      };
      self.spring.stop();
      self.el.classList.add('is-pressed');

      g.holdTimer = setTimeout(function () {
        if (!g || g.moved) return;
        g.held = true;
        self.el.classList.remove('is-pressed');
        self.el.classList.add('is-holding');
        if (navigator.vibrate) navigator.vibrate(12);
        setTimeout(function () { self.el.classList.remove('is-holding'); }, 260);
        self.toggle();
      }, HOLD_MS);

      self.el.setPointerCapture && self.el.setPointerCapture(e.pointerId);
    }

    function onMove(e) {
      if (!g || e.pointerId !== g.id) return;
      var dx = e.clientX - g.x0;
      var dy = e.clientY - g.y0;

      if (!g.moved && Math.hypot(dx, dy) > MOVE_SLOP) {
        g.moved = true;
        clearTimeout(g.holdTimer);
        self.el.classList.remove('is-pressed');
        g.axis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y';
      }
      if (!g.moved || g.axis !== 'x') return;

      // Track pointer velocity so the release can hand it to the spring.
      var now = performance.now();
      if (now > g.lastT) g.vx = (e.clientX - g.lastX) / (now - g.lastT) * 1000;
      g.lastX = e.clientX; g.lastT = now;

      // Only two activities can be swapped; with one, the pill rubber-bands.
      var free = self.stack.length > 1 && !self.alert && !self.isExpanded;
      self.spring.set(free ? dx : DI.Spring.rubber(dx, 26));
      e.preventDefault();
    }

    function onUp(e) {
      if (!g || e.pointerId !== g.id) return;
      clearTimeout(g.holdTimer);
      self.el.classList.remove('is-pressed');

      var dx = e.clientX - g.x0;
      var dy = e.clientY - g.y0;
      var rec = g;
      g = null;

      if (rec.held) { self.spring.to(0, rec.vx); return; }

      if (!rec.moved) {                                   // ── tap
        self._onTap(e);
        self.spring.to(0);
        return;
      }

      if (rec.axis === 'x') {                             // ── horizontal swipe
        var far = Math.abs(dx) > SWIPE_MIN || Math.abs(rec.vx) > 480;
        if (far && self.stack.length > 1 && !self.alert && !self.isExpanded) {
          self.swap(dx < 0 ? 'left' : 'right');
        } else {
          self.spring.to(0, rec.vx);
        }
        return;
      }

      // ── vertical swipe: down opens, up closes
      if (dy > 26 && !self.isExpanded) self.expand();
      else if (dy < -26 && self.isExpanded) self.collapse();
      self.spring.to(0);
    }

    this.el.addEventListener('pointerdown', onDown);
    this.el.addEventListener('pointermove', onMove, { passive: false });
    this.el.addEventListener('pointerup', onUp);
    this.el.addEventListener('pointercancel', function (e) {
      if (!g) return;
      clearTimeout(g.holdTimer);
      g = null;
      self.el.classList.remove('is-pressed', 'is-holding');
      self.spring.to(0);
    });

    // Expanded-view action buttons.
    this.el.addEventListener('click', function (e) {
      var b = e.target.closest && e.target.closest('[data-act]');
      if (!b) return;
      e.stopPropagation();
      var p = self.current();
      if (!p) return;
      p.def.action.call(p.def, p.data, self._ctx(p), b.getAttribute('data-act'));
      self.onChange(self);
    });

    // Tapping the minimal indicator promotes that activity.
    this.sat.addEventListener('click', function () { self.swap('left'); });

    // Tapping outside collapses, same as iOS.
    this.scrim.addEventListener('click', function () { self.collapse(); });

    // Keyboard parity for every gesture.
    this.el.addEventListener('keydown', function (e) {
      if (e.key === 'Enter')      { e.preventDefault(); self._onTap(e); }
      else if (e.key === ' ')     { e.preventDefault(); self.toggle(); }
      else if (e.key === 'Escape'){ self.collapse(); }
      else if (e.key === 'ArrowLeft')  { e.preventDefault(); self.swap('left'); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); self.swap('right'); }
    });

    // Geometry depends on the container width, so re-measure on resize.
    var raf = 0;
    window.addEventListener('resize', function () {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(function () { self._layout(); });
    });
  };

  Island.prototype._ctx = function (entry) {
    var self = this;
    return {
      dt: 0,
      elapsed: entry.alive,
      island: this,
      rerender: function () { self._repaint(true); },
      dismiss: function () { self.dismiss(entry.def.id); }
    };
  };

  Island.prototype._onTap = function () {
    var p = this.current();
    if (!p) return;
    if (this.isExpanded) return;                 // expanded taps belong to buttons
    p.def.tap.call(p.def, p.data, this._ctx(p));
    this.onChange(this);
  };

  return Island;
})();

/* ============================================================================
   spring.js — a tiny critically-tunable spring integrator.

   CSS easing curves handle the state morph (one curve, whole transition), but
   anything the finger drives has no fixed duration: the animation has to start
   from whatever position and velocity the gesture left behind. That is what a
   spring is for.

   Model: a damped harmonic oscillator integrated with a fixed 1/120 s
   sub-step, so behaviour is identical on 60 Hz and 120 Hz displays.

        a = -k·(x - target) - c·v

   `stiffness` is k, `damping` is c. damping = 2·√k is critical damping (no
   overshoot); below that the spring bounces.
   ========================================================================== */
window.DI = window.DI || {};

DI.Spring = (function () {
  'use strict';

  var STEP = 1 / 120;      // fixed integration step, seconds
  var REST_X = 0.15;       // px from target that counts as "arrived"
  var REST_V = 0.15;       // px/s below which motion is imperceptible

  /**
   * @param {object}   opts
   * @param {number}   opts.stiffness  spring constant (default 220)
   * @param {number}   opts.damping    damping coefficient (default 26)
   * @param {number}   opts.from       initial position
   * @param {Function} opts.onUpdate   called with the current value each frame
   * @param {Function} [opts.onRest]   called once when the spring settles
   */
  function Spring(opts) {
    opts = opts || {};
    this.k = opts.stiffness != null ? opts.stiffness : 220;
    this.c = opts.damping != null ? opts.damping : 26;
    this.x = opts.from || 0;
    this.v = 0;
    this.target = this.x;
    this.onUpdate = opts.onUpdate || function () {};
    this.onRest = opts.onRest || function () {};
    this._raf = 0;
    this._last = 0;
    this._tick = this._tick.bind(this);
  }

  /** Animate toward `target`, optionally injecting the gesture's exit velocity. */
  Spring.prototype.to = function (target, velocity) {
    this.target = target;
    if (velocity != null) this.v = velocity;
    this._start();
    return this;
  };

  /** Jump straight to a value — used while the finger is still down. */
  Spring.prototype.set = function (x) {
    this.stop();
    this.x = this.target = x;
    this.v = 0;
    this.onUpdate(this.x);
    return this;
  };

  Spring.prototype.stop = function () {
    if (this._raf) cancelAnimationFrame(this._raf);
    this._raf = 0;
    return this;
  };

  Spring.prototype._start = function () {
    if (this._raf) return;
    this._last = performance.now();
    this._raf = requestAnimationFrame(this._tick);
  };

  Spring.prototype._tick = function (now) {
    // Clamp dt so a backgrounded tab doesn't explode the integration.
    var dt = Math.min((now - this._last) / 1000, 0.064);
    this._last = now;

    var steps = Math.max(1, Math.round(dt / STEP));
    var h = dt / steps;
    for (var i = 0; i < steps; i++) {
      var a = -this.k * (this.x - this.target) - this.c * this.v;
      this.v += a * h;
      this.x += this.v * h;
    }

    if (Math.abs(this.x - this.target) < REST_X && Math.abs(this.v) < REST_V) {
      this.x = this.target;
      this.v = 0;
      this._raf = 0;
      this.onUpdate(this.x);
      this.onRest();
      return;
    }

    this.onUpdate(this.x);
    this._raf = requestAnimationFrame(this._tick);
  };

  /** Rubber-band resistance: drag past a boundary and the surface pushes back. */
  Spring.rubber = function (distance, limit) {
    var d = Math.abs(distance);
    var damped = (1 - 1 / (d / limit + 1)) * limit;
    return distance < 0 ? -damped : damped;
  };

  return Spring;
})();

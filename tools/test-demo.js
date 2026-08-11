#!/usr/bin/env node
/* ============================================================================
   test-demo.js — drives the real demo page in a real browser.

     node tools/test-demo.js

   Needs Playwright ("npm i -D playwright"). Set CHROME to point at a browser
   binary if Playwright's own download is not available.

   Everything here is an assertion about behaviour that has actually broken at
   least once: an app window left visible after its close animation, the open app
   swallowing the status bar, the island falling behind it, the lens drifting off
   the screen's centre line. Static checks would have caught none of them.
   ========================================================================== */
'use strict';

const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const PAGE = 'file://' + path.join(ROOT, 'web', 'dynamic-camera-punch-v2.1.0.html');

let fails = 0;
const ok = (m) => console.log('  ✓ ' + m);
const bad = (m) => { fails++; console.log('  ✗ ' + m); };

/** Prefer the preinstalled browser when Playwright's own is missing. */
function launchOptions() {
  const o = { args: ['--no-sandbox'] };
  const preinstalled = process.env.CHROME || '/opt/pw-browsers/chromium';
  if (fs.existsSync(preinstalled)) o.executablePath = preinstalled;
  return o;
}

async function appLaunching(page) {
  console.log('Opening apps from the home screen');
  const win = page.locator('[data-app-window]').first();

  await page.goto(PAGE);
  await page.waitForTimeout(900);

  (await win.count()) ? ok('app window present') : bad('app window missing');
  (await win.evaluate(e => getComputedStyle(e).visibility)) === 'hidden'
    ? ok('starts hidden') : bad('does not start hidden');

  await page.locator('.app[data-app="Camera"]').first().click();
  await page.waitForTimeout(750);

  const s = await win.evaluate(e => ({
    cls: e.className,
    vis: getComputedStyle(e).visibility,
    op: getComputedStyle(e).opacity,
    tf: getComputedStyle(e).transform
  }));
  s.cls.includes('is-open') ? ok('opens on tap') : bad('did not open: ' + s.cls);
  s.vis === 'visible' ? ok('visible once open') : bad('visibility ' + s.vis);
  parseFloat(s.op) > 0.9 ? ok('faded in') : bad('opacity ' + s.op);
  (s.tf === 'none' || s.tf === 'matrix(1, 0, 0, 1, 0, 0)')
    ? ok('settles at full screen') : bad('transform stuck at ' + s.tf);

  (await page.locator('[data-app-window-title]').first().textContent()) === 'Camera'
    ? ok('names the tapped app') : bad('wrong app name');
  (await page.locator('[data-app-window-icon] svg').count())
    ? ok('app icon rendered') : bad('no app icon');
  (await page.locator('figure.phone').first().evaluate(e => e.classList.contains('has-app-open')))
    ? ok('home screen recedes behind it') : bad('home screen did not recede');

  // Layering: an open app must not swallow the phone's own chrome.
  const z = await page.evaluate(() => {
    const g = (sel) => +getComputedStyle(document.querySelector(sel)).zIndex;
    return { app: g('[data-app-window]'), island: g('.island-root'),
             status: g('.statusbar'), bar: g('.homebar'), home: g('.screen-content') };
  });
  z.island > z.app ? ok('island stays above the app') : bad('island buried under the app');
  z.status > z.app ? ok('status bar stays above the app') : bad('app covers the status bar');
  z.bar > z.app ? ok('home bar stays above the app') : bad('home bar buried');
  z.app > z.home ? ok('app covers the home screen') : bad('app under the home screen');
  (await page.locator('[data-clock]').first().isVisible())
    ? ok('clock still readable') : bad('clock hidden by the app');

  await page.locator('[data-homebar]').first().click();
  await page.waitForTimeout(750);
  const c = await win.evaluate(e => ({ cls: e.className, vis: getComputedStyle(e).visibility }));
  !c.cls.includes('is-open') ? ok('home bar closes it') : bad('still open after close');
  c.vis === 'hidden' ? ok('hidden again, not left floating') : bad('left visible: ' + c.vis);

  await page.locator('.dock .app[data-app="Music"]').first().click();
  await page.waitForTimeout(700);
  (await page.locator('[data-app-window-title]').first().textContent()) === 'Music'
    ? ok('re-opens as a different app') : bad('stale app name on re-open');
  await page.locator('[data-homebar]').first().click();
  await page.waitForTimeout(700);
}

async function idleGeometry(page) {
  console.log('Idle shape and the camera invariant');
  await page.goto(PAGE + '?screen=both');
  await page.waitForTimeout(1200);

  for (const v of ['a', 'b']) {
    const b = await page.locator('.phone[data-variant="' + v + '"] [data-island]').first().boundingBox();
    Math.abs(b.width - b.height) < 9
      ? ok(`variant ${v} rests as a circle (${Math.round(b.width)}×${Math.round(b.height)})`)
      : bad(`variant ${v} idle is ${Math.round(b.width)}×${Math.round(b.height)}, not round`);
  }

  const isl = page.locator('.phone[data-variant="a"] [data-island]').first();
  await page.evaluate(() => window.dcp.present('music'));
  await page.waitForTimeout(900);
  (await isl.boundingBox()).width > 110 ? ok('grows for content') : bad('did not grow');

  await page.evaluate(() => window.dcp.dismiss('music'));
  await page.waitForTimeout(1000);
  const back = await isl.boundingBox();
  (Math.abs(back.width - back.height) < 9 && back.width < 45)
    ? ok('shrinks back into the circle')
    : bad(`did not return to the circle: ${Math.round(back.width)}×${Math.round(back.height)}`);

  // The whole point of the centre-anchored model.
  const lens = await page.locator('.phone[data-variant="a"] .island__lens').first().boundingBox();
  const screen = await page.locator('.phone[data-variant="a"] .phone__screen').first().boundingBox();
  const off = Math.abs((lens.x + lens.width / 2) - (screen.x + screen.width / 2));
  off < 1.5 ? ok(`lens dead centre (${off.toFixed(2)} px off)`) : bad(`lens drifted ${off.toFixed(2)} px`);
}

(async () => {
  const browser = await chromium.launch(launchOptions());
  const page = await browser.newPage({ viewport: { width: 1400, height: 950 } });

  const noise = [];
  page.on('pageerror', e => noise.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') noise.push('console: ' + m.text()); });

  try {
    await appLaunching(page);
    await idleGeometry(page);
    noise.length ? noise.forEach(bad) : ok('no console or page errors throughout');
  } finally {
    await browser.close();
  }

  console.log(fails ? `\n${fails} failure(s)` : '\nAll checks passed.');
  process.exit(fails ? 1 : 0);
})().catch(err => { console.error(err); process.exit(1); });

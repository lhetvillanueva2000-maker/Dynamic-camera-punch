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
const PAGE = 'file://' + path.join(ROOT, 'web', 'dynamic-camera-punch-v2.5.0.html');

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

  // Measured against the idle size rather than a pixel count: the frame is
  // scaled to fit the viewport, so absolute widths mean nothing here.
  const isl = page.locator('.phone[data-variant="a"] [data-island]').first();
  const idle = await isl.boundingBox();

  await page.evaluate(() => window.dcp.present('music'));
  await page.waitForTimeout(900);
  const grown = await isl.boundingBox();
  const factor = grown.width / idle.width;
  factor > 3 ? ok(`grows for content (${factor.toFixed(1)}× the idle width)`)
             : bad(`barely grew: ${factor.toFixed(2)}× idle`);

  await page.evaluate(() => window.dcp.dismiss('music'));
  await page.waitForTimeout(1000);
  const back = await isl.boundingBox();
  (Math.abs(back.width - back.height) < idle.width * 0.3
      && Math.abs(back.width - idle.width) < 2)
    ? ok('shrinks back to exactly the idle circle')
    : bad(`did not return to the circle: ${Math.round(back.width)}×${Math.round(back.height)}`
          + ` (idle was ${Math.round(idle.width)}×${Math.round(idle.height)})`);

  // The whole point of the centre-anchored model.
  const lens = await page.locator('.phone[data-variant="a"] .island__lens').first().boundingBox();
  const screen = await page.locator('.phone[data-variant="a"] .phone__screen').first().boundingBox();
  const off = Math.abs((lens.x + lens.width / 2) - (screen.x + screen.width / 2));
  off < 1.5 ? ok(`lens dead centre (${off.toFixed(2)} px off)`) : bad(`lens drifted ${off.toFixed(2)} px`);
}

/**
 * Every expanded presentation must fit inside the island.
 *
 * .island is overflow:hidden, so a plate measured even a pixel short has its
 * bottom row of text or its action buttons silently cut off. This walks all 23
 * presentations and compares every descendant's box against the island's.
 */
async function expandedFits(page) {
  console.log('Expanded views fit inside the island');
  await page.goto(PAGE);
  await page.waitForTimeout(800);

  const ids = await page.evaluate(() =>
    DI.Registry.alerts.concat(DI.Registry.activities).map(d => d.id));
  const clipped = [];

  for (const id of ids) {
    await page.evaluate(i => window.dcp.present(i), id);
    await page.waitForTimeout(380);
    await page.evaluate(() => window.dcp.expand());
    await page.waitForTimeout(700);

    const over = await page.evaluate(() => {
      const isl = document.querySelector('[data-island]');
      const box = isl.getBoundingClientRect();
      let worst = 0;
      isl.querySelectorAll('*').forEach(el => {
        const b = el.getBoundingClientRect();
        if (!b.width || !b.height) return;
        worst = Math.max(worst, b.bottom - box.bottom, b.right - box.right, box.left - b.left);
      });
      return +worst.toFixed(1);
    });
    if (over > 1) clipped.push(`${id} (+${over}px)`);

    await page.evaluate(() => window.dcp.collapse());
    await page.waitForTimeout(260);
    await page.evaluate(i => window.dcp.dismiss(i), id);
    await page.waitForTimeout(200);
  }

  clipped.length
    ? bad(`${clipped.length} of ${ids.length} expanded views are clipped: ${clipped.join(', ')}`)
    : ok(`all ${ids.length} expanded views fit with nothing cut off`);
}

/** A drag that starts inside the phone belongs to the phone. */
async function gestures(page) {
  console.log('Gestures inside the phone');
  await page.goto(PAGE);
  await page.waitForTimeout(800);
  await page.locator('.phone__screen').first().scrollIntoViewIfNeeded();
  await page.waitForTimeout(300);

  const scr = await page.locator('.phone__screen').first().boundingBox();
  const vp = page.viewportSize();
  if (scr.y + scr.height > vp.height) {
    bad('phone bottom is below the viewport — the gesture checks cannot reach it');
    return;
  }

  const win = page.locator('[data-app-window]').first();
  const isOpen = async () => (await win.evaluate(e => e.className)).includes('is-open');

  const ta = await page.evaluate(() => ({
    screen: getComputedStyle(document.querySelector('.phone__screen')).touchAction,
    content: getComputedStyle(document.querySelector('.screen-content')).touchAction
  }));
  ta.screen === 'none' ? ok('the phone claims the touch') : bad(`screen touch-action ${ta.screen}`);
  ta.content === 'pan-y' ? ok('its home screen still scrolls internally')
                         : bad(`content touch-action ${ta.content}`);

  async function drag(x0, y0, x1, y1) {
    await page.mouse.move(x0, y0);
    await page.mouse.down();
    for (let i = 1; i <= 8; i++) {
      await page.mouse.move(x0 + (x1 - x0) * i / 8, y0 + (y1 - y0) * i / 8);
    }
    await page.mouse.up();
    await page.waitForTimeout(750);
  }
  async function openApp() {
    await page.locator('.app[data-app="Camera"]').first().click();
    await page.waitForTimeout(700);
  }

  await openApp();
  await drag(scr.x + scr.width / 2, scr.y + scr.height - 14,
             scr.x + scr.width / 2, scr.y + scr.height - 140);
  (await isOpen()) ? bad('swipe up from the bottom did not close the app')
                   : ok('swipe up from the bottom closes the app');

  await openApp();
  await drag(scr.x + 6, scr.y + scr.height / 2, scr.x + 130, scr.y + scr.height / 2);
  (await isOpen()) ? bad('inward swipe from the left edge did nothing')
                   : ok('left edge, swiped inward, closes the app');

  await openApp();
  await drag(scr.x + scr.width - 6, scr.y + scr.height / 2,
             scr.x + scr.width - 130, scr.y + scr.height / 2);
  (await isOpen()) ? bad('inward swipe from the right edge did nothing')
                   : ok('right edge, swiped inward, closes the app');

  // Direction matters: outward at an edge is someone reaching past the phone.
  await openApp();
  await drag(scr.x + 8, scr.y + scr.height / 2, scr.x - 90, scr.y + scr.height / 2);
  (await isOpen()) ? ok('outward swipe at an edge is ignored')
                   : bad('outward swipe closed the app — direction is not being checked');

  await drag(scr.x + scr.width / 2, scr.y + scr.height * 0.45,
             scr.x + scr.width / 2, scr.y + scr.height * 0.2);
  (await isOpen()) ? ok('a drag across the middle does not dismiss')
                   : bad('mid-screen drag closed the app');

  await page.evaluate(() => window.scrollTo(0, 0));
  await drag(scr.x + scr.width / 2, scr.y + scr.height * 0.6,
             scr.x + scr.width / 2, scr.y + scr.height * 0.15);
  const scrolled = await page.evaluate(() => window.scrollY);
  scrolled === 0 ? ok('the page does not scroll from a drag inside the phone')
                 : bad(`the page scrolled to ${scrolled}`);

  await page.locator('[data-homebar]').first().click();
  await page.waitForTimeout(700);
}

(async () => {
  const browser = await chromium.launch(launchOptions());
  const page = await browser.newPage({ viewport: { width: 1400, height: 1200 } });

  const noise = [];
  page.on('pageerror', e => noise.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') noise.push('console: ' + m.text()); });

  try {
    await appLaunching(page);
    await idleGeometry(page);
    await expandedFits(page);
    await gestures(page);
    noise.length ? noise.forEach(bad) : ok('no console or page errors throughout');
  } finally {
    await browser.close();
  }

  console.log(fails ? `\n${fails} failure(s)` : '\nAll checks passed.');
  process.exit(fails ? 1 : 0);
})().catch(err => { console.error(err); process.exit(1); });

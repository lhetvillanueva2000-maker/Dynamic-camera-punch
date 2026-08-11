#!/usr/bin/env node
/* ============================================================================
   capture.js — regenerates everything in docs/ from the live demo.

     node tools/capture.js            all artefacts
     node tools/capture.js gif        just the animation

   Needs Playwright ("npm i -D playwright") and ffmpeg for the GIF. Every image
   is a screenshot of the real running island, never a mock-up, so the docs
   cannot drift away from the code.
   ========================================================================== */
'use strict';

const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const DOCS = path.join(ROOT, 'docs');
const url = (p, q) => 'file://' + path.join(ROOT, p) + (q ? '?' + q : '');

const FFMPEG = process.env.FFMPEG ||
  (fs.existsSync('/opt/pw-browsers/ffmpeg-1011/ffmpeg-linux')
    ? '/opt/pw-browsers/ffmpeg-1011/ffmpeg-linux'
    : 'ffmpeg');

const DEMO_PAGE = 'dynamic-camera-punch-v2.3.0.html';
const FPS = 12;
const GIF_WIDTH = 380;

const only = process.argv[2];
fs.mkdirSync(DOCS, { recursive: true });

/** Full-page shot of the state gallery for one variant. */
async function gallery(browser, variant, expanded, out, height) {
  const page = await browser.newPage({
    viewport: { width: 1560, height },
    deviceScaleFactor: 2
  });
  await page.goto(url('tools/gallery.html', 'variant=' + variant + (expanded ? '&expanded=1' : '')));
  await page.waitForTimeout(2600);          // let every morph settle
  await page.screenshot({ path: path.join(DOCS, out), fullPage: true });
  await page.close();
  console.log('  ✓ docs/' + out);
}

/** The demo page itself, in a given state. */
async function demo(browser, query, out, viewport) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 2 });
  await page.goto(url('web/' + DEMO_PAGE, query));
  await page.waitForTimeout(2200);
  await page.screenshot({ path: path.join(DOCS, out) });
  await page.close();
  console.log('  ✓ docs/' + out);
}

/** Records the scripted timeline in tools/stage.html and turns it into a GIF. */
async function gif(browser, variant, out, seconds) {
  const tmp = fs.mkdtempSync(path.join(require('os').tmpdir(), 'dcp-'));
  const size = { width: 460, height: 780 };

  const ctx = await browser.newContext({
    viewport: size,
    deviceScaleFactor: 2,
    recordVideo: { dir: tmp, size }
  });
  const page = await ctx.newPage();
  await page.goto(url('tools/stage.html', 'variant=' + variant));
  await page.waitForFunction(() => window.__stageReady);
  await page.waitForTimeout(seconds * 1000);
  await ctx.close();                         // flushes the .webm

  const webm = fs.readdirSync(tmp).find(f => f.endsWith('.webm'));
  if (!webm) throw new Error('no video produced');
  const src = path.join(tmp, webm);
  const dst = path.join(DOCS, out);

  // ffmpeg only decodes and rescales here. The palette work happens in
  // make-gif.py, because Playwright's bundled ffmpeg is a stripped build with
  // no GIF muxer and no palettegen/paletteuse filters.
  const frames = path.join(tmp, 'frames');
  fs.mkdirSync(frames);
  // -r rather than the fps filter: the bundled build only compiles in
  // scale/crop/pad, so frame-rate conversion has to happen at the output.
  execFileSync(FFMPEG, ['-y', '-i', src,
    '-vf', `scale=${GIF_WIDTH}:-1`, '-r', String(FPS),
    path.join(frames, '%04d.png')], { stdio: 'ignore' });

  execFileSync('python3', [path.join(__dirname, 'make-gif.py'), frames, dst, '--fps', String(FPS)],
    { stdio: 'inherit' });

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('  ✓ docs/' + out + '  (' + (fs.statSync(dst).size / 1048576).toFixed(2) + ' MB)');
}

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox', '--hide-scrollbars'] });
  try {
    if (!only || only === 'gif') {
      console.log('Recording animation…');
      await gif(browser, 'a', 'preview.gif', 23);
    }
    if (!only || only === 'shots') {
      console.log('Capturing galleries…');
      await gallery(browser, 'a', false, 'variant-a-states.png', 2400);
      await gallery(browser, 'b', false, 'variant-b-states.png', 2400);
      await gallery(browser, 'b', true, 'expanded-states.png', 3200);
      console.log('Capturing the demo page…');
      await demo(browser, 'screen=both&present=music,timer', 'demo-both.png', { width: 1400, height: 900 });
      await demo(browser, 'screen=b&present=call&expand=1', 'demo-expanded.png', { width: 1400, height: 900 });
    }
  } finally {
    await browser.close();
  }
})().catch(err => { console.error(err); process.exit(1); });

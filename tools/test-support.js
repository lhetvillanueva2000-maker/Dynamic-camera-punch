// The bundled support page, driven in a real browser.
//
// The first assertion is the important one: this page ships inside an app that
// declares no INTERNET permission, so a page that needed the network would show
// blanks on a device and pass nowhere but here. Every request is intercepted,
// and anything that is not a file:// URL fails the run.
const path = require('path');
const { chromium } = require('playwright');
const ROOT = path.resolve(__dirname, '..');
const PAGE = 'file://' + path.join(ROOT,
        'android', 'app', 'src', 'main', 'assets', 'support', 'index.html');
let fails=0; const ok=m=>console.log('  ✓ '+m); const bad=m=>{fails++;console.log('  ✗ '+m);};
(async()=>{
  const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium',args:['--no-sandbox']});
  const ctx=await b.newContext({viewport:{width:420,height:900}});
  // Nothing may reach the network: the app has no INTERNET permission.
  const external=[];
  await ctx.route('**', r => {
    const u=r.request().url();
    if(!u.startsWith('file://')) { external.push(u); return r.abort(); }
    r.continue();
  });
  const p=await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  await p.goto(PAGE);
  await p.waitForTimeout(700);

  external.length===0 ? ok('makes no network requests at all')
                      : bad('tried to reach '+external.join(', '));
  (await p.locator('.support-card').count()) ? ok('card rendered') : bad('card missing');
  (await p.locator('.amount-btn').count())===4 ? ok('four amount buttons') : bad('amount buttons wrong');
  (await p.locator('#qrMissing').isVisible()) ? ok('QR placeholder shown (no donate-qr.png bundled)')
                                              : bad('QR placeholder not shown');
  // Interactions still work with no network.
  await p.locator('.amount-btn[data-value="500"]').click(); await p.waitForTimeout(200);
  (await p.locator('#btnAmountDisplay').textContent()).includes('500')
    ? ok('amount picker works') : bad('amount picker broken');
  await p.locator('.accordion-btn').click(); await p.waitForTimeout(250);
  (await p.locator('#accordionContent').isVisible()) ? ok('accordion opens') : bad('accordion stuck');
  (await p.locator('.offline-note').count()) ? ok('offline note present') : bad('offline note missing');
  const font = await p.evaluate(()=>getComputedStyle(document.body).fontFamily);
  !/Jakarta/.test(font) ? ok('uses a system font, not a web font ('+font.split(',')[0]+')')
                        : bad('still asking for a web font');
  errs.length?errs.forEach(bad):ok('no page errors');
  await b.close();
  console.log(fails?`\n${fails} failure(s)`:'\nSupport page OK.');
  process.exit(fails?1:0);
})();

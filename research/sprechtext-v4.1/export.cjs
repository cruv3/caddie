// PDF-Export und Prüfung für den Sprechtext v4.1.
// 1. Prüft, dass jede Szene des Decks Sprechtext hat und die Klick-Schritte übereinstimmen.
// 2. Exportiert Caddie-Sprechtext-v4.1.pdf (A5) und Caddie-Spickzettel-v4.1.pdf (A4) und prüft das Layout.
// Aufruf: node export.cjs   (vorher: python3 build.py)
// Umgebungsvariablen wie tools/snap.mjs: CADDIE_PLAYWRIGHT, CADDIE_CHROMIUM
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.CADDIE_PLAYWRIGHT || 'playwright-core');
const root = __dirname;
const deckIndex = 'file://' + path.join(root, '..', 'presentation-v4.1', 'index.html');
const out = process.env.CADDIE_SHOTS || '/tmp/caddie-sprechtext-v4.1';
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CADDIE_CHROMIUM, args: ['--no-sandbox'] });
  try {
    // 1. Abgleich mit dem Deck
    const manu = JSON.parse(fs.readFileSync(path.join(root, 'Manuskript.json'), 'utf8'));
    const deckPage = await browser.newPage();
    await deckPage.goto(deckIndex);
    await deckPage.waitForFunction(() => window.Deck && Deck.current && Deck.current());
    const deck = await deckPage.evaluate(() => Deck.list().map((s, i) => { Deck.go(i, 0); return { id: s.id, backup: !!s.backup, steps: Deck.current().ctx._stops.length }; }));
    const problems = [];
    const byScene = Object.fromEntries(manu.slides.map((s) => [s.scene, s]));
    deck.forEach((d) => {
      const s = byScene[d.id];
      if (!s) problems.push(`Szene ${d.id}: kein Sprechtext`);
      else if (s.steps.length !== d.steps) problems.push(`Szene ${d.id}: Deck ${d.steps} Schritte, Manuskript ${s.steps.length}`);
    });
    manu.slides.forEach((s) => { if (!deck.find((d) => d.id === s.scene)) problems.push(`${s.title}: Szene ${s.scene} fehlt im Deck`); });
    const mainOrder = deck.filter((d) => !d.backup).map((d) => d.id);
    const manuOrder = manu.slides.filter((s) => s.title.startsWith('Folie')).map((s) => s.scene);
    if (mainOrder.join() !== manuOrder.join()) problems.push('Reihenfolge Deck ≠ Manuskript');
    if (problems.length) throw new Error('Abgleich Deck/Manuskript:\n' + problems.join('\n'));
    await deckPage.close();

    // 2. PDFs
    const page = await browser.newPage();
    const errors = []; page.on('pageerror', (e) => errors.push(e.message));
    await page.emulateMedia({ media: 'print' });
    await page.goto('file://' + path.join(root, 'Sprechtext.html'));
    await page.waitForFunction(() => window.manuscriptReady);
    await page.evaluate(() => document.fonts.ready);
    const checks = await page.evaluate(() => window.layoutResult);
    if (errors.length || checks.some((c) => !c.fits || !c.footerClear)) throw new Error(JSON.stringify({ errors, checks }));
    await page.pdf({ path: path.join(root, 'Caddie-Sprechtext-v4.1.pdf'), preferCSSPageSize: true, printBackground: true, tagged: true, outline: true });
    fs.mkdirSync(out, { recursive: true });
    for (const i of [0, 1, 2, 3, 12]) await page.locator('.sheet').nth(i).screenshot({ path: path.join(out, 'seite-' + (i + 1) + '.png') });
    await page.goto('file://' + path.join(root, 'Spickzettel.html'));
    const compact = await page.locator('.sheet').evaluateAll((sheets) => sheets.map((s, i) => ({ page: i + 1, clear: [...s.querySelectorAll('.card')].every((c) => c.getBoundingClientRect().bottom < s.querySelector('.foot').getBoundingClientRect().top) })));
    if (compact.some((c) => !c.clear)) throw new Error('Spickzettel läuft über: ' + JSON.stringify(compact));
    await page.pdf({ path: path.join(root, 'Caddie-Spickzettel-v4.1.pdf'), preferCSSPageSize: true, printBackground: true, tagged: true, outline: true });
    await page.locator('.sheet').nth(0).screenshot({ path: path.join(out, 'spickzettel.png') });
    console.log(JSON.stringify({ deckScenes: deck.length, scriptPages: checks.length, compactPages: compact.length, errors, allPagesFit: true }));
  } finally { await browser.close(); }
})().catch((e) => { console.error(e.message || e); process.exit(1); });

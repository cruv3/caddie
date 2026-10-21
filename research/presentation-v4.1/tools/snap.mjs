// Screenshots einzelner Szenen/Schritte, zum Prüfen ohne Klicken.
// Aufruf: node tools/snap.mjs [ausgabeordner] szene/schritt [szene/schritt …]
//   Ohne Szenen: alle Szenen, jeder Schritt.
// Umgebungsvariablen: CADDIE_PLAYWRIGHT (Pfad zu playwright-core), CADDIE_CHROMIUM (Chrome-Binary)
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const pw = require(process.env.CADDIE_PLAYWRIGHT || 'playwright-core');
const here = path.dirname(fileURLToPath(import.meta.url));
const index = pathToFileURL(path.join(here, '..', 'index.html')).href;
const out = process.argv[2] || '/tmp/caddie-v4.1-shots';
let targets = process.argv.slice(3);
fs.mkdirSync(out, { recursive: true });

const browser = await pw.chromium.launch({ executablePath: process.env.CADDIE_CHROMIUM, args: ['--autoplay-policy=no-user-gesture-required'] });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') errors.push(`${m.type()}: ${m.text()}`); });

await page.goto(index);
await page.waitForFunction(() => window.Deck && Deck.current && Deck.current());
if (!targets.length) {
  const info = await page.evaluate(async () => {
    const res = [];
    for (let i = 0; i < Deck.list().length; i++) { Deck.go(i, 0); res.push({ id: Deck.list()[i].id, steps: Deck.current().ctx._stops.length }); }
    return res;
  });
  targets = info.flatMap((s) => [...Array(s.steps).keys()].map((k) => `${s.id}/${k}`));
}
async function shoot(t) {
  const [id, step] = t.split('/');
  await page.evaluate(([id, step]) => {
    const i = Deck.list().findIndex((s) => s.id === id);
    if (+step === 0) Deck.go(i, 0); else Deck.go(i, +step);
  }, [id, step || '0']);
  await page.waitForTimeout(+step === 0 ? 4200 : 1800);
  await page.screenshot({ path: path.join(out, `${id}-${step || 0}.png`), timeout: 20000 });
}
for (const t of targets) {
  try {
    await shoot(t);
  } catch (e) {
    // Hängender Screenshot (z. B. Videodekodierung): Seite neu laden, einmal wiederholen.
    errors.push(`Wiederholung ${t}: ${e.message.split('\n')[0]}`);
    await page.goto(index);
    await page.waitForFunction(() => window.Deck && Deck.current && Deck.current());
    await shoot(t);
  }
}
console.log(errors.length ? errors.join('\n') : 'keine Konsolenfehler');
await browser.close();

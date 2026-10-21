"""Erzeugt aus Manuskript.md: script.js für die Präsentation v4.1 (Sprecheransicht),
Sprechtext.html (A5-PDF), Spickzettel.html (A4-PDF) und Manuskript.json.
Eine Quelle für alles: Änderungen nur in Manuskript.md, danach build.py und export.cjs ausführen.

Format je Folie:
  ## Folie NN · Titel   bzw.   ## Backup BN · Titel
  Szene: <id aus scenes/*.js>
  Zeitfenster: …
  Anker: …
  Quelle: …
  Absätze. [Regie] in eckigen Klammern. [Klick N · Label] beginnt Schritt N der Szene.
"""
from pathlib import Path
import json
import re
import html

ROOT = Path(__file__).resolve().parent
DECK = ROOT.parent / 'presentation-v4.1'
source = (ROOT / 'Manuskript.md').read_text()
KLICK = re.compile(r'^\[Klick (\d+)(?: · (.*?))?\]$')
slides = []
for part in re.split(r'^## ', source, flags=re.M)[1:]:
    lines = part.strip().splitlines()
    title = lines[0]
    meta = dict(line.split(': ', 1) for line in lines[1:5])
    assert set(meta) == {'Szene', 'Zeitfenster', 'Anker', 'Quelle'}, (title, meta.keys())
    blocks = []
    steps = [{'label': '', 'blocks': []}]
    for para in re.split(r'\n\s*\n', '\n'.join(lines[5:]).strip()):
        para = para.strip()
        if not para:
            continue
        kind = 'cue' if para.startswith('[') else 'speech'
        blocks.append({'kind': kind, 'text': para})
        k = KLICK.match(para)
        if k:
            n = int(k.group(1))
            assert n == len(steps), f'{title}: Klick {n} erwartet {len(steps)}'
            steps.append({'label': k.group(2) or '', 'blocks': []})
        else:
            steps[-1]['blocks'].append({'kind': kind, 'text': para})
    slides.append({'title': title, 'scene': meta['Szene'], 'time': meta['Zeitfenster'], 'anchor': meta['Anker'],
                   'source': meta['Quelle'], 'blocks': blocks, 'steps': steps})

main = [s for s in slides if s['title'].startswith('Folie ')]
backups = [s for s in slides if s['title'].startswith('Backup ')]
assert len(main) + len(backups) == len(slides)
assert [s['title'].split(' · ')[0] for s in main] == [f'Folie {i:02d}' for i in range(1, len(main) + 1)]
assert [s['title'].split(' · ')[0] for s in backups] == [f'Backup B{i}' for i in range(1, len(backups) + 1)]
assert len({s['scene'] for s in slides}) == len(slides), 'Szene doppelt'
words = sum(len(b['text'].split()) for s in main for b in s['blocks'] if b['kind'] == 'speech')
(ROOT / 'Manuskript.json').write_text(json.dumps({'spoken_words': words, 'slides': slides}, ensure_ascii=False, indent=2))

script_data = {s['scene']: {'title': s['title'], 'time': s['time'], 'anchor': s['anchor'], 'source': s['source'], 'steps': s['steps']} for s in slides}
(DECK / 'script.js').write_text(
    '/* ERZEUGT von research/sprechtext-v4.1/build.py aus Manuskript.md. Nicht von Hand bearbeiten. */\n'
    'Deck.script = ' + json.dumps(script_data, ensure_ascii=False, indent=1) + ';\n')

style = '''
@page { size: A5; margin: 0; }
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body { font-family: 'Liberation Sans', Arial, sans-serif; color: #172620; }
.sheet { width: 148mm; height: 210mm; padding: 11mm 12mm 15mm; position: relative; break-after: page; }
.sheet:last-child { break-after: auto; }
.flow { height: 174mm; }
.eyebrow { color: #527468; font-size: 8.5pt; line-height: 1.3; font-weight: bold; letter-spacing: .7px; text-transform: uppercase; margin-bottom: 6px; }
h1 { font-size: 22pt; line-height: 1.12; margin: 8px 0 15px; }
h2 { font-size: 18pt; line-height: 1.17; margin: 0 0 10px; }
.meta { font-size: 9pt; color: #637268; margin: 0 0 12px; line-height: 1.35; }
.anchor { border-top: 1px solid #d4dfd7; border-bottom: 1px solid #d4dfd7; font-size: 10pt; line-height: 1.35; padding: 7px 0; margin-bottom: 12px; }
p { font-size: 13pt; line-height: 1.38; margin: 0 0 10px; }
.cue { font-size: 10pt; line-height: 1.35; color: #4d645a; background: #edf4ef; border-left: 3px solid #7b9d89; padding: 7px 9px; margin: 0 0 9px; }
.group { display: flow-root; }
.footer { position: absolute; bottom: 7mm; left: 12mm; right: 12mm; font-size: 8pt; color: #6a766f; display: flex; justify-content: space-between; border-top: 1px solid #d9e1dc; padding-top: 5px; }
.guide p { font-size: 12pt; line-height: 1.45; }
.toc { display: grid; grid-template-columns: 1fr 1fr; column-gap: 15px; }
.toc a { display: block; font-size: 7.6pt; line-height: 1.22; margin-bottom: 3.5px; color: #254838; text-decoration: none; }
.toc a small { color: #65746b; }
.toc-title { font-size: 14pt; margin: 12px 0 8px; }
@media screen { body { background: #dbe2de; } .sheet { background: white; margin: 14px auto; box-shadow: 0 2px 12px #0002; } }
'''

script = r'''
const DATA = JSON.parse(document.getElementById('data').textContent); const slides = DATA.slides; const COUNTS = DATA.counts; const PLAN = DATA.plan;
let sheets = [];
function sheet(label) {
  const el = document.createElement('section'); el.className = 'sheet';
  const flow = document.createElement('div'); flow.className = 'flow'; el.append(flow);
  const footer = document.createElement('div'); footer.className = 'footer';
  const left = document.createElement('span'); left.textContent = label;
  const count = document.createElement('span'); count.className = 'page-number';
  footer.append(left, count); el.append(footer); document.body.append(el); sheets.push(el);
  return flow;
}
function text(tag, value, className) { const el = document.createElement(tag); el.textContent = value; if(className) el.className=className; return el; }
function fits(flow) {
  return [...flow.children].every(el => el.getBoundingClientRect().bottom <= flow.getBoundingClientRect().bottom + .1);
}
function start(slide, n, continued) {
  const flow = sheet(slide.title.split(' · ')[0] + ' · Shared Autonomy');
  flow.append(text('div', 'Caddie · Sprechtext · v4.1', 'eyebrow'));
  flow.append(text('h2', slide.title + (continued ? ' · Fortsetzung' : '')));
  if(!continued) {
    flow.id = 'folie-'+n;
    flow.append(text('div', 'Richtwert: '+slide.time, 'meta'));
    flow.append(text('div', 'Merken: '+slide.anchor, 'anchor'));
  }
  return flow;
}
const cover = sheet('Sprechtext · Anleitung'); cover.classList.add('guide');
cover.append(text('div', 'Andreas Schurawlev · TH Köln', 'eyebrow'));
cover.append(text('h1', 'Shared Autonomy\nSprechtext zum Üben'));
cover.append(text('p', COUNTS + ' Locker gesprochen, mit Klicks, Pausen und Übergängen.'));
cover.append(text('div', 'Plan: ' + PLAN + ' Minuten für den Hauptvortrag (Summe der Zeitfenster). Die Backups gehören nur zur Fragerunde.', 'cue'));
cover.append(text('p', 'Die normalen Absätze kannst du so sprechen. Eckige Klammern enthalten Regiehinweise; sie werden nicht vorgelesen. „Klick N“ ist der N-te Weiter-Klick auf der Folie. Derselbe Text steht in der Sprecheransicht (Taste P), dort Schritt für Schritt.'));
cover.append(text('p', 'Humorvolle Sätze sind optional. Kurz lächeln und weiterreden; du brauchst keine Reaktion abzuwarten. Es soll nach dir klingen.'));
cover.append(text('p', 'Erste Probe: mit Präsentation und Timer laut sprechen. Zweite Probe: nur auf die Merkwörter schauen. Bei Zahlen und Einschränkungen genau bleiben; den übrigen Wortlaut darfst du verändern.'));
cover.append(text('p', 'Die Zeitfenster sind Planwerte auf Grundlage des Decks, keine gemessenen Vortragszeiten. Videos und Publikumsfragen brauchen echte Pausen.'));
const toc = sheet('Sprechtext · Übersicht');
toc.append(text('div', 'Direkt zur Folie', 'eyebrow'));
toc.append(text('h2', 'Die Reihenfolge'));
const grid = document.createElement('div'); grid.className = 'toc'; toc.append(grid);
const tocColumns = [document.createElement('div'), document.createElement('div')]; grid.append(...tocColumns);
slides.forEach((s, i) => { const a=document.createElement('a'); a.href='#folie-'+i; a.dataset.slide=String(i); a.textContent=s.title; tocColumns[i<Math.ceil(slides.length/2)?0:1].append(a); });
slides.forEach((s, n) => {
  let flow = start(s, n, false);
  const groups=[];
  for(let i=0;i<s.blocks.length;i++) {
    const g=document.createElement('div'); g.className='group';
    const b=s.blocks[i]; g.append(text(b.kind==='cue'?'div':'p', b.text, b.kind==='cue'?'cue':null));
    if(b.kind==='cue' && s.blocks[i+1]?.kind==='speech') { const next=s.blocks[++i]; g.append(text('p',next.text)); }
    groups.push(g);
  }
  if(groups.length>1 && s.blocks.at(-1).kind==='cue') { const last=groups.pop(); groups.at(-1).append(...last.children); }
  for(const g of groups) {
    flow.append(g);
    if(!fits(flow)) { g.remove(); flow=start(s,n,true); flow.append(g); if(!fits(flow)) throw new Error('Zu großer Absatz: '+s.title); }
  }
});
sheets.forEach((s,i)=>s.querySelector('.page-number').textContent=(i+1)+' / '+sheets.length);
document.querySelectorAll('.toc a').forEach(a=>{ const dest=document.getElementById('folie-'+a.dataset.slide); const number=sheets.indexOf(dest.closest('.sheet'))+1; const small=text('small',' · S. '+number); a.append(small); });
window.layoutResult = sheets.map((s, i) => ({page:i+1, label:s.querySelector('.footer span').textContent, fits:fits(s.querySelector('.flow')), footerClear:s.querySelector('.flow').getBoundingClientRect().bottom<s.querySelector('.footer').getBoundingClientRect().top}));
window.manuscriptReady = true;
'''
plan = '{}:{:02d}'.format(*divmod(sum(int(m) * 60 + int(s) for x in main for m, s in [x['time'].split(' ')[0].split(':')]), 60))
data = json.dumps({'slides': [{k: v for k, v in x.items() if k != 'steps'} for x in slides], 'counts': f'{len(main)} Hauptfolien und {len(backups)} Backups.', 'plan': plan}, ensure_ascii=False).replace('</', '<\\/')
full = '<!doctype html><html lang="de"><meta charset="utf-8"><title>Caddie – Sprechtext v4.1</title><style>'+style+'</style><body><script type="application/json" id="data">'+data+'</script><script>'+script+'</script></body></html>'
(ROOT / 'Sprechtext.html').write_text(full)

compact_style = '''
@page { size: A4; margin: 0; }
* { box-sizing: border-box; }
body { margin: 0; color: #172620; font-family: 'Liberation Sans', Arial, sans-serif; }
.sheet { width: 210mm; height: 297mm; padding: 15mm 16mm; break-after: page; position: relative; }
.sheet:last-child { break-after: auto; }
h1 { font-size: 19pt; margin: 0 0 5px; }
.sub { font-size: 10pt; color: #527468; margin: 0 0 14px; }
.card { border-top: 1px solid #cbdad0; padding: 10px 0; }
h2 { font-size: 12pt; margin: 0 0 4px; }
p { font-size: 11pt; margin: 0; line-height: 1.4; }
.foot { position: absolute; bottom: 10mm; left: 16mm; font-size: 9pt; color: #617269; }
'''
compact_pages = [main[i:i+7] for i in range(0, len(main), 7)] + [backups[i:i+7] for i in range(0, len(backups), 7)]
main_pages = (len(main) + 6) // 7
out = ['<!doctype html><html lang="de"><meta charset="utf-8"><title>Caddie – Spickzettel v4.1</title><style>'+compact_style+'</style>']
for i, page in enumerate(compact_pages):
    out.append('<section class="sheet"><h1>'+('Spickzettel' if i<main_pages else 'Backups für Rückfragen')+'</h1><div class="sub">Shared Autonomy · v4.1 · Anker für das freie Sprechen</div>')
    for s in page:
        out.append('<div class="card"><h2>'+html.escape(s['title'])+'</h2><p>'+html.escape(s['anchor'])+'</p></div>')
    out.append(f'<div class="foot">{i+1} / {len(compact_pages)} · G öffnet die Folienübersicht; F das Vollbild.</div></section>')
out.append('</html>')
(ROOT / 'Spickzettel.html').write_text(''.join(out))
(ROOT / 'Sprechtext.md').write_text(source)
print(json.dumps({'main':len(main),'backups':len(backups),'spoken_words_main':words,'compact_pages':len(compact_pages)},ensure_ascii=False))

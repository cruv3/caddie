# Research notes — workflow

One file per paper. Strict separation between **direct quotes** and **paraphrases** to prevent notes-drift (the most common cause of accidental plagiarism in a thesis).

## Workflow

```
Paper PDF  →  notes/<paper-id>.md  →  Thesis draft
            (read once, fill once)  (cite only from here)
```

**When writing the thesis later, you should NEVER quote or paraphrase from the PDF directly.** Always go through your notes file. The mental translation step "PDF → my words in notes" is the actual plagiarism shield; if you skip it, your draft will absorb the source phrasing verbatim because your brain remembers the exact words you read four months ago.

## Per-paper file structure

Every paper file uses the same sections. Empty sections stay (so you notice they're empty later). See `_TEMPLATE.md`.

1. **Metadata** — full citation, link, where the PDF lives locally, when you first read it.
2. **TL;DR** — 3–4 lines in your own words, written *after* you've read the paper, before the detail notes. If you can't summarize it in 3 lines, you haven't read it carefully enough.
3. **Direct quotes** — verbatim from the paper. Always with page/section. Always `> "..."` formatted. **Never** mix paraphrase prose into the same block.
4. **Paraphrases / my notes** — what you understood, in your own words. Each block tagged with `(source: <paper-id> §X p.Y)` so you can verify later.
5. **Key concepts/terms** — short glossary if the paper introduces named concepts you'll cite (e.g. "Bellotti & Edwards principles", "in-context learning").
6. **How I plan to use this in the thesis** — which thesis section, what role (theoretical anchor / comparator / counter-example / benchmark).

## When you sit down to write the thesis

1. Open your notes file.
2. Copy paraphrases into the draft.
3. Copy direct quotes into the draft *as quotes*, with the page number already attached.
4. Never open the PDF again unless you need to verify something you already noted.

If a fact in the draft has no corresponding line in any notes file → you don't have a source for it. Either find the source or remove the claim.

## File naming convention

`<lastname-or-shortname><year>.md`, lowercase, hyphenated. Examples:
- `hardian2006.md`
- `appagent.md` (when the system name is more recognizable than the author)
- `androidworld.md`
- `plan-then-execute.md`

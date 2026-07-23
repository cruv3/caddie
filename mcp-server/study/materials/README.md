# Study Paper Materials

This directory contains paper materials for the Caddie Shared Autonomy study
(Shared Autonomy – Nutzeraufsicht bei LLM-basierten Smartphone-Agenten).

## Materials Overview

| File | Description |
|------|-------------|
| `01_einwilligungserklaerung.md` | Informed consent form (German, print-ready) |
| `02_einfuehrung_und_training.md` | Participant introduction & standardized training script |
| `03_aufgabenkarten.md` | Six participant-facing task instructions without condition or error information |
| `04_fragebogen_pro_bedingung.md` | Task-level ratings plus per-condition Raw NASA-TLX, TiA, TAM PU/PEOU, control items and free-text |
| `05_tam_fragebogen_am_studienende.md` | Deprecated pointer; no separate end-of-study TAM |
| `06_abschlussinterview.md` | Semi-structured exit interview guide |
| `07_debriefing.md` | Full debriefing: controlled errors, conditions, data summary, source references |
| `08_er experimenter-laufzettel.md` | Experimenter run sheet: step-by-step with timing, phase gates, C4 optional |
| `09_reset_checkliste_pro_aufgabe.md` | Per-task reset checklists (6 tasks × reset steps) + general reset |
| `10_pilot_checkliste_und_zeitmessung.md` | Pilot checklist: timing table, material validation, C4 decision, summary template |
| `11_c4_bildschirm_aus.md` | Optional C4: one delayed train-status task and three between-participant initiation modes |

## Legacy Files (replaced)

| File | Replaced by |
|------|-------------|
| `01_experiment_checklist.md` | `08_er experimenter-laufzettel.md` + `09_reset_checkliste_pro_aufgabe.md` |
| `02_consent_form.md` | `01_einwilligungserklaerung.md` |
| `03_task_cards.md` | `03_aufgabenkarten.md` |
| `04_tam_questionnaire.md` | `04_fragebogen_pro_bedingung.md` + `05_tam_fragebogen_am_studienende.md` |

## Study Design Reference

- Full study design: `../../docs/dossier/05-studiendesign.md`
- Task specs (YAML): `../specs/task_*.yaml`
- Counterbalancing matrix: `../../caddie/study/matrix.py`

## Print Instructions

1. Print the consent, introduction, participant task cards, authoritative questionnaire, exit interview and debriefing as required.
2. Do not print either deprecated TAM reference file.
3. Print `08_*` (run sheet) and `09_*` (reset list) only for the experimenter.
4. Print `10_*` (pilot sheet) for pilot sessions.
5. Print `11_*` (C4) only if the pilot confirms feasibility.

## German Study Properties

- **Gerät:** Vorbereitetes Studien-Smartphone (kein persönliches Telefon)
- **Daten:** Studiendaten (Mock-Daten), keine echten Konten
- **Banking:** Kein echtes Geld
- **Dauer:** 60–75 Minuten (Maximal 90 Min. nach Pilot)
- **Aufgaben:** 6 Hauptaufgaben in 3 Paaren, jede genau einmal
- **Bedingungen:** C1 (schrittweise), C2 (finaler Checkpoint), C3 (freiwilliger Eingriff)
- **Anreiz:** 0,3 Notenverbesserung für berechtigte Kursteilnehmende (unabhängig von der Aufgabenleistung)
- **Sprache:** Deutsch (alle Teilnehmermaterialien)

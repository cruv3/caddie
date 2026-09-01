# Model-trial screenshot evidence

This directory preserves screenshots recovered after the evaluations and
associated by trial identifier with the two manual classifications reported in
`../failure-mode-log.md` and `../claude-vs-local.md`.

The tests were conducted during development with the Python/ADB setup that
predated Caddie's Android-owned runtime. Preserving their evidence improves
auditability, but does not turn their recorded success counts into a benchmark
of the current implementation.

## Coverage

- Self-hosted candidate matrix: 67 of 72 run screenshots and their matching
  result JSON files are retained.
- Claude Opus 4.7 reference: all 12 run screenshots and matching result JSON
  files are retained.
- Two additional self-hosted capture files contain an Accessibility-service
  error response rather than an image: `qwen/qwen3-vl-8b` for `dark_mode_off`
  run 0 and `pixtral-12b` for `dark_mode_on` run 1. Their error payloads and
  result JSON files are retained.
- Three self-hosted slots were not retained at all: `gemma-4-e2b-it` for
  `dark_mode_on` run 1 and `dark_mode_off` runs 0 and 1. No matching screenshot
  or result JSON file was found for those three slots.

The screenshot files were recovered from the local, Git-ignored
`mcp-server/screenshots/trials/` directory. They were not present in any
reachable Caddie commit before this evidence package was prepared, and no
contemporaneous screenshot hashes are available. Matching filenames and trial
identifiers associate the recovered files with the retained result records;
the SHA-256 hashes below establish integrity only from the time this package
was assembled. Original filenames are preserved. The 12 Claude result JSON
files were restored from immutable Caddie commit
`c2d4bc06e3eef64639bc4043a83ed08a792fbb8d`; the self-hosted result JSON files
were already retained in the public release.

## Manifest

`manifest.csv` contains one row for every planned matrix or reference slot. An
available row records the model, task, run, timestamp prefix of the original
trial identifier, relative
screenshot path, SHA-256 digest, optional capture-error path, and matching result
JSON path. Failed captures use status `capture_failed`; unavailable rows use
status `not_retained`.

The script `verify_manifest.py` was written for this evidence package to check
all 84 expected model-by-task-by-run slots, their uniqueness, agreement with
the result JSON files, screenshot hashes, PNG structure and decompression, and
the explicitly documented missing or failed captures.

The success and failure labels remain author-created manual classifications.
They were not independently or blindly annotated. The preserved screenshots
allow the available labels to be inspected again, but do not remove that
methodological limitation.

## Privacy review

All 79 PNG files were visually reviewed on 2026-09-01 before the package was
prepared for publication. They contain no signed-in account, participant name,
message content, email address, phone number, saved credential, or participant
notification. One screenshot shows emulator system notifications only. The
pizza-search screenshots visibly show the coarse test area `51643 Gummersbach`
and public business results; they do not show a precise coordinate or
participant data. This location disclosure is retained because it is part of
the evaluated local-search result. The PNG files contain no EXIF fields.

Run the deterministic integrity check from the repository root:

```text
python -B research/evaluation/model-trial-screenshots/verify_manifest.py
```

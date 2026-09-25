# Research artifacts

Normal Caddie use requires only the Android app and a compatible model endpoint.
This directory retains the parts of the master's-thesis apparatus and technical
evaluation that are useful for inspecting the reported work without publishing
participant-facing or moderator materials.

- [Final master's thesis (PDF)](Finale-Masterarbeit.pdf) documents Caddie and
  its evaluation.
- `fixtures/` contains eight synthetic Android applications used to provide
  resettable study states. They are excluded from normal builds.
- `study/specs/` contains the six machine-readable task specifications used by
  the study runtime.
- `evaluation/` contains selected historical reports, raw trial records, and
  the small aggregation script used by Chapter 3.

The evaluation records predate the current Android-owned runtime and must not be
read as a benchmark of the released app. Obsolete host/ADB implementation code,
participant-facing and moderator materials, development diaries, migration
reports, literature notes, internal review documents, and non-retained
comparison runs are intentionally not part of this release.

Absolute user-home prefixes in retained JSON metadata were replaced with
`<user-home>`. Prompts, model outputs, classifications, timings, and reported
outcomes were not changed.

## Building the study fixtures

The fixture modules are opt-in:

```text
./gradlew -PincludeStudyFixtures=true :app:assembleStudyDebug \
  :research:fixtures:study-bank:assembleDebug \
  :research:fixtures:study-calendar:assembleDebug \
  :research:fixtures:study-mail:assembleDebug \
  :research:fixtures:study-telegram:assembleDebug \
  :research:fixtures:study-gallery:assembleDebug \
  :research:fixtures:study-notes:assembleDebug \
  :research:fixtures:study-music:assembleDebug \
  :research:fixtures:study-training-sandbox:assembleDebug
```

On Windows, `scripts/install-study-fixtures.ps1 -IncludeStudyFixtures` builds
and installs the fixtures on a controlled device.

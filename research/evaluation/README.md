# Historical technical evaluation

These artifacts support the bounded technical results reported in Chapter 3 of
the thesis. They were produced by earlier host/ADB versions of Caddie, not by the
current Android-owned runtime.

- `failure-mode-log.md` retains the manually classified failure patterns.
- `results/` contains raw trial JSON and the retained aggregate reports for the
  model, retrieval, and execution-strategy comparisons.
- `protocols/` contains the small task and retrieval datasets associated with
  those reports.
- `device-validation/` retains the two reports underlying Chapter 3's Android
  observation and native study-path checks. Their captures and export ZIP are
  not included.
- `evaluator.py` regenerates `results/SUMMARY.md` from the retained JSON trials.

The historical execution harness is not included. The reports can therefore be
inspected and re-aggregated, but the original environment cannot be rerun from
this release alone.

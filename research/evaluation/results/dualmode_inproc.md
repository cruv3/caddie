# Dual-mode eval (in-process) — fast (deep-link) vs observable (UI)

device=dedicated test Pixel, reps=2, replay OFF, after the session's fixes.

| task | obs ok | obs turns | obs s | fast ok | fast turns | fast s |
|---|---|---|---|---|---|---|
| display | 2/2 | 2.0 | 9 | 2/2 | 2.0 | 7 |
| sound | 2/2 | 5.5 | 16 | 2/2 | 2.0 | 8 |
| notifications | 2/2 | 2.0 | 9 | 1/2 | 2.0 | 6 |
| accessibility | 2/2 | 3.0 | 8 | 2/2 | 2.0 | 9 |
| storage | 2/2 | 11.0 | 42 | 2/2 | 13.5 | 50 |
| battery_saver | 2/2 | 13.0 | 58 | 2/2 | 8.0 | 31 |
| date | 2/2 | 6.5 | 26 | 2/2 | 2.0 | 8 |
| language | 1/2 | 3.5 | 9 | 2/2 | 2.0 | 7 |

**Totals:** observable 15/16 ok, 5.8 avg turns, 22s avg · fast 15/16 ok, 4.2 avg turns, 16s avg
**Delta (fast vs observable):** turns -28%, time -29%

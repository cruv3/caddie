# Dual-mode eval — fast (deep-link) vs observable (UI)

Real phone (Pixel, German), 8 settings tasks, replay OFF, reps=1.

| task | obs outcome | obs turns | obs s | fast outcome | fast turns | fast s |
|---|---|---|---|---|---|---|
| display | done | 8 | 41 | done | 2 | 15 |
| sound | done | 6 | 32 | done | 3 | 20 |
| notifications | done | 16 | 59 | done | 3 | 13 |
| accessibility | fail_loop | 26 | 103 | done | 2 | 8 |
| storage | done | 18 | 75 | done | 12 | 50 |
| battery_saver | done | 13 | 55 | done | 5 | 29 |
| date | done | 11 | 59 | done | 2 | 13 |
| language | done | 10 | 48 | done | 2 | 8 |

**Totals:** observable 7/8 done, 13.5 avg turns, 59s avg · fast 8/8 done, 3.9 avg turns, 19s avg.
**Delta (fast vs observable): turns -71%, time -67%, success +12pp.**
Fast rescued accessibility (observable fail_loop 103s -> fast done 8s). Only storage stayed
moderately slow in fast (deep-link opens Storage but the "how much used" sub-read needs UI).

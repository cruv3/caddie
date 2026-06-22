"""Phase 1a: measure index-build + startup latency with the real skill set.
Offline, no live path, no device."""
from __future__ import annotations

import sys
import time
from pathlib import Path

from caddie.memory import Embedder, MemoryIndex
from caddie.skills import SkillLibrary

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    lib = SkillLibrary.load(ROOT / "skills")
    embedder = Embedder()
    t0 = time.perf_counter(); embedder.encode(["cold start warmup"]); cold = time.perf_counter() - t0
    t0 = time.perf_counter(); idx = MemoryIndex.build(lib, embedder, threshold=0.55)
    build = time.perf_counter() - t0
    t0 = time.perf_counter(); idx.match("stelle die helligkeit auf 50 prozent"); q = (time.perf_counter() - t0) * 1000
    n = len(lib.all())
    report = (f"# Phase 1a — Index/Startup-Latenz\n\n"
              f"- Skills im Index: {n}\n"
              f"- Cold-Start (erstes encode): {cold:.2f} s\n"
              f"- Index-Build ({n} Skills): {build*1000:.0f} ms  (Budget ≤ 2000 ms)\n"
              f"- Beispiel-Query-Latenz: {q:.1f} ms  (Budget p95 ≤ 200 ms)\n")
    sys.stdout.reconfigure(encoding='utf-8')
    print(report)
    out = ROOT / "experiments" / "results" / "phase1a_index_latency.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

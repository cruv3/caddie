# User-Initiated Study Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let participants initiate an armed deterministic study trial through the ordinary Caddie voice/text request while unmatched input receives a retry prompt and normal Caddie remains unchanged when no trial is armed.

**Architecture:** Add trigger metadata to immutable trial specs, a pure deterministic matcher, and a lock-protected study coordinator that owns `ARMED -> RUNNING -> terminal` transitions. Both `/task` and `/task/stream` consult the coordinator before the normal agent; the existing `TrialExecutor` remains the only Android replay engine.

**Tech Stack:** Python 3, dataclasses/enums, `ThreadingHTTPServer`, pytest, YAML, existing EventBus/AgentLoop/TrialExecutor.

---

### Task 1: Add validated trigger contracts to trial specs

**Files:**
- Modify: `mcp-server/caddie/study/model.py`
- Modify: `mcp-server/caddie/study/spec_loader.py`
- Modify: `mcp-server/tests/test_study_spec_loader.py`
- Modify: active YAML files under `mcp-server/study/specs/`

- [ ] **Step 1: Write failing schema tests**

Add tests proving a trigger requires non-empty `required_concepts`, each concept group has at least one synonym, reference phrases are strings, and unknown trigger keys fail closed.

```python
assert spec.trigger.required_concepts == (
    ("prüfung", "pruefung", "exam"),
    ("kalender", "termin"),
    ("nicht stören", "nicht stoeren", "dnd"),
)
```

- [ ] **Step 2: Run the new loader tests red**

Run: `python -m pytest mcp-server/tests/test_study_spec_loader.py -q`

Expected: FAIL because `TriggerContract` and the `trigger` YAML key do not exist.

- [ ] **Step 3: Add immutable trigger types**

```python
@dataclass(frozen=True)
class TriggerContract:
    reference_phrases: tuple[str, ...]
    required_concepts: tuple[tuple[str, ...], ...]
    forbidden_concepts: tuple[str, ...] = ()
    wake_words: tuple[str, ...] = ("jarvis", "caddie")

@dataclass(frozen=True)
class TrialSpec:
    # existing fields remain unchanged
    trigger: TriggerContract | None = None
```

- [ ] **Step 4: Parse and validate the exact YAML shape**

```yaml
trigger:
  reference_phrases:
    - "Jarvis, finde die Prüfung morgen im Kalender und aktiviere Nicht stören für diese Zeit"
  required_concepts:
    - ["prüfung", "pruefung", "exam"]
    - ["kalender", "termin"]
    - ["nicht stören", "nicht stoeren", "dnd"]
  forbidden_concepts: ["überweisung", "ueberweisung", "fotos"]
  wake_words: ["jarvis", "caddie"]
```

Add `trigger` to `_TRIAL_SPEC_KEYS`; reject missing trigger contracts for active study specs.

- [ ] **Step 5: Add audited trigger contracts to every task that can appear in the six-trial participant matrix**

Use each spec's `instruction_de` as a reference phrase, plus task-specific concept groups and forbidden concepts. Do not use one catch-all trigger or free-form model classification.

- [ ] **Step 6: Run loader and matrix tests**

Run: `python -m pytest mcp-server/tests/test_study_spec_loader.py mcp-server/tests/test_study_matrix.py -q`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mcp-server/caddie/study/model.py mcp-server/caddie/study/spec_loader.py mcp-server/tests/test_study_spec_loader.py mcp-server/study/specs
git commit -m "feat: define deterministic study triggers"
```

### Task 2: Implement pure deterministic matching

**Files:**
- Create: `mcp-server/caddie/study/routing.py`
- Create: `mcp-server/tests/test_study_routing.py`

- [ ] **Step 1: Write failing normalization and matching tests**

Cover lowercasing, punctuation, whitespace, optional wake-word prefix, `ä/ae`, `ö/oe`, `ü/ue`, `ß/ss`, required synonym groups, forbidden concepts, missing concepts, and matching only the supplied armed spec.

```python
result = match_task(
    "Jarvis, finde die Pruefung im Kalender und aktiviere Nicht-Stoeren",
    calendar_dnd_spec.trigger,
)
assert result.matched is True
assert result.missing_concepts == ()
```

- [ ] **Step 2: Run routing tests red**

Run: `python -m pytest mcp-server/tests/test_study_routing.py -q`

Expected: import failure for `caddie.study.routing`.

- [ ] **Step 3: Implement normalization and structured results**

```python
class RouteDecision(StrEnum):
    PASS_THROUGH = "pass_through"
    RETRY = "retry"
    CLAIMED = "claimed"

@dataclass(frozen=True)
class MatchResult:
    matched: bool
    normalized_input: str
    matched_concepts: tuple[str, ...]
    missing_concepts: tuple[tuple[str, ...], ...]
    forbidden_matches: tuple[str, ...]
    reason: str
```

Normalize with `unicodedata.normalize("NFKC", text).casefold()`, explicit German ASCII equivalence, punctuation-to-space, and whitespace collapse. Compare padded normalized phrases so short tokens do not match inside unrelated words.

- [ ] **Step 4: Implement `StudyTaskRouter.route(text, armed_spec)`**

Return `PASS_THROUGH` only when `armed_spec is None`; return `RETRY` for any non-match while armed; return `CLAIMED` for a match. The router performs no Android action.

- [ ] **Step 5: Run focused tests and commit**

Run: `python -m pytest mcp-server/tests/test_study_routing.py -q`

Expected: PASS.

```bash
git add mcp-server/caddie/study/routing.py mcp-server/tests/test_study_routing.py
git commit -m "feat: match armed study requests deterministically"
```

### Task 3: Add a lock-protected armed-trial coordinator

**Files:**
- Create: `mcp-server/caddie/study/coordinator.py`
- Create: `mcp-server/tests/test_study_coordinator.py`

- [ ] **Step 1: Write failing lifecycle and concurrency tests**

Test `IDLE -> ARMED -> RUNNING -> COMPLETED|FAILED|ABORTED`, illegal transitions, arm conflicts, retry preserving `ARMED`, two simultaneous claims producing exactly one immutable claim, and restart beginning idle.

- [ ] **Step 2: Run coordinator tests red**

Run: `python -m pytest mcp-server/tests/test_study_coordinator.py -q`

Expected: import failure.

- [ ] **Step 3: Implement immutable arm configuration and state**

```python
class ArmedState(StrEnum):
    ARMED = "armed"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    ABORTED = "aborted"

@dataclass(frozen=True)
class ArmedTrialConfig:
    participant_id: str
    trial_index: int
    task_id: str
    condition: StudyCondition
    inject_error: bool
    specs_dir: Path | None
    data_dir: Path | None

@dataclass(frozen=True)
class ClaimedTrial:
    config: ArmedTrialConfig
    spec: TrialSpec
    participant_utterance: str
```

- [ ] **Step 4: Implement atomic `arm`, `route_and_claim`, `finish`, `abort`, `status`, and `clear`**

All state mutation occurs under one `threading.RLock`. A successful claim stores `RUNNING` before returning. An unmatched route logs the `MatchResult` and leaves the state `ARMED`. Terminal state cannot be reclaimed.

- [ ] **Step 5: Run concurrency tests and commit**

Run: `python -m pytest mcp-server/tests/test_study_coordinator.py -q`

Expected: PASS, including exactly one `CLAIMED` under concurrent requests.

```bash
git add mcp-server/caddie/study/coordinator.py mcp-server/tests/test_study_coordinator.py
git commit -m "feat: coordinate armed study trials"
```

### Task 4: Extract reusable real-backend trial preparation and execution

**Files:**
- Create: `mcp-server/caddie/study/runtime.py`
- Modify: `mcp-server/caddie/agent/http_api.py`
- Modify: `mcp-server/tests/test_study_http.py`
- Create: `mcp-server/tests/test_study_runtime.py`

- [ ] **Step 1: Write failing tests for preparation without execution**

Assert `prepare_trial(...)` resolves participant/trial/condition/error assignment and constructs no `TrialExecutor`, logger, session, or Android action. Assert `execute_claimed_trial(...)` uses the passed real backend and writes the participant utterance before `trial_start`/first action.

- [ ] **Step 2: Run runtime tests red**

Run: `python -m pytest mcp-server/tests/test_study_runtime.py -q`

Expected: import failure.

- [ ] **Step 3: Extract shared preparation from `_handle_study_run`**

```python
def prepare_trial(
    participant: str,
    trial_index: int,
    condition: StudyCondition,
    specs_dir: Path | None,
    data_dir: Path | None,
) -> PreparedTrial:
    """Validate and select only; never touch Android or start execution."""
```

Do not retain the current fallback `next(iter(specs.values()))`; missing assigned task IDs must fail closed.

- [ ] **Step 4: Add `execute_claimed_trial`**

Move logger, `RunControl`, oversight callbacks, verification adapter, `StudySession`, and `TrialExecutor` construction into one function used by both diagnostic `/study/trials/run` and the coordinator worker. Log a new structured `participant_utterance` event before calling `executor.run()`.

- [ ] **Step 5: Keep `/study/trials/run` as a synchronous diagnostic wrapper**

Preserve its response contract while delegating selection and execution to the new runtime functions.

- [ ] **Step 6: Run runtime, HTTP, executor, and logger tests**

Run: `python -m pytest mcp-server/tests/test_study_runtime.py mcp-server/tests/test_study_http.py mcp-server/tests/test_study_executor.py mcp-server/tests/test_study_logger.py -q`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add mcp-server/caddie/study/runtime.py mcp-server/caddie/agent/http_api.py mcp-server/caddie/study/logger.py mcp-server/tests/test_study_runtime.py mcp-server/tests/test_study_http.py mcp-server/tests/test_study_logger.py
git commit -m "refactor: share real study trial runtime"
```

### Task 5: Add arm, status, and abort HTTP control

**Files:**
- Modify: `mcp-server/caddie/agent/http_api.py`
- Modify: `mcp-server/tests/test_study_http.py`

- [ ] **Step 1: Write failing HTTP tests**

Cover successful arm without Android actions, invalid participant/index/condition, conflict while armed/running, idle/armed/running/terminal status payloads, abort from armed/running, and arm failure leaving the coordinator idle.

- [ ] **Step 2: Add the endpoint dispatch**

```python
if self.path == "/study/trials/arm":
    self._handle_study_arm()
    return
```

The handler calls `prepare_trial`, performs configured preflight/reset without executing task actions, then calls `coordinator.arm`. Return HTTP 201 for success, 409 for an existing armed/running trial, and 400/500 for validation/preflight failures.

- [ ] **Step 3: Point status and abort at the coordinator**

Status must expose `idle`, `armed`, `running`, `completed`, `failed`, or `aborted`, participant ID, trial index, task ID, and condition without leaking filesystem paths. Abort must be idempotent and request stop on an attached running session.

- [ ] **Step 4: Run HTTP tests and commit**

Run: `python -m pytest mcp-server/tests/test_study_http.py -q`

Expected: PASS.

```bash
git add mcp-server/caddie/agent/http_api.py mcp-server/tests/test_study_http.py
git commit -m "feat: arm study trials without executing them"
```

### Task 6: Route ordinary `/task` and `/task/stream` requests

**Files:**
- Modify: `mcp-server/caddie/agent/http_api.py`
- Modify: `mcp-server/caddie/agent/event_bus.py`
- Create: `mcp-server/tests/test_study_input_routing.py`

- [ ] **Step 1: Write failing ingress integration tests**

Assert no armed trial calls the normal `AgentLoop`; unmatched armed input calls neither `AgentLoop` nor backend and emits exactly `Das habe ich nicht ganz verstanden. Kannst du die Aufgabe bitte noch einmal sagen?`; matched input starts one background worker; duplicate input cannot start a second; input while running goes to `apply_control("intervene", text)`.

- [ ] **Step 2: Add one shared ingress decision helper**

```python
RETRY_MESSAGE = (
    "Das habe ich nicht ganz verstanden. "
    "Kannst du die Aufgabe bitte noch einmal sagen?"
)

def _route_study_input(task: str) -> StudyIngressResult:
    return coordinator.route_and_claim(task)
```

Call it before `context.skills.match(task)` in both request handlers. `PASS_THROUGH` continues unchanged. `RETRY` emits a participant-facing EventBus response and returns HTTP 200 without Android work. `CLAIMED` emits `task_started` and launches a named daemon worker that calls `execute_claimed_trial` with `context.backend`.

- [ ] **Step 3: Make stream and one-shot responses consistent**

Both surfaces expose `study_route` as `retry` or `claimed` and the task ID. The one-shot endpoint returns HTTP 202 after a claim; status supplies its later terminal outcome. The stream remains open and responsive to confirmation/intervention events until it emits the terminal outcome.

- [ ] **Step 4: Run ingress tests and existing agent HTTP tests**

Run: `python -m pytest mcp-server/tests/test_study_input_routing.py mcp-server/tests/test_study_http.py -q`

Expected: PASS and no normal-agent regression.

- [ ] **Step 5: Commit**

```bash
git add mcp-server/caddie/agent/http_api.py mcp-server/caddie/agent/event_bus.py mcp-server/tests/test_study_input_routing.py mcp-server/tests/test_study_http.py
git commit -m "feat: start armed trials from participant requests"
```

### Task 7: Exclude synthetic packages from normal Caddie tools

**Files:**
- Create: `mcp-server/caddie/study/packages.py`
- Modify: `mcp-server/caddie/tools/apps.py`
- Create: `mcp-server/tests/test_study_package_isolation.py`

- [ ] **Step 1: Write failing tool-boundary tests**

Assert `is_synthetic_study_package` recognizes exactly the six packages, `filter_normal_packages` removes them from `smartphone_list_apps`, and `ensure_normal_package` refuses them before `smartphone_open_app` calls the backend. Confirm unrelated `com.caddie.*` development packages remain visible. `TrialExecutor` calls the backend directly and therefore keeps access during a claimed replay.

- [ ] **Step 2: Define the explicit package set**

```python
SYNTHETIC_STUDY_PACKAGES = frozenset({
    "com.caddie.studytelegram",
    "com.caddie.studymail",
    "com.caddie.studygallery",
    "com.caddie.studynotes",
    "com.caddie.studycalendar",
    "com.caddie.studybank",
})
```

- [ ] **Step 3: Apply the boundary in normal app tools**

```python
def filter_normal_packages(packages: list[str]) -> list[str]:
    return [p for p in packages if p not in SYNTHETIC_STUDY_PACKAGES]

def ensure_normal_package(package_name: str) -> None:
    package = package_name.split("/", 1)[0]
    if package in SYNTHETIC_STUDY_PACKAGES:
        raise ValueError("Synthetic study apps are available only during an armed replay")
```

Call `filter_normal_packages` in `smartphone_list_apps` and `ensure_normal_package` before the backend call in `smartphone_open_app`. Do not use a broad package-prefix filter.

- [ ] **Step 4: Run package-isolation and resolver tests and commit**

Run: `python -m pytest mcp-server/tests/test_study_package_isolation.py mcp-server/tests/test_open_app_resolve.py -q`

Expected: PASS.

```bash
git add mcp-server/caddie/study/packages.py mcp-server/caddie/tools/apps.py mcp-server/tests/test_study_package_isolation.py
git commit -m "fix: isolate synthetic apps from normal Caddie"
```

### Task 8: Full regression and six-task Pixel acceptance

**Files:**
- Modify only for test-backed defects found during acceptance.

- [ ] **Step 1: Run the focused Python suite**

Run: `python -m pytest mcp-server/tests/test_study_routing.py mcp-server/tests/test_study_coordinator.py mcp-server/tests/test_study_runtime.py mcp-server/tests/test_study_input_routing.py mcp-server/tests/test_study_http.py mcp-server/tests/test_study_executor.py mcp-server/tests/test_study_spec_loader.py mcp-server/tests/test_study_matrix.py mcp-server/tests/test_study_package_isolation.py mcp-server/tests/test_open_app_resolve.py -q`

Expected: all PASS.

- [ ] **Step 2: Verify ordinary Caddie mode**

With coordinator idle, submit a non-study request through the phone overlay. Confirm the existing `AgentLoop` handles it and no synthetic study package is selected.

- [ ] **Step 3: Verify retry behavior**

Arm each P01 trial, submit an unrelated/ambiguous instruction, verify the exact German repetition message through the overlay/speech channel, zero backend actions, and state still `armed`.

- [ ] **Step 4: Verify participant initiation for every assigned P01 trial**

Speak/type the task naturally with `Jarvis` or `Caddie`. Verify exactly one deterministic replay starts, the initial utterance precedes the first action in JSONL, and no participant trial calls `/study/trials/run`.

- [ ] **Step 5: Verify running controls and terminal behavior**

Exercise C1/C2 confirmations, C3 intervention/correction, abort, duplicate utterance suppression, controlled-error assignment, reset between trials, and terminal status.

- [ ] **Step 6: Run the broader study regression suite**

Run: `python -m pytest mcp-server/tests/test_study_*.py -q`

Expected: PASS; record exact counts and elapsed time.

- [ ] **Step 7: Final review and handoff**

Compare behavior against `2026-07-22-user-initiated-study-routing-design.md`, inspect the diff for unrelated changes, and report exact automated/device evidence plus any limitation. Do not claim completion if any assigned fake app or trial remains unverified.

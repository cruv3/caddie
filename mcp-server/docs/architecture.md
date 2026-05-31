# Caddie MCP Server — Architecture and Design Decisions

As of: 2026-05-01 (renamed from LLMSmartphone to Caddie on 2026-05-31)

This document describes the structure, central design decisions, and the most important trade-offs of the MCP server. It is intended as a companion document for a Master's thesis and tries to *justify* each non-trivial decision instead of only describing the end state.

---

## 1. Goal of the system

A local LLM (e.g. `qwen/qwen3.6-35b-a3b` in LM Studio) should autonomously control a connected Android smartphone: launch apps, click UI elements, swipe, type text, take screenshots, verify content. To do this the LLM has *tools* and *knowledge* (skills). The system is built on Anthropic's **Model Context Protocol (MCP)** standard and uses FastMCP as the server framework on the Python side.

Architectural separation into two processes:

1. **Android app** on the smartphone — exposes an HTTP bridge on a local port (`/screen`, `/tap`, `/swipe`, `/screenshot`, …) on top of the Android `AccessibilityService`.
2. **MCP server** on the PC (Python, FastMCP) — translates MCP tool calls into HTTP requests to the phone bridge or into ADB commands, depending on the chosen backend.

The LLM client (LM Studio or Jan) connects via stdio to the MCP server and sees a list of `smartphone_*` tools. During the tool-calling loop it decides on its own which tools to use in which order.

---

## 2. System architecture

```
┌──────────────────┐    stdio MCP    ┌──────────────────────────────┐
│   LLM client     │ ──────────────► │     MCP server (Python)       │
│  (LM Studio,     │                 │          caddie/*             │
│   Jan, …)        │ ◄────────────── │   - tools/                    │
└──────────────────┘   tool results  │   - skills/                   │
                                     │   - agent/                    │
                                     │   - android/backends/         │
                                     │     ├─ adb/                   │
                                     │     └─ http/                  │
                                     └──────────┬─────────────────┬──┘
                                                │ ADB             │ HTTP
                                                ▼                 ▼
                                     ┌──────────────────┐  ┌──────────────────┐
                                     │  adb shell …     │  │  Android app     │
                                     │  (PC → Phone)    │  │  HTTP bridge     │
                                     └──────────────────┘  │  (port 8765)     │
                                                           └──────────────────┘
```

**Backends.** The server supports two backends, switchable via the env var `LLM_SMARTPHONE_BACKEND`:

- `adb` — classic, uses the `adb` command-line tool. Works when the phone is connected via USB or wireless debugging.
- `http` — talks to the Caddie Android app on the phone, which hosts a tiny HTTP server on port 8765. Reached via `adb reverse` or by direct IP.

**Why the backend split.** ADB is reliable for devbox workflows but tied to USB debugging. The HTTP bridge scales to the field (Wi-Fi, multi-phone, no debug bridge needed) and gives structured access to the accessibility tree — something ADB only delivers awkwardly through `dumpsys` or `uiautomator dump`. Both backends implement the same bridge interface (`take_screenshot`, `list_elements`, `tap`, `swipe`, …) — the tool layer does not see the difference.

**Layers.**

| Layer | Path | Responsibility |
|---|---|---|
| Tools | `caddie/tools/` | MCP tool definitions, FastMCP `@mcp.tool()` wrappers |
| Backends | `caddie/android/backends/{adb,http}` | Concrete phone communication |
| Skills | `caddie/skills/` | Skill library + persistence |
| Agent | `caddie/agent/` | System prompt, optional `/task` HTTP endpoint |
| Server | `server.py` | FastMCP init, tool registration |

---

## 3. Tool layer

Tools are the *capabilities* the LLM has. They are registered once at server start via `register_tools(mcp, context)`. Each tool function is decorated with `@mcp.tool()`; FastMCP automatically generates a JSON schema (name, description, parameters) from it that is sent to the client during the MCP `initialize` handshake.

About 20 tools currently, grouped into:

- **device**: `smartphone_list_devices`, `smartphone_get_screen_size`, `smartphone_get_orientation`, `smartphone_set_orientation`, `smartphone_press_button`
- **input**: `smartphone_tap_coordinates`, `smartphone_double_tap_coordinates`, `smartphone_long_press_coordinates`, `smartphone_swipe`, `smartphone_type_text`
- **apps**: `smartphone_list_apps`, `smartphone_open_app`, `smartphone_terminate_app`, `smartphone_install_app`, `smartphone_uninstall_app`, `smartphone_open_url`
- **screen**: `smartphone_take_screenshot`, `smartphone_list_elements`
- **skills**: one `smartphone_get_skill_<sanitized_id>` per skill, plus a single `smartphone_save_skill`

### 3.1 Design principle: no tools for use cases

A deliberate decision was to **not write one tool per use case**. So there is no `smartphone_set_brightness(percent)`, no `smartphone_toggle_dark_mode(on)`, or anything similar.

**Reason.** If each use case becomes a new tool, the tool list grows linearly with the number of use cases and the server needs constant code changes. Instead **skills** encapsulate the knowledge of *how* to solve a use case — and the LLM uses the generic tools (`tap`, `swipe`, `list_elements`) executed by following the skill's instructions. Skills are Markdown files — pure data, extensible without code changes.

### 3.2 Vision handling in the screenshot tool

`smartphone_take_screenshot(as_image: bool = True)` is an example of a subtle model-capability adaptation.

- **`as_image=True` (default)** returns the image as a FastMCP `Image(...)`. Multimodal models receive the pixels directly.
- **`as_image=False`** returns only the file path as JSON. For non-multimodal models that would otherwise choke on an image block in the tool-result history during inference.

The tool description tells the model explicitly: *"Set as_image=False only if your model is not multimodal — in that case you only get the file path back and cannot inspect the image."*

**Why not auto-detect.** An early iteration used an env var `LLM_SMARTPHONE_VISION` as a global switch. Discarded because:

1. Switching the model (in LM Studio with one click) would require an MCP server restart.
2. In multi-client setups (different clients on different networks with different models) there is no sensible global value.
3. The server cannot reliably know the caller's active model — a `/v1/models` probe against LM Studio only works locally.

A per-call parameter is the only clean solution. The skill (knowledge layer) can make the choice explicit: *"Use `smartphone_take_screenshot(as_image=True)` for visual verification of slider position."*

### 3.3 Robustness against model weaknesses

The tool result of `smartphone_take_screenshot(as_image=True)` returns **a text block in addition to the image**:

> "Screenshot saved at … . If you cannot see the inline image, your model is not multimodal — stop taking screenshots and call this tool with as_image=False or verify another way."

**Why.** We cannot guarantee that the MCP client (e.g. LM Studio) actually forwards the `ImageContent` block to the model. LM Studio has been observed logging `[processMcpToolResult] No working directory available, cannot save image file`, and it is unclear whether the image token actually arrives at the model or only a Markdown reference `![Image](./image-X.png)`. The text hint ensures the model at least gets *one* readable piece of communication from this tool call and can decide for itself.

Additionally, the `/task` HTTP endpoint (see §6) heuristically scans LM Studio errors for vision keywords (`image`, `vision`, `multimodal`, `image_url`, `modality`); if one matches, the server returns a clear plain-text recommendation instead of a bare HTTP 502.

---

## 4. Skill system

Skills are the system's *knowledge*. Each skill is a Markdown file under `skills/<category>/<name>.md` with YAML frontmatter and defined body sections. They encode reusable procedures for specific tasks.

### 4.1 Schema (current)

**Frontmatter (required):**

```yaml
---
id: display.dark_mode_off_settings
title: Turn off Dark Mode via Settings
description: Deactivates Dark Mode (Dunkles Design) through Android Settings under Display & Touch.
triggers: schalte darkmodus aus, deaktiviere dunkles design, dark mode aus
---
```

- `id` — atomic identifier `category.specific_goal_method`. Dot notation, lowercase, underscores for word separation.
- `title` — short human-readable title.
- `description` — 1–2 sentences (≥ 20 characters); the most important selection signal for the LLM (goes into the tool description, see §4.4).
- `triggers` — typical user phrases (localization allowed, unlike the rest of the body).

**Body sections (required):**

- `## Tested Environments` — tested devices / OS variants / languages.
- `## App Context` — target app or UI surface (e.g. `com.android.settings`).
- `## Starting Context` — which screen the flow works from.
- `## Rules` — invariants and constraints.
- `## Typical Flow` — numbered steps, **only those that have actually worked**.
- `## Device Variants` — known device-specific variants.
- `## Verification` — how success is confirmed.
- `## Failure Modes` — conditions under which to abort.

### 4.2 Design principles of the schema

**Atomic IDs.** `category.specific_goal_method` instead of just `category.feature`. That means `display.dark_mode_on_settings` and `display.dark_mode_off_settings` are two *separate* skills, as is `display.dark_mode_on_quick_settings`.

*Why.* Each variant has a different path, different UI elements, different verification steps. Merging them means the LLM would have to resolve branches *at runtime* inside the skill — a weak model fails at that. Atomic IDs let the LLM recognize the most fitting skill directly from the tool name.

**English-only validation for skill content.** When saving, the body is checked via regex `GERMAN_SKILL_TEXT_PATTERN`. If the heuristic matches (umlauts outside triggers, German verbs like *einstellungen, aktivieren, schalte, gehe*), the save is rejected.

*Why.* Skill bodies are later returned as tool descriptions to the LLM. Inconsistent language (mixed German/English) confuses weak models. Triggers stay **intentionally localized** — that's the place where user language matches. UI labels like *"Dunkles Design"* are allowed as quoted strings *inside* English instructions.

**Mandatory sections for context.** `Tested Environments`, `App Context`, `Starting Context`, `Device Variants`, `Failure Modes` — all required. Prevents a skill from only capturing the *one* place where it was just learned.

*Why.* Early skills often contained assumptions about the starting state (e.g. *"tap on Display & Touch"*) without making it clear that the flow assumes the Settings app is already open. The explicit sections force self-reflection while writing the skill.

### 4.3 Skill persistence: `write_skill` with round-trip validation

The `write_skill` function in `caddie/skills/library.py` is the single path through which new skills come into existence. It:

1. **Validates all inputs**: `id` matches pattern `^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$`, `description` ≥ 20 characters, `triggers/rules/flow/...` non-empty.
2. **Validates English-only** in the required fields (trigger list excluded).
3. **Refuses overwrite** if the file exists (when `overwrite=False`).
4. **Backup before overwrite**: with `overwrite=True` the old content is remembered.
5. **Writes** the rendered Markdown.
6. **Round-trip check**: immediately calls `_load_skill(target_path)`. If it would raise, the file is deleted again (or restored from the backup). The write and load paths thereby use the **same validator** — they cannot diverge.

*Why round-trip validation.* Without this check, subtle schema discrepancies could appear: `write_skill` produces frontmatter that `_load_skill` later cannot parse. With round-trip we notice this *at write time*, not at the next server start.

### 4.4 Skill visibility for the LLM: one tool per skill

Each skill is registered as its **own MCP tool**: `smartphone_get_skill_<sanitized_id>`. Calling it returns `{skill_id, body}`. The tool description summarizes intent + triggers:

> "Use this ONLY when the user task matches both the intent described below AND one of the listed triggers. Intent: {description}. Triggers: {triggers}. Calling this for related-but-different tasks (opposite direction, different method, different app, different target state) is wrong — proceed with general tools instead and consider saving a new skill afterward."

*Why this way and not another.* The original idea was to inject skills statically into the system prompt — via the MCP standard `instructions` field in the `initialize` response. That works with Claude Desktop and Cursor. **LM Studio, however, does not pick up the `instructions` field** (see §6.1, Bug Tracker issues #1412 and #1154). Through MCP to LM Studio, *only tools* come through reliably.

This makes the only reliable method: make skills visible *as* tools. As a side effect the tool list grows with the skill count — acceptable at the current ~5–10 skills, but at many skills one would need to think about pagination/filtering.

**Model-selection hardening.** The tool description is intentionally restrictive (`ONLY when ... AND ...`) and explicitly names what counts as a mis-call (*opposite direction, different method, different app, different target state*). The motivation was a test in which the model called `smartphone_get_skill_display_dark_mode_off_settings` for the task *"schalte darkmodus an"* — based on substring similarity ("dark mode appears in the name, sounds relevant"). With the sharpened description the model better understands that *direction* (on vs off) is part of the match criterion.

### 4.5 Override with proof-of-read

Skills cannot be blindly overwritten. The tool `smartphone_save_skill(replace=True)` requires the caller to **have read the existing skill via `smartphone_get_skill_<id>` in the same session**.

The server keeps `ServerContext.read_skill_ids: set[str]` for this. When a `smartphone_get_skill_*` tool is called, the id is recorded there. On replace it is checked:

- File exists + `replace=False` → `error: skill_exists`
- File exists + `replace=True` + id not in read set → `error: must_load_existing_first`
- File exists + `replace=True` + id in read set → overwrite with backup-restore on round-trip failure

*Why.* Prevents the model, after a suboptimal attempt, from "improving" existing skills without ever looking at the existing solution. The weak model could otherwise quickly overwrite the skill store with hallucinations. Server state clears on restart — a new attempt requires a fresh proof-of-read.

### 4.6 Auto skill generation

The system prompt contains the directive:

> "If the task succeeded and no matching `smartphone_get_skill_*` tool was used, you MUST call smartphone_save_skill before replying."

Plus the refinement *"Skill loaded does NOT mean skill followed: if you loaded a `smartphone_get_skill_*` tool but the loaded skill turned out not to match your actual task (wrong direction, …), that counts as no skill used"*.

*Why.* Auto-generation is the mechanism by which the system builds knowledge over time without a human writing every skill by hand. The filter instruction *"include only the tool calls that actually produced the expected outcome"* sits prominently in the tool docstring — the LLM is responsible for curating its own trace (the server has no tool-call history view in stdio MCP). Weak models do not do this perfectly — empirically the structured tool signature (separate fields for `flow`, `rules`, `verification`) guides them to clearly separate what worked from what didn't.

---

## 5. Phone bridge — dumb pipe

The Android app is intentionally designed as a **dumb pipe**. It receives HTTP requests, executes accessibility or gesture actions, and returns raw data. **Filtering, compaction, and heuristics happen exclusively in the MCP server.**

### 5.1 ScreenNodeSerializer

`ScreenNodeSerializer.java` serializes the `AccessibilityNodeInfo` tree to JSON. Fields per node:

```
id, depth, text, description, className, resourceId,
clickable, checkable, checked, enabled, selected, focused,
scrollable, password, editable,
bounds (left, top, right, bottom),
rangeInfo (type, min, max, current)  -- optional, only if present
actions (list of AccessibilityAction ids as int)
```

*Why this set.* Everything `AccessibilityNodeInfo` offers *and* is potentially relevant for UI control. In particular, `rangeInfo` (for sliders / progress bars) and `actions` (which actions Android itself supports, e.g. `ACTION_SET_PROGRESS`) were *not* included in an earlier version — that made deterministic slider verification impossible (see §7.2).

### 5.2 Testability on the Java side

Earlier iteration: `ScreenNodeSerializer.appendNode` built the JSON string directly from `AccessibilityNodeInfo`. That is not testable, because `AccessibilityNodeInfo` is an Android system class with package-private constructors that cannot be instantiated without Robolectric or mocks.

Refactor: extract a pure value class `NodeSnapshot` (all fields public, no logic). Production code: `AccessibilityNodeInfo → NodeSnapshot → appendSnapshot(StringBuilder, NodeSnapshot)`. Tests construct `NodeSnapshot` directly and check the JSON output.

7 tests in `ScreenNodeSerializerTest.java` cover: all standard fields, RangeInfo variations (int/float, with/without), actions array, JSON escaping, null handling, all state flags.

### 5.3 Server-side compacting

`compact_node` in `caddie/android/backends/http/screen.py` reduces the raw phone output:

- Filters invisible nodes (outside screen bounds, bottom padding).
- Filters *useless* nodes (no text/description, not clickable/checkable/scrollable/editable, no range).
- Maps action ids to symbolic labels (`16 → click`, `0x800020 → set_progress`).
- Maps range type codes (`0 → int`, `1 → float`, `2 → percent`).
- Omits fields that are `false` or empty (`scrollable: true` only when true).

*Why not in the phone code.* Filter logic is part of the *perception strategy* — what the agent should see and what it shouldn't. This strategy will change often (e.g. when new skills need new fields). In the phone code, every change would mean an app rebuild + reinstall. In Python it's a simple MCP-server restart.

---

## 6. MCP client integration

### 6.1 LM Studio limitations

During the first setup an architectural problem surfaced: the `instructions` field in the MCP `initialize` response is not evaluated by LM Studio. The system-prompt knowledge an MCP server can hand its client (anti-loop rule, skill manifest, verification instructions) **does not reach the model**.

Verification: a diagnostic marker `[MCP_INIT_OK]` was hidden in `instructions` with the directive to emit it at the end of every reply. The model never mentioned it.

**Sources:**
- LM Studio bug tracker issue #1412 — *"Feature Request: support for MCP Prompts"* — confirms that only tools (not prompts/resources/instructions) are passed through MCP.
- Issue #1154 — *"When using the /v1/responses API, the instructions field is not loaded"* — shows that the word "instructions" is generally not handled reliably in LM Studio.

**Consequence for our architecture:**

1. **Skills as individual tools** registered (see §4.4) instead of injecting them via `instructions`.
2. **System prompt manually pasted into LM Studio** (`Ctrl+Shift+E`), generated via `build_mcp_instructions()`. The source of truth is `BASE_SYSTEM_PROMPT` in `caddie/agent/prompt.py`.
3. **The `instructions` field stays set anyway** — clients like Claude Desktop, Cursor, and possibly a future LM Studio pick it up correctly; then everything is automatic.

### 6.2 Alternative clients

In the meantime Jan ([jan.ai](https://jan.ai)) was evaluated as an alternative — native multimodal support, MCP stable since v0.6.9. The first attempt, however, failed at a strict JSON schema parser: Jan does not accept `description: null` for tools, LM Studio was more tolerant here. Fix: give every tool function a docstring. With that, the server side works with both.

---

## 7. Empirical observations from tests

### 7.1 Weak model, non-deterministic behavior

`qwen/qwen3.6-35b-a3b` is a 35B mixture-of-experts with 3B active parameters — relatively small. Observable weaknesses:

- **Infinite loops**: without an anti-loop rule in the system prompt the model swiped the brightness slider 8x because `list_elements` did not return slider position (before the `rangeInfo` patch) and vision is unreliable.
- **Refusal before the attempt**: after adding the anti-loop rule the model swung to the other extreme — it refused to try, claiming *"no direct function for brightness adjustment"*. Fix: system-prompt addition *"Always attempt the user's task with the tools you have. Never refuse before trying. Stopping is only correct after a real attempt, not before."*
- **Speculative tool calls**: the model calls skill tools by substring similarity (tool name contains "dark_mode" → relevant for brightness). Fix: tool description explicitly *"Calling this for related-but-different tasks (opposite direction, different method, different app) is wrong"*.

### 7.2 Vision: hallucination or real seeing?

The brightness test showed: the model describes a screenshot in detail (*"blue fill is about halfway"*, later *"blue fill extending all the way to the right"*), claims success. In reality, brightness was unchanged at 100%.

Possible explanations:
1. Model is not multimodal, hallucinates based on tool-call context.
2. Model is multimodal but LM Studio does not pass through the `ImageContent`.
3. Model sees the image but interprets slider pixels unreliably.

Architectural consequences: **structured verification > visual verification whenever possible**. Hence the push to pass `rangeInfo` through from `AccessibilityNodeInfo` — the slider value becomes readable as a deterministic number in `list_elements` (e.g. `range.current = 65535, max = 65535`) without the model having to interpret pixels.

### 7.3 Skill triggers: substring vs. semantic

The current match mechanism in `SkillLibrary.match` is pure substring matching against the trigger list, case-insensitive. Empirically sufficient as long as triggers are well maintained.

An earlier iteration required the LLM to decide *in the system-prompt manifest itself* which skill matches. The weak model could not do that reliably. Substring matching on the server side is deterministic, costs no model reasoning, and is debuggable (you can extend triggers in a targeted way).

---

## 8. Design decisions — overview

| Decision | Alternative | Why this one |
|---|---|---|
| Skills as Markdown + tools | Skills as code functions | Maintainable without server restart, auto-generatable by the LLM |
| Skill per variant (atomic IDs) | One skill per feature with branches | Weak model fails at internal branches; atomic IDs are unambiguous |
| English-only skill body | Multilingual skills | Consistent model input, less confusion; triggers are separate |
| Skill-as-tool instead of skill-as-instruction | MCP `instructions` field | LM Studio ignores `instructions`; tools reach the model guaranteed |
| Phone app as dumb pipe | App filters/compacts itself | Filter logic changes often → cheaper in the server |
| `as_image: bool` parameter | Global env flag or auto-detect | Per-call decision, multi-client capable, the skill can prescribe it |
| Round-trip validation in `write_skill` | Write + later validation | Discrepancies between write and read paths become visible immediately |
| Replace only after read | Free overwrite | Prevents blind overwriting by hallucinating models |
| `resourceId`, `rangeInfo`, `actions` in compact_node | Reduced output | Structured values (e.g. slider value as number) replace image analysis |
| Anti-loop rule in system prompt | Server-side tool-call limits | Model behavior can only be steered via prompt in stdio MCP |

---

## 9. Known limitations / future work

1. **Hot-reload of new skills.** Currently the MCP server must be restarted after `smartphone_save_skill` so the new skill becomes visible as a tool. FastMCP can add tools dynamically — but the MCP client must then respect `tools/list_changed` notifications, which not all clients do.
2. **Skill versioning.** Today `replace=True` overwrites without version history. A `skills/_archive/<id>.<timestamp>.md` would preserve old variants.
3. **Tool-call history in the server.** In stdio MCP mode the server only sees individual tool calls, not the order or total context of a task. That limits possibilities for server-side analysis. SSE/HTTP transport with session state would be a workaround.
4. **Multi-phone support.** Currently exactly one phone (either via ADB or via HTTP bridge on a fixed port). Multiple phones would need routing in `ServerContext`.
5. **Vision diagnostic tool.** Planned, not implemented: a `smartphone_vision_check` that generates a PNG with a random text marker. Model response vs. server log gives a deterministic verdict on whether vision actually arrives.
6. **`/task` endpoint vs. MCP.** The existing `/task` HTTP endpoint (`agent/http_api.py`) was built for our own frontends but is bypassed under stdio connection from LM Studio. Both paths use the same system prompt today (`build_mcp_instructions()`); the `/task` path additionally does trigger-match-based skill injection into the system prompt — which does not happen on direct MCP. Two evaluation paths lead to differences that are hard to reproduce.
7. **Skill conflict detection.** When two skills are triggered for the same task (same trigger) there is no arbitration today. Pragmatic: the model sees both tools and chooses by description.
8. **Telemetry/audit.** Which skill was executed when by whom — not recorded. Possibly relevant for the Master's thesis; would require logging hooks.

---

## 10. Directory structure (current)

```
mcp-server/
├── server.py                          # Entry: FastMCP init, ServerContext, register_tools
├── requirements.txt                   # fastmcp, PyYAML, Pillow
├── caddie/
│   ├── config.py                      # env-var reading, defaults
│   ├── context.py                     # ServerContext (skills, backend, project_dir, read_skill_ids)
│   ├── registry.py                    # ToolRegistry for the tool-registrar pattern
│   ├── tools/
│   │   ├── device.py                  # smartphone_list_devices, _get_screen_size, …
│   │   ├── input.py                   # smartphone_tap_coordinates, _swipe, _type_text, …
│   │   ├── apps.py                    # smartphone_open_app, _list_apps, …
│   │   ├── screen.py                  # smartphone_take_screenshot, _list_elements
│   │   └── skills.py                  # smartphone_get_skill_* + smartphone_save_skill
│   ├── skills/
│   │   ├── library.py                 # SkillLibrary, write_skill, validate_english_skill_content
│   │   └── __init__.py
│   ├── agent/
│   │   ├── prompt.py                  # BASE_SYSTEM_PROMPT, build_mcp_instructions
│   │   ├── http_api.py                # /task endpoint
│   │   └── lmstudio.py                # LM Studio client with vision-error detection
│   └── android/
│       ├── adb.py
│       └── backends/
│           ├── adb/                   # ADB backend (screenshot, list_elements via dumpsys)
│           └── http/                  # HTTP-bridge backend
│               ├── client.py
│               ├── bridge.py
│               ├── screen.py          # compact_node, decode_actions, compact_range
│               └── …
├── skills/                            # Markdown skills, persistent
│   └── display/
│       └── dark_mode_off_settings.md
└── tests/                             # unit tests, ~41 tests
    ├── test_skills.py
    ├── test_agent_prompt.py
    └── test_compact_node.py
```

Companion app in Android Studio: `LLMSmartphone/app/src/main/java/com/caddie/`, Java 11. The most important class for the layer transition is `phone/ScreenNodeSerializer.java`. JUnit 4 tests under `app/src/test/...` (`ScreenNodeSerializerTest.java` with 7 tests).

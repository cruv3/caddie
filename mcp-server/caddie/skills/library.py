from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from pathlib import Path

import yaml


logger = logging.getLogger(__name__)


SKILL_ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$")
GERMAN_SKILL_TEXT_PATTERN = re.compile(
    r"[äöüßÄÖÜ]|"
    r"\b("
    r"ausschalten|anschalten|aktivieren|deaktivieren|"
    r"schaltet|schalte|schalter|"
    r"gehe|waehle|wähle|deaktiviere|aktiviere|"
    r"einstellungen|systemeinstellungen|geraet|gerät|"
    r"ueber|über|fuer|für|"
    r"ueberpruefe|überprüfe|dass"
    r")\b",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class Skill:
    id: str
    title: str
    description: str
    triggers: tuple[str, ...]
    body: str
    path: Path
    # Structured, replayable steps (cheap-assert replay). Empty = guidance-only
    # skill. Auto-recorded from a successful run into "<stem>.steps.json".
    steps: tuple[dict, ...] = ()


class SkillLibrary:
    def __init__(self, skills: list[Skill]) -> None:
        self._skills = skills
        self._by_id = {skill.id: skill for skill in skills}

    @classmethod
    def load(cls, skills_dir: Path) -> "SkillLibrary":
        if not skills_dir.exists():
            return cls([])
        skills = [
            _load_skill(path)
            for path in sorted(skills_dir.rglob("*.md"))
            if path.is_file()
        ]
        return cls(skills)

    def all(self) -> list[Skill]:
        return list(self._skills)

    def get(self, skill_id: str) -> Skill | None:
        return self._by_id.get(skill_id)

    def manifest_for(self, skills: list[Skill]) -> str:
        if not skills:
            return ""
        lines: list[str] = []
        for skill in skills:
            lines.append(f"- id: {skill.id}")
            lines.append(f"  title: {skill.title}")
            lines.append(f"  description: {skill.description}")
            if skill.triggers:
                lines.append(f"  triggers: {', '.join(skill.triggers)}")
        return "\n".join(lines)

    def manifest_all(self) -> str:
        return self.manifest_for(self._skills)

    def match(self, task: str) -> list[Skill]:
        if not task:
            return []
        normalized = task.casefold()
        matched: list[Skill] = []
        for skill in self._skills:
            for trigger in skill.triggers:
                if trigger.casefold() in normalized:
                    matched.append(skill)
                    break
        return matched


def _load_skill(path: Path) -> Skill:
    raw = path.read_text(encoding="utf-8")
    metadata, body = _split_frontmatter(raw)

    skill_id = str(metadata.get("id") or path.stem.replace("_", ".")).strip()
    title = str(metadata.get("title") or path.stem.replace("_", " ").title()).strip()
    description = str(metadata.get("description") or "").strip()
    if not description:
        raise ValueError(
            f"Skill {path} is missing required 'description' frontmatter field. "
            "Each skill must declare a 1-2 sentence description that the LLM uses to decide whether to load it."
        )

    triggers_raw = metadata.get("triggers")
    triggers = _parse_triggers(triggers_raw)

    if "## Verification" not in body and "## verification" not in body:
        logger.warning(
            "Skill %s has no '## Verification' section; agent cannot confirm post-action state.",
            skill_id,
        )

    # Load replayable steps if a sibling "<stem>.steps.json" exists.
    steps: tuple[dict, ...] = ()
    steps_path = path.with_name(path.stem + ".steps.json")
    if steps_path.exists():
        try:
            import json
            data = json.loads(steps_path.read_text(encoding="utf-8"))
            if isinstance(data, list):
                steps = tuple(s for s in data if isinstance(s, dict))
        except Exception as exc:  # pragma: no cover - defensive
            logger.warning("Skill %s: bad steps file %s: %s", skill_id, steps_path, exc)

    return Skill(
        id=skill_id,
        title=title,
        description=description,
        triggers=tuple(triggers),
        body=body.strip(),
        path=path,
        steps=steps,
    )


def _split_frontmatter(content: str) -> tuple[dict, str]:
    if not content.startswith("---"):
        return {}, content
    end = content.find("\n---", 3)
    if end < 0:
        return {}, content
    raw_meta = content[3:end].strip()
    body_start = content.find("\n", end + 4)
    body = content[body_start + 1:] if body_start >= 0 else ""
    try:
        parsed = yaml.safe_load(raw_meta) or {}
    except yaml.YAMLError as exc:
        raise ValueError(f"Invalid YAML frontmatter: {exc}") from exc
    if not isinstance(parsed, dict):
        raise ValueError("Skill frontmatter must be a YAML mapping.")
    return parsed, body


def write_skill(
    skills_dir: Path,
    *,
    id: str,
    title: str,
    description: str,
    triggers: list[str],
    tested_environments: list[str],
    app_context: str,
    starting_context: str,
    rules: list[str],
    flow: list[str],
    device_variants: list[str],
    verification: str,
    failure_modes: list[str],
    overwrite: bool = False,
) -> Path:
    """Write a new skill markdown file under skills_dir.

    Validates inputs, derives the file path from the dotted id (`category.name` →
    `<skills_dir>/<category>/<name>.md`), and round-trip-validates by re-loading
    the written file via `_load_skill`. On round-trip failure the file is removed
    (or restored to its previous content if overwriting) and the error is raised.

    If overwrite=False (default) and the file already exists, raises FileExistsError.
    """
    skill_id = id.strip()
    if not SKILL_ID_PATTERN.match(skill_id):
        raise ValueError(
            f"Invalid skill id {skill_id!r}: must match 'category.name' "
            "with lowercase letters/digits/underscores only (e.g. 'android.bluetooth')."
        )
    title_clean = title.strip()
    if not title_clean:
        raise ValueError("Skill title must not be empty.")
    description_clean = description.strip()
    if len(description_clean) < 20:
        raise ValueError(
            "Skill description must be at least 20 characters and describe what the skill does."
        )
    triggers_clean = [str(t).strip() for t in (triggers or []) if str(t).strip()]
    if not triggers_clean:
        raise ValueError("Skill must declare at least one trigger phrase.")
    tested_environments_clean = [
        str(t).strip() for t in (tested_environments or []) if str(t).strip()
    ]
    if not tested_environments_clean:
        raise ValueError("Skill must include at least one tested environment.")
    app_context_clean = app_context.strip()
    if not app_context_clean:
        raise ValueError("Skill must include an app context.")
    starting_context_clean = starting_context.strip()
    if not starting_context_clean:
        raise ValueError("Skill must include a starting context.")
    rules_clean = [str(r).strip() for r in (rules or []) if str(r).strip()]
    if not rules_clean:
        raise ValueError("Skill must include at least one rule.")
    flow_clean = [str(s).strip() for s in (flow or []) if str(s).strip()]
    if not flow_clean:
        raise ValueError("Skill must include at least one flow step.")
    device_variants_clean = [str(v).strip() for v in (device_variants or []) if str(v).strip()]
    if not device_variants_clean:
        raise ValueError("Skill must include at least one device variant.")
    verification_clean = verification.strip()
    if not verification_clean:
        raise ValueError("Skill must include a verification description.")
    failure_modes_clean = [str(f).strip() for f in (failure_modes or []) if str(f).strip()]
    if not failure_modes_clean:
        raise ValueError("Skill must include at least one failure mode.")
    _validate_english_skill_content(
        title=title_clean,
        description=description_clean,
        tested_environments=tested_environments_clean,
        app_context=app_context_clean,
        starting_context=starting_context_clean,
        rules=rules_clean,
        flow=flow_clean,
        device_variants=device_variants_clean,
        verification=verification_clean,
        failure_modes=failure_modes_clean,
    )

    category, _, name = skill_id.partition(".")
    target_dir = skills_dir / category
    target_path = target_dir / f"{name}.md"

    previous_content: str | None = None
    if target_path.exists():
        if not overwrite:
            raise FileExistsError(
                f"Skill file already exists: {target_path}. "
                "Pass overwrite=True to replace it (only after consciously reviewing the existing skill)."
            )
        previous_content = target_path.read_text(encoding="utf-8")

    content = _render_skill_markdown(
        skill_id=skill_id,
        title=title_clean,
        description=description_clean,
        triggers=triggers_clean,
        tested_environments=tested_environments_clean,
        app_context=app_context_clean,
        starting_context=starting_context_clean,
        rules=rules_clean,
        flow=flow_clean,
        device_variants=device_variants_clean,
        verification=verification_clean,
        failure_modes=failure_modes_clean,
    )

    target_dir.mkdir(parents=True, exist_ok=True)
    target_path.write_text(content, encoding="utf-8")

    try:
        _load_skill(target_path)
    except Exception:
        if previous_content is not None:
            target_path.write_text(previous_content, encoding="utf-8")
        else:
            try:
                target_path.unlink()
            except OSError:
                pass
        raise

    return target_path


def _validate_english_skill_content(
    *,
    title: str,
    description: str,
    tested_environments: list[str],
    app_context: str,
    starting_context: str,
    rules: list[str],
    flow: list[str],
    device_variants: list[str],
    verification: str,
    failure_modes: list[str],
) -> None:
    fields = [
        ("title", title),
        ("description", description),
        ("app_context", app_context),
        ("starting_context", starting_context),
        ("verification", verification),
    ]
    fields.extend(
        (f"tested_environments[{index}]", environment)
        for index, environment in enumerate(tested_environments)
    )
    fields.extend((f"rules[{index}]", rule) for index, rule in enumerate(rules))
    fields.extend((f"flow[{index}]", step) for index, step in enumerate(flow))
    fields.extend(
        (f"device_variants[{index}]", variant)
        for index, variant in enumerate(device_variants)
    )
    fields.extend(
        (f"failure_modes[{index}]", failure)
        for index, failure in enumerate(failure_modes)
    )

    for field, value in fields:
        if GERMAN_SKILL_TEXT_PATTERN.search(value):
            raise ValueError(
                f"Skill {field} must be written in English. Localized user phrases "
                "are allowed only in triggers."
            )


def _render_skill_markdown(
    *,
    skill_id: str,
    title: str,
    description: str,
    triggers: list[str],
    tested_environments: list[str],
    app_context: str,
    starting_context: str,
    rules: list[str],
    flow: list[str],
    device_variants: list[str],
    verification: str,
    failure_modes: list[str],
) -> str:
    frontmatter = {
        "id": skill_id,
        "title": title,
        "description": description,
        "triggers": ", ".join(triggers),
    }
    fm_yaml = yaml.safe_dump(frontmatter, allow_unicode=True, sort_keys=False).strip()

    tested_environments_block = "\n".join(
        f"- {environment}" for environment in tested_environments
    )
    rules_block = "\n".join(f"- {rule}" for rule in rules)
    flow_block = "\n".join(f"{i + 1}. {step}" for i, step in enumerate(flow))
    device_variants_block = "\n".join(f"- {variant}" for variant in device_variants)
    failure_modes_block = "\n".join(f"- {failure}" for failure in failure_modes)

    return (
        f"---\n{fm_yaml}\n---\n\n"
        f"# {title}\n\n"
        f"## Tested Environments\n\n{tested_environments_block}\n\n"
        f"## App Context\n\n{app_context}\n\n"
        f"## Starting Context\n\n{starting_context}\n\n"
        f"## Rules\n\n{rules_block}\n\n"
        f"## Typical Flow\n\n{flow_block}\n\n"
        f"## Device Variants\n\n{device_variants_block}\n\n"
        f"## Verification\n\n{verification}\n\n"
        f"## Failure Modes\n\n{failure_modes_block}\n"
    )


def _parse_triggers(value: object) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        return [item.strip() for item in value.split(",") if item.strip()]
    return []

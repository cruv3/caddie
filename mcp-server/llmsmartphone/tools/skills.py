from __future__ import annotations

import re

from fastmcp import FastMCP

from llmsmartphone.agent.event_bus import publish_tool_call
from llmsmartphone.context import ServerContext
from llmsmartphone.skills import Skill
from llmsmartphone.skills.library import write_skill


def register_skill_tools(mcp: FastMCP, context: ServerContext) -> None:
    """Register one MCP tool per skill so the model sees them in the tool list.

    LM Studio currently only exposes MCP Tools to the model — the `instructions`
    field, Prompts, and Resources are ignored. So skills are surfaced as tools:
    each skill becomes `smartphone_get_skill_<id>` whose description tells the
    model when to call it, and whose return is the skill's full body.
    """
    for skill in context.skills.all():
        _register_one(mcp, context, skill)


def _register_one(mcp: FastMCP, context: ServerContext, skill: Skill) -> None:
    tool_name = f"smartphone_get_skill_{_sanitize(skill.id)}"
    triggers_hint = ", ".join(skill.triggers) if skill.triggers else "(none)"
    description = (
        f"Use this ONLY when the user task matches both the intent described below AND one of the listed triggers. "
        f"Intent: {skill.description} "
        f"Triggers: {triggers_hint}. "
        f"Calling this for related-but-different tasks (opposite direction, different method, different app, "
        f"different target state) is wrong — proceed with general tools instead and consider saving a new skill "
        f"afterward. Returns the full skill body with rules, flow, and verification steps. Follow them exactly. "
        f"If your live observations contradict the skill, trust the observation and report the divergence in your final reply."
    )

    body = skill.body
    skill_id = skill.id

    @mcp.tool(name=tool_name, description=description)
    def _tool() -> dict:
        with publish_tool_call(tool_name, bus=context.events, skill_id=skill_id):
            context.read_skill_ids.add(skill_id)
            return {"skill_id": skill_id, "body": body}


def register_save_skill_tool(mcp: FastMCP, context: ServerContext) -> None:
    """Register the `smartphone_save_skill` tool that lets the model save a new skill
    after successfully completing a task that no existing skill covered."""

    skills_dir = context.project_dir / "skills"

    @mcp.tool()
    def smartphone_save_skill(
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
        replace: bool = False,
    ) -> dict:
        """Save a new skill describing how to perform a task you just completed.

        Call this ONLY after a task that no existing `smartphone_get_skill_*` tool
        covered, and ONLY after success. Include in `flow` ONLY the tool calls
        that actually produced the expected result. If you tried something that
        didn't work, omit it entirely — the skill must be a clean recipe future
        agents can follow blindly. Do not include narrative, only actionable steps.

        All saved skills must be written in English. Prefer precise atomic skill
        IDs using `category.specific_goal_method`, for example
        `display.dark_mode_on_settings` or `display.dark_mode_off_quick_settings`.
        Include the target state in the ID when relevant, such as on, off, zero,
        max, enabled, or disabled. Include the method or UI surface in the ID,
        such as settings, quick_settings, launcher, or app_ui. Do not overwrite a different approach
        just because it solves a similar user task. Save different methods as separate skills.
        Every saved skill must include reusable context sections so it is not tied
        to the accidental screen where it was learned: tested_environments,
        app_context, starting_context, device_variants, and failure_modes.
        German UI labels are allowed as quoted target labels inside otherwise
        English instructions, but the skill prose itself must remain English.

        To replace an existing skill, you must FIRST call its
        `smartphone_get_skill_<id>` tool to read the current version, decide that
        your new version is better, and then call this tool with replace=True.
        Replacing without reading the existing skill first is rejected.

        Parameters
        ----------
        id: dotted identifier `category.specific_goal_method`, e.g.
            `display.dark_mode_on_settings`. Lowercase letters, digits,
            underscores only.
        title: short human title, e.g. `Android Bluetooth Toggle`.
        description: 1–2 sentences (≥ 20 chars) summarizing what the skill does.
            Future agents see this in the tool description for trigger matching.
        triggers: list of typical user phrasings, e.g. `["bluetooth", "bt einschalten"]`.
            Triggers may include localized user phrases.
        tested_environments: tested devices, OS/UI variants, language, orientation, and screen assumptions.
        app_context: target app or UI surface, e.g. Android Settings or Quick Settings.
        starting_context: where the flow can start from, normally any screen.
        rules: invariants and constraints to follow (e.g. "always inspect before tapping").
        flow: numbered steps that worked, in order. Each entry one concrete step.
        device_variants: known OEM, Android version, UI language, or device-specific variants.
        verification: how to confirm the action succeeded.
        failure_modes: conditions where the agent must stop instead of guessing.
        replace: set True to overwrite an existing skill — only allowed if you have
            already loaded that skill via its smartphone_get_skill_* tool in this
            session. Default False.
        """
        with publish_tool_call(
            "smartphone_save_skill", bus=context.events, id=id, title=title, replace=replace
        ):
            return _save_skill_impl(
                context,
                skills_dir,
                id=id,
                title=title,
                description=description,
                triggers=triggers,
                tested_environments=tested_environments,
                app_context=app_context,
                starting_context=starting_context,
                rules=rules,
                flow=flow,
                device_variants=device_variants,
                verification=verification,
                failure_modes=failure_modes,
                replace=replace,
            )


def _save_skill_impl(
    context: ServerContext,
    skills_dir,
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
    replace: bool,
) -> dict:
    category, _, _name = id.partition(".")
    target_path = skills_dir / category / f"{_name}.md"
    existed = target_path.exists()

    if existed and replace and id not in context.read_skill_ids:
        return {
            "ok": False,
            "error": "must_load_existing_first",
            "message": (
                f"Cannot replace skill '{id}' without reading the existing version "
                f"first. Call smartphone_get_skill_{_sanitize(id)} in this session, "
                "review its body, and then call smartphone_save_skill with replace=True."
            ),
        }

    try:
        path = write_skill(
            skills_dir,
            id=id,
            title=title,
            description=description,
            triggers=triggers,
            tested_environments=tested_environments,
            app_context=app_context,
            starting_context=starting_context,
            rules=rules,
            flow=flow,
            device_variants=device_variants,
            verification=verification,
            failure_modes=failure_modes,
            overwrite=replace,
        )
    except FileExistsError as exc:
        return {"ok": False, "error": "skill_exists", "message": str(exc)}
    except ValueError as exc:
        return {"ok": False, "error": "validation_failed", "message": str(exc)}
    return {
        "ok": True,
        "skill_id": id,
        "path": str(path),
        "replaced": existed,
        "note": "Skill saved. Restart the MCP server to make it appear as a smartphone_get_skill_* tool.",
    }


_SANITIZE_RE = re.compile(r"[^a-z0-9_]+")


def _sanitize(value: str) -> str:
    cleaned = _SANITIZE_RE.sub("_", value.lower())
    return cleaned.strip("_") or "skill"

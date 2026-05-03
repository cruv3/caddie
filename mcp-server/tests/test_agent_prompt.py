import unittest
from pathlib import Path

from llmsmartphone.agent.prompt import build_mcp_instructions, build_system_prompt
from llmsmartphone.skills import Skill


def _skill(id_: str, body: str = "body content", triggers: tuple[str, ...] = ()) -> Skill:
    return Skill(
        id=id_,
        title=id_.replace(".", " ").title(),
        description=f"Description for {id_}.",
        triggers=triggers,
        body=body,
        path=Path(f"{id_}.md"),
    )


class AgentPromptTest(unittest.TestCase):
    def test_matched_skills_render_full_body(self) -> None:
        skill = _skill("android.dark_mode", body="Never toggle blindly.", triggers=("dark mode",))
        prompt = build_system_prompt([skill])

        self.assertIn("Active Skills", prompt)
        self.assertIn('<skill id="android.dark_mode"', prompt)
        self.assertIn("Never toggle blindly.", prompt)
        self.assertIn("skill_divergences", prompt)

    def test_no_match_returns_base_prompt_only(self) -> None:
        prompt = build_system_prompt([])

        self.assertIn("autonomous Android phone control agent", prompt)
        self.assertNotIn("Active Skills", prompt)
        self.assertNotIn("<skill", prompt)
        self.assertNotIn("skill_divergences", prompt)

    def test_anti_loop_rule_present(self) -> None:
        prompt = build_system_prompt([])
        self.assertIn("Anti-loop rule", prompt)
        self.assertIn("Never refuse before trying", prompt)
        self.assertIn("after a real attempt", prompt)

    def test_screenshot_evidence_rules_present(self) -> None:
        prompt = build_system_prompt([])

        self.assertIn("Screenshots are evidence for you to inspect", prompt)
        self.assertIn("Do not present screenshots to the user", prompt)
        self.assertIn("Do not claim a visual state is correct unless", prompt)

    def test_skill_save_decision_rule_present(self) -> None:
        prompt = build_system_prompt([])

        self.assertIn("Before every final response", prompt)
        self.assertIn("MUST call smartphone_save_skill before replying", prompt)
        self.assertIn("existing_skill_used", prompt)
        self.assertIn("task_failed", prompt)
        self.assertIn("verification_uncertain", prompt)
        self.assertIn("no_reusable_flow", prompt)

    def test_atomic_skill_id_rules_present(self) -> None:
        prompt = build_system_prompt([])

        self.assertIn("All saved skills must be written in English", prompt)
        self.assertIn("Prefer precise atomic skill IDs", prompt)
        self.assertIn("category.specific_goal_method", prompt)
        self.assertIn("display.dark_mode_on_settings", prompt)
        self.assertIn("display.dark_mode_off_quick_settings", prompt)
        self.assertIn("Do not overwrite a different approach", prompt)
        self.assertIn("Save different methods as separate skills", prompt)

    def test_skill_context_sections_rule_present(self) -> None:
        prompt = build_system_prompt([])

        self.assertIn("Every saved skill must include reusable context sections", prompt)
        self.assertIn("Tested Environments", prompt)
        self.assertIn("App Context", prompt)
        self.assertIn("Starting Context", prompt)
        self.assertIn("Device Variants", prompt)
        self.assertIn("Failure Modes", prompt)
        self.assertIn("German UI labels are allowed as quoted target labels", prompt)

    def test_multiple_matched_skills_render_all_bodies(self) -> None:
        a = _skill("a.one", body="BODY A", triggers=("a",))
        b = _skill("b.two", body="BODY B", triggers=("b",))

        prompt = build_system_prompt([a, b])

        self.assertIn("BODY A", prompt)
        self.assertIn("BODY B", prompt)


class McpInstructionsTest(unittest.TestCase):
    def test_contains_base_prompt_and_tool_hint_no_skill_bodies(self) -> None:
        instructions = build_mcp_instructions()

        self.assertIn("autonomous Android phone control agent", instructions)
        self.assertIn("smartphone_get_skill_", instructions)
        self.assertIn("smartphone_list_elements", instructions)
        self.assertNotIn("smarthone_list_elements", instructions)
        self.assertIn("Anti-loop rule", instructions)
        self.assertIn("Screenshots are evidence for you to inspect", instructions)
        self.assertIn("Before every final response", instructions)
        self.assertIn("MUST call smartphone_save_skill before replying", instructions)
        self.assertIn("existing_skill_used", instructions)
        self.assertIn("task_failed", instructions)
        self.assertIn("verification_uncertain", instructions)
        self.assertIn("no_reusable_flow", instructions)
        self.assertIn("All saved skills must be written in English", instructions)
        self.assertIn("Prefer precise atomic skill IDs", instructions)
        self.assertIn("display.dark_mode_on_settings", instructions)
        self.assertIn("display.dark_mode_off_quick_settings", instructions)
        self.assertIn("Save different methods as separate skills", instructions)
        self.assertIn("Every saved skill must include reusable context sections", instructions)
        self.assertIn("Tested Environments", instructions)
        self.assertIn("Device Variants", instructions)
        self.assertIn("Failure Modes", instructions)
        self.assertIn("divergence", instructions)
        self.assertNotIn("<skill ", instructions)
        self.assertNotIn("Available Skills", instructions)


if __name__ == "__main__":
    unittest.main()

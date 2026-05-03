import tempfile
import unittest
import inspect
from pathlib import Path

from llmsmartphone.skills import SkillLibrary
from llmsmartphone.skills.library import write_skill
from llmsmartphone.tools.skills import register_save_skill_tool


class SkillLibraryTest(unittest.TestCase):
    def test_loads_markdown_skill_with_description_and_strips_frontmatter(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        skill = library.get("android.dark_mode")
        self.assertIsNotNone(skill)
        assert skill is not None
        self.assertEqual("Android Dark Mode", skill.title)
        self.assertIn("Toggle Android dark mode", skill.description)
        self.assertIn("Never toggle blindly.", skill.body)
        self.assertNotIn("triggers:", skill.body)

    def test_get_returns_none_for_unknown_id(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        self.assertIsNone(library.get("nonexistent.skill"))

    def test_manifest_all_renders_id_title_description(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        manifest = library.manifest_all()

        self.assertIn("id: android.dark_mode", manifest)
        self.assertIn("title: Android Dark Mode", manifest)
        self.assertIn("description: Toggle Android dark mode", manifest)

    def test_missing_description_raises(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            skill_dir = Path(tmp) / "android"
            skill_dir.mkdir(parents=True)
            (skill_dir / "broken.md").write_text(
                "---\nid: broken\ntitle: Broken\n---\n\n# Broken\n\n## Verification\n\nNone.\n",
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                SkillLibrary.load(Path(tmp))

    def test_missing_skills_dir_returns_empty(self) -> None:
        library = SkillLibrary.load(Path(tempfile.gettempdir()) / "definitely_no_skills_here_xyz")

        self.assertEqual([], library.all())

    def test_match_finds_skill_by_trigger_substring(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        matched = library.match("Schalte den Dark Mode aus")
        self.assertEqual(["android.dark_mode"], [s.id for s in matched])

    def test_match_returns_empty_for_unrelated_task(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        self.assertEqual([], library.match("öffne YouTube"))

    def test_match_handles_empty_task(self) -> None:
        library = SkillLibrary.load(_fixture_skills_dir())

        self.assertEqual([], library.match(""))


_VALID_SKILL = dict(
    id="android.bluetooth",
    title="Android Bluetooth Toggle",
    description="Enable or disable Android Bluetooth via the Settings app reliably.",
    triggers=["bluetooth", "bt einschalten", "bt ausschalten"],
    tested_environments=[
        "Device: Android emulator with stock Settings.",
        "UI language: English or German labels may appear.",
        "Orientation: portrait.",
    ],
    app_context="Android Settings app (`com.android.settings`), Connections area.",
    starting_context="Any screen, including already inside Android Settings.",
    rules=["Open the Connections menu before toggling.", "Verify the switch state changed."],
    flow=[
        "Call smartphone_list_elements to inspect the current screen.",
        "Open Settings via smartphone_open_app with package com.android.settings.",
        "Tap the 'Connections' entry.",
        "Tap the Bluetooth row's switch.",
    ],
    device_variants=[
        "Stock Android: Bluetooth is usually under Connections or Connected devices.",
        "German UI: Bluetooth labels may still appear as Bluetooth.",
    ],
    verification="Call smartphone_list_elements and confirm the Bluetooth switch checked value matches the requested target.",
    failure_modes=[
        "Stop if no Bluetooth control can be identified after opening Settings.",
        "Stop if verification cannot confirm the final checked state.",
    ],
)


class WriteSkillTest(unittest.TestCase):
    def test_writes_valid_skill_and_round_trip_loads(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            skills_dir = Path(tmp)
            path = write_skill(skills_dir, **_VALID_SKILL)

            self.assertTrue(path.exists())
            self.assertEqual(skills_dir / "android" / "bluetooth.md", path)

            library = SkillLibrary.load(skills_dir)
            ids = [s.id for s in library.all()]
            self.assertIn("android.bluetooth", ids)
            skill = library.get("android.bluetooth")
            assert skill is not None
            self.assertIn("## Rules", skill.body)
            self.assertIn("## Typical Flow", skill.body)
            self.assertIn("## Verification", skill.body)
            self.assertIn("## Tested Environments", skill.body)
            self.assertIn("## App Context", skill.body)
            self.assertIn("## Starting Context", skill.body)
            self.assertIn("## Device Variants", skill.body)
            self.assertIn("## Failure Modes", skill.body)
            self.assertIn("Open the Connections menu", skill.body)

    def test_refuses_to_overwrite_existing_file_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            skills_dir = Path(tmp)
            write_skill(skills_dir, **_VALID_SKILL)
            with self.assertRaises(FileExistsError):
                write_skill(skills_dir, **_VALID_SKILL)

    def test_overwrite_true_replaces_file_and_keeps_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            skills_dir = Path(tmp)
            write_skill(skills_dir, **_VALID_SKILL)

            updated = {
                **_VALID_SKILL,
                "title": "Android Bluetooth Toggle (Improved)",
                "rules": ["New invariant only.", "Verify the switch state changed."],
                "overwrite": True,
            }
            path = write_skill(skills_dir, **updated)

            library = SkillLibrary.load(skills_dir)
            skill = library.get("android.bluetooth")
            assert skill is not None
            self.assertEqual("Android Bluetooth Toggle (Improved)", skill.title)
            self.assertIn("New invariant only.", skill.body)
            self.assertEqual(skills_dir / "android" / "bluetooth.md", path)

    def test_overwrite_failure_restores_previous_content(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            skills_dir = Path(tmp)
            path = write_skill(skills_dir, **_VALID_SKILL)
            original = path.read_text(encoding="utf-8")

            broken = {
                **_VALID_SKILL,
                "description": "x",
                "overwrite": True,
            }
            with self.assertRaises(ValueError):
                write_skill(skills_dir, **broken)

            self.assertEqual(original, path.read_text(encoding="utf-8"))

    def test_rejects_invalid_id(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                write_skill(Path(tmp), **{**_VALID_SKILL, "id": "../etc/passwd"})
            with self.assertRaises(ValueError):
                write_skill(Path(tmp), **{**_VALID_SKILL, "id": "no-dot"})
            with self.assertRaises(ValueError):
                write_skill(Path(tmp), **{**_VALID_SKILL, "id": "Android.Bluetooth"})

    def test_accepts_precise_atomic_skill_id(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = write_skill(
                Path(tmp),
                **{
                    **_VALID_SKILL,
                    "id": "display.dark_mode_on_quick_settings",
                    "title": "Enable Dark Mode via Quick Settings",
                    "description": "Enable Android dark mode through the Quick Settings tile.",
                    "triggers": ["dark mode on quick settings", "enable dark mode quick settings"],
                },
            )

            self.assertEqual(Path(tmp) / "display" / "dark_mode_on_quick_settings.md", path)

    def test_rejects_non_english_skill_body_fields_but_allows_localized_triggers(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            german_skill = {
                **_VALID_SKILL,
                "id": "display.dark_mode_off_settings",
                "title": "Dark Mode uber Einstellungen ausschalten",
                "description": (
                    "Schaltet den Dark Mode auf einem Android-Geraet ueber "
                    "die Systemeinstellungen aus."
                ),
                "triggers": [
                    "darkmodus ausschalten",
                    "dark mode off",
                    "dunkles design deaktivieren",
                ],
                "rules": [
                    "Gehe in die Einstellungen.",
                    "Waehle Display und Touchbedienung.",
                    "Deaktiviere den Schalter fuer Dunkles Design.",
                ],
                "verification": (
                    "Ueberpruefe mit smartphone_list_elements, dass der "
                    "Schalter Dunkles Design checked false ist."
                ),
            }

            with self.assertRaisesRegex(ValueError, "must be written in English"):
                write_skill(Path(tmp), **german_skill)

    def test_rejects_short_description(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                write_skill(Path(tmp), **{**_VALID_SKILL, "description": "too short"})

    def test_rejects_empty_lists(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            for field in ("triggers", "tested_environments", "rules", "flow", "device_variants", "failure_modes"):
                with self.assertRaises(ValueError):
                    write_skill(Path(tmp), **{**_VALID_SKILL, field: []})

    def test_rejects_empty_verification(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                write_skill(Path(tmp), **{**_VALID_SKILL, "verification": "   "})

    def test_rejects_empty_context_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            for field in ("app_context", "starting_context"):
                with self.assertRaises(ValueError):
                    write_skill(Path(tmp), **{**_VALID_SKILL, field: "   "})

    def test_allows_localized_ui_labels_inside_english_skill_text(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = write_skill(
                Path(tmp),
                **{
                    **_VALID_SKILL,
                    "id": "display.dark_mode_on_settings",
                    "title": "Enable Dark Mode via Settings",
                    "description": "Enable Android dark mode through the Settings app reliably.",
                    "triggers": ["darkmodus an", "dark mode on", "dunkles design aktivieren"],
                    "app_context": "Android Settings app (`com.android.settings`), Display area.",
                    "rules": [
                        "Match labels such as Dark mode, Dark theme, or Dunkles Design.",
                        "Do not tap the switch if it is already checked true.",
                    ],
                    "flow": [
                        "Call smartphone_list_elements to inspect the current screen.",
                        "Open Settings if the current screen is not already in Settings.",
                        "Find the Display entry by label, then tap its center.",
                    ],
                    "device_variants": [
                        "German UI: the target switch may be labeled Dunkles Design.",
                    ],
                },
            )

            self.assertEqual(Path(tmp) / "display" / "dark_mode_on_settings.md", path)


class SaveSkillToolDocumentationTest(unittest.TestCase):
    def test_save_skill_tool_documents_atomic_english_skill_rules(self) -> None:
        source = inspect.getsource(register_save_skill_tool)

        self.assertIn("All saved skills must be written in English", source)
        self.assertIn("category.specific_goal_method", source)
        self.assertIn("display.dark_mode_on_settings", source)
        self.assertIn("display.dark_mode_off_quick_settings", source)
        self.assertIn("tested_environments", source)
        self.assertIn("app_context", source)
        self.assertIn("starting_context", source)
        self.assertIn("device_variants", source)
        self.assertIn("failure_modes", source)
        self.assertIn("Do not overwrite a different approach", source)
        self.assertIn("Save different methods as separate skills", source)


def _fixture_skills_dir() -> Path:
    return Path(__file__).parent / "fixtures" / "skills"


if __name__ == "__main__":
    unittest.main()

import unittest

from llmsmartphone.tools.screen import screenshot_analysis_instruction


class ScreenToolTest(unittest.TestCase):
    def test_screenshot_instruction_requires_analysis_not_presentation(self) -> None:
        instruction = screenshot_analysis_instruction()

        self.assertIn("evidence for the assistant to inspect", instruction)
        self.assertIn("Analyze the image before answering", instruction)
        self.assertIn("Do not present", instruction)
        self.assertIn("verification is uncertain", instruction)
        self.assertIn("smartphone_list_elements", instruction)
        self.assertIn("instead of guessing", instruction)


if __name__ == "__main__":
    unittest.main()

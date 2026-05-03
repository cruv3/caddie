import io
import unittest
from contextlib import redirect_stdout

import server


class ServerCliTest(unittest.TestCase):
    def test_print_system_prompt_prints_prompt_and_exits_without_starting_server(self) -> None:
        output = io.StringIO()

        with redirect_stdout(output):
            exit_code = server.main(["--print-system-prompt"])

        prompt = output.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("autonomous Android phone control agent", prompt)
        self.assertIn("Screenshots are evidence for you to inspect", prompt)
        self.assertIn("smartphone_list_elements", prompt)
        self.assertNotIn("smarthone_list_elements", prompt)


if __name__ == "__main__":
    unittest.main()

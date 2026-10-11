"""Safety regressions: rejected configuration must never reach a DB subprocess."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("postgres_runner", Path(__file__).with_name("run-postgres-tests.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class DatabaseSafetyTest(unittest.TestCase):
    def rejected(self, url):
        config = {"DB_URL": url, "DB_USERNAME": "test-only", "DB_PASSWORD": "test-only"}
        with patch.dict(runner.os.environ, config), patch.object(runner.subprocess, "run") as command:
            with self.assertRaises(SystemExit):
                runner.main()
            command.assert_not_called()

    def test_remote_database_is_rejected(self):
        self.rejected("jdbc:postgresql://production.example:5432/database")

    def test_existing_schema_override_is_rejected(self):
        self.rejected("jdbc:postgresql://localhost:5432/database?currentSchema=public")

    def test_search_path_options_are_rejected(self):
        self.rejected("jdbc:postgresql://127.0.0.1:5432/database?options=-c%20search_path%3Dpublic")

    def test_credentials_in_url_are_rejected(self):
        self.rejected("jdbc:postgresql://test-only:test-only@localhost:5432/database")


if __name__ == "__main__":
    unittest.main()

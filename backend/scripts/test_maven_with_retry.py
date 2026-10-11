"""Exercise the real retry shell script without Maven downloads or waiting."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


def find_posix_shell():
    shell = shutil.which("sh")
    if shell:
        return shell
    if os.name == "nt":
        git = shutil.which("git")
        if git:
            shell = Path(git).resolve().parent.parent / "bin" / "sh.exe"
            if shell.is_file():
                return str(shell)
    return None


POSIX_SHELL = find_posix_shell()
RETRY_SCRIPT = Path(__file__).with_name("maven-with-retry.sh").resolve()


def shell_path(path):
    path = path.resolve()
    if os.name == "nt":
        # Git's POSIX shell interprets a colon in PATH as a separator.
        return f"/{path.drive[0].lower()}{path.as_posix()[2:]}"
    return path.as_posix()


@unittest.skipUnless(POSIX_SHELL, "A POSIX sh (for example Git Bash) is required")
class MavenRetryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="maven retry test ")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.count_file = self.directory / "count"
        self.argument_log = self.directory / "arguments"
        self.sleep_log = self.directory / "sleeps"
        self.write_stub("mvn", """#!/bin/sh
count=0
if [ -f "$MOCK_MVN_COUNT" ]; then
    read -r count < "$MOCK_MVN_COUNT"
fi
count=$((count + 1))
printf '%s\\n' "$count" > "$MOCK_MVN_COUNT"
printf '%s\\n' "CALL:$count" >> "$MOCK_MVN_ARGUMENTS"
for argument in "$@"; do
    printf '%s\\n' "ARG:$argument" >> "$MOCK_MVN_ARGUMENTS"
done
if [ "$count" -lt "$MOCK_SUCCESS_ATTEMPT" ]; then
    printf '%s\\n' "$MOCK_FAILURE_MESSAGE" >&2
    exit "$MOCK_FAILURE_CODE"
fi
exit 0
""")
        self.write_stub("sleep", """#!/bin/sh
printf '%s\\n' "$@" >> "$MOCK_SLEEP_LOG"
""")

    def write_stub(self, name, contents):
        stub = self.directory / name
        stub.write_text(contents, encoding="utf-8", newline="\n")
        stub.chmod(0o755)

    def run_retry(self, *arguments, success_attempt=1, failure_code=7,
                  failure_message="HTTP 429 Too Many Requests"):
        environment = os.environ.copy()
        environment.update({
            "MOCK_MVN_COUNT": self.count_file.as_posix(),
            "MOCK_MVN_ARGUMENTS": self.argument_log.as_posix(),
            "MOCK_SLEEP_LOG": self.sleep_log.as_posix(),
            "MOCK_SUCCESS_ATTEMPT": str(success_attempt),
            "MOCK_FAILURE_CODE": str(failure_code),
            "MOCK_FAILURE_MESSAGE": failure_message,
        })
        # Set PATH inside sh because Git Bash may rewrite an inherited PATH.
        # Only the stubs are discoverable while the real helper executes.
        command = 'PATH="$1"; export PATH; shift; retry_script=$1; shift; . "$retry_script"'
        return subprocess.run(
            [POSIX_SHELL, "-c", command, "maven retry test",
             shell_path(self.directory), RETRY_SCRIPT.as_posix(), *arguments],
            env=environment, cwd=self.directory, text=True,
            capture_output=True, timeout=10,
        )

    def calls(self):
        calls = []
        if self.argument_log.exists():
            for line in self.argument_log.read_text(encoding="utf-8").splitlines():
                if line.startswith("CALL:"):
                    calls.append([])
                else:
                    self.assertTrue(line.startswith("ARG:"), line)
                    calls[-1].append(line[4:])
        return calls

    def sleeps(self):
        if not self.sleep_log.exists():
            return []
        return self.sleep_log.read_text(encoding="utf-8").splitlines()

    def test_immediate_success_does_not_retry_or_sleep(self):
        result = self.run_retry("dependency:resolve")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(self.calls()), 1)
        self.assertEqual(self.sleeps(), [])

    def test_third_attempt_success_uses_exponential_backoff(self):
        result = self.run_retry("dependency:resolve", success_attempt=3)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(self.calls()), 3)
        self.assertEqual(self.sleeps(), ["30", "60"])
        output = result.stdout + result.stderr
        for attempt in range(1, 4):
            self.assertIn(f"Maven attempt {attempt}/4", output)

    def test_persistent_rate_limit_is_bounded_and_preserves_exit_code(self):
        result = self.run_retry("dependency:resolve", success_attempt=99)
        self.assertEqual(result.returncode, 7, result.stdout + result.stderr)
        self.assertEqual(len(self.calls()), 4)
        self.assertEqual(self.sleeps(), ["30", "60", "120"])
        output = result.stdout + result.stderr
        self.assertIn("Maven attempt 4/4", output)
        self.assertNotIn("Maven attempt 5/4", output)

    def test_maven_flags_and_arguments_are_preserved_on_every_attempt(self):
        arguments = ["clean", "package", "-DskipTests", "-Dbuild.label=with spaces"]
        result = self.run_retry(*arguments, success_attempt=2)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        expected = ["-B", "--no-transfer-progress", "-U", *arguments]
        self.assertEqual(self.calls(), [expected, expected])

    def test_compile_failure_is_also_bounded(self):
        result = self.run_retry(
            "package", success_attempt=99, failure_code=1,
            failure_message="[ERROR] COMPILATION ERROR",
        )
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(len(self.calls()), 4)
        self.assertEqual(self.sleeps(), ["30", "60", "120"])


if __name__ == "__main__":
    unittest.main()

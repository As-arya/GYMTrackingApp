"""Exercise the real hooks with synthetic data in an ignored tmp repository.

Run: python scripts/check_git_safety.py [path/to/gitleaks]
Requires Git and Gitleaks. Fixtures remain in tmp/ for inspection.
"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def main():
    root = Path(__file__).resolve().parents[1]
    git = shutil.which("git")
    scanner = (sys.argv[1] if len(sys.argv) > 1 else
               shutil.which("gitleaks") or str(root / "tmp/security-tools/gitleaks.exe"))
    if not git or not Path(scanner).is_file():
        raise SystemExit("Install Git and Gitleaks, or pass the scanner executable path.")
    scanner = Path(scanner).resolve()
    (root / "tmp").mkdir(exist_ok=True)
    fixture = Path(tempfile.mkdtemp(prefix="git-safety-check-", dir=root / "tmp"))
    env = dict(os.environ, PATH=str(scanner.parent) + os.pathsep + os.environ["PATH"])
    for name in (".gitignore", ".gitleaks.toml", ".githooks/pre-commit", ".githooks/pre-push"):
        target = fixture / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / name, target)
        if name.startswith(".githooks/"):
            target.chmod(0o755)

    def run(*args, expected=0, override_env=None):
        result = subprocess.run([git, *args], cwd=fixture, env=override_env or env,
                                capture_output=True, text=True)
        assert result.returncode == expected, (
            f"{args[:3]} returned {result.returncode}, expected {expected}: "
            + result.stderr[-1500:])
        return result

    def write(name, content):
        path = fixture / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    run("init", "-b", "main")
    run("config", "core.hooksPath", ".githooks")
    run("config", "user.name", "Hook self-check")
    run("config", "user.email", "hook-check@example.invalid")
    run("config", "commit.gpgsign", "false")
    write("safe.txt", "Synthetic fixture only.\n")
    run("add", ".")
    run("hook", "run", "pre-commit")

    write(".env", "EXAMPLE=fixture\n")
    run("add", "--force", ".env")
    assert ".env" in run("hook", "run", "pre-commit", expected=1).stderr
    run("rm", "--cached", ".env")

    secret = "gsk" + "_" + "A1b2C3d4" * 7
    write("unsafe.txt", secret)
    run("add", "unsafe.txt")
    write("unsafe.txt", "Clean working copy; index still contains the synthetic key.\n")
    assert "leaks found" in run("hook", "run", "pre-commit", expected=1).stderr
    run("add", "unsafe.txt")
    run("hook", "run", "pre-commit")

    write("renamed.json", json.dumps(dict(profile=dict(name="Fixture", age=25))))
    run("add", "renamed.json")
    run("hook", "run", "pre-commit", expected=1)
    run("rm", "--cached", "renamed.json")

    write("signing.txt", "store" + "Password = " + json.dumps("synthetic-demo-password"))
    run("add", "signing.txt")
    run("hook", "run", "pre-commit", expected=1)
    run("rm", "--cached", "signing.txt")

    missing_env = dict(env, PATH=str(Path(git).parent))
    result = run("hook", "run", "pre-commit", expected=1, override_env=missing_env)
    assert "install Gitleaks" in result.stderr

    run("commit", "-m", "Synthetic clean fixture")
    run("hook", "run", "pre-push")
    write("unsafe.txt", secret)
    run("add", "unsafe.txt")
    # Bypass hooks only in this disposable fixture to test deleted-secret history.
    run("-c", "core.hooksPath=", "commit", "-m", "Synthetic secret fixture")
    write("unsafe.txt", "Removed synthetic key.\n")
    run("add", "unsafe.txt")
    run("commit", "-m", "Remove synthetic key")
    run("hook", "run", "pre-push", expected=1)

    write("local.properties", "sdk.dir=fixture\n")
    run("add", "--force", "local.properties")
    run("-c", "core.hooksPath=", "commit", "-m", "Synthetic private path")
    run("rm", "--cached", "local.properties")
    run("commit", "-m", "Untrack synthetic private path")
    result = run("hook", "run", "pre-push", expected=1)
    assert "private/ignored paths exist in Git history" in result.stderr
    print("PASS: clean commit/push, forced private file, staged key, renamed backup,")
    print("signing password, missing scanner, deleted key and private path history.")


if __name__ == "__main__":
    main()

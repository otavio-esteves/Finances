#!/usr/bin/env python3
"""Validate a local PDF corpus on an isolated emulator; never add the PDFs to an APK."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Emulator serial from adb devices")
    parser.add_argument("--cases", type=Path, default=Path("samples/private/cases.json"))
    parser.add_argument("--adb", default=shutil.which("adb") or str(
        Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb"))
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use an emulator to keep this validation isolated from personal app data.")
    directory = args.cases.resolve().parent
    cases = json.loads(args.cases.read_text())
    if not cases:
        parser.error("The corpus must contain at least one case.")
    ids = set()
    for case in cases:
        case_id = case["id"]
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", case_id) or case_id in ids:
            parser.error("Each case needs a unique alphanumeric id (underscore and hyphen allowed).")
        ids.add(case_id)
        if case["kind"] not in ("CARD_BILL", "BANK_STATEMENT"):
            parser.error("kind must be CARD_BILL or BANK_STATEMENT.")
        source = (directory / case["file"]).resolve()
        if not source.is_relative_to(directory) or not source.is_file():
            parser.error("Each PDF must be a file inside the private corpus directory.")

    package = "br.com.otavioesteves.finances.validation"
    cache = "cache/private-pdf-validation"

    def adb(*command, **kwargs):
        return subprocess.run([args.adb, "-s", args.serial, *command], check=True, **kwargs)

    def stage(name, content):
        adb("shell", "-T", "run-as", package, "sh", "-c", f'"cat > {cache}/{name}"',
            input=content, stdout=subprocess.DEVNULL)

    output = directory / "validation"
    output.mkdir(exist_ok=True)
    adb("shell", "run-as", package, "mkdir", "-p", cache)
    try:
        # Remove previous results so a crash cannot be mistaken for a successful run.
        adb("shell", "run-as", package, "rm", "-f", f"{cache}/results.json")
        for case in cases:
            stage(f'{case["id"]}.pdf', (directory / case["file"]).read_bytes())
        stage("cases.json", json.dumps(cases).encode())
        run = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                  "br.com.otavioesteves.finances.data.statement.PrivatePdfCorpusTest",
                  "-e", "privatePdfValidation", "true",
                  f"{package}.test/androidx.test.runner.AndroidJUnitRunner", capture_output=True)
        (output / "instrumentation.txt").write_bytes(run.stdout + run.stderr)
        result = adb("exec-out", "run-as", package, "cat", f"{cache}/results.json", capture_output=True)
        (output / "results.json").write_bytes(result.stdout)
        if not result.stdout.strip():
            raise RuntimeError(f"Validation did not produce results; see {output / 'instrumentation.txt'}")
        results = json.loads(result.stdout)
        for case in cases:
            for suffix in ("extracted.txt",):
                diagnostic = subprocess.run(
                    [args.adb, "-s", args.serial, "exec-out", "run-as", package, "cat", f'{cache}/{case["id"]}.{suffix}'],
                    capture_output=True)
                if diagnostic.returncode == 0 and not diagnostic.stdout.startswith(b"cat: "):
                    (output / f'{case["id"]}.{suffix}').write_bytes(diagnostic.stdout)
        for result in results:
            print(f'{result["id"]}: {"OK" if result["ok"] else "FAIL"}' +
                  (f' ({result["count"]} entries)' if "count" in result else ""))
        print(f"Private diagnostics: {output}")
        return 0 if len(results) == len(cases) and all(r["ok"] for r in results) else 1
    finally:
        # Only this dedicated cache of the isolated validation app is removed.
        adb("shell", "run-as", package, "rm", "-rf", cache)


if __name__ == "__main__":
    raise SystemExit(main())

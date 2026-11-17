#!/usr/bin/env python3
"""Three guards that had a fix and no test (17 Nov 2026, the register's NONE rows).

  publish_jobs_hold_no_secrets (BC-192): a job that publishes a GitHub release (softprops'
      action, `gh release create/upload/edit`) may see no secret but the GITHUB_TOKEN. Signing
      keys and the Firebase file live in the build job; the publish job only takes its artifact.
      A release step added back into a job that holds secrets fails here.
  no_tracked_ignored_files (BC-195): no file git still tracks matches .gitignore (a .pyc left
      tracked was rewritten by every local run). `git ls-files -ci --exclude-standard` is empty.
  csv_reads_are_decoded (BC-225): every CsvImport.parse / ProductCsv.parse in the app and the
      computer reads text that CsvImport.decode made from the file's bytes (UTF-16 by its mark,
      UTF-8, else Windows-1252: Excel's "CSV (Comma delimited)"), never readText() or a reader
      that assumes UTF-8.

    python3 scripts/check_repo_guards.py         # exit 1 and a line per fault
"""
import pathlib, re, subprocess, sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
PUBLISH = re.compile(r"softprops/action-gh-release|\bgh release (create|upload|edit)\b")
SECRET = re.compile(r"secrets\.([A-Za-z0-9_]+)")


def publish_jobs_hold_no_secrets():
    faults = []
    for wf in sorted((ROOT / ".github/workflows").glob("*.yml")):
        doc = yaml.safe_load(wf.read_text()) or {}
        for name, job in (doc.get("jobs") or {}).items():
            text = yaml.safe_dump(job)
            if not PUBLISH.search(text):
                continue
            held = sorted(set(SECRET.findall(text)) - {"GITHUB_TOKEN"})
            if held:
                faults.append("%s job %s publishes a release and holds %s: publish from a job that only "
                              "takes the build's artifact" % (wf.name, name, ", ".join(held)))
    return faults


def no_tracked_ignored_files():
    out = subprocess.run(["git", "ls-files", "-ci", "--exclude-standard"], cwd=ROOT,
                         capture_output=True, text=True, check=True).stdout.split()
    return ["%s is tracked though .gitignore ignores it: git rm --cached it" % f for f in out]


CSV_PARSE = re.compile(r"\b(CsvImport|ProductCsv)\.parse\s*\(")


def csv_reads_are_decoded():
    faults = []
    for base in ("app/src/main", "desktop/src/main"):
        for p in sorted((ROOT / base).rglob("*.kt")):
            lines = p.read_text().splitlines()
            for i, line in enumerate(lines):
                if not CSV_PARSE.search(line):
                    continue
                near = "\n".join(lines[max(0, i - 3):i + 1])
                if "CsvImport.decode(" not in near:
                    faults.append("%s:%d parses CSV text that CsvImport.decode did not make" %
                                  (p.relative_to(ROOT), i + 1))
    return faults


def main():
    faults = publish_jobs_hold_no_secrets() + no_tracked_ignored_files() + csv_reads_are_decoded()
    for f in faults:
        print("FAIL " + f)
    if faults:
        sys.exit(1)
    print("repo guards: release jobs hold no secrets, nothing ignored is tracked, every CSV read is decoded")


if __name__ == "__main__":
    main()

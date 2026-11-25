#!/usr/bin/env python3
"""Three guards that had a fix and no test (17 Nov 2026, the register's NONE rows).

  publish_jobs_hold_no_secrets (BC-192): a job that publishes a GitHub release (softprops'
      action, `gh release create/upload/edit`) may see no secret but the GITHUB_TOKEN. Signing
      keys and the Firebase file live in the build job; the publish job only takes its artifact.
      A release step added back into a job that holds secrets fails here.
  no_tracked_ignored_files (BC-195): no file git still tracks matches .gitignore (a .pyc left
      tracked was rewritten by every local run). `git ls-files -ci --exclude-standard` is empty.
  shown_bitmaps_never_recycled (BC-235): a bitmap a view was given (setImageBitmap) is never
      recycled by hand, in the same function or, for a field of the screen, anywhere in the file.
      A list row, a pager page or a fading dialog may draw it once more, and a recycled bitmap a
      view still draws ends the app ("Canvas: trying to use a recycled bitmap", Play, 4 Nov 2026,
      the poster's design pager). Let it go to the collector. By name only: an alias (old = shown)
      is not followed, so the rule is also written beside each such place.
  left_margins_name_their_side (BC-237): a view laid over a picture by the picture's own left is
      placed through ui/PlaceAt.params, which hangs it from LEFT | TOP. A FrameLayout hangs a child
      with no gravity from its START, the right edge in an Urdu, Sindhi, Arabic or Farsi window, and
      then never reads leftMargin: the posters' live 3D hero stood beside its place on the owner's
      Urdu phone (19 Nov 2026). No other file of the app sets leftMargin (or rightMargin) in code;
      a start or end margin, which turns with the language on purpose, is not a left margin.
  csv_reads_are_decoded (BC-225): every CsvImport.parse / ProductCsv.parse in the app and the
      computer reads text that CsvImport.decode made from the file's bytes (UTF-16 by its mark,
      UTF-8, else Windows-1252: Excel's "CSV (Comma delimited)"), never readText() or a reader
      that assumes UTF-8.

  test_names_are_jvm_names (BC-287): a test written as a backtick name may hold none of
      : . ; [ ] / < > and backslash — the JVM refuses them in a method name and the module's tests do not
      compile (lesson 38). It slipped through on 25 Nov 2026 ("the first-use question: ...") and
      the Windows and Android builds both failed at :desktop:compileTestKotlin.

  gradle_cache_on_except_release (BC-290): every workflow that sets up Gradle keeps its dependency
      cache on (cache-read-only for anything but main and kmp-* pushes), except aab.yml, the Play
      bundle, which is built from fresh downloads. With the cache off every run fetched every jar
      from Maven Central, and Sonatype's rate limit (429) failed the Android build twice on 25 Nov 2026.

  windows_cancel_asks_first (BC-292): on the computer every Cancel that closes a form is a CancelButton, which asks
      "Discard what you typed?" when the form holds typing, as the phone's FormGuard does (the owner's yes, 25 Nov
      2026). Only two Cancels are plain buttons, by name: the entry form's "put the line back" and the Cancel of the
      discard question itself (AskDialog).

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


SHOWN = re.compile(r"\bsetImageBitmap\(([^)]*)\)")
WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
FIELD = re.compile(r"^ {4}(?:private |internal |protected )?(?:val|var) ([A-Za-z_][A-Za-z0-9_]*)")
FUN = re.compile(r"^\s*(?:private |internal |override |protected |public |inline |suspend )*fun\b")
NOT_NAMES = {"null", "it", "this", "true", "false", "as", "let", "also", "get", "set"}


def shown_bitmaps_never_recycled(root=ROOT):
    faults = []
    for p in sorted((root / "app/src/main").rglob("*.kt")):
        lines = p.read_text().splitlines()
        shown_lines = [i for i, l in enumerate(lines) if SHOWN.search(l)]
        if not shown_lines:
            continue
        fields = {m.group(1) for l in lines for m in [FIELD.match(l)] if m}
        starts = [i for i, l in enumerate(lines) if FUN.match(l)] + [len(lines)]

        def fun_of(i):
            begun = [s for s in starts if s <= i]
            return begun[-1] if begun else 0

        local, everywhere = {}, set()
        for i in shown_lines:
            for arg in SHOWN.findall(lines[i]):
                names = set(WORD.findall(arg)) - NOT_NAMES
                local.setdefault(fun_of(i), set()).update(names)
                everywhere |= names & fields
        for i, l in enumerate(lines):
            if "recycle()" not in l:
                continue
            words = set(WORD.findall(l))
            hit = (words & local.get(fun_of(i), set())) | (words & everywhere)
            if hit:
                faults.append("%s:%d recycles %s, a bitmap a view was given: let it go to the collector" %
                              (p.relative_to(root), i + 1, ", ".join(sorted(hit))))
    return faults


SIDE_MARGIN = re.compile(r"\b(leftMargin|rightMargin)\s*=[^=]")
PLACE_AT = "app/src/main/java/com/innovation313/roshankhata/ui/PlaceAt.kt"


def left_margins_name_their_side(root=ROOT):
    faults = []
    for p in sorted((root / "app/src/main").rglob("*.kt")):
        rel = str(p.relative_to(root))
        if rel == PLACE_AT:
            continue
        for i, l in enumerate(p.read_text().splitlines()):
            code = l.split("//")[0]
            if code.lstrip().startswith("*"):
                continue
            m = SIDE_MARGIN.search(code)
            if m:
                faults.append("%s:%d sets %s in code: place the view with PlaceAt.params (hung from LEFT | TOP), "
                              "or it stands beside its place in a right-to-left window" % (rel, i + 1, m.group(1)))
    if not (root / PLACE_AT).exists() or "Gravity.LEFT" not in (root / PLACE_AT).read_text():
        faults.append("%s must hang its view from Gravity.LEFT" % PLACE_AT)
    return faults


TEST_NAME = re.compile(r"fun\s+`([^`]+)`")
JVM_REFUSED = re.compile(r"[:.;\[\]/<>\\]")


def test_names_are_jvm_names(root=ROOT):
    faults = []
    for base in ("app/src/test", "app/src/androidTest", "shared/src", "desktop/src/test"):
        for p in sorted((root / base).rglob("*.kt")):
            for i, l in enumerate(p.read_text().splitlines()):
                for m in TEST_NAME.finditer(l):
                    bad = JVM_REFUSED.search(m.group(1))
                    if bad:
                        faults.append("%s:%d a test name holds '%s', which the JVM refuses in a method name"
                                      % (p.relative_to(root), i + 1, bad.group(0)))
    return faults


RELEASE_UNCACHED = {"aab.yml"}


def gradle_cache_on_except_release(root=ROOT):
    faults = []
    for p in sorted((root / ".github/workflows").glob("*.yml")):
        jobs = (yaml.safe_load(p.read_text()) or {}).get("jobs") or {}
        for name, job in jobs.items():
            for step in job.get("steps") or []:
                if "setup-gradle" not in str(step.get("uses", "")):
                    continue
                off = str((step.get("with") or {}).get("cache-disabled", "false")).lower() == "true"
                if p.name in RELEASE_UNCACHED and not off:
                    faults.append("%s job %s: the Play bundle must be built with the Gradle cache off" % (p.name, name))
                if p.name not in RELEASE_UNCACHED and off:
                    faults.append("%s job %s turns the Gradle cache off: every run then downloads every jar from "
                                  "Maven Central, which answers 429 (keep cache-read-only for other branches)" % (p.name, name))
    return faults


PLAIN_CANCEL_OK = {("EntryEditor.kt", "goods.putBack()"), ("Fluent.kt", "onDismiss, kind = ButtonKind.STANDARD)"),
                   ("Fluent.kt", "guardedClose(onClick)")}  # the last is CancelButton's own body


def windows_cancel_asks_first(root=ROOT):
    faults = []
    for p in sorted((root / "desktop/src/main/kotlin").rglob("*.kt")):
        for i, l in enumerate(p.read_text().splitlines()):
            if "FluentButton(word(W.cancel)" not in l:
                continue
            if any(p.name == f and mark in l for f, mark in PLAIN_CANCEL_OK):
                continue
            faults.append("%s:%d a Cancel that closes a form is a CancelButton, so typing is never thrown away unasked"
                          % (p.relative_to(root), i + 1))
    return faults


def main():
    faults = (publish_jobs_hold_no_secrets() + no_tracked_ignored_files() + csv_reads_are_decoded()
              + shown_bitmaps_never_recycled() + left_margins_name_their_side() + test_names_are_jvm_names()
              + gradle_cache_on_except_release()
              + windows_cancel_asks_first())
    for f in faults:
        print("FAIL " + f)
    if faults:
        sys.exit(1)
    print("repo guards: release jobs hold no secrets, nothing ignored is tracked, every CSV read is decoded, "
          "no bitmap a view was given is recycled, every left margin names its side, every test name is a JVM name, the Gradle cache is on except for the Play bundle, every Windows Cancel asks first")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""The bug-class register stays true to the code (docs/bug-classes.tsv; how to add a row: docs/bug-classes.md).

The owner's rule (16 Nov 2026): every class of bug found, and every guard built against it, is kept in one list with the
test that holds it, so the suite catches the same kind of mistake quickly next time. This checks the list, not the code's
behaviour (lesson 125: drift protection, not proof). It fails when

  - a required column is empty, an id is malformed or used twice, or the date is not YYYY-MM-DD (or <=YYYY-MM-DD: on
    or before that day, for a class whose day is not on record);
  - a guard's file does not exist or does not contain its symbol as a word;
  - a test's file does not exist, or it does not declare the class named after "::" (a script check is named by its
    path alone, e.g. scripts/check_strings.py);
  - a Kotlin test file named for a class has no test that runs (every @Test also @Ignore'd);
  - a fixed class has NONE in its tests column. Until 23 Nov 2026 NONE passed as the honest backlog; the last such
    class (BC-168) got its test that day, and from then on a class goes in with its test in the same commit (the
    owner's rule of 16 Nov 2026). Only a row whose class starts "OPEN FAULT:" (found, not yet fixed) may say NONE, and
    every run prints those ids.

    python3 scripts/check_bug_classes.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REGISTER = ROOT / "docs/bug-classes.tsv"
COLUMNS = ["id", "area", "class", "root_cause", "guard", "tests", "lesson", "found"]
REQUIRED = ["id", "area", "class", "root_cause", "guard", "tests", "found"]
AREAS = {"hudhud", "money", "schema", "ui", "security", "i18n", "sync", "backup", "print", "perf", "release", "stock",
         "privacy", "build"}


def word_in(path: pathlib.Path, word: str) -> bool:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return False
    return re.search(r"(?<![\w$])" + re.escape(word) + r"(?![\w$])", text) is not None


def declares(path: pathlib.Path, name: str) -> bool:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return False
    return re.search(r"^\s*(?:internal\s+|private\s+|abstract\s+|open\s+)*(?:class|object)\s+" + re.escape(name) + r"\b",
                     text, re.M) is not None


def runs_a_test(path: pathlib.Path) -> bool:
    """A Kotlin test file has at least one @Test that is not @Ignore'd (a whole class @Ignore'd runs none)."""
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return False
    if re.search(r"@Ignore\b[^\n]*\n(?:\s*@[^\n]*\n)*\s*(?:internal\s+|open\s+)*class\s", text):
        return False
    tests = len(re.findall(r"@Test\b", text))
    ignored = len(re.findall(r"@Ignore\b", text))
    return tests > ignored


def check(rows):
    failures = []
    seen = set()
    no_test = []
    for n, row in rows:
        where = f"line {n}"
        if len(row) != len(COLUMNS):
            failures.append(f"{where}: {len(row)} columns, expected {len(COLUMNS)} ({', '.join(COLUMNS)})")
            continue
        r = dict(zip(COLUMNS, (c.strip() for c in row)))
        where = f"line {n} ({r['id'] or '?'})"
        for c in REQUIRED:
            if not r[c]:
                failures.append(f"{where}: column '{c}' is empty")
        if r["id"]:
            if not re.fullmatch(r"BC-\d{3}", r["id"]):
                failures.append(f"{where}: id must be BC-nnn")
            if r["id"] in seen:
                failures.append(f"{where}: id {r['id']} is used twice")
            seen.add(r["id"])
        if r["area"] and r["area"] not in AREAS:
            failures.append(f"{where}: area '{r['area']}' is not one of {sorted(AREAS)}")
        if r["found"] and not re.fullmatch(r"(<=)?\d{4}-\d{2}-\d{2}", r["found"]):
            failures.append(f"{where}: found must be YYYY-MM-DD or <=YYYY-MM-DD")
        if r["lesson"] and not re.fullmatch(r"-|\d+(,\s*\d+)*", r["lesson"]):
            failures.append(f"{where}: lesson must be '-' or numbers")
        if r["guard"] and r["guard"] != "NONE":
            for g in (x.strip() for x in r["guard"].split(";")):
                path, _, symbol = g.partition("::")
                p = ROOT / path
                if not p.is_file():
                    failures.append(f"{where}: guard file {path} does not exist")
                elif symbol and not word_in(p, symbol):
                    failures.append(f"{where}: guard symbol {symbol} is not in {path}")
        if r["tests"] == "NONE":
            no_test.append(r["id"])
            if not r["class"].startswith("OPEN FAULT:"):
                failures.append(f"{where}: no test (NONE): a fixed class goes in with the test that holds it "
                                f"(an open fault's class starts 'OPEN FAULT:')")
        elif r["tests"]:
            for t in (x.strip() for x in r["tests"].split(";")):
                path, _, name = t.partition("::")
                p = ROOT / path
                if not p.is_file():
                    failures.append(f"{where}: test file {path} does not exist")
                elif name and not declares(p, name):
                    failures.append(f"{where}: {path} declares no class {name}")
                elif name and p.suffix == ".kt" and not runs_a_test(p):
                    failures.append(f"{where}: {path} runs no test (every @Test is @Ignore'd)")
                elif not name and p.suffix not in (".py", ".sh"):
                    failures.append(f"{where}: {path} needs ::ClassName (only a script is named by its path alone)")
    return failures, no_test, len(seen)


def main() -> int:
    if not REGISTER.is_file():
        print(f"{REGISTER} is missing")
        return 1
    lines = REGISTER.read_text(encoding="utf-8").splitlines()
    if not lines or [c.strip() for c in lines[0].split("\t")] != COLUMNS:
        print(f"the first line must be the header: {chr(9).join(COLUMNS)}")
        return 1
    rows = [(i + 1, l.split("\t")) for i, l in enumerate(lines) if i > 0 and l.strip() and not l.startswith("#")]
    failures, no_test, count = check(rows)
    if failures:
        print("\n".join(failures))
        print(f"bug-class register: {len(failures)} problem(s)")
        return 1
    print(f"bug-class register OK: {count} classes, {count - len(no_test)} with their test, "
          f"{len(no_test)} open fault(s){': ' + ', '.join(no_test) if no_test else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

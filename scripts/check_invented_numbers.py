#!/usr/bin/env python3
"""No real phone number goes into this public repository (lesson 80, bug class BC-077).

On 6 Nov 2026 five customers' numbers read off the owner's photo of the Duplicates screen were written into a comment
of a public file, and the whole history had to be rewritten. What the owner photographs are Pakistani mobile numbers,
so this check looks for exactly that shape — 03xx followed by seven digits, with +92 / 0092 / 92 in front or a space
or hyphen between — in every file git tracks, in any script's digits (Urdu, Arabic, Hindi, Bengali figures are read
as 0-9 first). A number passes only when it is

  - one of the invented family (the numbers lesson 80 names): 0300 1234567, 0300 7654321, 0300 000000x; or
  - in INVENTED below: a number a person read and wrote down as made up (repeated or sequential digits, a test's
    "one digit apart"). A new made-up number is added here on purpose, in the same commit as the test that uses it —
    that is the point: somebody looks at every number before it is public.

Drift protection, not proof (lesson 125): a real number in another shape (a landline, another country's, a CNIC) is
not looked for — barcodes, timestamps and amounts have the same length — and a real NAME cannot be told from an
invented one by a script. Names stay the reviewer's job.

    python3 scripts/check_invented_numbers.py            # fail on a number that is neither family nor listed
    python3 scripts/check_invented_numbers.py --report   # also list every number found and where
"""
import re
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The family every new test should use: (operator code, subscriber number).
FAMILY = {("300", "1234567"), ("300", "7654321")} | {("300", "000000%d" % d) for d in range(10)}

# Numbers already in the repository, each read on 16 Nov 2026 and judged made up (patterned or one digit apart from
# the family). Operator code and subscriber number, as the pattern below splits them.
INVENTED = {
    ("300", "0000101"), ("300", "1111111"), ("300", "1112111"), ("300", "1112223"), ("300", "1234568"),
    ("300", "1244567"), ("300", "9999999"),
    ("301", "0000505"), ("301", "1111111"), ("301", "1234567"), ("301", "2222222"), ("301", "5566778"),
    ("302", "2222222"),
    ("311", "1111111"), ("311", "1222333"),
    ("312", "4433221"),
    ("321", "4445556"), ("321", "7654321"),
    ("333", "0000202"), ("333", "7654321"), ("333", "9988776"), ("333", "9998887"),
    ("345", "0000303"), ("345", "1112223"), ("345", "1122334"), ("345", "1234567"), ("345", "9998887"),
    ("348", "0000000"),
}

# A Pakistani mobile number: optional country code or trunk 0, the operator's 3xx, the subscriber's seven digits.
PK_MOBILE = re.compile(r"(?<![\w])(?:\+92|0092|92|0)[ \-]?(3\d\d)[ \-]?(\d{7})(?![\w])")

SKIP_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".pdf", ".bin", ".gz", ".zip", ".ttf", ".otf", ".jar",
                 ".keystore", ".jks", ".db", ".ico", ".mp4", ".ktx", ".filamat", ".glb", ".whl", ".so"}


def ascii_digits(text: str) -> str:
    """Every script's figures as 0-9, so an Urdu or Bengali number is looked at too."""
    return "".join(str(unicodedata.digit(c)) if unicodedata.category(c) == "Nd" else c for c in text)


def is_invented(operator: str, subscriber: str) -> bool:
    return (operator, subscriber) in FAMILY or (operator, subscriber) in INVENTED


def numbers_in(text: str):
    """(operator, subscriber, as written) for every Pakistani-mobile-shaped number in [text]."""
    for m in PK_MOBILE.finditer(ascii_digits(text)):
        yield m.group(1), m.group(2), m.group(0)


def tracked_files():
    out = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout
    for name in out.decode("utf-8").split("\0"):
        if name and Path(name).suffix.lower() not in SKIP_SUFFIXES:
            yield name


def main() -> int:
    report = "--report" in sys.argv[1:]
    failures, seen = [], {}
    for name in tracked_files():
        path = ROOT / name
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            for op, sub, written in numbers_in(line):
                seen.setdefault((op, sub), []).append(f"{name}:{lineno}")
                if not is_invented(op, sub):
                    failures.append(f"{name}:{lineno}: '{written.strip()}' is not an invented number "
                                    f"(use 0300 1234567 / 0300 7654321 / 0300 000000x, or add it to INVENTED after "
                                    f"checking it is made up)")
    if report:
        for (op, sub), where in sorted(seen.items()):
            mark = "family" if (op, sub) in FAMILY else ("listed" if (op, sub) in INVENTED else "UNKNOWN")
            print(f"0{op} {sub}  {mark:7}  {len(where):3}  {where[0]}")
    unused = sorted(INVENTED - set(seen))
    if failures:
        print("\n".join(failures))
        print(f"invented numbers: {len(failures)} number(s) not known to be made up")
        return 1
    note = f"; listed but no longer used (may be removed): {unused}" if unused else ""
    print(f"invented numbers OK: {len(seen)} distinct Pakistani mobile numbers, all family or listed{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

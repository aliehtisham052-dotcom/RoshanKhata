#!/usr/bin/env python3
"""The two string failures the Android build does NOT catch.

aapt already fails a build on malformed XML, duplicate keys, and a
translation with no default. What it happily ships:

1. A key present in values/ but MISSING from a locale — the phone falls
   back to English silently, and a Sindhi-speaking shopkeeper meets an
   English sentence in the middle of their own language.
2. A locale translation whose FORMAT PLACEHOLDERS disagree with the
   default (%1$s dropped, %2$d retyped as %2$s) — that one is worse than
   silent: getString(key, args) CRASHES at runtime, only in that
   language, only on that screen.

Every push runs this so no session — human or Claude — has to remember
to check by hand. Zero dependencies; stdlib only.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DEFAULT = RES / "values" / "strings.xml"
LOCALES = ["values-b+ur+Latn", "values-ur", "values-sd", "values-fa", "values-ar"]

PLACEHOLDER = re.compile(r"%\d+\$[sd]")


def load(path: Path):
    """name -> (text, translatable) for every <string> in the file."""
    out = {}
    root = ET.parse(path).getroot()
    for s in root.iter("string"):
        name = s.get("name")
        translatable = s.get("translatable", "true") != "false"
        # itertext so placeholders inside nested tags still count
        out[name] = ("".join(s.itertext()), translatable)
    return out


def shapes(text: str):
    """The SET of placeholder identities, not their repeat counts.

    Repeating a positional placeholder (%2$s used twice in English, once
    in Urdu) is legal in both directions and crashes nothing — the first
    run of this guard flagged exactly that and taught the rule. What DOES
    crash getString at runtime is a locale asking for an index the code
    never supplies, or the same index with a different type (%2$s vs
    %2$d) — and set comparison catches precisely those two.
    """
    return sorted(set(PLACEHOLDER.findall(text)))


def main() -> int:
    default = load(DEFAULT)
    translatable = {k: v[0] for k, v in default.items() if v[1]}

    failures = []
    for folder in LOCALES:
        path = RES / folder / "strings.xml"
        locale = load(path)
        for key, text in sorted(translatable.items()):
            if key not in locale:
                failures.append(f"{folder}: MISSING <string name=\"{key}\">")
                continue
            want, got = shapes(text), shapes(locale[key][0])
            if want != got:
                failures.append(
                    f"{folder}: PLACEHOLDER MISMATCH {key}: default {want} vs {got}"
                )

    if failures:
        print(f"{len(failures)} string parity failure(s):")
        for f in failures:
            print("  -", f)
        return 1

    print(
        f"strings OK: {len(translatable)} translatable keys present in all "
        f"{len(LOCALES)} locales with matching placeholders"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

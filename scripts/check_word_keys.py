#!/usr/bin/env python3
"""Every word key the shared code names (W.x, WP.x, WA.x) exists in values/strings.xml or arrays.xml.

The phone's WordIds (app/build.gradle.kts, generateWordIds) turns each name into an R reference; a name
that is not a resource fails the phone's build only on CI, minutes later. Comment lines are left out, as
the generator leaves them out (BC-105: a doc comment saying "`W.x`" broke the build, 16 Nov 2026).
Run from the repository root; exit 0 when every key exists.
"""
import glob, re, sys

RES = ["app/src/main/res/values/strings.xml", "app/src/main/res/values/arrays.xml"]
KINDS = {"W": "string", "WP": "plurals", "WA": "string-array"}


def main() -> int:
    have = {"string": set(), "plurals": set(), "string-array": set()}
    for f in RES:
        text = open(f, encoding="utf-8").read()
        for tag, name in re.findall(r'<(string|plurals|string-array)\s+name="([^"]+)"', text):
            have[tag].add(name)
    bad = []
    for f in sorted(glob.glob("shared/src/**/*.kt", recursive=True)):
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            if line.lstrip().startswith(("*", "//", "/*")):
                continue
            for m in re.finditer(r"\b(WP|WA|W)\.(\w+)", line):
                kind = KINDS[m.group(1)]
                if m.group(2) not in have[kind]:
                    bad.append(f"{f}:{n}: {m.group(1)}.{m.group(2)} is no {kind} in values/")
    for b in bad:
        print(b)
    print(f"word keys: {len(bad)} problem(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

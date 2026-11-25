#!/usr/bin/env python3
"""A MEASUREMENT copy of Hudhud.kt whose certainty gates can be set per meaning (calibration, plan Marhala 3, 25 Nov 2026).

    python3 -I scripts/shared_harness/patch_gates.py <Hudhud.kt> <out/Hudhud.kt>

Reads Hudhud.kt and writes a copy (into the build folder, never over the repository's file) in which every comparison of the
sense's certainty `p` inside `decide` — `p >= SENSE_FIRM_TURN`, `SENSE_OVERRULE`, `SENSE_CERTAIN`, `SENSE_SURE`, `SENSE_MAYBE`,
`refine` and the bare `0.5` — becomes `p >= HudhudCalibGate.at(top.label, "<GATE>", <the same value>)`. HudhudCalibGate
(scripts/shared_harness/HudhudCalibrate.kt) returns the value unchanged unless a policy is set, so with no policy the copy
reads exactly as the app does (the calibration runner checks that against the baseline's figures). `p < OWN_PAGE_SENSE` is
left alone: it keeps a help page, it never lets the sense in.

It fails, and writes nothing, when `decide` is not found or the number of comparisons is not the expected one: a changed
decide must be looked at before a measurement of it is trusted (lesson 125: a list held to the code protects against drift).
"""
import re
import sys
from pathlib import Path

EXPECTED = {"FIRM_TURN": 1, "OVERRULE": 3, "CERTAIN": 2, "SURE": 3, "MAYBE": 4, "REFINE": 1, "HALF": 1}  # 15, at c870984
START = "    private fun decide("
END = "    /** Whether the sense's best meaning has a runner-up"
CMP = re.compile(r"\bp >= (SENSE_FIRM_TURN|SENSE_OVERRULE|SENSE_CERTAIN|SENSE_SURE|SENSE_MAYBE|refine|0\.5)\b")
NAME = {"SENSE_FIRM_TURN": "FIRM_TURN", "SENSE_OVERRULE": "OVERRULE", "SENSE_CERTAIN": "CERTAIN", "SENSE_SURE": "SURE",
        "SENSE_MAYBE": "MAYBE", "refine": "REFINE", "0.5": "HALF"}


def patch(text):
    a = text.find(START)
    b = text.find(END, a)
    if a < 0 or b < 0:
        raise SystemExit("patch_gates: Hudhud.decide (or the doc comment of `tied` after it) not found")
    body = text[a:b]
    seen = {}

    def one(m):
        gate = NAME[m.group(1)]
        seen[gate] = seen.get(gate, 0) + 1
        return f'p >= HudhudCalibGate.at(top.label, "{gate}", {m.group(1)})'

    new = CMP.sub(one, body)
    if seen != EXPECTED:
        raise SystemExit(f"patch_gates: decide's certainty comparisons changed: found {seen}, expected {EXPECTED}. "
                         "Read the new decide and update EXPECTED (and the gate names) before measuring.")
    if new.count("p < OWN_PAGE_SENSE") != 1:
        raise SystemExit("patch_gates: `p < OWN_PAGE_SENSE` not found once in decide")
    return text[:a] + new + text[b:], seen


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    src, out = Path(argv[0]), Path(argv[1])
    text, seen = patch(src.read_text(encoding="utf-8"))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"patch_gates: {sum(seen.values())} certainty gates of decide routed through HudhudCalibGate -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Hudhud baseline, part 1 (stage 8, H0): the inventory of every labelled set, as counts (lesson 144).

Reuses scripts/hudhud_sets.py (its records, overlaps and duplicates); prints markdown tables to stdout and changes nothing.
    python3 scripts/hudhud_baseline_inventory.py
"""
import importlib.util
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("hudhud_sets", ROOT / "scripts" / "hudhud_sets.py")
S = importlib.util.module_from_spec(spec)
sys.modules["hudhud_sets"] = S
spec.loader.exec_module(S)

engine, problems = S.Engine(), []
recs = S.records(engine, problems)
LANGS = ["en", "ur-Latn", "ur", "sd", "ar", "fa", "hi", "bn", "id"]


def sets_of(r):
    if r["split"] in ("train", "heldout"):
        return r["split"]
    return r["source"].split(":")[0].replace(".tsv", "")


count = defaultdict(Counter)
for r in recs:
    count[sets_of(r)][r["language"]] += 1
order = ["train", "heldout", "owner_set", "owner_1000", "review_d", "review_o"]
print("| set | " + " | ".join(LANGS) + " | total | unique sentences |")
print("|---|" + "---|" * (len(LANGS) + 2))
for s in order:
    uniq = len({S.key(r["text"]) for r in recs if sets_of(r) == s})
    row = " | ".join(str(count[s][l]) if count[s][l] else "-" for l in LANGS)
    print(f"| {s} | {row} | {sum(count[s].values())} | {uniq} |")
print()
print("Owner sets have no language column (Roman Urdu and English as he writes; counted as `ur-Latn` by hudhud_sets.py).")
now = S.overlaps(recs)
ov = Counter(n.replace(".tsv", "") for n, _ in now)
print()
print("| set | sentences also in the bank (train) | share of the set |")
print("|---|---|---|")
for s in order[1:]:
    tot = sum(count[s].values())
    print(f"| {s} | {ov[s]} | {100 * ov[s] / tot:.1f}% |")
dups = S.duplicates_within(recs)
print()
print(f"Duplicates inside owner/review sets: {len(dups)} sentences appear twice or more (owner_set 3, review_d 8 at the time of writing).")
print(f"Gate problems reported by hudhud_sets.py while reading: {len(problems)}")

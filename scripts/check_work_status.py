#!/usr/bin/env python3
"""The work status register stays honest (docs/work-status.tsv; master plan v2 of 24 Nov 2026, sections 7, 8 and 10).

One line per item of the plan (F, A, B, C, H, D). The owner's rule: no claim without its proof, and a "done" is only
a done when something can be opened to see it. This checks the register, not the work itself (lesson 125: drift
protection, not proof). It fails when

  - the header is not the register's, a line has the wrong number of columns, an id is malformed or used twice;
  - an item of the plan is missing (F1-F3, A1-A6, B1-B12, C1-C5, H0-H9, D1-D14);
  - a status is not one of the five the plan allows, a priority is not P0-P2, an importance is not 1-10;
  - VERIFIED COMPLETE or PARTIAL has no evidence, or its evidence names a repository path that does not exist
    (a word with a "/" in it is read as a path; "#Name" after a path is a symbol that must be in that file);
  - a line has no next step (a done item says "-" only when VERIFIED COMPLETE);
  - duplicate_of names itself or an id that is not in the register (the duplicate register is this column, not a file).

    python3 scripts/check_work_status.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REGISTER = ROOT / "docs/work-status.tsv"
COLUMNS = ["id", "work", "sources", "priority", "importance", "status", "evidence", "next", "duplicate_of"]
STATUSES = {"VERIFIED COMPLETE", "PARTIAL", "NOT STARTED", "BLOCKED", "NEEDS REVALIDATION"}
PRIORITIES = {"P0", "P1", "P2"}
PLAN = ([f"F{i}" for i in range(1, 4)] + [f"A{i}" for i in range(1, 7)] + [f"B{i}" for i in range(1, 13)]
        + [f"C{i}" for i in range(1, 6)] + [f"H{i}" for i in range(0, 10)] + [f"D{i}" for i in range(1, 15)])
ID = re.compile(r"^[A-Z]{1,2}\d{1,3}$")


def paths_in(evidence: str):
    """Repository paths named in an evidence cell: words with a '/' that are not URLs, an optional #symbol after."""
    for word in re.split(r"[\s;,]+", evidence):
        word = word.strip("()")
        if "/" not in word or "://" in word:
            continue
        path, _, symbol = word.partition("#")
        yield path, symbol


def main() -> int:
    problems = []
    lines = REGISTER.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].split("\t") != COLUMNS:
        print(f"{REGISTER.relative_to(ROOT)}: the header must be: " + "\t".join(COLUMNS))
        return 1
    seen = {}
    for n, line in enumerate(lines[1:], start=2):
        if not line.strip():
            continue
        cells = line.split("\t")
        where = f"line {n}"
        if len(cells) != len(COLUMNS):
            problems.append(f"{where}: {len(cells)} columns, want {len(COLUMNS)}")
            continue
        row = dict(zip(COLUMNS, (c.strip() for c in cells)))
        rid = row["id"]
        where = f"line {n} ({rid})"
        if not ID.match(rid):
            problems.append(f"{where}: the id is not like F1, B9 or H0")
        if rid in seen:
            problems.append(f"{where}: the id is also on line {seen[rid]}")
        seen[rid] = n
        for col in ("work", "sources", "evidence", "next", "duplicate_of"):
            if not row[col]:
                problems.append(f"{where}: {col} is empty (write - when there is nothing)")
        if row["status"] not in STATUSES:
            problems.append(f"{where}: status '{row['status']}' is not one of {sorted(STATUSES)}")
        if row["priority"] not in PRIORITIES:
            problems.append(f"{where}: priority '{row['priority']}' is not P0, P1 or P2")
        if not row["importance"].isdigit() or not 1 <= int(row["importance"]) <= 10:
            problems.append(f"{where}: importance '{row['importance']}' is not 1 to 10")
        if row["status"] in ("VERIFIED COMPLETE", "PARTIAL"):
            if row["evidence"] in ("", "-"):
                problems.append(f"{where}: {row['status']} needs evidence (a file, a test or a run)")
            for path, symbol in paths_in(row["evidence"]):
                target = ROOT / path
                if not target.exists():
                    problems.append(f"{where}: the evidence names {path}, which does not exist")
                elif symbol and target.is_file() and not re.search(
                        r"(?<![\w$])" + re.escape(symbol) + r"(?![\w$])", target.read_text(encoding="utf-8")):
                    problems.append(f"{where}: {path} has no '{symbol}'")
        if row["next"] == "-" and row["status"] != "VERIFIED COMPLETE":
            problems.append(f"{where}: only a VERIFIED COMPLETE item may have no next step")
    for rid, n in seen.items():
        dup = dict(zip(COLUMNS, lines[n - 1].split("\t")))["duplicate_of"].strip()
        if dup != "-" and (dup == rid or dup not in seen):
            problems.append(f"line {n} ({rid}): duplicate_of '{dup}' is not another id of the register")
    for rid in PLAN:
        if rid not in seen:
            problems.append(f"the plan's item {rid} has no line")
    if problems:
        print("\n".join(problems))
        print(f"{len(problems)} problem(s) in {REGISTER.relative_to(ROOT)}")
        return 1
    counts = {}
    for n in seen.values():
        st = lines[n - 1].split("\t")[5].strip()
        counts[st] = counts.get(st, 0) + 1
    print(f"work status: {len(seen)} items, " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())

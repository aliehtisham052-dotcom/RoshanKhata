# The bug-class register

`docs/bug-classes.tsv` keeps every class of bug found in Roshan Khata, the guard built against it and the test that holds
it (the owner's rule, 16 Nov 2026), so the test suite catches the same kind of mistake quickly next time and it is never
made twice. `scripts/check_bug_classes.py` runs in the Strings workflow and fails when a named test or guard is not in the
code, an id is used twice or a required column is empty. It checks names, not behaviour (lesson 125).

## Adding a row

One line per new class, **in the same commit as the fix and its test**. A tab between columns:

| Column | What goes in it |
| --- | --- |
| `id` | The next free `BC-nnn`. Never reuse or renumber one. |
| `area` | One of `hudhud money stock schema ui security privacy i18n sync backup print perf release build`. |
| `class` | The class in one line, as the owner would see it — not the one sentence or screen that showed it. |
| `root_cause` | Why it happened, in one line. |
| `guard` | `path::symbol` for the code that keeps it out (the symbol must be a word in that file); several joined by ` ; `; a script is named by its path alone; `NONE` when nothing in the code guards it yet. |
| `tests` | `path::ClassName` for each test that fails when the class comes back, joined by ` ; `; a script check by its path alone (`scripts/check_strings.py`); `NONE` when no test holds it yet. |
| `lesson` | The CLAUDE.md lesson number(s), or `-`. |
| `found` | `YYYY-MM-DD`, or `<=YYYY-MM-DD` when only "on or before" is on record. |

Example:

```
BC-077	hudhud	A bare "bill" read as a supplier bill count	The sense had learnt every "bill" as that count	shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/Hudhud.kt::featureNamed	shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data/HudhudBillWordTest.kt::HudhudBillWordTest	145	2026-11-15
```

Rules:

- **Never invent a test.** If nothing holds the class yet, write `NONE`: those rows are the honest backlog, and the check
  prints their ids every run.
- A test named here must really fail when the class comes back: break the guard once on purpose (lessons 100, 126) before
  writing its name.
- An open fault found but not yet fixed is a row too, its class starting `OPEN FAULT:`, its tests `NONE` until the fix
  lands with its test.
- Run `python3 scripts/check_bug_classes.py` before the push.

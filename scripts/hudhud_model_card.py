#!/usr/bin/env python3
"""Fills Hudhud's model card from the repository (plan v1, Marhala 4 and 12; 25 Nov 2026).

    python3 scripts/hudhud_model_card.py                      # the card on stdout
    python3 scripts/hudhud_model_card.py --out FILE [--name "Hudhud 1.3 (H4 sense)"]
    python3 scripts/hudhud_model_card.py --check              # every {{name}} of the template is filled; writes nothing

It reads docs/hudhud-model-card-template.md and fills what the repository can say for itself: the commit and branch, the
app versions (app/build.gradle.kts, desktop/build.gradle.kts), sense.bin's SHA-256 and size and the previous sense.bin's
(from git: the version before the last change of the file, or HEAD's when the working file differs), every bank and set
file's SHA-256 and line count, the bank's sentences per language and meanings (gen_hudhud_bank.read()), the recipe's
constants and the seeds as HudhudSense.kt says them, decide's gates as Hudhud.kt says them, and the result of the two code
gates (check_hudhud_readonly.py, check_hudhud_offline.py). Every [[MEASURED: ...]] stays for a person to fill from a named
run: this script never writes a figure it did not read from the repository.
"""
import hashlib
import importlib.util
import re
import subprocess
import sys
from collections import Counter
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "docs/hudhud-model-card-template.md"
SENSE = "app/src/main/assets/hudhud/sense.bin"
RES = "shared/src/jvmSharedTest/resources/hudhud"
ENGINE = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data"


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha(b):
    return hashlib.sha256(b).hexdigest()


def git(*args, binary=False):
    try:
        out = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=True)
        return out.stdout if binary else out.stdout.decode("utf-8").strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def previous_sense():
    """(sha, note) of the sense.bin a rollback goes back to."""
    here = (ROOT / SENSE).read_bytes()
    head = git("show", f"HEAD:{SENSE}", binary=True)
    if head is not None and sha(head) != sha(here):
        return sha(head), "HEAD's, the working file is a new sense not yet committed"
    last = git("log", "-1", "--format=%h", "--", SENSE)
    if not last:
        return "unknown", "git history not readable"
    before = git("show", f"{last}^:{SENSE}", binary=True)
    if before is None:
        if git("rev-parse", "--is-shallow-repository") == "true":
            return "unknown", f"this clone is shallow: {last} is the oldest commit it has; read it from a full clone"
        return "none", f"{last} is the file's first commit"
    return sha(before), f"the file before {last}, the commit that last changed it"


def const(text, name):
    m = re.search(r"\bconst val " + re.escape(name) + r"\s*=\s*([0-9.]+)L?", text)
    return m.group(1) if m else "?"


def gradle(path, pattern):
    m = re.search(pattern, (ROOT / path).read_text(encoding="utf-8"))
    return m.group(1) if m else "?"


def check_result(script):
    r = subprocess.run([sys.executable, "-I", str(ROOT / "scripts" / script)], cwd=ROOT, capture_output=True, text=True)
    last = (r.stdout.strip().splitlines() or ["(no output)"])[-1]
    return (f"0 (pass on {date.today().isoformat()}: {last})" if r.returncode == 0
            else f"FAIL on {date.today().isoformat()}: {last}")


def values(name):
    bank_mod = _load("gen_hudhud_bank")
    bank, held = bank_mod.read()
    sense_bytes = (ROOT / SENSE).read_bytes()
    prev, note = previous_sense()
    sense_kt = (ENGINE / "HudhudSense.kt").read_text(encoding="utf-8")
    hud_kt = (ENGINE / "Hudhud.kt").read_text(encoding="utf-8")
    seed = int(const(sense_kt, "SEED"))
    m = re.search(r"SHIPPED_SEEDS[^=]*=\s*\(SEED until SEED \+ (\d+)L\)", sense_kt)
    count = int(m.group(1)) if m else None

    def lines_of(rel):
        return sum(1 for l in (ROOT / rel).read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#"))

    data = [SENSE] + [f"{RES}/{f}" for f in ("bank.tsv", "heldout.tsv", "owner_set.tsv", "owner_1000.tsv", "review_d.tsv",
                                              "review_o.tsv", "pages.tsv")]
    rows = []
    for rel in data:
        b = (ROOT / rel).read_bytes()
        rows.append(f"| `{rel}` | `{sha(b)}` | {'binary, ' + str(len(b)) + ' bytes' if rel.endswith('.bin') else lines_of(rel)} |")
    sources = sorted((ROOT / "shared/hudhud").glob("*.tsv")) + sorted((ROOT / "shared/hudhud/more").glob("*.tsv"))
    source_sha = sha("".join(f"{sha(p.read_bytes())}  {p.relative_to(ROOT)}\n" for p in sources).encode("utf-8"))
    gates = []
    for g in ("SENSE_MAYBE", "SENSE_SURE", "SENSE_CERTAIN", "SENSE_OVERRULE", "SENSE_FIRM_TURN", "OWN_PAGE_SENSE", "TIE_MARGIN"):
        gates.append(f"- `{g}` = {const(hud_kt, g)}")
    gates.append("- an inline 0.5 (a tenseless word of paying read as \"who has not paid\"); every gate is global today, one value for every meaning")
    overlaps = sum(1 for l in (ROOT / "scripts/hudhud_sets_overlap.tsv").read_text(encoding="utf-8").splitlines()
                   if l.strip() and not l.startswith("#"))
    return {
        "release_name": name,
        "made_on": date.today().isoformat(),
        "commit": git("rev-parse", "--short", "HEAD") or "?",
        "branch": git("rev-parse", "--abbrev-ref", "HEAD") or "?",
        "phone_version": gradle("app/build.gradle.kts", r'versionName\s*=\s*"([^"]+)"'),
        "phone_code": gradle("app/build.gradle.kts", r"versionCode\s*=\s*(\d+)"),
        "desktop_version": gradle("desktop/build.gradle.kts", r'packageVersion\s*=\s*"([^"]+)"'),
        "sense_sha": sha(sense_bytes), "sense_bytes": f"{len(sense_bytes):,}",
        "prev_sense_sha": prev, "prev_sense_note": note,
        "data_rows": "\n".join(rows),
        "bank_lines": f"{len(bank):,}", "bank_meanings": str(len({l for _, l, _ in bank})),
        "bank_recipe": str(bank_mod.DEFAULT_RECIPE),
        "bank_per_lang": ", ".join(f"{k} {v:,}" for k, v in Counter(l for l, _, _ in bank).items()),
        "held_lines": f"{len(held):,}",
        "held_per_lang": ", ".join(f"{k} {v}" for k, v in Counter(l for l, _, _ in held).items()),
        "owner_short": str(lines_of(f"{RES}/owner_set.tsv")), "owner_1000": f"{lines_of(f'{RES}/owner_1000.tsv'):,}",
        "review_d": f"{lines_of(f'{RES}/review_d.tsv'):,}", "review_o": f"{lines_of(f'{RES}/review_o.tsv'):,}",
        "source_files": str(len(sources)), "source_sha": source_sha,
        "known_overlaps": str(overlaps),
        "epochs": const(sense_kt, "EPOCHS"), "min_df": const(sense_kt, "MIN_DF"), "temperature": const(sense_kt, "TEMPERATURE"),
        "w_concept": const(sense_kt, "W_CONCEPT"), "w_word": const(sense_kt, "W_WORD"), "w_pair": const(sense_kt, "W_PAIR"),
        "w_sound": const(sense_kt, "W_SOUND"), "w_piece": const(sense_kt, "W_PIECE"),
        "seed_count": str(count) if count else "?",
        "seeds": f"{seed}..{seed + count - 1}" if count else "?",
        "prune": const(sense_kt, "SHIPPED_PRUNE"),
        "gates": "\n".join(gates),
        "readonly_result": check_result("check_hudhud_readonly.py"),
        "offline_result": check_result("check_hudhud_offline.py"),
    }


def fill(template, vals):
    out = template
    for k, v in vals.items():
        out = out.replace("{{" + k + "}}", v)
    # The template's own comment (how to use it) is not part of a card.
    out = re.sub(r"<!--.*?-->\n", "", out, count=1, flags=re.S)
    return out


def main(argv):
    name = argv[argv.index("--name") + 1] if "--name" in argv else "Hudhud (unreleased build)"
    template = TEMPLATE.read_text(encoding="utf-8")
    vals = values(name)
    card = fill(template, vals)
    left = sorted(set(re.findall(r"\{\{(\w+)\}\}", card)))
    unused = sorted(k for k in vals if "{{" + k + "}}" not in template)
    if left or unused:
        print(f"hudhud model card: template and script disagree: unfilled {left}, unused {unused}")
        return 1
    if "--check" in argv:
        print(f"hudhud model card OK: {len(vals)} fields filled from the repository, "
              f"{len(re.findall(r'\[\[MEASURED', card))} left [[MEASURED]] for a named run")
        return 0
    if "--out" in argv:
        out = Path(argv[argv.index("--out") + 1])
        if out.resolve() == TEMPLATE.resolve():
            print("hudhud model card: --out would overwrite the template")
            return 2
        out.write_text(card, encoding="utf-8")
        print(f"hudhud model card written to {out}")
        return 0
    sys.stdout.write(card)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

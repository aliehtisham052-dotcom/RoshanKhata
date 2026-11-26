#!/usr/bin/env bash
# Hudhud's training cycle as ONE reviewed command (plan v1, "the training cycle (ten steps)", Marhala 8; 26 Nov 2026).
# docs/hudhud-training-cycle.md says what each step and gate means; scripts/hudhud_cycle.py's header says the SHIP rule.
#
#   scripts/hudhud_cycle.sh --additions FILE                 # gate, learn, measure, compare, calibrate, verdict (~40 min, 2 cores)
#   scripts/hudhud_cycle.sh --additions FILE --apply         # the same, then write the bank, sense.bin and the manifest if SHIP
#   scripts/hudhud_cycle.sh --self-test                      # the cycle's own checks, no Java, no training (seconds)
# Options: --next-key (recipe 3: the measured sets' sentences leave the bank, scripts/gen_hudhud_bank.py), --sealed DIR (the
# owner's sealed set, outside the repository: near-duplicates are refused, never shown), --others 1000,2000 (the two other
# 15-seed ensembles, lesson 163), --no-calibrate (fast; can never SHIP), --allow-word W, --out DIR (under build/).
#
# Writes only DIR (default build/hudhud-cycle, git-ignored) until --apply; --apply writes nothing unless the verdict is SHIP.
# The run's record is DIR/cycle-manifest.tsv (the data manifest v3 of a cycle: every input and output with its SHA-256).
# Exit: 0 SHIP, 1 NO-SHIP (or a gate refused), 2 usage, 3 a compile or a step failed.
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
H="$HERE/shared_harness"
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2

if [ "${1:-}" = "--self-test" ]; then
  started=$SECONDS
  rc=0
  python3 -I scripts/test_hudhud_cycle.py 2>&1 | tail -3 | sed 's/^/  /'; [ "${PIPESTATUS[0]}" = 0 ] || rc=1
  python3 -I scripts/hudhud_pairs.py --self-test || rc=1
  python3 -I scripts/test_gen_hudhud_bank.py 2>&1 | tail -1 | sed 's/^/  gen_hudhud_bank tests: /'; [ "${PIPESTATUS[0]}" = 0 ] || rc=1
  python3 -I scripts/hudhud_cycle.py ledger-check || rc=1
  for f in HudhudCycle.kt HudhudBaseline.kt; do [ -f "$H/$f" ] || { echo "MISSING: $H/$f"; rc=1; }; done
  grep -q 'sink: RowSink' "$H/HudhudBaseline.kt" || { echo "HudhudBaseline.pass has no row sink: the miss lists cannot be compared"; rc=1; }
  bash -n "$0" || rc=1
  echo "hudhud cycle self-test $([ $rc = 0 ] && echo OK || echo FAILED) ($((SECONDS - started)) s)"
  exit $rc
fi

additions="" out="$ROOT/build/hudhud-cycle" others="1000,2000" calibrate=1 apply=0 gateargs=()
while [ $# -gt 0 ]; do
  case "$1" in
    --additions) additions=$(realpath -m "$2"); shift 2 ;;
    --out) out=$(realpath -m "$2"); shift 2 ;;
    --others) others="$2"; shift 2 ;;
    --no-calibrate) calibrate=0; shift ;;
    --apply) apply=1; shift ;;
    --next-key) gateargs+=(--next-key); shift ;;
    --sealed) gateargs+=(--sealed "$(realpath -m "$2")"); shift 2 ;;
    --allow-word) gateargs+=(--allow-word "$2"); shift 2 ;;
    *) echo "unknown option $1 (see the header of $0)"; exit 2 ;;
  esac
done
[ -n "$additions" ] && [ -f "$additions" ] || { echo "--additions FILE is needed (an empty file means: no change)"; exit 2; }
started=$SECONDS
step() { echo; echo "== [$((SECONDS - started)) s] $*"; }
mkdir -p "$out"

step "steps 3-4: the quality gate and the candidate bank ($out)"
python3 -I scripts/hudhud_cycle.py gate --additions "$additions" --out "$out" "${gateargs[@]+"${gateargs[@]}"}"
g=$?
if [ $g = 3 ] || [ $g = 1 ]; then
  step "step 10: the verdict"
  python3 -I scripts/hudhud_cycle.py verdict --out "$out"
  echo "cycle ended in $((SECONDS - started)) s; the repository was not touched"
  exit 1
fi
[ $g = 0 ] || exit 3
recipe=$(python3 -I -c "import json,sys; print(json.load(open(sys.argv[1]))['recipe'])" "$out/gate.json")

jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar'):$(jar 'hamcrest-core-*.jar')"
engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$H/deps.txt" | grep -v '^\s*$')
tests=$(ls "$TESTS"/Hudhud*.kt "$TESTS"/Voice*.kt)

step "compiling the engine, its tests and the cycle's runner (shared harness, lesson 93)"
(
  flock 9
  rm -rf "$out/classes" "$out/gen"
  python3 "$H/gen_w.py" "$out/gen/W.kt" || exit 2
  # shellcheck disable=SC2086
  java -Xmx3g -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$out/classes" \
    $engine "$H"/stubs/*.kt "$out/gen/W.kt" $tests "$H/HudhudBaseline.kt" "$H/HudhudCycle.kt" > "$out/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$out/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $out/clog.txt)"; exit 3; }

# A folder that looks like the repository to the tests (HudhudModelTest.root, HudhudRealPages): the candidate's sense.bin as the
# app's asset, the app's own strings; the candidate bank comes first on the class path. Nothing of the repository is written.
fake="$out/root"
rm -rf "$fake"; mkdir -p "$fake/shared" "$fake/app/src/main/assets/hudhud"
: > "$fake/settings.gradle.kts"
ln -s "$ROOT/app/src/main/res" "$fake/app/src/main/res"
CP="$out/classes:$out/res:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME"

step "steps 6-7: learn the candidate (seeds 313..327) and both banks under the ensembles $others; read every set with each"
(cd "$ROOT/shared" && java -Xmx3g -Dstdout.encoding=UTF-8 -cp "$CP" com.innovation313.roshankhata.data.HudhudCycle \
  --base-bank "$ROOT/shared/src/jvmSharedTest/resources/hudhud/bank.tsv" --cand-bank "$out/res/hudhud/bank.tsv" \
  --base-sense "$ROOT/app/src/main/assets/hudhud/sense.bin" --out "$out" --others "$others") || { echo "the measuring step failed"; exit 3; }
cp "$out/sense-candidate.bin" "$fake/app/src/main/assets/hudhud/sense.bin"

step "step 9a: the old and new miss lists, sentence by sentence (comm)"
mkdir -p "$out/compare"
for rows in "$out"/rows-candidate-*.tsv; do
  e=${rows##*-}; e=${e%.tsv}
  # a row is set, its key (one to three fields), right (1/0), the reading shown: keep set and key of the rows read wrong
  misses() { awk -F'\t' '$(NF-1) == 0 { s = $1; for (i = 2; i <= NF - 2; i++) s = s "\t" $i; print s }' "$1" | LC_ALL=C sort; }
  misses "$out/rows-base-$e.tsv" > "$out/compare/.base-miss"
  misses "$rows" > "$out/compare/.cand-miss"
  LC_ALL=C comm -13 "$out/compare/.base-miss" "$out/compare/.cand-miss" > "$out/compare/$e-lost.tsv"     # right before, wrong now
  LC_ALL=C comm -23 "$out/compare/.base-miss" "$out/compare/.cand-miss" > "$out/compare/$e-gained.tsv"   # wrong before, right now
  echo "  ensemble $e: $(wc -l < "$out/compare/$e-gained.tsv") rows gained, $(wc -l < "$out/compare/$e-lost.tsv") rows lost"
done
rm -f "$out/compare/.base-miss" "$out/compare/.cand-miss"

step "step 9b: the seed-safe guards and the byte-for-byte model test against the candidate bank and sense"
(cd "$fake/shared" && java -Xmx3g -Dstdout.encoding=UTF-8 -cp "$CP" org.junit.runner.JUnitCore \
  com.innovation313.roshankhata.data.HudhudModelTest com.innovation313.roshankhata.data.HudhudSeedSafeTest \
  com.innovation313.roshankhata.data.HudhudSeedSafeMoneyTest > "$out/junit.txt" 2>&1)
grep -E "^OK|^Tests run|^FAILURES" "$out/junit.txt" | sed 's/^/  /'

rm -f "$out/calibration-summary.txt"
if [ $calibrate = 1 ]; then
  step "step 8: the certainty table and the thresholds again, on the candidate (hudhud_calibrate.sh, lesson 165)"
  CALIB_WORK="$out/calibrate" CALIB_RES="$out/res" CALIB_RUN_DIR="$fake/shared" \
    CALIB_FOLDS_ARGS="--src $out/src --bank $out/res/hudhud/bank.tsv --recipe $recipe" \
    bash scripts/hudhud_calibrate.sh > "$out/calibration.md" 2> "$out/calibration.log" || echo "  the calibration failed (see $out/calibration.log)"
  grep -E "^Recipe check|^Summary" "$out/calibration.md" | tee "$out/calibration-summary.txt" | sed 's/^/  /'
fi

step "the run's record: $out/cycle-manifest.tsv (data manifest v3 of this cycle)"
{
  printf 'item\tpath\tsha256\n'
  printf 'commit\t%s\t-\n' "$(git rev-parse HEAD 2>/dev/null || echo unknown)$(git diff --quiet 2>/dev/null || echo ' (working tree changed)')"
  printf 'recipe\t%s\t-\n' "$recipe"
  printf 'seeds\tcandidate 313..327 prune 200; others %s (15 seeds each)\t-\n' "$others"
  for f in "$additions" "$ROOT/shared/src/jvmSharedTest/resources/hudhud/bank.tsv" "$out/res/hudhud/bank.tsv" \
           "$out/res/hudhud/heldout.tsv" "$ROOT/app/src/main/assets/hudhud/sense.bin" "$out/sense-candidate.bin" \
           "$out/figures.tsv" "$out/junit.txt" "$out/calibration.md"; do
    [ -f "$f" ] && printf '%s\t%s\t%s\n' "$(basename "$f")" "${f#"$ROOT/"}" "$(sha256sum "$f" | cut -d' ' -f1)"
  done
} > "$out/cycle-manifest.tsv"

step "step 10: the verdict"
python3 -I scripts/hudhud_cycle.py verdict --out "$out"
v=$?
if [ $v != 0 ] || [ $apply = 0 ]; then
  echo "cycle ended in $((SECONDS - started)) s; the repository was not touched"
  exit $v
fi

step "--apply: the bank sources, the ledger, bank.tsv, sense.bin (the byte-for-byte path), the overlap list, the manifest"
python3 -I scripts/hudhud_cycle.py apply --out "$out" || exit 3
python3 scripts/gen_hudhud_bank.py || exit 3
cmp -s "$ROOT/shared/src/jvmSharedTest/resources/hudhud/bank.tsv" "$out/res/hudhud/bank.tsv" || { echo "the written bank is not the measured one"; exit 3; }
(cd "$ROOT/shared" && java -Xmx3g -cp "$CP" com.innovation313.roshankhata.data.HudhudModelTest \
  "$ROOT/shared/src/jvmSharedTest/resources/hudhud/bank.tsv" "$ROOT/app/src/main/assets/hudhud/sense.bin") || exit 3
cmp -s "$ROOT/app/src/main/assets/hudhud/sense.bin" "$out/sense-candidate.bin" || { echo "sense.bin is not the measured candidate"; exit 3; }
[ "$recipe" = 3 ] && { python3 scripts/hudhud_sets.py --write-overlap || exit 3; }
python3 scripts/hudhud_manifest.py || exit 3
python3 scripts/hudhud_sets.py --check > /dev/null && python3 scripts/gen_hudhud_bank.py --check > /dev/null && \
  python3 scripts/hudhud_manifest.py --check > /dev/null && python3 -I scripts/hudhud_cycle.py ledger-check || exit 3
python3 scripts/hudhud_model_card.py --out "$out/model-card.md" --name "Hudhud sense (cycle $(date +%Y-%m-%d))" > /dev/null || exit 3
echo "model card: $out/model-card.md; its lines from the repository:"
grep -iE "sha-?256|sense\.bin|bytes|bank|recipe|seed" "$out/model-card.md" | head -20 | sed 's/^/  /'
echo
echo "Applied in $((SECONDS - started)) s. Before the commit: scripts/shared_harness/run.sh; set the floors from $out/verdict.md;"
echo "decide every row of $out/compare/313-lost.tsv (promote or re-class, lesson 163); fill the card's [[MEASURED]] from $out."

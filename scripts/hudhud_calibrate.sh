#!/usr/bin/env bash
# Hudhud's calibration (plan Marhala 3, lessons 107, 163, 165; 25 Nov 2026): how often the learnt sense is right at each
# certainty, measured out-of-fold on the bank (5 folds by paraphrase family, the shipped recipe) and on the held-out set, the
# lowest certainty at which each meaning reaches its target precision, and what per-meaning gates would change end to end.
# Writes nothing of the repository: the folds, a gate copy of Hudhud.kt (scripts/shared_harness/patch_gates.py) and the
# classes go to build/hudhud-calibrate (git-ignored); sense.bin is only read. Report: docs/hudhud-calibration-25nov2026.md.
#
#   scripts/hudhud_calibrate.sh                       # OOF (shipped seeds) + held-out + the three policies (~15 min, 2 cores)
#   scripts/hudhud_calibrate.sh --extra-seeds 1000,2000 --naive   # plus two other 15-seed ensembles and the naive split
#   scripts/hudhud_calibrate.sh --no-policies         # only the tables
# Other flags go to the runner: --min-n N (rows a cut needs, 20), --money-target 0.98, --other-target 0.95.
# stdout = the markdown tables; stderr = progress; per-row predictions and proposals.tsv in build/hudhud-calibrate/out.
#
# Needs what scripts/shared_harness/README.md needs. The compile queues behind the harness lock (HARNESS_LOCK); heaps stay at
# 2.5 GB so two helpers fit on a 7 GB machine (CALIB_HEAP to change).
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
H="$HERE/shared_harness"
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${CALIB_WORK:-$ROOT/build/hudhud-calibrate}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
HEAP=${CALIB_HEAP:-2500m}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2

pass=()
naive=0
for a in "$@"; do [ "$a" = "--naive" ] && naive=1 || pass+=("$a"); done

mkdir -p "$WORK/out"
python3 -I "$HERE/hudhud_calib_folds.py" --out "$WORK/folds.tsv" >&2 || exit 2
if [ $naive = 1 ]; then
  python3 -I "$HERE/hudhud_calib_folds.py" --by-text --out "$WORK/folds-text.tsv" >&2 || exit 2
  pass+=(--naive-folds "$WORK/folds-text.tsv")
fi
python3 -I "$H/patch_gates.py" "$MAIN/Hudhud.kt" "$WORK/patched/Hudhud.kt" >&2 || exit 2

jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"

# The engine with Hudhud.kt replaced by its gate copy; everything else as the harness compiles it.
engine=$(ls "$MAIN"/Hudhud*.kt | grep -v '/Hudhud\.kt$'; echo "$WORK/patched/Hudhud.kt"; grep -v '^\s*#' "$H/deps.txt" | grep -v '^\s*$')
helpers="$TESTS/HudhudRealPages.kt $TESTS/HudhudShown.kt $TESTS/HudhudEntities.kt $TESTS/HudhudModelTest.kt"
(
  flock 9
  rm -rf "$WORK/classes" "$WORK/gen"
  python3 "$H/gen_w.py" "$WORK/gen/W.kt" || exit 2
  java -Xmx"$HEAP" -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
    $engine "$H"/stubs/*.kt "$WORK/gen/W.kt" $helpers "$H/HudhudBaseline.kt" "$H/HudhudCalibrate.kt" > "$WORK/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$WORK/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $WORK/clog.txt)" >&2; exit 3; }

cd "$ROOT/shared" || exit 2
exec java -Xmx"$HEAP" -Dstdout.encoding=UTF-8 -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  com.innovation313.roshankhata.data.HudhudCalibrate --folds "$WORK/folds.tsv" --out "$WORK/out" "${pass[@]+"${pass[@]}"}"

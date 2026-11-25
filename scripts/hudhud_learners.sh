#!/usr/bin/env bash
# Hudhud's learners compared (plan v1, Marhala 5 item 2, 26 Nov 2026): the shipped averaged-perceptron ensemble against
# multinomial logistic regression and a one-vs-rest linear SVM, on the SAME features (HudhudSense.features) and the SAME
# paraphrase-family folds as the calibration (scripts/hudhud_calib_folds.py), out of fold and end to end (each learner's
# whole-bank model put in sense.bin's place in memory; Hudhud.decide unchanged). MEASUREMENT ONLY: writes nothing of the
# repository; the folds and the classes go to build/hudhud-learners (git-ignored); sense.bin is only read.
# Report: docs/hudhud-learners-26nov2026.md.
#
#   scripts/hudhud_learners.sh                         # three seeds per learner, lambda grids, end to end (~40 min, 2 cores)
#   scripts/hudhud_learners.sh --no-e2e                # only the out-of-fold tables
#   scripts/hudhud_learners.sh --seeds 313 --lr-lambdas 1e-5 --svm-lambdas 1e-5   # one quick pass
# Other flags go to the runner: --lr-epochs N (20), --svm-epochs N (20), --lr-eta X (0.5, the first SGD step).
# stdout = the markdown tables; stderr = progress. The compile queues behind the harness lock (HARNESS_LOCK); the heap stays
# at 2.5 GB (LEARNERS_HEAP to change).
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
H="$HERE/shared_harness"
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${LEARNERS_WORK:-$ROOT/build/hudhud-learners}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
HEAP=${LEARNERS_HEAP:-2500m}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2

mkdir -p "$WORK"
python3 -I "$HERE/hudhud_calib_folds.py" --out "$WORK/folds.tsv" >&2 || exit 2

jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"

# The engine as the harness compiles it (Hudhud.kt itself, unpatched: the sense is swapped in memory, decide is not touched).
engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$H/deps.txt" | grep -v '^\s*$')
helpers="$TESTS/HudhudRealPages.kt $TESTS/HudhudShown.kt $TESTS/HudhudEntities.kt $TESTS/HudhudModelTest.kt"
(
  flock 9
  rm -rf "$WORK/classes" "$WORK/gen"
  python3 "$H/gen_w.py" "$WORK/gen/W.kt" || exit 2
  java -Xmx"$HEAP" -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
    $engine "$H"/stubs/*.kt "$WORK/gen/W.kt" $helpers "$H/HudhudBaseline.kt" "$H/HudhudLearners.kt" > "$WORK/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$WORK/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $WORK/clog.txt)" >&2; exit 3; }

cd "$ROOT/shared" || exit 2
exec java -Xmx"$HEAP" -Dstdout.encoding=UTF-8 -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  com.innovation313.roshankhata.data.HudhudLearners --folds "$WORK/folds.tsv" "$@"

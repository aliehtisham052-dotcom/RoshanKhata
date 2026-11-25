#!/usr/bin/env bash
# Hudhud's reading timed on the computer (26 Nov 2026, BC-353): the shared engine compiled as the harness compiles it, then
# HudhudSpeed over the on-device test's sample (app/src/androidTest/assets/hudhud/speed_sample.tsv) and its books.
#
#   scripts/shared_harness/speed.sh [sizes] [reps] [readings.tsv]   # sizes default 9,1000; reps 3
#
# REPORT ONLY, and the computer's figures, not the phone's. Compare two builds in one sitting (other work on the machine moves
# them). With a readings file, every answer is written (one line a question) so two builds can be held to the same readings.
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${HARNESS_WORK:-$ROOT/build/shared-harness}-speed
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2
jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"
engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$HERE/deps.txt" | grep -v '^\s*$')
mkdir -p "$WORK"
(
  flock 9
  rm -rf "$WORK/classes" "$WORK/gen"
  python3 "$HERE/gen_w.py" "$WORK/gen/W.kt" || exit 2
  java -Xmx3g -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
    $engine "$HERE"/stubs/*.kt "$WORK/gen/W.kt" "$TESTS/HudhudRealPages.kt" "$TESTS/HudhudModelTest.kt" "$HERE/HudhudSpeed.kt" \
    > "$WORK/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$WORK/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $WORK/clog.txt)" >&2; exit 3; }
cd "$ROOT/shared" || exit 2
exec java -Xmx2g -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  com.innovation313.roshankhata.data.HudhudSpeed "$ROOT/app/src/androidTest/assets/hudhud/speed_sample.tsv" "${1:-9,1000}" "${2:-3}" ${3:+"$3"}

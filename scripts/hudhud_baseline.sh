#!/usr/bin/env bash
# Hudhud's baseline before any retrain (stage 8, H0). Compiles the plain-Kotlin engine with the shared harness's method
# (lesson 93) plus scripts/shared_harness/HudhudBaseline.kt, and prints the figures as markdown to stdout. Writes nothing
# of the repository: only build/hudhud-baseline (git-ignored); the shipped sense.bin is only read.
#
#   scripts/hudhud_baseline.sh [--no-seeds]     # about 10 minutes with the seeds; stdout = the tables, stderr = progress
#   scripts/hudhud_baseline.sh --hashes         # only the SHA-256 of sense.bin and every bank and set file
#   scripts/hudhud_baseline.sh --inventory      # only the inventory of the sets (python, no Java)
#
# Needs what scripts/shared_harness/README.md needs. The compile queues behind the harness lock (HARNESS_LOCK); the run
# does not hold it, so figures of speed depend on what else the machine does: the load average is printed first.
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
H="$HERE/shared_harness"
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${BASELINE_WORK:-$ROOT/build/hudhud-baseline}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2

if [ "${1:-}" = "--hashes" ]; then
  sha256sum app/src/main/assets/hudhud/sense.bin shared/hudhud/*.tsv shared/hudhud/more/*.tsv \
    shared/src/jvmSharedTest/resources/hudhud/*.tsv | sed 's/  / /'
  exit 0
fi
if [ "${1:-}" = "--inventory" ]; then exec python3 -I "$HERE/hudhud_baseline_inventory.py"; fi

jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"

engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$H/deps.txt" | grep -v '^\s*$')
# Only the test-side helpers the runner reads: the real pages, the readings' spelling, the entities, the shipped model's reader.
helpers="$TESTS/HudhudRealPages.kt $TESTS/HudhudShown.kt $TESTS/HudhudEntities.kt $TESTS/HudhudModelTest.kt"

mkdir -p "$WORK"
(
  flock 9
  rm -rf "$WORK/classes" "$WORK/gen"
  python3 "$H/gen_w.py" "$WORK/gen/W.kt" || exit 2
  java -Xmx3g -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
    $engine "$H"/stubs/*.kt "$WORK/gen/W.kt" $helpers "$H/HudhudBaseline.kt" > "$WORK/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$WORK/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $WORK/clog.txt)" >&2; exit 3; }

cd "$ROOT/shared" || exit 2
exec java -Xmx2g -Dstdout.encoding=UTF-8 -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  com.innovation313.roshankhata.data.HudhudBaseline "$@"

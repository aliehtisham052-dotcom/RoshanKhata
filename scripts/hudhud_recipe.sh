#!/usr/bin/env bash
# Hudhud's stable training recipe, measured (plan H1, 25 Nov 2026). Compiles the plain-Kotlin engine with the shared harness's
# method (lesson 93) plus scripts/shared_harness/HudhudBaseline.kt and HudhudRecipe.kt, learns the sense in memory (never writes
# sense.bin or the shipped bank) and prints the figures as markdown. Details: docs/hudhud-recipe-25nov2026.md.
#
#   python3 scripts/gen_hudhud_bank.py --recipe 2 --out-dir /tmp/bank2        # recipe 2's bank, first
#   scripts/hudhud_recipe.sh --bank2 /tmp/bank2/bank.tsv [--k 5] [--outer 5] [--only 1x1,2x5]   # 30-60 minutes
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
H="$HERE/shared_harness"
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${RECIPE_WORK:-$ROOT/build/hudhud-recipe}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data
cd "$ROOT" || exit 2
jar() { local j; j=$(ls "$LIB"/$1 2>/dev/null | head -1); [ -n "$j" ] || { echo "No $1 in $LIB" >&2; exit 2; }; echo "$j"; }
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"
engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$H/deps.txt" | grep -v '^\s*$')
helpers="$TESTS/HudhudRealPages.kt $TESTS/HudhudShown.kt $TESTS/HudhudEntities.kt $TESTS/HudhudModelTest.kt $TESTS/HudhudMoneySideTest.kt $TESTS/HudhudMoneySidePairsTest.kt $TESTS/HudhudInjectionTest.kt"
mkdir -p "$WORK"
(
  flock 9
  rm -rf "$WORK/classes" "$WORK/gen"
  python3 "$H/gen_w.py" "$WORK/gen/W.kt" || exit 2
  java -Xmx3g -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
    $engine "$H"/stubs/*.kt "$WORK/gen/W.kt" $helpers "$H/HudhudBaseline.kt" "$H/HudhudRecipe.kt" > "$WORK/clog.txt" 2>&1
) 9>"$LOCK" || { grep -E "error:" "$WORK/clog.txt" | head -20; echo "COMPILE FAILED (whole log: $WORK/clog.txt)" >&2; exit 3; }
cd "$ROOT/shared" || exit 2
exec java -Xmx3g -Dstdout.encoding=UTF-8 -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  com.innovation313.roshankhata.data.HudhudRecipe "$@"

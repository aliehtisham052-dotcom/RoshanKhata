#!/usr/bin/env bash
# The shared harness (lesson 93): compiles the plain-Kotlin Hudhud engine of :shared with Gradle's own Kotlin compiler and
# runs its tests with JUnit, without Gradle or Maven. It is NOT CI: Android, Room and Compose code are not compiled here.
#
#   scripts/shared_harness/run.sh [extra test files...]   compile and run; prints the JUnit verdict; exits 1 when a test
#                                                         fails, 3 when the code does not compile
#   scripts/shared_harness/run.sh --lists-only            only check that every listed file exists (no Java needed)
#
# Environment: KOTLIN_LIB  folder with kotlin-compiler-embeddable, kotlin-stdlib, kotlin-reflect, kotlin-script-runtime,
#                          trove4j, annotations, kotlinx-coroutines-core-jvm, junit 4 and hamcrest-core (default /opt/gradle/lib)
#              HARNESS_WORK where it builds (default <repo>/build/shared-harness, ignored by git)
#              HARNESS_LOCK a lock file so runs on one machine queue one after another (default /tmp/harness.lock)
set -u -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
LIB=${KOTLIN_LIB:-/opt/gradle/lib}
WORK=${HARNESS_WORK:-$ROOT/build/shared-harness}
LOCK=${HARNESS_LOCK:-/tmp/harness.lock}
MAIN=shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data
TESTS=shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data

# Extra test files may be given from anywhere: made absolute before the work moves to the repository root.
extras=()
for a in "$@"; do [ "$a" = "--lists-only" ] && extras+=("$a") || extras+=("$(realpath -m "$a")"); done
set -- "${extras[@]+"${extras[@]}"}"
cd "$ROOT" || exit 2

# The engine: every Hudhud*.kt of the shared module (a new one is taken by itself) and what deps.txt lists.
engine=$(ls "$MAIN"/Hudhud*.kt; grep -v '^\s*#' "$HERE/deps.txt" | grep -v '^\s*$')
# The tests: every Hudhud*.kt and Voice*.kt of jvmSharedTest's data package (helpers such as HudhudShown included;
# only files ending in Test.kt are run). HudhudWordsTest is a desktop test (it reads strings.xml) and is not here.
tests=$(ls "$TESTS"/Hudhud*.kt "$TESTS"/Voice*.kt)
missing=0
for f in $engine $tests; do [ -f "$f" ] || { echo "MISSING: $f"; missing=1; }; done
for f in "$@"; do [ "$f" = "--lists-only" ] || [ -f "$f" ] || { echo "MISSING: $f"; missing=1; }; done
[ $missing = 0 ] || exit 2
if [ "${1:-}" = "--lists-only" ]; then
  echo "lists OK: $(echo "$engine" | wc -l) engine files, $(echo "$tests" | wc -l) test files"
  exit 0
fi

jar() {  # the first jar in $LIB matching $1, or a clear stop
  local j
  j=$(ls "$LIB"/$1 2>/dev/null | head -1)
  [ -n "$j" ] || { echo "No $1 in $LIB (set KOTLIN_LIB to Gradle's lib folder)" >&2; exit 2; }
  echo "$j"
}
COMPILER="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
RUNTIME="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar')"

exec 9>"$LOCK"
flock 9
mkdir -p "$WORK"
# A compile that fails must not leave the last run's verdict to be read as this one's (24 Nov 2026, BC-273).
rm -rf "$WORK/classes" "$WORK/gen" "$WORK/tout.txt"
python3 "$HERE/gen_w.py" "$WORK/gen/W.kt" || exit 2
java -Xmx3g -cp "$COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$RUNTIME" -d "$WORK/classes" \
  $engine "$HERE"/stubs/*.kt "$WORK/gen/W.kt" $tests "$@" > "$WORK/clog.txt" 2>&1 || {
  grep -E "error:" "$WORK/clog.txt" | head -20
  echo "COMPILE FAILED (whole log: $WORK/clog.txt)"
  exit 3
}

# Every file ending in Test.kt is a class to run, named from its own package line.
classes=""
count=0
for f in $tests "$@"; do
  case "$(basename "$f")" in *Test.kt) ;; *) continue ;; esac
  pkg=$(grep -m1 '^package ' "$f" | awk '{print $2}')
  classes="$classes $pkg.$(basename "$f" .kt)"
  count=$((count + 1))
done
cd "$ROOT/shared" || exit 2
java -Xmx2g -Dstdout.encoding=UTF-8 -cp "$WORK/classes:$ROOT/shared/src/jvmSharedTest/resources:$RUNTIME:$(jar 'hamcrest-core-*.jar')" \
  org.junit.runner.JUnitCore $classes > "$WORK/tout.txt" 2>&1
rc=$?
# The verdict is the summary line of the whole output, read from the file (lesson 131).
echo "$count test classes"
grep -E "^OK|^Tests run|^FAILURES" "$WORK/tout.txt"
[ $rc = 0 ] && grep -q "^OK" "$WORK/tout.txt" || { echo "FAILED (whole output: $WORK/tout.txt)"; exit 1; }

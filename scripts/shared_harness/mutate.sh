#!/usr/bin/env bash
# One deliberate breakage (lessons 100, 126): replaces the first <old> with <new> in <file>, runs the shared harness, prints
# whether a test caught it, and always puts the file back (also on Ctrl-C).
#
#   scripts/shared_harness/mutate.sh <file> <name> <old> <new> [extra test files...]
#
# "killed" = a test failed (the guard is held); "SURVIVED" = every test still passed (a class of sentence is missing);
# a breakage that does not compile is said as such, never counted as killed.
set -u -o pipefail
[ $# -ge 4 ] || { sed -n '2,7p' "$0"; exit 2; }
HERE=$(cd "$(dirname "$0")" && pwd)
f=$1; name=$2; old=$3; new=$4; shift 4
backup=$(mktemp)
cp "$f" "$backup"
trap 'cp "$backup" "$f"; rm -f "$backup"' EXIT INT TERM
python3 - "$f" "$old" "$new" <<'PY' || { echo "== $name: anchor not found"; exit 2; }
import sys
p, o, n = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if o not in s:
    sys.exit(1)
open(p, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
"$HERE/run.sh" "$@" > /dev/null 2>&1
case $? in
  0) echo "== $name: SURVIVED" ;;
  1) echo "== $name: killed" ;;
  3) echo "== $name: did not compile (not a test's catch: change the breakage)" ;;
  *) echo "== $name: harness error" ;;
esac

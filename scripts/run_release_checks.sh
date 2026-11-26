#!/usr/bin/env bash
# Every check of the Strings workflow, in one place, for the release gate (aab.yml) — 17 Nov 2026.
#
# aab.yml ran 9 of the Strings workflow's checks: a fault the other 10 catch (a real phone number, a Room alias that is an
# SQL keyword, a word key that is no resource, a bug class with no test, Hudhud writing to the book…) could reach the Play
# bundle. The list below is checked against strings.yml's own "run: python3 scripts/…" lines first, so the two cannot drift:
# a check added there and not here fails here.
set -euo pipefail
cd "$(dirname "$0")/.."

CHECKS=(
  "check_strings.py"
  "check_dark_colours.py"
  "check_dark_pairs.py"
  "check_pdf_rtl.py"
  "check_icon_direction.py"
  "check_icon_fit.py"
  "gen_desktop_invoice.py --check"
  "gen_desktop_cards.py --check"
  "gen_desktop_poster.py --check"
  "gen_desktop_inspector.py --check"
  "gen_hudhud_bank.py --check"
  "check_hudhud_readonly.py"
  "check_hudhud_offline.py"
  "hudhud_manifest.py --check"
  "hudhud_glossary.py --check"
  "test_hudhud_checklog.py"
  "hudhud_speed_sample.py --check"
  "gen_hudhud_catalog.py --check"
  "check_book_columns.py"
  "check_bug_classes.py"
  "check_invented_numbers.py"
  "check_room_aliases.py"
  "hudhud_sets.py --check"
  "check_word_keys.py"
  "check_dao_gates.py --ratchet"
  "check_repo_guards.py"
  "check_main_thread.py"
  "check_work_status.py"
  "check_pref_names.py"
  "check_capability_matrix.py"
  "check_data_flows.py"
  "check_formula_book.py"
  "gen_covering_array.py --check"
)

# The list must be strings.yml's own.
want=$(grep -oE 'python3 scripts/[a-z_0-9]+\.py( --[a-z]+)?' .github/workflows/strings.yml | sed 's#python3 scripts/##' | sort -u)
have=$(printf '%s\n' "${CHECKS[@]}" | sort -u)
if [ "$want" != "$have" ]; then
  echo "::error::run_release_checks.sh and strings.yml list different checks:"
  diff <(echo "$want") <(echo "$have") || true
  exit 1
fi

failed=0
for c in "${CHECKS[@]}"; do
  # shellcheck disable=SC2086
  if ! python3 scripts/$c; then echo "::error::release check failed: $c"; failed=1; fi
done
bash scripts/shared_harness/run.sh --lists-only || { echo "::error::the shared harness's file lists are stale"; failed=1; }
# The training cycle's own checks (no Java, no training; about 30 s): the gate, the SHIP rule, the pairs, the bank recipes, the ledger.
bash scripts/hudhud_cycle.sh --self-test || { echo "::error::the training cycle's self-test failed"; failed=1; }
exit $failed

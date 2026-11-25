# Hudhud model card — {{release_name}}

<!--
The template of the model card every Hudhud release carries (plan v1, Marhala 4 and 12; the training cycle's step 10).
scripts/hudhud_model_card.py fills every {{name}} from the repository (hashes, sizes, counts, the recipe and the gates as the
code says them) and leaves every [[MEASURED: ...]] for a person to fill from a named run. Write a measured figure as a count
("1,097 of 1,170"), never a feeling (lesson 144); write a zero as "0 in N", never "never" (the rule of three, below).
Release cards are written outside docs/ (e.g. the release notes); this file stays a template.
-->

| | |
|---|---|
| Card made | {{made_on}} from commit {{commit}} (branch {{branch}}) |
| App versions | phone {{phone_version}} (versionCode {{phone_code}}), Windows {{desktop_version}} |
| What it is | Hudhud's learnt sense: an averaged-perceptron ensemble read beside hand-written rules (`Hudhud.decide`). Offline; no language model in the app (D4). |
| `sense.bin` | SHA-256 `{{sense_sha}}`, {{sense_bytes}} bytes |
| Rollback | the previous `sense.bin`: SHA-256 `{{prev_sense_sha}}` ({{prev_sense_note}}). Rolling back means checking that file out with its own bank (`bank.tsv` at the same commit), so HudhudModelTest re-learns it byte for byte. |
| Owner's yes | [[MEASURED: date and words of the owner's yes for this release]] |

## Data

| File | SHA-256 | Lines |
|---|---|---|
{{data_rows}}

- Bank: {{bank_lines}} sentences, {{bank_meanings}} meanings, recipe {{bank_recipe}} (`scripts/gen_hudhud_bank.py`). Per language: {{bank_per_lang}}.
- Held-out (dev data since the rules were tuned looking at it, lessons 107 and 163): {{held_lines}} sentences; per language: {{held_per_lang}}.
- Owner's lists: short {{owner_short}} rows, 1,000 list {{owner_1000}} rows. Review sets: D {{review_d}} rows, O {{review_o}} rows.
- Source files (`shared/hudhud/**/*.tsv`): {{source_files}} files, combined SHA-256 `{{source_sha}}` (the SHA-256 of their sorted `sha  path` lines).
- Sealed set v2 (D12, D18): kept in the owner's folder, never in the repository. SHA-256 of the sealed file(s) [[MEASURED: from the owner's folder, written by the owner]]; sentences 500 (400 Roman Urdu, 100 English; 400 by the owner, 100 by a second writer).
- Leakage: `python3 scripts/hudhud_sets.py --check --sealed <owner's folder>`: exact train overlaps listed {{known_overlaps}}; near-duplicates [[MEASURED: the "near-duplicates" lines of that run, sealed included]].
- No real customer, number or shop name in any set (plan "Never" 4); names are the invented books of the set tests.

## Recipe

- Learner: averaged perceptron, {{epochs}} passes, features: words, Mind concepts, word pairs, sound skeletons, three-letter pieces (weights concept {{w_concept}}, word {{w_word}}, pair {{w_pair}}, sound {{w_sound}}, piece {{w_piece}}); features seen in fewer than {{min_df}} sentences dropped; softmax temperature {{temperature}}.
- Ensemble: {{seed_count}} shuffles, seeds {{seeds}}, weights averaged before 16-bit quantisation; weights under {{prune}} steps pruned (`HudhudSense.trainShipped`).
- Reproduce: `python3 scripts/gen_hudhud_bank.py --check`, then HudhudModelTest re-learns `sense.bin` byte for byte (CI).
- Seeds noise (lesson 163): [[MEASURED: two other ensembles' figures (e.g. seeds 1000.. and 2000..), scripts/hudhud_recipe.sh]].

## Gates (`Hudhud.decide`, as the code says them)

{{gates}}

Calibration (lesson 165, `scripts/hudhud_calibrate.sh`, `docs/hudhud-calibration-25nov2026.md` for the method):
[[MEASURED: OOF ECE and held-out ECE; share right at 0.95-0.97 / 0.98-0.99 / 0.99+; the per-meaning thresholds proposed and whether this release adopted them]].

## Figures (counts; dev sets are not "accuracy")

| Set | en | ur-Latn | ur | hi | id | sd (beta) | bn (beta) | fa (beta) | ar (beta) | All |
|---|---|---|---|---|---|---|---|---|---|---|
| Held-out strict | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] of {{held_lines}} |
| Review D (held rows) | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] of {{review_d}} |
| Review O (held rows) | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] | [[MEASURED]] of {{review_o}} |
| Sealed v2 (certified only for ur-Latn and en, D18) | [[MEASURED]] | [[MEASURED]] | not certified | not certified | not certified | not certified | not certified | not certified | not certified | [[MEASURED]] of 500 |

- Owner's short list: [[MEASURED]] of {{owner_short}} (floor [[MEASURED: floor at this release]]); owner's 1,000 list: [[MEASURED]] of {{owner_1000}}.
- **Beta (D17):** Sindhi, Bengali, Farsi and Arabic are "beta" in the app and in every figure until a native speaker has read them. [[MEASURED: native read done? by whom, when]]
- Speed (Marhala 2, the owner's phone): p50 / p95 / p99 [[MEASURED]] ms per question; start [[MEASURED]] ms; memory [[MEASURED]] MB.

## Hard-zero gates

A zero on N sentences is not a proof of "never": by the rule of three the true rate may still be up to about 3/N
(0.6% on 500). Write "0 in N".

| Gate | Result | Measured on |
|---|---|---|
| Wrong money side | [[MEASURED: "0 in N" per set: held-out / owner short / owner 1,000 / D / O / sealed]] | entity metrics, money-side pairs, sealed set |
| Wrong amount | [[MEASURED]] | entity metrics, sealed set |
| Answer about the wrong person or product | [[MEASURED]] | entity metrics, name tests |
| The owner's figure shown to a cashier | [[MEASURED]] | HudhudRolesTable, cashier golden answers |
| A write by Hudhud itself | {{readonly_result}} | `scripts/check_hudhud_readonly.py` |
| A private note spoken at the counter | [[MEASURED]] | voice tests |
| A network call during a text answer | {{offline_result}} | `scripts/check_hudhud_offline.py` (the engine closure; a code check, not a run) |

## Known weaknesses

[[MEASURED: the top confusions of this release (scripts/hudhud_baseline.sh), the meanings that never reach their target
precision (the calibration's "not reached"), the rows lost against the previous release (`comm` of the two miss lists,
lesson 165), and what the rules hold that the sense does not.]]

- The sense is over-sure out of fold (lesson 165): its certainty is a ranking, not a probability; the gates come from the measured table, never from the number itself.
- Cross-script twins (Roman Urdu / Urdu / Hindi) share the sense's sound features: a figure from a split that keeps them apart is flattered.
- The owner's lists and held-out are dev data; only the sealed set is an exam, and only for Roman Urdu and English (D18).

## Rollback

1. `git checkout <previous release commit> -- app/src/main/assets/hudhud/sense.bin shared/src/jvmSharedTest/resources/hudhud/bank.tsv shared/hudhud`
2. Check `sha256sum app/src/main/assets/hudhud/sense.bin` = `{{prev_sense_sha}}`.
3. Run the full harness and every release check; the floors of that release apply again.

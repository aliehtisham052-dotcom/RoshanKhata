# Hudhud's training cycle as one command (Marhala 8 preparation, 26 Nov 2026)

The plan's ten steps (`docs/hudhud-master-plan-v1.md`, "The training cycle") run as one reviewed command. A retrain is
done only when a mistake is proven (the owner's check-log marks, his lists, a native read) and only through this cycle.

```
scripts/hudhud_cycle.sh --additions FILE                  # gate, learn, measure, compare, calibrate, verdict; writes only build/
scripts/hudhud_cycle.sh --additions FILE --apply          # the same; if SHIP, writes the bank, sense.bin, ledger, manifest
scripts/hudhud_cycle.sh --self-test                       # the cycle's own checks, no Java (about 30 s; run_release_checks.sh)
```

Options: `--next-key` (recipe 3, below), `--sealed DIR` (the owner's sealed set, outside the repository: an addition
near it is refused and the sealed sentence is never printed), `--others 1000,2000` (the two other ensembles),
`--no-calibrate` (a fast look; it can never SHIP), `--allow-word W`, `--out DIR` (must be under `build/`).
Exit 0 SHIP, 1 NO-SHIP or refused, 2 usage, 3 a step failed.

## Step 1-2: from a mistake to its class

Each check-log mark (`scripts/hudhud_checklog.py`, names and numbers masked, kept outside the repository) becomes a CLASS
in `docs/bug-classes.tsv`. Rule first (step 5): a money side, a write or a not-moved sum is fixed in the rules with a test,
never by bank sentences. Only what the rules cannot carry goes to the bank.

## Step 3: the additions file

One sentence a line: `language<TAB>meaning<TAB>sentence<TAB>source<TAB>class`.

- `language`: the nine (`in` is read as `id`); `meaning`: a label of Hudhud.kt's meaning table.
- `sentence`: names and goods only as `{NAME}` and `{ITEM}`; written in small letters as the bank is.
- `source`: `checklog-class` (written by Claude from a class, never the owner's row; plan "Never" 5), `synthetic`, or
  `pair` (`scripts/hudhud_pairs.py`).
- `class`: a BC id or a class name (required for `checklog-class` and `pair`).

`scripts/hudhud_pairs.py` writes one-word-changed partners (who gave: `ne`/`ko`/`se`; the figure: sale/munafa/wusooli,
sales/profit/payments received) with their own labels, in Roman Urdu, Urdu script and English. A partner where no money
moved (`nahi diye`, `did not give`) or will move (`dega`, `dunga`, `will give`) has no bank label: it goes to a separate
RULES file for a rule test, never into the bank (lesson 166). Other languages wait for a native speaker's rule (D17).

## Step 4: the quality gate (`hudhud_cycle.py gate`)

Refused, with every reason: bad columns, language, meaning, source or class; an empty sentence or none of its script's
letters; a phone-shaped number outside the invented family; a capital word (a name; the bank's own capital words such as
WhatsApp, Hudhud, Pak, Bang pass) or a test book's invented name or good; a sentence already in the bank (nothing would be
learnt) or there with another meaning; two additions that are one sentence; **leakage**: an addition that is a held-out,
owner or review (or sealed) sentence by the bank's key, or a near-duplicate of one (`hudhud_sets.near_key`, Jaccard
>= 0.80 with names, goods and numbers masked). Then the candidate tree is built in `DIR/src` and its bank in
`DIR/res/hudhud/` by `gen_hudhud_bank.read`, and `hudhud_sets.py`'s whole gate runs on the candidate: every addition must
be in the candidate bank, the held-out set byte for byte the repository's. Cross-script twins (the same sentence in
Roman Urdu and Urdu script) are not seen by letters; the calibration's folds keep them together instead.

## Steps 6-9: learn and measure (`HudhudCycle.kt`, `HudhudBaseline.pass`)

- The candidate is learnt by the shipped recipe (`HudhudSense.trainShipped`: seeds 313..327, prune 200), which is what
  `HudhudModelTest.main` writes byte for byte.
- The base bank and the candidate bank are both learnt again under two other 15-seed ensembles (1000.., 2000..):
  a difference that one shuffle makes is noise (lesson 163). The base at 313 is the shipped `sense.bin` itself.
- Every labelled set is read with each of the six senses; `figures.tsv` holds the counts and the entity WRONG counts per
  set; `rows-<model>-<ensemble>.tsv` every row's reading.
- `compare/<ensemble>-lost.tsv` and `-gained.tsv`: the old and new miss lists, sentence by sentence (`comm`).
- `junit.txt`: `HudhudModelTest`, `HudhudSeedSafeTest` and `HudhudSeedSafeMoneyTest` run against the candidate bank and
  sense (a folder `DIR/root` that looks like the repository to them; nothing of the repository is written).
- `calibration.md`: `hudhud_calibrate.sh` on the candidate (its folds by paraphrase family, out-of-fold certainty table,
  per-meaning thresholds, what they would change). A REPORT: a threshold in `Hudhud.decide` is changed only by a person,
  judged on the owner's lists (lessons 165, 167).

## Step 10: the SHIP rule (`hudhud_cycle.py verdict`, `decide`)

SHIP only when every one holds; otherwise NO-SHIP with each reason:

1. the gate passed and the candidate bank differs from the shipped one ("no change" never ships);
2. hard zeros: wrong money side, wrong amount, wrong person, wrong product = 0 on every set, under all three ensembles;
3. the gain is above the seed noise under EVERY ensemble: the owner's two lists together +12, or held-out strict +6;
4. no loss past the noise under any ensemble (held-out -6, each owner list and each review set -12);
5. the floors the tests hold (HudhudOwnerSetTest, HudhudSenseTest) under the shipped ensemble;
6. the seed-safe guards and the model test pass against the candidate;
7. the thresholds were derived again (no `--no-calibrate`).

Rule 2 is the plan's hard-zero gate as written: while the shipped sense itself reads a wrong side on the owner's lists
(Marhala 1's work), no candidate can SHIP. That is intended: the money side is fixed by rules first.

`--apply` (only after SHIP, and only if the repository's bank and the additions file are the ones measured): the additions
are appended to `shared/hudhud/more/<lang>.tsv`, their provenance to `docs/hudhud-bank-additions.tsv`; `bank.tsv` is
written by `gen_hudhud_bank.py` and compared with the measured one; `sense.bin` is written by `HudhudModelTest.main` and
compared byte for byte with the measured candidate; the overlap list is rewritten (it only shrinks), the manifest
regenerated, the checks run, and the model card filled (`DIR/model-card.md`). Then, by hand: the harness, the floors
from `verdict.md`, a row-by-row decision on `compare/313-lost.tsv`, the card's `[[MEASURED]]` fields, the commit.

## The owed retrain fixes

- **Done in H4 (25 Nov 2026), recipe 2 is the default:** the Indic-safe sentence key (Devanagari and Bengali vowel signs
  kept) and the held-out copies left out of the bank.
- **Prepared, not applied: recipe 3** (`gen_hudhud_bank.py --recipe 3`, alias `--next-key`): also leaves out of the
  bank every sentence of the measured sets (owner_set, owner_1000, review_d, review_o; 240 rows today, the 241 entries
  of `scripts/hudhud_sets_overlap.tsv`), so the floors stop measuring the bank's memory. `--check` proves recipe 3 only
  loses those rows and keeps the held-out set; `scripts/test_gen_hudhud_bank.py` (9 tests) holds it. The cycle's
  `--next-key --apply` is the one road that makes it the default (it flips `DEFAULT_RECIPE`, learns the sense and
  shrinks the overlap list in the same step). Expect the owner's floors to fall when it lands: they were partly memory.

## Out-of-scope sentences (MASSIVE, CC-BY-4.0)

Checked 26 Nov 2026 from this session: the dataset's hosts (Hugging Face `AmazonScience/massive`, the Amazon S3 archive)
answer 403 through the proxy; only the code repository on GitHub and an unrelated PyPI package named `massive` are
reachable. Not fetched. The hook is documented in `scripts/hudhud_pairs.py` (`--massive DIR`, refused today): with the
data placed by the owner in a folder outside the repository, a reader would keep only non-ledger intents (weather, music,
alarm, IoT, play) in the nine locales and write them as RULES rows expecting Hudhud's OffTopic, never as bank sentences
(the bank has no off-topic meaning). Its licence goes into `docs/hudhud-rights-register.tsv` before any row is used.

## Data manifest v3: what a cycle writes

| Where | What | Kept |
|---|---|---|
| `DIR/gate.txt`, `DIR/gate.json` | the gate's reasons; the run's state (recipe, additions and their SHA-256, both banks' SHA-256) | build only |
| `DIR/src/`, `DIR/res/hudhud/` | the candidate's sources, bank and held-out | build only |
| `DIR/sense-candidate.bin` | the candidate sense (what `--apply` ships) | build only |
| `DIR/figures.tsv`, `DIR/rows-*.tsv`, `DIR/compare/` | counts, every row's reading, the miss lists | build only |
| `DIR/junit.txt`, `DIR/calibration.md` | guards against the candidate; the certainty table and thresholds | build only |
| `DIR/cycle-manifest.tsv` | the run's record: commit, recipe, seeds, every input and output with its SHA-256 | build only |
| `DIR/verdict.md` | SHIP or NO-SHIP with every reason | build only |
| `shared/hudhud/more/<lang>.tsv` | the additions (with `--apply`) | repository |
| `docs/hudhud-bank-additions.tsv` | the ledger: date, cycle (additions SHA-256 prefix), language, meaning, source, class, sentence | repository |
| `docs/hudhud-data-manifest.tsv` | regenerated (rows, bytes, SHA-256 of every data file) | repository |

The ledger is not in the data manifest's scope (`docs/`); `hudhud_cycle.py ledger-check` (in `--self-test`) holds each of
its rows to a line of its `more/` file.

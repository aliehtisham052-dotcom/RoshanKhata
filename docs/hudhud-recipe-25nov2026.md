# Hudhud sense: a stable training recipe (plan H1, 25 Nov 2026)

Measured, not shipped. `sense.bin`, `bank.tsv` and `heldout.tsv` are unchanged. Importance 9.
All figures are counts of rows (lesson 144), measured in memory with `scripts/hudhud_recipe.sh`.
Runs A/B were taken on an earlier tree snapshot than C/D/E; the other helper's edits to `Hudhud.kt`
moved the SHIPPED sense's owner_1000 from 807 to 804/805 during the day (not caused by this work).
Shipped sense at the time of E: held-out 1059 strict / 1076 lenient, owner short 157, owner_1000 805,
review D 1046, review O 1074, wrong side 4 (0/1/3/0/0 by set), wrong amount 0.

## What was built

1. **Data recipe 2** (`scripts/gen_hudhud_bank.py --recipe 2 --out-dir DIR`; default stays recipe 1 until H4):
   - the sentence key keeps Devanagari and Bengali vowel signs (marks in U+0900-U+09FF), so 43 distinct
     sentences that recipe 1 wrongly treated as copies are kept (bn 26, hi 17);
   - first-round copies of held-out sentences are left out of the bank (9 lines, 6 distinct);
   - bank 13,340 -> 13,374 lines; the held-out set is identical across recipes (checked).
   - `--check` proves both (synthetic pairs plus data invariants); `scripts/test_gen_hudhud_bank.py` (5 tests).
2. **Ensemble training** (`HudhudSense.trainEnsemble(lines, seeds)`): K perceptrons, one seed each, weights
   averaged BEFORE quantisation. `train(lines, seed)` is `trainEnsemble(lines, listOf(seed))` and is
   byte-identical to before. Reading code, features and file format are unchanged.
3. **Features lock** (`HudhudRecipeTest`, 7 tests): 30 sentences in nine languages with the feature count and
   a SHA-256 prefix of the sorted features; weight constants; K=1 equals `train` byte for byte; an ensemble
   is deterministic, is the mean, round-trips through the file; an empty seed list is refused.
   Five mutations (piece length, no mean division, last seed dropped, W_WORD, Arabic article) all failed it.
   After a deliberate feature change run `HudhudRecipeTest.main` and paste the table.

## Results (5 outer seeds each; K=1 uses 314..318, K>1 uses 1000*(o+1)+i)

Mean over the five outer seeds, with min..max:

| recipe | held-out strict | owner short | owner_1000 | review D held | review O held | wrong side |
|---|---|---|---|---|---|---|
| shipped (seed 313) | 1059 | 157 | 805 | 1046 | 1074 | 4 |
| data 1, K=1 | 1056.8 (1050..1062) | 151.0 (146..155) | 798.8 (795..802) | 1025.6 | 1055.0 | 4..6 |
| data 2, K=1 | 1059.4 (1056..1063) | 150.2 (148..152) | 796.2 (794..799) | 1026.8 | 1053.0 | 4..8 |
| data 1, K=5 | 1076.6 (1072..1082) | 154.2 (151..157) | 807.6 (804..813) | 1034.8 | 1059.0 | 5..6 |
| data 2, K=5 | 1077.2 (1074..1082) | 155.8 (154..158) | 802.8 (799..811) | 1033.6 | 1060.6 | 5..7 |
| data 2, K=15 | 1081.2 (1078..1083) | 156.0 (155..157) | 804.4 (802..808) | 1035.8 | 1062.2 | 5..6 |

Wrong amount is 0 everywhere except two K=5 data-1 cells and K=1 data-2 (1..4 rows under some seeds).
Wrong side K=15 by set (held/owner short/owner_1000/D/O): 0/1/4..5/0/0 (shipped 0/1/3/0/0).

**Seed spread (rows whose reading is not the same under all 5 outer seeds, of 4,652):**
data 1 K=1 515; data 2 K=1 529; data 1 K=5 242; data 2 K=5 244; data 2 K=15 139.
Per set at data 2 K=15: held-out 31, owner short 13, owner_1000 51, review D 14, review O 30.

**Guard sentences** (542, mirroring HudhudSeedSafeTest and HudhudSeedSafeMoneyTest): failing under at least
one outer seed: data 1 K=1 0; data 1 K=5 2; **data 2 K=1 9 distinct** (two rows 2 of 5 seeds); data 2 K=5 0;
data 2 K=15 0. Sentences whose money side differs between outer seeds: 13 (data 2 K=1), 3 (K=5), 0 (K=15).
So recipe 2 data alone is not safe with one seed: it needs the ensemble.

**Held-out strict per language, data 2 K=15 (of 130, spread over 5 outer seeds)** vs data 1 K=1 spread:
en 122..123 (120..122); ur-Latn 119..121 (114..122); ur 120..122 (115..119); sd 121..122 (116..121);
ar 115..116 (112..117); fa 119..121 (113..120); hi 119..121 (117..121); bn 118..121 (112..117); id 120..122 (117..120).

**Against the shipped sense (data 2 K=15, a row counts when 3 of 5 seeds agree):** lost 57 rows it read right,
gained 57 it read wrong, 30 more right rows lost under some seeds only. Lost by set: held-out 12 (hi 4, ar 2,
sd 2, en/ur/ur-Latn/fa 1), owner short 5, owner_1000 18, review D 10, review O 12. Gained: held-out 35,
owner short 6, owner_1000 16. Money side opposite to shipped under at least one seed: 1 row
(owner_1000 "Mere hisse ka kitna baqi hai", 4 of 5 seeds read TO_GIVE; shipped TO_GET).

**Cost:** the K=15 file is 764,812 bytes (shipped 365,222) because the averaged weights fill more
pieces (285,195 against 110,570). The median weight is 820 steps at K=1 and 193 at K=15 (of 32,767), and 42,280 K=15
weights are under 50 steps. Training K=15 takes 5..14 s. A pruning step (drop weights under a few
quantisation steps) is the way back to about 400 KB; it was not measured.

## Reading the numbers honestly

- The shipped seed 313 is a lucky one for the owner's and reviewers' sets: the rules were fixed while looking
  at its reading. Any retrain, even K=1 at another seed, loses about 20 review D/O rows and about 6 owner
  short and 6 owner_1000 rows against shipped (K=1 means 1026/1055 against 1046/1074). Ensembling does not bring them back;
  it only makes the loss the same under every seed. The lost rows are listed in `/tmp/recipe-out/E.md` (kept
  outside the repository; re-create with the command below).
- Held-out is dev data (lessons 107, 163): the +20 over shipped at K=15 is real in the sense of stability
  (averaging halves-to-fifths the seed noise) but is not an accuracy claim about unseen questions.
- Averaging weights is not voting: the reading code sees one sense, so no code that reads `HudhudSense` changes.

## Recommendation for H4

1. Flip `DEFAULT_RECIPE` in `gen_hudhud_bank.py` to 2, regenerate `bank.tsv`/`heldout.tsv` (held-out identical),
   run `--check`.
2. Train one sense with **K=15, seeds 313..327**, with the data of recipe 2; write `sense.bin` once.
   (K=5 already gives most of the stability: 244 moved rows against 139; K=15 is what removes every guard failure.)
3. Make `HudhudModelTest` compare against `trainEnsemble(bank, seeds 313..327)`. Make `HudhudSeedSafe*Test`
   learn other ENSEMBLES (e.g. 1000..1014, 2000..2014), not single seeds: single-seed learning of the new data
   fails 9 guards by design.
4. Re-check these guard readings: money side (`HudhudSeedSafeMoneyTest`, 542-sentence guard set), orders to the machine,
   "how much money is stuck outside", "top 5 baqidar dikhao", Arabic "which customers did not pay me",
   Farsi suppliers I have not paid, Indonesian "debitur terbesar".
5. Floors: re-measure and set from the new sense (expect owner short 155..157, owner_1000 about 802..808,
   review D about 1036, review O about 1062; the old 807/1046/1074 will not hold). Decide row by row for the
   lost lists: re-class with a rule (the rules fix what must not move), or promote.
   Special case: owner_1000 "Mere hisse ka kitna baqi hai" needs a rule (money side said by the sentence)
   or its wrong-side floor of 3 raised to 4.
6. Consider pruning to keep the app's size close to today's. Check the file size in the APK budget first.
7. Re-run `HudhudRecipeTest` (it fails if `HudhudSense.features` changed) and the full harness; then every release check.

## Reproduce

```
python3 scripts/gen_hudhud_bank.py --recipe 2 --out-dir /tmp/bank2
scripts/hudhud_recipe.sh --bank2 /tmp/bank2/bank.tsv --k 15 --only 1x1,1x5,2x1,2x5,2x15
python3 -I scripts/test_gen_hudhud_bank.py
scripts/shared_harness/run.sh   # the verdict is the line in build/shared-harness/tout.txt
```

## H4 result (25 Nov 2026): the one retrain, recipe 2, K=15 ensemble (final, after the regression follow-up)

- Recipe 2 is the default in `scripts/gen_hudhud_bank.py`; bank.tsv has 13,374 lines (held-out 1,170, unchanged). `sense.bin` was written once by `HudhudSense.trainShipped` (seeds 313..327, `SHIPPED_PRUNE` 200): 434,933 bytes. CI must show it re-learns byte for byte (HudhudModelTest). The model, seed-safe and money-side tests use the same ensemble recipe (`otherSenses` = four ensembles of five).
- **Root cause of the 30 regressions:** not prune (0, 100 and 200 behave alike on the owner sets). The ensemble is surer than one perceptron, and on held-out it is right about 68% of the time at 0.95-0.97 (HEAD's sense 78%), about 96% at 0.98-0.99 and 98% from 0.99. The FIRM turn of a rules' reading by the sense is therefore `SENSE_FIRM_TURN = 0.99`; `SENSE_OVERRULE` stays 0.95 (raising it globally broke goods turns).
- Four class guards in `Hudhud.decide` (through `understand` with the sense, lesson 145): cash left ("cash bacha") is the drawer; "bechna" with today is the day's sales; "kis ko payment ki" is the day's activity; a low-stock reading is not turned to a money side by the sense. Earlier: `MY_SHARE`, whole-name words, order "do", praise, earned today, drawer "hua", tell-me, who-supplies, tied party_details. Each guard was broken once (sell, paid, stuck, cash) and a test failed.
- Measured (849 shared tests): held-out 1,097 of 1,170; owner short lists 159 of 200 (floor raised to 158); owner 1,000 sentences 811 (floor raised to 808); review O/D rows keep their readings except two Sindhi rows (below); seed-safe, money-side and entity-metric tests pass; HudhudWordsTest 9 of 9 (lesson 112 route); bank, sets and release checks pass.
- **Left, for the lead:** two Sindhi review rows read differently (no relabelling done): "Urea جون ڪيتريون ٻوريون آهن" reads Goods:STOCK (HEAD OVERVIEW) and "ميعاد ڪڏهن ختم ٿئي ٿو" reads Figure:EXPIRING (HEAD NeedGoods). Both new readings are arguably the better answers; no safe class rule restores HEAD's. HudhudShortWordSweepTest prints that [blm, nma] are no longer met and can leave its frozen list.

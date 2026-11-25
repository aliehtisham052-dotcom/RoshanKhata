# Hudhud calibration and the clean-exam guards (plan v1, Marhala 3 and 12; 25 Nov 2026)

Measured, not shipped. REPORT ONLY: no reading changed — nothing under `shared/src/jvmSharedMain`, `sense.bin`, the bank or
any shipped threshold was edited. Importance 8. Commit base c870984 (kmp-desktop-16), branch m3-calib. All figures are counts
of rows (lesson 144); every set here is dev data except the sealed set, which was not used (plan "Never" 9).

## What was built

| Piece | File | What it does |
|---|---|---|
| Folds | `scripts/hudhud_calib_folds.py` | Traces every bank line to (round, meaning, place) and splits the bank in 5 folds by **paraphrase family**: one place's forms in all nine languages plus every sentence equal under `key_indic` (hudhud_sets.py's leakage key) are one family (union-find); dealt per meaning to the smallest fold. 13,374 lines, 1,486 families, folds 2,671 / 2,678 / 2,671 / 2,678 / 2,676. `--check` proves no family, sentence or place spans two folds. `--by-text` gives the naive split (13,355 families) for comparison. |
| Runner | `scripts/shared_harness/HudhudCalibrate.kt` | Learns each fold's sense with the SHIPPED recipe (`trainEnsemble(seeds 313..327, prune 200)`, first proved byte-identical to `sense.bin` on the whole bank), reads the held-out fold (OOF), reads held-out with the shipped `sense.bin`, prints the reliability tables, ECE, per-meaning thresholds, and runs policies end to end. Writes per-row predictions and `proposals.tsv` to `build/hudhud-calibrate/out`. |
| Gate copy | `scripts/shared_harness/patch_gates.py` | Writes a copy of `Hudhud.kt` into the build folder in which the 15 `p >= <gate>` comparisons of `decide` go through `HudhudCalibGate.at(label, gate, today)`; with no rule it returns `today`, so the copy reads as the app (proved: P0 below equals the b86f257 baseline to the row). Fails if decide's comparisons change (expected counts written in the script). |
| Wrapper | `scripts/hudhud_calibrate.sh` | Folds, gate copy, one compile behind `/tmp/harness.lock`, the run; heaps 2.5 GB (`CALIB_HEAP`). `--extra-seeds 1000,2000 --naive` adds two other 15-seed ensembles (lesson 163) and the naive split. |

Run time (2 cores, 7 GB, sharing the machine): the whole run with three ensembles, the naive split and three policies was
5 min 3 s wall (runner 225-300 s). One fold's 15-seed ensemble learns in 3.2-7.5 s and reads its 2,675 rows in 0.1-0.3 s, so
the **full 5 folds x 15 seeds was run; no smaller setting was needed**. Each end-to-end policy pass takes 50-65 s. The OOF
tables were identical across two runs (determinism).

```
scripts/hudhud_calibrate.sh --extra-seeds 1000,2000 --naive > calib.md   # stdout = these tables; stderr = progress
```

What the sense reads here is its own reading of the sentence with the bank's placeholders (`{NAME}` -> qqname), the
certainty `decide` compares; it is the sense's calibration, not the whole pipeline's (the pipeline is measured in section 4).

## 1. Reliability (certainty -> share right)

**Out of fold, shipped recipe (seeds 313..327, prune 200), 13,374 rows:**

| certainty | rows | right | share right | mean certainty | gap | money-side rows | money right | opposite side |
|---|---|---|---|---|---|---|---|---|
| 0.000-0.300 | 135 | 23 | 17.0% | 0.180 | -0.010 | 11 | 0.0% | 0 |
| 0.300-0.500 | 436 | 156 | 35.8% | 0.414 | -0.057 | 63 | 30.2% | 10 |
| 0.500-0.600 | 381 | 174 | 45.7% | 0.549 | -0.092 | 51 | 45.1% | 10 |
| 0.600-0.800 | 933 | 541 | 58.0% | 0.706 | -0.126 | 121 | 62.0% | 18 |
| 0.800-0.900 | 677 | 450 | 66.5% | 0.854 | -0.189 | 95 | 63.2% | 13 |
| 0.900-0.920 | 204 | 159 | 77.9% | 0.910 | -0.130 | 26 | 61.5% | 6 |
| 0.920-0.950 | 459 | 358 | 78.0% | 0.936 | -0.156 | 56 | 85.7% | 5 |
| 0.950-0.970 | 504 | 428 | 84.9% | 0.961 | -0.112 | 64 | 82.8% | 4 |
| 0.970-0.980 | 388 | 343 | 88.4% | 0.975 | -0.091 | 48 | 85.4% | 2 |
| 0.980-0.990 | 677 | 611 | 90.3% | 0.986 | -0.083 | 93 | 92.5% | 6 |
| 0.990-0.995 | 733 | 689 | 94.0% | 0.993 | -0.053 | 91 | 94.5% | 3 |
| 0.995-1.000 | 7,847 | 7,714 | 98.3% | 0.999 | -0.016 | 728 | 98.1% | 11 |
| all | 13,374 | 11,646 | 87.1% | 0.925 | | 1,447 | 84.4% | 88 |

ECE 0.0544 (15 equal bins), 0.0537 (the gate bins). Over-sure in every bin above 0.30.

**Held-out (dev data), the shipped sense.bin, 1,170 rows:**

| certainty | rows | right | share right | mean certainty | money-side rows | money right | opposite side |
|---|---|---|---|---|---|---|---|
| under 0.60 | 47 | 21 | 44.7% | | 5 | 2 | 2 |
| 0.600-0.900 | 86 | 56 | 65.1% | | 9 | 7 | 0 |
| 0.900-0.950 | 35 | 29 | 82.9% | | 7 | 6 | 1 |
| 0.950-0.970 | 28 | 23 | 82.1% | 0.961 | 3 | 2 | 1 |
| 0.970-0.980 | 27 | 27 | 100.0% | 0.976 | 1 | 1 | 0 |
| 0.980-0.990 | 63 | 62 | 98.4% | 0.986 | 10 | 10 | 0 |
| 0.990-0.995 | 82 | 80 | 97.6% | 0.993 | 8 | 8 | 0 |
| 0.995-1.000 | 802 | 801 | 99.9% | 0.999 | 83 | 82 | 1 |
| all | 1,170 | 1,099 | 93.9% | 0.956 | 126 | 118 | 5 |

ECE 0.0214 (15 equal bins). Cumulative share right from a cut (all meanings / money meanings):

| cut | OOF all | OOF money | held-out all | held-out money |
|---|---|---|---|---|
| >= 0.60 (SURE) | 90.9% of 12,422 | 89.2% of 1,322 | 96.0% of 1,123 | 95.9% of 121 |
| >= 0.92 (CERTAIN) | 95.6% of 10,608 | 95.2% of 1,080 | 98.7% of 1,031 | 97.3% of 111 |
| >= 0.95 (OVERRULE) | 96.4% of 10,149 | 95.7% of 1,024 | 99.1% of 1,002 | 98.1% of 105 |
| >= 0.98 | 97.4% of 9,257 | 97.1% of 912 | 99.6% of 947 | 99.0% of 101 |
| >= 0.99 (FIRM_TURN) | 97.9% of 8,580 | 97.7% of 819 | 99.7% of 884 | 98.9% of 91 |
| >= 0.995 | 98.3% of 7,848 | 98.1% of 728 | 99.9% of 802 | 98.8% of 83 |

**Other ensembles and the split (lesson 163):** OOF ECE 0.0543 (seeds 1000..1014) and 0.0533 (2000..2014): the table is a
property of the recipe, not of one shuffle (share right per bin moves by at most about 4 points between ensembles). The naive
text-only split reads 11,753 right (87.9%, ECE 0.0498) against 11,646 (87.1%, ECE 0.0544) by family: keeping one sentence's
other scripts out of training costs 107 rows, so a split that ignores the scripts flatters the sense by about 0.8 points.

**Per language (OOF right, ECE / held-out right, ECE):** en 89.2%, 0.048 / 125 of 130, 0.029; ur 88.4%, 0.045 / 123, 0.044;
ur-Latn 89.1%, 0.039 / 121, 0.038; hi 88.6%, 0.047 / 121, 0.036; bn 87.7%, 0.062 / 122, 0.026; **ar 80.5%, 0.101** / 118, 0.061;
fa 85.1%, 0.061 / 120, 0.055; sd 87.4%, 0.054 / 125, 0.023; id 87.7%, 0.060 / 124, 0.039. Arabic is the worst calibrated and
least accurate out of fold — one more reason it stays "beta" (D17), with Farsi next.

### Reading it honestly

- **Out of fold the ensemble is over-sure everywhere** (ECE 0.054): at 0.95-0.97 it is right 84.9%, at 0.98-0.99 90.3%, at
  0.99-0.995 94.0%. Only from 0.995 is it near its word (98.3%). Lesson 165 holds out of fold.
- **Held-out looks far better calibrated** (ECE 0.021, 98-100% from 0.97) because held-out is dev data: its sentences are the
  first round's places 3 and 8, written with the same hands as their siblings in the bank, and the rules were tuned on it
  (lessons 107, 163). OOF holds out whole families; it is the harsher, more honest table for the sense.
- **Lesson 165's "68% at 0.95-0.97" is not reproduced** by this measure: the sense alone with placeholders gives 23 of 28
  (82.1%) on held-out. That figure was taken through another route during H4 (the whole pipeline with the books' names; the
  route is not written down). The two tables agree on the direction: 0.95-0.97 is not safe, and 0.99 is the first cut that is.
  Small bins: 28 rows; one row is 3.6 points.
- Held-out, the sense alone reads 1,099 of 1,170 right (93.9%) — more than the whole pipeline's strict 1,081 — because names
  here are already placeholders; the pipeline must find them first.

## 2. Per meaning: the lowest certainty that reaches the target

Rule: the lowest certainty t (from 0.30 up) such that the share right of rows read as that meaning at t **and at every
higher cut of at least 20 rows** is at least the target: **0.98 for the seven money-side meanings** (to_get, to_give,
collected, entry_gave, entry_got, who_owes_most, not_paid), **0.95 for the other 58**. Derived out of fold; the conservative t
is the highest over the three ensembles (any ensemble that misses the target makes it "not reached").

| meaning | OOF read as it | @0.95 | @0.99 | t (313..) | t (1000..) | t (2000..) | **conservative t** | OOF @t | Wilson 95% low @t | held-out @t | opposite side @t OOF / held |
|---|---|---|---|---|---|---|---|---|---|---|---|
| to_get | 197 | 90.1% (111) | 97.2% (72) | 0.9959 | 0.9974 | 0.9945 | **0.9974** | 98.4% (61) | 0.913 | 80.0% (5) | 0 / 1 |
| to_give | 224 | 89.5% (133) | 91.4% (105) | 0.9995 | 0.9996 | 0.9996 | **0.9996** | 100% (34) | 0.898 | 100% (5) | 0 / 0 |
| collected | 207 | 96.2% (132) | 100% (96) | 0.9736 | 0.9613 | 0.9727 | **0.9736** | 98.3% (119) | 0.941 | 100% (13) | 0 / 0 |
| entry_gave | 207 | 97.8% (183) | 98.2% (163) | 0.9979 | 0.9991 | 0.9956 | **0.9991** | 98.6% (140) | 0.949 | 100% (16) | 2 / 0 |
| entry_got | 210 | 97.9% (188) | 98.3% (173) | 0.9752 | 0.9789 | 0.9855 | **0.9855** | 98.4% (183) | 0.953 | 100% (17) | 3 / 0 |
| who_owes_most | 202 | 96.3% (162) | 98.4% (127) | 0.9618 | 0.9786 | 0.9844 | **0.9844** | 98.1% (159) | 0.946 | 100% (18) | 0 / 0 |
| not_paid | 200 | 100% (115) | 100% (83) | 0.9098 | 0.9125 | 0.9257 | **0.9257** | 98.5% (131) | 0.946 | 100% (17) | 0 / 0 |

(the OOF @t / held-out @t columns are at the shipped seeds' t.)

Against today's gates (all global: MAYBE 0.30, the inline 0.5, SURE 0.60, CERTAIN 0.92, OVERRULE 0.95, FIRM_TURN 0.99;
`OWN_PAGE_SENSE` 0.80 keeps a page):

- **Money:** at OVERRULE 0.95 only not_paid (100%) reaches 0.98; to_get (90.1%) and to_give (89.5%) are the weakest — they
  are confused with each other, the very wrong side the hard-zero gate is about. Even at FIRM_TURN 0.99 to_give is right
  only 91.4% of 105 out of fold. The conservative t of five of the seven money meanings is above 0.98, of three above 0.99.
- **No t is statistically certified at 0.98:** the Wilson 95% lower bounds at t are 0.90-0.95 (34-183 rows). Out-of-fold rows
  per meaning are about 200; a 98% claim needs several hundred rows at the cut.
- **Others:** 63 of 65 meanings reach their target at some certainty under all three ensembles (conservative t); 52 of them
  below FIRM_TURN 0.99 and 21 above OVERRULE 0.95. Never reached: **goods_price** (91.3% at 0.99) and **who_are_you** (93.1%
  at 0.99). Unstable between ensembles (t moves by more than 0.2): salaam (0.33 / 1.00 / 1.00), calculator_uses
  (0.70 / 0.39 / 0.71), backup_status (0.33 / 0.49 / 0.61), shop_info (0.88 / 0.67 / 0.76); by 0.15-0.2: party_details,
  party_balance, cheques_status, expiring, returns_today — their precision hovers at the target, so one shuffle decides;
  such a t must not be adopted from one seed (lesson 163).
- **The sense's surest money-side mistakes:** out of fold 14 rows read on the opposite side at 0.99 or more (to_get read as
  to_give 7, entry_gave/entry_got both ways 3 + 3, to_give read as to_get 1; en 5, id 3, bn 2, ur, ur-Latn, hi, ar 1 each).
  Held-out, 5 opposite-side rows, one of them sure: Arabic "كم باقي علينا ندفع" (to_give) read as to_get at 0.9966; the
  others are under 0.97 (Farsi "کل بدهی مشتریان به مغازه" 0.962 and "کل پولی که باید بدم چقدره" 0.935, Hindi 0.392,
  Sindhi 0.587). **No certainty gate removes a wrong side the sense is 0.997 sure of: only a rule that reads who owes
  whom can** (lesson 163; Agent A's money-side work).

## 3. What per-meaning gates would change, end to end (report only)

Measured with the gate copy on every labelled set (counts of rows; held-out strict and lenient; wrong side by the entity
metrics: held-out / owner short / owner 1,000 / D / O):

| policy | held strict | held lenient | owner short | owner 1,000 | review D | review O | wrong side | wrong amount | rows changed | right -> wrong | wrong -> right |
|---|---|---|---|---|---|---|---|---|---|---|---|
| P0 today (no rule) | 1,081 | 1,097 | 159 | 812 | 1,046 | 1,074 | 4 (0/1/3/0/0) | 0 | 0 | 0 | 0 |
| P1 money floor: every gate >= the money meaning's conservative t | 1,069 | 1,085 | 158 | 804 | 1,042 | 1,068 | 4 (0/1/3/0/0) | 0 | 48 | 34 | 3 |
| P2 per-meaning: gates >= 0.90 become the meaning's t (not reached: 1.01), money floors as P1 | 1,076 | 1,092 | 154 | 801 | 1,040 | 1,063 | 4 (0/1/3/0/0) | 0 | 102 | 57 | 19 |

P0 equals the b86f257 baseline (`docs/hudhud-baseline-25nov2026.md`) on every figure: the gate copy reads as the app.
(The first run, with the shipped seeds' t instead of the conservative t, gave P1 1,073 / 158 / 805 / 1,043 / 1,069, 39 rows
changed, and P2 1,080 / 154 / 801 / 1,039 / 1,063, 104 changed — the same picture.)

**On the owner's lists (P1):** owner short 3 rows change (1 lost: "Logon ke zimme kitna hai?" TO_GET -> a help page; 2 were
wrong and stay wrong); owner 1,000: 17 change, 11 lost, 3 won. Lost readings are all correct money readings the sense made
below its conservative t and the rules could not make: "Logon ke zimme kitna hai", "Bazaar mein kitna paisa phansa hai",
"Mujhe kitne paise milne hain" (TO_GET), "Mere upar kitna bojh hai" (TO_GIVE), "Bade baqayadar dikhao", "Mere sab se bade
customers kaun hain", "Kis ka khata sab se bhaari hai" (WHO_OWES_MOST), "Kis ka hisaab bohat purana ho gaya", "Purane baqaye
ki list", "Payment ki list date ke saath dikhao" (NOT_PAID), "Is hafte cash kitna aya" (Collected) — each falls to a help page
or Unknown. Won: "Kaun sa din sab se achha raha", "Payment likhni hai", "Dono ka total ek saath dikhao" (each a wrong money
reading becoming a help page). Held-out loses 12, every one a money question read right today (7 fall to Unknown, three
entries such as "write 750 udhaar for {NAME}" become the person's account, one becomes a count, one a help page); review D
loses 4 and review O 6 ("top 5 baqidar dikhao", "how much money is stuck outside", "list of people I need to chase for money",
"total outstanding in the market").

**On the owner's lists (P2):** owner short 9 change (6 lost: "Aaj kis kis ko paise diye?", "Aaj ki adaigi kis ko ki?",
"Aaj kis kis ne payment ki?", "Aaj ke jama paise kis kis ke hain?" become WhenPays; "Logon ke zimme kitna hai?" and
"Baqaya kitna hai?" lost); owner 1,000: 36 change, 19 lost, 8 won. Lowering FIRM_TURN per meaning brings back the class of the
H4 regressions the 0.99 gate was made for (lesson 165): "Aaj kis ko payment ki" read as who will pay is H4's own class.

**The four wrong money sides at P0 are untouched by both policies** (owner short "Kis ki payment late ho gayi?" NOT_PAID;
owner 1,000 the same sentence, "Kitna qarz baqi hai" TO_GET, "Kis ka maal ka paisa baqi hai" TO_GET): they are read by the
rules or by the sense above every proposed t.

### Recommendation (for the lead; nothing changed)

1. **Keep decide's global gates as they are.** Per-meaning gates from this calibration cost 4-11 of the owner's rows
   (P1: owner 1,000 812 -> 804; P2 -> 801) and remove **no** wrong money side. The sense's sub-threshold money readings on the
   owner's lists are mostly right; the bank's OOF precision does not transfer to his kind of sentence.
2. **The wrong side is a rules problem, not a threshold problem:** the four P0 rows and the sure Arabic held-out row need a
   rule that reads who owes whom (plan Marhala 1, Agent A). A threshold that would stop a 0.997-sure opposite reading would
   also stop thousands of right ones.
3. Re-run this calibration after every retrain (lesson 165) and compare the OOF table, not only held-out: held-out is
   flattered (dev data) and hides the over-sureness.
4. Before any per-meaning gate is adopted, it needs (a) the same t under at least three ensembles, (b) a Wilson lower bound
   at or above the target (several hundred OOF rows per meaning: more bank sentences for to_get/to_give first), and (c) no
   loss on the owner's lists (`comm` of the miss lists, lesson 165).

## 4. Leakage: near-duplicates (`scripts/hudhud_sets.py --check`, report only)

The near scan folds each sentence (the leakage key, then {NAME}/{ITEM} and the test books' invented names and products in
their scripts masked, every number one token) and compares character 3-grams (Jaccard >= 0.80), only between different
families (train, heldout, owner, review, sealed). Pairs of sentences on 25 Nov 2026 (1.5 s):

| families | exact (the leakage key) | the same once masked | near (>= 0.80) |
|---|---|---|---|
| train x heldout | 0 | 5 | 89 |
| train x owner | 25 | 12 | 16 |
| train x review | 223 | 102 | 195 |
| heldout x owner | 0 | 0 | 1 |
| heldout x review | 6 | 13 | 18 |
| owner x review | 23 | 1 | 15 |

At Jaccard >= 0.90 instead: train x review 352, train x owner 38, owner x review 29, heldout x review 21, train x heldout 17.
The exact train overlaps are the 241 sentences already listed in `scripts/hudhud_sets_overlap.tsv` (pairs count a sentence
once per partner). Exit code unchanged unless `--near-strict` (which today would fail with 667 problems). `--sealed DIR`
reads the owner's sealed set v2 from outside the repository (refused inside it) and prints only counts and the sealed file's
line numbers, never a sealed sentence or its partner. Cross-script twins (Roman Urdu / Urdu / Hindi) are not caught by
letters; the calibration's folds keep them together by place instead.

## 5. Offline guard (`scripts/check_hudhud_offline.py`)

The hard-zero gate "a network call during a text answer = 0" as a code check over the engine closure (the 22 `data/Hudhud*.kt`
plus the 14 files of `scripts/shared_harness/deps.txt`: 36 files): no network package (java.net, javax.net, OkHttp, Ktor,
Retrofit, android.net, Firebase, Apache HTTP, javax.websocket, socket channels), no network type or call (URL(...),
HttpURLConnection, openConnection, sockets, InetAddress, HttpClient, WebSocket, ConnectivityManager, WebView, DownloadManager)
and no reference to a top-level name of the shared module's network files (CustomerLink, CustomerPage, JdkLines, LiveCloud,
LiveControl, LiveWire, Rfc3161, WarisCrypto, EcMath, WifiSync — found by their own network imports). Comments and strings are
blanked as `check_hudhud_readonly.py` does. The allow-list is empty. Wired into the Strings workflow (paths and step, next to
the read-only check) and `scripts/run_release_checks.sh`. It is a code check, not a run: it proves the closure names no
network, not that a phone made no call.

**Proof by deliberate breakage** (on the committed tree 06edd0f, one change at a time, each put back with `git checkout`,
the tree clean after; lesson 153):

| change | result |
|---|---|
| `import java.net.URL` added to `HudhudSense.kt` | FAILS: `HudhudSense.kt:4: java.net` |
| `private val offlineProbe = WifiSync::class` added to `HudhudSay.kt` (same package, no import) | FAILS: `HudhudSay.kt:111: WifiSync (a network file's, …/WifiSync.kt)` |
| `java.net.URL("https://example.invalid").openStream()` added to `Digits.kt` (a deps.txt file) | FAILS: `Digits.kt:175: java.net` and `openStream()` |
| a comment naming `java.net.URL` and `HttpURLConnection` added to `HudhudSay.kt` | passes (comments are not code) |
| put back | passes: 36 engine files, 10 network-file names watched |

## Files

New: `scripts/hudhud_calib_folds.py`, `scripts/hudhud_calibrate.sh`, `scripts/shared_harness/HudhudCalibrate.kt`,
`scripts/shared_harness/patch_gates.py`, `scripts/check_hudhud_offline.py`, `scripts/hudhud_model_card.py`,
`docs/hudhud-model-card-template.md`, this file. Changed: `scripts/hudhud_sets.py` (near scan, `--sealed`, `--near-list`,
`--near-strict`), `.github/workflows/strings.yml`, `scripts/run_release_checks.sh`.

# Hudhud's learners compared (plan v1, Marhala 5 item 2, 26 Nov 2026)

Measured, not shipped. REPORT ONLY: nothing under `shared/src/jvmSharedMain`, `sense.bin` (434,933 bytes, sha256
d79eab4b…6b5a), the bank or any threshold was edited. Importance 7. Base 5f87a67 (kmp-desktop-16), branch w-l. All figures
are counts of rows (lesson 144); every set here is dev data (the sealed set was not used, plan "Never" 9).

## The question and the rule

Does another learner, on the SAME features, read the owner's questions better than the shipped averaged-perceptron ensemble?
The plan's rule: a challenger replaces the perceptron only if it beats it **on the owner's two lists together (owner_set 200 +
owner_1000 1,000) by more than the seed noise (+12 rows)**, with the hard zeros at 0 (wrong money side, wrong amount, wrong
person, wrong product) and a file near 434,933 bytes. Else the perceptron stays.

## How it was measured

| Piece | File | What it does |
|---|---|---|
| Runner | `scripts/shared_harness/HudhudLearners.kt` | Builds every sentence's feature vector exactly as `HudhudSense.trainEnsemble` does (checked at start: the same 14,235 features, df and 65 meanings), learns each learner per fold, writes its weights into the app's own `HudhudSense.Model` (the same sixteen-bit file, no bias term) and reads with `HudhudSense.rank`, so every certainty is the one `decide` would compare. End to end, the whole-bank model is put in `sense.bin`'s place in memory (`HudhudSense.use`) and `HudhudBaseline.pass` reads every labelled set through `Hudhud.understand`; `decide` is not patched or touched. |
| Wrapper | `scripts/hudhud_learners.sh` | The calibration's family folds (`scripts/hudhud_calib_folds.py`, reused unchanged: 5 folds, 1,486 paraphrase families, 2,671-2,678 lines each), one compile behind `/tmp/harness.lock`, the run; heap 2.5 GB (`LEARNERS_HEAP`). Writes only `build/hudhud-learners` (git-ignored). |

```
scripts/hudhud_learners.sh --seeds 313,1000,2000 --lr-lambdas 3e-8,1e-7,3e-7 --svm-lambdas 3e-6,1e-5,3e-5 \
  --lr-epochs 40 --svm-epochs 40 --lr-eta 4 > learners.md        # ~12 min wall on 2 cores
```

**The learners** (pure Kotlin, a fixed `java.util.Random(seed)` shuffle, `StrictMath.exp`, lesson 106):

1. **Perceptron ensemble (baseline):** `trainEnsemble(seeds, prune 200)`, 15 seeds, 12 epochs — the shipped recipe. Seeds
   313..327 (= sense.bin), 1000..1014 and 2000..2014 (the calibration's two other ensembles) for the noise.
2. **Multinomial logistic regression:** softmax, L2, plain SGD with Bottou's step eta0/(1+eta0·lambda·t), eta0 = 4, 40 epochs,
   the mean of the epoch-end weights of the second half. Grid (seed 313, OOF, unquantised): eta0 0.5 / 4 / 16 x lambda 0 .. 1e-4;
   best eta0 4, lambda 3e-7 (lambda 3e-8 .. 3e-7 all within 3 rows; eta0 16 over-fits, 0.5 under-fits).
3. **Linear SVM, one meaning against the rest:** hinge, Pegasos steps, no bias, 40 epochs, mean of the epoch-end weights of
   the second half. Grid lambda 1e-7 .. 1e-4; best 1e-5.
4. **Character n-grams: not run as a separate learner, by the plan's own condition.** `HudhudSense.features` already holds
   them: every word of 4+ letters gives its three-letter pieces with word-boundary marks (`g:`, 6,092 of the 14,235 features),
   besides its sound skeleton in any script (`s:`, 1,126), the word (`w:`, 3,194), word pairs (`b:`, 3,741) and HudhudMind's
   concepts (`c:`, 82). Mean 23.0 features a sentence.

**Certainty.** The app reads softmax(scores / 0.1). A challenger's weights are multiplied by 0.1 / T so the app gives
softmax(scores / T): for LR T = 1 (its own probabilities); for the SVM T fitted by likelihood on its out-of-fold scores (0.287).
Because `decide`'s gates were measured on the perceptron's sharpness (lesson 165), each challenger is also run at a **matched
T** that gives the same share of out-of-fold rows at 0.95 or more as the perceptron (75.9%): LR 0.695-0.697, SVM 0.219. Both
T are fitted on the same OOF scores they are then judged on (one scalar; a small optimism, said here).

**Size.** Each challenger is pruned in whole-number steps (as `SHIPPED_PRUNE`) to the smallest cut whose file is no larger than
sense.bin: LR prune 177-178 (433,776-434,646 bytes; 1,752,639 unpruned), SVM 993-1000 (434,668-434,924; 1,431,130 unpruned).
The out-of-fold models are pruned at the same cut, so the OOF figures are those of the file that would ship.

## 1. Out of fold (5 family folds, 13,374 rows, read through the app's model)

| learner | seed | right | share | ECE (15 bins) | >= 0.95: share right of rows | >= 0.99 | 0.95-0.99 | money read on the opposite side (at >= 0.99) | learn s, 5 folds |
|---|---|---|---|---|---|---|---|---|---|
| perceptron x15 | 313..327 (shipped) | 11,646 | 87.1% | 0.0544 | 96.4% of 10,149 | 97.9% of 8,580 | 88.1% of 1,569 | 88 (14) | 17.7 |
| perceptron x15 | 1000..1014 | 11,650 | 87.1% | 0.0543 | 96.4% of 10,120 | 97.9% of 8,568 | 87.9% of 1,552 | 90 (15) | 16.2 |
| perceptron x15 | 2000..2014 | 11,642 | 87.0% | 0.0533 | 96.3% of 10,115 | 97.9% of 8,566 | 87.4% of 1,549 | 87 (12) | 16.1 |
| LR, own T 1 | 313 | 11,663 | 87.2% | **0.0164** | 98.3% of 8,183 | 99.4% of 5,256 | 96.3% of 2,927 | 86 (0) | 5.1 |
| LR, own T 1 | 1000 | 11,654 | 87.1% | 0.0157 | 98.2% of 8,187 | 99.4% of 5,268 | 96.2% of 2,919 | 88 (0) | 5.2 |
| LR, own T 1 | 2000 | 11,658 | 87.2% | 0.0155 | 98.3% of 8,184 | 99.4% of 5,264 | 96.2% of 2,920 | 88 (0) | 5.9 |
| LR, matched T 0.695 | 313 | 11,663 | 87.2% | 0.0543 | 96.2% of 10,136 | 98.1% of 8,510 | 86.5% of 1,626 | 86 (10) | 5.1 |
| LR, matched T 0.695 | 1000 | 11,654 | 87.1% | 0.0543 | 96.2% of 10,132 | 97.9% of 8,521 | 87.3% of 1,611 | 88 (10) | 5.2 |
| LR, matched T 0.697 | 2000 | 11,658 | 87.2% | 0.0540 | 96.3% of 10,130 | 98.0% of 8,513 | 87.2% of 1,617 | 88 (10) | 5.9 |
| SVM, own T 0.287 | 313 | 11,509 | 86.1% | 0.0277 | 97.9% of 8,096 | 99.0% of 5,537 | 95.6% of 2,559 | 80 (0) | 2.9 |
| SVM, own T 0.287 | 1000 | 11,496 | 86.0% | 0.0256 | 97.9% of 8,144 | 99.0% of 5,511 | 95.5% of 2,633 | 80 (0) | 2.7 |
| SVM, own T 0.287 | 2000 | 11,506 | 86.0% | 0.0267 | 97.9% of 8,132 | 99.1% of 5,509 | 95.5% of 2,623 | 76 (0) | 3.5 |
| SVM, matched T 0.219 | 313 / 1000 / 2000 | 11,509 / 11,496 / 11,506 | 86.0-86.1% | 0.051-0.053 | 96.1% of ~9,830 | 97.9-98.0% of ~8,230 | 86.6-86.9% | 76-80 (8-10) | as above |

(Temperature does not change which meaning is on top, so "right" is the same at both T.)

**Per language, out-of-fold right** (seed 313 / 313..327; ECE in brackets):

| learner | en /1479 | ur /1485 | ur-Latn /1469 | hi /1495 | bn /1493 | ar /1485 | fa /1494 | sd /1492 | id /1482 |
|---|---|---|---|---|---|---|---|---|---|
| perceptron x15 | 1,319 (0.048) | 1,312 (0.045) | 1,309 (0.039) | 1,324 (0.047) | 1,310 (0.062) | 1,196 (0.101) | 1,272 (0.061) | 1,304 (0.054) | 1,300 (0.060) |
| LR, own T | 1,321 (0.018) | 1,319 (0.022) | 1,316 (0.022) | 1,314 (0.031) | 1,313 (0.022) | 1,192 (0.051) | 1,277 (0.034) | 1,316 (0.024) | 1,295 (0.024) |
| SVM, own T | 1,306 (0.034) | 1,306 (0.030) | 1,294 (0.029) | 1,302 (0.033) | 1,286 (0.027) | 1,184 (0.053) | 1,244 (0.035) | 1,306 (0.038) | 1,281 (0.041) |

Spread over the three seeds of each learner, per language: at most 8 rows for each learner. Arabic stays the
weakest for every learner (80-81%); LR halves its calibration error (0.101 -> 0.049-0.051).

**Reading it.** Out of fold, LR **ties** the perceptron on accuracy (11,654-11,663 against 11,642-11,650: +4 to +21 rows of
13,374, about the perceptron's own seed spread of 8) and is far **better calibrated** (ECE 0.016 against 0.054; at 0.99 or more
it is right 99.4% and puts **no** money meaning on the opposite side, where the perceptron has 12-15). The SVM is 140 rows worse
(86.0%) and better calibrated than the perceptron, less than LR.

## 2. End to end (Hudhud.understand, decide unchanged; the whole-bank model in sense.bin's place)

| learner | seed | held-out strict /1170 | lenient /1170 | owner_set /200 | owner_1000 /1000 | **owner lists /1200** | review D /1115 | review O /1167 | wrong side (held/os/o1k/D/O) | wrong amount | wrong person | wrong product | owner rows lost / won vs sense.bin | file bytes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| perceptron x15 | 313..327 (sense.bin) | 1,080 | 1,096 | 159 | 812 | **971** | 1,046 | 1,074 | 0 (0/0/0/0/0) | 0 | 0 | 0 | 0 / 0 | 434,933 |
| perceptron x15 | 1000..1014 | 1,078 | 1,094 | 160 | 817 | **977** | 1,045 | 1,073 | 0 | 0 | 0 | 0 | 4 / 10 | 432,666 |
| perceptron x15 | 2000..2014 | 1,074 | 1,090 | 159 | 812 | **971** | 1,045 | 1,071 | **1** | 0 | 0 | 0 | 7 / 7 | 436,286 |
| LR, own T 1 | 313 | 1,052 | 1,069 | 157 | 816 | **973** | 1,035 | 1,061 | **2 (0/0/2/0/0)** | 0 | 0 | 0 | 11 / 13 | 433,778 |
| LR, own T 1 | 1000 | 1,055 | 1,072 | 158 | 819 | **977** | 1,036 | 1,059 | **2** | 0 | 0 | 0 | 11 / 17 | 434,194 |
| LR, own T 1 | 2000 | 1,053 | 1,071 | 157 | 819 | **976** | 1,036 | 1,058 | **2** | 0 | 0 | 0 | 11 / 16 | 434,646 |
| LR, matched T | 313 | 1,076 | 1,094 | 158 | 817 | **975** | 1,041 | 1,070 | **2 (0/0/2/0/0)** | 0 | 0 | 0 | 7 / 11 | 433,776 |
| LR, matched T | 1000 | 1,076 | 1,093 | 159 | 817 | **976** | 1,042 | 1,067 | **2** | 0 | 0 | 0 | 8 / 13 | 434,191 |
| LR, matched T | 2000 | 1,077 | 1,095 | 158 | 817 | **975** | 1,041 | 1,067 | **2** | 0 | 0 | 0 | 7 / 11 | 434,639 |
| SVM, own T 0.287 | 313 | 1,038 | 1,053 | 152 | 811 | **963** | 1,029 | 1,062 | **1 (0/0/0/1/0)** | 0 | 0 | 0 | 27 / 19 | 434,746 |
| SVM, own T 0.287 | 1000 | 1,039 | 1,054 | 152 | 813 | **965** | 1,029 | 1,061 | **1** | 0 | 0 | 0 | 24 / 18 | 434,924 |
| SVM, own T 0.287 | 2000 | 1,037 | 1,053 | 153 | 812 | **965** | 1,028 | 1,062 | **1** | 0 | 0 | 0 | 24 / 18 | 434,674 |
| SVM, matched T 0.219 | 313 | 1,070 | 1,086 | 152 | 811 | **963** | 1,035 | 1,065 | 0 | 0 | 0 | 0 | 21 / 13 | 434,743 |
| SVM, matched T 0.219 | 1000 | 1,067 | 1,083 | 154 | 811 | **965** | 1,035 | 1,066 | 0 | 0 | 0 | 0 | 17 / 11 | 434,921 |
| SVM, matched T 0.219 | 2000 | 1,064 | 1,080 | 153 | 811 | **964** | 1,035 | 1,067 | 0 | 0 | 0 | 0 | 19 / 12 | 434,668 |

The shipped row equals today's baseline (wrong side 0/0/0/0/0 since d470d9f, Marhala 1). One full pass takes 27-34 s. A second
run of LR and SVM at seed 313 gave every figure above again, to the row (determinism).

**Seed noise on the owner lists:** the perceptron's three ensembles read 971 / 977 / 971 (spread 6, inside the plan's +12).

**Against the rule:**

| challenger | owner lists vs 971 | beats it by more than +12? | hard zeros | size | verdict |
|---|---|---|---|---|---|
| LR, own T | +2 / +6 / +5 | no | **wrong side 2** (owner_1000) | 433,778-434,646 | fails |
| LR, matched T | +4 / +5 / +4 | no | **wrong side 2** (owner_1000) | same | fails |
| SVM, own T | -8 / -6 / -6 | no | **wrong side 1** (review D) | 434,674-434,924 | fails |
| SVM, matched T | -8 / -6 / -7 | no | 0 | same | fails |

## 3. The decision

**The averaged-perceptron ensemble stays.** No challenger clears the rule:

1. **Logistic regression is not better on the owner's lists** (+2 to +6 rows, inside the perceptron's own spread of 6 and far
   under +12) and **breaks a hard zero**: two owner_1000 rows read on the opposite money side at both temperatures and all three
   seeds. At its own T it also loses 25-28 held-out and 10-16 review rows, because its honest certainties pass fewer of
   `decide`'s gates (lesson 165: the gates were measured on the perceptron's over-sure scale); at the matched T those losses
   shrink to noise (held-out 1,076-1,077, review D/O within 4-7) but the owner-list gain does not grow.
2. **The SVM is worse** out of fold (-140 rows) and on the owner's lists (-6 to -8), and at its own T reads one review D row on
   the wrong side.
3. Size is not the obstacle: both challengers fit 434,933 bytes after pruning, with the same features and file format.

**What LR does better, and is worth keeping in view.** Out of fold it is as accurate and much better calibrated (ECE 0.016
against 0.054; Arabic 0.049-0.051 against 0.101), and at 0.99 or more it is right 99.4% of 5,256-5,268 rows with **no**
opposite-side money reading, where the perceptron is right 97.9% of 8,580 with 12-15. That is the calibration Marhala 3 asked
for, and it learns in about 1.5 s on the whole bank (the perceptron ensemble about 4 s). It does not show on the owner's lists
because `decide`'s gates and the rules around the sense were tuned on the perceptron, and today the rules, not the sense,
decide most of the owner's money readings.

## 4. What would be needed to ship a challenger (if a later round wants LR)

Not proposed now; written so the work is not done twice (lesson 128).

1. **Thresholds from its own table** (lesson 165): run the calibration (`scripts/hudhud_calibrate.sh`) with LR in place of
   `trainEnsemble`, re-derive every `decide` gate from LR's out-of-fold certainty-to-accuracy table, measure them on the owner's
   lists sentence by sentence (`comm` of the miss lists), not only by count.
2. **The two wrong-side rows** on owner_1000 must be read right by a rule first (Marhala 1's way: the side said by the
   sentence), then the wrong-side floor 0/0/0/0/0 must hold under three LR seeds.
3. **A recipe in `HudhudSense`:** `trainLR` beside `trainEnsemble` (deterministic SGD; StrictMath; the epoch-end mean), the
   prune for the size budget, `HudhudModelTest` held to it byte for byte, `HudhudRecipeTest`'s weight-constant lock extended,
   seed-safe and money-side tests learning LR with other seeds, all floors re-measured (lesson 163).
4. **The gain must then be shown on the owner's lists above +12** — nothing measured here suggests it would be; the realistic
   case for LR is calibration and the money side at high certainty, which is a Marhala 6/8 question (certainty per part,
   retrain only where a mistake is proven), not this one.

## Run times (2 cores, 7 GB, shared machine)

- Compile: about 50 s (queued behind the harness lock).
- Whole run (3 perceptron ensembles + LR lambda grid 3 + SVM grid 3, 3 seeds each, 2 temperatures, 15 end-to-end passes): about
  12 min wall (03:58-04:08). The earlier grids (eta0 x lambda, 40 epochs, seed 313, no end to end) took 2-3 min each.
- Learning, 5 folds: perceptron x15 16-18 s; LR 5-6 s; SVM 3-4 s. Whole bank: perceptron x15 3.6-4.2 s; LR 1.2-1.5 s; SVM 0.9 s.
- One end-to-end pass over the five sets (4,652 rows): 27-34 s.

## Files

New: `scripts/shared_harness/HudhudLearners.kt`, `scripts/hudhud_learners.sh`, this file. Nothing else changed.

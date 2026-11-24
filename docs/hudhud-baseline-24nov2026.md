# Hudhud baseline before any retrain (stage 8, H0), 24 Nov 2026

Counts, not feelings (lesson 144). Everything is read through `Hudhud.understand` with the shipped sense (`sense.bin`, seed 313),
invented names only. Reproduce: `scripts/hudhud_baseline.sh` (tables), `--hashes`, `--inventory`; it writes only `build/hudhud-baseline`.

**State measured:** repository commit 4855b08 plus the working tree of that moment, in which another helper (stage 7) had
`Hudhud.kt` (+3 lines) and `VoiceQuestion.kt` (about +10) modified and two new files (`HudhudSenseState.kt`, `HudhudReadFailure.kt`).
A clean-commit run was not possible without a git command that changes the tree (NOT TESTED). Two full passes, 10:28 and 10:41 (PKT),
gave identical shipped figures in every set, so those edits did not move anything measured here (VERIFIED).

## What these figures are, plainly
- **No clean accuracy figure exists yet.** The held-out set (1,170) is **dev data now**: it was used to find rules (money sides, off-topic
  and others), 6 of its sentences are also in the bank, and the sense is judged on it at every retrain.
- The owner's lists (200 + 1,000) and review sets D and O are also dev data: the rules were fixed class by class against them, and
  5 / 20 / 119 / 110 of their sentences are also bank sentences. Review sets' "right" means: the reviewer judged the reading OK when
  it was written and it still reads **exactly** so. It is a regression count, not accuracy (their MISS rows count as not right).
- The only accuracy figure the plan allows is the sealed set (audit folder, not frozen: it failed the leakage gate). Until then every
  number below is a **regression floor** or a **dev figure**, never "Hudhud is X% accurate".
- Realistic accuracy on the owner's own kind of questions is about 78-81% (owner lists); the 90-92% held-out figure is optimistic.

## 1. Inventory (counts; `scripts/hudhud_baseline_inventory.py`, reusing `hudhud_sets.py`)
| set | en | ur-Latn | ur | sd | ar | fa | hi | bn | id | total | unique |
|---|---|---|---|---|---|---|---|---|---|---|---|
| bank (train) | 1483 | 1469 | 1485 | 1492 | 1486 | 1496 | 1478 | 1467 | 1484 | 13340 | 13318 |
| heldout | 130 | 130 | 130 | 130 | 130 | 130 | 130 | 130 | 130 | 1170 | 1170 |
| owner_set | - | 200 | - | - | - | - | - | - | - | 200 | 197 |
| owner_1000 | - | 1000 | - | - | - | - | - | - | - | 1000 | 1000 |
| review_d | 130 | 322 | 108 | 86 | 94 | 94 | 101 | 90 | 90 | 1115 | 1104 |
| review_o | 153 | 308 | 161 | 69 | 74 | 72 | 145 | 78 | 107 | 1167 | 1167 |

Owner sets carry no language column (Roman Urdu and English, listed under ur-Latn). Overlap with the bank (frozen list
`scripts/hudhud_sets_overlap.tsv`, 260 in all): heldout 6 (0.5%), owner_set 5 (2.5%), owner_1000 20 (2.0%), review_d 119 (10.7%),
review_o 110 (9.4%). Duplicates inside owner/review sets: 11. Gate problems: 0.

## 2. Accuracy per set and language (right/total, shipped sense)
| set | en | ur-Latn | ur | sd | ar | fa | hi | bn | id | ALL |
|---|---|---|---|---|---|---|---|---|---|---|
| heldout strict | 121 | 113 | 118 | 119 | 117 | 117 | 122 | 113 | 119 | 1059/1170 90.5% |
| heldout lenient | 122 | 115 | 121 | 121 | 119 | 118 | 124 | 116 | 121 | 1077/1170 92.1% |
| owner_set | - | 157 | - | - | - | - | - | - | - | 157/200 78.5% |
| owner_1000 | - | 807 | - | - | - | - | - | - | - | 807/1000 80.7% |
| review_d held | 127/130 | 299/322 | 103/108 | 79/86 | 87/94 | 85/94 | 97/101 | 84/90 | 85/90 | 1046/1115 93.8% |
| review_o held | 143/153 | 292/308 | 130/161 | 69/69 | 69/74 | 71/72 | 123/145 | 78/78 | 99/107 | 1074/1167 92.0% |

Held-out cells are right of 130. Strict = the label read; lenient = HudhudSenseTest's `close` table (the test's floor is 88%).
Review D: 1,046 OK rows, all held, 69 not yet; review O: 1,074 held, 93 not yet. Weakest: held-out ur-Latn and bn (86.9%), review_o ur (80.7%).

## 3. Entity metrics (correct / wrong / choice / missing / otherwise; - = not labelled)
| set | side | amount | person | product | days |
|---|---|---|---|---|---|
| heldout | 122/0/0/4/0 | 36/0/0/0/0 | 162/0/0/0/0 | 90/0/0/0/0 | - |
| owner_set | 36/1/0/6/6 | - | 13/0/0/0/0 | - | - |
| owner_1000 | 123/3/0/28/66 | - | 58/0/0/0/0 | - | 24/0/0/8/8 |
| review_d | 207/0/0/0/0 | 19/0/0/0/0 | 167/0/0/0/0 | 83/0/0/4/0 | 4/0/0/0/0 |
| review_o | 122/0/0/0/0 | 7/0/0/0/0 | 130/0/0/0/16 | 100/0/0/26/0 | 3/0/0/0/0 |

Per language, all sets pooled:
| lang | side | amount | person | product | days |
|---|---|---|---|---|---|
| en | 48/0/0/0/0 | 9/0/0/0/0 | 63/0/0/0/2 | 44/0/0/0/0 | 2/0/0/0/0 |
| ur-Latn | 102/0/0/0/0 | 11/0/0/0/0 | 115/0/0/0/2 | 80/0/0/0/0 | 4/0/0/0/0 |
| ur | 56/0/0/0/0 | 5/0/0/0/0 | 45/0/0/0/2 | 16/0/0/17/0 | 1/0/0/0/0 |
| sd | 39/0/0/0/0 | 6/0/0/0/0 | 36/0/0/0/1 | 21/0/0/0/0 | - |
| ar | 36/0/0/1/0 | 7/0/0/0/0 | 35/0/0/0/1 | 23/0/0/0/0 | - |
| fa | 38/0/0/1/0 | 6/0/0/0/0 | 37/0/0/0/2 | 23/0/0/0/0 | - |
| hi | 49/0/0/0/0 | 6/0/0/0/0 | 43/0/0/0/2 | 16/0/0/13/0 | - |
| bn | 43/0/0/0/0 | 6/0/0/0/0 | 35/0/0/0/2 | 23/0/0/0/0 | - |
| id | 40/0/0/2/0 | 6/0/0/0/0 | 50/0/0/0/2 | 27/0/0/0/0 | - |
| owner | 159/4/0/34/72 | - | 71/0/0/0/0 | - | 24/0/0/8/8 |

Wrong side: 4 readings (1 in owner_set, 3 in owner_1000), none anywhere else; wrong amount: 0. Missing products
cluster in ur (17) and hi (13): a product written in Urdu/Hindi script for a Latin-saved product (the known class). The scoring is
`HudhudEntityMetricsTest.score` copied (its tally is private); if that test changes this copy is measured again.

## 4. Confusions, top 20 (owner lists + review OK rows: expected -> read; 236 misses, 197 distinct pairs)
Expected is the set's accepted list (cut here; the script prints all).
| n | expected (accepted readings) | read |
|---|---|---|
| 8 | Help:screen_seasons / Activity / ... | Figure:TO_GET |
| 5 | Today / Activity / ... | Status:BOOK |
| 4 | Figure:EXPIRING / Help:intro_expiry / ... | Status:LOW_STOCK |
| 3 | BackupStatus / Help:faq_backup / ... | Returns |
| 3 | Drawer / Cashbook / Today | Status:BOOK |
| 3 | Figure:PROFIT_MONTH / Period:PROFIT / ... | Figure:WHO_OWES_MOST |
| 3 | Figure:SALES / Period:SALES / ... | SpanLimit |
| 3 | Help:screen_seasons / Collected / ... | SpanLimit |
| 3 | Voice / Chat / Abilities / ... | About:OWNER_NAME |
| 2 | Cashbook / Help:intro_cashbook / Status:STAFF | Figure:WHO_OWES_MOST |
| 2 | Drawer / Help:intro_cashbook / Cashbook | Status:LOW_STOCK |
| 2 | Figure:SALES / Period:SALES / ... | Figure:PROFIT_MONTH |
| 2 | Figure:SALES / Period:SALES / ... | Help:screen_seasons |
| 2 | Figure:TO_GIVE / Status:BILLS | Figure:NOT_PAID |
| 2 | Figure:TO_GIVE / Status:BILLS | Figure:TO_GET |
| 2 | Figure:WHO_OWES_MOST / Figure:TO_GET / ... | Status:BOOK |
| 2 | Help:faq_language / About:LANGUAGES / ... | About:PRIVACY |
| 2 | Help:intro_documents / Count:INVOICES | Count:BILLS:null |
| 2 | Help:screen_cashflow | Figure:TO_GIVE |
| 2 | Help:screen_insights / Help:screen_sales_report / ... | Status:LOW_STOCK |

Held-out (labels), top 6 of 81 pairs,
111 strict misses: help_howto -> abilities 14; bye -> (none) 3; can_talk -> praise 3; abilities -> who_are_you 2;
act_jump -> act_fly 2; app_about -> maker 2. Most held-out "misses" are near readings that `close` grants.

## 5. Seed noise (sense learnt from the same bank with seeds 314-318, in memory only; `sense.bin` never written)
| seed | heldout strict | heldout lenient | owner_set | owner_1000 | review_d held | review_o held | wrong side | wrong amount |
|---|---|---|---|---|---|---|---|---|
| 313 (shipped) | 1059 | 1077 | 157 | 807 | 1046 | 1074 | 4 | 0 |
| 314 | 1060 | 1076 | 154 | 800 | 1022 | 1047 | 5 | 0 |
| 315 | 1062 | 1079 | 147 | 796 | 1026 | 1056 | 4 | 0 |
| 316 | 1054 | 1074 | 152 | 800 | 1021 | 1055 | 4 | 0 |
| 317 | 1050 | 1069 | 155 | 801 | 1025 | 1057 | 6 | 0 |
| 318 | 1058 | 1074 | 146 | 795 | 1029 | 1058 | 6 | 0 |
| spread | 12 | 10 | 11 | 12 | 25 | 27 | 2 | 0 |

Read: the shipped seed is the **best or near best on every regression set** (all four floor sets: 157, 807, 1046, 1074) because
the floors were measured at it; another shuffle of the same bank loses 2-12 owner-list readings and 16-27 held review rows (lesson
163). Held-out strict is the only figure that does not favour 313 (1050-1062): about +-6 sentences (+-0.5 point) is shuffle noise,
so any retrain effect below that on the held-out set means nothing. Per language, held-out strict moves up to 9 of 130 (ur-Latn
113-122, fa 113-120, ar 112-117): never read a per-language change under 10 sentences as real. Money sides: 4-6 wrong, amounts 0
under every seed.

## 6. Reading speed (ms per `understand` call, JIT warm after 600 questions, 2 processors shared with other helpers)
Two full passes of 4,652 questions (median / p95 / max ms): 10:28, load 1.5: 7.15 / 15.72 / 136.5; 10:41, load 2.2: 10.07 / 24.69 / 332.9.
Per set (10:41): heldout 15.0 / 27.6; owner_set 15.5 / 37.7; owner_1000 10.4 / 26.8; review_d 8.4 / 21.4; review_o 6.7 / 17.5
(median / p95). Timings on a shared machine move with the load: read them as "about 7-10 ms median, under 40 ms p95"
(INFERRED for a phone: not measured there, a phone is slower; NOT TESTED).

## 7. SHA-256 (`scripts/hudhud_baseline.sh --hashes`)
```
sense.bin  63f3efb4ac33d4f60a79a4eb3dbee97f2394f968e110fed9d241aa771621c635
1st/ar.tsv e8ce2d4d586e   1st/bn.tsv f90ac30df1a9   1st/en.tsv 2f9894c7c840   1st/fa.tsv 3215e2c7baff
1st/hi.tsv 72b5025b412d   1st/id.tsv f3fc5015f9f7   1st/sd.tsv 1f8313c8d1ed   1st/ur-Latn.tsv 108e595495f2
1st/ur.tsv 740dfc5e0410   more/ar.tsv 983d5a40138a   more/bn.tsv a9fd90558b76   more/en.tsv bfa90e842872
more/fa.tsv 4b19e8633e73   more/hi.tsv ee640ce54693   more/id.tsv fad973ed763c   more/sd.tsv 1f34f43b9ef8
more/ur-Latn.tsv 8c35a697677b   more/ur.tsv ad6ec8a48b68   bank.tsv ecdfc31599cf   heldout.tsv 907a9e7f51a5
owner_1000.tsv 08de74dc6998   owner_set.tsv 3862e89bb2a9   pages.tsv b9ed16367517   review_d.tsv 17c83427ebd8
review_o.tsv dc52c4d76fff
```
(12-digit prefixes; `--hashes` prints all 64. 1st/ = `shared/hudhud/<lang>.tsv`, more/ = `shared/hudhud/more/`, the rest `shared/src/jvmSharedTest/resources/hudhud/`. bank.tsv has 13,340 lines.)

## 8. Which figures are regression floors, and what is not done
Floors in the tests today (never lower, raise with every improvement): owner short 157 (`HudhudOwnerSetTest`), owner 1,000: 807,
held-out 88% lenient (`HudhudSenseTest`; measured 92.1%), review D at least 865 held and none changed (measured 1,046), review O at
least 1,074 held (measured 1,074), entity wrong-side at most 0 / 1 / 3 / 0 / 0 (held-out, owner_set, owner_1000, D, O) and wrong amount 0.
Per-language figures, the confusions and the seed spread have **no floor**: they are this baseline, to compare against after the retrain.

Not done: the sealed set (no clean figure); a measurement on a phone (speed, memory); a clean-commit pass (stage 7 was editing);
other-seed detail per row (only counts are kept); owner-set entity labels for spelled-out amounts (never labelled);
`HudhudWordsTest` (a desktop test, not in the harness).

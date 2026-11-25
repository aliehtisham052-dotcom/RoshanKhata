# Hudhud training master plan v1 (25 Nov 2026)

The owner's full plan is the Claude Doc "Hudhud Training Master Plan v1" (Roman Urdu, 25 Nov 2026). This file is its
working summary in the repository, so a session can start from the repository alone. The decisions are in
`docs/decisions-25nov2026.md` (D1–D21). Outside AI tools get only the name-free context pack
(`ledger-assistant-pack.zip`, kept by the owner), never this file.

## The decision

Hudhud stays offline, with rules and the learnt sense, and no language model in the app (D4). The gains now come from
the owner's real use and from safety, not from more synthetic data:

- 14–15 Nov, the owner's own sentences and class-wide rules: owner 1,000 list 686 → 797 (+111 in two days).
- 17–25 Nov, new data and a 15-model ensemble: 807 → 812 (+5 in eight days), inside the seed noise (about ±12 rows
  on the owner's lists, ±6 on held-out; `docs/hudhud-baseline-24nov2026.md`).
- The wrong money side still reaches 4 sentences on the owner's lists; in a ledger it must be 0.

## Never (changed only by a new written decision of the owner)

1. A language model inside the app while D4 stands.
2. Hudhud making a figure itself: every figure comes from its Formula Book function.
3. Hudhud saving, deleting or sending anything (D7); look settings only, with Undo (D3).
4. A real customer's name, number or a shop's name in data, tests or the repository (lesson 80, BC-307).
5. Training on the owner's own book: from the check log only the class of a mistake is taken, names always invented.
6. A tax rate or legal rule without its source and date.
7. A pesticide dose, mixing or pre-harvest interval, or medical advice.
8. Religious rulings: a disputed Zakat or loan question goes to a qualified Hanafi scholar.
9. Fixing anything by looking at the sealed set.
10. A retrain without two more seeds (lesson 163); a changed model shape gets new thresholds (lesson 165).
11. Hudhud's or the app's name given to an outside AI.

## Hard-zero gates (any one fails, the release stops)

| Gate | At b86f257 | At release | How measured |
|---|---|---|---|
| Wrong money side | up to 4 (floors 0/1/3/0/0) | 0 | entity metrics, money-side pairs, sealed set |
| Wrong amount | 0 | 0 | entity metrics, sealed set |
| Answer about the wrong person or product | 0 on held-out | 0 | entity metrics, name tests |
| The owner's figure shown to a cashier | tests exist | 0 | HudhudRolesTable, cashier golden answers |
| A write by Hudhud itself | 0 | 0 | `scripts/check_hudhud_readonly.py` |
| A private note spoken at the counter | by D8 | 0 | voice tests |
| A network call during a text answer | no test yet | 0 | new test (Marhala 3) |

A zero on 500 sentences is not a proof of never: by the rule of three the true rate can still be up to about 0.6%
(3 / 500). The model card says "0 in 500", never "never wrong".

## Floors (never lower; raised with every improvement)

| Set | Floor at b86f257 |
|---|---|
| Owner short list | 159 / 200 |
| Owner 1,000 list | 812 / 1,000 |
| Held-out (lenient) | 88% (measured 93.8% strict; dev data now, not "accuracy") |
| Review D / O | rows already right keep their reading; two Sindhi rows to decide |
| HudhudWordsTest | 9 / 9 |
| Wrong side (held-out / short / 1,000 / D / O) | 0 / 1 / 3 / 0 / 0, target 0 / 0 / 0 / 0 / 0 |

## The training cycle (every time, ten steps)

1. Collect mistakes: the check log, the owner's lists, native reads.
2. Make the class: the root cause and every sentence that cause breaks (bug-class register).
3. Label by `docs/hudhud-annotation-guide.md`: the money's side as the sentence says it; names invented.
4. Split: all forms of one sentence in one set; `scripts/hudhud_sets.py --check` for leakage.
5. Rule first: what must never move (money side, writes) goes into the rules.
6. Then the sense: bank sentences, the ensemble retrained; CI rebuilds it byte for byte.
7. Two more seeds (lesson 163), so data and noise are told apart.
8. The certainty table and thresholds again (lesson 165).
9. Gates: hard zeros, floors, and the old and new miss lists compared sentence by sentence.
10. Ship or go back: a model card, sense.bin's hash and a rollback; a failure goes back to step 2.

No retrain when the gain is inside the noise (±6 held-out, ±12 the owner's lists).

## Phases

| # | Phase | When | Importance | Exit gate |
|---|---|---|---|---|
| 0 | Decisions and the base | 26–27 Nov | 10 | D15–D21 written; baseline at b86f257; Formula Book line fixed |
| 1 | Money first (wrong side 4 → 0) | 27 Nov – 3 Dec | 10 | wrong-side floors 0/0/0/0/0; wrong amount 0; no regression; every new class in the register with its test |
| 2 | Real use = real training data (Oppo Reno 11F, B5, B6, H7) | 27 Nov – 10 Dec | 9 | every check-log mark has a class and a test; phone speed table (p50/p95/p99, start, memory); voice measured apart |
| 3 | Clean exam: sealed set v2 and calibration (D18) | 4–20 Dec | 10 | exact leakage 0, near-duplicates clean; SHA-256 written; calibration table; thresholds again; network-call test |
| 4 | Release gate (H8 + C3) | 21–27 Dec | 10 | all hard zeros 0; model card; D17 "beta"; Android/Windows parity; the owner's yes |
| 5 | Result and the model decision (H9) | Jan 2027, weeks 1–2 | 7 | learners compared (logistic regression, linear SVM, char n-grams; pure Kotlin, size near 434,933 bytes); targets set per language |
| 6 | The map: 65 labels, certainty per part, NO_DATA vs 0 | Jan wk 3 – Feb wk 1 | 7 | no orphan label; NO_DATA tests in nine languages; adapter around Hudhud.kt, no big refactor |
| 7 | Conversation: memory, "no, the other Ali", follow-ups | Feb 2027 | 8 | stale-memory mistakes 0; correction tests in nine languages |
| 8 | Data v3 and retrain, only where a mistake is proven | Feb–Mar 2027 | 8 | data manifest v3; gain above noise; hard zeros 0 |
| 9 | Business knowledge (lane B, D16) | Mar 2027 | 7 | every atom reviewed with source and date; new formulas (margin, markup, break-even, F65) with golden tests; legal and medical refusals 100% |
| 10 | Advice (lane C) | Mar–Apr 2027 | 6 | every advice with its formula id and test; no label or decision on a person (D8, D19) |
| 11 | Languages and voice | from Dec 2026, ongoing | 7 | a floor per language; native sign-off or "beta" (D17) |
| 12 | Governance | with every phase | 8 | model card, manifest, rights register and decision log at every release |

Coordinator outcomes (Marhala 6): ACCEPT, CLARIFY (D15), SAFE_GUESS (D1), REFUSE, DRAFT (D7), NO_DATA ("no record",
never "Rs 0"), ERROR (no guess).

## "Hudhud Pro" only when all ten hold

Hard zeros 0 on sealed and regression sets; a published sealed figure per language with a sealed set and its targets
met; follow-up and correction tests pass with stale-memory mistakes 0; every figure has a Formula Book id; every
knowledge answer has a source and date, and legal, medical and pesticide-dose questions are refused 100%; every advice
rests on facts, with no label or decision on a person; the phone speed limit from Marhala 2 is met; native sign-off
for Sindhi, Bengali, Farsi and Arabic, or they stay "beta"; a model card and rollback for every release; network
calls during a text answer 0.

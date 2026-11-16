# Hudhud's sentence sets: how to label a sentence

One page for whoever writes or checks Hudhud's labelled sentences (the bank in `shared/hudhud/`, the owner's lists and the
review sets in `shared/src/jvmSharedTest/resources/hudhud/`). `python3 scripts/hudhud_sets.py --check` holds every set to the
shape below; it cannot tell whether a label is the *right* one — that is this guide's job.

## The rule above all

**Label what a shopkeeper means, as the app can answer it today — never what makes a test pass.** A sentence is never
rewritten, and a label never chosen, to fit what Hudhud reads now (lesson 111). If no reading fits, the sentence is a miss
(`MISS`), not a mislabel.

## 1. Names, numbers, products: invented only

- Names: the invented book only (Ali Raza, Bashir Ahmad, Tariq Butt, …), or `{NAME}` / `{ITEM}` in the bank.
- Phone numbers: only 0300 1234567, 0300 7654321 or 0300 000000x. The gate fails on anything else.
- **Nothing from the owner's photos, check log or real book** — not a name, a number, a nickname or a village (lesson 80).
  Take the *class* of a sentence from his log ("a question word read as a name"), never the row.

## 2. The money side

Write the side the sentence **states**, never the one you expect.

| The sentence says | Label | Misaal |
|---|---|---|
| others owe the shop | `Figure:TO_GET` | "mujhe kitne paise lene hain", "market se kitna wusool karna hai" |
| the shop owes others | `Figure:TO_GIVE` | "mujhe kitna dena hai", "supplier ke kitne dene hain" |
| a named person's balance | `Parties` / `Person:…` | "Ali Raza ko kitna dena hai" (his account, not the shop's total) |
| money came in | `Collected` | "aaj kitni wusooli hui" |
| a fact to write: shop gave | `Entry` (gave) | "Ali ko 500 diye" |
| a fact to write: customer gave | `Entry` (got) | "Ali ne 500 diye" — *ne* marks the doer: the customer paid |

- "Lena" (take) is TO_GET, "dena" (give) is TO_GIVE **from the shop's side**; "X ne … dene hain" is X owing, not the shop.
- A bare word of debt with no side ("qarz kitna hai") has no stated side: label what a shopkeeper would mean and tag the row
  `side_unstated` in your notes; it must never be used as a money-direction test.
- Qarz-e-Hasna (an interest-free loan) is `Qarz`, never a sale and never TO_GET by itself.
- Write direction pairs together ("kitna lena hai" / "kitna dena hai") so a flip is caught both ways.

## 3. "kal"

In Roman Urdu and Hindi, *kal* is both yesterday and tomorrow; in Urdu script کل is also *kul* (total).

- Past verb ("kal kitna bika", "kal ki sale kitni thi") → yesterday (`…YESTERDAY`).
- Future verb ("kal kaun dega") → tomorrow, a day the book does not hold yet: label the ask with no window.
- Urdu script: yesterday only as "کل کی / کل کا / کل کے" or "گزشتہ روز"; a lone کل beside a figure is "total".
- No verb and no other clue ("kal ka hisaab") → a guess: label the most likely reading and mark the row `GUESS`.

## 4. Refusals

- Off the book (weather, cricket, a joke, a capital city) → `OffTopic`. A sum written with signs or words ("500 aur 300 ka
  total") → `OffTopic` with the sum (Hudhud opens the calculator).
- "Can the app do X?" for a feature the app lacks, or an "agar … to kya hoga" with nothing of the book named → `Unknown`.
  Only if the app has the feature is the answer its help page (`Help:<page>`).
- A write, delete or change order ("khata delete kar do", "PIN badal do") → the help page of that screen; Hudhud never writes.
- A cashier asking for an owner's figure: label the ask; the gate (who may see it) is the answer's business, not the label's.

## 5. Follow-ups that carry a person

A sentence that makes sense only after another goes with that sentence in the **before** column (review sets) or `context`
(JSONL). Label it as it reads *after* that sentence: after "Ali Raza ka hisaab", "aur number?" is `Person:PHONE` for Ali.
Without its before-sentence the same words are `NeedName`. Never write a follow-up without its context.

## 6. Mixed script

Write it as people type it: "Ali Raza کا نمبر دو", "موجودی Urea چقدر است". The **language** is the language of the sentence's
grammar (here `ur`, `fa`), not of the name. A sentence of a non-Latin language needs at least one letter of its own script
(the gate checks). Indonesian is `id` (the review sets also write `in`, Android's folder name; both mean the same).

## 7. What counts as a guess

- `OK` — read as a shopkeeper would count right.
- `GUESS` — Hudhud answers it "Taken as: …"; right only if that reading is what was meant.
- `MISS` — wrong or "I can't answer this yet" where an answer exists.
- `NOW` — answered "as it stands now" (`Ask.Now`) instead of over the span asked: not yet right.

A guess shown and right is still a guess: do not mark it `OK`.

## 8. Before you add a row

1. The sentence is not already in the bank (the gate fails on leakage: a measured sentence must never be learnt).
2. Every accepted answer is a reading Hudhud can make: `Kind` or `Kind:VALUE` as `HudhudShown` spells it; a help page that exists.
3. Sindhi, Bengali, Farsi and Arabic rows get a native read before they are frozen.
4. Run `python3 scripts/hudhud_sets.py --check`.

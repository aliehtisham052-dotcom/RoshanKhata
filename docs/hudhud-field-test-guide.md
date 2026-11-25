# Hudhud field test (Marhala 2): the check log and the speed table

Marhala 2 of `docs/hudhud-master-plan-v1.md`: for 7 days the owner asks Hudhud about 20 real questions a day on his
phone (Oppo Reno 11F) and marks every wrong answer. Claude reads the marks **by class**, never by copying a row.

## 1. Marking answers (the owner)

1. More → **Hudhud's voice** → **Testing tools** → turn on **Mark every answer right or wrong**.
2. Ask Hudhud as on any day, by voice or by typing.
3. Under each answer: **Was this answer right?** → **Right** or **Wrong**. A second tap changes the mark.
4. The count line under the switch says how many were asked, right, wrong, and the % right.

Not written: answers about matters of faith and Hudhud's own story (personal things stay personal). The list keeps the
newest 3,000 answers, one file for every shop on the phone (each row says which shop); deleting a shop deletes its rows.

Optional: if the owner opens the shared file in a spreadsheet and adds a column headed `expected` with what he meant
("Ali ka baqaya", "aaj ki sale"), the triage groups by it too. Nothing else needs to be added.

## 2. Sending the list (the owner)

Testing tools → **Share the list** → pick where to send it (for example WhatsApp "Message yourself", then into the chat
with Claude). The file is `hudhud_check_<date>_<time>.tsv`. The copy made for sharing is deleted from the phone after
10 minutes; the list itself stays until **Clear the list**. Clear it only after the shared file has arrived.

## 3. The privacy rule

The list holds real customers' names, amounts and what the recogniser heard. Therefore (lessons 80 and 120, plan
"Never" 4 and 5):

- The file never enters the repository, a commit message, a GitHub issue or a public page, and is never given to any
  other AI or person. It lives only in a private folder outside the repository.
- What enters the repository is the **class** of a mistake ("a question word read as a name", "a polite wrapper"),
  written again with **invented** names and the invented phone family (0300 1234567), as
  `docs/hudhud-annotation-guide.md` says. Never a row, a name, a nickname, a village or an amount from the list.

## 4. The triage (Claude)

```
python3 scripts/hudhud_checklog.py /private/hudhud_check_20261127_2100.tsv --out /private/triage
```

- It refuses an output folder, or an input file, inside the repository.
- It prints counts only: answers read, right, wrong, not marked, rows needing a look, classes.
- `<name>.rows.tsv`: each **wrong** row (`--all-marked` adds the right ones): row number, the app's language, the
  script and language guessed from the question, what Hudhud read (`read_as`, the book's ids removed), and the
  question and card with names → `{NAME1}`/`{ITEM1}`, phone numbers → `{PHONE}`, numbers of 5+ digits → `{NUM}`,
  the shop → `{SHOP}`, any word Hudhud's sentences never use → `{X1}`.
- `<name>.classes.tsv`: rows grouped by (expected, what Hudhud read), largest first, with row numbers and one masked
  example.
- `needs_look`: a masked word may be a name, a product, a typo or a recogniser's garble; or the card named somebody the
  question did not. Claude opens that row number in the owner's own file, decides, and writes nothing from it.

Then, class by class: the root cause, every sentence the same cause breaks, a row in `docs/bug-classes.tsv`, a test
with invented sentences, the fix, and the training cycle's gates (plan, ten steps). Every wrong mark ends in a class
and a test (Marhala 2's exit gate). The masking is a safety net, not proof: a name that is also an everyday word
("Barkat", "Qeemat") stays unmasked unless the card named it, so the rows file is private too.

Voice is measured apart: the log's question is what the recogniser **heard**, and the log does not yet say whether a
question was spoken, typed or a chip tap, nor what was meant. Until the app records that, a voice miss is told from a
reading miss by the owner's `expected` column or by asking him.

## 5. The speed table

`HudhudSpeedOnDeviceTest` times the call Hudhud's sheet makes for every question (`Hudhud.understand`: rules and the
learnt sense) over 293 held-out sentences in nine languages, against a small book and a shop-sized book of 1,000
invented customers. The on-device workflow shows it as the **"Hudhud speed"** annotation:

| Line | Meaning |
|---|---|
| first line | which Android ran it. **EMULATOR figures are not the owner's phone** — they show change from build to build |
| model load | reading and decoding `sense.bin`, once per app start (the first question waits for it) |
| first question | the first reading after the load, before the code is warm |
| p50 | half of the questions were read faster than this |
| p95 / p99 | 95 / 99 of every 100 were faster: the slow end the owner feels now and then |
| max | the single slowest |
| memory | the app's Java heap at its highest during the run, and its limit |

It never fails for being slow, only for an error (a question that throws, or the sense not loading).

The owner's own figures: the same test on his phone over USB, run by Claude only with his yes, with
`-PsideBySide=true` (the TEST app's package, never the Play app's), after the check log has been shared — Gradle
removes the app it tested afterwards, and its data with it. Then `adb logcat -d -s HudhudSpeed:I`.

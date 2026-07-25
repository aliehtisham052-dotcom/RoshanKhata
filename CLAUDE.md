# Roshan Khata — project instructions

A Kotlin-native Android ledger for a Pakistani shopkeeper, specifically an
agri-chemical dealer (pesticides, seeds, feed). Brand: Innovation-313.

No account, no server, no ads. Everything stays on the phone.

> This repository is **public**. Never commit tokens, keystores, API keys,
> customer data, phone numbers or personal email addresses to it — including
> into this file.

---

## Shape of the project

- ~70 Kotlin files, ~13,400 lines
- 634 string keys × 6 locales — English, Arabic, Sindhi, Farsi, Urdu, Roman Urdu
- Room database, version 9
- CI builds a debug APK on every push to `main` and publishes it to the
  rolling `latest` release

### Key files

```
app/src/main/java/com/innovation313/roshankhata/
  MainActivity.kt           Home — 13 coloured tiles, walkthrough
  KhataActivity.kt          Ledger list — search, voice entry, filters
  PartyDetailActivity.kt    One customer — two-step full-screen entry dialog
  EntryDetailActivity.kt    Receipt + edit
  BusinessCardActivity.kt   Card picker
  BusinessSettingsActivity  QR, signature, photo-on-statement switch
  RecycleBinActivity.kt     Bin + bulk delete
  data/
    VoiceEntry.kt           Spoken sentence -> party, amount, direction
    BusinessProfile.kt      QR, signature, photo-on-statement setting
    PdfExport.kt            Statement PDF — signature, optional photo
    DriveFeature.kt         ENABLED = false — the one line to flip
    KhataDatabase.kt        version = 9
  ui/
    CardTemplates.kt        12 business card designs
    NameSearch.kt           One search rule for every screen
    Calc.kt                 evalPad, resolvePercent, trim
    DateRangeFilter.kt      Shared date filter
    DateTimeField.kt        Shared date + time picker

app/src/test/java/.../data/VoiceEntryTest.kt   What the microphone may conclude
```

---

## Verifying a change

CI runs `testDebugUnitTest` before it packages anything. A build that
compiles is not a build that works.

```bash
# Unit tests
gradle testDebugUnitTest

# Every locale has every key, and no duplicates
python3 -c "
import re
base = set(re.findall(r'name=\"([^\"]+)\"', open('app/src/main/res/values/strings.xml').read()))
for loc in ['ar','sd','fa','ur','b+ur+Latn']:
    o = set(re.findall(r'name=\"([^\"]+)\"', open(f'app/src/main/res/values-{loc}/strings.xml').read()))
    print(loc, len(o), 'complete' if o==base else 'DIFF')"

# Every XML parses, every constrained id exists
python3 -c "
import re, glob, xml.etree.ElementTree as ET
for f in glob.glob('app/src/main/res/**/*.xml', recursive=True):
    ET.parse(f)
    src = open(f).read()
    d = set(re.findall(r'android:id=\"@\+id/(\w+)\"', src))
    u = set(re.findall(r'app:layout_constraint\w+=\"@id/(\w+)\"', src))
    if u - d: print('DANGLING', f, u - d)"
```

When a build does fail, read the **check-run annotations through the GitHub
API** rather than guessing. Log blobs are often unavailable; annotations come
back reliably. The workflow emits one per error line for exactly this reason.

A build takes roughly four minutes.

---

## Conventions, learned the hard way

Each of these cost a build or a bug.

1. **Verify before asserting.** A claim that ML Kit read handwritten Urdu at
   "20-30%" was invented; it does not support Arabic script at all. The owner
   made decisions on that number. Search first, then answer.
2. **On-device Urdu OCR is not possible today.** ML Kit covers Latin, Chinese,
   Devanagari, Japanese and Korean only. Do not re-attempt.
3. `inflate(layout, null)` discards every `layout_*` attribute — set height
   and margin in code.
4. `selectedItemId` fires its listener — set it before attaching one.
5. `MaterialButton` ignores `android:background` — use `backgroundTint`.
6. The table is `transactions`, not `entries`.
7. **Check for an existing string key before adding one.** Duplicates break
   the resource merger, and prefixes collide: `tpl_classic` matched
   `biz_tpl_classic` and silently skipped a whole write.
8. `Calc.eval()` rejects `%` and parentheses — handle it in the caller.
9. **Validate every anchor before writing any of them.** A script that applied
   two of three edits and exited left a layout the next script corrupted.
10. A disabled Material button fades its own colours — a faded label on a
    near-black fill is invisible.
11. **Removing a view breaks whatever was constrained to it.** Check
    `layout_constraint*="@id/..."` after any deletion.
12. Quotes in a commit message break the shell. Use `git commit -F file`.
13. **Colour is not decoration.** Text on the dark section headers needs its
    own `*_on_dark` variant; the ledger's plain red and green measure barely
    above 1:1 there. Anything carrying a figure must clear 4.5:1.

---

## Working with the owner

- **Reply in Roman Urdu** unless English is asked for.
- **Confirmation first.** Present the plan, wait for approval, then build.
  Push only after an explicit yes.
- **Never hide facts**, good or bad. No false guarantees. Say plainly what was
  verified and what was not — in particular, code checked by reasoning is not
  code tested on a real device.
- **Root-cause debugging.** Find the real reason; do not guess.
- **Ask before spending** any paid credits.
- **Flag privacy concerns unprompted**, and Shariah concerns too — while
  noting you are not a mufti and pointing to a qualified scholar.
- **Professional standard.** No rough or quick fixes.
- Content and store copy are planned bilingually: Urdu for local trust,
  English for reach.

---

## Where the project stands

**Play Store publishing is the blocker.** Nothing else matters as much.
Screenshots, bilingual listing, content rating, data safety form (easy and
strong — no data is collected), feature graphic, signed AAB.

Use dummy data for store screenshots. Real customer names and numbers must not
appear in a public listing.

**Google Drive backup** is written and hidden behind `DriveFeature.ENABLED`.
It waits on Google's OAuth verification for the Drive app-data scope, which
needs a homepage, domain ownership proved in Search Console, and a video of
the consent flow. The scope is sensitive rather than restricted, so no paid
security audit applies. Once verified, flip the constant — nothing else.

**Data import from other ledger apps** is agreed but unbuilt. Balance-only
first: name, phone, current balance as one opening entry.

Smaller and deferred: merging duplicate parties, cashbook and cheque
`createdAt` editing, a WhatsApp number on the Help screen.

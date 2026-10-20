# Roshan Khata — project instructions

A Kotlin-native Android ledger for a Pakistani shopkeeper, specifically an
agri-chemical dealer (pesticides, seeds, feed). Brand: Innovation-313.

No account, no server, no ads. Everything stays on the phone.

> This repository is **public**. Never commit tokens, keystores, API keys,
> customer data, phone numbers or personal email addresses to it — including
> into this file.

---

## Shape of the project

- Three modules (Kotlin Multiplatform since 18 Oct 2026):
  - `:app` — the Android app, ~194 Kotlin files, ~47,000 lines
  - `:shared` — everything that does not need Android, ~14,500 lines with
    `:desktop`: sums, voice parsing, search, dates, the Room database, the
    backup format, the shared Compose screens. Android and Windows both run it.
  - `:desktop` — Roshan Khata for Windows (Compose Desktop, MSI)
- 2,038 strings × 9 locales — English, Urdu, Roman Urdu (`b+ur+Latn`), Sindhi,
  Arabic, Farsi, Hindi, Bengali, Indonesian (`in`)
- Room database, version 31 (schema checked in at `app/schemas`)
- CI builds the **dev** APK on every push to `main` and publishes it to the
  rolling `latest` release: the release code with R8 and resource shrinking,
  ARM only, package `.dev` ("Roshan Khata TEST"). The on-device workflow
  monkeys the same build on the emulator (x86_64 kept with
  `-PdevAllAbis=true`) and checks in the APK that the trade goods lists and
  all nine languages survived the shrinker. `kmp-*` branches build too; their
  APK goes to the `kmp-test` pre-release, never `latest`.
- `desktop.yml` builds the Windows MSI on GitHub's Windows runner and puts it
  on the `desktop-test` pre-release (not signed yet).
- The Windows app (`desktop/src/main/kotlin/.../desktop/`) opens maximised on
  **Home** (1.8.0, 20 Oct redesign, `HomePane.kt`): the phone's Home made wide —
  Net balance card with the eye, get/give, late payers (MainActivity.observeLate's
  rule), today's chips, the four slides, the three tile grids in the phone's
  order. The header (`Shell.kt`, `AppHeader`) is the phone's: shop button,
  Back up now, From phone, bell, More (Data / Display / Help groups, with a
  Theme choice kept in app.properties), and one green Add Party. The rail is
  grouped as the phone's Home (Every day, Business, Tools) with the computer's
  own pages folded under More; colours beyond the shared palette live in
  `Look` (Panes.kt) and dark is read from the theme (`isDark()`), never only
  from Windows. Invoices are list + page: a click shows the invoice in its own
  design beside the list. Khata has the phone's tabs (All / Customers /
  Suppliers), side pills, its five orders and a several-at-once delete
  (`KhataControls.kt`, KhataActivity.rank's rule); the open customer has the
  WhatsApp reminder; More → Text size has the phone's four steps (a font
  scale over Windows' own). The bell and Home's chips come from
  `LedgerAlerts` (:shared — cheques, plans, bills, expiry, cash gap; the
  phone's HomeAlerts calls it and adds its backup and update rows around it);
  the backup chip shows the newest daily copy's time; the open customer shows
  the promised day and season record and has the phone's entry search and
  newest/oldest order; Ctrl+F jumps to the Khata search. Khata also has the
  phone's date filter (today, yesterday, this week, this month — by last
  dealing); invoices have the due date and the phone's Invoice settings
  switches (kept in app.properties; a field already filled always shows);
  a nameless shop is asked its name on opening (ShopNameActivity's question). The shop's trade
  (`TradeKit.kt`) is required on Windows (the owner, 20 Oct): a book with none
  gets the phone's 25-card picker before anything else (More → Shop type to
  change it); `LocalTrade` gives every form the phone's TradeVocab words from
  strings.xml — item and unit hints, starter goods and unit pills, product
  types — and TradeFeatures' switches: Seasons only for agri, Stock register
  and Register only for agri/medical, harvest-day choices only for agri. App Lock on Windows
  (`AppLock.kt`, More → Security): the owner's 4-8 digit PIN, kept only as a
  salted PBKDF2 hash in app.properties; nothing of the book is composed until
  it is typed; five misses wait 30 s. Unlike the phone (Android's own screen
  lock) it is the app's PIN, because a JVM cannot reach Windows Hello; it
  hides the ledger, it does not encrypt the file — the screen says so. A customer's
  statement dialog shows the phone's Report figures first (gave, got, net in
  the window; the whole account's closing balance). A first-run walkthrough
  (`Tour.kt`, the phone's coach words) shows once after trade and name. A shop picture
  (logo, signature, stamp, QR) is cropped before it is kept (`ImageCrop.kt`,
  the phone's Adjust crop: corners resize, inside moves; signature, stamp and
  QR start at their ink). Esc closes every dialog window (`escCloses`). MSI size (1.8.1): ICU4J is
  gone from the MSI (14.7 MB, used only for plural rules — the computer's own
  `pluralCategory` covers the nine languages; PluralRulesDesktopTest holds it
  to ICU4J for 0..10,000, ICU4J is test-only); on the Windows runner a Gradle
  artifact transform drops sqlite-bundled's Linux/macOS natives (4.7 MB). No
  ProGuard yet: Room, Compose and reflection would need a run-time smoke test
  first (lesson 17).
  `ScreenProofTest`
  draws the window (Home, English/Urdu, light/dark, made-up shop) to
  build/screen-proof, put on desktop-test — look at it after any UI change.
  The scrolling section rail (each section wears the
  phone's own tile icon, synced from `app/src/main/res` at build time — plus
  the header glyphs ic_storefront, ic_bell, ic_eye_*, ic_phone, ic_search;
  `TileIconsTest` reads them all) holds: Khata (master-detail, newest dealing first, statement for the whole
  ledger or a period), Follow-up (WhatsApp reminders via wa.me), Cashbook,
  Cheques, Plans, Staff, Seasons, Schemes, Supplier Bills (new bills via
  `insertSupplierBill`), Expiry, Stock register, Products (edit, who bought),
  Purchase rates, Price list, Invoices (new + print, one plain design), Khata
  Report, Register, Cash flow, Insights, Zakat, Calculator, Duplicates, Daily
  copies, Help, Business Profile; Businesses on the header (one book per
  shop: `khata.db` / `khata_b<id>.db`, Business 1 never deleted).
  Money-moving writes that the phone did step by step are DAO transactions
  here (`clearCheque`, `recordPlanPayment`); any new DAO write needs
  `scripts/gen_viewer_dao.py` or `ViewerDaoTest` fails.
  Bump `packageVersion` in `desktop/build.gradle.kts` for every MSI the owner
  installs, or Windows will not upgrade it; the MSIX takes its version from
  the same line. Material's own colours come from `RoshanTheme` (dark mode
  once hid typed text). Entries carry date, goods, payment method, Qarz-e-Hasna and recovery; the bin purges after 30 days. Counter shortcuts: F1 Khata, F2/F3 gave/got,
  Ctrl+N, Ctrl+P. The Khata page exports every party to Excel (CSV), prints
  every statement as one job, and imports customers from another app's CSV
  (`CsvImport`: name, phone, balance -> one opening entry each, in one
  `importOpeningBalances` transaction; the owner picks the file's sign before
  anything is written). Daily copies can also go to a chosen folder (USB,
  Google Drive for Desktop), rewritten on open and on close. Receipts print on
  a Windows-installed thermal printer as an ESC/POS picture (`ThermalReceipt`,
  raw through the spooler; not yet tried on a real printer). Bills and invoices
  are edited (`editSupplierBill`, `saveBillLine`, `updateInvoiceWithItems`);
  sales carry rate type, paid-now pairing (`recordCashForSale`), a batch and
  the payment day (Party.harvestPromise); Expiring traces a batch to its buyers.
  A customer card's QR typed by a USB scanner opens that customer.
  **The phone's printed designs run unchanged on the computer**: invoices
  (InvoicePdfExport, InvoiceTemplateKit), business cards (CardTemplates,
  CardGlyphs, CardLogo) and posters (PosterKit, PosterDesigns, PosterFlat,
  PosterGlyphs, PosterNewStock, PosterWords, PosterMotion, poster3d's Hero,
  CoinFace, SealFace, data's Poster and PosterFields) are COPIED by
  `scripts/gen_desktop_invoice.py`, `gen_desktop_cards.py` and
  `gen_desktop_poster.py` (Strings CI fails if stale — re-run after
  touching the phone's files) and drawn on `desktop/.../gfx`, a small
  android.graphics stand-in over Java2D (StaticLayout, shadows and blur,
  Path.op, SweepGradient, PathParser, drawBitmapMesh, Canvas(bitmap) since
  20 Oct). Posters are drawn flat on the computer (the 3D heroes are
  Filament's); proof pictures go to desktop-test (PosterDesignsTest).
  **Phone to computer over WiFi** (`data/WifiSync`, shared): the computer's
  "From phone" window shows a QR (its LAN addresses, a port, a fresh secret);
  the phone's Backup screen scans it and sends its backup sealed with
  AES-GCM over a plain socket (no HTTP: Android blocks clear text); the
  computer hands it to the usual restore confirmation. One way only. Proof pictures of every design go
  to the desktop-test release. Shop pictures (logo, signature, stamp, QR) and
  photos live per computer (the text backup carries none). The MSI's Java
  runtime has only the modules in desktop/build.gradle.kts (no java.prefs).
  Not on Windows: the posters' 3D heroes and video, voice entry
  (no free offline Urdu recognition), Google Drive sign-in (the backup
  folder can be a Google Drive for Desktop folder instead).

### The shared module (KMP)

```
shared/src/
  jvmSharedMain/   Kotlin that may use the JDK; Android and the computer share it
    data/          KhataDatabase (Room, @ConstructedBy), KhataDao, entities,
                   BackupCodec (the backup format), ShopSettings, Format...
    screens/       Shared Compose screens: About, PartyLedger, EntryForm, RoshanTheme
    words/         Words: the app's strings for shared screens (W / WP / WA names)
  androidMain/     The phone's side of each door (android.icu, JsonReader, BidiFormatter)
  desktopMain/     The computer's side (java.time Hijri, Gson, BidiPort, FileShopSettings,
                   DesktopDatabase, XmlWords from the copied strings.xml)
  desktopTest/     Run with `gradle :shared:desktopTest`
```

Rules learned moving code there:
- Android-only calls stay in `app` as **extensions with the same call shape**
  (`ShopCalendar.refresh(context)`, `Digits.quantity(res, ...)`,
  `UnitWords.choices(context)`): callers outside the `data` package import them.
- Across modules there are no smart casts on public `var`s and `internal` is
  not visible: copy to a local `val`, make test-used members public.
- A platform difference is a **door**: an `expect` in jvmSharedMain, one
  `actual` per platform, and a test holding the computer's side to the phone's
  (BidiPortTest, XmlWordsVsAndroidTest, HijriDesktopTest).
- Shared screens name words as `W.x`; the phone reads them through its own
  resources (generated `WordIds`, which also keeps them through shrinking),
  the computer from the same strings.xml (XmlWords). Compose resources are
  not used: Roman Urdu's script folder needs Compose 1.12, which needs
  compileSdk 37 and AGP 9.
- Compose Multiplatform is **1.11.1**; 1.12 waits for the AGP 9 move.
- `.gitattributes` keeps binaries binary: the Windows runner otherwise
  rewrote a PDF fixture's line endings.

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
  BaseActivity.kt           Every screen extends this (not AppCompatActivity): text size
  data/TextSize.kt          Owner's text size (Home ⋮) x phone scale, 85%-150% bounds
  ui/TextFit.kt             Fixed-height buttons may grow ONLY when text > 100%;
                            call TextFit.relax(view) after inflating any dialog/row in code
  data/
    VoiceEntry.kt           Spoken sentence -> party, amount, direction
    BusinessProfile.kt      QR, signature, photo-on-statement setting
    PdfExport.kt            Statement PDF — signature, optional photo
    DriveFeature.kt         ENABLED = false — the one line to flip
    KhataDatabaseAndroid.kt get(context): migrations, the phone's file (DB itself in :shared)
  ui/
    CardTemplates.kt        12 business card designs
    NameSearch.kt           One search rule for every screen
    Calc.kt                 evalPad, resolvePercent, trim
    DateRangeFilter.kt      Shared date filter
    DateTimeField.kt        Shared date + time picker

app/src/test/java/.../data/VoiceEntryTest.kt   What the microphone may conclude
```

VoiceEntry, DriveFeature, Calc and NameSearch now live in `:shared`
(`shared/src/jvmSharedMain/...`, same packages); their tests are in
`shared/src/jvmSharedTest`.

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

14. **3D posters (Filament): the CI emulator must use `-gpu swangle_indirect`.**
    The old `swiftshader_indirect` translator renders every material white,
    aborts on FXAA, and shadow maps kill the emulator (the run then hangs).
15. **A Filament read-back finishes only on `Renderer` frames.** `readPixels`
    waits on a fence that only `driver.tick()` (beginFrame/endFrame) checks;
    `Engine.flush()` then delivers the callback. `HeroStage.still` pumps both.
    Its rows come back top first — do not flip them.
16. **Look at the pictures, not only the numbers.** Upside-down 3D heroes and
    a reversed phone number passed every assertion; the "Poster proof"
    workflow puts the CI pictures on the `poster-proof` pre-release.
17. **R8 and the shrinker break at run time, not at build time.** Anything
    found by NAME must be kept: `getIdentifier` resources go in
    `res/raw/keep.xml`, classes compared by name in `proguard-rules.pro`.
    Enum names stored in preferences are safe (R8 keeps `name()`), but check
    any new reflection before shipping.

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

**Google Drive backup** asks for one Drive scope, `drive.appdata`, which
Google classes as **non-sensitive** (checked 9 Oct 2026 on Google's "Choose
Google Drive API scopes" page): no sensitive-scope verification, no 100-user
cap, no security audit. Basic brand verification (homepage, privacy policy,
domain ownership) is still worth doing so the consent screen shows the app's
name and logo. Do not add a sensitive Drive scope without re-checking this.

Sign-in needs an **Android OAuth client per package + SHA-1 pair** in the
`roshan-khata` Cloud project, beside the Web client the app passes as
serverClientId. The CI test APK is `com.innovation313.roshankhata.dev`
(client added 9 Oct); the Play build is `com.innovation313.roshankhata`
signed by Play's app-signing key, whose SHA-1 is in Play Console. A missing
pair shows as "cannot sign in"; the Backup screen's sign-in dialog prints the
running build's package and SHA-1 for exactly this.

**Data import from other ledger apps** is agreed but unbuilt. Balance-only
first: name, phone, current balance as one opening entry.

Smaller and deferred: merging duplicate parties, cashbook and cheque
`createdAt` editing, a WhatsApp number on the Help screen.

# Live sync relay on the owner's hosting (web/relay)

The third relay for live sync (after the shop's WiFi, Cloudflare and Supabase):
the same conversation as `cloud/worker`, in PHP with one SQLite file per book.
It keeps parcels already sealed on the device; it can open none.

## Putting it on the hosting (Hostinger, by hand)

1. Take `relay-upload.zip` from the "Relays" workflow's artifacts (it holds
   `r/index.php`, `r/.htaccess`, `r/data/.htaccess`).
2. In hPanel → File Manager → `public_html`, upload the zip and Extract it
   there, so the files land in `public_html/r/`.
3. Open `https://<domain>/r/v1/health` — it must say `{"ok":true,"live":true}`.
4. Open `https://<domain>/r/data/` — it must be refused (403), never a list.
5. The books' files are kept in `rk-relay-data/` beside `public_html` when the
   hosting allows it (else `r/data/`, which `.htaccess` denies).

The app already knows the address (`LiveCloud.HOSTING`, `https://ehtisham.shop/r`).

## The owner's switch

An empty file named `halt` in the store folder answers 503 to every call (the
app passes over to the next relay; the books on the devices are untouched).

## Limits (top of index.php)

1 MB a parcel, 40 parcels / 4 MB an answer, 3,000 parcels a book a UTC day,
8 devices a book, 2,000 books, 200 MB of disk always kept free. Parcels go
once every other device took them, or after 7 days; a book nobody touched for
30 days goes.

`scripts/test_relay.py` holds this relay, the Supabase one and Cloudflare's to
the same checks (relays.yml, cloud-worker.yml).

## Waris ki Chaabi (heirs' vaults, 4 Nov 2026)

The same `index.php` also keeps the heirs' vaults (`/r/v1/w/...`): one SQLite
per vault in `rk-relay-data/waris/w-<vault>.sqlite`, a folder the books'
cleanup never reads. A vault goes only after 1,095 days with no call, or when
the owner deletes it. Vaults count in the same 1 GB store limit; 2,000 vaults
at most. A snapshot is sent in chunks of at most 512 KB, as raw PUT bodies:
if the hosting's own upload limit is lower (`post_max_size` is for forms, not
PUT, but some hostings cap every body), a chunk is refused before PHP sees it —
then raise the limit to at least 1 MB in hPanel → PHP Configuration.

Nothing to set by hand for the heirs: `WARIS_DAY_MS` is for tests only and
must NOT be set on the hosting (the wait is then real days).

## Updating it

Upload the new `relay-upload.zip` and extract it over `public_html` the same
way; the books' files are kept. Until the 4 Nov 2026 `index.php` is up, the
heirs' routes answer 404 here and the app uses the other relays for them.
Until the new `index.php` is up, an app of the 22 Oct 2026 build (one-call `round`) gets 404 from it and falls back to the
older three calls on its own — nothing is lost, only three calls instead of one.


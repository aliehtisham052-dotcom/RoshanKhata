# Customer page (web/khata)

A customer's own page: the balance (and, only if the shop chooses, the last
three entries), sealed on the phone with a key that is only in the customer's
link. The server stores bytes it cannot read. Nothing here is uploaded by CI.

The app sends links on `https://khata.ehtisham.shop/` (`CustomerLink.HOST`,
7 Nov 2026). The same link opens Roshan Khata's "Mera Khata" screen when the
app is on the phone (App Links, `.well-known/assetlinks.json`), and the page
offers "Open in the app" on Android either way.

## Putting it on the hosting

1. hPanel → Domains → Subdomains: create `khata` for ehtisham.shop. Its folder
   is `public_html/khata`.
2. Upload this folder's files there, hidden ones included: `index.html`,
   `view.js`, `api.php`, `.htaccess`, `d/.htaccess`, `data/.htaccess`,
   `.well-known/.htaccess`, `.well-known/assetlinks.json` (this README need
   not go). The ready zip is made with `scripts/pack_khata_web.sh`.
3. Open `https://khata.ehtisham.shop/data/` and `https://khata.ehtisham.shop/d/`
   in a browser: both must say 403 (forbidden), never a list of files.
   `https://khata.ehtisham.shop/.well-known/assetlinks.json` must show the JSON.
4. The shops' passes and page lists are kept in `rk-khata-data/` beside
   `public_html` (api.php finds it from a subdomain's root too), else in
   `khata/data/`, which `.htaccess` denies. Check which one appeared after the
   first request. Beside the shops (`s/`) it holds `g.json` (the counts),
   `ip.json` (today's new shops by address, as a keyed hash only), `secret`
   (that hash's key, made on the first new shop) and `u/` (the ids ever let
   go, at most 256 files). Never upload or share any of them.

## App Links (the link opens the app without a question)

`assetlinks.json` names each package and the SHA-256 of the key that signs it:
the Play build (`com.innovation313.roshankhata`: the app signing key in use, the
previous app signing key and the upload key, copied from Play Console → Protected
with Play → Play Store protection → Manage Play app signing → Copy all certificate
fingerprints, 7 Nov 2026) and the test build (`com.innovation313.roshankhata.dev`).
A new signing key in Play Console means a new line here, uploaded again.

"Open in the app" (7 Nov 2026) is an Android intent that names the Play
build's package, so the key in the link goes to Roshan Khata alone, never to
another app that also answers `roshankhata://`; without the app it opens the
Play Store. The test build is opened by the shop's https link itself (its App
Link), not by this button.

## The owner's switches (empty files in the store folder)

- `halt` — every request is refused (also `khata/data/halt`).
- `closed` — no new shop is taken; shops already there go on.

## Limits (top of api.php)

200 pages a request, 4 KB a page, 1 MB a request; per shop 5,000 live pages
and 1,500 requests a UTC day; per server 2,000 shops and 100,000 page files;
nothing new is written below 200 MB free. A page not sent for 180 days
becomes a stub (the app refreshes every page monthly).

So that no one can use up what every shop needs (7 Nov 2026):

- a shop makes at most 50 new pages (and ids it keeps from others) a UTC day
  while it is younger than 30 days, 500 after; pages it already holds are
  kept fresh whatever the count;
- a fifth of the 100,000 files is kept for shops older than 30 days;
- new shops come at most 10 a UTC day from one address (an IPv6 address
  counts by its /64) and 60 an hour in all; the address is kept only as a
  hash keyed by `secret`. Behind a proxy or CDN that hides the visitor's
  address, every shop would share one; raise `NEW_SHOPS_IP` then;
- a request that makes no page leaves no shop behind, and a shop with no
  live page for 7 days goes;
- an id is never free again: a stub let go (after 180 more days, or with its
  shop) is written to `u/` first, and no shop, its own included, can put a
  page under it again.

The app itself sends only changed pages, at most once every 10 minutes, and
asks for a page only when Mera Khata opens (10 minutes apart; Refresh once a
minute) with the last ETag — an unchanged page is a 304. When the server says
busy, full or today's limit, the app says so to the owner in those words.

## What the page itself checks (view.js, 7 Nov 2026)

A page's seal names its link (`rk-clink-v2|<id>`), so it opens under no other
link; a page sealed before that date still opens. Each page carries a counter
that only grows: the browser keeps the newest it opened (localStorage) and
says so when an older one is served again — a browser that never opened the
link has nothing to compare with, and believes what it is given. Nothing
longer than the app writes is shown. The site is HTTPS only for this host
(HSTS, a year, no subdomains).

`scripts/test_khata_web.sh` checks all of this on PHP's own server, and runs
view.js itself in Node.

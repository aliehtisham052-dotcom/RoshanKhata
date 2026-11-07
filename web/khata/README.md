# Customer page (web/khata)

A customer's own page: the balance (and, only if the shop chooses, the last
three entries), sealed on the phone with a key that is only in the customer's
link. The server stores bytes it cannot read. Nothing here is uploaded by CI.

The app sends links on `https://khata.ehtisham.shop/` (`CustomerLink.HOST`,
7 Nov 2026). The same link opens Roshan Khata's "Mera Khata" screen when the
app is on the phone (App Links, `.well-known/assetlinks.json`), and the page
offers "Open in the app" (roshankhata://khata?l=…) on Android either way.

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
   first request.

## App Links (the link opens the app without a question)

`assetlinks.json` names each package and the SHA-256 of the key that signs it:
the Play build (`com.innovation313.roshankhata`: the app signing key in use, the
previous app signing key and the upload key, copied from Play Console → Protected
with Play → Play Store protection → Manage Play app signing → Copy all certificate
fingerprints, 7 Nov 2026) and the test build (`com.innovation313.roshankhata.dev`).
A new signing key in Play Console means a new line here, uploaded again. The
page's "Open in the app" button opens the app either way.

## The owner's switches (empty files in the store folder)

- `halt` — every request is refused (also `khata/data/halt`).
- `closed` — no new shop is taken; shops already there go on.

## Limits (top of api.php)

200 pages a request, 4 KB a page, 1 MB a request; per shop 5,000 live pages
and 1,500 requests a UTC day; per server 2,000 shops, 100,000 page files,
20 new shops an hour; nothing new is written below 200 MB free. A page not
sent for 180 days becomes a stub (the app refreshes every page monthly).
The app itself sends only changed pages, at most once every 10 minutes, and
asks for a page only when Mera Khata opens (10 minutes apart; Refresh once a
minute) with the last ETag — an unchanged page is a 304.

`scripts/test_khata_web.sh` checks all of this on PHP's own server.

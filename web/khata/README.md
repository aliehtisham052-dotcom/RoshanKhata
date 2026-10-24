# Customer page (web/khata)

A customer's own page: the balance and, if the shop chooses, the last three
entries, sealed on the phone with a key that is only in the customer's link.
The server stores bytes it cannot read. Nothing here is uploaded by CI.

## Putting it on the hosting

1. Upload `index.html`, `view.js`, `api.php`, `.htaccess`, `d/.htaccess` and
   `data/.htaccess` to `public_html/khata/` (this README need not go).
2. Open `https://<domain>/khata/data/` and `https://<domain>/khata/d/` in a
   browser: both must say 403 (forbidden), never a list of files.
3. The shops' passes and page lists are kept in `rk-khata-data/` beside
   `public_html` when the hosting allows it (else `khata/data/`, which
   `.htaccess` denies). Check which one appeared after the first request.
4. Then set `CustomerLink.base` in the app to `https://<domain>/khata/`.

## The owner's switches (empty files in the store folder)

- `halt` — every request is refused (also `khata/data/halt`).
- `closed` — no new shop is taken; shops already there go on.

## Limits (top of api.php)

200 pages a request, 4 KB a page, 1 MB a request; per shop 5,000 live pages
and 1,500 requests a UTC day; per server 2,000 shops, 100,000 page files,
20 new shops an hour; nothing new is written below 200 MB free. A page not
sent for 180 days becomes a stub (the app refreshes every page monthly).

`scripts/test_khata_web.sh` checks all of this on PHP's own server.

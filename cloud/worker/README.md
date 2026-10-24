# Roshan Khata relay ("daakiya")

Live sync's second road (Marhala 2): when the phone and the computer are not
on the same WiFi, each leaves its sealed parcels here and picks up the
other's. Parcels are sealed on the device with the book's key (AES-256-GCM);
this Worker has no key and cannot read a single figure or name.

- One Durable Object (SQLite) per book; a parcel goes once every other device
  has acknowledged it, or after `KEEP_DAYS` (7). A book nobody touched for 30
  days is emptied. It is a postbox, never a backup.
- Pass: the first device's `hello` sets the book's pass (SHA-256 of
  `HMAC-SHA256(bookKey, "rk-relay-v1:" + book)`); every call carries
  `Authorization: RK <token>` and `X-Device: <16 hex>`.
- Calls: `POST hello`, `POST send` (raw bytes, ≤ 1 MiB), `GET take?after=N`
  (40 a page, base64), `POST ack {"upTo":N}`, `POST wake {"token":fcm|null}`,
  `POST leave`; `GET /v1/health`. `LIVE = "0"` halts every book (503).
- Optional wake-up push through FCM HTTP v1 (data-only, no content) when the
  secrets `FCM_SERVICE_ACCOUNT` and `FCM_PROJECT_ID` are set.

Not deployed yet. `.github/workflows/cloud-worker.yml` type-checks and smoke
tests it on every push; it deploys only when run by hand with
`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` in the repository secrets.
Workers Free supports SQLite-backed Durable Objects (Cloudflare docs, checked
22 Oct 2026).

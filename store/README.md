# Play Store listing material

Everything here is free to make and free of real customer data.

- `listing/en-US.md`, `listing/ur.md` — app name, short and full description,
  release notes, within Play's limits (30 / 80 / 4000 / 500 characters).
- `feature-graphic.png` — 1024 x 500, rendered from the brand logo and fonts
  (the HTML lives in the session scratchpad; re-render by hand if it changes).
- `play-icon-512.png` — the store icon.
- Screenshots: Actions → "Store screenshots" → Run workflow. The emulator
  seeds a demo book (invented names), captures six screens in English and
  Urdu, and `scripts/frame_screenshots.py` frames each with a caption. The
  result is the `store-screenshots` artifact; nothing is committed.
- `fonts/` — the caption font for the images only, not shipped in the app.

Real customer names and numbers must never appear in anything here.

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

## Launcher icon (10 Oct)
`icon-source-book.png` is the owner's open-book tile the launcher icon is
cut from. The adaptive layers (`mipmap-*/ic_launcher_foreground.png` and
`ic_launcher_background.png`), the legacy `ic_launcher(.png|_round.png)` for
Android 7 and below, and `play-icon-512.png` are all rendered from it: the
book inside the 72dp safe disc, a circular gold ring at the disc's edge (a
circle fits inside every launcher mask), the tile's own green gradient as
the background out to the corners. `drawable/ic_launcher_monochrome.xml` is
the same book as one silhouette for Android 13 themed icons.

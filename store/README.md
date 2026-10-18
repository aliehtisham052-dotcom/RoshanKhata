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
cut from. The adaptive layers (`mipmap-*/ic_launcher_foreground.webp` and
`ic_launcher_background.webp`; lossless WebP since 12 Oct, pixel for pixel the
PNGs they replaced), the legacy `ic_launcher(.webp|_round.webp)` for
Android 7 and below, and `play-icon-512.png` are all rendered from it: the
book inside the 72dp safe disc, a circular gold ring at the disc's edge (a
circle fits inside every launcher mask), the tile's own green gradient as
the background out to the corners. `drawable/ic_launcher_monochrome.xml` is
the same book as one silhouette for Android 13 themed icons.

## In-app brand art (18 Oct)
The old "R + chart" emblem is gone from the app. Two pictures carried it:
- `drawable-nodpi/splash_art.webp` (language screen painting): the emblem was
  painted out and the book tile from `icon-source-book.png` set in its place.
  The wordmark, tagline, room and desk are untouched, so the tagline anchor in
  `ui/SplashArt.kt` (`TAGLINE_OF_ART = 0.62`) is unchanged.
- `drawable-nodpi/logo_full.webp` (App Lock screen): the green tile, gold rim,
  ROSHAN KHATA and tagline kept (they read on both light and dark `page_bg`);
  only the emblem replaced by the same book tile.
The launcher icon, system splash (`splash_logo`), About screen, PDF logo
(`assets/roshan_logo.png`) and notification icon were already the book.
`drawable-nodpi/app_badge.webp` is referenced nowhere.

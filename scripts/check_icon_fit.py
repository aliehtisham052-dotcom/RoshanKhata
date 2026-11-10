#!/usr/bin/env python3
"""The launcher icon and the splash mark stay inside the shapes phones cut them to (10 Nov 2026).

The owner saw the splash's corners and the icon's gold ring cut on some phones, not others:
- Android 12+ shows the splash icon inside a circle a third of the canvas wide (192 of 288 dp);
  the mark's rounded square reached 0.368 of the canvas, so its corners went.
- An adaptive icon is cut to the phone's own shape. A Pixel-style circle shows 72 of the 108 dp;
  Android promises only the middle 66 dp (the safe zone). The ring sat at 35.2 dp from the centre.
Older phones draw no circle, which is why only some phones showed it.

Fails when any opaque pixel of the splash mark lies outside that circle, or when anything of the
launcher foreground lies outside the 33 dp safe radius. Pillow only; run from the repo root.
"""
import glob
import math
import sys

from PIL import Image

ALPHA = 24  # what is fainter than this is shadow and antialiasing, not artwork


def reach(path: str) -> float:
    """How far the artwork reaches from the centre, as a share of the canvas width."""
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    a = im.split()[3].load()
    cx, cy = (w - 1) / 2, (h - 1) / 2
    far = 0.0
    for y in range(h):
        for x in range(w):
            if a[x, y] > ALPHA:
                far = max(far, math.hypot(x - cx, y - cy))
    return far / w


def main() -> int:
    bad = []
    splash = "app/src/main/res/drawable-nodpi/splash_logo.webp"
    r = reach(splash)
    if r > 1 / 3:
        bad.append(f"{splash}: reaches {r:.3f} of the canvas, Android 12+ shows only 0.333 (corners cut)")
    for f in sorted(glob.glob("app/src/main/res/mipmap-*/ic_launcher_foreground.webp")):
        dp = reach(f) * 108
        if dp > 33.0:
            bad.append(f"{f}: reaches {dp:.1f} dp from the centre, the safe zone is 33 dp (cut on round icons)")
    for line in bad:
        print("::error::" + line)
    print("icon fit:", "FAIL" if bad else "ok")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())

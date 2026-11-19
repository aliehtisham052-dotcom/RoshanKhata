"""Satin gold with a warm base tone, for every poster hero (19 Nov 2026).

The heroes' gold was a near mirror (roughness 0.12 to 0.22) in a dark studio. A mirror shows its room: the part of a
crescent or a dome that faces the camera reflected the dim wall behind the camera, so on the CI emulator's pictures
and on a phone the gold read as dark bronze with a thin bright rim, not as gold.

Two changes, written into the .glb files' own materials (glTF core fields, no code in the app):
  * roughness at least GOLD_ROUGH: a satin finish spreads the softbox and the strip lights over the surface;
  * an emissive base of GOLD_LIFT x the material's own colour: the warm light a real room would bounce back, so
    no part of the gold falls to black. For scale: the lantern glass glows at 0.6 (make_heroes.GLOW_STRENGTH).

make_heroes.py and make_models.py give the same values to a newly built model (their mat()), so running this on
their output changes nothing. Idempotent.

    python3 store/poster3d/tune_gold.py app/src/main/assets/poster3d
"""
import glob
import json
import os
import struct
import sys

GOLD_NAMES = ("gold", "rim")
GOLD_ROUGH = 0.26
GOLD_LIFT = 0.14


def tune(path):
    b = open(path, "rb").read()
    assert b[:4] == b"glTF", path
    n = struct.unpack("<I", b[12:16])[0]
    assert b[16:20] == b"JSON", path
    j = json.loads(b[20:20 + n])
    hit = 0
    for m in j.get("materials", []):
        if m.get("name") not in GOLD_NAMES:
            continue
        pbr = m.setdefault("pbrMetallicRoughness", {})
        base = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])[:3]
        rough = max(pbr.get("roughnessFactor", 1.0), GOLD_ROUGH)
        lift = [round(c * GOLD_LIFT, 4) for c in base]
        if pbr.get("roughnessFactor") != rough or m.get("emissiveFactor") != lift:
            pbr["roughnessFactor"] = rough
            m["emissiveFactor"] = lift
            hit += 1
    if not hit:
        return 0
    js = json.dumps(j, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    out = b[:12] + struct.pack("<I", len(js)) + b"JSON" + js + b[20 + n:]
    out = out[:8] + struct.pack("<I", len(out)) + out[12:]
    open(path, "wb").write(out)
    return hit


def main():
    folder = sys.argv[1]
    for path in sorted(glob.glob(os.path.join(folder, "*.glb"))):
        print(os.path.basename(path), tune(path), "gold material(s) tuned")


if __name__ == "__main__":
    main()

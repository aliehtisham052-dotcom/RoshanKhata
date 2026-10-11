"""The 3D heroes of the occasion posters, built in code (no paid tool, no
downloaded model). Run:  python3 make_heroes.py <out dir>

Every moving part is its own node whose ORIGIN IS ITS PIVOT, so the app turns
it with one rotation (ui/poster3d/HeroStage.kt, Motion):

eid_fitr.glb  crescent            inflated gold crescent, sways about Y
              lantern_l/lantern_r hexagonal lanterns, pivot at the hook, swing about Z
ramazan.glb   crescent, lantern_r
eid_adha.glb  mosque              gold mosque (dome, drum, minarets), sways about Y
hours.glb     clock (tilts), hand_hour / hand_minute / hand_second (turn about Z)
thanks.glb    seal                green wax seal; its face material "seal_face" carries a
                                  texture the app replaces with the shop's initials or logo

Units: metres-ish; +Y up, the camera looks down -Z.
No people and no animals, as everywhere in the app.
"""
import io
import math
import os
import sys

import numpy as np
import trimesh
from PIL import Image
from scipy.spatial import Delaunay
from shapely.geometry import Point, Polygon
from trimesh.visual import TextureVisuals
from trimesh.visual.material import PBRMaterial

OUT = sys.argv[1]
GOLD = (255, 205, 110)
GOLD_PALE = (255, 226, 160)
T = trimesh.transformations


def mat(colour, metal, rough, name, emissive=None, tex=None):
    kw = dict(baseColorFactor=[c / 255 for c in colour] + [1.0], metallicFactor=metal,
              roughnessFactor=rough, name=name)
    if emissive is not None:
        kw["emissiveFactor"] = [c / 255 for c in emissive]
    if tex is not None:
        kw["baseColorTexture"] = tex
    return PBRMaterial(**kw)


def paint(mesh, m, uv=None):
    mesh.visual = TextureVisuals(uv=uv, material=m)
    return mesh


def flat(mesh):
    """Faceted shading: every face keeps its own normal."""
    mesh = mesh.copy()
    mesh.unmerge_vertices()
    return mesh


def inflate(poly, height, spacing, back=1.0):
    """A pillow over a 2D outline: z = height(d) on the front, -back*height(d)
    behind, where d is the distance from the outline. Returns mesh, uv."""
    ring = poly.exterior.segmentize(spacing * 0.5)
    bnd = np.array(ring.coords[:-1])
    minx, miny, maxx, maxy = poly.bounds
    xs = np.arange(minx, maxx, spacing)
    ys = np.arange(miny, maxy, spacing * 0.866)
    inner = []
    for j, y in enumerate(ys):
        for x in xs + (spacing / 2 if j % 2 else 0):
            p = Point(x, y)
            if poly.contains(p) and poly.exterior.distance(p) > spacing * 0.45:
                inner.append((x, y))
    pts = np.vstack([bnd, np.array(inner)]) if inner else bnd
    tri = Delaunay(pts).simplices
    keep = [t for t in tri if poly.contains(Point(pts[t].mean(axis=0)))]
    tri = np.array(keep)
    nb = len(bnd)
    d = np.array([0.0] * nb + [poly.exterior.distance(Point(*p)) for p in pts[nb:]])
    z = np.array([height(v) for v in d])
    n = len(pts)
    # Front surface uses every point; the back reuses the boundary (z = 0) so the shape is closed.
    back_idx = np.arange(n)
    back_idx[nb:] = np.arange(n, n + n - nb)
    verts = np.vstack([np.column_stack([pts, z]), np.column_stack([pts[nb:], -back * z[nb:]])])
    front = tri[:, ::-1] if _ccw(pts, tri) < 0 else tri
    faces = np.vstack([front, back_idx[front][:, ::-1]])
    uv = np.column_stack([(verts[:, 0] - minx) / (maxx - minx), (verts[:, 1] - miny) / (maxy - miny)])
    m = trimesh.Trimesh(verts, faces, process=False)
    return m, uv


def _ccw(pts, tri):
    a, b, c = pts[tri[0, 0]], pts[tri[0, 1]], pts[tri[0, 2]]
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def rounded(h, r):
    """Flat top of height h with a quarter-round edge of radius r."""
    return lambda d: h * math.sqrt(max(0.0, 1 - (1 - min(d / r, 1.0)) ** 2))


def circle(cx, cy, r, n=180):
    return Polygon([(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)])


def crescent(r):
    """The moon: a tube swept along an arc, thickest in the middle and tapering
    to fine points, flattened front to back. Opens to the right."""
    rings, around = 120, 40
    span = math.radians(128)                     # half the arc
    rc = r * 0.78                                # radius of the centre line
    verts, uv = [], []
    for i in range(rings + 1):
        t = -1 + 2 * i / rings                   # -1 .. 1 along the horn
        a = math.pi + t * span                   # centred on the left
        w = r * 0.36 * max(0.0, math.cos(t * math.pi / 2)) ** 0.75
        c = np.array([rc * math.cos(a), rc * math.sin(a), 0])
        out = np.array([math.cos(a), math.sin(a), 0])
        for j in range(around):
            b = 2 * math.pi * j / around
            # Thicker toward the outside of the curve, as a real crescent is.
            radial = w * (1.0 + 0.35 * math.cos(b)) * math.cos(b)
            depth = w * 0.55 * math.sin(b)
            verts.append(c + out * (radial + w * 0.2) + np.array([0, 0, depth]))
            uv.append((i / rings, j / around))
    faces = []
    for i in range(rings):
        for j in range(around):
            a0 = i * around + j
            a1 = i * around + (j + 1) % around
            b0 = a0 + around
            b1 = a1 + around
            faces += [(a0, b0, b1), (a0, b1, a1)]
    m = trimesh.Trimesh(np.array(verts), np.array(faces), process=True)
    m.fix_normals()
    return paint(m, mat(GOLD, 1.0, 0.16, "gold"))


def polar_pillow(radius, height, rings=40, around=200, back=0.35):
    """A pillow over a star-shaped outline, radius(angle), meshed on rings so the
    shading is smooth: z = height(d), d = distance in from the edge."""
    ang = np.linspace(0, 2 * math.pi, around, endpoint=False)
    rr = radius(ang)
    verts, uv = [], []
    for side in (1, -1):
        for i in range(rings + 1):
            f = 1 - i / rings                    # 1 at the edge .. 0 at the centre
            for j in range(around):
                x, y = f * rr[j] * math.cos(ang[j]), f * rr[j] * math.sin(ang[j])
                z = height((1 - f) * rr[j]) * (1 if side > 0 else -back)
                verts.append((x, y, z))
    n = (rings + 1) * around
    faces = []
    for side in range(2):
        o = side * n
        for i in range(rings):
            for j in range(around):
                a0 = o + i * around + j
                a1 = o + i * around + (j + 1) % around
                b0, b1 = a0 + around, a1 + around
                faces += [(a0, a1, b1), (a0, b1, b0)] if side == 0 else [(a0, b1, a1), (a0, b0, b1)]
    v = np.array(verts)
    # Weld the two edges (z = 0) and the centres.
    m = trimesh.Trimesh(v, np.array(faces), process=True)
    m.fix_normals()
    lo, hi = v[:, :2].min(axis=0), v[:, :2].max(axis=0)
    uvs = (m.vertices[:, :2] - lo) / (hi - lo)
    return m, uvs


def revolve(profile, sections, colour, metal, rough, name, faceted=False, emissive=None):
    """Turn a (radius, height) profile about the Y axis."""
    m = trimesh.creation.revolve(np.array(profile, float), sections=sections)
    m.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
    if faceted:
        m = flat(m)
    return paint(m, mat(colour, metal, rough, name, emissive))


def lantern(scale=1.0, chain=1.2):
    """A hexagonal lantern hanging from its hook at the origin."""
    s = scale
    parts = []
    gold = mat(GOLD, 1.0, 0.22, "gold")
    # Hook ring and chain up out of frame.
    ring = trimesh.creation.torus(0.045 * s, 0.012 * s, major_sections=32, minor_sections=10)
    ring.apply_transform(T.rotation_matrix(math.pi / 2, [0, 1, 0]))
    parts.append(paint(ring, gold))
    rod = trimesh.creation.cylinder(radius=0.008 * s, height=chain, sections=10)
    rod.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
    rod.apply_translation([0, chain / 2 + 0.04 * s, 0])
    parts.append(paint(rod, gold))
    top = 0.045 * s
    # Finial, crown and dome.
    parts.append(revolve([(0, -top), (0.03 * s, -top - 0.02 * s), (0.02 * s, -top - 0.06 * s),
                          (0.07 * s, -top - 0.12 * s), (0.16 * s, -top - 0.24 * s), (0.20 * s, -top - 0.27 * s),
                          (0.20 * s, -top - 0.30 * s), (0, -top - 0.30 * s)][::-1], 6, GOLD, 1.0, 0.2, "gold", faceted=True))
    gy0, gy1 = -top - 0.30 * s, -top - 0.80 * s
    # Glass body: warm light behind amber glass.
    glass = revolve([(0, gy0), (0.18 * s, gy0), (0.21 * s, (gy0 + gy1) / 2), (0.18 * s, gy1), (0, gy1)][::-1],
                    6, (120, 50, 6), 0.0, 0.2, "glow", faceted=True, emissive=(255, 118, 18))
    parts.append(glass)
    # Gold bars on the six edges and two bands.
    for k in range(6):
        a = 2 * math.pi * k / 6
        for y0, y1, r0, r1 in ((gy0, (gy0 + gy1) / 2, 0.185, 0.215), ((gy0 + gy1) / 2, gy1, 0.215, 0.185)):
            p0 = np.array([r0 * s * math.cos(a), y0, r0 * s * math.sin(a)])
            p1 = np.array([r1 * s * math.cos(a), y1, r1 * s * math.sin(a)])
            bar = trimesh.creation.cylinder(radius=0.014 * s, segment=[p0, p1], sections=8)
            parts.append(paint(bar, gold))
    for y, r in ((gy0, 0.19), ((gy0 + gy1) / 2, 0.222), (gy1, 0.19)):
        band = revolve([(r * s - 0.012 * s, y - 0.016 * s), (r * s + 0.012 * s, y - 0.016 * s),
                        (r * s + 0.012 * s, y + 0.016 * s), (r * s - 0.012 * s, y + 0.016 * s)], 6, GOLD, 1.0, 0.2, "gold", faceted=True)
        parts.append(band)
    # Base and drop.
    parts.append(revolve([(0, gy1), (0.2 * s, gy1), (0.2 * s, gy1 - 0.04 * s), (0.1 * s, gy1 - 0.12 * s),
                          (0.03 * s, gy1 - 0.16 * s), (0.05 * s, gy1 - 0.2 * s), (0, gy1 - 0.25 * s)], 6, GOLD, 1.0, 0.2, "gold", faceted=True))
    return parts


def add(scene, parts, node, parent=None, at=(0, 0, 0)):
    """One node at [at] (its pivot) holding [parts]."""
    first = True
    for i, p in enumerate(parts if isinstance(parts, list) else [parts]):
        if first:
            scene.add_geometry(p, node_name=node, geom_name=f"{node}_{i}", parent_node_name=parent,
                               transform=T.translation_matrix(at))
            first = False
        else:
            scene.add_geometry(p, node_name=f"{node}_{i}", geom_name=f"{node}_{i}", parent_node_name=node)


# Placement: every hero's camera (Hero.kt, Hero.framed) puts the centre of its
# rectangle on the poster at world (0, 0) and shows 200 poster pixels per unit,
# so a part drawn at poster pixel (px, py) sits at ((px - cx) / 200, (cy - py) / 200).
# The numbers below come from the approved mockups (CSS px x 3).
U = 200.0


def at(px, py, cx, cy, z=0.0):
    return ((px - cx) / U, (cy - py) / U, z)


def eid_fitr():
    # Rectangle (0, 0)-(1080, 690), centre (540, 345).
    c = (540, 345)
    sc = trimesh.Scene()
    add(sc, crescent(0.583), "crescent", at=at(528, 516, *c))
    add(sc, lantern(1.26, chain=1.4), "lantern_l", at=at(132, 234, *c))
    add(sc, lantern(1.03, chain=0.8), "lantern_r", at=at(936, 108, *c))
    add(sc, lantern(0.78, chain=0.4), "lantern_m", at=at(810, 45, *c, z=-0.3))
    return sc


def ramazan():
    # Rectangle (0, 0)-(1080, 700), centre (540, 350).
    c = (540, 350)
    sc = trimesh.Scene()
    add(sc, crescent(0.84), "crescent", at=at(408, 426, *c))
    add(sc, lantern(1.1, chain=1.2), "lantern_r", at=at(900, 150, *c))
    return sc


def mosque():
    gold = (GOLD, 1.0, 0.2)
    parts = []
    base = trimesh.creation.box(extents=[1.5, 0.55, 0.7])
    base.apply_translation([0, 0.275 - 0.9, 0])
    ivory = mat((246, 236, 214), 0.0, 0.42, "ivory")
    parts.append(paint(flat(base), ivory))
    step = trimesh.creation.box(extents=[1.7, 0.06, 0.82])
    step.apply_translation([0, -0.93, 0])
    parts.append(paint(flat(step), mat(*gold, "gold")))
    # Arched door and two windows, dark, set just proud of the wall.
    for cx, w, h, y0 in ((0, 0.28, 0.38, -0.9), (-0.45, 0.14, 0.2, -0.68), (0.45, 0.14, 0.2, -0.68)):
        arch = Polygon([(cx + w / 2, y0)] + [(cx + w / 2 * math.cos(math.pi * i / 24), y0 + h - w / 2 + w / 2 * math.sin(math.pi * i / 24)) for i in range(25)] + [(cx - w / 2, y0)])
        door = trimesh.creation.extrude_polygon(arch, 0.02)
        door.apply_translation([0, 0, 0.352])
        parts.append(paint(door, mat((90, 36, 6), 0.0, 0.5, "glow", emissive=(255, 118, 18))))
    drum = trimesh.creation.cylinder(radius=0.36, height=0.16, sections=64)
    drum.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
    drum.apply_translation([0, -0.35 + 0.08, 0])
    parts.append(paint(drum, ivory))
    # Onion dome.
    prof = []
    for i in range(41):
        t = i / 40
        y = -0.19 + t * 0.78
        r = 0.36 * (1.0 + 0.18 * math.sin(math.pi * min(t / 0.55, 1.0))) * (1 - t ** 2.2) ** 0.6
        prof.append((max(r, 0.0), y))
    prof[-1] = (0.0, prof[-1][1])
    parts.append(revolve([(0, -0.19)] + prof, 72, GOLD, 1.0, 0.14, "gold"))
    # Finial with a small crescent.
    fin = trimesh.creation.cylinder(radius=0.012, height=0.2, sections=12)
    fin.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
    fin.apply_translation([0, 0.66, 0])
    parts.append(paint(fin, mat(*gold, "gold")))
    moon = crescent(0.07)
    moon.apply_transform(T.rotation_matrix(math.pi / 2, [0, 0, 1]))
    moon.apply_translation([0, 0.82, 0])
    parts.append(moon)
    # Two minarets with balconies and pointed caps.
    for x in (-0.92, 0.92):
        shaft = trimesh.creation.cylinder(radius=0.07, height=1.55, sections=8)
        shaft.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
        shaft.apply_translation([x, -0.93 + 0.775, 0])
        parts.append(paint(flat(shaft), ivory))
        for y in (-0.15, 0.42):
            bal = trimesh.creation.cylinder(radius=0.11, height=0.05, sections=8)
            bal.apply_transform(T.rotation_matrix(-math.pi / 2, [1, 0, 0]))
            bal.apply_translation([x, y, 0])
            parts.append(paint(flat(bal), mat(*gold, "gold")))
        cap = revolve([(0, 0.62), (0.085, 0.62), (0.07, 0.7), (0, 0.92)], 8, GOLD, 1.0, 0.16, "gold", faceted=True)
        cap.apply_translation([x, 0, 0])
        parts.append(cap)
    # Small side domes over the wings.
    for x in (-0.55, 0.55):
        sd = trimesh.creation.icosphere(subdivisions=3, radius=0.16)
        sd.vertices[:, 1] = np.maximum(sd.vertices[:, 1], 0)
        sd.apply_translation([x, -0.35, 0])
        parts.append(paint(sd, mat(*gold, "gold")))
    # Rectangle (90, 150)-(990, 930), centre (540, 540); the mosque about (540, 504),
    # 630 px tall, so x1.66 and its middle at world y 0.21.
    for p in parts:
        p.apply_scale(1.66)
    sc = trimesh.Scene()
    add(sc, parts, "mosque", at=(0, 0.21, 0))
    return sc


def clock():
    # Rectangle (240, 126)-(840, 726), centre (540, 426): the clock fills it, radius 1.2.
    sc = trimesh.Scene()
    parts = []
    bezel = trimesh.creation.torus(1.0, 0.09, major_sections=128, minor_sections=24)
    parts.append(paint(bezel, mat(GOLD, 1.0, 0.12, "gold")))
    face = trimesh.creation.cylinder(radius=1.0, height=0.06, sections=128)
    face.apply_translation([0, 0, -0.02])
    parts.append(paint(face, mat((5, 42, 28), 0.0, 0.45, "face")))
    back = trimesh.creation.cylinder(radius=1.02, height=0.16, sections=128)
    back.apply_translation([0, 0, -0.1])
    parts.append(paint(back, mat((150, 104, 30), 1.0, 0.35, "gold_dark")))
    for k in range(12):
        a = 2 * math.pi * k / 12
        long = k % 3 == 0
        tick = trimesh.creation.box(extents=[0.05 if long else 0.03, 0.17 if long else 0.1, 0.03])
        tick.apply_translation([0, 0.8 if long else 0.83, 0.03])
        tick.apply_transform(T.rotation_matrix(-a, [0, 0, 1]))
        parts.append(paint(tick, mat(GOLD_PALE, 1.0, 0.2, "gold")))
    for p in parts:
        p.apply_scale(1.1)
    add(sc, parts, "clock")

    def hand(length, width, tail, z, colour, rough, node):
        h = trimesh.creation.box(extents=[width, length + tail, 0.02])
        h.apply_translation([0, (length - tail) / 2, 0])
        h.apply_scale(1.1)
        p = [paint(h, mat(colour, 1.0, rough, "gold"))]
        add(sc, p, node, parent="clock", at=(0, 0, z * 1.1))
    hand(0.5, 0.07, 0.1, 0.05, GOLD_PALE, 0.18, "hand_hour")
    hand(0.74, 0.045, 0.12, 0.08, GOLD_PALE, 0.18, "hand_minute")
    hand(0.82, 0.016, 0.2, 0.11, (232, 96, 80), 0.3, "hand_second")
    cap = trimesh.creation.cylinder(radius=0.06, height=0.06, sections=32)
    cap.apply_translation([0, 0, 0.13])
    cap.apply_scale(1.1)
    add(sc, [paint(cap, mat(GOLD, 1.0, 0.15, "gold"))], "hub", parent="clock")
    return sc


def seal():
    rng = np.random.default_rng(313)
    harmonics = [(k, rng.uniform(0.01, 0.022), rng.uniform(0, 6.3)) for k in (2, 3, 5, 7)]
    drips = rng.uniform(0, 2 * math.pi, 5)

    def radius(ang):
        r = 1 + sum(a * np.sin(k * ang + p) for k, a, p in harmonics)
        for c in drips:     # five rounded drips where the wax ran
            r = r + 0.07 * np.exp(-(np.angle(np.exp(1j * (ang - c))) / 0.14) ** 2)
        return r

    def h(d):
        base = 0.16 * math.sqrt(max(0.0, 1 - (1 - min(d / 0.24, 1.0)) ** 2))
        ring = 0.03 * math.exp(-((d - 0.22) / 0.04) ** 2)      # the pressed rim
        return base + ring

    m, uv = polar_pillow(radius, h)
    # Rectangle (360, 1296)-(720, 1656), centre (540, 1476): radius 0.62 (124 px).
    m.apply_scale(0.58)
    # Placeholders the app replaces with the shop's mark (ui/poster3d/SealFace.kt).
    # The textures must exist here so the material samples them at all.
    tex = Image.open(os.environ["SEAL_TEX"]).convert("RGB") if os.environ.get("SEAL_TEX") else Image.new("RGB", (8, 8), (255, 255, 255))
    mr = Image.new("RGB", (8, 8), (0, 66, 0))         # G roughness 0.26, B metal 0
    face = PBRMaterial(baseColorFactor=[1.0, 1.0, 1.0, 1.0], metallicFactor=1.0, roughnessFactor=1.0,
                       baseColorTexture=tex, metallicRoughnessTexture=mr, name="seal_face")
    sc = trimesh.Scene()
    add(sc, [paint(m, face, uv)], "seal")
    return sc


GLOW_STRENGTH = float(os.environ.get("GLOW", "0.6"))


def glow(path):
    """trimesh writes no KHR_materials_emissive_strength; without it emissive
    tops out at 1 and the lantern glass reads as grey under the studio light."""
    import json
    import struct
    b = open(path, "rb").read()
    n = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + n])
    hit = False
    for m in j.get("materials", []):
        if m.get("name") == "glow":
            m.setdefault("extensions", {})["KHR_materials_emissive_strength"] = {"emissiveStrength": GLOW_STRENGTH}
            hit = True
    if not hit:
        return
    j.setdefault("extensionsUsed", []).append("KHR_materials_emissive_strength")
    js = json.dumps(j, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    rest = b[20 + n:]
    out = b[:12] + struct.pack("<I", len(js)) + b"JSON" + js + rest
    out = out[:8] + struct.pack("<I", len(out)) + out[12:]
    open(path, "wb").write(out)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, build in (("eid_fitr", eid_fitr), ("ramazan", ramazan), ("eid_adha", mosque),
                        ("hours", clock), ("thanks", seal)):
        path = os.path.join(OUT, name + ".glb")
        build().export(path)
        glow(path)
        print(path, os.path.getsize(path))


main()

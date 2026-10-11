"""The posters' 3D heroes, built in code (no paid tool, no downloaded model).

naya_maal.glb : node "box"    - an emerald crate, gold-framed faces with a sprout,
                                real gold edge bars that catch the studio light
                node "podium" - a glossy dark-green plinth with a gold rim
"""
import io
import math
import sys

import numpy as np
import trimesh
from PIL import Image, ImageDraw, ImageFilter
from trimesh.visual.material import PBRMaterial
from trimesh.visual import TextureVisuals

OUT = sys.argv[1]
GOLD = (255, 205, 110)


def face_textures(size=512):
    """Base colour and metallic-roughness for one crate face."""
    base = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(base)
    for y in range(size):
        t = y / size
        c = tuple(int(a + (b - a) * t) for a, b in zip((38, 140, 98), (10, 52, 34)))
        d.line([(0, y), (size, y)], fill=c)
    mr = Image.new("RGB", (size, size), (0, int(0.42 * 255), 0))   # G roughness, B metallic
    m = ImageDraw.Draw(mr)
    f = int(size * 0.07)
    for img, colour in ((d, GOLD), (m, (0, int(0.22 * 255), 255))):
        img.rectangle([f, f, size - f, size - f], outline=colour, width=int(size * 0.025))
    # A sprout: stem and two leaves, in gold.
    cx, cy, s = size / 2, size / 2, size * 0.26

    def leaf(draw, colour, flip):
        pts = []
        for i in range(25):
            t = i / 24
            x = cx + flip * (t * s * 0.95)
            y = cy - s * 0.15 - math.sin(t * math.pi) * s * 0.42 - t * s * 0.35
            pts.append((x, y))
        for i in range(24, -1, -1):
            t = i / 24
            x = cx + flip * (t * s * 0.95)
            y = cy - s * 0.15 - math.sin(t * math.pi) * s * 0.05 - t * s * 0.35
            pts.append((x, y))
        draw.polygon(pts, fill=colour)

    for draw, colour in ((d, GOLD), (m, (0, int(0.22 * 255), 255))):
        draw.line([(cx, cy + s * 0.9), (cx, cy - s * 0.25)], fill=colour, width=int(size * 0.03))
        leaf(draw, colour, 1)
        leaf(draw, colour, -1)
    return base, mr


def png(img):
    b = io.BytesIO()
    img.save(b, format="PNG")
    return Image.open(io.BytesIO(b.getvalue()))


def textured_cube(half, base, mr):
    """A cube whose six faces each carry the whole texture."""
    faces = [
        ((0, 0, 1), (1, 0, 0), (0, 1, 0)), ((0, 0, -1), (-1, 0, 0), (0, 1, 0)),
        ((1, 0, 0), (0, 0, -1), (0, 1, 0)), ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
        ((0, 1, 0), (1, 0, 0), (0, 0, -1)), ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
    ]
    v, uv, fc, nrm = [], [], [], []
    for n, r, u in faces:
        n, r, u = map(np.array, (n, r, u))
        o = len(v)
        for a, b, t in ((-1, -1, (0, 0)), (1, -1, (1, 0)), (1, 1, (1, 1)), (-1, 1, (0, 1))):
            v.append((n + a * r + b * u) * half)
            uv.append(t)
            nrm.append(n)
        fc += [(o, o + 1, o + 2), (o, o + 2, o + 3)]
    mesh = trimesh.Trimesh(vertices=np.array(v), faces=np.array(fc), vertex_normals=np.array(nrm), process=False)
    mat = PBRMaterial(baseColorTexture=png(base), metallicRoughnessTexture=png(mr),
                      metallicFactor=1.0, roughnessFactor=1.0, name="crate")
    mesh.visual = TextureVisuals(uv=np.array(uv, float), material=mat)
    return mesh


def solid(mesh, colour, metal, rough, name):
    mesh.visual = TextureVisuals(material=PBRMaterial(
        baseColorFactor=[c / 255 for c in colour] + [1.0], metallicFactor=metal, roughnessFactor=rough, name=name))
    return mesh


def edge_bars(half, thick):
    bars = []
    L = 2 * half + thick
    for axis in range(3):
        for s1 in (-1, 1):
            for s2 in (-1, 1):
                ext = [thick, thick, thick]
                ext[axis] = L
                b = trimesh.creation.box(extents=ext)
                pos = [0, 0, 0]
                others = [i for i in range(3) if i != axis]
                pos[others[0]] = s1 * half
                pos[others[1]] = s2 * half
                b.apply_translation(pos)
                bars.append(b)
    return trimesh.util.concatenate(bars)


def main():
    base, mr = face_textures()
    scene = trimesh.Scene()
    crate = textured_cube(0.5, base, mr)
    edges = solid(edge_bars(0.5, 0.07), GOLD, 1.0, 0.18, "gold")
    box = trimesh.util.concatenate([crate])  # keep the crate's own material
    scene.add_geometry(box, node_name="box", geom_name="crate")
    scene.add_geometry(edges, node_name="box_edges", geom_name="edges", parent_node_name="box")

    plinth = trimesh.creation.cylinder(radius=0.95, height=0.26, sections=96)
    plinth.apply_transform(trimesh.transformations.rotation_matrix(math.pi / 2, [1, 0, 0]))
    plinth.apply_translation([0, -0.95, 0])
    solid(plinth, (18, 70, 48), 0.0, 0.12, "plinth")
    rim = trimesh.creation.annulus(r_min=0.93, r_max=0.98, height=0.03, sections=96)
    rim.apply_transform(trimesh.transformations.rotation_matrix(math.pi / 2, [1, 0, 0]))
    rim.apply_translation([0, -0.82, 0])
    solid(rim, GOLD, 1.0, 0.2, "rim")
    scene.add_geometry(plinth, node_name="podium", geom_name="plinth")
    scene.add_geometry(rim, node_name="podium_rim", geom_name="rim", parent_node_name="podium")
    scene.export(OUT)
    print(OUT)


main()

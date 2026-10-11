"""A dark photo-studio environment for the poster's 3D heroes.

Equirectangular, linear HDR (Radiance .hdr, flat RGBE): a warm softbox overhead,
two tall strip lights left and right, a faint warm floor bounce. cmgen turns it
into the image-based light that gives gold its reflections.
"""
import numpy as np
import sys

W, H = 1024, 512
v, u = np.mgrid[0:H, 0:W]
theta = (u + 0.5) / W * 2 * np.pi - np.pi          # longitude -pi..pi (0 = front, -Z)
phi = np.pi / 2 - (v + 0.5) / H * np.pi            # latitude +pi/2 (up) .. -pi/2
x = np.cos(phi) * np.sin(theta)
y = np.sin(phi)
z = -np.cos(phi) * np.cos(theta)

img = np.zeros((H, W, 3), np.float32)
# Base: dark room, slightly brighter toward the horizon, faint warm floor.
base = 0.015 + 0.03 * np.clip(1 - np.abs(y), 0, 1) ** 2
img += base[..., None] * np.array([1.0, 0.95, 0.9])
floor = np.clip(-y, 0, 1) ** 0.5 * 0.05
img += floor[..., None] * np.array([1.0, 0.8, 0.55])


def box(cx, cy, cz, half_w, half_h, power, colour, up=(0, 1, 0)):
    """A rectangular light facing the centre, defined by its direction."""
    d = np.array([cx, cy, cz], np.float32); d /= np.linalg.norm(d)
    upv = np.array(up, np.float32)
    right = np.cross(upv, d); n = np.linalg.norm(right)
    if n < 1e-4:
        right = np.array([1, 0, 0], np.float32)
    else:
        right /= n
    upp = np.cross(d, right)
    dirs = np.stack([x, y, z], -1)
    t = dirs @ d
    a = (dirs @ right) / np.maximum(t, 1e-4)
    b = (dirs @ upp) / np.maximum(t, 1e-4)
    m = (t > 0) & (np.abs(a) < half_w) & (np.abs(b) < half_h)
    soft = np.clip(1 - (np.abs(a) / half_w) ** 8, 0, 1) * np.clip(1 - (np.abs(b) / half_h) ** 8, 0, 1)
    img[m] += (soft[m][..., None] * power) * np.array(colour, np.float32)


box(0.0, 1.0, -0.25, 0.55, 0.35, 9.0, (1.0, 0.94, 0.82), up=(0, 0, -1))   # overhead softbox
box(-1.0, 0.25, -0.35, 0.12, 0.9, 5.0, (1.0, 0.97, 0.92))                  # left strip
box(1.0, 0.2, -0.3, 0.12, 0.9, 4.0, (1.0, 0.85, 0.6))                       # right strip, warmer
box(0.0, 0.1, 1.0, 0.6, 0.25, 1.2, (0.55, 0.85, 0.7))                       # soft green fill behind camera

out = sys.argv[1]
with open(out, "wb") as f:
    f.write(b"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n")
    f.write(f"-Y {H} +X {W}\n".encode())
    mx = img.max(-1)
    e = np.where(mx > 1e-32, np.ceil(np.log2(np.maximum(mx, 1e-32))), -128).astype(np.int32)
    scale = np.where(mx > 1e-32, 256.0 / np.exp2(e), 0)
    rgb = np.clip(img * scale[..., None], 0, 255).astype(np.uint8)
    ex = np.clip(e + 128, 0, 255).astype(np.uint8)
    rgbe = np.concatenate([rgb, ex[..., None]], -1)
    f.write(rgbe.tobytes())
print(out, img.max())

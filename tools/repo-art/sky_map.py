"""
All-sky map of every deep-sky object bundled in the app (Hammer projection, RA/Dec J2000).
Run: python3 tools/repo-art/sky_map.py   (needs Pillow). Writes docs/art/every-object.png.

Galaxies (blue) avoid a band across the sky: that is the Milky Way's dust hiding what lies behind it
(the "zone of avoidance"), while nebulae and clusters (pink, gold) crowd into the same band.
"""
import gzip
import json
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(__file__)
CATALOG = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sky_catalog.json.gz")
OUT = os.path.join(HERE, "..", "..", "docs", "art", "every-object.png")
W, H = 1000, 500
BG = (5, 7, 16)
COLORS = {"Ga": (110, 170, 255), "Ne": (255, 90, 200), "Oc": (255, 205, 90), "Gc": (255, 150, 60)}


def hammer(ra, dec):
    """RA/Dec degrees -> pixel. RA increases to the left, as on a sky chart."""
    lon = math.radians(((180 - ra) + 180) % 360 - 180)
    lat = math.radians(dec)
    z = math.sqrt(1 + math.cos(lat) * math.cos(lon / 2))
    x = 2 * math.sqrt(2) * math.cos(lat) * math.sin(lon / 2) / z
    y = math.sqrt(2) * math.sin(lat) / z
    return (W / 2 + x / (2 * math.sqrt(2)) * (W / 2 - 4), H / 2 - y / math.sqrt(2) * (H / 2 - 4))


def main():
    data = json.load(gzip.open(CATALOG))
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, W - 4, H - 4), fill=(9, 13, 30), outline=(40, 60, 100))
    for dec in range(-60, 90, 30):  # graticule
        d.line([hammer(ra, dec) for ra in range(0, 361, 5)], fill=(22, 32, 60))
    for ra in range(0, 360, 30):
        d.line([hammer(ra, dec) for dec in range(-90, 91, 5)], fill=(22, 32, 60))
    px = img.load()
    counts = {}
    # Galaxies first so the rarer nebulae and clusters sit on top.
    for kind in ("Ga", "Ne", "Oc", "Gc"):
        c = COLORS[kind]
        for o in data["dso"]:
            if o["t"] != kind:
                continue
            counts[kind] = counts.get(kind, 0) + 1
            x, y = hammer(o["ra"], o["dec"])
            x, y = int(x), int(y)
            if 0 <= x < W and 0 <= y < H:
                old = px[x, y]
                a = 0.55 if kind == "Ga" else 0.9
                px[x, y] = tuple(min(255, int(old[i] + c[i] * a)) for i in range(3))
                if kind in ("Oc", "Gc"):
                    for dx, dy in ((1, 0), (0, 1), (1, 1)):
                        if x + dx < W and y + dy < H:
                            px[x + dx, y + dy] = c
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, optimize=True)
    print(OUT, counts, sum(counts.values()))


if __name__ == "__main__":
    main()

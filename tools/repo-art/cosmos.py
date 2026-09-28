"""
More README pixel animations: a spiral galaxy, the planets, and a star-strip divider.
Run through make_art.py (python3 tools/repo-art/make_art.py). Writes docs/art/galaxy.gif, planets.gif and divider.gif.
"""
import math
import random

from PIL import Image

from make_art import AMBER, SKY_TOP, WHITE, lerp, save


def spiral_galaxy():
    """A two-armed pixel spiral galaxy, tilted like Andromeda, turning slowly. Half a turn is one loop, so it repeats seamlessly."""
    size, scale, frames = 120, 3, 48
    rnd = random.Random(31)
    stars = []  # (radius, angle, colour, brightness, twinkle phase)
    for _ in range(2600):
        arm = rnd.randrange(2)
        r = rnd.random() ** 0.8 * 52 + 2
        theta = arm * math.pi + math.log(r) * 2.3 + rnd.gauss(0, 0.28 + 6 / r)
        colour = (170, 200, 255) if rnd.random() < 0.8 else (255, 150, 210)  # young blue stars, pink star-forming knots
        stars.append((r, theta, colour, 0.35 + 0.65 * rnd.random(), rnd.random() * 6.28))
    for _ in range(900):  # old yellow stars in the bulge
        r = abs(rnd.gauss(0, 9))
        stars.append((r, rnd.random() * 6.28, (255, 225, 160), 0.5 + 0.5 * rnd.random(), rnd.random() * 6.28))
    field = [(rnd.randrange(size), rnd.randrange(size), rnd.random() * 6.28) for _ in range(60)]
    tilt, pa = 0.42, math.radians(-35)  # seen at an angle, like M31
    out = []
    for f in range(frames):
        img = Image.new("RGB", (size, size), SKY_TOP)
        px = img.load()
        for x, y, ph in field:
            px[x, y] = lerp(SKY_TOP, WHITE, 0.25 + 0.3 * (0.5 + 0.5 * math.sin(f / frames * 2 * math.pi * 2 + ph)))
        spin = f / frames * math.pi
        light = {}
        for r, theta, colour, b, ph in stars:
            a = theta + spin
            x, y = r * math.cos(a), r * math.sin(a) * tilt
            key = (int(size / 2 + x * math.cos(pa) - y * math.sin(pa)), int(size / 2 + x * math.sin(pa) + y * math.cos(pa)))
            if 0 <= key[0] < size and 0 <= key[1] < size:
                tw = 0.75 + 0.25 * math.sin(f / frames * 2 * math.pi * 3 + ph)
                light.setdefault(key, []).append(tuple(ch * b * tw for ch in colour))
        for (x, y), cs in light.items():  # add up the light in each pixel, so dense parts glow
            total = [min(255, int(sum(c[i] for c in cs) * 0.7)) for i in range(3)]
            px[x, y] = tuple(max(px[x, y][i], total[i]) for i in range(3))
        for x in range(size):  # a dust lane darkens the near side of the disc
            for y in range(size):
                dx, dy = x - size / 2, y - size / 2
                u = dx * math.cos(-pa) - dy * math.sin(-pa)
                v = (dx * math.sin(-pa) + dy * math.cos(-pa)) / tilt
                if 14 < math.hypot(u, v) < 40 and 3 < v < 9:
                    c = px[x, y]
                    px[x, y] = (int(c[0] * 0.55), int(c[1] * 0.55), int(c[2] * 0.6))
        for dx in range(-2, 3):  # bright core
            for dy in range(-1, 2):
                cx, cy = size // 2 + dx, size // 2 + dy
                px[cx, cy] = lerp(px[cx, cy], (255, 244, 214), 0.9 - 0.15 * abs(dx))
        out.append(img.resize((size * scale, size * scale), Image.NEAREST))
    save(out, "galaxy.gif")


def _disc(px, cx, cy, r, colour_at):
    for y in range(-r, r + 1):
        for x in range(-r, r + 1):
            if x * x + y * y <= r * r:
                px[cx + x, cy + y] = colour_at(x / r, y / r)


def planets():
    """The Moon cycling through its phases, Jupiter turning (bands and Great Red Spot), Saturn and its rings, and Mars."""
    w, h, scale, frames = 240, 56, 3, 32
    rnd = random.Random(5)
    field = [(rnd.randrange(w), rnd.randrange(h), rnd.random() * 6.28) for _ in range(70)]
    out = []
    for f in range(frames):
        t = f / frames
        img = Image.new("RGB", (w, h), SKY_TOP)
        px = img.load()
        for x, y, ph in field:
            px[x, y] = lerp(SKY_TOP, WHITE, 0.2 + 0.4 * (0.5 + 0.5 * math.sin(t * 2 * math.pi * 2 + ph)))

        # Moon: the lit part grows from new to full and shrinks back once per loop.
        phase = t * 2 * math.pi
        def moon(u, v):
            edge = math.cos(phase) * math.sqrt(max(0.0, 1 - v * v))
            lit = u > edge if phase <= math.pi else u < -edge
            if not lit:
                return (28, 30, 40)
            crater = (int((u + 1) * 7) * 3 + int((v + 1) * 7) * 5) % 11 == 0
            return (205, 205, 196) if crater else (232, 232, 222)
        _disc(px, 26, 28, 16, moon)

        # Jupiter: coloured belts, and the Great Red Spot carried round by the rotation.
        def jupiter(u, v):
            lon = (math.asin(max(-1.0, min(1.0, u / math.sqrt(max(1e-6, 1 - v * v))))) / math.pi + t) % 1
            belts = [(226, 200, 160), (190, 140, 100), (236, 214, 176), (170, 120, 86), (226, 200, 160), (200, 160, 120)]
            c = belts[int((v + 1) * 6) % 6]
            if 0.25 < v < 0.45 and abs(lon - 0.5) < 0.08:
                c = (205, 90, 60)
            shade = 0.65 + 0.35 * math.sqrt(max(0.0, 1 - u * u - v * v))
            return tuple(int(ch * shade) for ch in c)
        _disc(px, 88, 28, 20, jupiter)

        # Saturn: the back half of the rings, the globe, then the front half.
        def rings(front):
            for a in range(0, 360, 2):
                s = math.sin(math.radians(a))
                if (s > 0) != front:
                    continue
                for rr in (26, 29, 32):
                    x, y = 158 + rr * math.cos(math.radians(a)), 28 + rr * 0.3 * s
                    px[int(x), int(y)] = (170, 150, 110) if rr == 29 else (214, 196, 150)
        rings(False)
        _disc(px, 158, 28, 13, lambda u, v: tuple(int(ch * (0.6 + 0.4 * math.sqrt(max(0.0, 1 - u * u - v * v))))
                                                  for ch in ((230, 206, 150) if int((v + 1) * 5) % 2 else (210, 186, 130))))
        rings(True)

        # Mars: a polar cap and a dark marking drifting round.
        def mars(u, v):
            if v < -0.75:
                return (240, 240, 240)
            dark = abs(((u + 1) / 2 + t) % 1 - 0.4) < 0.12 and abs(v) < 0.3
            shade = 0.6 + 0.4 * math.sqrt(max(0.0, 1 - u * u - v * v))
            return tuple(int(ch * shade) for ch in ((150, 70, 40) if dark else (214, 110, 64)))
        _disc(px, 218, 28, 11, mars)
        out.append(img.resize((w * scale, h * scale), Image.NEAREST))
    save(out, "planets.gif")


def divider():
    """A thin strip of twinkling stars with a shooting star, to separate README sections."""
    w, h, scale, frames = 200, 10, 4, 36
    rnd = random.Random(11)
    field = [(rnd.randrange(w), rnd.randrange(h), rnd.random() * 6.28, rnd.random()) for _ in range(55)]
    out = []
    for f in range(frames):
        img = Image.new("RGB", (w, h), SKY_TOP)
        px = img.load()
        for x, y, ph, b in field:
            colour = WHITE if b > 0.2 else AMBER
            px[x, y] = lerp(SKY_TOP, colour, (0.3 + 0.7 * b) * (0.5 + 0.5 * math.sin(f / frames * 2 * math.pi * 2 + ph)))
        if f < 18:  # a shooting star crosses once per loop
            hx, hy = 10 + f * 11, 2 + f // 4
            for k in range(8):
                x, y = hx - k * 2, hy - k // 4
                if 0 <= x < w and 0 <= y < h:
                    px[x, y] = lerp(px[x, y], (255, 240, 200), 1 - k / 8)
        out.append(img.resize((w * scale, h * scale), Image.NEAREST))
    save(out, "divider.gif")

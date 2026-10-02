"""
Pixel-art animations for the README: the hero banner and Hop's idle loop.
Run: python3 tools/repo-art/make_art.py   (needs Pillow). Writes docs/art/*.gif, including cosmos.py's animations
and guide_art.py's explainers (banner, how alignment works, plate solving, next star). walkthrough.py stitches the
real app screens in docs/screens/new/ into docs/art/walkthrough.gif; run readme_screens.py first to refresh them.

The banner acts out what the app does: Hop starts at the telescope, lines up on a bright guide star (Vega),
then star-hops along the guidance line to a faint target (the Andromeda galaxy), where the reticle locks on.
"""
import math
import os
import random

from PIL import Image

import sprites

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "art")
W, H, SCALE = 200, 84, 4
FRAMES = 72
FRAME_MS = 90

SKY_TOP = (4, 6, 16)
SKY_MID = (11, 16, 38)
SKY_LOW = (27, 42, 74)
HILL = (7, 16, 10)
HILL_EDGE = (59, 90, 58)
CYAN = (0, 229, 255)
PINK = (255, 79, 216)
AMBER = (232, 163, 61)
WHITE = (242, 246, 255)

# Pixel font (5x7) for the title.
FONT = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'F': ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'X': ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(img):
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        c = lerp(SKY_TOP, SKY_MID, t / 0.6) if t < 0.6 else lerp(SKY_MID, SKY_LOW, (t - 0.6) / 0.4)
        for x in range(W):
            px[x, y] = c


def hills_height(x):
    return int(70 + 3 * math.sin(x / 13.0) + 2 * math.sin(x / 5.3 + 1) + (4 if 150 < x < 185 else 0) * math.sin((x - 150) / 35 * math.pi))


def hills(img):
    px = img.load()
    for x in range(W):
        top = hills_height(x)
        for y in range(top, H):
            px[x, y] = HILL
        px[x, top] = HILL_EDGE


def text(img, s, x0, y0, color, shadow=None):
    px = img.load()
    for i, ch in enumerate(s):
        glyph = FONT[ch]
        for y, row in enumerate(glyph):
            for x, bit in enumerate(row):
                if bit == "1":
                    X, Y = x0 + i * 6 + x, y0 + y
                    if shadow:
                        px[X + 1, Y + 1] = shadow
                    px[X, Y] = color


def line(img, a, b, color, dash=0, upto=1.0):
    """Pixel line from a to b; dash > 0 draws every other `dash` pixels; `upto` draws only that fraction."""
    px = img.load()
    n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
    for i in range(int(n * upto) + 1):
        if dash and (i // dash) % 2:
            continue
        x = round(a[0] + (b[0] - a[0]) * i / max(n, 1))
        y = round(a[1] + (b[1] - a[1]) * i / max(n, 1))
        if 0 <= x < W and 0 <= y < H:
            px[x, y] = color


def telescope(img):
    """A little Dobsonian: box base, white tube tilted up-right, phone strapped on top with a cyan screen."""
    px = img.load()
    base = [(x, y) for x in range(18, 30) for y in range(64, 72)]
    for x, y in base:
        px[x, y] = (120, 80, 40) if (x + y) % 7 else (90, 60, 30)
    for i in range(26):  # tube
        cx, cy = 22 + i, 64 - int(i * 0.62)
        for d in range(-3, 4):
            x, y = cx - int(d * 0.5), cy + d
            px[x, y] = (230, 232, 240) if abs(d) < 3 else (150, 155, 170)
    for d in range(-3, 4):  # front ring
        px[47 - int(d * 0.5), 48 + d] = (60, 60, 70)
    for x in range(30, 36):  # phone
        for y in range(51, 55):
            px[x, y] = (20, 20, 24)
    for x in range(31, 35):
        for y in range(52, 54):
            px[x, y] = CYAN


def galaxy(img, x0, y0, glow):
    """Andromeda: a tilted pixel ellipse with a bright core."""
    px = img.load()
    for dx in range(-7, 8):
        for dy in range(-3, 4):
            u = (dx * 0.94 + dy * 0.34) / 7.0
            v = (-dx * 0.34 + dy * 0.94) / 2.6
            r = u * u + v * v
            if r < 1:
                a = (1 - r) * (0.55 + 0.45 * glow)
                base = px[x0 + dx, y0 + dy]
                px[x0 + dx, y0 + dy] = lerp(base, (210, 200, 255), a)
    px[x0, y0] = WHITE


def star(img, x, y, b, big=False, color=WHITE):
    px = img.load()
    c = lerp(px[x, y], color, b)
    px[x, y] = c
    if big:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = lerp(px[x + dx, y + dy], color, b * 0.55)


def reticle(img, x, y, r, color):
    px = img.load()
    for a in range(0, 360, 12):
        X = x + round(r * math.cos(math.radians(a)))
        Y = y + round(r * math.sin(math.radians(a)))
        if 0 <= X < W and 0 <= Y < H:
            px[X, Y] = color
    for d in range(r + 2, r + 5):
        for X, Y in ((x + d, y), (x - d, y), (x, y + d), (x, y - d)):
            if 0 <= X < W and 0 <= Y < H:
                px[X, Y] = color


def hop_arc(a, b, t, height):
    """Position along a hop from a to b at t in [0, 1]."""
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - height * 4 * t * (1 - t))


def banner():
    rnd = random.Random(7)
    stars = [(rnd.randrange(W), rnd.randrange(58), rnd.random() * 0.8 + 0.2, rnd.random() * 6.28) for _ in range(150)]
    milky = [(x, y) for x, y in ((rnd.randrange(W), rnd.randrange(70)) for _ in range(2200))
             if abs(y - (72 - x * 0.33)) < 7 + 5 * math.sin(x / 17)]
    vega = (74, 30)
    hops = [(40, 44), vega, (104, 22), (128, 30), (152, 18)]  # telescope mouth, Vega, two stepping stars, target
    target = hops[-1]
    frames = []
    for f in range(FRAMES):
        img = Image.new("RGB", (W, H))
        sky(img)
        px = img.load()
        for x, y in milky:
            px[x, y] = lerp(px[x, y], (120, 130, 170), 0.18)
        for x, y, b, ph in stars:
            tw = 0.55 + 0.45 * math.sin(f / FRAMES * 2 * math.pi * 3 + ph)
            star(img, x, y, b * tw)
        galaxy(img, *target, 0.5 + 0.5 * math.sin(f / 5))
        star(img, *vega, 1.0, big=True, color=(200, 225, 255))
        for s in hops[2:4]:
            star(img, *s, 0.9, big=True)

        # Act 1 (0-15): the guidance line draws itself from Vega to the target.
        draw_line = min(1.0, f / 15)
        for a, b in zip(hops[1:], hops[2:]):
            line(img, a, b, CYAN, dash=2, upto=draw_line)
        reticle(img, *vega, 4, PINK)  # the alignment star is ringed, as in the app

        # Act 2 (16-55): Hop hops telescope -> Vega -> stars -> target.  Act 3: sits on target, reticle locks.
        leg_frames = 10
        t = (f - 16) / leg_frames
        if f < 16:
            pos, sprite = hops[0], sprites.BLINK if f in (6, 7) else sprites.SIT
        elif t < len(hops) - 1:
            leg = int(t)
            lt = t - leg
            pos = hop_arc(hops[leg], hops[leg + 1], lt, 10)
            sprite = sprites.CROUCH if lt < 0.15 else sprites.LEAP
        else:
            pos, sprite = (target[0], target[1] + 24), sprites.BLINK if f in (64, 65) else sprites.SIT
            reticle(img, *target, 9 + (f % 4 == 0), CYAN)

        hills(img)
        telescope(img)
        # Shooting star (frames 30-40).
        if 30 <= f <= 40:
            k = f - 30
            head = (170 - k * 6, 6 + k * 2)
            line(img, head, (head[0] + 9, head[1] - 3), (255, 240, 200))

        text(img, "ASTROFIXXER", (W - 66) // 2, 75, AMBER, shadow=(60, 30, 10))
        sprites.draw(img, sprite, int(pos[0]) - 8, int(pos[1]) - 14)
        frames.append(img.resize((W * SCALE, H * SCALE), Image.NEAREST))
    save(frames, "hero.gif")


def idle():
    """Hop on a little moon rock, blinking and bobbing: 48 x 40 px, for the "Meet Hop" section."""
    frames = []
    rnd = random.Random(3)
    stars = [(rnd.randrange(48), rnd.randrange(26), rnd.random() * 6.28) for _ in range(18)]
    for f in range(24):
        img = Image.new("RGB", (48, 40), SKY_MID)
        px = img.load()
        for x, y, ph in stars:
            px[x, y] = lerp(SKY_MID, WHITE, 0.4 + 0.6 * abs(math.sin(f / 24 * math.pi * 2 + ph)))
        for x in range(48):  # moon rock
            for y in range(33, 40):
                if (x - 24) ** 2 / 400 + (y - 40) ** 2 / 64 < 1:
                    px[x, y] = (150, 150, 165) if (x * 7 + y * 3) % 11 else (115, 115, 130)
        bob = 1 if f % 12 >= 6 else 0
        sprite = sprites.BLINK if f in (9, 10) else sprites.SIT
        sprites.draw(img, sprite, 16, 20 + bob)
        if f % 8 < 4:  # headlamp flicker
            px[24, 19 + bob] = (255, 120, 120)
        frames.append(img.resize((48 * 6, 40 * 6), Image.NEAREST))
    save(frames, "hop.gif")


def save(frames, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    # One palette for the whole animation (built from a strip of all frames) so colours don't shift between frames.
    strip = Image.new("RGB", (frames[0].width, frames[0].height * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * f.height))
    palette = strip.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.Dither.NONE)
    pal = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=FRAME_MS, loop=0, optimize=False, disposal=1)
    print(f"{path}: {len(frames)} frames, {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    banner()
    idle()
    import cosmos  # galaxy, planets and divider; imported here because it uses this module's helpers
    cosmos.spiral_galaxy()
    cosmos.planets()
    cosmos.divider()
    import guide_art
    guide_art.build_all()
    import walkthrough
    walkthrough.build()

"""
Pixel-art explainers for the README guide: how alignment works, how plate solving works, how next-star guidance works,
and a small banner. Run through make_art.py (python3 tools/repo-art/make_art.py).
Writes docs/art/guide-banner.gif, align-how.gif, solve-how.gif and next-star.gif.

These are drawings, not screenshots. They show the idea with simple shapes (a telescope, a phone, a few stars).
The real app screens are in docs/screens/new/. Everything is seeded, so the files are the same on every run.
"""
import math
import os
import random

from PIL import Image, ImageDraw

import sprites
from make_art import AMBER, CYAN, PINK, SKY_LOW, SKY_MID, SKY_TOP, WHITE, lerp, OUT

W, H, SCALE = 200, 100, 4
GREEN = (92, 196, 92)
DIM = (70, 86, 120)
INK = (8, 12, 26)          # panel background
PANEL_EDGE = (74, 98, 150)
SKIN = (255, 214, 170)
SKIN_EDGE = (120, 70, 50)
TUBE = (230, 232, 240)
TUBE_SHADE = (150, 155, 170)

# A 5x7 pixel font: letters, digits and the few marks the captions need.
_F = {
    'A': "01110 10001 10001 11111 10001 10001 10001", 'B': "11110 10001 10001 11110 10001 10001 11110",
    'C': "01110 10001 10000 10000 10000 10001 01110", 'D': "11110 10001 10001 10001 10001 10001 11110",
    'E': "11111 10000 10000 11110 10000 10000 11111", 'F': "11111 10000 10000 11110 10000 10000 10000",
    'G': "01110 10001 10000 10111 10001 10001 01110", 'H': "10001 10001 10001 11111 10001 10001 10001",
    'I': "11111 00100 00100 00100 00100 00100 11111", 'J': "00111 00010 00010 00010 00010 10010 01100",
    'K': "10001 10010 10100 11000 10100 10010 10001", 'L': "10000 10000 10000 10000 10000 10000 11111",
    'M': "10001 11011 10101 10101 10001 10001 10001", 'N': "10001 11001 10101 10011 10001 10001 10001",
    'O': "01110 10001 10001 10001 10001 10001 01110", 'P': "11110 10001 10001 11110 10000 10000 10000",
    'Q': "01110 10001 10001 10001 10101 10010 01101", 'R': "11110 10001 10001 11110 10100 10010 10001",
    'S': "01111 10000 10000 01110 00001 00001 11110", 'T': "11111 00100 00100 00100 00100 00100 00100",
    'U': "10001 10001 10001 10001 10001 10001 01110", 'V': "10001 10001 10001 10001 10001 01010 00100",
    'W': "10001 10001 10001 10101 10101 11011 10001", 'X': "10001 10001 01010 00100 01010 10001 10001",
    'Y': "10001 10001 01010 00100 00100 00100 00100", 'Z': "11111 00001 00010 00100 01000 10000 11111",
    '0': "01110 10001 10011 10101 11001 10001 01110", '1': "00100 01100 00100 00100 00100 00100 01110",
    '2': "01110 10001 00001 00110 01000 10000 11111", '3': "11110 00001 00001 01110 00001 00001 11110",
    '4': "00010 00110 01010 10010 11111 00010 00010", '5': "11111 10000 11110 00001 00001 10001 01110",
    '6': "00110 01000 10000 11110 10001 10001 01110", '7': "11111 00001 00010 00100 01000 01000 01000",
    '8': "01110 10001 10001 01110 10001 10001 01110", '9': "01110 10001 10001 01111 00001 00010 01100",
    '.': "0 0 0 0 0 0 1", ':': "0 0 1 0 1 0 0", '+': "00000 00100 00100 11111 00100 00100 00000",
    '-': "000 000 000 111 000 000 000", '°': "010 101 010 000 000 000 000", '!': "1 1 1 1 1 0 1",
    ',': "0 0 0 0 0 1 1", '?': "01110 10001 00001 00110 00100 00000 00100", '/': "00001 00001 00010 00100 01000 10000 10000", ' ': "00 00 00 00 00 00 00",
}
FONT = {k: v.split() for k, v in _F.items()}


def text_width(s):
    return sum(len(FONT[c][0]) + 1 for c in s) - 1


def text(d, s, x, y, color, shadow=None):
    """Draws `s` with its top-left corner at (x, y); returns the x after the last letter."""
    for ch in s:
        g = FONT[ch]
        for yy, row in enumerate(g):
            for xx, bit in enumerate(row):
                if bit == "1":
                    if shadow:
                        d.point((x + xx + 1, y + yy + 1), fill=shadow)
                    d.point((x + xx, y + yy), fill=color)
        x += len(g[0]) + 1
    return x


def text_centered(d, s, cx, y, color, shadow=None):
    text(d, s, cx - text_width(s) // 2, y, color, shadow)


def gradient(h=H, w=W):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        c = lerp(SKY_TOP, SKY_MID, t / 0.6) if t < 0.6 else lerp(SKY_MID, SKY_LOW, (t - 0.6) / 0.4)
        d.line([(0, y), (w, y)], fill=c)
    return img


def backdrop(seed, f, frames, h=H, w=W, n=70):
    """Night sky with gently twinkling stars; `f / frames` loops the twinkle seamlessly."""
    img = gradient(h, w)
    rnd = random.Random(seed)
    px = img.load()
    for _ in range(n):
        x, y, b, ph = rnd.randrange(w), rnd.randrange(h), rnd.random() * 0.7 + 0.2, rnd.random() * 6.28
        px[x, y] = lerp(px[x, y], WHITE, b * (0.5 + 0.5 * math.sin(f / frames * 2 * math.pi * 2 + ph)))
    return img


def caption_bar(img, s, color=WHITE, sub=None):
    """A dark strip along the bottom with a centred caption."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, H - 13, W, H], fill=(6, 9, 20))
    d.line([(0, H - 13), (W, H - 13)], fill=PANEL_EDGE)
    text_centered(d, s, W // 2, H - 10, color)


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def seg(f, a, b):
    """Progress 0..1 of frame f between frames a and b."""
    return 0.0 if f <= a else 1.0 if f >= b else (f - a) / (b - a)


def ring(d, c, r, color):
    d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=color)


def plus(d, c, r, color, gap=1):
    x, y = round(c[0]), round(c[1])
    for a, b in ((x - r, x - gap), (x + gap, x + r)):
        d.line([(a, y), (b, y)], fill=color)
    for a, b in ((y - r, y - gap), (y + gap, y + r)):
        d.line([(x, a), (x, b)], fill=color)


def star_dot(img, x, y, b=1.0, big=False, color=WHITE):
    px = img.load()
    x, y = int(round(x)), int(round(y))
    if 0 <= x < img.width and 0 <= y < img.height:
        px[x, y] = lerp(px[x, y], color, b)
        if big:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < img.width and 0 <= y + dy < img.height:
                    px[x + dx, y + dy] = lerp(px[x + dx, y + dy], color, b * 0.55)


def finger(img, x, y):
    """A pointing fingertip whose tip is at (x, y)."""
    rows = ["...KK...", "..KSSK..", "..KSSK..", "..KSSK..", ".KKSSKK.", "KSSSSSSK", "KSSSSSSK", ".KSSSSK.", "..KKKK.."]
    px = img.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            X, Y = int(x) - 4 + i, int(y) + j
            if ch != "." and 0 <= X < img.width and 0 <= Y < img.height:
                px[X, Y] = SKIN if ch == "S" else SKIN_EDGE


def check(d, cx, cy, s, color):
    """A thick tick mark about 2*s wide."""
    pts = [(cx - s, cy), (cx - s // 3, cy + s * 2 // 3), (cx + s, cy - s * 2 // 3)]
    for w in (-1, 0, 1):
        d.line([(pts[0][0], pts[0][1] + w), (pts[1][0], pts[1][1] + w), (pts[2][0], pts[2][1] + w)], fill=color)


def dotted(d, a, b, color, upto=1.0, step=3):
    n = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
    for i in range(int(n * upto) + 1):
        if (i // step) % 2 == 0:
            d.point((round(a[0] + (b[0] - a[0]) * i / n), round(a[1] + (b[1] - a[1]) * i / n)), fill=color)


def panel(d, box, fill=INK, edge=PANEL_EDGE):
    d.rectangle(box, fill=fill, outline=edge)


def scale_up(img):
    return img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)


def save_gif(frames, name, ms):
    """One shared palette for the whole animation. `ms` is one duration for all frames, or a list, one per frame."""
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    durations = ms if isinstance(ms, list) else [ms] * len(frames)
    strip = Image.new("RGB", (frames[0].width, frames[0].height * len(frames)))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * fr.height))
    palette = strip.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.Dither.NONE)
    pal = [fr.quantize(palette=palette, dither=Image.Dither.NONE) for fr in frames]
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=durations, loop=0, optimize=False, disposal=1)
    print(f"{path}: {len(frames)} frames, {os.path.getsize(path) // 1024} KB")


# ------------------------------------------------------------------------------------------------------------------
# 1. How alignment works
# ------------------------------------------------------------------------------------------------------------------

def _tube(d, pivot, deg, length=74, width=9):
    """A telescope tube tilted `deg` above the horizontal, pointing up and to the right; returns its axis vectors."""
    a = math.radians(deg)
    ux, uy = math.cos(a), -math.sin(a)        # along the tube, toward the front
    nx, ny = -uy, ux                           # across the tube (points down-right of the axis)
    px, py = pivot
    back, front = -12, length
    hw = width / 2

    def pt(s, t):
        return (px + ux * s + nx * t, py + uy * s + ny * t)

    d.polygon([pt(back, -hw), pt(front, -hw), pt(front, hw), pt(back, hw)], fill=TUBE, outline=TUBE_SHADE)
    d.polygon([pt(back, hw - 2), pt(front, hw - 2), pt(front, hw), pt(back, hw)], fill=TUBE_SHADE)
    d.polygon([pt(front - 4, -hw - 1), pt(front, -hw - 1), pt(front, hw + 1), pt(front - 4, hw + 1)], fill=(60, 60, 70))  # front ring
    d.polygon([pt(back - 6, -2), pt(back, -2), pt(back, 2), pt(back - 6, 2)], fill=(60, 60, 70))  # eyepiece
    return pt


def _stand(d, pivot):
    x, y = pivot
    for dx in (-14, 0, 14):
        d.line([(x, y + 4), (x + dx, y + 16)], fill=(120, 80, 40))
    d.ellipse([x - 3, y - 2, x + 3, y + 4], fill=(150, 100, 50))


def _phone_on_tube(d, pt, s0=22, s1=46):
    t0 = -4.5 - 1
    d.polygon([pt(s0, t0 - 5), pt(s1, t0 - 5), pt(s1, t0 + 1), pt(s0, t0 + 1)], fill=(18, 18, 24), outline=(90, 100, 120))
    d.polygon([pt(s0 + 2, t0 - 4), pt(s1 - 2, t0 - 4), pt(s1 - 2, t0 - 1), pt(s0 + 2, t0 - 1)], fill=CYAN)


def _map_stars(seed=5, n=90):
    """Stars of the phone's map, in map pixels relative to Vega. The Lyra parallelogram is hand-placed."""
    rnd = random.Random(seed)
    stars = [(rnd.uniform(-70, 70), rnd.uniform(-90, 90), rnd.random() * 0.6 + 0.3) for _ in range(n)]
    lyra = [(3, 9), (9, 12), (7, 20), (1, 17)]
    return stars, lyra


def align_how():
    frames, total = [], 72
    stars, lyra = _map_stars()
    sx0, sy0, sx1, sy1 = 126, 8, 178, 76     # the phone's screen
    plus_c = ((sx0 + sx1) // 2, (sy0 + sy1) // 2)
    start = (15.0, -10.0)                     # where Vega sits on the map before the drag, relative to the +
    pivot = (34, 66)
    ec, er = (26, 25), 15                     # the eyepiece view
    eye_rnd = random.Random(9)
    eye_stars = [(eye_rnd.uniform(-er + 3, er - 3), eye_rnd.uniform(-er + 3, er - 3)) for _ in range(14)]
    for f in range(total):
        img = backdrop(41, f, total)
        d = ImageDraw.Draw(img)
        s1 = ease(seg(f, 6, 22))    # step 1 (frames 6-22): the telescope is moved until Vega is centred in the eyepiece
        s2 = ease(seg(f, 28, 50))   # step 2 (frames 28-50): a finger drags the map until Vega is under the +
        done = f >= 52
        d.rectangle([0, 82, W, H], fill=(7, 16, 10))
        d.line([(0, 82), (W, 82)], fill=(59, 90, 58))
        _stand(d, pivot)
        pt = _tube(d, pivot, 27 + 6 * s1)
        _phone_on_tube(d, pt)
        # the eyepiece view: a small circle
        d.ellipse([ec[0] - er, ec[1] - er, ec[0] + er, ec[1] + er], fill=(3, 4, 10), outline=AMBER if 4 <= f < 26 else (200, 205, 220))
        for ox, oy in eye_stars:
            if ox * ox + oy * oy < (er - 3) ** 2:
                d.point((round(ec[0] + ox - 5 * s1), round(ec[1] + oy + 4 * s1)), fill=DIM)
        star_dot(img, ec[0] + 8 * (1 - s1), ec[1] - 6 * (1 - s1), 1.0, True, (200, 225, 255))  # Vega slides to the middle
        text_centered(d, "EYEPIECE", ec[0], 2, (200, 205, 220))
        # the phone
        d.rounded_rectangle([sx0 - 4, sy0 - 4, sx1 + 4, sy1 + 4], radius=4, fill=(24, 26, 34), outline=AMBER if 26 <= f < 52 else (120, 130, 150))
        vx, vy = plus_c[0] + start[0] * (1 - s2), plus_c[1] + start[1] * (1 - s2)
        inner = Image.new("RGB", (sx1 - sx0 + 1, sy1 - sy0 + 1), (5, 8, 18))
        idr = ImageDraw.Draw(inner)
        ox, oy = vx - sx0, vy - sy0
        for x, y, b in stars:
            star_dot(inner, ox + x, oy + y, b)
        pts = [(ox + x, oy + y) for x, y in lyra]
        for p, q in zip([(ox, oy)] + pts, pts + [pts[0]]):
            idr.line([p, q], fill=(60, 90, 130))
        for p in pts:
            star_dot(inner, p[0], p[1], 0.9, True)
        star_dot(inner, ox, oy, 1.0, True, (200, 225, 255))
        ring(idr, (round(ox), round(oy)), 4, PINK)
        img.paste(inner, (sx0, sy0))
        d = ImageDraw.Draw(img)
        plus(d, plus_c, 6, GREEN if done else CYAN)
        ring(d, plus_c, 9, GREEN if done else (0, 120, 140))
        if 26 <= f < 52:   # the finger drags from where Vega was toward the +, and the map follows it
            p0 = (plus_c[0] + start[0] + 4, plus_c[1] + start[1] + 10)
            p1 = (plus_c[0] + 4, plus_c[1] + 10)
            finger(img, p0[0] + (p1[0] - p0[0]) * s2, p0[1] + (p1[1] - p0[1]) * s2)
        if f < 28:
            caption_bar(img, "1 MOVE THE TELESCOPE", AMBER if f >= 4 else WHITE)
        elif not done:
            caption_bar(img, "2 NOW DRAG ONLY THE MAP", AMBER)
        else:
            caption_bar(img, "ALIGNED ON VEGA", GREEN)
            if f >= 54:
                check(ImageDraw.Draw(img), 152, 62, 9 if f >= 56 else 6, GREEN)
        frames.append(scale_up(img))
    ms = [90] * total
    ms[-1] = 1000  # hold the finished picture
    save_gif(frames, "align-how.gif", ms)


# ------------------------------------------------------------------------------------------------------------------
# 2. How plate solving works
# ------------------------------------------------------------------------------------------------------------------

def _solve_field():
    """Sky stars in tangent-plane units, with magnitudes: the photo sees a rotated, zoomed-in part of them."""
    rnd = random.Random(23)
    return [(rnd.uniform(-2.3, 2.3), rnd.uniform(-2.3, 2.3), rnd.choice([1, 2, 2, 3, 3, 3, 4, 4, 4, 4])) for _ in range(220)]


def _area(a, b, c):
    return abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2


def _triangles(stars, count):
    """Well-shaped small triangles of neighbouring stars that share no star (what a solver matches)."""
    used, out = set(), []
    for i in range(len(stars)):
        if i in used:
            continue
        near = sorted((j for j in range(len(stars)) if j != i and j not in used),
                      key=lambda j: math.hypot(stars[i][0] - stars[j][0], stars[i][1] - stars[j][1]))[:6]
        best = None
        for x in range(len(near)):
            for y in range(x + 1, len(near)):
                t = (stars[i], stars[near[x]], stars[near[y]])
                per = sum(math.hypot(t[k][0] - t[(k + 1) % 3][0], t[k][1] - t[(k + 1) % 3][1]) for k in range(3))
                if _area(*[p[:2] for p in t]) >= 0.05 and (best is None or per < best[0]):
                    best = (per, (i, near[x], near[y]))
        if best:
            out.append(best[1])
            used.update(best[1])
        if len(out) == count:
            break
    return out


# The camera solve is told in five beats: (1) the phone sits on the eyepiece, (2) it takes a 1 to 4 second photo, (3) the app
# matches the star pattern in the photo against the star list on the phone (no internet), (4) it knows where it points,
# (5) "Apply to alignment" only works when the phone did not move. Frame numbers of each beat:
SOLVE_SLIDE = (51, 59)      # the photo panel slides left, the star list slides in
SOLVE_RINGS = (60, 72)
SOLVE_TRI = (73, 93)
SOLVE_POINT = (101, 112)
SOLVE_WIPE = (119, 126)     # a wipe to the "Apply to alignment" screen
SOLVE_TOTAL = 178


def _solve_ground(d):
    d.rectangle([0, 82, W, 86], fill=(7, 16, 10))
    d.line([(0, 82), (W, 82)], fill=(59, 90, 58))


def _solve_scope(d, dx=0, dy=0, deg=45, phone_in=1.0, lit=True):
    """The telescope with the phone on the eyepiece, its camera looking in. `phone_in` 0..1 slides the phone on."""
    pivot = (56 + dx, 54 + dy)
    ground = 82
    for leg in (-16, 0, 16):   # tripod
        d.line([(pivot[0], pivot[1] + 4), (pivot[0] + leg, ground)], fill=(120, 80, 40))
    d.ellipse([pivot[0] - 3, pivot[1] - 2, pivot[0] + 3, pivot[1] + 4], fill=(150, 100, 50))
    pt = _tube(d, pivot, deg, length=48, width=9)
    gap = 4 + 22 * (1 - phone_in)      # how far the phone still is from the eyepiece
    s_cam, s_back = -19 - gap, -26 - gap
    d.polygon([pt(s_back, -9), pt(s_cam, -9), pt(s_cam, 9), pt(s_back, 9)], fill=(18, 18, 24), outline=(110, 120, 145))
    d.line([pt(s_back - 0.5, -8), pt(s_back - 0.5, 8)], fill=CYAN)
    d.line([pt(s_back - 1.5, -8), pt(s_back - 1.5, 8)], fill=CYAN)           # its screen faces away from the telescope
    cx, cy = pt(s_cam, 0)
    d.rectangle([round(cx) - 1, round(cy) - 1, round(cx), round(cy)], fill=AMBER if lit else (120, 120, 120))  # its camera
    return pt


def solve_how():
    frames, total = [], SOLVE_TOTAL
    sky = _solve_field()
    roll = math.radians(34)
    ppx = 37
    rnd = random.Random(2)
    box_cat = [103, 6, 195, 80]
    box_photo0 = [5, 6, 101, 80]
    ccx, ccy, cpx = 151, 45, 21
    c_, s_ = math.cos(roll), math.sin(roll)

    def inside(p, b, m=3):
        return b[0] + m <= p[0] <= b[2] - m and b[1] + m <= p[1] <= b[3] - m

    # The stars the solver finds, chosen against the panel in its final place (left).
    pcx0, pcy0 = 53, 43

    def to_photo0(u, v):
        return (pcx0 + (u * c_ - v * s_) * ppx, pcy0 + (u * s_ + v * c_) * ppx)

    def to_cat(u, v):
        return (ccx + u * cpx, ccy + v * cpx)

    seen = sorted((st for st in sky if st[2] <= 3 and inside(to_photo0(*st[:2]), box_photo0, 6)), key=lambda st: st[2])
    detected = []
    for st in seen:
        if all(math.hypot(st[0] - o[0], st[1] - o[1]) > 0.16 for o in detected):
            detected.append(st)
    detected = detected[:12]
    hot = [(rnd.randrange(8, 98), rnd.randrange(9, 78)) for _ in range(9)]
    tri = _triangles(detected, 3)
    cols = [CYAN, PINK, (255, 224, 138), GREEN]
    corners = []
    for x, y in ((box_photo0[0], box_photo0[1]), (box_photo0[2], box_photo0[1]), (box_photo0[2], box_photo0[3]), (box_photo0[0], box_photo0[3])):
        px_, py_ = (x - pcx0) / ppx, (y - pcy0) / ppx
        corners.append(to_cat(px_ * c_ + py_ * s_, -px_ * s_ + py_ * c_))

    def photo_scene(f):
        img = backdrop(57, 0, total, n=40)
        d = ImageDraw.Draw(img)
        slide = ease(seg(f, *SOLVE_SLIDE))
        shift = 95 * (1 - slide)                      # the photo panel starts on the right, beside the telescope
        box_p = [box_photo0[0] + shift, box_photo0[1], box_photo0[2] + shift, box_photo0[3]]
        pcx = pcx0 + shift

        def to_photo(u, v):
            return (pcx + (u * c_ - v * s_) * ppx, pcy0 + (u * s_ + v * c_) * ppx)

        # The telescope scene on the left; it slides away as the panel moves over it.
        tele_dx = -105 * slide
        if slide < 1:
            _solve_ground(d)
            phone_in = 1.0
            _solve_scope(d, dx=tele_dx, phone_in=phone_in)
            if f >= 12 and f < SOLVE_SLIDE[0] and tele_dx == 0:
                text(d, "PHONE", 4, 56, (200, 205, 220))
                d.line([(16, 64), (28, 72)], fill=(200, 205, 220))
            if 25 <= f < SOLVE_SLIDE[0]:
                text(d, "HOLD STILL", 6, 6, AMBER)

        panel(d, [round(v) for v in box_p])
        live = ease(seg(f, 8, 14))
        exposing = seg(f, 25, 49)
        # What the camera shows: dim and live, then the exposure builds up (stars brighten, noise appears).
        if f < 25:
            a_photo = 0.25 * live
        elif f < 50:
            a_photo = 0.25 + 0.75 * ease(exposing)
        else:
            a_photo = 1.0
        flash = 25 <= f < 27
        for u, v, m in sky:
            p = to_photo(u, v)
            if m <= 4 and inside(p, box_p) and not (p[1] < box_p[1] + 12 and (p[0] < box_p[0] + 38 or p[0] > box_p[2] - 24)):
                j = (math.sin(u * 91 + v * 37) * 0.3, math.cos(u * 53 - v * 71) * 0.3)
                star_dot(img, p[0] + j[0], p[1] + j[1], a_photo * (1.0 if m <= 2 else 0.75 if m == 3 else 0.4), m <= 2)
        for hx, hy in hot:
            if exposing > 0.3 or f >= 50:
                star_dot(img, hx + shift, hy, a_photo * 0.3, False, (255, 140, 140))
        if flash:
            d2 = ImageDraw.Draw(img)
            d2.rectangle([round(box_p[0]) + 1, box_p[1] + 1, round(box_p[2]) - 1, box_p[3] - 1], outline=WHITE)
        d = ImageDraw.Draw(img)
        label = "CAMERA" if f < 25 else "PHOTO"
        text(d, label, round(box_p[0]) + 3, box_p[1] + 3, (150, 170, 210))
        if 25 <= f < SOLVE_SLIDE[0]:   # the exposure timer
            secs = 1 + int(exposing * 2.999)
            text(d, f"{secs} S", round(box_p[2]) - 20, box_p[1] + 3, AMBER)
            bw = int((box_p[2] - box_p[0] - 8) * exposing)
            d.rectangle([round(box_p[0]) + 4, box_p[3] - 6, round(box_p[0]) + 4 + bw, box_p[3] - 4], fill=AMBER)
            d.rectangle([round(box_p[0]) + 4, box_p[3] - 6, round(box_p[2]) - 4, box_p[3] - 4], outline=(120, 90, 40))

        # The star list on the phone, sliding in from the right.
        if slide > 0:
            cdx = round(100 * (1 - slide))
            bc = [box_cat[0] + cdx, box_cat[1], box_cat[2] + cdx, box_cat[3]]
            panel(d, bc)
            for u, v, m in sky:
                p = to_cat(u, v)
                if inside(p, box_cat) and not (p[1] < box_cat[1] + 12 and p[0] < box_cat[0] + 56):
                    star_dot(img, p[0] + cdx, p[1], slide * (1.0 if m <= 2 else 0.75 if m == 3 else 0.5), m <= 2, (190, 220, 255))
            d = ImageDraw.Draw(img)
            text(d, "STAR LIST", bc[0] + 3, bc[1] + 3, (150, 170, 210))
        else:
            cdx = 100
        if f < SOLVE_SLIDE[1]:
            return img
        # rings, then the triangles, then the pointing
        nring = int(12 * seg(f, *SOLVE_RINGS) + 0.001)
        for i, (u, v, m) in enumerate(detected):
            if i < nring:
                ring(d, [round(c) for c in to_photo(u, v)], 3, AMBER if f < SOLVE_TRI[0] else (130, 105, 60))
        shown = int(seg(f, *SOLVE_TRI) * len(tri) + 0.999) if f >= SOLVE_TRI[0] else 0
        for k in range(min(shown, len(tri))):
            now = k == shown - 1 and f < SOLVE_TRI[1] + 2
            col = cols[k] if now else lerp(cols[k], INK, 0.35 if f < SOLVE_TRI[1] + 4 else 0.6)
            for to in (to_photo, to_cat):
                ps = [to(*detected[i][:2]) for i in tri[k]]
                for p, q in zip(ps, ps[1:] + ps[:1]):
                    d.line([(round(p[0]), round(p[1])), (round(q[0]), round(q[1]))], fill=col)
                if now:
                    for p in ps:
                        d.rectangle([round(p[0]) - 1, round(p[1]) - 1, round(p[0]) + 1, round(p[1]) + 1], outline=col)
        if f >= SOLVE_POINT[0]:
            s4 = ease(seg(f, *SOLVE_POINT))
            for p, q in zip(corners, corners[1:] + corners[:1]):
                d.line([(round(p[0]), round(p[1])), (round(p[0] + (q[0] - p[0]) * s4), round(p[1] + (q[1] - p[1]) * s4))], fill=CYAN)
            plus(d, (pcx, pcy0), 4 + int(6 * s4), CYAN, gap=2)
            ring(d, (pcx, pcy0), 12 - int(4 * s4), CYAN)
            plus(d, (ccx, ccy), 5, CYAN, gap=2)
            if f >= SOLVE_POINT[1]:
                text(d, "HERE", ccx - text_width("HERE") // 2, ccy + 10, CYAN, shadow=(0, 40, 50))
        return img

    def apply_scene(f):
        """f counts from the first frame of this screen. 0-23 the phone moved; 24+ it stayed still."""
        img = backdrop(57, 0, total, n=40)
        d = ImageDraw.Draw(img)
        _solve_ground(d)
        moved = f < 24
        if moved:
            jit = [(0, 0), (2, 1), (-2, 0), (1, -2), (-1, 2), (2, -1), (-2, 1), (1, 1)][f % 8]
            _solve_scope(d, dx=jit[0], dy=jit[1], deg=45 + (2 if f % 2 else -2))
            for i in range(3):   # motion marks beside the phone
                d.line([(26 - i * 3, 66 + i * 4), (26 - i * 3, 69 + i * 4)], fill=(255, 110, 110))
            text(d, "MOVED!", 6, 6, (255, 110, 110))
        else:
            _solve_scope(d)
            text(d, "STILL", 6, 6, GREEN)
        # the result screen of the phone
        box = [108, 6, 192, 80]
        d.rounded_rectangle(box, radius=4, fill=INK, outline=(120, 130, 150))
        text(d, "SOLVED", 114, 11, GREEN)
        plus(d, (178, 14), 4, CYAN, gap=1)
        text(d, "POINTING HERE", 114, 22, CYAN)
        # a mini star field with the pointing
        for k, (sx, sy) in enumerate([(120, 36), (150, 33), (170, 40), (131, 45), (160, 28), (183, 31), (141, 39)]):
            star_dot(img, sx, sy, 0.9, k % 3 == 0, (190, 220, 255))
        # the button
        btn = [113, 52, 187, 75]
        tap = 36 <= f < 40
        if moved:
            fill, edge, tc = (34, 38, 50), (74, 80, 100), (92, 100, 124)
        else:
            fill, edge, tc = ((60, 150, 80) if tap else (30, 104, 56)), GREEN, WHITE
        d.rounded_rectangle(btn, radius=3, fill=fill, outline=edge)
        text_centered(d, "APPLY TO", (btn[0] + btn[2]) // 2, btn[1] + 4, tc)
        text_centered(d, "ALIGNMENT", (btn[0] + btn[2]) // 2, btn[1] + 13, tc)
        text(d, "RETAKE" if moved else "STILL", 114, 41, (255, 110, 110) if moved else GREEN)
        if 30 <= f < 44:   # a finger taps the button
            tf = ease(seg(f, 30, 36))
            finger(img, 150 + 10 * (1 - tf), 62 + 14 * (1 - tf))
        if f >= 42:
            check(d, 174, 45, 6 if f >= 44 else 4, GREEN)
        return img

    wipe_a, wipe_b = SOLVE_WIPE
    apply_start = wipe_a + 2
    for f in range(total):
        if f < wipe_a:
            img = photo_scene(f)
        elif f <= wipe_b:
            old = photo_scene(min(f, SOLVE_POINT[1] + 7))
            new = apply_scene(0)
            x = int(W * seg(f, wipe_a, wipe_b))
            img = old.copy()
            img.paste(new.crop((0, 0, x, H)), (0, 0))
            ImageDraw.Draw(img).line([(x, 0), (x, H)], fill=AMBER)
        else:
            img = apply_scene(f - wipe_b - 1)
        if f < 25:
            cap = ("1 PHONE ON THE EYEPIECE", WHITE)
        elif f < SOLVE_SLIDE[0]:
            cap = ("2 TAKE A 1-4 S PHOTO", AMBER)
        elif f < SOLVE_TRI[0]:
            cap = ("3 MATCH THE STAR PATTERN", AMBER)
        elif f < SOLVE_TRI[1] + 6:
            cap = ("3 TRIANGLES VS THE STAR LIST", AMBER)
        elif f < SOLVE_POINT[0]:
            cap = ("OFFLINE. ON THE PHONE.", WHITE)
        elif f < wipe_a:
            cap = ("4 POINTING HERE", GREEN)
        else:
            k = f - wipe_b - 1
            cap = ("5 MOVED? RETAKE IT", (255, 110, 110)) if k < 24 else ("5 STILL? APPLY IT", GREEN) if k < 44 else ("ALIGNED FROM A PHOTO", GREEN)
        assert text_width(cap[0]) <= W - 8, cap
        caption_bar(img, cap[0], cap[1])
        frames.append(scale_up(img))
    ms = [90] * total
    ms[SOLVE_SLIDE[0] - 1] = 250       # let the finished photo register
    ms[SOLVE_POINT[1] + 6] = 600       # "pointing here"
    ms[-1] = 1500
    save_gif(frames, "solve-how.gif", ms)


# ------------------------------------------------------------------------------------------------------------------
# 3. Next-star guidance
# ------------------------------------------------------------------------------------------------------------------

def _arrow(d, c, ang, length, color, head=5, w=2):
    """An arrow centred on c, pointing at ang (degrees, 0 = right, 90 = down), `length` long."""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    tail = (c[0] - ux * length / 2, c[1] - uy * length / 2)
    tip = (c[0] + ux * length / 2, c[1] + uy * length / 2)
    d.line([tail, tip], fill=color, width=w)
    h = min(head, max(2, length * 0.6))
    for s in (-1, 1):
        b = a + math.radians(150 * s)
        d.line([tip, (tip[0] + math.cos(b) * h, tip[1] + math.sin(b) * h)], fill=color, width=w)


def next_star():
    frames, total = [], 80
    far, close, on = 12.0, 3.25, 0.54
    pos0, target = (30.0, 66.0), (88.0, 22.0)    # the telescope starts here and ends on the target
    sky_box = [5, 6, 101, 80]
    card = [106, 6, 195, 80]
    rnd = random.Random(14)
    bg = [(rnd.randrange(8, 99), rnd.randrange(9, 78), rnd.random() * 0.6 + 0.25) for _ in range(40)]
    vega = (34, 62)
    ang = math.degrees(math.atan2(target[1] - pos0[1], target[0] - pos0[0]))
    for f in range(total):
        img = backdrop(63, f, total, n=40)
        d = ImageDraw.Draw(img)
        panel(d, sky_box)
        panel(d, card)
        t = ease(seg(f, 6, 60))
        pos = (pos0[0] + (target[0] - pos0[0]) * t, pos0[1] + (target[1] - pos0[1]) * t)
        dist = far * (1 - t) + 0.2 * t
        for x, y, b in bg:
            star_dot(img, x, y, b)
        d = ImageDraw.Draw(img)
        # the sky with the telescope's + moving toward M57
        dotted(d, vega, target, (0, 150, 175))
        star_dot(img, *vega, 1.0, True, (200, 225, 255))
        ring(d, vega, 4, PINK)
        text(d, "VEGA", vega[0] + 6, vega[1] - 3, PINK)
        ring(d, target, 4, CYAN)
        star_dot(img, *target, 0.8)
        text(d, "M57", target[0] - 8, target[1] + 9, CYAN)
        state = "on" if dist <= on else "close" if dist <= close else "far"
        col = AMBER if state == "close" else CYAN
        plus(d, (round(pos[0]), round(pos[1])), 5, col)
        ring(d, (round(pos[0]), round(pos[1])), 7, col if state != "far" else (0, 120, 140))
        # the guidance card
        c = (card[0] + 26, 35)
        ring(d, c, 20, (40, 60, 100))
        ring(d, c, 11, (40, 60, 100))
        if state == "on":
            r = 4 + int(6 * ease(seg(f, 58, 64)))
            d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=CYAN)
            if f >= 64 and (f // 4) % 2 == 0:
                ring(d, c, 18, CYAN)
        else:
            _arrow(d, c, ang, 30, col)   # the real arrow keeps its size and only turns; the number counts down
        title = {"far": "MOVE TO M57", "close": "CLOSE TO M57", "on": "ON TARGET: M57"}[state]
        text(d, title, card[0] + 5, 62, col)
        d = ImageDraw.Draw(img)
        # the distance counting down
        num = "%.1f" % dist
        # (drawn as "12.0° TO GO"; "°" is a small ring glyph)
        text(d, num + "° TO GO", card[0] + 5, 71, WHITE)
        cap = "1 FOLLOW THE ARROW" if state == "far" else "2 CLOSE: GO SLOWLY" if state == "close" else "3 ON TARGET: LOOK IN THE EYEPIECE"
        caption_bar(img, cap, CYAN if state == "far" else AMBER if state == "close" else GREEN)
        frames.append(scale_up(img))
    ms = [90] * total
    ms[-1] = 1200
    save_gif(frames, "next-star.gif", ms)


# ------------------------------------------------------------------------------------------------------------------
# 4. The banner: Hop visits the four steps
# ------------------------------------------------------------------------------------------------------------------

def _icon_setup(d, x, y, lit):
    """A telescope with a phone on it."""
    c = TUBE if lit else DIM
    d.polygon([(x - 13, y + 6), (x - 11, y + 9), (x + 13, y - 4), (x + 11, y - 7)], fill=c)
    d.rectangle([x - 4, y - 1, x + 3, y + 2], fill=(18, 18, 24), outline=CYAN if lit else DIM)
    d.line([(x - 4, y + 9), (x - 9, y + 17)], fill=(150, 100, 50))
    d.line([(x - 4, y + 9), (x + 1, y + 17)], fill=(150, 100, 50))


def _icon_align(d, x, y, lit):
    c = CYAN if lit else DIM
    plus(d, (x, y + 3), 9, c, gap=3)
    ring(d, (x, y + 3), 11, c)
    d.rectangle([x + 4, y - 1, x + 6, y + 1], fill=PINK if lit else DIM)


def _icon_solve(d, x, y, lit):
    c = AMBER if lit else DIM
    pts = [(x - 10, y + 10), (x + 9, y + 8), (x - 1, y - 8)]
    for a, b in zip(pts, pts[1:] + pts[:1]):
        d.line([a, b], fill=c)
    for p in pts:
        d.rectangle([p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1], fill=WHITE if lit else DIM)


def _icon_go(d, x, y, lit):
    c = GREEN if lit else DIM
    ring(d, (x, y + 3), 11, c)
    ring(d, (x, y + 3), 6, c)
    d.ellipse([x - 2, y + 1, x + 2, y + 5], fill=c)


def banner():
    W2, H2 = 200, 74
    total = 64
    names = [("SET UP", _icon_setup), ("ALIGN", _icon_align), ("SOLVE", _icon_solve), ("GO", _icon_go)]
    xs = [28, 76, 124, 172]
    frames = []
    for f in range(total):
        img = backdrop(77, f, total, h=H2, n=60)
        d = ImageDraw.Draw(img)
        d.rectangle([0, H2 - 8, W2, H2], fill=(7, 16, 10))
        d.line([(0, H2 - 8), (W2, H2 - 8)], fill=(59, 90, 58))
        # Hop waits at the first step, hops to each next one, and rests on the last
        leg_frames = 11
        t = (f - 6) / leg_frames
        if t < 0:
            cur, hop = 0, None
        elif t < 3:
            cur, hop = int(t) + (1 if (t % 1) > 0.5 else 0), (int(t), t % 1)
        else:
            cur, hop = 3, None
        for i, (name, icon) in enumerate(names):
            lit = i <= cur
            d.rectangle([xs[i] - 21, 4, xs[i] + 21, 44], fill=INK, outline=PANEL_EDGE if lit else (36, 46, 72))
            icon(d, xs[i], 12, lit)
            text_centered(d, name, xs[i], 35, WHITE if lit else DIM)
        # dotted guide line along the top of the boxes
        for i in range(3):
            dotted(d, (xs[i] + 22, 26), (xs[i + 1] - 22, 26), CYAN if i < cur else (36, 46, 72), step=2)
        if hop:
            leg, lt = hop
            a, b = (xs[leg], 66), (xs[leg + 1], 66)
            px_, py_ = a[0] + (b[0] - a[0]) * lt, a[1] + (b[1] - a[1]) * lt - 10 * 4 * lt * (1 - lt)
            sprite = sprites.CROUCH if lt < 0.15 else sprites.LEAP
        else:
            px_, py_ = xs[cur], 66
            sprite = sprites.BLINK if f in (2, 3, 59, 60) else sprites.SIT
        sprites.draw(img, sprite, int(px_) - 8, int(py_) - 14)
        frames.append(scale_up(img))
    save_gif(frames, "guide-banner.gif", 90)


def build_all():
    banner()
    align_how()
    solve_how()
    next_star()


if __name__ == "__main__":
    build_all()

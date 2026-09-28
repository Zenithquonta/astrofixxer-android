"""Hop, the AstroFixxer mascot: a pixel-art frog astronomer with a red headlamp (red keeps your night vision)."""

PALETTE = {
    'K': (18, 58, 30),     # outline (dark green, visible on a night sky)
    'k': (10, 10, 20),     # pupils
    'G': (92, 196, 92),    # skin
    'g': (46, 125, 50),    # shade
    'L': (190, 240, 160),  # belly
    'W': (255, 255, 255),  # eye white
    'P': (255, 143, 176),  # cheek
    'R': (255, 48, 48),    # headlamp
    'r': (150, 20, 20),    # headlamp strap
    'Y': (255, 224, 138),  # lamp glow
}

# 16 x 14, sitting.
SIT = """
....KKKKYKKKK...
...KWkkKRKWkkK..
...KWkkKrKWkkK..
..KGWWWKrKWWWGK.
.KGGKKKGGGKKKGGK
.KGPGGGGGGGGGPGK
.KGGGKGGGGGKGGGK
..KGGGKKKKKGGGK.
.KgGLLLLLLLLLGgK
KgGGLLLLLLLLLGGgK
KggKLLLLLLLLLKggK
.KKgKLLLLLLLKgKK.
KgggKKKKKKKKKgggK
.KKK.........KKK.
"""

BLINK = SIT.replace("...KWkkKRKWkkK..", "...KGGGKRKGGGK..").replace("...KWkkKrKWkkK..", "...KKKKKrKKKKK..")

# Crouched, about to jump.
CROUCH = """
................
....KKKKYKKKK...
...KWkkKRKWkkK..
...KWkkKrKWkkK..
..KGWWWKrKWWWGK.
.KGGKKKGGGKKKGGK
.KGPGGGGGGGGGPGK
.KGGGKKKKKKKGGGK
KgGGLLLLLLLLLGGgK
KggKLLLLLLLLLKggK
KggggKKKKKKKgggggK
.KKKKK.....KKKKK.
................
................
"""

# In the air, legs stretched.
LEAP = """
....KKKKYKKKK...
...KWkkKRKWkkK..
...KWkkKrKWkkK..
..KGWWWKrKWWWGK.
.KGGKKKGGGKKKGGK
.KGPGGGGGGGGGPGK
.KGGGKGGGGGKGGGK
..KGGGKKKKKGGGK.
..KgLLLLLLLLLgK.
..KgLLLLLLLLLgK.
...KKLLLLLLLKK..
...KgK.....KgK..
..KggK.....KggK.
..KKK.......KKK.
"""


def parse(art):
    rows = [r for r in art.strip("\n").split("\n")]
    w = max(len(r) for r in rows)
    return [r.ljust(w, '.') for r in rows]


def draw(img, art, x0, y0, flip=False):
    """Pastes a sprite onto a Pillow RGB(A) image at (x0, y0)."""
    px = img.load()
    rows = parse(art)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[::-1] if flip else row):
            if ch == '.':
                continue
            X, Y = x0 + x, y0 + y
            if 0 <= X < img.width and 0 <= Y < img.height:
                c = PALETTE[ch]
                px[X, Y] = c + ((255,) if img.mode == 'RGBA' else ())

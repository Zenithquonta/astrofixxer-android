"""
A slideshow GIF (docs/art/walkthrough.gif) made from the real app screens in docs/screens/new/, with a short caption
beside each one. It is a plain crossfade; nothing is drawn on the screens themselves.
The screens are rendered on a desktop test harness (tools/desktop-check), not photographed on a phone, and the GIF says so.
Run through make_art.py, after readme_screens.py has copied the screens. Skips quietly if they are missing.
"""
import os

from PIL import Image, ImageDraw

from guide_art import AMBER, CYAN, DIM, GREEN, WHITE, gradient, save_gif, text, text_width
from make_art import OUT

SCREENS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "screens", "new")
CW, CH = 600, 580           # canvas
PW, PH = 252, 546           # the screen
PX, PY = 22, 16

# (file, big title, small lines, colour of the small lines)
SLIDES = [
    ("setup-placement", "TELL IT ABOUT YOUR TELESCOPE", ["WHERE THE PHONE SITS", "CHANGES THE MATHS."], WHITE),
    ("align-2-centre", "CENTRE THE STAR", ["MOVE THE TELESCOPE UNTIL", "THE STAR IS IN THE EYEPIECE."], WHITE),
    ("align-3-dragged", "DRAG THE MAP", ["PUT THE STAR UNDER THE +.", "THE TELESCOPE STAYS STILL."], WHITE),
    ("align-4-result", "ALIGNED", ["THE APP NOW KNOWS", "WHERE YOU POINT."], GREEN),
    ("guide-1-arrow", "FOLLOW THE ARROW", ["DEGREES TO GO COUNT DOWN."], WHITE),
    ("guide-2-close", "CLOSE", ["THE ARROW TURNS AMBER."], AMBER),
    ("guide-3-on-target", "ON TARGET", ["THE BULLSEYE FILLS."], CYAN),
    ("camera-3-result", "OR LET THE CAMERA FIND IT", ["PLATE SOLVING, OFFLINE.", "IN VERIFICATION."], AMBER),
    ("updates-available", "UPDATE FROM INSIDE THE APP", ["GITHUB DOWNLOAD ONLY."], WHITE),
]


def wrap(s, width_px):
    lines, cur = [], ""
    for word in s.split():
        t = (cur + " " + word).strip()
        if text_width(t) <= width_px or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = word
    return lines + [cur]


def slide(n, total, name, title, small, colour):
    img = gradient(CH // 2, CW // 2).resize((CW, CH), Image.NEAREST)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([PX - 6, PY - 6, PX + PW + 6, PY + PH + 6], radius=14, fill=(24, 26, 34), outline=(120, 130, 150), width=2)
    shot = Image.open(os.path.join(SCREENS, name + ".png")).convert("RGB").resize((PW, PH), Image.LANCZOS)
    img.paste(shot, (PX, PY))
    # big title, 3x
    big = Image.new("RGBA", (CW // 3, CH // 3), (0, 0, 0, 0))
    bd = ImageDraw.Draw(big)
    tx = (PX + PW + 28) // 3
    y = 40
    text(bd, "%d OF %d" % (n, total), tx, 12, DIM)
    for line in wrap(title, (CW - PX - PW - 50) // 3):
        text(bd, line, tx, y, AMBER if "VERIFICATION" in " ".join(small) else CYAN, shadow=(6, 9, 20))
        y += 11
    img.paste(big.resize((CW, CH), Image.NEAREST), (0, 0), big.resize((CW, CH), Image.NEAREST))
    # small text, 2x
    sm = Image.new("RGBA", (CW // 2, CH // 2), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sm)
    sx = (PX + PW + 28) // 2
    sy = y * 3 // 2 + 10
    room = CW // 2 - 14 - sx
    for line in wrap(" ".join(small), room):
        text(sd, line, sx, sy, colour)
        sy += 10
    foot = wrap("APP SCREENS RENDERED ON A DESKTOP TEST HARNESS. NOT PHOTOS FROM A PHONE.", room)
    fy = CH // 2 - 10 * len(foot) - 8
    sd.line([(sx, fy - 6), (CW // 2 - 14, fy - 6)], fill=(74, 98, 150))
    for line in foot:
        text(sd, line, sx, fy, (150, 170, 210))
        fy += 10
    big2 = sm.resize((CW, CH), Image.NEAREST)
    img.paste(big2, (0, 0), big2)
    return img


def build():
    slides = [s for s in SLIDES if os.path.exists(os.path.join(SCREENS, s[0] + ".png"))]
    if len(slides) < 3:
        print("walkthrough.gif skipped: run readme_screens.py first (screens missing in docs/screens/new)")
        return
    imgs = [slide(i + 1, len(slides), *s) for i, s in enumerate(slides)]
    frames, ms = [], []
    for i, im in enumerate(imgs):
        frames.append(im)
        ms.append(2400)
        nxt = imgs[(i + 1) % len(imgs)]
        for k in (1, 2):
            frames.append(Image.blend(im, nxt, k / 3))
            ms.append(70)
    save_gif(frames, "walkthrough.gif", ms)


if __name__ == "__main__":
    build()

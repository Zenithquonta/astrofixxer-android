"""
Copies the README's app screens from the desktop test harness and shrinks them.

  (cd tools/desktop-check && ./gradlew test --offline --tests ReadmeShotsTest)   # renders build/screens/readme-*.png
  python3 tools/repo-art/readme_screens.py                                     # writes docs/screens/new/*.png

Each screen is a real Compose screen rendered on the desktop at 360 x 780 dp (see ReadmeShotsTest.kt). The script only
resizes them to 480 px wide and stores them as palette PNGs under about 150 KB. It is deterministic.

The camera plate-solve screens (docs/screens/new/camera-*.png) come from ReadmeCameraShotsTest, which waits for the
camera flow to be merged (tools/desktop-check/pending-w4/). Until then, pass the folder where it was run:
  python3 tools/repo-art/readme_screens.py --camera-from /path/to/build/screens
Screens whose source is missing are skipped with a warning; files already in docs/screens/new stay as they are.
Needs Pillow.
"""
import argparse
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
DEFAULT_SRC = os.path.join(ROOT, "tools", "desktop-check", "build", "screens")
DEST = os.path.join(ROOT, "docs", "screens", "new")
WIDTH = 480
MAX_BYTES = 150 * 1024

# destination name -> source name in build/screens (without .png)
MAIN = {
    "setup-type": "readme-wizard-type",
    "setup-mount": "readme-wizard-mount",
    "setup-placement": "readme-wizard-placement",
    "setup-summary": "readme-wizard-summary",
    "telescope-tab": "readme-telescope-tab",
    "align-1-pick": "readme-align-1-pick",
    "align-2-centre": "readme-align-2-centre",
    "align-3-dragged": "readme-align-3-dragged",
    "align-4-result": "readme-align-4-result",
    "guide-1-arrow": "readme-guide-1-arrow",
    "guide-2-close": "readme-guide-2-close",
    "guide-3-on-target": "readme-guide-3-on-target",
    "orient-1-menu": "readme-orient-1-menu",
    "orient-2-question": "readme-orient-2-question",
    "orient-3-result": "readme-orient-3-result",
    "view-more": "readme-view-more",
    "updates-available": "readme-updates-available",
}
CAMERA = {
    "camera-1-arrangement": "readme-solve-1-arrangement",
    "camera-2-live-eyepiece": "readme-solve-2-live-eyepiece",
    "camera-3-result": "readme-solve-3-result",
    "camera-4-aligned": "readme-solve-4-aligned",
    "camera-5-failed": "readme-solve-5-failed",
    "camera-beside-1-arrangement": "readme-solve-camera-1-arrangement",
    "camera-beside-2-live": "readme-solve-camera-2-live",
}


def shrink(src, dest):
    im = Image.open(src).convert("RGB")
    im = im.resize((WIDTH, round(im.height * WIDTH / im.width)), Image.LANCZOS)
    # 256 colours keep the amber and cyan accents true; fewer colours would turn them beige.
    for method, colors in ((Image.Quantize.MEDIANCUT, 256), (Image.Quantize.FASTOCTREE, 256), (Image.Quantize.FASTOCTREE, 128), (Image.Quantize.FASTOCTREE, 64)):
        q = im.quantize(colors=colors, method=method, dither=Image.Dither.NONE)
        q.save(dest, optimize=True)
        if os.path.getsize(dest) <= MAX_BYTES:
            break
    return os.path.getsize(dest)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--from", dest="src", default=DEFAULT_SRC, help="folder with the harness screens (default: tools/desktop-check/build/screens)")
    ap.add_argument("--camera-from", default=None, help="folder with the camera flow's readme-solve-*.png (default: same as --from)")
    a = ap.parse_args()
    os.makedirs(DEST, exist_ok=True)
    for table, src_dir in ((MAIN, a.src), (CAMERA, a.camera_from or a.src)):
        for dest, name in table.items():
            src = os.path.join(src_dir, name + ".png")
            if not os.path.exists(src):
                print(f"skipped {dest}: {src} not found", file=sys.stderr)
                continue
            size = shrink(src, os.path.join(DEST, dest + ".png"))
            print(f"{dest}.png: {size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())

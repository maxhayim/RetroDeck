"""
Turns a skin's Blender renders into the app's images.

    python3 tools/blender/finish.py <render dir> <skin id>

Night images get the bloom a camera sees around backlit legends, or, when the skin has its legends
as a separate glow layer for the app to tint, that layer does; then every image is saved as WebP in
core/src/main/assets/skins/<skin id>/.
"""
import os
import sys

from PIL import Image, ImageChops, ImageFilter

src, skin = sys.argv[1], sys.argv[2]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "src", "main", "assets", "skins", skin)
os.makedirs(out, exist_ok=True)


def bloom(im):
    """Light spreading round each lit legend: a tight halo and a wide faint one."""
    s = im.width / 1920
    tight = im.filter(ImageFilter.GaussianBlur(8 * s))
    wide = im.filter(ImageFilter.GaussianBlur(28 * s))
    im = ImageChops.add(im, ImageChops.multiply(tight, Image.new("RGB", im.size, (150, 150, 150))))
    return ImageChops.add(im, ImageChops.multiply(wide, Image.new("RGB", im.size, (110, 110, 110))))


glow = os.path.exists(os.path.join(src, "glow.png"))
for name in ("day", "night", "day-pressed", "night-pressed") + (("glow",) if glow else ()):
    im = Image.open(os.path.join(src, name + ".png")).convert("RGB")
    if name == "glow" or (name.startswith("night") and not glow):
        im = bloom(im)
    im.save(os.path.join(out, name + ".webp"), quality=88, method=6)
    print(name, os.path.getsize(os.path.join(out, name + ".webp")) // 1024, "KB")

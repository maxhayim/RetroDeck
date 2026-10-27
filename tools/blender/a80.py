"""
A80 Factory 1994, modelled in 3D and rendered to the images the app layers.

    blender -b --factory-startup -P tools/blender/a80.py -- <layout.json> <out dir> [samples]

The layout is core/build/skin-renders/a80factory94.json, written by the render test, so every key
sits on its touch area. Renders day, night, the two again with every key pressed in, and glow: the
backlit legends alone, white, which the app tints green. ONLY=day,glow renders just those, and
PCT=50 renders at half size, for trying things out.

Modelled on the factory CD and tape unit of the mid-1990s: charcoal plastic, a CD slot across the
top, a TUNE SEEK rocker beside six preset keys with their CD functions printed above in orange and
below in green, a cassette door, and a sound panel with three small knobs beside the display.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rd import *  # noqa: E402,F401,F403
import rd  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
L = json.load(open(args[0]))
OUT = args[1]
SAMPLES = int(args[2]) if len(args) > 2 else 128

scene_setup(SAMPLES)

FACE = 0.30      # the front of the faceplate
PANEL = 0.06     # how far the sound panel is set back
KEYS = {k["label"] if k["id"] is None else k["id"]: k for k in L["keys"]}
hole = hole_material()

plastic = material("plastic", (0.020, 0.021, 0.023), 0.46, 0.45, bump=0.25, bump_scale=500)
inset = material("inset", (0.013, 0.013, 0.015), 0.55, 0.4, bump=0.2, bump_scale=500)
keycap = material("keycap", (0.028, 0.029, 0.032), 0.36, 0.5, bump=0.05, bump_scale=1200)
gloss = material("gloss", (0.006, 0.006, 0.007), 0.12, 0.6, coat=0.7)
knurl = material("knurl", (0.016, 0.016, 0.018), 0.45, 0.4)
felt = material("felt", (0.02, 0.02, 0.022), 0.95, 0.1)
tab = material("tab", srgb(0x2F6FC4), 0.35, 0.5)
lens = material("lens", (0.02, 0.035, 0.02), 0.08, 0.6, coat=1.0)
white = material("white", srgb(0xC9CDD2), 0.55)
orange = material("orange", srgb(0xD9853A), 0.55)
green = material("green", srgb(0x6FB86F), 0.55)
grey = material("grey", srgb(0x7E838A), 0.55)

# Key legends: printed by day in their own colours, lit green from behind at night (the glow layer).
lit_white = legend_material("legend", srgb(0xD3D6DA))
lit_orange = legend_material("legend-orange", srgb(0xE07A3E))
lit_green = legend_material("legend-green", srgb(0x7CC47C))


def printed(s, x, y, size, mat, align="CENTER", spacing=1.15, z=FACE):
    """Print on the face: x, y as screen fractions of the text's centre."""
    return text(s, X(x), Y(y), z + 0.001, size, mat, align, spacing)


def chevron(cx, cy, size, up, mat, z):
    """The thin V (or upside-down V) printed beside TRACK, FAST and on the rocker."""
    w, h, t = size * 0.5, size * 0.3, size * 0.09
    s = 1 if up else -1
    v = [(cx - w, cy - s * h, z), (cx - w + t * 1.4, cy - s * h, z), (cx, cy + s * (h - t * 2.2), z), (cx, cy + s * h, z),
         (cx + w - t * 1.4, cy - s * h, z), (cx + w, cy - s * h, z)]
    return flat("chevron", v, [(0, 1, 2, 3) if up else (3, 2, 1, 0), (3, 2, 4, 5) if up else (5, 4, 2, 3)], mat)


# ---- the case and face ----

box("dash", -1, -1, W + 1, H + 1, -0.6, -0.1, material("dash", (0.012, 0.012, 0.014), 0.62, 0.35, bump=0.35,
                                                       bump_scale=220))
box("pocket", X(0.0), Y(1.0), X(1.0), Y(0.0), -0.5, -0.09, hole)
face = target(rbox("face", X(0.006), Y(0.988), X(0.994), Y(0.012), -0.5, FACE, plastic, 0.012 * 9, bevel=0.05))

# The two printed bands either side of the presets, set a little into the face.
for y0, y1 in ((0.208, 0.25), (0.418, 0.458)):
    cut_box(X(0.193), Y(y1), X(0.962), Y(y0), FACE, 0.015, hole, 0.02, floor=inset)
BAND_Z = FACE - 0.008

x0, y0, x1, y1 = rect(KEYS["prev"]["box"])
rocker_mid = (x0 + rect(KEYS["next"]["box"])[2]) / 2
for label, x, mat in (("TRACK", rocker_mid, orange), ("FAST", None, orange), ("RPT", 2, orange), ("SCAN", 3, orange),
                      ("RANDOM", 4, orange)):
    if x is None:
        x = (rect(KEYS["preset1"]["box"])[2] + rect(KEYS["preset2"]["box"])[0]) / 2
    elif isinstance(x, int):
        a, _, c, _ = rect(KEYS["preset%d" % (x + 1)]["box"])
        x = (a + c) / 2
    text(label, x, Y(0.229), BAND_Z, 0.19, mat, spacing=1.15)
    if label in ("TRACK", "FAST"):
        off = 0.42 + len(label) * 0.055
        chevron(x - off, Y(0.229), 0.2, False, mat, BAND_Z)
        chevron(x + off, Y(0.229), 0.2, True, mat, BAND_Z)

a, _, c, _ = rect(KEYS["prev"]["box"])
for label, x in (("REW", (a + c) / 2 - 0.15), ("FF", (rect(KEYS["next"]["box"])[0] + rect(KEYS["next"]["box"])[2]) / 2 + 0.15)):
    text(label, x, Y(0.438), BAND_Z, 0.19, green, spacing=1.15)
for i, label in enumerate(("PROG", "APS", "RPT", "SKIP", "B·C NR", "RM")):
    a, _, c, _ = rect(KEYS["preset%d" % (i + 1)]["box"])
    text(label, (a + c) / 2, Y(0.438), BAND_Z, 0.19, green, spacing=1.15)

# ---- the CD slot ----

a, b, c, d = rect(L["slot"])
cut_box(a, b, c, d, FACE, 0.08, hole, 0.06, floor=inset)
rbox("slit", a + 0.25, (b + d) / 2 - 0.09, c - 0.25, (b + d) / 2 + 0.09, FACE - 0.08, FACE - 0.07, hole, 0.08)
box("felt", a + 0.35, (b + d) / 2 - 0.05, c - 0.35, (b + d) / 2 - 0.02, FACE - 0.07, FACE - 0.068, felt)

# ---- keys ----


def legend_for(label):
    return lit_orange if label == "CD" else lit_green if label == "TAPE" else lit_white


def plain(k, size, top=FACE, label=None):
    k = dict(k)
    if label is not None:
        k["label"] = label
    if k["id"] == "play":
        k["id"] = "tape"   # a plain legend, not the play and pause marks
    return key(k, top, keycap, hole, legend_for(k["label"]), size, height=0.12, gap=0.045, bevel=0.035, r=0.05,
               face_up=0.0)


for k in L["keys"]:
    if k["label"] == "EJECT":
        plain(k, 0.21)
plain(KEYS["power"], 0.18)
for i in range(1, 7):
    plain(KEYS["preset%d" % i], 0.30)
plain(KEYS["display"], 0.24)
for name in ("source", "AM·FM", "play"):
    parts = plain(KEYS[name], 0.24)
    # The source keys' indicator, a small lens at the left of the cap.
    a, b, c, d = rect(KEYS[name]["box"])
    parts.append(rbox("lamp", a + 0.16, (b + d) / 2 - 0.05, a + 0.34, (b + d) / 2 + 0.05, FACE + 0.10, FACE + 0.122,
                      lens, 0.04))
    parts[1].location.x += 0.1

# The TUNE SEEK rocker: one cap across the previous and next touch areas.
a, b, _, d = rect(KEYS["prev"]["box"])
_, _, c, _ = rect(KEYS["next"]["box"])
cut_box(a, b, c, d, FACE, 0.2, hole, 0.05)
top = FACE + 0.12
rocker = [rbox("rocker", a + 0.045, b + 0.045, c - 0.045, d - 0.045, FACE - 0.18, top, keycap, 0.05, bevel=0.035)]
mid, cy = (a + c) / 2, (b + d) / 2
rocker.append(text("TUNE", mid, cy + 0.14, top + 0.001, 0.2, lit_white, spacing=1.1, face="BarlowSemiCondensed-SemiBold"))
rocker.append(text("SEEK", mid, cy - 0.14, top + 0.001, 0.2, lit_white, spacing=1.1, face="BarlowSemiCondensed-SemiBold"))
rocker.append(chevron(a + 0.3, cy, 0.24, False, lit_white, top + 0.001))
rocker.append(chevron(c - 0.3, cy, 0.24, True, lit_white, top + 0.001))
rd.KEYS.append(rocker)

# ---- knob ----

cx, cy, r = knob(L["knob"], FACE, hole, knurl, keycap, ridges=60, height=0.42, cap_frac=0.86)

# ---- the tape deck ----

a, b, c, d = rect(L["changer"])
cut_box(a, b, c, d, FACE, 0.2, hole, 0.04)
DOOR = FACE - 0.07
box("door", a + 0.04, b + 0.04, c - 0.04, d - 0.04, FACE - 0.2, DOOR, gloss, bevel=0.025)
box("lip", a + 0.04, d - 0.22, c - 0.04, d - 0.04, FACE - 0.2, DOOR + 0.03, keycap, bevel=0.02)
box("tab", c - 0.42, b + 0.18, c - 0.22, d - 0.3, DOOR, DOOR + 0.02, tab, bevel=0.01)
printed("ANTI-THEFT SYSTEM", 0.51, 0.632, 0.15, grey, spacing=1.3, z=DOOR)
# The anti-theft lamp between the door and SCAN.
lx, ly = X(0.76), Y(0.52)
cut_box(lx - 0.22, ly - 0.05, lx + 0.22, ly + 0.05, FACE, 0.03, hole, 0.05, floor=inset)
rbox("lamp", lx - 0.2, ly - 0.035, lx + 0.2, ly + 0.035, FACE - 0.03, FACE - 0.01, lens, 0.035)

# ---- the sound panel and display ----

pa, pb, pc, pd = X(0.198), Y(0.963), X(0.968), Y(0.664)
cut(rbox("cut", pa, pb, pc, pd, FACE - PANEL, FACE + 0.2, hole, 0.06))
PTOP = FACE - PANEL
target(rbox("panel", pa - 0.03, pb - 0.03, pc + 0.03, pd + 0.03, -0.5, PTOP, plastic, 0.08))

plain(KEYS["menu"], 0.24, top=PTOP)
loud = next(k for k in L["keys"] if k["label"] == "LOUD")
plain(loud, 0.2, top=PTOP, label=" ")
la, lb, lc, ld = rect(loud["box"])
printed("LOUD", lc / W + 0.006, 0.718, 0.19, white, "LEFT", z=PTOP)

for t in L["trims"]:
    tx, ty = centre(t["box"])
    tr = min(rect(t["box"])[2] - rect(t["box"])[0], rect(t["box"])[3] - rect(t["box"])[1]) / 2 * 0.62
    cut_circle(tx, ty, tr * 1.12, PTOP, 0.2, hole)
    cylinder("trim", tx, ty, tr, PTOP - 0.2, PTOP + 0.24, keycap, bevel=tr * 0.25)
    name = t["label"].split("|")[0]
    text(name, tx, Y(t["box"][1]) + 0.04, PTOP + 0.001, 0.2, white, spacing=1.1, face="BarlowSemiCondensed-SemiBold")
xb = centre(L["trims"][0]["box"])[0]
xt = centre(L["trims"][2]["box"])[0]
xm = centre(L["trims"][1]["box"])[0]
yl = Y(0.957)
text("BAL", xb, yl, PTOP + 0.001, 0.19, white, spacing=1.1, face="BarlowSemiCondensed-SemiBold")
text("PULL", xm, yl, PTOP + 0.001, 0.15, white, spacing=1.1)
text("FADE", xt, yl, PTOP + 0.001, 0.19, white, spacing=1.1, face="BarlowSemiCondensed-SemiBold")
for p0, p1 in ((xb + 0.26, xm - 0.25), (xm + 0.25, xt - 0.28)):
    box("rule", p0, yl - 0.008, p1, yl + 0.008, PTOP - 0.001, PTOP + 0.001, white)

printed("COMPACT DISC PLAYER / PLL SYNTHESIZED AM/FM RECEIVER", 0.63, 0.695, 0.14, white, "LEFT", 1.2, z=PTOP)

# The display: a recessed window. The app draws the lit panel and its segments into it.
a, b, c, d = rect(L["lcd"])
cut_box(a - 0.06, b - 0.06, c + 0.06, d + 0.06, PTOP, 0.12, hole, 0.05,
        floor=material("surround", (0.008, 0.008, 0.009), 0.6, 0.3))

rd.apply_cuts()
render_all(OUT, Lights(sun=2400, fill=120, softbox=0.35), press_depth=0.06)

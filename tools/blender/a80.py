"""
A80 Factory 1994, modelled in 3D and rendered to the images the app layers.

    blender -b --factory-startup -P tools/blender/a80.py -- <layout.json> <out dir> [samples]

The layout is core/build/skin-renders/a80factory94.json, written by the render test, so every key
sits on its touch area. Renders day, night, the two again with every key pressed in, and glow: the
backlit legends alone, white, which the app tints green. ONLY=day,glow renders just those, and
PCT=50 renders at half size, for trying things out.

Modelled on the factory CD and tape unit of the mid-1990s: charcoal plastic, a CD slot across the
top over a strip display that numbers the preset keys, the knob beside a cassette door, and the
source keys and a TUNE rocker along the foot beside three small tone knobs. The displays are olive
grey by day and lime green at night, as on the real unit.
"""
import json
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
EJECT = "⏏"
hole = hole_material()

plastic = material("plastic", (0.020, 0.021, 0.023), 0.46, 0.45, bump=0.25, bump_scale=500)
inset = material("inset", (0.012, 0.012, 0.014), 0.55, 0.4, bump=0.2, bump_scale=500)
keycap = material("keycap", (0.028, 0.029, 0.032), 0.36, 0.5, bump=0.05, bump_scale=1200)
gloss = material("gloss", (0.006, 0.006, 0.007), 0.12, 0.6, coat=0.7)
knurl = material("knurl", (0.016, 0.016, 0.018), 0.45, 0.4)
felt = material("felt", (0.02, 0.02, 0.022), 0.95, 0.1)
lens = material("lens", (0.02, 0.035, 0.02), 0.08, 0.6, coat=1.0)
white = material("white", srgb(0xC9CDD2), 0.55)
grey = material("grey", srgb(0x8A8F96), 0.55)
label = material("label", srgb(0xE4E6E8), 0.5)
lit = legend_material("legend", srgb(0xD3D6DA))   # printed by day, lit green from behind at night
segment = material("segment", srgb(0x1E2A1C), 0.5)

# The strip display: olive grey by day, its lime backlight showing at night, as the app draws the
# main display.
strip = material("strip", srgb(0x7A7762), 0.45, 0.4)
strip_bsdf = strip.node_tree.nodes["Principled BSDF"]
strip_bsdf.inputs["Emission Color"].default_value = (*srgb(0xA6D83A), 1)


def strip_light(pressed, night):
    strip_bsdf.inputs["Base Color"].default_value = (*(srgb(0x7A7762) if not night else (0, 0, 0)), 1)
    strip_bsdf.inputs["Emission Strength"].default_value = 0.9 if night else 0.0


def key_named(name):
    return [k for k in L["keys"] if k["label"] == name]


def key_id(i):
    return next(k for k in L["keys"] if k["id"] == i)


def chevron(cx, cy, size, up, mat, z):
    """A thin V, or an upside-down one."""
    w, h, t = size * 0.5, size * 0.3, size * 0.09
    s = 1 if up else -1
    v = [(cx - w, cy - s * h, z), (cx - w + t * 1.4, cy - s * h, z), (cx, cy + s * (h - t * 2.2), z), (cx, cy + s * h, z),
         (cx + w - t * 1.4, cy - s * h, z), (cx + w, cy - s * h, z)]
    return flat("chevron", v, [(0, 1, 2, 3) if up else (3, 2, 1, 0), (3, 2, 4, 5) if up else (5, 4, 2, 3)], mat)


def eject_mark(cx, cy, size, mat, z):
    """The eject symbol: a triangle over a bar."""
    return [tri(cx, cy + size * 0.12, size * 0.62, True, mat, z),
            box("bar", cx - size * 0.31, cy - size * 0.34, cx + size * 0.31, cy - size * 0.24, z - 0.001, z, mat)]


def skip_mark(cx, cy, size, forward, mat, z):
    """|<< or >>|: two triangles and a bar."""
    s = 1 if forward else -1
    parts = []
    h = size * 0.34
    for dx in (-0.22, 0.12):
        x = cx + s * dx * size
        v = [(x - s * h * 0.5, cy - h / 2, z), (x + s * h * 0.5, cy, z), (x - s * h * 0.5, cy + h / 2, z)]
        parts.append(flat("tri", v, [(0, 1, 2) if forward else (0, 2, 1)], mat))
    bx = cx + s * 0.36 * size
    parts.append(box("bar", bx - 0.012, cy - size * 0.17, bx + 0.012, cy + size * 0.17, z - 0.001, z, mat))
    return parts


def cap(k, top=FACE, height=0.11, r=0.06):
    """A key cap in its own opening: its parts, and the height to print its legend at."""
    x0, y0, x1, y1 = rect(k["box"])
    cut_box(x0, y0, x1, y1, top, 0.2, hole, r)
    return [rbox("key", x0 + 0.04, y0 + 0.04, x1 - 0.04, y1 - 0.04, top - 0.18, top + height, keycap, r - 0.02,
                 bevel=0.035)], top + height + 0.001


def plain(k, size):
    parts, z = cap(k)
    cx, cy = centre(k["box"])
    if k["label"] == EJECT:
        parts += eject_mark(cx, cy, size * 1.4, lit, z)
    else:
        parts.append(text(k["label"], cx, cy, z, size, lit, spacing=1.05, face="BarlowSemiCondensed-SemiBold"))
    rd.KEYS.append(parts)
    return parts


# ---- the case and face ----

box("dash", -1, -1, W + 1, H + 1, -0.6, -0.1, material("dash", (0.012, 0.012, 0.014), 0.62, 0.35, bump=0.35,
                                                       bump_scale=220))
box("pocket", X(0.0), Y(1.0), X(1.0), Y(0.0), -0.5, -0.09, hole)
target(rbox("face", X(0.006), Y(0.988), X(0.994), Y(0.012), -0.5, FACE, plastic, 0.012 * 9, bevel=0.05))

# The upper half sits in a frame: a fine groove round it.
for x0, y0, x1, y1 in ((0.03, 0.035, 0.97, 0.042), (0.03, 0.495, 0.97, 0.502), (0.03, 0.035, 0.034, 0.502),
                       (0.966, 0.035, 0.97, 0.502)):
    cut_box(X(x0), Y(y1), X(x1), Y(y0), FACE, 0.02, hole)

# ---- the upper half ----

a, b, c, d = rect(L["slot"])
cut_box(a, b, c, d, FACE, 0.08, hole, 0.06, floor=inset)
rbox("slit", a + 0.12, (b + d) / 2 - 0.1, c - 0.12, (b + d) / 2 + 0.1, FACE - 0.08, FACE - 0.07, hole, 0.09)
box("felt", a + 0.2, (b + d) / 2 - 0.06, c - 0.2, (b + d) / 2 - 0.03, FACE - 0.07, FACE - 0.068, felt)

for k in key_named(EJECT):
    plain(k, 0.26)

# The strip display over the presets, numbering them.
sx0, sy0, sx1, sy1 = X(0.051), Y(0.317), X(0.648), Y(0.232)
cut_box(sx0 - 0.05, sy0 - 0.05, sx1 + 0.05, sy1 + 0.05, FACE, 0.08, hole, 0.03, floor=inset)
box("strip", sx0, sy0, sx1, sy1, FACE - 0.08, FACE - 0.07, strip)
dseg = bpy.data.fonts.load(os.path.join(HERE, "..", "..", "core", "src", "main", "assets", "fonts",
                                        "DSEG7Classic-BoldItalic.ttf"))
for i in range(6):
    k = key_id("preset%d" % (i + 1))
    t = text(str(i + 1), centre(k["box"])[0], (sy0 + sy1) / 2, FACE - 0.069, 0.42, segment)
    t.data.font = dseg

# The six preset keys: a short printed bar on each; the number is on the strip above.
for i in range(6):
    k = key_id("preset%d" % (i + 1))
    parts, z = cap(k, r=0.05)
    cx, cy = centre(k["box"])
    parts.append(box("bar", cx - 0.16, cy - 0.022, cx + 0.16, cy + 0.022, z - 0.001, z, lit))
    rd.KEYS.append(parts)

# The main display: a recessed window. The app draws the panel and its segments into it.
a, b, c, d = rect(L["lcd"])
cut_box(a - 0.06, b - 0.06, c + 0.06, d + 0.06, FACE, 0.1, hole, 0.05,
        floor=material("surround", (0.008, 0.008, 0.009), 0.6, 0.3))
text("B·C NR", X(0.965), Y(0.222), FACE + 0.001, 0.14, grey, "RIGHT", 1.2)

# ---- the middle ----

knob(L["knob"], FACE, hole, knurl, keycap, ridges=60, height=0.5, cap_frac=0.9)

pwr = key_id("power")
parts, z = cap(pwr, r=0.05)
px, _ = centre(pwr["box"])
_, pb, _, pd = rect(pwr["box"])
parts.append(text("PWR", px, pd - 0.3, z, 0.24, lit, face="BarlowSemiCondensed-SemiBold"))
parts.append(text("PUSH", px, pd - 0.52, z, 0.15, lit))
parts.append(text("VOL", px, pb + 0.25, z, 0.22, lit, face="BarlowSemiCondensed-SemiBold"))
rd.KEYS.append(parts)

# The cassette door, its anti-theft label along the top.
a, b, c, d = rect(L["changer"])
cut_box(a, b, c, d, FACE, 0.2, hole, 0.04)
DOOR = FACE - 0.08
box("door", a + 0.04, b + 0.04, c - 0.04, d - 0.04, FACE - 0.2, DOOR, gloss, bevel=0.025)
box("label", a + 0.5, d - 0.36, c - 0.5, d - 0.08, DOOR, DOOR + 0.01, label, bevel=0.005)
text("ANTI-THEFT SYSTEM", (a + c) / 2, d - 0.22, DOOR + 0.012, 0.2, hole, spacing=1.1,
     face="BarlowSemiCondensed-SemiBold")

plain(key_id("menu"), 0.26)
plain(key_named("R·M")[0], 0.26)
# The anti-theft lamp under EQ and R·M.
lx, ly = X(0.79), Y(0.735)
cut_box(lx - 0.2, ly - 0.06, lx + 0.2, ly + 0.06, FACE, 0.03, hole, 0.06, floor=inset)
rbox("lamp", lx - 0.18, ly - 0.045, lx + 0.18, ly + 0.045, FACE - 0.03, FACE - 0.01, lens, 0.045)

# ---- the foot ----

# The source keys and rocker sit in a recessed bar; the tone knobs in a panel of their own.
cut_box(X(0.09), Y(0.975), X(0.692), Y(0.80), FACE, 0.04, hole, 0.05, floor=inset)
cut_box(X(0.70), Y(0.975), X(0.93), Y(0.80), FACE, 0.04, hole, 0.05, floor=inset)
FOOT = FACE - 0.04   # the recess floors; print sits just above them
PRINT = FOOT + 0.006

plain(key_named("AM·FM")[0], 0.24)
plain(key_id("play"), 0.24)
plain(key_id("source"), 0.24)
plain(key_id("display"), 0.24)

# The TUNE rocker: one cap across the previous and next touch areas.
a, b, _, d = rect(key_id("prev")["box"])
_, _, c, _ = rect(key_id("next")["box"])
cut_box(a, b, c, d, FACE, 0.2, hole, 0.06)
top = FACE + 0.11
rocker = [rbox("rocker", a + 0.04, b + 0.04, c - 0.04, d - 0.04, FACE - 0.18, top, keycap, 0.05, bevel=0.035)]
mid, cy = (a + c) / 2, (b + d) / 2
rocker.append(text("TUNE", mid, cy + 0.13, top + 0.001, 0.22, lit, spacing=1.1, face="BarlowSemiCondensed-SemiBold"))
rocker.append(chevron(mid - 0.62, cy + 0.13, 0.24, False, lit, top + 0.001))
rocker.append(chevron(mid + 0.62, cy + 0.13, 0.24, True, lit, top + 0.001))
rocker += skip_mark(mid - 0.62, cy - 0.17, 0.3, False, lit, top + 0.001)
rocker += skip_mark(mid + 0.62, cy - 0.17, 0.3, True, lit, top + 0.001)
rd.KEYS.append(rocker)

for t in L["trims"]:
    tx, ty = centre(t["box"])
    x0, y0, x1, y1 = rect(t["box"])
    tr = min(x1 - x0, y1 - y0) / 2 * 0.62
    cut_circle(tx, ty, tr * 1.12, FOOT, 0.2, hole)
    cylinder("trim", tx, ty, tr, FOOT - 0.2, FOOT + 0.22, keycap, bevel=tr * 0.25)
    text(t["label"].split("|")[0], tx, y1 + 0.13, PRINT, 0.22, white, spacing=1.05,
         face="BarlowSemiCondensed-SemiBold")
xs = [centre(t["box"])[0] for t in L["trims"]]
yb = rect(L["trims"][0]["box"])[1] - 0.12
text("PULL", (xs[0] + xs[1]) / 2 - 0.18, yb, PRINT, 0.13, white)
text("BAL", (xs[0] + xs[1]) / 2 + 0.16, yb, PRINT, 0.19, white, face="BarlowSemiCondensed-SemiBold")
text("FADE", xs[2], yb, PRINT, 0.19, white, face="BarlowSemiCondensed-SemiBold")

rd.apply_cuts()
render_all(OUT, Lights(sun=2400, fill=120, softbox=0.35), press_depth=0.06, on_press=strip_light)

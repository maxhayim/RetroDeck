"""
The rendered skins other than Z32 Factory 1990 (z32.py), modelled in 3D from the app's layout.

    blender -b --factory-startup -P tools/blender/skins.py -- <layout.json> <out dir> [samples]

The layout is core/build/skin-renders/<skin id>.json, written by the render test, so every key sits
on its touch area. Renders day, night, the two again with every key pressed in, and glow: the
backlit legends alone, white, which the app tints with the dash lighting at night. ONLY=day,glow
renders just those, and PCT=50 renders at half size, for trying things out.

No skin copies a real unit: each is what car stereos of its era were generally made of.
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

BODY = L.get("body", [0.0, 0.0, 1.0, 1.0])
SINGLE = L.get("din", 2) == 1
# The screen's short side: sizes in the app are fractions of it. A single-DIN face is half as tall,
# so its print, radii and screws are made smaller too, though not by half, so they stay legible.
U = (BODY[3] - BODY[1]) * H * 1.47 if SINGLE else min(W, H)
FACE = 0.30      # the front of the faceplate
hole = hole_material()
CONTROLS = [k for k in L["keys"] if k["id"] is not None]
DECOR = [k for k in L["keys"] if k["id"] is None]


def bx(f):
    """A fraction across the faceplate, as a fraction of the screen."""
    return BODY[0] + f * (BODY[2] - BODY[0])


def by(f):
    """A fraction down the faceplate, as a fraction of the screen."""
    return BODY[1] + f * (BODY[3] - BODY[1])


def dash():
    box("dash", -1, -1, W + 1, H + 1, -0.6, -0.1, material("dash", (0.012, 0.012, 0.014), 0.62, 0.35, bump=0.35,
                                                           bump_scale=220))
    if SINGLE and L.get("screen"):
        # The radio's slot in the dash, and the dash's own screen above it for the visualizer. On a
        # single-DIN unit's own wide screen the radio fills it, and there is neither.
        x0, y0, x1, y1 = rect(BODY)
        box("pocket", x0 - 0.06, y0 - 0.06, x1 + 0.06, y1 + 0.06, -0.12, -0.099, hole)
        x0, y0, x1, y1 = rect(L["screen"])
        m = 0.16
        bezel = material("bezel", (0.010, 0.010, 0.012), 0.38, 0.5, bump=0.08, bump_scale=700)
        rbox("bezel", x0 - m, y0 - m, x1 + m, y1 + m, -0.1, 0.10, bezel, 0.22, bevel=0.05)
        rbox("screen", x0, y0, x1, y1, 0.10, 0.104, material("screen", (0.002, 0.0025, 0.003), 0.05, 0.5, coat=1.0),
             0.03)


def plate(outline, mat, edge=0.03, edge_mat=None):
    """The faceplate: the outline pushed out to FACE, its front edge chamfered, openings cut out of it."""
    o = target(prism("face", outline, -0.5, FACE, mat))
    m = o.modifiers.new("bevel", "BEVEL")
    m.width = edge
    m.segments = 1
    m.limit_method = "ANGLE"
    if edge_mat:
        o.data.materials.append(edge_mat)
        m.material = 1
    return o


def plate_box(x0, y0, x1, y1, r, mat, edge=0.03, edge_mat=None):
    return plate(rrect(X(x0), Y(y1), X(x1), Y(y0), r), mat, edge, edge_mat)


def window(b, border, r, glass, surround):
    """A recessed display window: a cut in the face, a dark surround, the glass set back in it."""
    x0, y0, x1, y1 = rect(b)
    cut_box(x0 - border, y0 - border, x1 + border, y1 + border, FACE, 0.12, hole, r + border * 0.8, floor=surround)
    rbox("glass", x0, y0, x1, y1, FACE - 0.14, FACE - 0.08, glass, r)


def keys(top, mat, legend, **kw):
    size = fit(CONTROLS)
    for k in CONTROLS:
        key(k, top, mat, hole, legend, size, **kw)
    if DECOR:
        size = fit(DECOR)
        for k in DECOR:
            key(k, top, mat, hole, legend, size, **kw)


def printed(s, x, y, size, mat, align="CENTER", spacing=1.2):
    """Print on the face: x, y as screen fractions, y the text's baseline as in the app."""
    return text(s, X(x), Y(y) + size * 0.36, FACE + 0.001, size, mat, align, spacing)


def screws(fx, fy, chrome, slot):
    r = 0.011 * U
    for i, (x, y) in enumerate(((fx, fy), (1 - fx, fy), (fx, 1 - fy), (1 - fx, 1 - fy))):
        cx, cy = X(bx(x)), Y(by(y))
        cut_circle(cx, cy, r * 1.12, FACE, 0.04, hole)
        cylinder("screw", cx, cy, r, FACE - 0.04, FACE + 0.02, chrome, bevel=r * 0.35, top_r=r * 0.9)
        t = math.radians(17 + i * 23)
        for a in (t, t + math.pi / 2):
            s = box("cross", -r * 0.62, -r * 0.1, r * 0.62, r * 0.1, FACE + 0.005, FACE + 0.035, slot)
            s.location = (cx, cy, 0)
            s.rotation_euler = (0, 0, a)


def tape_door(b, chrome, flap):
    """A cassette door: a chrome-lipped opening, the flap set back in it, a finger notch."""
    x0, y0, x1, y1 = rect(b)
    lw = 0.005 * U
    target(rbox("lip", x0 - lw, y0 - lw, x1 + lw, y1 + lw, FACE - 0.1, FACE + 0.03, chrome, lw * 1.5, bevel=lw * 0.6))
    cut_box(x0, y0, x1, y1, FACE + 0.031, 0.25, hole, 0.02)
    fy1 = y1 - (y1 - y0) * 0.22
    box("flap", x0 + 0.03, y0 + 0.03, x1 - 0.03, fy1, FACE - 0.2, FACE - 0.06, flap, bevel=0.01)
    nw, nh = (x1 - x0) * 0.16, (fy1 - y0) * 0.28
    nx, ny = (x0 + x1) / 2, y0 + 0.03 + (fy1 - y0) * 0.18 + nh / 2
    rbox("notch", nx - nw / 2, ny - nh / 2, nx + nw / 2, ny + nh / 2, FACE - 0.065, FACE - 0.058, hole, nh / 2)


def slot(b, lip_mat):
    """A CD or cassette slot: a lipped, rounded opening into the dark, felt across it."""
    x0, y0, x1, y1 = rect(b)
    r = (y1 - y0) / 2 * 0.98   # just under round, or the corners meet and the opening won't cut
    lw = (0.006 if lip_mat.name.startswith("chrome") else 0.004) * U
    target(rbox("lip", x0 - lw, y0 - lw, x1 + lw, y1 + lw, FACE - 0.1, FACE + 0.035, lip_mat, r + lw, bevel=lw * 0.7))
    cut_box(x0, y0, x1, y1, FACE + 0.036, 0.4, hole, r)
    felt = material("felt", (0.02, 0.02, 0.022), 0.95, 0.1)
    box("felt", x0 + r, (y0 + y1) / 2 - (y1 - y0) * 0.2, x1 - r, (y0 + y1) / 2 - (y1 - y0) * 0.12, FACE - 0.3, FACE - 0.28,
        felt)


def gloss_cap(cx, cy, r, top, ring):
    """A gloss black knob cap, with a polished ring round it if ring is a material."""
    gloss = material("gloss", (0.004, 0.004, 0.005), 0.08, 0.6, coat=1.0)
    if ring:
        cylinder("ring", cx, cy, r * 0.97, top - 0.04, top + 0.02, ring, bevel=0.012)
    cylinder("cap", cx, cy, r * (0.88 if ring else 0.97), top - 0.03, top + 0.04, gloss, bevel=r * 0.08)


# ---- the skins ----

def classic():
    """Brushed aluminium with black keys, a spun-metal knob, a recessed fluorescent window and a tape door."""
    lights = Lights(sun=1800, fill=100, softbox=0.55)
    alu = material("alu", (0.66, 0.68, 0.71), 0.30, metal=1.0, aniso=0.75, brushed=True, bump=0.12)
    chrome = material("chrome", (0.92, 0.93, 0.95), 0.08, metal=1.0)
    spun = material("spun", (0.62, 0.63, 0.65), 0.26, metal=1.0, rings=True, bump=0.15)
    keycap = material("keycap", (0.010, 0.010, 0.011), 0.30, 0.55, bump=0.05, bump_scale=1200)
    knurl = material("knurl", (0.016, 0.016, 0.018), 0.45, 0.4)
    ink = material("ink", srgb(0x25272B), 0.6)
    flap = material("flap", (0.30, 0.31, 0.33), 0.35, metal=1.0, aniso=0.6, brushed=True, bump=0.1)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.018 * U, alu, edge=0.035)
    # Engraved line between the display and the presets.
    x0, x1 = L["lcd"][0] - 0.01, L["lcd"][2] + 0.01
    gy = (L["lcd"][3] + L["keys"][-6]["box"][1]) / 2
    cut_box(X(x0), Y(gy) - 0.012, X(x1), Y(gy) + 0.012, FACE, 0.025, hole)
    screws(0.018, 0.045, chrome, material("slot", (0.05, 0.05, 0.055), 0.5))
    window(L["lcd"], 0.016 * U, L["corner"] * U, material("vfd", (0.003, 0.004, 0.0035), 0.45, 0.12),
           material("surround", (0.012, 0.012, 0.013), 0.6, 0.3))
    keys(FACE, keycap, legend_material("legend", srgb(0xB4B9C0)), r=L["corner"] * U)
    knob(L["knob"], FACE, hole, knurl, spun, ridges=110)
    x0, y0, x1, y1 = L["knob"]
    printed("VOLUME", (x0 + x1) / 2, y1 + 0.03 * U / H, 0.026 * U, ink, spacing=1.25)
    tape_door(L["slot"], chrome, flap)
    printed("AUTO REVERSE", L["slot"][0], L["slot"][1] - 0.012 * U / H, 0.018 * U, ink, "LEFT", 1.3)
    return lights


def digital():
    """Grained black plastic with soft rubber keys, a ribbed knob and a glossy dot-matrix window."""
    lights = Lights(sun=2400, fill=120, softbox=0.35)
    plastic = material("plastic", (0.020, 0.021, 0.024), 0.48, 0.45, bump=0.3, bump_scale=420)
    rubber = material("rubber", (0.030, 0.031, 0.035), 0.75, 0.3, bump=0.35, bump_scale=600)
    satin = material("satin", (0.10, 0.105, 0.115), 0.35, metal=0.6)
    silver = material("chrome-ring", (0.85, 0.86, 0.88), 0.15, metal=1.0, rings=True, bump=0.08)
    grey = material("ink", srgb(0x8E949C), 0.6)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.04 * U, plastic, edge=0.06)
    window(L["lcd"], 0.014 * U, L["corner"] * U * 0.6, material("lcd", (0.004, 0.006, 0.005), 0.12, 0.5, coat=0.6),
           material("surround", (0.008, 0.008, 0.009), 0.5, 0.3))
    keys(FACE, rubber, legend_material("legend", srgb(0xB4B9C0)), r=L["corner"] * U, bevel=0.07, round_bevel=0.09,
         height=0.13)
    cx, cy, r = knob(L["knob"], FACE, hole, rubber, rubber, ridges=40, cap_frac=0.80, height=0.45)
    gloss_cap(cx, cy, r * 0.80, FACE + 0.47, silver)
    x0, y0, x1, y1 = L["knob"]
    printed("VOLUME", (x0 + x1) / 2, y1 + 0.03 * U / H, 0.026 * U, grey, spacing=1.25)
    slot(L["slot"], satin)
    return lights


def modern():
    """One sheet of black glass: a bonded display, touch keys as faint raised pads."""
    lights = Lights(sun=900, fill=60, softbox=0.12)
    glass = material("glass", (0.003, 0.0035, 0.0045), 0.04, 0.5, coat=1.0)
    panel = material("panel", (0.006, 0.008, 0.013), 0.06, 0.5, coat=1.0)
    mask = material("mask", (0.0, 0.0, 0.0), 0.06, 0.5, coat=1.0)
    pad = material("pad", (0.011, 0.012, 0.016), 0.10, 0.5, coat=1.0)
    pad_bsdf = pad.node_tree.nodes["Principled BSDF"]
    pad_bsdf.inputs["Emission Color"].default_value = (0.55, 0.6, 0.7, 1)
    sheet = rbox("glass", 0.0, 0.0, W, H, -0.5, FACE, glass, 0.04 * U * 0.25, bevel=0.06, segments=4)
    sheet.modifiers["bevel"].angle_limit = math.radians(30)
    x0, y0, x1, y1 = rect(L["lcd"])
    r = L["corner"] * U
    m = 0.008 * U
    rbox("mask", x0 - m, y0 - m, x1 + m, y1 + m, FACE - 0.01, FACE + 0.0004, mask, r * 1.3)
    rbox("panel", x0, y0, x1, y1, FACE - 0.01, FACE + 0.0008, panel, r)
    keys(FACE, pad, legend_material("legend", srgb(0xA8B0BE)), r=r, height=0.02, gap=0.0, bevel=0.012, depth=0.03,
         face_up=0.0, cut_face=False)

    def lit(pressed, night):
        # A touch key doesn't move: it lights up under the finger.
        pad_bsdf.inputs["Emission Strength"].default_value = (0.015 if night else 0.12) if pressed else 0.0
    return lights, lit


def hifi():
    """
    Black anodised aluminium, finely brushed, diamond-cut bright edges, champagne pinstripes and print,
    slim keys, a small spun knob and a chrome-lipped tape slot.
    """
    lights = Lights(sun=1800, fill=100, softbox=0.9)
    anod = material("anodised", (0.035, 0.036, 0.04), 0.28, metal=1.0, aniso=0.8, brushed=True, bump=0.1)
    cut_edge = material("cut", (0.90, 0.91, 0.93), 0.12, metal=1.0)
    chrome = material("chrome", (0.92, 0.93, 0.95), 0.08, metal=1.0)
    spun = material("spun", (0.60, 0.61, 0.63), 0.26, metal=1.0, rings=True, bump=0.15)
    keycap = material("keycap", (0.014, 0.014, 0.016), 0.32, 0.55, bump=0.05, bump_scale=1200)
    knurl = material("knurl", (0.02, 0.02, 0.022), 0.4, 0.45)
    champagne = material("champagne", srgb(0xC2AC82), 0.45, 0.5, metal=0.3)
    dull = material("champagne-dull", srgb(0x8A7C60), 0.5, 0.4)
    dash()
    face = plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.008 * U, anod, edge=0.03, edge_mat=cut_edge)
    lcd, presets, switch = L["lcd"], L["keys"][-6]["box"], next(k for k in DECOR if k["label"] == "NR B")["box"]
    # Pinstripes: across under the top row, and either side of the presets.
    for x0, y, x1 in ((bx(0.03), (L["slot"][3] + lcd[1]) / 2, bx(0.97)),
                      (lcd[0] - 0.01, (lcd[3] + presets[1]) / 2, lcd[2] + 0.01),
                      (lcd[0] - 0.01, (presets[3] + switch[1]) / 2, lcd[2] + 0.01)):
        box("stripe", X(x0), Y(y) - 0.01, X(x1), Y(y) + 0.01, FACE - 0.01, FACE + 0.0008, champagne)
    window(L["lcd"], 0.012 * U, L["corner"] * U, material("vfd", (0.006, 0.007, 0.006), 0.35, 0.3),
           material("surround", (0.012, 0.012, 0.013), 0.6, 0.3))
    keys(FACE, keycap, legend_material("legend", srgb(0xC2AC82)), r=L["corner"] * U, height=0.06, bevel=0.012,
         gap=0.035, edge=cut_edge)
    knob(L["knob"], FACE, hole, knurl, spun, ridges=120, height=0.32)
    x0, y0, x1, y1 = L["knob"]
    printed("VOLUME", (x0 + x1) / 2, y1 + 0.032 * U / H, 0.022 * U, champagne, spacing=1.3)
    slot(L["slot"], chrome)
    s0, t0, s1, t1 = L["slot"]
    sh = (t1 - t0) * H
    if SINGLE:
        # Beside the slot: there's no room under it on a single-DIN face.
        py = (t0 + t1) / 2 + 0.006 * U / H
        printed("AUTO REVERSE", s0 - 0.012, py, 0.016 * U, champagne, "RIGHT", 1.3)
        printed("METAL", s1 + 0.012, py, 0.016 * U, champagne, "LEFT", 1.3)
        printed("QUARTZ PLL SYNTHESIZER", bx(0.655), by(0.08), 0.017 * U, champagne, "LEFT", 1.3)
        printed("FM/AM STEREO", bx(0.655), by(0.14), 0.017 * U, dull, "LEFT", 1.3)
    else:
        py = t1 + 0.032 * U / H
        printed("AUTO REVERSE", s0 + sh * 0.5 / W, py, 0.016 * U, champagne, "LEFT", 1.3)
        printed("METAL", s1 - sh * 0.5 / W, py, 0.016 * U, champagne, "RIGHT", 1.3)
        printed("QUARTZ PLL SYNTHESIZER", 0.035, 0.90, 0.017 * U, champagne, "LEFT", 1.3)
        printed("FM/AM STEREO", 0.035, 0.94, 0.017 * U, dull, "LEFT", 1.3)
    # The diamond cut runs round every opening too.
    rd.apply_cuts()
    rd.TARGETS.clear()
    m = face.modifiers.new("cut edges", "BEVEL")
    m.width = 0.012
    m.segments = 1
    m.limit_method = "ANGLE"
    m.material = 1
    return lights


def detachable():
    """A curved, glossy grey front with chrome trim, a big knob in a chrome bezel and round side keys."""
    lights = Lights(sun=2200, fill=120, softbox=0.6)
    plastic = material("plastic", (0.045, 0.048, 0.054), 0.32, 0.5, bump=0.2, bump_scale=500, coat=0.35)
    chrome = material("chrome", (0.92, 0.93, 0.95), 0.07, metal=1.0)
    spun = material("chrome-spun", (0.88, 0.89, 0.91), 0.14, metal=1.0, rings=True, bump=0.1)
    keycap = material("keycap", (0.012, 0.012, 0.014), 0.30, 0.55, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.022, 0.022, 0.025), 0.7, 0.3, bump=0.3, bump_scale=600)
    ink = material("ink", srgb(0x9DA4AE), 0.55)
    dash()
    top = quad((X(bx(0.008)), Y(by(0.08))), (X(bx(0.5)), Y(by(-0.02))), (X(bx(0.992)), Y(by(0.08))))
    bottom = quad((X(bx(0.992)), Y(by(0.92))), (X(bx(0.5)), Y(by(1.02))), (X(bx(0.008)), Y(by(0.92))))
    # Anticlockwise from the front: along the bottom left to right, then the top right to left.
    outline = list(reversed(bottom)) + list(reversed(top))
    plate(outline, plastic, edge=0.05)
    trim = quad((X(bx(0.02)), Y(by(0.09))), (X(bx(0.5)), Y(by(-0.005))), (X(bx(0.98)), Y(by(0.09))))
    tube("trim", [(x, y, FACE + 0.01) for x, y in trim], 0.03, chrome)
    # Along the gap between the display and the presets.
    gy = (L["lcd"][3] + L["keys"][-6]["box"][1]) / 2
    swoosh = quad((X(L["lcd"][0] - 0.005), Y(gy)), (X(bx(0.42)), Y(gy + 0.012)), (X(L["knob"][0] - 0.005), Y(gy - 0.014)))
    tube("swoosh", [(x, y, FACE + 0.01) for x, y in swoosh], 0.024, chrome)
    window(L["lcd"], 0.014 * U, 0.035 * U, material("lcd", (0.002, 0.005, 0.010), 0.12, 0.5, coat=0.6),
           material("surround", (0.008, 0.008, 0.009), 0.5, 0.3))
    keys(FACE, keycap, legend_material("legend", srgb(0xB4B9C0)), r=L["corner"] * U)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("bezel", cx, cy, kr * 1.2, FACE - 0.05, FACE + 0.08, spun, bevel=0.04)
    _, _, r = knob(L["knob"], FACE + 0.08, hole, rubber, rubber, ridges=40, cap_frac=0.78, height=0.42)
    gloss_cap(cx, cy, r * 0.78, FACE + 0.08 + 0.44, chrome)
    printed("MOSFET 50W × 4", (L["knob"][0] + L["knob"][2]) / 2, (L["knob"][3] + L["zones"]["faceplate"][1]) / 2 + 0.004,
            0.018 * U, ink, spacing=1.25)
    return lights


# ---- stacks and later units ----

def pocket():
    """The opening in the dash a stack stands in: dark in the gaps between its units."""
    us = L["units"]
    x0, y0, x1, y1 = rect([min(u[0] for u in us), min(u[1] for u in us), max(u[2] for u in us), max(u[3] for u in us)])
    box("pocket", x0 - 0.06, y0 - 0.06, x1 + 0.06, y1 + 0.06, -0.12, -0.099, hole)


def units(mat, r, edge=0.03, edge_mat=None):
    for u in L["units"]:
        plate_box(u[0], u[1], u[2], u[3], r, mat, edge, edge_mat)


def on_glass(s, x, y, size, mat, align="CENTER", spacing=1.2):
    """Printed on a display's glass, as its fixed legends are: x, y as screen fractions, y the baseline."""
    return text(s, X(x), Y(y) + size * 0.36, FACE - 0.079, size, mat, align, spacing)


def band_x(i, zone, n=16):
    """The centre of visualizer band i, as the app draws it."""
    w = zone[2] - zone[0]
    gap = w * 0.012
    bw = (w - gap * (n - 1)) / n
    return zone[0] + i * (bw + gap) + bw / 2


def bands_print(zone, y, size, mat, put=None):
    """Frequency marks under the bars, at the bands the app gives those pitches."""
    for i, f in ((0, "63"), (3, "160"), (5, "400"), (8, "1K"), (11, "2.5K"), (13, "6.3K"), (15, "12K")):
        (put or on_glass)(f, band_x(i, zone), y, size, mat, spacing=1.0)


def glowing(*mats, day=0.0, night=0.12):
    """Keys moulded in a light, clear plastic glow at night with the lamp behind them."""
    def lit(pressed, n):
        for m in mats:
            m.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = night if n else day
    for m in mats:
        p = m.node_tree.nodes["Principled BSDF"]
        p.inputs["Emission Color"].default_value = p.inputs["Base Color"].default_value
    return lit


def eqstack():
    """Silver stack: an equaliser window with its curves and bands printed on the glass, over a CD unit."""
    lights = Lights(sun=1800, fill=100, softbox=0.6)
    silver = material("silver", (0.50, 0.52, 0.55), 0.34, metal=0.85, aniso=0.5, brushed=True, bump=0.08)
    edge = material("edge", (0.78, 0.79, 0.81), 0.2, metal=1.0)
    keycap = material("keycap", (0.040, 0.041, 0.045), 0.35, 0.5, bump=0.05, bump_scale=1200)
    mint = material("mint", srgb(0x86D9BC), 0.42, 0.4, bump=0.04, bump_scale=900)
    ink = material("ink", srgb(0x26282C), 0.6)
    red = material("red", srgb(0xB3261E), 0.5)
    white = legend_material("legend", srgb(0xE2E5E8))
    dark = legend_material("legend-dark", srgb(0x1F3A30))
    tab = emitter("tab", srgb(0x8FC8FF), 0.9)
    curve = emitter("curve", srgb(0xE8ECEF), 0.7)
    dash()
    pocket()
    units(silver, 0.010 * U, edge=0.03, edge_mat=edge)
    glass = material("vfd", (0.004, 0.005, 0.006), 0.4, 0.15)
    surround = material("surround", (0.010, 0.010, 0.011), 0.6, 0.3)
    window(L["eq"], 0.010 * U, L["corner"] * U, glass, surround)
    window(L["lcd"], 0.010 * U, L["corner"] * U, glass, surround)
    vz = L["zones"]["visualizer"]
    for i, t in enumerate(("FLAT", "ROCK", "POPS", "VOCAL", "JAZZ", "NEWS")):
        on_glass(t, vz[0] + (i + 0.5) * (vz[2] - vz[0]) / 6, vz[1] - 0.022, 0.018 * U, tab, spacing=1.15)
    bands_print(vz, vz[3] + 0.024, 0.014 * U, tab)
    e = L["eq"]
    on_glass("6-PATTERN EQUALIZER CURVE", e[0] + 0.02, e[1] + 0.05, 0.017 * U, curve, "LEFT", 1.2)
    for i, t in enumerate(("FLAT    ROCK", "POPS    VOCAL", "JAZZ    NEWS")):
        on_glass(t, e[0] + 0.035, e[1] + 0.105 + i * 0.045, 0.016 * U, curve, "LEFT", 1.25)
    on_glass("16-BAND", e[0] + 0.035, e[3] - 0.035, 0.016 * U, tab, "LEFT", 1.3)

    def pick(k):
        return (mint, dark) if k["label"] in ("SRC", "DISP") else (keycap, white)
    for k in CONTROLS + DECOR:
        mat, legend = pick(k)
        key(k, FACE, mat, hole, legend, min(fit([k]), 0.22), r=min(0.08, (rect(k["box"])[3] - rect(k["box"])[1]) / 2 - 0.04),
            height=0.10)
    slot(L["slot"], edge)
    cylinder("led", X(0.125), Y(0.595), 0.035, FACE, FACE + 0.02, emitter("led", (0.9, 0.08, 0.05), 1.2))
    printed("AM STEREO", 0.20, 0.553, 0.016 * U, red, "LEFT", 1.3)
    printed("DISC TITLE MEMORY", 0.125, 0.662, 0.013 * U, ink, "LEFT", 1.2)
    printed("16-BAND GRAPHIC EQUALIZER", 0.95, 0.468, 0.016 * U, ink, "RIGHT", 1.3)
    printed("CD HEAD UNIT  ·  4 × 35 W", 0.315, 0.948, 0.014 * U, ink, "LEFT", 1.3)
    return lights, glowing(mint)


def level():
    """One black face, a big window between columns of slim keys, the analyser's bands printed under it."""
    lights = Lights(sun=2200, fill=120, softbox=0.4)
    plastic = material("plastic", (0.022, 0.023, 0.026), 0.42, 0.45, bump=0.25, bump_scale=450)
    keycap = material("keycap", (0.030, 0.031, 0.035), 0.32, 0.5, bump=0.05, bump_scale=1200)
    satin = material("satin", (0.10, 0.105, 0.115), 0.35, metal=0.6)
    grey = material("ink", srgb(0x9097A0), 0.6)
    tab = emitter("tab", srgb(0x58C8D8), 0.6)
    dash()
    plate_box(0.006, 0.012, 0.994, 0.988, 0.02 * U, plastic, edge=0.05)
    window(L["lcd"], 0.012 * U, L["corner"] * U, material("vfd", (0.003, 0.004, 0.005), 0.35, 0.2),
           material("surround", (0.008, 0.008, 0.009), 0.5, 0.3))
    vz = L["zones"]["visualizer"]
    bands_print(vz, vz[3] + 0.022, 0.016 * U, tab)
    on_glass("SPECTRUM", vz[0], vz[1] - 0.012, 0.016 * U, tab, "LEFT", 1.3)
    legend = legend_material("legend", srgb(0xB4B9C0))
    for k in CONTROLS + DECOR:
        key(k, FACE, keycap, hole, legend, min(fit([k]), 0.24), r=0.06, height=0.10)
    slot(L["slot"], satin)
    printed("SOUND FIELD PROCESSOR", 0.17, 0.155, 0.017 * U, grey, "LEFT", 1.3)
    printed("4 × 45 W", 0.86, 0.155, 0.017 * U, grey, "RIGHT", 1.3)
    return lights


def trio():
    """Three black units, their main keys in mint green: equaliser, disc changer controller, head unit."""
    lights = Lights(sun=2200, fill=120, softbox=0.35)
    plastic = material("plastic", (0.020, 0.021, 0.024), 0.45, 0.45, bump=0.28, bump_scale=420)
    keycap = material("keycap", (0.028, 0.029, 0.032), 0.34, 0.5, bump=0.05, bump_scale=1200)
    mint = material("mint", srgb(0x7FD8B4), 0.40, 0.4, bump=0.04, bump_scale=900)
    flap = material("flap", (0.012, 0.012, 0.014), 0.25, 0.5, coat=0.5)
    satin = material("satin", (0.09, 0.095, 0.105), 0.35, metal=0.6)
    grey = material("ink", srgb(0x9097A0), 0.6)
    white = legend_material("legend", srgb(0xC8CDD3))
    dark = legend_material("legend-dark", srgb(0x163327))
    tab = emitter("tab", srgb(0x7CF0A0), 0.5)
    dash()
    pocket()
    units(plastic, 0.012 * U, edge=0.045)
    glass = material("vfd", (0.003, 0.005, 0.004), 0.35, 0.2)
    surround = material("surround", (0.008, 0.008, 0.009), 0.5, 0.3)
    window(L["eq"], 0.010 * U, L["corner"] * U, glass, surround)
    window(L["lcd"], 0.010 * U, L["corner"] * U, glass, surround)
    vz = L["zones"]["visualizer"]
    bands_print(vz, vz[3] + 0.022, 0.014 * U, tab)
    on_glass("EQ", vz[0], vz[1] - 0.025, 0.017 * U, tab, "LEFT", 1.3)
    on_glass("MEMO 1  2  3  4", vz[2], vz[1] - 0.025, 0.017 * U, tab, "RIGHT", 1.3)
    mints = ("SRC", "DISP", "MENU", "|<<", "PLAY", ">>|", "PWR", "MODE", "LOUD")
    for k in CONTROLS + DECOR:
        m = k["label"] in mints
        key(k, FACE, mint if m else keycap, hole, dark if m else white, min(fit([k]), 0.22), r=0.05, height=0.11)
    slot(L["slot"], satin)
    tape_door(L["changer"], satin, flap)
    printed("GRAPHIC EQUALIZER", 0.88, 0.318, 0.015 * U, grey, "RIGHT", 1.3)
    printed("DISC CHANGER CONTROL", 0.32, 0.60, 0.015 * U, grey, "LEFT", 1.3)
    printed("AUTO REVERSE", 0.26, 0.688, 0.013 * U, grey, "LEFT", 1.3)
    return lights, glowing(mint)


def spectrum():
    """Silver stack with black glass bands: a receiver with a knob over a CD player that's mostly analyser."""
    lights = Lights(sun=1800, fill=100, softbox=0.7)
    silver = material("silver", (0.56, 0.58, 0.61), 0.30, metal=0.85, aniso=0.4, brushed=True, bump=0.06)
    edge = material("edge", (0.82, 0.83, 0.85), 0.18, metal=1.0)
    panel = material("panel", (0.003, 0.003, 0.004), 0.22, 0.5, coat=0.4)
    keycap = material("keycap", (0.34, 0.35, 0.37), 0.30, metal=0.8, aniso=0.3, brushed=True)
    darkkey = material("darkkey", (0.030, 0.031, 0.035), 0.3, 0.5)
    knurl = material("knurl", (0.02, 0.02, 0.022), 0.45, 0.4)
    spun = material("spun", (0.62, 0.63, 0.65), 0.24, metal=1.0, rings=True, bump=0.12)
    white = material("ink-white", srgb(0xE6E9EC), 0.5)
    ink = material("ink", srgb(0x2A2C30), 0.6)
    legend = legend_material("legend", srgb(0x24262A))
    tab = emitter("teal", srgb(0x2FD0B8), 0.5)
    dash()
    pocket()
    units(silver, 0.012 * U, edge=0.03, edge_mat=edge)
    # Black glass bands the displays sit in.
    for b in ((0.12, 0.125, 0.71, 0.345), (0.035, 0.545, 0.795, 0.935)):
        x0, y0, x1, y1 = rect(b)
        target(rbox("band", x0, y0, x1, y1, FACE - 0.02, FACE + 0.02, panel, 0.012 * U, bevel=0.01))
    glass = material("vfd", (0.002, 0.003, 0.006), 0.3, 0.2)
    surround = material("surround", (0.004, 0.004, 0.005), 0.5, 0.3)
    window(L["lcd"], 0.006 * U, L["corner"] * U, glass, surround)
    window(L["eq"], 0.006 * U, L["corner"] * U, glass, surround)
    def on_band(s, x, y, size, mat, align="CENTER", spacing=1.2):
        return text(s, X(x), Y(y) + size * 0.36, FACE + 0.021, size, mat, align, spacing)
    vz = L["zones"]["visualizer"]
    bands_print(vz, L["eq"][3] + 0.035, 0.016 * U, white, on_band)
    for side in (-1, 1):
        cx = (L["eq"][0] + 0.012) if side < 0 else (L["eq"][2] - 0.012)
        on_glass("(((" if side < 0 else ")))", cx, (vz[1] + vz[3]) / 2 + 0.01, 0.05 * U, tab, "CENTER", 0.5)
    for k in CONTROLS + DECOR:
        key(k, FACE, keycap, hole, legend, min(fit([k]), 0.22), r=0.06, height=0.10, edge=edge)
    knob(L["knob"], FACE, hole, knurl, spun, ridges=100, height=0.34)
    slot(L["slot"], edge)
    on_band("CD PLAYER", 0.05, 0.70, 0.022 * U, white, "LEFT", 1.25)
    on_band("DIGITAL SERVO", 0.05, 0.905, 0.014 * U, white, "LEFT", 1.25)
    on_band("16-BAND SPECTRUM ANALYZER", 0.785, 0.915, 0.014 * U, white, "RIGHT", 1.25)
    printed("1-BIT D/A CONVERTER", 0.95, 0.535, 0.013 * U, ink, "RIGHT", 1.25)
    printed("DISC RECEIVER  ·  4 × 45 W", 0.70, 0.115, 0.014 * U, white, "RIGHT", 1.25)
    return lights


def multi():
    """Black, nearly all window: fixed legends on the glass, keys down both sides, a chrome volume lever."""
    lights = Lights(sun=2000, fill=110, softbox=0.45)
    plastic = material("plastic", (0.018, 0.019, 0.022), 0.40, 0.45, bump=0.22, bump_scale=480)
    keycap = material("keycap", (0.024, 0.025, 0.028), 0.30, 0.5, bump=0.05, bump_scale=1200)
    chrome = material("chrome-ribbed", (0.42, 0.43, 0.45), 0.22, metal=1.0, aniso=0.6, brushed=True)
    lever = legend_material("legend-lever", srgb(0x16171A))
    legend = legend_material("legend", srgb(0xE0A040))
    blue = emitter("frame", srgb(0x3FA8FF), 0.45)
    amber = emitter("amber", srgb(0xF0A040), 0.45)
    ink = material("ink", srgb(0xC8962E), 0.55)
    dash()
    plate_box(0.006, 0.012, 0.994, 0.988, 0.02 * U, plastic, edge=0.05)
    window(L["lcd"], 0.010 * U, L["corner"] * U, material("vfd", (0.002, 0.003, 0.006), 0.25, 0.25),
           material("surround", (0.006, 0.006, 0.007), 0.5, 0.3))
    # The glass's fixed legends: framed names beside the keys, and three dials on the right.
    for k in DECOR:
        if k["label"] in ("REP", "SHUF", "PGM"):
            y = (k["box"][1] + k["box"][3]) / 2 + 0.012
            on_glass(k["label"], 0.145, y, 0.02 * U, blue, "LEFT", 1.2)
            x0, y0, x1, y1 = rect([0.14, k["box"][1] + 0.01, 0.235, k["box"][3] - 0.01])
            for a, b in (((x0, y0), (x1, y0)), ((x0, y1), (x1, y1)), ((x0, y0), (x0, y1)), ((x1, y0), (x1, y1))):
                tube("frame", [(a[0], a[1], FACE - 0.079), (b[0], b[1], FACE - 0.079)], 0.012, blue)
    for i, t in enumerate(("HIGH", "MID", "LOW")):
        cy = 0.40 + i * 0.12
        on_glass(t, 0.845, cy + 0.045, 0.015 * U, amber, "CENTER", 1.2)
        cx, cyw = X(0.82), Y(cy)
        pts = [(cx + math.cos(a) * 0.42, cyw + math.sin(a) * 0.42, FACE - 0.079)
               for a in [math.radians(-60 + j * 6) for j in range(21)]]
        tube("dial", pts, 0.02, blue)
    vz = L["zones"]["visualizer"]
    on_glass("VOL", (vz[0] + vz[2]) / 2, vz[3] - 0.03, 0.02 * U, amber, "CENTER", 1.3)
    for k in CONTROLS + DECOR:
        if k["id"] in ("vol_down", "vol_up"):
            k = dict(k, label="-" if k["id"] == "vol_down" else "+")
            key(k, FACE, chrome, hole, lever, 0.5, r=0.03, height=0.14, gap=0.012, face_up=0.25)
        else:
            key(k, FACE, keycap, hole, legend, min(fit([k]), 0.24), r=0.06, height=0.10)
    x0, y0, x1, y1 = rect([0.42, 0.80, 0.58, 0.95])
    for i in range(1, 24):
        x = x0 + (x1 - x0) * i / 24
        if abs(x - (x0 + x1) / 2) > 0.05:
            box("rib", x - 0.014, y0 + 0.10, x + 0.014, y0 + 0.42, FACE + 0.14, FACE + 0.17, chrome)
    printed("MD · CD CHANGER CONTROL", 0.50, 0.785, 0.016 * U, ink, "CENTER", 1.3)
    printed("4 × 40 W", 0.96, 0.785, 0.016 * U, ink, "RIGHT", 1.3)
    return lights


def bluetooth():
    """Gloss black, curved side keys edged with light strips, a big knob in a lit ring, a mic hole."""
    lights = Lights(sun=2200, fill=120, softbox=0.5)
    gloss = material("gloss-face", (0.006, 0.006, 0.007), 0.12, 0.6, coat=1.0)
    keycap = material("keycap", (0.012, 0.012, 0.014), 0.18, 0.55, coat=0.6)
    rubber = material("rubber", (0.022, 0.022, 0.025), 0.7, 0.3, bump=0.3, bump_scale=600)
    silver = material("silver-ring", (0.70, 0.71, 0.73), 0.2, metal=1.0, rings=True)
    led = legend_material("led", srgb(0x1C2F70))
    legend = legend_material("legend", srgb(0xC8CCD2))
    grey = material("ink", srgb(0x8A9098), 0.55)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.05 * U, gloss, edge=0.06)
    window(L["lcd"], 0.012 * U, 0.01 * U, material("lcd", (0.002, 0.003, 0.006), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    for k in CONTROLS + DECOR:
        x0, y0, x1, y1 = rect(k["box"])
        key(k, FACE, keycap, hole, legend, min(fit([k]), 0.24), r=min(0.18, (y1 - y0) / 2 - 0.05), height=0.12)
    # Light strips curving down the outer edge of each pair of side keys.
    for x, bow in ((0.055, -0.012), (0.545, 0.012)):
        pts = quad((X(bx(x)), Y(by(0.10))), (X(bx(x + bow)), Y(by(0.50))), (X(bx(x)), Y(by(0.90))))
        tube("strip", [(px_, py_, FACE + 0.03) for px_, py_ in pts], 0.045, led)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("ring", cx, cy, kr * 1.16, FACE - 0.05, FACE + 0.05, led, bevel=0.02)
    _, _, r = knob(L["knob"], FACE + 0.05, hole, rubber, rubber, ridges=36, cap_frac=0.80, height=0.42)
    gloss_cap(cx, cy, r * 0.80, FACE + 0.05 + 0.44, silver)
    cut_circle(X(bx(0.575)), Y(by(0.07)), 0.011 * U, FACE, 0.05, hole)
    printed("BLUETOOTH", bx(0.80), by(0.07), 0.026 * U, grey, "RIGHT", 1.25)
    printed("4 × 50 W", bx(0.965), by(0.05), 0.022 * U, grey, "RIGHT", 1.25)
    return lights


# ---- single DIN, more eras ----

def lip_frame(b, border, lw, mat, r):
    """A raised lip round an opening: what's inside is cut away by the opening's own cut."""
    x0, y0, x1, y1 = rect(b)
    m = border + lw
    target(rbox("lip", x0 - m, y0 - m, x1 + m, y1 + m, FACE - 0.1, FACE + 0.035, mat, r + m, bevel=lw * 0.6))


def rim(b, m, lw, mat, z0, z1):
    """A frame of four bars round an opening, m out from it and lw wide: stands on its own, uncut."""
    x0, y0, x1, y1 = rect(b)
    x0, y0, x1, y1 = x0 - m, y0 - m, x1 + m, y1 + m
    for a in ((x0 - lw, y0 - lw, x1 + lw, y0), (x0 - lw, y1, x1 + lw, y1 + lw), (x0 - lw, y0, x0, y1), (x1, y0, x1 + lw, y1)):
        box("rim", *a, z0, z1, mat, bevel=lw * 0.35, segments=3)


def chrome(modern=False):
    """
    A thick polished chrome frame round a black face ribbed in silver from end to end, big knurled
    chrome knobs, a chrome-framed window and keys in a chrome channel under it: a bank of chrome piano
    keys stamped in black, as on a 1950s radio, or black chiclets on the one made again today.
    """
    lights = Lights(sun=1800, fill=110, softbox=1.3)
    chrome_ = material("chrome", (0.95, 0.95, 0.97), 0.04, metal=1.0)
    knurled = material("chrome-knurled", (0.90, 0.91, 0.93), 0.10, metal=1.0, rings=True, bump=0.1)
    face = material("face", (0.010, 0.010, 0.011), 0.35, 0.45)
    rib = material("chrome-rib", (0.82, 0.83, 0.85), 0.12, metal=1.0)
    keycap = material("keycap", (0.008, 0.008, 0.009), 0.14, 0.6, coat=0.8)
    dark = material("knob-dark", (0.006, 0.006, 0.007), 0.20, 0.55, coat=0.6)
    legend = legend_material("legend", srgb(0xE8EAEC))
    dash()
    # The frame: chrome all over, its outer edge chamfered wide; the ribbed face sits a step inside it.
    plate_box(bx(0.004), by(0.01), bx(0.996), by(0.99), 0.035 * U, chrome_, edge=0.16)
    gx0, gy0, gx1, gy1 = bx(0.035), by(0.095), bx(0.965), by(0.905)
    cut_box(X(gx0), Y(gy1), X(gx1), Y(gy0), FACE, 0.07, hole, 0.012 * U, floor=face)
    box("step", X(gx0) - 0.03, Y(gy1) - 0.03, X(gx1) + 0.03, Y(gy0) + 0.03, FACE - 0.072, FACE - 0.068, chrome_)
    lcd, row = L["lcd"], [k["box"] for k in CONTROLS]
    keys_box = [min(b[0] for b in row), min(b[1] for b in row), max(b[2] for b in row), max(b[3] for b in row)]
    # Fine silver ribs from end to end, broken only by the window and the key channel.
    n = 34
    gaps = [(lcd[1] - 0.02, lcd[3] + 0.02, lcd[0] - 0.012, lcd[2] + 0.012),
            (keys_box[1] - 0.02, keys_box[3] + 0.02, keys_box[0] - 0.012, keys_box[2] + 0.012)]
    for i in range(n):
        y = gy0 + (gy1 - gy0) * (i + 0.5) / n
        spans = [(gx0 + 0.003, gx1 - 0.003)]
        for t, bot, l, r in gaps:
            if t < y < bot:
                spans = [(gx0 + 0.003, l), (r, gx1 - 0.003)]
        for a, b in spans:
            box("rib", X(a), Y(y) - 0.010, X(b), Y(y) + 0.010, FACE - 0.07, FACE - 0.035, rib, bevel=0.006, segments=2)
    # The window in a thin raised chrome frame, the keys in a chrome channel.
    rim(lcd, 0.008 * U, 0.016 * U, chrome_, FACE - 0.07, FACE + 0.04)
    window(lcd, 0.008 * U, 0.004 * U, material("vfd", (0.002, 0.003, 0.006), 0.3, 0.2),
           material("surround", (0.004, 0.004, 0.005), 0.5, 0.3))
    rim(keys_box, 0.010 * U, 0.016 * U, chrome_, FACE - 0.07, FACE + 0.04)
    x0, y0, x1, y1 = rect(keys_box)
    cut_box(x0 - 0.010 * U, y0 - 0.010 * U, x1 + 0.010 * U, y1 + 0.010 * U, FACE + 0.036, 0.22, hole, 0.012 * U,
            floor=material("channel", (0.004, 0.004, 0.005), 0.5, 0.3))
    if modern:
        for k in CONTROLS:
            key(k, FACE - 0.08, keycap, hole, legend, min(fit(CONTROLS), 0.2), r=0.07, height=0.24, bevel=0.05,
                round_bevel=0.08, cut_face=False)
    else:
        # 1950s push-buttons: rounded chrome caps close together, fluted top and bottom, the name
        # stamped in black on a smooth band across the middle.
        piano = material("chrome-key", (0.94, 0.94, 0.96), 0.05, metal=1.0)
        groove = material("flute", (0.03, 0.03, 0.035), 0.3, metal=1.0)
        ink = legend_material("stamp", srgb(0x111214))
        size = min(fit(CONTROLS), 0.18)
        for k in CONTROLS:
            x0, y0, x1, y1 = rect(k["box"])
            w, h, g = x1 - x0, y1 - y0, 0.010
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            top = FACE + 0.30
            parts = [rbox("key", x0 + g, y0 + g, x1 - g, y1 - g, FACE - 0.28, top, piano, w * 0.12, bevel=w * 0.14,
                          segments=10)]
            band = h * 0.17
            for i in range(9):
                fx = x0 + w * (0.20 + 0.60 * i / 8)
                for fy0, fy1 in ((y0 + h * 0.18, cy - band), (cy + band, y1 - h * 0.18)):
                    parts.append(box("flute", fx - w * 0.012, fy0, fx + w * 0.012, fy1, top - 0.01, top + 0.004, groove))
            if k["id"] == "play":
                parts.append(play_mark(cx + size * 0.2, cy, size * 0.62, ink, top + 0.005))
            else:
                parts.append(text(k["label"], cx, cy, top + 0.005, size, ink, face="BarlowSemiCondensed-SemiBold"))
            rd.KEYS.append(parts)
    # The knobs: knurled chrome outside, a black band, a chrome cap with a dark centre.
    for b in (L["knob"], L["trims"][0]["box"]):
        cx, cy, r = knob(b, FACE, hole, knurled, dark, ridges=140, height=0.42, cap_frac=0.76, r_frac=0.86)
        cylinder("cap", cx, cy, r * 0.60, FACE + 0.40, FACE + 0.52, chrome_, bevel=r * 0.06)
        cylinder("centre", cx, cy, r * 0.24, FACE + 0.52, FACE + 0.535, dark, bevel=r * 0.02)
    return lights


def tape():
    """Black, a tape door with tone sliders beside it, a recessed LCD, presets in clear green plastic."""
    lights = Lights(sun=2200, fill=120, softbox=0.4)
    plastic = material("plastic", (0.020, 0.021, 0.024), 0.45, 0.45, bump=0.28, bump_scale=420)
    keycap = material("keycap", (0.026, 0.027, 0.030), 0.34, 0.5, bump=0.05, bump_scale=1200)
    green = material("green", srgb(0x7CE08A), 0.30, 0.45, coat=0.3)
    satin = material("satin", (0.09, 0.095, 0.105), 0.35, metal=0.6)
    flap = material("flap", (0.012, 0.012, 0.014), 0.25, 0.5, coat=0.5)
    knurl = material("knurl", (0.02, 0.02, 0.022), 0.45, 0.4)
    white = legend_material("legend", srgb(0xD0D4D8))
    darkleg = legend_material("legend-dark", srgb(0x14301A))
    grey = material("ink", srgb(0x9097A0), 0.6)
    orange = material("ink-orange", srgb(0xE08A2A), 0.5)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.012 * U, plastic, edge=0.04)
    # The display: the app lights the panel and draws its segments into the window.
    window(L["lcd"], 0.012 * U, L["corner"] * U, material("lcd", (0.02, 0.022, 0.02), 0.5, 0.3),
           material("surround", (0.008, 0.008, 0.009), 0.5, 0.3))
    greens = ("SRC", "DISP")
    for k in CONTROLS + DECOR:
        g = k["label"] in greens or (k["id"] or "").startswith("preset")
        key(k, FACE, green if g else keycap, hole, darkleg if g else white, min(fit([k]), 0.2), r=0.04, height=0.11)
    tape_door(L["changer"], satin, flap)
    # Tone sliders: a slot each with a lever in it, a scale above and the name at the end.
    for t, (y0, y1) in (("TREB", (0.07, 0.17)), ("BASS", (0.22, 0.32))):
        sx0, sx1, sy = bx(0.04), bx(0.145), by((y0 + y1) / 2 + 0.015)
        cut_box(X(sx0), Y(sy) - 0.03, X(sx1), Y(sy) + 0.03, FACE, 0.15, hole, 0.03)
        lx = X(sx0 + (sx1 - sx0) * (0.62 if t == "TREB" else 0.45))
        box("lever", lx - 0.07, Y(sy) - 0.13, lx + 0.07, Y(sy) + 0.13, FACE - 0.05, FACE + 0.12, keycap, bevel=0.02)
        printed("-  ·  ·  ·  ·  ·  ·  +", (sx0 + sx1) / 2, by(y0 + 0.02), 0.016 * U, grey, spacing=1.0)
        printed(t, bx(0.15), by((y0 + y1) / 2 + 0.03), 0.016 * U, grey, "LEFT", 1.2)
    knob(L["knob"], FACE, hole, knurl, knurl, ridges=50, height=0.36)
    printed("AUTO REVERSE", bx(0.26), by(0.43), 0.015 * U, orange, "LEFT", 1.25)
    printed("BI-LEVEL  ·  20 W + 20 W", bx(0.525), by(0.43), 0.015 * U, grey, "RIGHT", 1.25)
    printed("METAL", L["changer"][2] - 0.008, L["changer"][3] - 0.012 * U / H, 0.014 * U, grey, "RIGHT", 1.25)
    return lights, glowing(green, day=0.04, night=0.12)


def red():
    """Matt black, a red segment window, a knob in a red ring, round lit keys and an icon panel."""
    lights = Lights(sun=2200, fill=120, softbox=0.45)
    plastic = material("plastic", (0.018, 0.019, 0.021), 0.55, 0.4, bump=0.25, bump_scale=480)
    keycap = material("keycap", (0.022, 0.023, 0.026), 0.45, 0.45, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.022, 0.022, 0.025), 0.7, 0.3, bump=0.3, bump_scale=600)
    gloss = material("gloss-panel", (0.006, 0.006, 0.007), 0.08, 0.6, coat=1.0)
    legend = legend_material("legend", srgb(0xE84034))
    ring = legend_material("ring", srgb(0x701810))
    grey = material("ink", srgb(0x8A9098), 0.55)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.03 * U, plastic, edge=0.05)
    window(L["lcd"], 0.012 * U, 0.01 * U, material("lcd", (0.006, 0.002, 0.002), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    for k in CONTROLS + DECOR:
        x0, y0, x1, y1 = rect(k["box"])
        key(k, FACE, rubber if k["round"] else keycap, hole, legend, min(fit([k]), 0.22),
            r=min(0.08, (y1 - y0) / 2 - 0.05), height=0.11)
        if k["round"]:
            cx, cy = centre(k["box"])
            rr = min(x1 - x0, y1 - y0) / 2
            pts = [(cx + math.cos(a) * rr * 1.08, cy + math.sin(a) * rr * 1.08, FACE + 0.01)
                   for a in [2 * math.pi * j / 64 for j in range(65)]]
            tube("key-ring", pts, 0.022, ring)
    # A glossy panel with the inputs printed on it.
    px0, py0, px1, py1 = rect([bx(0.54), by(0.08), bx(0.835), by(0.44)])
    rbox("panel", px0, py0, px1, py1, FACE - 0.02, FACE + 0.04, gloss, 0.012 * U, bevel=0.01)
    for i, t in enumerate(("USB", "BT", "AUX", "SD", "MP3")):
        text(t, X(bx(0.57 + i * 0.058)), Y(by(0.375)) + 0.016 * U * 0.36, FACE + 0.041, 0.016 * U, grey, "CENTER", 1.1)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("ring", cx, cy, kr * 1.14, FACE - 0.05, FACE + 0.05, ring, bevel=0.02)
    knob(L["knob"], FACE + 0.05, hole, rubber, rubber, ridges=36, cap_frac=0.82, height=0.42)
    cut_circle(X(bx(0.163)), Y(by(0.93)), 0.008 * U, FACE, 0.05, hole)
    printed("4 × 50 W", bx(0.965), by(0.035), 0.016 * U, grey, "RIGHT", 1.25)
    return lights


def matte():
    """Matt black with a CD slot, a knob in a chrome ring, a white dot-matrix window and red-printed keys."""
    lights = Lights(sun=2000, fill=120, softbox=0.45)
    plastic = material("plastic", (0.028, 0.029, 0.032), 0.62, 0.35, bump=0.22, bump_scale=520)
    keycap = material("keycap", (0.030, 0.031, 0.034), 0.55, 0.4, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.020, 0.020, 0.023), 0.7, 0.3, bump=0.3, bump_scale=600)
    spun = material("chrome-spun", (0.88, 0.89, 0.91), 0.12, metal=1.0, rings=True, bump=0.08)
    satin = material("satin", (0.08, 0.085, 0.095), 0.35, metal=0.6)
    legend = legend_material("legend", srgb(0xD8504A))
    green = legend_material("legend-green", srgb(0x3CC060))
    grey = material("ink", srgb(0x8A9098), 0.55)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.02 * U, plastic, edge=0.05)
    window(L["lcd"], 0.012 * U, 0.008 * U, material("lcd", (0.002, 0.002, 0.003), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    for k in CONTROLS + DECOR:
        key(k, FACE, keycap, hole, green if k["id"] == "play" else legend, min(fit([k]), 0.2), r=0.05, height=0.09)
    slot(L["slot"], satin)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("bezel", cx, cy, kr * 1.16, FACE - 0.05, FACE + 0.07, spun, bevel=0.03)
    knob(L["knob"], FACE + 0.07, hole, rubber, rubber, ridges=40, cap_frac=0.84, height=0.40)
    # A USB port and a headphone-size aux jack on the right.
    ux, uy = X(bx(0.905)), Y(by(0.585))
    cut_box(ux - 0.045 * U * 0.6, uy - 0.016 * U * 0.6, ux + 0.045 * U * 0.6, uy + 0.016 * U * 0.6, FACE, 0.2, hole, 0.004)
    printed("USB", bx(0.905), by(0.66), 0.014 * U, grey, spacing=1.2)
    jx, jy = X(bx(0.90)), Y(by(0.87))
    cylinder("jack-ring", jx, jy, 0.016 * U, FACE - 0.02, FACE + 0.02, spun, bevel=0.005)
    cut_circle(jx, jy, 0.010 * U, FACE + 0.021, 0.2, hole)
    printed("CD  ·  BT  ·  USB", L["lcd"][2], by(0.745), 0.014 * U, grey, "RIGHT", 1.25)
    return lights


def heritage():
    """Black, square keys, presets in clear red, a knob in a lit ring, a flip-down cover over the display."""
    lights = Lights(sun=2200, fill=120, softbox=0.5)
    plastic = material("plastic", (0.018, 0.019, 0.021), 0.45, 0.45, bump=0.22, bump_scale=480)
    cover = material("cover", (0.020, 0.021, 0.024), 0.30, 0.5, bump=0.12, bump_scale=700)
    keycap = material("keycap", (0.022, 0.023, 0.026), 0.32, 0.5, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.020, 0.020, 0.023), 0.7, 0.3, bump=0.3, bump_scale=600)
    clear = material("clear-red", srgb(0xE8503C), 0.28, 0.45, coat=0.4)
    white = legend_material("legend", srgb(0xDDE0E4))
    darkleg = legend_material("legend-dark", srgb(0x3A0E08))
    grey = material("ink", srgb(0xB4B9C0), 0.55)
    red = material("ink-red", srgb(0xD8302A), 0.5)
    lamp = emitter("lamp", srgb(0xFF5A3A), 0.9)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.015 * U, plastic, edge=0.05)
    window(L["lcd"], 0.012 * U, 0.008 * U, material("lcd", (0.003, 0.002, 0.002), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    # The flip-down cover, standing proud with a lip along its lower edge.
    cx0, cy0, cx1, cy1 = rect([bx(0.35), by(0.07), bx(0.80), by(0.36)])
    rbox("cover", cx0, cy0, cx1, cy1, FACE - 0.02, FACE + 0.10, cover, 0.008 * U, bevel=0.02)
    box("cover-lip", cx0 + 0.04, cy0 - 0.02, cx1 - 0.04, cy0 + 0.05, FACE + 0.06, FACE + 0.14, cover, bevel=0.015)
    def on_cover(t, x, y, size, mat, align):
        return text(t, X(x), Y(y) + size * 0.36, FACE + 0.101, size, mat, align, 1.25)
    on_cover("DOT MATRIX DISPLAY", bx(0.785), by(0.13), 0.017 * U, grey, "RIGHT")
    on_cover("MOSFET 50 W × 4", bx(0.785), by(0.20), 0.015 * U, red, "RIGHT")
    on_cover("MEDIA / SOURCE / AUX", bx(0.785), by(0.31), 0.015 * U, grey, "RIGHT")
    for k in CONTROLS + DECOR:
        p = (k["id"] or "").startswith("preset")
        x0, y0, x1, y1 = rect(k["box"])
        key(k, FACE, clear if p else keycap, hole, darkleg if p else white, min(fit([k]), 0.22),
            r=min(0.06, (y1 - y0) / 2 - 0.05), height=0.11)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("ring", cx, cy, kr * 1.14, FACE - 0.05, FACE + 0.04, lamp, bevel=0.02)
    knob(L["knob"], FACE + 0.04, hole, rubber, rubber, ridges=48, cap_frac=0.84, height=0.46)
    return lights, glowing(clear, day=0.10, night=0.15)


def twin():
    """
    Black, nearly filled by a satin silver frame: a wide band round each of two round green dials, a
    polished ring and a black collar ticked in grey inside it, the dials' fixed graphics printed in
    green on the glass. Between the dials a recessed panel holds a slim VOL rocker under a red lamp;
    under them, curved MODE and SOURCE keys and a bar of silver keys; a small black key in each corner.
    """
    lights = Lights(sun=2000, fill=110, softbox=0.9)
    plastic = material("plastic", (0.014, 0.015, 0.017), 0.40, 0.45, bump=0.22, bump_scale=480)
    silver = material("silver", (0.62, 0.63, 0.65), 0.30, metal=1.0)
    polished = material("chrome", (0.90, 0.91, 0.93), 0.08, metal=1.0)
    recess = material("recess", (0.30, 0.31, 0.33), 0.38, metal=1.0)
    keycap = material("keycap-silver", (0.55, 0.56, 0.58), 0.32, metal=0.9)
    black = material("keycap", (0.010, 0.010, 0.012), 0.18, 0.55, coat=0.6)
    collar = material("collar", (0.012, 0.012, 0.014), 0.45, 0.35)
    grey = material("tick", (0.16, 0.16, 0.17), 0.5)
    vfd = material("vfd", (0.002, 0.004, 0.003), 0.25, 0.25)
    legend = legend_material("legend", srgb(0xE0502A))
    blue = legend_material("legend-blue", srgb(0x5AA0FF))
    green = emitter("print", srgb(0x48F040), 0.55)
    lamp = emitter("lamp", srgb(0xFF3020), 2.5)
    dash()
    plate_box(0.004, 0.007, 0.996, 0.993, 0.02 * U, plastic, edge=0.05)
    (lx, ly), (rx, ry) = centre(L["eq"]), centre(L["lcd"])
    R = (rect(L["lcd"])[2] - rect(L["lcd"])[0]) / 2
    base = FACE + 0.10

    def ring(cx, cy, r, z, a0=0.0, a1=360.0, n=144):
        return [(cx + math.cos(math.radians(a0 + (a1 - a0) * i / n)) * r,
                 cy + math.sin(math.radians(a0 + (a1 - a0) * i / n)) * r, z) for i in range(n + 1)]

    # The frame: a stadium round both dials, its edge rounded over.
    rb = R * 1.44
    out = [(rx + math.cos(math.radians(-90 + 180 * i / 64)) * rb, ry + math.sin(math.radians(-90 + 180 * i / 64)) * rb)
           for i in range(65)]
    out += [(lx + math.cos(math.radians(90 + 180 * i / 64)) * rb, ly + math.sin(math.radians(90 + 180 * i / 64)) * rb)
            for i in range(65)]
    bez = target(prism("bezel", out, FACE - 0.1, base, silver))
    m = bez.modifiers.new("bevel", "BEVEL")
    m.width = 0.16
    m.segments = 6
    m.limit_method = "ANGLE"
    # Each dial: a polished ring, a black collar ticked in grey, the glass set in it.
    rc = R * 1.13
    glass_z = FACE - 0.04
    for cx, cy in ((lx, ly), (rx, ry)):
        cut_circle(cx, cy, rc, base, 0.30, hole)
        cylinder("collar", cx, cy, rc + 0.02, FACE - 0.12, glass_z - 0.012, collar)
        cylinder("glass", cx, cy, R + 0.02, glass_z - 0.012, glass_z, vfd)
        tube("rim", ring(cx, cy, rc + 0.08, base + 0.01), 0.10, polished)
        for i in range(120):
            a = 2 * math.pi * i / 120
            r0, r1 = R + 0.05, R + (0.24 if i % 10 == 0 else 0.15)
            c_, s_, w = math.cos(a), math.sin(a), 0.012
            flat("tick", [(cx + c_ * r0 + s_ * w, cy + s_ * r0 - c_ * w, glass_z - 0.011),
                          (cx + c_ * r1 + s_ * w, cy + s_ * r1 - c_ * w, glass_z - 0.011),
                          (cx + c_ * r1 - s_ * w, cy + s_ * r1 + c_ * w, glass_z - 0.011),
                          (cx + c_ * r0 - s_ * w, cy + s_ * r0 + c_ * w, glass_z - 0.011)], [(0, 1, 2, 3)], grey)
    # The dials' fixed graphics, printed on the glass and lit green: the app draws the spokes round
    # the edge, the volume and the track. Screen angles: 90 is straight down.
    z = glass_z + 0.001
    g = 0.026

    def arc(cx, cy, r, a0, a1, width=g):
        tube("arc", ring(cx, cy, r, z, -a1, -a0, 48), width, green)

    def word(t, cx, cy, dx, dy, size):
        text(t, cx + dx, cy - dy, z, size, green, spacing=1.15, face="BarlowSemiCondensed-SemiBold")

    # Left: dB beside the volume, a hub of rings with POS and DSO either side, FRONT, REAR and SUB
    # under it, an arc along the foot between LOW and HIGH.
    word("dB", lx, ly, 0.78, -0.62, 0.17)
    for r in (0.62, 0.40):
        tube("hub", ring(lx, ly - 0.1, r, z, n=72), g, green)
    cylinder("hub", lx, ly - 0.1, 0.16, z - 0.001, z, green)
    for t, dx in (("POS", -1.18), ("DSO", 1.18)):
        word(t, lx, ly, dx, -0.05, 0.19)
        x0 = lx + dx - 0.36
        tube("box", [(x0, ly + 0.24, z), (x0 + 0.72, ly + 0.24, z), (x0 + 0.72, ly - 0.16, z), (x0, ly - 0.16, z),
                     (x0, ly + 0.24, z)], g * 0.8, green)
    word("FRONT", lx, ly, -0.95, 0.72, 0.15)
    word("REAR", lx, ly, 0.95, 0.72, 0.15)
    word("SUB", lx, ly, 0, 1.02, 0.15)
    arc(lx, ly, 1.55, 55, 125, 0.05)
    word("LOW", lx, ly, -1.25, 1.62, 0.13)
    word("HIGH", lx, ly, 1.25, 1.62, 0.13)
    # Right: three disc rings joined along a curve, DISC and TRACK, and a wide arc under them.
    for i, dx in enumerate((-0.62, 0, 0.62)):
        tube("disc", ring(rx + dx, ry - 1.12 - (0.08 if i == 1 else 0), 0.21, z, n=48), g, green)
    word("TRACK", rx, ry, 0, 0.86, 0.12)
    word("DISC", rx, ry, -1.25, 0.95, 0.13)
    word("BANK", rx, ry, 1.25, 0.95, 0.13)
    arc(rx, ry, 1.62, 48, 132, 0.055)
    # The recessed panel between the dials, its red lamp, and the VOL rocker in it.
    up = next(k for k in CONTROLS if k["id"] == "vol_up")["box"]
    down = next(k for k in CONTROLS if k["id"] == "vol_down")["box"]
    px0, py0, px1, py1 = rect([up[0] - 0.012, up[1] - 0.045, up[2] + 0.012, down[3] + 0.02])
    cut_box(px0, py0, px1, py1, base, 0.12, hole, 0.14, floor=recess)
    cylinder("lamp", (px0 + px1) / 2, py1 - 0.17, 0.05, base - 0.12, base - 0.06, lamp)
    x0, y0, x1, y1 = rect([up[0], up[1], up[2], down[3]])
    cx = (x0 + x1) / 2
    top = base + 0.14
    parts = [rbox("key", x0 + 0.05, y0 + 0.04, x1 - 0.05, y1 - 0.04, base - 0.1, top, keycap, 0.26, bevel=0.06,
                  segments=4)]
    for t, by_ in (("+", Y(up[1]) - 0.38), ("VOL", (y0 + y1) / 2), ("\u2212", Y(down[3]) + 0.38)):
        parts.append(text(t, cx, by_, top + 0.001, 0.30 if len(t) == 1 else 0.17, legend, spacing=1.1,
                          face="BarlowSemiCondensed-SemiBold"))
    rd.KEYS.append(parts)

    # MODE and SOURCE: curved keys whose ends follow the rings round the dials.
    size = fit([k for k in CONTROLS if k["id"] in ("menu", "source")])
    for k in CONTROLS:
        if k["id"] not in ("menu", "source"):
            continue
        b = k["box"]
        lim = (X(b[2]) - X(b[0])) / 2
        pts = []
        for i in range(13):
            wy = Y(b[1]) - (Y(b[1]) - Y(b[3])) * i / 12
            d = max(0.0, (R * 1.38) ** 2 - (wy - ly) ** 2)
            half = min(lim, max(0.3, (rx - lx) / 2 - math.sqrt(d)))
            pts.append((wy, half))
        mid = (lx + rx) / 2
        outline = [(mid - h, wy) for wy, h in pts[::-1]] + [(mid + h, wy) for wy, h in pts]
        cap = prism("key", outline, base - 0.15, base + 0.12, keycap, bevel=0.05)
        cy_ = (Y(b[1]) + Y(b[3])) / 2
        rd.KEYS.append([cap, text(k["label"], mid, cy_, base + 0.121, size, legend, spacing=1.15,
                                  face="BarlowSemiCondensed-SemiBold")])
    # The bar under them, DISC on its left half and the track keys on its right.
    bar = [k for k in CONTROLS + DECOR if not k["round"] and k["id"] not in ("vol_up", "vol_down", "menu", "source")]
    b = [min(k["box"][0] for k in bar), min(k["box"][1] for k in bar), max(k["box"][2] for k in bar),
         max(k["box"][3] for k in bar)]
    x0, y0, x1, y1 = rect(b)
    target(rbox("bar", x0 - 0.10, y0 - 0.08, x1 + 0.10, y1 + 0.20, FACE - 0.1, base + 0.14, silver, 0.20, bevel=0.06))
    size = min(fit([k for k in bar if k["id"] in ("prev", "next")]), 0.24)
    for k in bar:
        key(k, base + 0.14, keycap, hole, legend, size * (1.3 if k["id"] is None else 1.0), r=0.05, height=0.06,
            bevel=0.03, gap=0.03, face_up=-0.12)
    disc = [k["box"] for k in DECOR if not k["round"]]
    track = [k["box"] for k in CONTROLS if k["id"] in ("prev", "play", "next")]
    for t, bb in (("DISC", disc), ("AMS", track)):
        text(t, X((bb[0][0] + bb[-1][2]) / 2), y1 + 0.10, base + 0.141, 0.13, legend, spacing=1.3,
             face="BarlowSemiCondensed-SemiBold")
    # The corner keys: small, round, black, lettered in blue.
    corners = [k for k in CONTROLS + DECOR if k["round"]]
    size = fit(corners) * 0.85
    for k in corners:
        x0, y0, x1, y1 = rect(k["box"])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        r = min(x1 - x0, y1 - y0) / 2 * 0.72
        cut_circle(cx, cy, r + 0.04, FACE, 0.2, hole)
        rd.KEYS.append([cylinder("key", cx, cy, r, FACE - 0.18, FACE + 0.10, black, bevel=0.05),
                        text(k["label"], cx, cy, FACE + 0.101, size, blue, face="BarlowSemiCondensed-SemiBold")])
    return lights


# ---- more units from photos ----

def usb_port(cx, cy, w, h, metal):
    """A USB-A socket: a metal shell round a dark opening with the contact tongue in it."""
    cut_box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, FACE + 0.031, 0.3, hole, 0.01)
    target(rbox("shell", cx - w / 2 - 0.02, cy - h / 2 - 0.02, cx + w / 2 + 0.02, cy + h / 2 + 0.02, FACE - 0.05,
                FACE + 0.03, metal, 0.02, bevel=0.008))
    box("tongue", cx - w * 0.38, cy - h * 0.05, cx + w * 0.38, cy + h * 0.28, FACE - 0.25, FACE - 0.06,
        material("tongue", (0.02, 0.02, 0.022), 0.5, 0.3))


def jack(cx, cy, r, metal):
    """A 3.5 mm aux jack: a metal ring round a dark hole."""
    cylinder("jack-ring", cx, cy, r * 1.6, FACE - 0.02, FACE + 0.02, metal, bevel=0.005)
    cut_circle(cx, cy, r, FACE + 0.021, 0.2, hole)


def menu_mark(cx, cy, size, mat, z):
    """Three bars: a menu key's sign."""
    w, t = size * 0.75, size * 0.11
    v, f = [], []
    for dy in (-size * 0.28, 0, size * 0.28):
        i = len(v)
        v += [(cx - w / 2, cy + dy - t / 2, z), (cx + w / 2, cy + dy - t / 2, z), (cx + w / 2, cy + dy + t / 2, z),
              (cx - w / 2, cy + dy + t / 2, z)]
        f.append((i, i + 1, i + 2, i + 3))
    return flat("menu", v, f, mat)


def chevrons(cx, cy, size, left, mat, z):
    """Two chevrons, the font having no such sign: << or >>."""
    t, h = size * 0.10, size * 0.32
    v, f = [], []
    for dx in (-size * 0.2, size * 0.2):
        tip = cx + dx + (-h / 2 if left else h / 2)
        back = cx + dx + (h / 2 if left else -h / 2)
        for sy in (1, -1):
            i = len(v)
            v += [(back, cy + sy * h, z), (back + (t if left else -t) * 1.4, cy + sy * h, z),
                  (tip + (t if left else -t) * 1.4, cy, z), (tip, cy, z)]
            f.append((i, i + 1, i + 2, i + 3) if (sy > 0) == left else (i + 3, i + 2, i + 1, i))
    return flat("chevrons", v, f, mat)


def lit_ring(cx, cy, r, mat, z=FACE + 0.01, width=0.022):
    pts = [(cx + math.cos(a) * r, cy + math.sin(a) * r, z) for a in [2 * math.pi * j / 72 for j in range(73)]]
    return tube("key-ring", pts, width, mat)


def amber():
    """
    Matt black: a red segment window, ports for a card, USB, aux and USB-C on a gloss panel beside it,
    a knob in a ring lit orange, round keys ringed in orange, and rows of keys whose legends glow orange.
    """
    lights = Lights(sun=2200, fill=120, softbox=0.45)
    plastic = material("plastic", (0.014, 0.015, 0.017), 0.55, 0.4, bump=0.25, bump_scale=480)
    keycap = material("keycap", (0.020, 0.021, 0.024), 0.42, 0.45, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.020, 0.020, 0.023), 0.7, 0.3, bump=0.3, bump_scale=600)
    gloss = material("gloss-panel", (0.005, 0.005, 0.006), 0.08, 0.6, coat=1.0)
    steel = material("steel", (0.72, 0.73, 0.75), 0.22, metal=1.0)
    legend = legend_material("legend", srgb(0xF0802E))
    ring = legend_material("ring", srgb(0x8A3A10))
    grey = material("ink", srgb(0x7A8088), 0.55)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.03 * U, plastic, edge=0.05)
    window(L["lcd"], 0.012 * U, 0.008 * U, material("lcd", (0.006, 0.002, 0.002), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    for k in CONTROLS + DECOR:
        x0, y0, x1, y1 = rect(k["box"])
        key(k, FACE, rubber if k["round"] else keycap, hole, legend, min(fit([k]), 0.2),
            r=min(0.07, (y1 - y0) / 2 - 0.05), height=0.11)
        if k["round"]:
            cx, cy = centre(k["box"])
            lit_ring(cx, cy, min(x1 - x0, y1 - y0) / 2 * 1.08, ring)
    # The gloss panel: a card slot along its top, then USB, aux and USB-C.
    px0, py0, px1, py1 = rect([bx(0.582), by(0.19), bx(0.866), by(0.47)])
    target(rbox("panel", px0, py0, px1, py1, FACE - 0.02, FACE + 0.03, gloss, 0.012 * U, bevel=0.01))
    sx0, sy, sx1 = X(bx(0.60)), Y(by(0.245)), X(bx(0.85))
    cut_box(sx0, sy - 0.025, sx1, sy + 0.025, FACE + 0.031, 0.2, hole, 0.02)
    usb_port(X(bx(0.635)), Y(by(0.36)), 0.66, 0.24, steel)
    jack(X(bx(0.715)), Y(by(0.36)), 0.07, steel)
    cx = X(bx(0.80))
    cut_box(cx - 0.22, Y(by(0.36)) - 0.08, cx + 0.22, Y(by(0.36)) + 0.08, FACE + 0.031, 0.3, hole, 0.075)
    target(rbox("usbc", cx - 0.25, Y(by(0.36)) - 0.11, cx + 0.25, Y(by(0.36)) + 0.11, FACE - 0.05, FACE + 0.03, steel,
                0.1, bevel=0.008))
    for t, x in (("USB", 0.635), ("AUX", 0.715), ("TYPE-C", 0.80)):
        text(t, X(bx(x)), Y(by(0.445)), FACE + 0.031, 0.014 * U, grey, "CENTER", 1.15)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("ring", cx, cy, kr * 1.13, FACE - 0.05, FACE + 0.05, ring, bevel=0.02)
    knob(L["knob"], FACE + 0.05, hole, rubber, rubber, ridges=40, cap_frac=0.84, height=0.44)
    cut_circle(X(bx(0.305)), Y(by(0.86)), 0.008 * U, FACE, 0.05, hole)
    printed("MX-21", bx(0.305), by(0.585), 0.020 * U, grey, "CENTER", 1.3)
    printed("4 × 50 W", bx(0.965), by(0.10), 0.015 * U, grey, "RIGHT", 1.25)
    return lights


def dab():
    """
    Black, after the radios of the 1980s: split tone keys either side of an open cover over a card
    slot, USB and aux, a knob in a ring lit amber, a four-way pad, an amber dot-matrix window with a
    stripe under it, and a block of square keys, every legend amber.
    """
    lights = Lights(sun=2200, fill=120, softbox=0.45)
    plastic = material("plastic", (0.016, 0.017, 0.019), 0.50, 0.42, bump=0.22, bump_scale=480)
    keycap = material("keycap", (0.022, 0.023, 0.026), 0.40, 0.45, bump=0.05, bump_scale=1200)
    rubber = material("rubber", (0.020, 0.020, 0.023), 0.7, 0.3, bump=0.3, bump_scale=600)
    tray = material("tray", (0.010, 0.010, 0.011), 0.35, 0.5)
    steel = material("steel", (0.72, 0.73, 0.75), 0.22, metal=1.0)
    legend = legend_material("legend", srgb(0xF0A040))
    ring = legend_material("ring", srgb(0x8A5A12))
    grey = material("ink", srgb(0x9AA0A8), 0.55)
    stripe = material("stripe", srgb(0xD87A1E), 0.4)
    dash()
    plate_box(bx(0.006), by(0.012), bx(0.994), by(0.988), 0.012 * U, plastic, edge=0.05)
    window(L["lcd"], 0.010 * U, 0.006 * U, material("lcd", (0.003, 0.002, 0.001), 0.15, 0.5, coat=0.6),
           material("surround", (0.004, 0.004, 0.005), 0.4, 0.3))
    # The pad's raised surround, and the top row's.
    for b in ([bx(0.203), by(0.405), bx(0.364), by(0.935)], [bx(0.030), by(0.08), bx(0.367), by(0.39)],
              [bx(0.743), by(0.08), bx(0.973), by(0.39)]):
        x0, y0, x1, y1 = rect(b)
        target(rbox("surround", x0, y0, x1, y1, FACE - 0.05, FACE + 0.04, plastic, 0.012 * U, bevel=0.02))
    for k in CONTROLS + DECOR:
        x0, y0, x1, y1 = rect(k["box"])
        top = FACE + 0.04 if (k["box"][1] < by(0.40) and (k["box"][2] < bx(0.37) or k["box"][0] > bx(0.74))) \
            or bx(0.20) < k["box"][0] < bx(0.36) else FACE
        lab = k["label"]
        parts = key(dict(k, label="" if lab == "≡" else lab), top, rubber if k["round"] else keycap, hole, legend,
                    min(fit([k]), 0.17), r=min(0.05, (y1 - y0) / 2 - 0.04), height=0.10, gap=0.035)
        if lab == "≡":
            parts.append(menu_mark((x0 + x1) / 2, (y0 + y1) / 2 + (y1 - y0) * 0.04, 0.30, legend, top + 0.101))
        elif lab in ("\u00ab", "\u00bb"):
            parts.append(chevrons((x0 + x1) / 2, (y0 + y1) / 2, 0.42, lab == "\u00ab", legend, top + 0.101))
    # The open cover: a recessed tray with a card slot, a USB port and an aux jack.
    cx0, cy0, cx1, cy1 = rect([bx(0.378), by(0.09), bx(0.735), by(0.38)])
    target(rbox("cover", cx0, cy0, cx1, cy1, FACE - 0.05, FACE + 0.06, plastic, 0.01 * U, bevel=0.02))
    cut_box(cx0 + 0.10, cy0 + 0.10, cx1 - 0.10, cy1 - 0.16, FACE + 0.061, 0.16, hole, 0.03, floor=tray)
    box("cover-lip", cx0 + 0.3, cy1 - 0.12, cx1 - 0.3, cy1 - 0.02, FACE + 0.05, FACE + 0.10, plastic, bevel=0.015)
    floor = FACE + 0.061 - 0.16
    ty = (cy0 + cy1) / 2 - 0.03
    sx = X(bx(0.44))
    rbox("card", sx - 0.36, ty - 0.035, sx + 0.36, ty + 0.035, floor, floor + 0.02, hole, 0.02)
    box("card-lip", sx - 0.40, ty - 0.10, sx + 0.40, ty - 0.06, floor, floor + 0.03, steel)
    ux = X(bx(0.555))
    rbox("usb-shell", ux - 0.36, ty - 0.14, ux + 0.36, ty + 0.14, floor, floor + 0.05, steel, 0.02, bevel=0.008)
    box("usb-hole", ux - 0.32, ty - 0.10, ux + 0.32, ty + 0.10, floor + 0.05, floor + 0.052, hole)
    box("usb-tongue", ux - 0.27, ty - 0.01, ux + 0.27, ty + 0.07, floor + 0.052, floor + 0.06,
        material("tongue", (0.02, 0.02, 0.022), 0.5, 0.3))
    jx = X(bx(0.675))
    cylinder("jack-ring", jx, ty, 0.12, floor, floor + 0.04, steel, bevel=0.005)
    cylinder("jack-hole", jx, ty, 0.07, floor + 0.04, floor + 0.042, hole)
    # Under the window, its stripe; the knob and its lit ring.
    sx0, sy0, sx1, sy1 = rect([bx(0.40), by(0.885), bx(0.69), by(0.897)])
    box("stripe", sx0, sy0, sx1, sy1, FACE, FACE + 0.004, stripe)
    printed("DIGITAL RADIO  ·  BT  ·  USB", bx(0.69), by(0.865), 0.016 * U, grey, "RIGHT", 1.25)
    cx, cy = centre(L["knob"])
    x0, y0, x1, y1 = rect(L["knob"])
    kr = min(x1 - x0, y1 - y0) / 2 * 0.80
    cylinder("ring", cx, cy, kr * 1.15, FACE - 0.05, FACE + 0.05, ring, bevel=0.02)
    knob(L["knob"], FACE + 0.05, hole, rubber, rubber, ridges=48, cap_frac=0.86, height=0.44)
    printed("SEL  \u00b7  STBY", bx(0.10), by(0.465), 0.020 * U, grey, "CENTER", 1.2)
    # Screw holes at the ends, where the frame goes.
    for fx in (0.016, 0.984):
        for fy in (0.24, 0.78):
            cut_circle(X(bx(fx)), Y(by(fy)), 0.008 * U, FACE, 0.05, hole)
    return lights


def rainbow():
    """
    Satin silver: a CD changer's slot across the top, a wide black window whose analyser runs through
    the rainbow, printed with its bands, a round blue key and the volume down the left, the presets down
    the right and a row of silver keys along the foot.
    """
    lights = Lights(sun=1800, fill=100, softbox=0.75)
    silver = material("silver", (0.58, 0.60, 0.63), 0.30, metal=0.85, aniso=0.4, brushed=True, bump=0.06)
    edge = material("edge", (0.82, 0.83, 0.85), 0.18, metal=1.0)
    band = material("band", (0.42, 0.43, 0.46), 0.26, metal=0.9, aniso=0.3, brushed=True)
    keycap = material("keycap", (0.62, 0.63, 0.66), 0.28, metal=0.8, aniso=0.3, brushed=True)
    blue = material("clear-blue", srgb(0x2E6CFF), 0.2, 0.5, coat=0.8)
    ink = material("ink", srgb(0x2A2C30), 0.6)
    white = material("ink-white", srgb(0xD8DCE2), 0.5)
    legend = legend_material("legend", srgb(0x24262A))
    dash()
    plate_box(0.004, 0.007, 0.996, 0.993, 0.025 * U, silver, edge=0.05, edge_mat=edge)
    # The changer's band across the top, its slot in it.
    x0, y0, x1, y1 = rect([0.17, 0.035, 0.975, 0.265])
    target(rbox("band", x0, y0, x1, y1, FACE - 0.02, FACE + 0.04, band, 0.015 * U, bevel=0.015))
    slot(L["slot"], edge)
    text("IN-DASH 6-DISC CD CHANGER", X(0.86), Y(0.20) + 0.12, FACE + 0.041, 0.016 * U, ink, "RIGHT", 1.3)
    glass = material("vfd", (0.002, 0.003, 0.006), 0.25, 0.25)
    surround = material("surround", (0.006, 0.006, 0.007), 0.5, 0.3)
    window(L["lcd"], 0.008 * U, L["corner"] * U, glass, surround)
    vz = L["zones"]["visualizer"]
    bands_print(vz, L["lcd"][3] - 0.012, 0.014 * U, white)
    for k in CONTROLS + DECOR:
        if k["id"] == "power":
            cx, cy = centre(k["box"])
            x0, y0, x1, y1 = rect(k["box"])
            r = min(x1 - x0, y1 - y0) / 2
            cut_circle(cx, cy, r, FACE, 0.2, hole)
            cylinder("bezel", cx, cy, r * 1.12, FACE - 0.03, FACE + 0.05, edge, bevel=0.02)
            key(k, FACE + 0.05, blue, hole, legend_material("legend-pwr", srgb(0xE8EEFF)), 0.17, height=0.12,
                round_bevel=0.10, cut_face=False)
        else:
            key(k, FACE, keycap, hole, legend, min(fit([k]), 0.22), r=0.05, height=0.10, edge=edge)
    printed("VOL", 0.085, 0.505, 0.016 * U, ink, "CENTER", 1.3)
    printed("CD · MP3 · TUNER", 0.03, 0.80, 0.015 * U, ink, "LEFT", 1.2)
    printed("4 × 50 W", 0.97, 0.835, 0.015 * U, ink, "RIGHT", 1.3)
    return lights, glowing(blue, day=0.15, night=0.6)


def matrix():
    """
    Dark chrome, nearly all window: a big blue dot-matrix display behind navy glass, a black strip of
    slim silver keys under it, two big polished knobs, the volume and a multi-control, and a small round
    chrome key at each end.
    """
    lights = Lights(sun=1900, fill=110, softbox=0.8)
    frame = material("dark-chrome", (0.20, 0.21, 0.23), 0.16, metal=1.0, aniso=0.3, brushed=True, bump=0.04)
    edge = material("edge", (0.70, 0.71, 0.74), 0.12, metal=1.0)
    strip = material("strip", (0.008, 0.008, 0.009), 0.25, 0.5, coat=0.5)
    keycap = material("keycap", (0.55, 0.56, 0.59), 0.24, metal=0.9, aniso=0.3, brushed=True)
    chrome = material("chrome", (0.90, 0.91, 0.93), 0.06, metal=1.0)
    spun = material("chrome-spun", (0.86, 0.87, 0.89), 0.12, metal=1.0, rings=True, bump=0.08)
    knurl = material("knurl", (0.70, 0.71, 0.73), 0.2, metal=1.0)
    legend = legend_material("legend", srgb(0x1A1C20))
    white = material("ink-white", srgb(0xC8CCD2), 0.5)
    dash()
    plate_box(0.004, 0.007, 0.996, 0.993, 0.03 * U, frame, edge=0.06, edge_mat=edge)
    # A black band round the window, carrying the strip of keys under it.
    x0, y0, x1, y1 = rect([0.095, 0.035, 0.905, 0.735])
    target(rbox("band", x0, y0, x1, y1, FACE - 0.02, FACE + 0.03, strip, 0.02 * U, bevel=0.02))
    glass = material("navy-glass", (0.002, 0.004, 0.012), 0.3, 0.2)
    surround = material("surround", (0.004, 0.004, 0.005), 0.5, 0.3)
    window(L["lcd"], 0.006 * U, L["corner"] * U, glass, surround)
    for k in CONTROLS + DECOR:
        if k["round"]:
            cx, cy = centre(k["box"])
            x0, y0, x1, y1 = rect(k["box"])
            r = min(x1 - x0, y1 - y0) / 2
            cut_circle(cx, cy, r * 1.06, FACE, 0.2, hole)
            parts = [cylinder("key", cx, cy, r * 0.94, FACE - 0.15, FACE + 0.14, chrome, bevel=r * 0.18)]
            parts.append(text(k["label"], cx, cy, FACE + 0.141, 0.15, legend, face="BarlowSemiCondensed-SemiBold"))
            rd.KEYS.append(parts)
        else:
            key(k, FACE + 0.03, keycap, hole, legend, min(fit([k]), 0.21), r=0.04, height=0.08, gap=0.03,
                edge=edge)
    # The two big knobs: polished caps in knurled chrome skirts.
    for b in [L["knob"]] + [t["box"] for t in L["trims"]]:
        cx, cy = centre(b)
        x0, y0, x1, y1 = rect(b)
        r = min(x1 - x0, y1 - y0) / 2 * 0.80
        cylinder("bezel", cx, cy, r * 1.12, FACE - 0.05, FACE + 0.04, edge, bevel=0.02)
        knob(b, FACE + 0.04, hole, knurl, spun, ridges=90, cap_frac=0.80, height=0.40)
        cylinder("cap-ring", cx, cy, r * 0.84, FACE + 0.40, FACE + 0.43, chrome, bevel=0.01)
    printed("WMA", 0.055, 0.785, 0.016 * U, white, "CENTER", 1.25)
    printed("MP3", 0.055, 0.83, 0.016 * U, white, "CENTER", 1.25)
    printed("AUTO", 0.93, 0.785, 0.016 * U, white, "CENTER", 1.25)
    printed("TA/EQ", 0.93, 0.83, 0.016 * U, white, "CENTER", 1.25)
    return lights


def wood(name, light, dark):
    """Woodgrain: long wavy bands along x, light and dark, with a fine pore bump and a satin lacquer."""
    m = material(name, light, 0.38, 0.5, coat=0.35)
    n, links = m.node_tree.nodes, m.node_tree.links
    coord = n.new("ShaderNodeTexCoord")
    mp = n.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (0.08, 1.6, 1.0)
    links.new(coord.outputs["Object"], mp.inputs["Vector"])
    wave = n.new("ShaderNodeTexWave")
    wave.wave_type = "BANDS"
    wave.bands_direction = "Y"
    wave.inputs["Scale"].default_value = 2.2
    wave.inputs["Distortion"].default_value = 9.0
    wave.inputs["Detail"].default_value = 6
    wave.inputs["Detail Scale"].default_value = 1.4
    links.new(mp.outputs["Vector"], wave.inputs["Vector"])
    ramp = n.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*dark, 1)
    ramp.color_ramp.elements[1].color = (*light, 1)
    ramp.color_ramp.elements[0].position = 0.25
    links.new(wave.outputs["Fac"], ramp.inputs["Fac"])
    p = n["Principled BSDF"]
    links.new(ramp.outputs["Color"], p.inputs["Base Color"])
    pore = n.new("ShaderNodeTexNoise")
    pore.inputs["Scale"].default_value = 900
    b = n.new("ShaderNodeBump")
    b.inputs["Strength"].default_value = 0.15
    b.inputs["Distance"].default_value = 0.002
    links.new(pore.outputs["Fac"], b.inputs["Height"])
    links.new(b.outputs["Normal"], p.inputs["Normal"])
    return m


def dial(modern=False):
    """
    An early-1970s factory radio: a chrome-edged woodgrain face, or black on the one made again today,
    a slide-rule dial behind glass with its scale printed on it (the app draws the needle), five tall
    black push-buttons under it in the same chrome frame, and a black knob at each end, TUNING and
    SW-VOL, on chrome skirts.
    """
    lights = Lights(sun=1900, fill=120, softbox=1.1)
    chrome_ = material("chrome", (0.94, 0.94, 0.96), 0.05, metal=1.0)
    satin = material("chrome-satin", (0.80, 0.81, 0.83), 0.18, metal=1.0, aniso=0.3, brushed=True, bump=0.04)
    if modern:
        face = material("face", (0.010, 0.010, 0.011), 0.42, 0.4, bump=0.18, bump_scale=600)
    else:
        face = wood("wood", srgb(0x6A4A30), srgb(0x2E1D10))
    recess = material("recess", (0.006, 0.006, 0.007), 0.5, 0.3)
    keycap = material("keycap", (0.010, 0.010, 0.011), 0.22, 0.55, coat=0.5)
    grip = material("knob", (0.012, 0.012, 0.013), 0.30, 0.5, coat=0.3)
    cap = material("knob-cap", (0.008, 0.008, 0.009), 0.14, 0.6, coat=0.8)
    legend = legend_material("legend", srgb(0xD8DADD))
    ink = material("ink", srgb(0xE6E8EB), 0.5)
    plate_ink = material("plate", (0.008, 0.008, 0.009), 0.4, 0.4)
    # The scale, printed on the glass and lit from behind at night.
    scale_ink = material("scale", srgb(0xE8EDE6 if modern else 0xF2EEE4), 0.5)
    glass = material("dial-glass", (0.020, 0.032, 0.026) if modern else (0.004, 0.004, 0.005), 0.25, 0.3, coat=0.6)
    dash()
    # The frame: chrome, a face set a step inside it.
    plate_box(bx(0.004), by(0.012), bx(0.996), by(0.988), 0.02 * U, chrome_, edge=0.10)
    gx0, gy0, gx1, gy1 = bx(0.016), by(0.055), bx(0.984), by(0.945)
    target(rbox("face", X(gx0), Y(gy1), X(gx1), Y(gy0), FACE - 0.05, FACE + 0.025, face, 0.008 * U, bevel=0.01))
    # The dial and the keys share one chrome-framed recess in the middle.
    keys_ = [k for k in CONTROLS if k["id"] in ("power", "source", "prev", "play", "next")]
    lcd = L["lcd"]
    mid = [lcd[0], lcd[1], lcd[2], max(k["box"][3] for k in keys_)]
    m = 0.012 * U
    rim(mid, m, 0.014 * U, chrome_, FACE - 0.06, FACE + 0.05)
    x0, y0, x1, y1 = rect(mid)
    cut_box(x0 - m, y0 - m, x1 + m, y1 + m, FACE + 0.03, 0.40, hole, 0.006 * U, floor=recess)
    rbox("dial", *rect(lcd), FACE - 0.25, FACE - 0.18, glass, 0.004 * U)
    # A thin chrome bar between the dial and the keys.
    bx0, _, bx1, _ = rect(lcd)
    by_ = Y(lcd[3]) - 0.012 * U
    box("bar", bx0, by_ - 0.025, bx1, by_ + 0.025, FACE - 0.25, FACE - 0.05, satin, bevel=0.01)
    # The scale: numbers along the top of the dial, ticks under them.
    sc = L["dialScale"] if "dialScale" in L else [lcd[0] + 0.02, lcd[1] + 0.03, lcd[2] - 0.02, lcd[1] + 0.155]
    z = FACE - 0.179
    nums = ["5\u00B75", "7", "8", "9", "10", "12", "14", "16"] if modern else ["5", "7", "9", "12", "16"]
    pos = [0.03, 0.17, 0.27, 0.37, 0.47, 0.63, 0.79, 0.95] if modern else [0.05, 0.27, 0.47, 0.70, 0.92]
    size = (sc[3] - sc[1]) * H * 0.42
    tx0, ty0, tx1, ty1 = rect(sc)
    for t, f in zip(nums, pos):
        x = tx0 + (tx1 - tx0) * f
        text(t, x, ty1 - (ty1 - ty0) * 0.42, z, size, scale_ink, face="BarlowSemiCondensed-SemiBold")
        if t == "5" and not modern:
            text("4", x + size * 0.38, ty1 - (ty1 - ty0) * 0.22, z, size * 0.5, scale_ink)
    if not modern:
        text("kHz", tx1 - (tx1 - tx0) * 0.005, ty1 - (ty1 - ty0) * 0.80, z, size * 0.45, scale_ink, "RIGHT")
    for i in range(41):
        x = tx0 + (tx1 - tx0) * (0.02 + 0.96 * i / 40)
        h = (ty1 - ty0) * (0.16 if i % 5 == 0 else 0.08)
        box("tick", x - 0.006, ty0 + (ty1 - ty0) * 0.06, x + 0.006, ty0 + (ty1 - ty0) * 0.06 + h, z, z + 0.001,
            scale_ink)
    box("scale-line", tx0 + (tx1 - tx0) * 0.02, ty0 + (ty1 - ty0) * 0.05, tx0 + (tx1 - tx0) * 0.98,
        ty0 + (ty1 - ty0) * 0.065, z, z + 0.001, scale_ink)
    # The push-buttons: tall black caps standing close together, a small legend on each.
    size = min(fit(keys_), 0.20)
    for k in keys_:
        key(k, FACE - 0.045, keycap, hole, legend, size, height=0.30, gap=0.025, bevel=0.03, r=0.03, cut_face=False)
    # The knobs: black, ridged, on chrome skirts; TUNING (MENU) on the left, SW-VOL on the right.
    tuning = [k for k in CONTROLS if k["id"] == "menu"][0]
    for b, grips in ((L["knob"], 60), (tuning["box"], 60)):
        cx, cy, r = knob(b, FACE, hole, grip, cap, ridges=grips, height=0.42, cap_frac=0.80, r_frac=0.72,
                         skirt=chrome_ if modern else satin)
        cylinder("skirt-ring", cx, cy, r * 1.22, FACE - 0.04, FACE + 0.06, chrome_, bevel=0.02)
    # Their names, on small black plates on the 1971 face, printed on the modern one.
    for t, b in ((["TUNING"], tuning["box"]), (["SW-VOL", "TONE"], L["knob"])):
        cx = X((b[0] + b[2]) / 2)
        top = Y(b[1]) + 0.02 * U
        sz = 0.034 * U
        for i, line in enumerate(t):
            y = top + (len(t) - 1 - i) * sz * 1.15 + sz * 0.6
            if not modern:
                w = len(line) * sz * 0.55 + sz * 0.5
                rbox("name-plate", cx - w / 2, y - sz * 0.6, cx + w / 2, y + sz * 0.6, FACE, FACE + 0.04,
                     plate_ink, sz * 0.15)
            text(line, cx, y, FACE + (0.041 if not modern else 0.026), sz, ink, face="BarlowSemiCondensed-SemiBold", spacing=1.15)

    def lit(pressed, night):
        p = scale_ink.node_tree.nodes["Principled BSDF"]
        p.inputs["Emission Color"].default_value = (*srgb(0xD8F5DE if modern else 0xFFE2B0), 1)
        p.inputs["Emission Strength"].default_value = 1.4 if night else 0.0
        g = glass.node_tree.nodes["Principled BSDF"]
        g.inputs["Emission Color"].default_value = (*srgb(0x2E6A48 if modern else 0x5A3A12), 1)
        g.inputs["Emission Strength"].default_value = 0.10 if night else 0.0
    return lights, lit


def r34():
    """
    A late-1990s factory pair: a navigation unit over a CD and MD changer control, titanium grey,
    in a dark brushed panel. Dark keys, a disc slot in each, the navigation keys round a joystick, the
    radio's light grey source keys, its MD slot, a small LCD and a knurled volume knob.
    """
    lights = Lights(sun=2100, fill=120, softbox=0.9)
    panel = material("panel", (0.050, 0.050, 0.052), 0.32, metal=0.85, aniso=0.5, brushed=True, bump=0.06)
    ti = material("titanium", (0.20, 0.19, 0.175), 0.30, metal=0.75, bump=0.10, bump_scale=900)
    edge = material("edge", (0.34, 0.33, 0.31), 0.22, metal=0.85)
    keycap = material("keycap", (0.016, 0.016, 0.018), 0.36, 0.5, bump=0.05, bump_scale=1200)
    light = material("keycap-light", (0.50, 0.51, 0.52), 0.30, 0.5, metal=0.4)
    knurl = material("knurl", (0.10, 0.10, 0.105), 0.30, metal=0.8)
    spun = material("spun", (0.40, 0.40, 0.41), 0.16, metal=1.0, rings=True, bump=0.08)
    satin = material("satin", (0.30, 0.30, 0.31), 0.25, metal=0.9)
    legend = legend_material("legend", srgb(0xE8EAEC))
    dark = legend_material("legend-dark", srgb(0x202124))
    ink = material("ink", srgb(0xE2E4E6), 0.5)
    inkd = material("ink-dark", srgb(0x1A1B1D), 0.5)
    dash()
    # The brushed panel the pair is set in, filling the screen.
    box("panel", -0.2, -0.2, W + 0.2, H + 0.2, -0.5, FACE - 0.12, panel)
    units(ti, 0.010 * U, edge=0.05, edge_mat=edge)
    nav, radio = L["units"]
    # Navigation unit: a disc slot across the top, its name, the keys.
    slot([nav[0] + 0.14, 0.085, nav[2] - 0.17, 0.125], satin)
    printed("NAVIGATION SYSTEM", 0.345, 0.285, 0.024 * U, ink, "LEFT", 1.1)
    printed("MULTI FUNCTION", 0.075, 0.20, 0.018 * U, ink, "LEFT", 1.1)
    printed("DIGITAL AUDIO", 0.91, 0.14, 0.014 * U, ink, "CENTER", 1.2)
    # Radio: CD slot, MD slot, the window.
    slot(L["slot"], satin)
    printed("CD/MD CHANGER CONTROL", 0.5, 0.568, 0.018 * U, ink, "CENTER", 1.15)
    ch = L["changer"]
    x0, y0, x1, y1 = rect(ch)
    target(rbox("md-lip", x0 - 0.04, y0 - 0.04, x1 + 0.04, y1 + 0.04, FACE - 0.1, FACE + 0.03, satin, 0.03, bevel=0.01))
    cut_box(x0, y0, x1, y1, FACE + 0.031, 0.25, hole, 0.01)
    box("md-door", x0 + 0.03, y0 + 0.03, x1 - 0.03, y1 - 0.03, FACE - 0.12, FACE - 0.05,
        material("md-door", (0.006, 0.006, 0.007), 0.3, 0.5), bevel=0.01)
    text("16Mbit SHOCK PROOF MEMORY", (x0 + x1) / 2, (y0 + y1) / 2, FACE - 0.049, 0.016 * U, ink, "CENTER", 1.1)
    window(L["lcd"], 0.008 * U, 0.004 * U, material("lcd", (0.02, 0.022, 0.016), 0.3, 0.3),
           material("surround", (0.010, 0.010, 0.011), 0.5, 0.3))
    printed("AUTO PRESET", 0.09, 0.82, 0.016 * U, ink, "LEFT", 1.1)
    printed("VOL", 0.915, 0.645, 0.022 * U, ink, "CENTER", 1.1)
    lights_keys = ("CD", "MD", "FM/AM")
    for k in CONTROLS + DECOR:
        x0, y0, x1, y1 = rect(k["box"])
        if k["round"]:
            # The joystick: a black dome in a ring, ENTER on it, four arrows round it.
            cx, cy = centre(k["box"])
            r = min(x1 - x0, y1 - y0) / 2
            cut_circle(cx, cy, r, FACE, 0.2, hole)
            cylinder("ring", cx, cy, r * 0.98, FACE - 0.05, FACE + 0.08, keycap, bevel=0.03)
            for i in range(8):
                a = math.radians(22.5 + 45 * i)
                cylinder("dot", cx + math.cos(a) * r * 0.88, cy + math.sin(a) * r * 0.88, r * 0.035, FACE + 0.08,
                         FACE + 0.085, ink)
            parts = [cylinder("stick", cx, cy, r * 0.62, FACE - 0.1, FACE + 0.22, keycap, bevel=r * 0.18)]
            parts.append(text("ENTER", cx, cy, FACE + 0.221, r * 0.22, legend, face="BarlowSemiCondensed-SemiBold"))
            for a, up in ((90, True), (270, False)):
                parts.append(tri(cx, cy + math.sin(math.radians(a)) * r * 0.42, r * 0.10, up, legend, FACE + 0.221))
            rd.KEYS.append(parts)
            continue
        light_ = k["label"] in lights_keys
        k = dict(k)
        if k["label"] in ("\u2227", "\u2228"):
            k["label"] = "\u25b2" if k["label"] == "\u2227" else "\u25bc"
        if k["id"] == "play":
            k["id"] = "cd"   # a source key: its name, not the play sign
        key(k, FACE, light if light_ else keycap, hole, dark if light_ else legend, min(fit([k]), 0.20),
            r=0.03, height=0.10, gap=0.03, edge=None)
    cx, cy, r = knob(L["knob"], FACE, hole, knurl, spun, ridges=120, height=0.42, cap_frac=0.78, r_frac=0.84)
    cylinder("knob-ring", cx, cy, r * 1.1, FACE - 0.04, FACE + 0.04, edge, bevel=0.02)
    return lights


def touch():
    """
    A 2013 touch-screen head unit: a glossy black tablet in a dark knit surround, the screen flat black
    glass with its fixed marks lit (back, list, signal, battery, the home button), and a shelf under it
    with backlit cyan VOL chevrons, a round power knob with an amber glow round it, a home key, and a
    blue light strip along the foot. The app draws the song, transport keys and clock on the glass.
    """
    lights = Lights(sun=1500, fill=100, softbox=0.35)
    knit = material("knit", (0.016, 0.016, 0.018), 0.75, 0.3, bump=0.6, bump_scale=160)
    gloss = material("gloss", (0.004, 0.004, 0.005), 0.12, 0.5, coat=0.35)
    shelf = material("shelf", (0.010, 0.010, 0.011), 0.22, 0.5, coat=0.6)
    satin = material("satin", (0.30, 0.31, 0.33), 0.25, metal=0.9)
    chrome_ = material("chrome", (0.85, 0.86, 0.88), 0.08, metal=1.0)
    glass = material("glass", (0.0005, 0.0005, 0.0006), 0.30, 0.2)
    pixel = emitter("pixel", srgb(0xF2F4F6), 1.6)
    dim = emitter("pixel-dim", srgb(0x9A9EA6), 1.0)
    amber = emitter("amber", srgb(0xFF7A1E), 0.45)
    blue = emitter("blue", srgb(0x2FB8F0), 3.0)
    legend = legend_material("legend", srgb(0x7FD6E2))
    box("dash", -1, -1, W + 1, H + 1, -0.6, -0.1, knit)
    # The tablet: rounded, glossy, the shelf under the glass narrower, as the unit tapers.
    plate_box(0.085, 0.025, 0.915, 0.70, 0.045 * U, gloss, edge=0.06, edge_mat=satin)
    plate_box(0.13, 0.62, 0.87, 0.955, 0.06 * U, shelf, edge=0.05, edge_mat=satin)
    lcd = L["lcd"]
    x0, y0, x1, y1 = rect(lcd)
    cut_box(x0, y0, x1, y1, FACE, 0.03, hole, 0.012 * U, floor=glass)
    # The amber light along the shelf under the glass, and the blue strip along the foot.
    bx0, by0, bx1, by1 = rect([0.20, 0.698, 0.80, 0.702])
    box("amber-line", bx0, by0, bx1, by1, FACE + 0.001, FACE + 0.004, amber)
    cx = X(0.5)
    for i in range(41):
        f = (i - 20) / 20
        x = cx + f * X(0.25)
        y = Y(0.925) + (f * f) * 0.18
        box("blue", x - 0.11, y - 0.035, x + 0.11, y + 0.035, FACE - 0.2, FACE + 0.01, blue)
    z = FACE - 0.022
    # Fixed marks on the glass: back, list, signal dots and LTE, battery, the home button.
    def bar(xa, ya, xb, yb, w, mat, zz):
        mx, my, ln = (xa + xb) / 2, (ya + yb) / 2, math.hypot(xb - xa, yb - ya)
        o = box("mark", -ln / 2, -w / 2, ln / 2, w / 2, zz, zz + 0.002, mat)
        o.location = (mx, my, 0)
        o.rotation_euler = (0, 0, math.atan2(yb - ya, xb - xa))
        return o
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    bxx, byy = centre(k["\u2039"]["box"])
    a = 0.012 * U
    bar(bxx + a * 0.5, byy + a, bxx - a * 0.5, byy, 0.006 * U, pixel, z)
    bar(bxx - a * 0.5, byy, bxx + a * 0.5, byy - a, 0.006 * U, pixel, z)
    lx, ly = centre(k["source"]["box"])
    for i in (-1, 0, 1):
        cylinder("dot", lx - a * 0.9, ly + i * a * 0.75, 0.0028 * U, z, z + 0.002, pixel)
        box("line", lx - a * 0.55, ly + i * a * 0.75 - 0.0022 * U, lx + a * 1.0, ly + i * a * 0.75 + 0.0022 * U, z,
            z + 0.002, pixel)
    sb = L.get("statusBar") or [0.13, 0.605, 0.87, 0.655]
    sx0, sy0, sx1, sy1 = rect(sb)
    sy = (sy0 + sy1) / 2
    for i in range(5):
        cylinder("signal", sx0 + 0.25 + i * 0.13, sy, 0.045, z, z + 0.002, pixel)
    text("LTE", sx0 + 0.25 + 5 * 0.13 + 0.05, sy, z, 0.20, pixel, "LEFT", 1.0)
    bw, bh = 0.42, 0.16
    bxr = sx1 - 0.18
    for xa, ya, xb, yb in ((bxr - bw, sy - bh / 2, bxr, sy - bh / 2 + 0.02), (bxr - bw, sy + bh / 2 - 0.02, bxr, sy + bh / 2),
                           (bxr - bw, sy - bh / 2, bxr - bw + 0.02, sy + bh / 2), (bxr - 0.02, sy - bh / 2, bxr, sy + bh / 2)):
        box("battery", xa, ya, xb, yb, z, z + 0.002, pixel)
    box("battery-fill", bxr - bw + 0.05, sy - bh / 2 + 0.05, bxr - 0.05, sy + bh / 2 - 0.05, z, z + 0.002, pixel)
    box("battery-tip", bxr + 0.005, sy - 0.035, bxr + 0.035, sy + 0.035, z, z + 0.002, pixel)
    hx, hy = centre(k["display"]["box"])
    hr = min(rect(k["display"]["box"])[2] - rect(k["display"]["box"])[0], sy1 - sy0) / 2
    cylinder("home-ring", hx, hy, hr * 0.92, z, z + 0.001, emitter("home-grey", srgb(0x3A3C40), 1.0))
    cylinder("home-in", hx, hy, hr * 0.78, z + 0.001, z + 0.002, material("home-dark", (0.004, 0.004, 0.005), 0.3, 0.4))
    q = hr * 0.30
    for xa, ya, xb, yb in ((hx - q, hy - q, hx + q, hy - q + 0.012), (hx - q, hy + q - 0.012, hx + q, hy + q),
                           (hx - q, hy - q, hx - q + 0.012, hy + q), (hx + q - 0.012, hy - q, hx + q, hy + q)):
        box("home-sq", xa, ya, xb, yb, z + 0.002, z + 0.004, dim)
    # The shelf: VOL chevrons and the word between them, the power knob, the home key.
    zt = FACE + 0.002
    w = 0.006 * U
    for kid, up in (("vol_down", False), ("vol_up", True)):
        cx_, cy_ = centre(k[kid]["box"])
        s_ = 0.014 * U
        d = s_ * 0.55 * (1 if up else -1)
        parts = [bar(cx_ - s_, cy_ - d, cx_, cy_ + d, w, legend, zt), bar(cx_, cy_ + d, cx_ + s_, cy_ - d, w, legend, zt)]
        rd.KEYS.append(parts)
    vx = (centre(k["vol_down"]["box"])[0] + centre(k["vol_up"]["box"])[0]) / 2
    text("VOL", vx, centre(k["vol_up"]["box"])[1], zt, 0.024 * U, legend, face="BarlowSemiCondensed-Medium", spacing=1.1)
    pcx, pcy = centre(k["power"]["box"])
    px0, py0, px1, py1 = rect(k["power"]["box"])
    pr = min(px1 - px0, py1 - py0) / 2 * 0.86
    cylinder("knob-glow", pcx, pcy, pr * 1.045, FACE - 0.05, FACE + 0.02, amber, bevel=0.01)
    cut_circle(pcx, pcy, pr * 1.02, FACE, 0.1, hole)
    parts = [cylinder("knob", pcx, pcy, pr, FACE - 0.08, FACE + 0.30, gloss, bevel=pr * 0.12),
             cylinder("knob-ring", pcx, pcy, pr * 1.0, FACE + 0.12, FACE + 0.16, chrome_, bevel=0.01)]
    zr = FACE + 0.301
    gr = pr * 0.30
    for i in range(26):
        t = math.radians(-50 + 280 * i / 25 - 90 + 180) if False else math.radians(120 - 300 * i / 25 + 90)
        x_, y_ = pcx + math.cos(t) * gr, pcy + math.sin(t) * gr
        o = box("pwr-arc", -gr * 0.08, -gr * 0.045, gr * 0.08, gr * 0.045, zr, zr + 0.002, legend)
        o.location = (x_, y_, 0)
        o.rotation_euler = (0, 0, t + math.pi / 2)
        parts.append(o)
    parts.append(box("pwr-bar", pcx - gr * 0.07, pcy, pcx + gr * 0.07, pcy + gr * 1.15, zr, zr + 0.002, legend))
    rd.KEYS.append(parts)
    hcx, hcy = centre(k["menu"]["box"])
    s_ = 0.016 * U
    w = 0.005 * U
    parts = [bar(hcx - s_ * 1.1, hcy + s_ * 0.05, hcx, hcy + s_ * 1.0, w, legend, zt),
             bar(hcx, hcy + s_ * 1.0, hcx + s_ * 1.1, hcy + s_ * 0.05, w, legend, zt),
             bar(hcx - s_ * 0.8, hcy + s_ * 0.25, hcx - s_ * 0.8, hcy - s_ * 0.9, w, legend, zt),
             bar(hcx + s_ * 0.8, hcy + s_ * 0.25, hcx + s_ * 0.8, hcy - s_ * 0.9, w, legend, zt),
             bar(hcx - s_ * 0.8, hcy - s_ * 0.9, hcx + s_ * 0.8, hcy - s_ * 0.9, w, legend, zt),
             bar(hcx - s_ * 0.22, hcy - s_ * 0.9, hcx - s_ * 0.22, hcy - s_ * 0.25, w, legend, zt),
             bar(hcx + s_ * 0.22, hcy - s_ * 0.9, hcx + s_ * 0.22, hcy - s_ * 0.25, w, legend, zt),
             bar(hcx - s_ * 0.22, hcy - s_ * 0.25, hcx + s_ * 0.22, hcy - s_ * 0.25, w, legend, zt)]
    rd.KEYS.append(parts)
    return lights


# ---- Factory units of the 2000s and 2010s: black or silver plastic, two knobs, rockers, a slot ----

def push_knob(b, grip, cap, ring=None, r_frac=0.86, height=0.30):
    """A knob that is also a key: it sinks when pressed, so its parts go with the keys."""
    cx, cy = centre(b)
    x0, y0, x1, y1 = rect(b)
    r = min(x1 - x0, y1 - y0) / 2 * r_frac
    cut_circle(cx, cy, r * 1.08, FACE, 0.2, hole)
    parts = []
    if ring:
        cylinder("knob-ring", cx, cy, r * 1.16, FACE - 0.04, FACE + 0.05, ring, bevel=0.015)
    parts.append(cylinder("grip", cx, cy, r * 0.96, FACE - 0.2, FACE + height, grip, bevel=r * 0.05))
    for i in range(72):
        a = 2 * math.pi * i / 72
        rid = box("ridge", -r * 0.03, -r * 0.018, r * 0.03, r * 0.018, FACE + 0.04, FACE + height - 0.03, grip,
                  bevel=r * 0.008, segments=2)
        rid.location = (cx + math.cos(a) * r, cy + math.sin(a) * r, 0)
        rid.rotation_euler = (0, 0, a)
        parts.append(rid)
    parts.append(cylinder("cap", cx, cy, r * 0.8, FACE + height - 0.05, FACE + height + 0.02, cap, bevel=r * 0.04))
    rd.KEYS.append(parts)
    return cx, cy, r


def vol_knob(b, grip, cap, ring=None, r_frac=0.86):
    cx, cy, r = knob(b, FACE, hole, grip, cap, ridges=72, height=0.30, cap_frac=0.8, r_frac=r_frac)
    if ring:
        cylinder("knob-ring", cx, cy, r * 1.16, FACE - 0.04, FACE + 0.05, ring, bevel=0.015)
    return cx, cy, r


def factory_keys(keycap, legend, skip=(), mats=None, legends=None, r=0.02, height=0.10, gap=0.03, size=None):
    """Every key but those in skip: rockers get triangles, the play key its sign, CALL and END their colours."""
    mats = mats or {}
    legends = legends or {}
    for k in CONTROLS + DECOR:
        if k["label"] in skip or (k["id"] or "") in skip:
            continue
        k = dict(k)
        lab = k["label"]
        if lab in ("∧", "∨"):
            k["label"] = "▲" if lab == "∧" else "▼"
        if lab == "▶":
            k["id"] = "play"
        elif k["id"] == "play" and lab in ("CD", "DISC"):
            k["id"] = "cd"   # a source key that plays: its name, not the play sign
        pid = (k["id"] or "")
        m = mats.get(lab) or (mats.get("preset") if pid.startswith("preset") else None) or keycap
        lg = legends.get(lab) or (legends.get("preset") if pid.startswith("preset") else None) or legend
        key(k, FACE, m, hole, lg, size or min(fit([k]), 0.20), r=r, height=height, gap=gap, edge=None)


def print_only(label, mat, size):
    """A key that is only its print, as PWR·VOL above a knob: it sinks with the keys when pressed."""
    for k in CONTROLS:
        if k["label"] == label:
            cx, cy = centre(k["box"])
            rd.KEYS.append([text(label, cx, cy, FACE + 0.001, size, mat, face="BarlowSemiCondensed-SemiBold")])


def factory_mats(face_rgb, rough=0.55):
    plastic = material("plastic", face_rgb, rough, 0.4, bump=0.25, bump_scale=900)
    gloss = material("gloss", (0.006, 0.006, 0.007), 0.10, 0.5, coat=0.8)
    keycap = material("keycap", (0.014, 0.014, 0.016), 0.40, 0.5, bump=0.05, bump_scale=1200)
    satin = material("satin", (0.30, 0.30, 0.31), 0.25, metal=0.9)
    chrome_ = material("chrome", (0.85, 0.86, 0.88), 0.07, metal=1.0)
    grip = material("grip", (0.02, 0.02, 0.022), 0.35, 0.5)
    spun = material("spun", (0.42, 0.42, 0.43), 0.16, metal=1.0, rings=True, bump=0.08)
    return plastic, gloss, keycap, satin, chrome_, grip, spun


def lcd_window(b, panel_rgb):
    window(b, 0.008 * U, 0.004 * U, material("lcd", panel_rgb, 0.3, 0.3),
           material("surround", (0.010, 0.010, 0.011), 0.5, 0.3))


def f05():
    """Mid-2000s: black, a curved top over the slot, two knobs, a big LCD, presets, a row of source keys."""
    lights = Lights(sun=2100, fill=120, softbox=0.5)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.022, 0.022, 0.024))
    legend = legend_material("legend", srgb(0xE6E8EA))
    ink = material("ink", srgb(0xD8DADC), 0.5)
    gold = material("gold", srgb(0xC9A24A), 0.35, metal=0.6)
    dash()
    # The face: a curved top edge, as the unit's top arches over the disc slot.
    pts = [(X(0.025), Y(0.95)), (X(0.975), Y(0.95))]
    for i in range(33):
        f = i / 32
        x = 0.975 - f * 0.95
        y = 0.075 - 0.035 * math.sin(math.pi * f)
        pts.append((X(x), Y(y)))
    plate(pts, plastic, edge=0.05, edge_mat=satin)
    slot(L["slot"], satin)
    printed("MP3", 0.20, 0.13, 0.022 * U, gold, "CENTER", 1.0)
    printed("WMA", 0.80, 0.13, 0.018 * U, ink, "CENTER", 1.0)
    lcd_window(L["lcd"], (0.020, 0.024, 0.018))
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    push_knob(k["menu"]["box"], grip, spun)
    vol_knob(L["knob"], grip, spun)
    printed("TUNE·FILE", 0.13, 0.215, 0.020 * U, ink)
    printed("PUSH", 0.13, 0.485, 0.013 * U, ink)
    printed("AUDIO CONT", 0.13, 0.505, 0.016 * U, ink)
    printed("PUSH", 0.87, 0.485, 0.013 * U, ink)
    printed("SEEK", 0.853, 0.885, 0.016 * U, ink)
    printed("TRACK", 0.853, 0.905, 0.016 * U, ink)
    print_only("PWR·VOL", legend, 0.020 * U)
    factory_keys(keycap, legend, skip=("menu", "PWR·VOL"), r=0.04)
    return lights


def f07():
    """Late 2000s: black, an amber LCD between two knobs, white presets, phone keys, rockers both sides."""
    lights = Lights(sun=2100, fill=120, softbox=0.5)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.020, 0.020, 0.022))
    legend = legend_material("legend", srgb(0xE6E8EA))
    dark = legend_material("legend-dark", srgb(0x26282B))
    green = legend_material("legend-green", srgb(0x3CC86A))
    red_ = legend_material("legend-red", srgb(0xE5483C))
    ink = material("ink", srgb(0xE2E4E6), 0.5)
    white = material("keycap-white", (0.72, 0.73, 0.74), 0.35, 0.5)
    dash()
    plate_box(0.025, 0.04, 0.975, 0.96, 0.03 * U, plastic, edge=0.05, edge_mat=satin)
    # A chrome line framing the middle, as these units had round the display and presets.
    rim([0.195, 0.20, 0.805, 0.70], 0.0, 0.004 * U, satin, FACE - 0.01, FACE + 0.02)
    slot(L["slot"], satin)
    lcd_window(L["lcd"], (0.022, 0.022, 0.016))
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    push_knob(k["menu"]["box"], grip, spun)
    vol_knob(L["knob"], grip, spun)
    printed("TUNE·FILE", 0.12, 0.215, 0.020 * U, ink)
    printed("PUSH", 0.12, 0.455, 0.012 * U, ink)
    printed("AUDIO CTRL", 0.12, 0.475, 0.015 * U, ink)
    printed("TYPE", 0.115, 0.632, 0.016 * U, ink)
    printed("FOLDER", 0.115, 0.652, 0.016 * U, ink)
    printed("SEEK", 0.885, 0.632, 0.016 * U, ink)
    printed("TRACK", 0.885, 0.652, 0.016 * U, ink)
    printed("MP3 WMA", 0.885, 0.775, 0.016 * U, ink)
    jack(X(0.885), Y(0.935), 0.010 * U, satin)
    print_only("PWR·VOL", legend, 0.020 * U)
    factory_keys(keycap, legend, skip=("menu", "PWR·VOL"), mats={"preset": white},
                 legends={"preset": dark, "CALL": green, "END": red_}, r=0.04)
    return lights


def f10():
    """2010: silver grey, a green dot-matrix LCD, slim presets, two big knobs in chrome rings at the foot."""
    lights = Lights(sun=2000, fill=130, softbox=0.9)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.03, 0.03, 0.034))
    silver = material("silver", (0.30, 0.31, 0.32), 0.38, metal=0.6, bump=0.1, bump_scale=900)
    legend = legend_material("legend", srgb(0xEDEFF2))
    ink = material("ink", srgb(0x1C1D20), 0.5)
    silverkey = material("keycap-silver", (0.36, 0.37, 0.38), 0.3, metal=0.7)
    dark = legend_material("legend-dark", srgb(0x1C1D20))
    dash()
    plate_box(0.03, 0.04, 0.97, 0.96, 0.04 * U, plastic, edge=0.05, edge_mat=silver)
    # The silver frame round the dark inner panel the display and keys sit in.
    rim([0.06, 0.055, 0.94, 0.945], 0.0, 0.022 * U, silver, FACE - 0.05, FACE + 0.05)
    slot(L["slot"], satin)
    lcd_window(L["lcd"], (0.020, 0.026, 0.018))
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    cx, cy, r = push_knob(k["play"]["box"], grip, chrome_, ring=chrome_, r_frac=0.80, height=0.34)
    rd.KEYS[-1].append(text("ENTER", cx, cy + r * 0.16, FACE + 0.38, r * 0.20, ink, face="BarlowSemiCondensed-SemiBold"))
    rd.KEYS[-1].append(text("LIST", cx, cy - r * 0.16, FACE + 0.38, r * 0.20, ink, face="BarlowSemiCondensed-SemiBold"))
    vcx, vcy, vr = vol_knob(L["knob"], grip, gloss, ring=chrome_, r_frac=0.80)
    printed("TUNE·FILE", 0.14, 0.85, 0.018 * U, legend)
    printed("VOLUME", 0.86, 0.85, 0.018 * U, legend)
    printed("SEEK·TRACK", 0.50, 0.735, 0.016 * U, legend)
    jack(X(0.40), Y(0.805), 0.010 * U, satin)
    print_only("PWR", legend, 0.022 * U)
    factory_keys(keycap, legend, skip=("play", "PWR"), mats={"preset": silverkey}, legends={"preset": dark}, r=0.03)
    return lights


def f12():
    """2012: black, a slot across the top, knobs in the top corners, a screen with presets either side."""
    lights = Lights(sun=2000, fill=120, softbox=0.5)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.018, 0.018, 0.020))
    legend = legend_material("legend", srgb(0xE6E8EA))
    ink = material("ink", srgb(0xE4E6E8), 0.5)
    glass = material("glass", (0.002, 0.0025, 0.003), 0.08, 0.5, coat=1.0)
    dash()
    plate_box(0.03, 0.04, 0.97, 0.96, 0.03 * U, plastic, edge=0.05, edge_mat=satin)
    slot(L["slot"], satin)
    window(L["lcd"], 0.010 * U, 0.004 * U, glass, material("surround", (0.008, 0.008, 0.009), 0.4, 0.3))
    rim([0.205, 0.345, 0.29, 0.685], 0.0, 0.003 * U, satin, FACE - 0.01, FACE + 0.015)
    rim([0.71, 0.345, 0.795, 0.685], 0.0, 0.003 * U, satin, FACE - 0.01, FACE + 0.015)
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    push_knob(k["TUNE"]["box"], grip, spun)
    vol_knob(L["knob"], grip, spun)
    printed("TUNE·FILE", 0.105, 0.135, 0.016 * U, ink)
    printed("SEEK\u00B7TRACK", 0.885, 0.345, 0.014 * U, ink)
    # A second thin slot at the foot, for a map card, and the aux jack.
    sb = [0.36, 0.86, 0.64, 0.885]
    x0, y0, x1, y1 = rect(sb)
    cut_box(x0, y0, x1, y1, FACE, 0.2, hole, 0.01)
    jack(X(0.25), Y(0.87), 0.010 * U, satin)
    print_only("PWR·VOL", legend, 0.018 * U)
    factory_keys(keycap, legend, skip=("TUNE", "PWR·VOL"), r=0.03)
    return lights


def f14():
    """2014: gloss black, a slot across the top, a knob and keys down the left, a wide touch screen."""
    lights = Lights(sun=1700, fill=110, softbox=0.45)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.012, 0.012, 0.013), rough=0.3)
    legend = legend_material("legend", srgb(0xE6E8EA))
    glass = material("glass", (0.0006, 0.0008, 0.0012), 0.25, 0.3)
    px = emitter("pixel", srgb(0xE8EEF6), 0.9)
    dim = emitter("pixel-dim", srgb(0x6E7A88), 0.6)
    row = emitter("row", srgb(0x16243A), 0.25)
    hi = emitter("row-hi", srgb(0x2E4C72), 0.30)
    dash()
    plate_box(0.025, 0.04, 0.975, 0.96, 0.035 * U, gloss, edge=0.05, edge_mat=satin)
    slot(L["slot"], satin)
    lcd = L["lcd"]
    x0, y0, x1, y1 = rect(lcd)
    cut_box(x0, y0, x1, y1, FACE, 0.03, hole, 0.006 * U, floor=glass)
    vol_knob(L["knob"], grip, gloss, ring=satin)
    # An SD slot beside the keys.
    sx0, sy0, sx1, sy1 = rect([0.158, 0.44, 0.17, 0.62])
    cut_box(sx0, sy0, sx1, sy1, FACE, 0.2, hole, 0.003)
    z = FACE - 0.022
    # The screen's fixed look: a status line, the preset list's rows, the transport keys' buttons, tabs.
    def zbox(b, mat, zz=z):
        a0, b0, a1, b1 = rect(b)
        return box("ui", a0, b0, a1, b1, zz, zz + 0.002, mat)
    zbox([0.205, 0.17, 0.945, 0.172], dim)
    text("FM", X(0.23), Y(0.195), z, 0.020 * U, px, "LEFT", 1.0)
    for i, kk in enumerate([c for c in CONTROLS if (c["id"] or "").startswith("preset")]):
        b = kk["box"]
        parts = [zbox(b, hi if i == 1 else row)]
        bx0, by0, bx1, by1 = rect(b)
        parts.append(text("%d." % (i + 1), bx0 + 0.12, (by0 + by1) / 2, z + 0.003, 0.020 * U, px, "LEFT", 1.0))
        rd.KEYS.append(parts)
    for kid, mark in (("prev", "|<<"), ("play", None), ("next", ">>|")):
        c = [c for c in CONTROLS if c["id"] == kid][0]
        b = c["box"]
        bx0, by0, bx1, by1 = rect(b)
        cx, cy = centre(b)
        parts = [zbox(b, row)]
        if mark:
            parts.append(text(mark, cx, cy, z + 0.003, 0.024 * U, px, "CENTER", 1.0))
        else:
            parts.append(play_mark(cx + 0.024 * U * 0.2, cy, 0.024 * U * 0.62, px, z + 0.003))
        rd.KEYS.append(parts)
    for i, t in enumerate(("Source", "Sound", "Settings")):
        text(t, X(0.30 + i * 0.22), Y(0.86), z, 0.020 * U, dim, "CENTER", 1.0)
    zbox([0.205, 0.82, 0.945, 0.822], dim)
    factory_keys(keycap, legend, skip=("PWR", "preset1", "preset2", "preset3", "preset4", "preset5", "preset6",
                                       "prev", "play", "next"), r=0.03)
    print_only("PWR", legend, 0.020 * U)
    return lights


def s35():
    """2008: charcoal plastic, an orange display between rockers, a slot, presets round a raised knob housing."""
    lights = Lights(sun=2000, fill=120, softbox=0.6)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.030, 0.031, 0.033), rough=0.62)
    keycap = material("keycap", (0.028, 0.029, 0.031), 0.55, 0.45, bump=0.12, bump_scale=1100)
    legend = legend_material("legend", srgb(0xE6E8EA))
    ink = material("ink", srgb(0xE2E4E6), 0.5)
    dash()
    plate_box(0.025, 0.035, 0.975, 0.965, 0.03 * U, plastic, edge=0.05, edge_mat=satin)
    # The display, a dark window, and the rockers' recessed frames either side of it.
    window(L["lcd"], 0.010 * U, 0.004 * U, material("lcd", (0.004, 0.010, 0.009), 0.25, 0.4),
           material("surround", (0.010, 0.010, 0.011), 0.45, 0.3))
    for b in ([0.064, 0.12, 0.156, 0.385], [0.853, 0.12, 0.945, 0.385]):
        x0, y0, x1, y1 = rect(b)
        cut_box(x0, y0, x1, y1, FACE, 0.06, hole, 0.012 * U, floor=material("well", (0.012, 0.012, 0.013), 0.5, 0.3))
    for x, a, b_ in ((0.11, "SEEK", "TRACK"), (0.899, "TUNE", "FF·REW")):
        text(a, X(x), Y(0.243), FACE - 0.05, 0.022 * U, ink, "CENTER", 1.0)
        text(b_, X(x), Y(0.264), FACE - 0.05, 0.022 * U, ink, "CENTER", 1.0)
    printed("CAT", 0.11, 0.105, 0.022 * U, ink)
    printed("CH/FOLDER", 0.899, 0.105, 0.022 * U, ink)
    printed("6CD CHANGER", 0.75, 0.105, 0.022 * U, ink)
    printed("MP3", 0.29, 0.105, 0.018 * U, ink)
    printed("AUX IN", 0.786, 0.75, 0.016 * U, ink)
    slot(L["slot"], satin)
    jack(X(0.786), Y(0.80), 0.010 * U, satin)
    # The raised housing round the knob, breaking the two rows of keys.
    kx0, ky0, kx1, ky1 = rect([0.415, 0.545, 0.585, 0.875])
    cx, cy = (kx0 + kx1) / 2, (ky0 + ky1) / 2
    o = cylinder("housing", cx, cy, (kx1 - kx0) / 2, FACE - 0.05, FACE + 0.06, plastic, bevel=0.03)
    o.scale = (1.0, (ky0 - ky1) / (kx1 - kx0) * -1 if ky0 < ky1 else (ky0 - ky1) / (kx1 - kx0), 1.0)
    vcx, vcy, vr = vol_knob(L["knob"], grip, gloss, ring=chrome_, r_frac=0.98)
    zt = FACE + 0.30 + 0.021
    text("PUSH", vcx, vcy + vr * 0.42, zt, vr * 0.16, ink, "CENTER", 1.0)
    text("PWR", vcx, vcy + vr * 0.20, zt, vr * 0.24, ink, "CENTER", 1.0)
    text("VOL", vcx, vcy - vr * 0.38, zt, vr * 0.24, ink, "CENTER", 1.0)
    for i in range(8):
        a = 2 * math.pi * i / 8
        cylinder("tick", vcx + math.cos(a) * vr * 1.10, vcy + math.sin(a) * vr * 1.10, vr * 0.025, FACE + 0.05,
                 FACE + 0.06, ink)
    for k in CONTROLS:
        if k["label"] == "PWR":
            px_, py_ = centre(k["box"])
            rd.KEYS.append([text("PWR", px_, py_, FACE + 0.061, 0.020 * U, legend, face="BarlowSemiCondensed-SemiBold")])
    factory_keys(keycap, legend, skip=("PWR",), r=0.012)
    return lights


def res():
    """2008: a wide black face, a row of keys across the top, a slot, knurled knobs with chrome rims."""
    lights = Lights(sun=2000, fill=120, softbox=0.6)
    plastic, gloss, keycap, satin, chrome_, grip, spun = factory_mats((0.022, 0.022, 0.024), rough=0.6)
    keycap = material("keycap", (0.020, 0.020, 0.022), 0.55, 0.45, bump=0.12, bump_scale=1100)
    knurl = material("knurl", (0.012, 0.012, 0.013), 0.45, 0.5, bump=0.5, bump_scale=2400)
    legend = legend_material("legend", srgb(0xE6E8EA))
    ink = material("ink", srgb(0xE2E4E6), 0.5)
    dash()
    plate_box(0.022, 0.115, 0.978, 0.89, 0.012 * U, plastic, edge=0.04, edge_mat=satin)
    # The top row stands proud in its own frame, as the slot band and the lower half are set back.
    rim([0.03, 0.124, 0.972, 0.288], 0.0, 0.004 * U, satin, FACE - 0.02, FACE + 0.01)
    window(L["lcd"], 0.006 * U, 0.003 * U, material("lcd", (0.003, 0.006, 0.006), 0.25, 0.4),
           material("surround", (0.010, 0.010, 0.011), 0.45, 0.3))
    slot(L["slot"], satin)
    printed("VOLUME", 0.121, 0.768, 0.016 * U, ink)
    printed("PUSH AUDIO/SELECT", 0.876, 0.48, 0.016 * U, ink)
    printed("TUNE/SCROLL", 0.876, 0.768, 0.016 * U, ink)
    printed("AUX", 0.88, 0.80, 0.014 * U, ink)
    jack(X(0.88), Y(0.84), 0.010 * U, satin)
    vol_knob(L["knob"], knurl, gloss, ring=chrome_, r_frac=0.80)
    k = {c["id"] or c["label"]: c for c in L["keys"]}
    push_knob(k["TUNE"]["box"], knurl, gloss, ring=chrome_, r_frac=0.80)
    print_only("PUSH ON", ink, 0.018 * U)
    factory_keys(keycap, legend, skip=("TUNE", "PUSH ON"), r=0.008)
    return lights


BUILD = {"classic85": classic, "digital96": digital, "modern": modern, "hifi84": hifi, "detach98": detachable,
         "eqstack89": eqstack, "level91": level, "trio93": trio, "spectrum97": spectrum, "multi2000": multi,
         "bt16": bluetooth, "chrome65": chrome, "chrome65m": lambda: chrome(True), "tape87": tape, "red19": red, "cd20": matte, "heritage23": heritage,
         "twin01": twin, "amber21": amber, "dab22": dab, "rainbow02": rainbow, "matrix04": matrix,
         "b110factory71": dial, "b110factory71m": lambda: dial(True), "r34factory99": r34, "cp13": touch,
         "n70factory05": f05, "e150factory08": f07, "xp90factory09": f10, "n70factory11": f12, "n70factory12": f14,
         "s35factory08": s35, "resfactory08": res}
made = BUILD[L["id"]]()
lights, on_press = made if isinstance(made, tuple) else (made, None)
rd.apply_cuts()
render_all(OUT, lights, press_depth=0.0 if L["id"] == "modern" else 0.06, on_press=on_press)

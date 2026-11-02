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


BUILD = {"classic85": classic, "digital96": digital, "modern": modern, "hifi84": hifi, "detach98": detachable,
         "eqstack89": eqstack, "level91": level, "trio93": trio, "spectrum97": spectrum, "multi2000": multi,
         "bt16": bluetooth, "chrome65": chrome, "chrome65m": lambda: chrome(True), "tape87": tape, "red19": red, "cd20": matte, "heritage23": heritage}
made = BUILD[L["id"]]()
lights, on_press = made if isinstance(made, tuple) else (made, None)
rd.apply_cuts()
render_all(OUT, lights, press_depth=0.0 if L["id"] == "modern" else 0.06, on_press=on_press)

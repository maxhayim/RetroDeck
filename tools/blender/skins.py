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
U = 9.0 * (0.72 if SINGLE else 1.0)
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
    if SINGLE:
        # The radio's slot in the dash, and the dash's own screen above it for the visualizer.
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


BUILD = {"classic85": classic, "digital96": digital, "modern": modern, "hifi84": hifi, "detach98": detachable}
made = BUILD[L["id"]]()
lights, on_press = made if isinstance(made, tuple) else (made, None)
rd.apply_cuts()
render_all(OUT, lights, press_depth=0.0 if L["id"] == "modern" else 0.06, on_press=on_press)

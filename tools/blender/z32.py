"""
Z32 Factory 1990, modelled in 3D and rendered to the images the app layers.

    blender -b --factory-startup -P tools/blender/z32.py -- <layout.json> <out dir> [samples]

The layout comes from the app (core/build/skin-renders/z32factory90.json, written by the render
test), so every key sits exactly on its touch area. Renders day.png, night.png and the same two
with every key pressed in; the app crops a pressed key out of those. ONLY=day,night renders just
those, and PCT=50 renders at half size, for trying things out.
"""
import json
import math
import os
import sys

import bmesh
import bpy

args = sys.argv[sys.argv.index("--") + 1:]
LAYOUT, OUT = args[0], args[1]
SAMPLES = int(args[2]) if len(args) > 2 else 128
HERE = os.path.dirname(os.path.abspath(__file__))
L = json.load(open(LAYOUT))

# The screen is 16 x 9 world units, seen straight on: 1 unit is 120 px at 1920 x 1080.
W, H = 16.0, 9.0
FACE = 0.30      # front of each unit
KEY = 0.13       # how far a key stands out
PRESS = 0.07     # how far a pressed key goes in
DEEP = 0.22      # how deep the openings in the face go
UP, DOWN = "▲", "▼"


def X(f): return f * W
def Y(f): return (1 - f) * H


def rect(b):
    """A layout box as world x0, y0, x1, y1, y up."""
    return X(b[0]), Y(b[3]), X(b[2]), Y(b[1])


# ---- scene ----

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "GPU"
scene.cycles.samples = SAMPLES
scene.cycles.use_adaptive_sampling = True
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = int(os.environ.get("PCT", "100"))
scene.view_settings.view_transform = "Standard"
prefs = bpy.context.preferences.addons["cycles"].preferences
prefs.compute_device_type = "METAL"
prefs.get_devices()
for d in prefs.devices:
    d.use = True

cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
scene.collection.objects.link(cam)
cam.data.type = "ORTHO"
cam.data.ortho_scale = W
cam.location = (W / 2, H / 2, 30)
scene.camera = cam

world = bpy.data.worlds.new("world")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]


# ---- materials ----

def material(name, color, rough, spec=0.5, bump=0.0, bump_scale=300.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    n = m.node_tree.nodes
    p = n["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Specular IOR Level"].default_value = spec
    if coat:
        p.inputs["Coat Weight"].default_value = coat
        p.inputs["Coat Roughness"].default_value = 0.08
    if bump:
        tex = n.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = bump_scale
        tex.inputs["Detail"].default_value = 8
        b = n.new("ShaderNodeBump")
        b.inputs["Strength"].default_value = bump
        b.inputs["Distance"].default_value = 0.002
        m.node_tree.links.new(tex.outputs["Fac"], b.inputs["Height"])
        m.node_tree.links.new(b.outputs["Normal"], p.inputs["Normal"])
    return m


def glow(name, color, strength, rough=0.55):
    """Lit from behind at night: plain by day, glowing when the backlight is on."""
    m = material(name, color, rough)
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Emission Color"].default_value = (1.0, 0.97, 0.92, 1)
    p.inputs["Emission Strength"].default_value = 0.0
    m["night"] = strength
    return m


dash = material("dash", (0.014, 0.014, 0.016), 0.62, 0.35, bump=0.35, bump_scale=220)
satin = material("satin", (0.008, 0.008, 0.009), 0.40, 0.5, bump=0.10, bump_scale=900)
frame = material("frame", (0.011, 0.011, 0.012), 0.34, 0.5, bump=0.08, bump_scale=700)
keycap = material("keycap", (0.010, 0.010, 0.011), 0.30, 0.55, bump=0.05, bump_scale=1200)
gloss = material("gloss", (0.003, 0.003, 0.004), 0.10, 0.6, coat=0.6)
hole = material("hole", (0.0, 0.0, 0.0), 1.0, 0.0)
well = glow("well", (0.0, 0.0, 0.0), 0.0, rough=1.0)
legend = glow("legend", (0.62, 0.63, 0.65), 2.2)
printed = material("print", (0.42, 0.43, 0.45), 0.6)
faint = material("faint", (0.22, 0.23, 0.24), 0.6)
knurl = material("knurl", (0.016, 0.016, 0.018), 0.45, 0.4)


# ---- shapes ----

def obj(name, mesh, mat):
    o = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(o)
    o.data.materials.append(mat)
    return o


def smooth(o, width, segments, angle_only=False):
    m = o.modifiers.new("bevel", "BEVEL")
    m.width = width
    m.segments = segments
    m.limit_method = "ANGLE" if angle_only else "NONE"
    o.modifiers.new("normals", "WEIGHTED_NORMAL")
    for p in o.data.polygons:
        p.use_smooth = True


def box(name, x0, y0, x1, y1, z0, z1, mat, bevel=0.0, segments=4):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x = x0 + (v.co.x + 0.5) * (x1 - x0)
        v.co.y = y0 + (v.co.y + 0.5) * (y1 - y0)
        v.co.z = z0 + (v.co.z + 0.5) * (z1 - z0)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me, mat)
    if bevel:
        smooth(o, bevel, segments)
    return o


def cylinder(name, cx, cy, r, z0, z1, mat, bevel=0.0, verts=96):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r, depth=z1 - z0)
    for v in bm.verts:
        v.co.x += cx
        v.co.y += cy
        v.co.z += (z0 + z1) / 2
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me, mat)
    if bevel:
        smooth(o, bevel, 5, angle_only=True)
    return o


FONT = bpy.data.fonts.load(os.path.join(HERE, "fonts", "BarlowSemiCondensed-SemiBold.ttf"))
FONT_PRINT = bpy.data.fonts.load(os.path.join(HERE, "fonts", "BarlowSemiCondensed-Medium.ttf"))


def text(body, x, y, z, size, mat, align="CENTER", spacing=1.0, font=None):
    cu = bpy.data.curves.new("t", "FONT")
    cu.body = body
    cu.font = font or FONT
    cu.size = size
    cu.space_character = spacing
    cu.align_x = align
    cu.align_y = "CENTER"
    o = bpy.data.objects.new("t:" + body, cu)
    scene.collection.objects.link(o)
    o.location = (x, y, z)
    cu.materials.append(mat)
    return o


def flat(name, verts, faces, mat):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    return obj(name, me, mat)


def tri(cx, cy, size, up, mat, z):
    """A printed triangle, as the font has none."""
    h = size * 0.87
    s = 1 if up else -1
    return flat("tri", [(cx - size / 2, cy - s * h / 2, z), (cx + size / 2, cy - s * h / 2, z), (cx, cy + s * h / 2, z)],
                [(0, 1, 2) if up else (0, 2, 1)], mat)


def play_mark(cx, cy, size, mat, z):
    """The play and pause symbols printed under PLAY."""
    a = size
    v = [(cx - a * 1.25, cy - a / 2, z), (cx - a * 0.4, cy, z), (cx - a * 1.25, cy + a / 2, z)]
    f = [(0, 1, 2)]
    for x in (cx + a * 0.2, cx + a * 0.72):
        i = len(v)
        v += [(x, cy - a / 2, z), (x + a * 0.28, cy - a / 2, z), (x + a * 0.28, cy + a / 2, z), (x, cy + a / 2, z)]
        f.append((i, i + 1, i + 2, i + 3))
    return flat("play", v, f, mat)


# Openings in the faces: cutters gathered here are taken out of both faces at the end.
CUTS = bpy.data.collections.new("cuts")
scene.collection.children.link(CUTS)
CUTS.hide_render = True


def cut(o):
    scene.collection.objects.unlink(o)
    CUTS.objects.link(o)
    o.display_type = "WIRE"


def cut_box(x0, y0, x1, y1):
    cut(box("cut", x0, y0, x1, y1, FACE - DEEP, FACE + 0.1, hole))


def cut_circle(cx, cy, r):
    cut(cylinder("cut", cx, cy, r, FACE - DEEP, FACE + 0.1, hole))


def floor_box(x0, y0, x1, y1, mat=None):
    """The bottom of an opening: black, or the bulbs' glow at night."""
    box("floor", x0 - 0.02, y0 - 0.02, x1 + 0.02, y1 + 0.02, FACE - DEEP - 0.03, FACE - DEEP + 0.005, mat or hole)


FACES = []


def unit(x0, y0, x1, y1):
    """One unit set into a pocket in the dash: a dark gap, a moulded frame, then the flat face."""
    box("pocket", x0 - 0.08, y0 - 0.08, x1 + 0.08, y1 + 0.08, -0.4, 0.002, hole)
    # The frame is solid behind the face, so the openings go through it as well.
    FACES.append(box("frame", x0, y0, x1, y1, -0.4, FACE - 0.03, frame, bevel=0.07))
    # Flat shaded: smoothing across the openings' long faces would streak.
    f = box("face", x0 + 0.13, y0 + 0.13, x1 - 0.13, y1 - 0.13, FACE - 0.36, FACE, satin)
    m = f.modifiers.new("bevel", "BEVEL")
    m.width = 0.02
    m.segments = 1
    FACES.append(f)


KEYS = []   # every key cap and its legend, so the pressed renders can push them in


def key(k, size):
    x0, y0, x1, y1 = rect(k["box"])
    gap = 0.06
    top = FACE + KEY
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    if k["round"]:
        r = min(x1 - x0, y1 - y0) / 2
        cut_circle(cx, cy, r)
        cylinder("floor", cx, cy, r + 0.02, FACE - DEEP - 0.03, FACE - DEEP + 0.005, well)
        top = FACE + KEY + 0.04
        parts = [cylinder("key", cx, cy, r - gap, FACE - DEEP + 0.02, top, keycap, bevel=0.07),
                 text("PLAY", cx, cy + r * 0.2, top + 0.001, size * 1.05, legend),
                 play_mark(cx, cy - r * 0.27, size * 0.42, legend, top + 0.001)]
    else:
        cut_box(x0, y0, x1, y1)
        floor_box(x0, y0, x1, y1, well)
        parts = [box("key", x0 + gap, y0 + gap, x1 - gap, y1 - gap, FACE - DEEP + 0.02, top, keycap, bevel=0.045)]
        label = k["label"]
        if label in (UP, DOWN):
            parts.append(tri(cx, cy + (y1 - y0) * 0.04, size * 0.62, label == UP, legend, top + 0.001))
        elif (k["id"] or "").startswith("preset"):
            # A tape key: its function, and the preset number small in the corner.
            name, num = label.split("|")
            parts.append(text(name, cx, cy + (y1 - y0) * 0.12, top + 0.001, size, legend))
            parts.append(text(num, x1 - gap - 0.16, y0 + gap + 0.17, top + 0.001, size * 0.72, legend))
        else:
            parts.append(text(label, cx, cy + (y1 - y0) * 0.04, top + 0.001, size, legend))
    KEYS.append(parts)


def knob(b, r_frac=0.80, ridges=64, skirt=True):
    x0, y0, x1, y1 = rect(b)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = min(x1 - x0, y1 - y0) / 2 * r_frac
    cut_circle(cx, cy, r * 1.07)
    cylinder("floor", cx, cy, r * 1.1, FACE - DEEP - 0.03, FACE - DEEP + 0.005, hole)
    if skirt:
        cylinder("skirt", cx, cy, r * 1.035, FACE - DEEP, FACE + 0.06, knurl, bevel=0.02)
    cylinder("knob", cx, cy, r * 0.97, FACE - DEEP, FACE + 0.42, keycap, bevel=r * 0.10)
    # Fine ridges round the grip.
    for i in range(ridges):
        a = 2 * math.pi * i / ridges
        rid = box("ridge", -r * 0.03, -r * 0.022, r * 0.03, r * 0.022, FACE + 0.06, FACE + 0.37, knurl,
                  bevel=r * 0.01, segments=2)
        rid.location = (cx + math.cos(a) * r * 1.0, cy + math.sin(a) * r * 1.0, 0)
        rid.rotation_euler = (0, 0, a)


# ---- the stereo ----

box("dash", -1, -1, W + 1, H + 1, -0.5, 0.0, dash)
unit(X(0.025), Y(0.475), X(0.975), Y(0.035))
unit(X(0.025), Y(0.965), X(0.975), Y(0.515))

for k in L["keys"]:
    key(k, 0.28)

PZ = FACE + 0.001
small = 0.17

knob(L["knob"])
x0, y0, x1, y1 = rect(L["knob"])
text("PUSH ON", x0, y1 + 0.12, PZ, small, printed, "LEFT", 1.25, FONT_PRINT)
text("VOL", x1, y1 + 0.12, PZ, small, printed, "RIGHT", 1.25, FONT_PRINT)
for t in L["trims"]:
    knob(t["box"], 0.62, 40, skirt=False)
    a, b, c, d = rect(t["box"])
    top, bottom = t["label"].split("|")
    text(top, (a + c) / 2, d + 0.12, PZ, small * 0.9, printed, spacing=1.25, font=FONT_PRINT)
    text(bottom, (a + c) / 2, b - 0.13, PZ, small * 0.9, faint, spacing=1.25, font=FONT_PRINT)

up = next(k for k in L["keys"] if k["label"] == UP)
a, b, c, d = rect(up["box"])
text("TUNE", c + 0.12, b - 0.12, PZ, small, printed, "LEFT", 1.25, FONT_PRINT)
a, b, c, d = rect(L["zones"]["corner"])
text("PLL SYNTHESIZED TUNER", (a + c) / 2, Y(0.43), PZ, small, printed, spacing=1.3, font=FONT_PRINT)

# The tape door: a gloss flap set back in its opening, the cassette slot along its foot.
DOOR = FACE - 0.05
a, b, c, d = rect(L["slot"])
cut_box(a, b, c, d)
floor_box(a, b, c, d)
box("door", a + 0.04, b + 0.04, c - 0.04, d - 0.04, FACE - DEEP, DOOR, gloss, bevel=0.03)
box("tape-slot", a + 0.35, b + 0.13, c - 0.35, b + 0.19, DOOR - 0.01, DOOR + 0.002, hole)
text("AUTO REVERSE", a + 0.35, b + 0.38, DOOR + 0.001, small * 0.95, printed, "LEFT", 1.3, FONT_PRINT)
text("METAL", c - 0.35, b + 0.38, DOOR + 0.001, small * 0.95, printed, "RIGHT", 1.3, FONT_PRINT)

# The changer door, with its legend in a printed box.
a, b, c, d = rect(L["changer"])
cut_box(a, b, c, d)
floor_box(a, b, c, d)
box("changer-door", a + 0.04, b + 0.04, c - 0.04, d - 0.04, FACE - DEEP, DOOR, gloss, bevel=0.03)
box("disc-slot", a + 0.6, b + 0.42, c - 0.6, b + 0.52, DOOR - 0.01, DOOR + 0.002, hole)
mid = (a + c) / 2
text("3 DISC AUTO CHANGER", mid, d - 0.2, DOOR + 0.001, small * 1.05, printed, spacing=1.35, font=FONT_PRINT)
for (p0, p1, q0, q1) in [(mid - 1.85, mid + 1.85, d - 0.33, d - 0.32), (mid - 1.85, mid + 1.85, d - 0.08, d - 0.07),
                         (mid - 1.86, mid - 1.85, d - 0.33, d - 0.07), (mid + 1.85, mid + 1.86, d - 0.33, d - 0.07)]:
    box("rule", p0, q0, p1, q1, DOOR - 0.001, DOOR + 0.001, faint)

# The display: a deep black opening. The app draws the segments and the glass into it.
a, b, c, d = rect(L["lcd"])
cut_box(a, b, c, d)
floor_box(a, b, c, d)

text("DISC SELECT", X(0.628), Y(0.752), PZ, small * 0.9, printed, spacing=1.3, font=FONT_PRINT)
text("APS", X(0.7925), Y(0.84), PZ, small * 0.85, printed, spacing=1.3, font=FONT_PRINT)

for f in FACES:
    m = f.modifiers.new("openings", "BOOLEAN")
    m.operation = "DIFFERENCE"
    m.operand_type = "COLLECTION"
    m.collection = CUTS
    m.solver = "EXACT"


# ---- light ----

def area(name, loc, rot, size, energy, color=(1, 0.98, 0.95)):
    l = bpy.data.lights.new(name, "AREA")
    l.shape = "RECTANGLE"
    l.size, l.size_y = size
    l.energy = energy
    l.color = color
    o = bpy.data.objects.new(name, l)
    scene.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = rot
    return o


# Daylight through the windscreen: a broad soft source above and in front, and a little cabin light.
sun = area("windscreen", (W / 2, H + 6, 9), (math.radians(-38), 0, 0), (30, 6), 2600)
fill = area("cabin", (W * 0.15, H * 0.3, 14), (math.radians(10), math.radians(-25), 0), (10, 10), 120, (0.9, 0.95, 1))


# At night only a little street light reaches the dash, from above.
street = area("street", (W / 2, H + 4, 12), (math.radians(-30), 0, 0), (30, 8), 220, (0.75, 0.82, 1.0))


def look(night):
    sun.hide_render = fill.hide_render = night
    street.hide_render = not night
    bg.inputs["Color"].default_value = (0.006, 0.0065, 0.007, 1) if not night else (0.0012, 0.0012, 0.0015, 1)
    for m in (legend, well):
        m.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = m["night"] if night else 0.0


for parts in KEYS:
    for o in parts:
        o["z"] = o.location.z


def press(on):
    for parts in KEYS:
        for o in parts:
            o.location.z = o["z"] - (PRESS if on else 0.0)


os.makedirs(OUT, exist_ok=True)
ONLY = os.environ.get("ONLY")
for name, night, pressed in [("day", False, False), ("night", True, False),
                             ("day-pressed", False, True), ("night-pressed", True, True)]:
    if ONLY and name not in ONLY.split(","):
        continue
    look(night)
    press(pressed)
    scene.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)

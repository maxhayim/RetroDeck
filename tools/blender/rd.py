"""
The parts every rendered skin is built from: the scene and camera, materials, shapes, openings cut
into a faceplate, keys, knobs, printing, light, and the render loop. skins.py uses them.

World units: the screen is 16 x 9, seen straight on from the front (+z); 1 unit is 120 px at
1920 x 1080. Layout boxes are fractions of the screen, as in the app.
"""
import math
import os

import bmesh
import bpy

# SIZE=1920x720 renders for a single-DIN unit's wide screen: still 16 units across, less tall.
PX_W, PX_H = (int(v) for v in os.environ.get("SIZE", "1920x1080").split("x"))
W, H = 16.0, 16.0 * PX_H / PX_W
HERE = os.path.dirname(os.path.abspath(__file__))


def X(f): return f * W
def Y(f): return (1 - f) * H


def rect(b):
    """A layout box as world x0, y0, x1, y1, y up."""
    return X(b[0]), Y(b[3]), X(b[2]), Y(b[1])


def centre(b):
    x0, y0, x1, y1 = rect(b)
    return (x0 + x1) / 2, (y0 + y1) / 2


# ---- scene ----

def scene_setup(samples):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "GPU"
    sc.cycles.samples = samples
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = PX_W, PX_H
    sc.render.resolution_percentage = int(os.environ.get("PCT", "100"))
    sc.view_settings.view_transform = "Standard"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "METAL"
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = W
    cam.location = (W / 2, H / 2, 30)
    sc.camera = cam
    world = bpy.data.worlds.new("world")
    sc.world = world
    world.use_nodes = True
    global CUTS
    CUTS = bpy.data.collections.new("cuts")
    sc.collection.children.link(CUTS)
    CUTS.hide_render = True
    return sc


def scene():
    return bpy.context.scene


# ---- materials ----

def material(name, color, rough, spec=0.5, bump=0.0, bump_scale=300.0, coat=0.0, metal=0.0, aniso=0.0,
             brushed=False, rings=False):
    """Principled material. brushed: fine lines along x; rings: concentric tool marks round the object."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    n = m.node_tree.nodes
    links = m.node_tree.links
    p = n["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Specular IOR Level"].default_value = spec
    p.inputs["Metallic"].default_value = metal
    if aniso:
        p.inputs["Anisotropic"].default_value = aniso
    if coat:
        p.inputs["Coat Weight"].default_value = coat
        p.inputs["Coat Roughness"].default_value = 0.06
    height = None
    if brushed:
        coord = n.new("ShaderNodeTexCoord")
        mp = n.new("ShaderNodeMapping")
        mp.inputs["Scale"].default_value = (2.0, 900.0, 2.0)
        tex = n.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = 3.0
        tex.inputs["Detail"].default_value = 12
        links.new(coord.outputs["Object"], mp.inputs["Vector"])
        links.new(mp.outputs["Vector"], tex.inputs["Vector"])
        height = tex.outputs["Fac"]
    elif rings:
        # Spun metal: the highlight runs round the centre, as on a lathe-turned cap.
        tan = n.new("ShaderNodeTangent")
        tan.direction_type = "RADIAL"
        tan.axis = "Z"
        links.new(tan.outputs["Tangent"], p.inputs["Tangent"])
        p.inputs["Anisotropic"].default_value = aniso or 0.85
        coord = n.new("ShaderNodeTexCoord")
        wave = n.new("ShaderNodeTexWave")
        wave.wave_type = "RINGS"
        wave.inputs["Scale"].default_value = 40.0
        wave.inputs["Distortion"].default_value = 1.5
        wave.inputs["Detail"].default_value = 4
        links.new(coord.outputs["Object"], wave.inputs["Vector"])
        height = wave.outputs["Fac"]
    elif bump:
        tex = n.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = bump_scale
        tex.inputs["Detail"].default_value = 8
        height = tex.outputs["Fac"]
    if height is not None:
        b = n.new("ShaderNodeBump")
        b.inputs["Strength"].default_value = bump or 0.25
        b.inputs["Distance"].default_value = 0.002
        links.new(height, b.inputs["Height"])
        links.new(b.outputs["Normal"], p.inputs["Normal"])
    return m


def emitter(name, color, strength):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    n = m.node_tree.nodes
    for x in list(n):
        if x.type != "OUTPUT_MATERIAL":
            n.remove(x)
    e = n.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = (*color, 1)
    e.inputs["Strength"].default_value = strength
    m.node_tree.links.new(e.outputs["Emission"], n["Material Output"].inputs["Surface"])
    return m


def hole_material():
    return material("hole", (0.0, 0.0, 0.0), 1.0, 0.0)


# ---- shapes ----

def obj(name, mesh, mat):
    o = bpy.data.objects.new(name, mesh)
    scene().collection.objects.link(o)
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


def box(name, x0, y0, x1, y1, z0, z1, mat, bevel=0.0, segments=4, flat=False):
    """A box; flat keeps flat shading, for faces that openings will be cut into."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x = (v.co.x + 0.5) * (x1 - x0) - (x1 - x0) / 2
        v.co.y = (v.co.y + 0.5) * (y1 - y0) - (y1 - y0) / 2
        v.co.z = z0 + (v.co.z + 0.5) * (z1 - z0)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me, mat)
    o.location = ((x0 + x1) / 2, (y0 + y1) / 2, 0)
    if bevel and flat:
        m = o.modifiers.new("bevel", "BEVEL")
        m.width = bevel
        m.segments = 1
    elif bevel:
        smooth(o, bevel, segments)
    return o


def cylinder(name, cx, cy, r, z0, z1, mat, bevel=0.0, verts=96, top_r=None):
    """A cylinder centred on its own origin, so ring textures centre on it."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=top_r or r, depth=z1 - z0)
    for v in bm.verts:
        v.co.z += (z0 + z1) / 2
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me, mat)
    o.location = (cx, cy, 0)
    if bevel:
        smooth(o, bevel, 5, angle_only=True)
    return o


def prism(name, outline, z0, z1, mat, bevel=0.0):
    """An outline (x, y points, anticlockwise) pushed out from z0 to z1."""
    bm = bmesh.new()
    vs = [bm.verts.new((x, y, z0)) for x, y in outline]
    f = bm.faces.new(vs)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in ext["geom"]:
        if isinstance(v, bmesh.types.BMVert):
            v.co.z = z1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me, mat)
    if bevel:
        m = o.modifiers.new("bevel", "BEVEL")
        m.width = bevel
        m.segments = 3
        m.limit_method = "ANGLE"
    return o


def tube(name, points, radius, mat):
    """A round bar along a path of x, y, z points, like a chrome trim."""
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = radius
    cu.bevel_resolution = 6
    sp = cu.splines.new("POLY")
    sp.points.add(len(points) - 1)
    for i, (x, y, z) in enumerate(points):
        sp.points[i].co = (x, y, z, 1)
    o = bpy.data.objects.new(name, cu)
    scene().collection.objects.link(o)
    cu.materials.append(mat)
    return o


def quad(p0, c, p1, n=48):
    """Points along a quadratic curve."""
    out = []
    for i in range(n + 1):
        t = i / n
        out.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]))
    return out


def srgb(hex_rgb):
    """An Android colour (0xRRGGBB) as linear RGB, as Blender wants."""
    out = []
    for sh in (16, 8, 0):
        c = ((hex_rgb >> sh) & 0xFF) / 255
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return tuple(out)


def rrect(x0, y0, x1, y1, r, n=8):
    """A rounded rectangle's outline, anticlockwise."""
    r = max(0.0, min(r, (x1 - x0) / 2 - 1e-4, (y1 - y0) / 2 - 1e-4))
    if r <= 0:
        return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def rbox(name, x0, y0, x1, y1, z0, z1, mat, r, bevel=0.0, segments=3):
    """A box with rounded corners seen from the front; bevel rounds its front edge too."""
    if r <= 0:
        return box(name, x0, y0, x1, y1, z0, z1, mat, bevel=bevel, segments=segments)
    o = prism(name, rrect(x0, y0, x1, y1, r), z0, z1, mat)
    if bevel:
        m = o.modifiers.new("bevel", "BEVEL")
        m.width = bevel
        m.segments = segments
        m.limit_method = "ANGLE"
        m.angle_limit = math.radians(60)
        m.harden_normals = True
        for f in o.data.polygons:
            f.use_smooth = True
    return o


FONTS = {}


def font(name):
    if name not in FONTS:
        FONTS[name] = bpy.data.fonts.load(os.path.join(HERE, "fonts", name + ".ttf"))
    return FONTS[name]


def text(body, x, y, z, size, mat, align="CENTER", spacing=1.0, face="BarlowSemiCondensed-Medium"):
    cu = bpy.data.curves.new("t", "FONT")
    cu.body = body
    cu.font = font(face)
    cu.size = size
    cu.space_character = spacing
    cu.align_x = align
    cu.align_y = "CENTER"
    o = bpy.data.objects.new("t:" + body, cu)
    scene().collection.objects.link(o)
    o.location = (x, y, z)
    cu.materials.append(mat)
    return o


# ---- openings ----

CUTS = None
TARGETS = []


def cut(o):
    scene().collection.objects.unlink(o)
    CUTS.objects.link(o)
    o.display_type = "WIRE"


def cut_box(x0, y0, x1, y1, top, depth, hole, r=0.0, floor=None):
    cut(rbox("cut", x0, y0, x1, y1, top - depth, top + 0.2, hole, r))
    rbox("floor", x0 - 0.03, y0 - 0.03, x1 + 0.03, y1 + 0.03, top - depth - 0.03, top - depth + 0.005, floor or hole,
         r + 0.03 if r else 0)


def cut_circle(cx, cy, r, top, depth, hole):
    cut(cylinder("cut", cx, cy, r, top - depth, top + 0.2, hole))
    cylinder("floor", cx, cy, r + 0.03, top - depth - 0.03, top - depth + 0.005, hole)


def target(o):
    """Openings will be cut out of this object."""
    TARGETS.append(o)
    return o


def apply_cuts():
    for o in TARGETS:
        m = o.modifiers.new("openings", "BOOLEAN")
        m.operation = "DIFFERENCE"
        m.operand_type = "COLLECTION"
        m.collection = CUTS
        m.solver = "EXACT"


# ---- keys and knobs ----

KEYS = []      # every key cap and its legend, so the pressed renders can push them in
LEGENDS = []   # the backlit legend materials, lit only in the glow render


def legend_material(name, color):
    """Printed by day; in the glow render the backlight shines through it, white, for the app to tint."""
    m = material(name, color, 0.55)
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Emission Color"].default_value = (1, 1, 1, 1)
    p.inputs["Emission Strength"].default_value = 0.0
    LEGENDS.append(m)
    return m


def flat(name, verts, faces, mat):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    return obj(name, me, mat)


def tri(cx, cy, size, up, mat, z):
    h = size * 0.87
    s = 1 if up else -1
    return flat("tri", [(cx - size / 2, cy - s * h / 2, z), (cx + size / 2, cy - s * h / 2, z), (cx, cy + s * h / 2, z)],
                [(0, 1, 2) if up else (0, 2, 1)], mat)


def play_mark(cx, cy, size, mat, z):
    """Play and pause, side by side: the key is both."""
    a = size
    v = [(cx - a * 1.25, cy - a / 2, z), (cx - a * 0.4, cy, z), (cx - a * 1.25, cy + a / 2, z)]
    f = [(0, 1, 2)]
    for x in (cx + a * 0.2, cx + a * 0.72):
        i = len(v)
        v += [(x, cy - a / 2, z), (x + a * 0.28, cy - a / 2, z), (x + a * 0.28, cy + a / 2, z), (x, cy + a / 2, z)]
        f.append((i, i + 1, i + 2, i + 3))
    return flat("play", v, f, mat)


def fit(keys, round_too=True):
    """One legend size for a set of keys, the largest that fits every one, as the app does."""
    size = 1e9
    for k in keys:
        x0, y0, x1, y1 = rect(k["box"])
        w, h = x1 - x0, y1 - y0
        if k["round"]:
            w = h = min(w, h) * 0.72
        label = "PAUSE" if k["id"] == "play" else k["label"].split("|")[0]
        size = min(size, h * 0.34 / 0.72, w * 0.7 / (0.5 * max(1, len(label))))
    return size


def key(k, top, mat, hole, legend, size, height=0.12, gap=0.05, bevel=0.04, round_bevel=0.06, depth=0.2,
        face_up=0.04, edge=None, r=0.0, cut_face=True):
    """A key cap standing in its own opening, with its legend printed on top."""
    x0, y0, x1, y1 = rect(k["box"])
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    z = top + height + 0.001
    if k["round"]:
        r = min(x1 - x0, y1 - y0) / 2
        rr = min(x1 - x0, y1 - y0) / 2
        if cut_face:
            cut_circle(cx, cy, rr, top, depth, hole)
        parts = [cylinder("key", cx, cy, rr - gap, top - depth + 0.02, top + height, mat, bevel=round_bevel)]
    else:
        if cut_face:
            cut_box(x0, y0, x1, y1, top, depth, hole, r)
        parts = [rbox("key", x0 + gap, y0 + gap, x1 - gap, y1 - gap, top - depth + 0.02, top + height, mat,
                      max(0.0, r - gap), bevel=bevel)]
        if edge:
            m = parts[0].modifiers["bevel"]
            parts[0].data.materials.append(edge)
            m.material = 1
    label = k["label"]
    ly = cy + (0 if k["round"] else (y1 - y0) * face_up)
    if label in ("\u25b2", "\u25bc"):
        parts.append(tri(cx, ly, size * 0.62, label == "\u25b2", legend, z))
    elif k["id"] == "play":
        parts.append(play_mark(cx + size * 0.2, ly, size * 0.62, legend, z))
    elif (k["id"] or "").startswith("preset") and "|" in label:
        name, num = label.split("|")
        parts.append(text(name, cx, cy + (y1 - y0) * 0.12, z, size, legend))
        parts.append(text(num, x1 - gap - 0.16, y0 + gap + 0.17, z, size * 0.72, legend))
    else:
        parts.append(text(label, cx, ly, z, size, legend, face="BarlowSemiCondensed-SemiBold"))
    KEYS.append(parts)
    return parts


def knob(b, top, hole, grip, cap, r_frac=0.80, ridges=72, height=0.45, cap_frac=0.84, skirt=None):
    """A volume knob in a collar: ridged grip, and a cap (spun metal, gloss, rubber) on the front."""
    cx, cy = centre(b)
    x0, y0, x1, y1 = rect(b)
    r = min(x1 - x0, y1 - y0) / 2 * r_frac
    cut_circle(cx, cy, r * 1.08, top, 0.2, hole)
    if skirt:
        cylinder("skirt", cx, cy, r * 1.04, top - 0.2, top + 0.05, skirt, bevel=0.02)
    cylinder("grip", cx, cy, r * 0.96, top - 0.2, top + height, grip, bevel=r * 0.05)
    for i in range(ridges):
        a = 2 * math.pi * i / ridges
        rid = box("ridge", -r * 0.03, -r * 0.018, r * 0.03, r * 0.018, top + 0.05, top + height - 0.03, grip,
                  bevel=r * 0.008, segments=2)
        rid.location = (cx + math.cos(a) * r, cy + math.sin(a) * r, 0)
        rid.rotation_euler = (0, 0, a)
    cylinder("cap", cx, cy, r * cap_frac, top + height - 0.05, top + height + 0.02, cap, bevel=r * 0.04)
    return cx, cy, r


# ---- light ----

def area(name, loc, rot, size, energy, color=(1, 0.98, 0.95)):
    l = bpy.data.lights.new(name, "AREA")
    l.shape = "RECTANGLE"
    l.size, l.size_y = size
    l.energy = energy
    l.color = color
    o = bpy.data.objects.new(name, l)
    scene().collection.objects.link(o)
    o.location = loc
    o.rotation_euler = rot
    return o


class Lights:
    """Daylight through the windscreen, a softbox metal can reflect, and a little street light at night."""

    def __init__(self, sun=2600, fill=120, softbox=0.0, street=220, night_world=0.0012):
        self.sun = area("windscreen", (W / 2, H + 6, 9), (math.radians(-38), 0, 0), (30, 6), sun)
        self.fill = area("cabin", (W * 0.15, H * 0.3, 14), (math.radians(10), math.radians(-25), 0), (10, 10), fill,
                         (0.9, 0.95, 1))
        # Lights stand in for the sky and the cabin: glossy faces show the softbox, not their hard edges.
        self.fill.visible_glossy = False
        self.street = area("street", (W / 2, H + 4, 12), (math.radians(-30), 0, 0), (30, 8), street, (0.75, 0.82, 1.0))
        self.night_world = night_world
        self.softbox = None
        if softbox:
            # Behind the camera, so only reflections see it: what brushed and polished metal shows.
            me = bpy.data.meshes.new("softbox")
            me.from_pydata([(-6, -4, 40), (W + 6, -4, 40), (W + 6, H + 8, 40), (-6, H + 8, 40)], [], [(0, 3, 2, 1)])
            m = bpy.data.materials.new("softbox")
            m.use_nodes = True
            n = m.node_tree.nodes
            for x in list(n):
                if x.type != "OUTPUT_MATERIAL":
                    n.remove(x)
            coord = n.new("ShaderNodeTexCoord")
            sep = n.new("ShaderNodeSeparateXYZ")
            ramp = n.new("ShaderNodeValToRGB")
            ramp.color_ramp.elements[0].color = (0.25, 0.25, 0.27, 1)
            ramp.color_ramp.elements[1].color = (1.0, 0.99, 0.97, 1)
            e = n.new("ShaderNodeEmission")
            e.inputs["Strength"].default_value = softbox
            L = m.node_tree.links
            L.new(coord.outputs["Generated"], sep.inputs["Vector"])
            L.new(sep.outputs["Y"], ramp.inputs["Fac"])
            L.new(ramp.outputs["Color"], e.inputs["Color"])
            L.new(e.outputs["Emission"], n["Material Output"].inputs["Surface"])
            me.materials.append(m)
            self.softbox = bpy.data.objects.new("softbox", me)
            scene().collection.objects.link(self.softbox)
            self.softbox.visible_camera = False

    def look(self, night):
        self.sun.hide_render = self.fill.hide_render = night
        if self.softbox:
            self.softbox.hide_render = night
        self.street.hide_render = not night
        bg = scene().world.node_tree.nodes["Background"]
        v = self.night_world if night else 0.006
        bg.inputs["Color"].default_value = (v, v * 1.05, v * 1.15, 1)


def render_all(out, lights, press_depth=0.06, glow=True, on_press=None):
    """day, night, both again with every key pressed in, and glow: only the backlit legends, white."""
    for parts in KEYS:
        for o in parts:
            o["z"] = o.location.z
    os.makedirs(out, exist_ok=True)
    only = os.environ.get("ONLY")
    bg = scene().world.node_tree.nodes["Background"]
    passes = [("day", False, False), ("night", True, False), ("day-pressed", False, True), ("night-pressed", True, True)]
    if glow:
        passes.append(("glow", None, False))
    for name, night, pressed in passes:
        if only and name not in only.split(","):
            continue
        lights.look(bool(night))
        lit = night is None
        if lit:
            for l in (lights.sun, lights.fill, lights.street, lights.softbox):
                if l:
                    l.hide_render = True
            bg.inputs["Color"].default_value = (0, 0, 0, 1)
        for m in LEGENDS:
            m.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = 2.2 if lit else 0.0
        for parts in KEYS:
            for o in parts:
                o.location.z = o["z"] - (press_depth if pressed else 0.0)
        if on_press:
            on_press(pressed, bool(night))
        scene().render.filepath = os.path.join(out, name + ".png")
        bpy.ops.render.render(write_still=True)

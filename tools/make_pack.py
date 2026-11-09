#!/usr/bin/env python3
"""Builds a RetroDeck branding pack from a theme file and your own logo files.

A theme file (theme.json in a folder of custom-themes) picks the skin and says where each logo goes on it. The logos
are yours: RetroDeck ships none, and this tool never fetches any. Import the ZIP on the head unit with
Settings -> Personalization -> Vehicle Branding -> Import/Export Branding Pack -> Import a pack.

    python3 tools/make_pack.py custom-themes/my-car/theme.json \
        --manufacturer custom-themes/my-car/car-maker.png --model custom-themes/my-car/model-badge.png \
        --audio custom-themes/my-car/audio-brand.png -o custom-themes/my-car.zip

Keep your theme's working files in its folder (custom-themes/my-car/) and write the pack next to the
folder (custom-themes/my-car.zip): that ZIP is the one file you send.

Logos: PNG (best: light artwork on a transparent background), JPEG, WebP or SVG, at most 4096 pixels
a side and 8 MB. Only share a pack with people who may use the logos in it.

The pack gets screenshots: the stereo by day and by night, the full-screen visualizer by day and by
night, and the startup screen (assets/p0-screenshot-NAME.png, inside the ZIP; --save-screenshots
also saves copies next to it), as packs exported in the app have, so whoever you send it to can see it
before importing.
It is drawn by RetroDeck's own importer and renderer (PackRenderTest), which needs this repository's
build setup (JDK 17 in JAVA_HOME and the Android SDK). Without it, or with --no-preview, the pack is
made without them. RetroDeck ignores the screenshots when importing. A pack with an audio brand logo
needs a RetroDeck that knows about it (pack format 3).
Standard library only.
"""
import argparse
import json
import os
import re
import struct
import subprocess
import sys
import zipfile

ROLES = ("manufacturer", "model", "audio")
MAX_BYTES = 8 * 1024 * 1024
MAX_SIDE = 4096
MAX_VIDEO_BYTES = 20 * 1024 * 1024


def sniff(b):
    if b[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if b[:3] == b"\xff\xd8\xff":
        return "jpg"
    if b[:4] == b"RIFF" and b[8:12] == b"WEBP":
        return "webp"
    head = b[:1024].decode("utf-8", "ignore").lstrip("﻿").strip().lower()
    if head.startswith(("<?xml", "<svg", "<!--")) and "<svg" in head:
        return "svg"
    return None


def png_size(b):
    return struct.unpack(">II", b[16:24]) if len(b) >= 24 and b[12:16] == b"IHDR" else None


def svg_problem(text):
    s = text.lower()
    if "<!doctype" in s or "<!entity" in s:
        return "has a DOCTYPE or entities"
    if "<script" in s:
        return "has a script"
    if "<foreignobject" in s:
        return "has embedded HTML"
    if re.search(r"(href\s*=\s*[\"']\s*|url\(\s*[\"']?\s*)(https?|file|content|ftp):", s):
        return "loads other files"
    return None


def load_logo(role, path):
    """The logo's bytes and extension, checked the way RetroDeck checks a pack."""
    if not os.path.isfile(path):
        sys.exit(f"{role} logo: no file at {path}")
    if os.path.getsize(path) > MAX_BYTES:
        sys.exit(f"{role} logo: larger than 8 MB")
    with open(path, "rb") as f:
        data = f.read()
    kind = sniff(data)
    if kind is None:
        sys.exit(f"{role} logo: {path} isn't a PNG, JPEG, WebP or SVG image")
    if kind == "svg":
        problem = svg_problem(data.decode("utf-8", "ignore"))
        if problem:
            sys.exit(f"{role} logo: the SVG {problem}, which RetroDeck refuses. Export it as a PNG instead.")
    size = png_size(data) if kind == "png" else None
    if size and max(size) > MAX_SIDE:
        sys.exit(f"{role} logo: {size[0]}x{size[1]}; the limit is {MAX_SIDE} pixels a side")
    return data, kind


# The screenshots, as the app's own export makes them: stereo by day and night, full-screen visualizer
# by day and night, and the startup screen.
# When the visualizer looks the same day and night there's one, "visualizer".
SCREENSHOTS = ("day", "night", "visualizer", "visualizer-day", "visualizer-night", "startup")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def write_zip(path, manifest, packed):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        for name, data in packed.items():
            z.writestr(name, data)


def render_preview(pack, song=None):
    """Draws the pack with RetroDeck's own importer and renderer: {screenshot name: PNG bytes}, or None."""
    out = os.path.join(REPO, "branding", "build", "branding-renders")
    for f in os.listdir(out) if os.path.isdir(out) else []:
        if f.startswith("pack-"):
            os.remove(os.path.join(out, f))
    gradlew = os.path.join(REPO, "gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [gradlew, "-q", ":branding:testDebugUnitTest", "--tests", "*PackRenderTest*", "--rerun",
           "-Ppack=" + os.path.abspath(pack), "-Prender.size=1920x1080"]
    # The song shown playing in the screenshot: "previewSong": {"title": ..., "artist": ...} in the theme file.
    for key in ("title", "artist"):
        if song and song.get(key):
            cmd.append(f"-Ppack.{key}={song[key]}")
    print("Drawing the screenshots (the first time takes a few minutes)...")
    try:
        r = subprocess.run(cmd, cwd=REPO, capture_output=True, text=True)
    except OSError as e:
        print(f"No screenshot: couldn't run Gradle ({e}).")
        return None
    shots = sorted(f for f in os.listdir(out) if f.startswith("pack-")) if os.path.isdir(out) else []
    if r.returncode != 0 or not shots:
        tail = "\n".join((r.stdout + r.stderr).strip().splitlines()[-8:])
        print("No screenshot: the preview didn't run. Is JAVA_HOME set to JDK 17, and the Android SDK installed?")
        print(tail)
        return None
    result = {}
    for name in SCREENSHOTS:
        file = next((f for f in shots if f.startswith("pack-0-") and f.endswith("-" + name + ".png")), None)
        if file:
            with open(os.path.join(out, file), "rb") as f:
                result[name] = f.read()
    return result


def main():
    ap = argparse.ArgumentParser(description="Build a RetroDeck branding pack from a theme file and your own logos.")
    ap.add_argument("preset", help="a theme file, e.g. custom-themes/my-car/theme.json")
    ap.add_argument("--manufacturer", help="logo file for the manufacturer role")
    ap.add_argument("--model", help="logo file for the model role (a model badge)")
    ap.add_argument("--audio", help="logo file for the audio brand (the stereo maker on a factory radio)")
    ap.add_argument("-o", "--out", required=True, help="the .zip to write")
    ap.add_argument("--no-preview", action="store_true", help="don't add screenshots")
    ap.add_argument("--video", help="an MP4 tour to include (Vehicle Branding -> Record a Video); "
                    "needs RetroDeck 0.20.0 or newer to import")
    ap.add_argument("--save-screenshots", action="store_true",
                    help="also save the screenshots next to the ZIP (they're always inside it)")
    a = ap.parse_args()

    with open(a.preset, encoding="utf-8") as f:
        preset = json.load(f)
    profile = dict(preset["profile"])
    files = {"manufacturer": a.manufacturer, "model": a.model, "audio": a.audio}
    if not any(files.values()):
        for role in ROLES:
            print(f"--{role}: {preset.get('logos', {}).get(role, '')}")
        sys.exit("Give at least one logo.")

    assets, packed = {}, {}
    for role in ROLES:
        if not files[role]:
            continue
        data, kind = load_logo(role, files[role])
        name = f"assets/{role}.{kind}"
        packed[name] = data
        assets[role] = name
    profile["assets"] = assets

    manifest = {
        "format": "retrodeck-branding-pack",
        "formatVersion": 3 if "audio" in assets else 1,
        "name": preset.get("name", "Branding pack")[:40],
        "createdWith": "RetroDeck tools/make_pack.py",
        "compatibleSkins": [profile["skin"]],
        "profiles": [profile],
    }
    video = None
    if a.video:
        with open(a.video, "rb") as f:
            video = f.read()
        if len(video) > MAX_VIDEO_BYTES:
            sys.exit(f"video: {len(video) >> 20} MB; the limit is {MAX_VIDEO_BYTES >> 20} MB")
        if video[4:8] != b"ftyp":
            sys.exit("video: not an MP4 file")
    if os.path.exists(a.out):
        sys.exit(f"{a.out} already exists; pick another name")
    write_zip(a.out, manifest, packed)
    if not a.no_preview:
        shots = render_preview(a.out, preset.get("previewSong"))
        if shots:
            listed = {}
            for name, png in shots.items():
                inside = f"assets/p0-screenshot-{name}.png"
                packed[inside] = png
                listed[name] = inside
                if a.save_screenshots:
                    with open(os.path.splitext(a.out)[0] + f"-{name}.png", "wb") as f:
                        f.write(png)
            manifest["profiles"][0]["screenshots"] = listed
            if "day" in listed:
                manifest["preview"] = listed["day"]
            print(f"Added {len(listed)} screenshots ({', '.join(listed)}) inside the pack"
                  + (f", with copies next to it ({os.path.splitext(a.out)[0]}-NAME.png)." if a.save_screenshots else "."))
            write_zip(a.out, manifest, packed)
    if video:
        # Added after the screenshots are drawn: the video is for people to watch, not imported.
        packed["assets/tour.mp4"] = video
        manifest["video"] = "assets/tour.mp4"
        manifest["formatVersion"] = 4
        write_zip(a.out, manifest, packed)
        print(f"Added the video ({len(video) >> 20} MB) as assets/tour.mp4.")
    print(f"Wrote {a.out}: {preset.get('name')}, skin {profile['skin']}, logos: {', '.join(assets) or 'none'}")
    print("Import it on the head unit: Vehicle Branding -> Import/Export Branding Pack -> Import a pack.")
    print("Only share it with people who may use the logos in it.")


if __name__ == "__main__":
    main()

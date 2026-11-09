#!/usr/bin/env python3
"""Builds a RetroDeck branding pack from a theme file and your own logo files.

A theme file (theme.json in a folder of custom-themes) picks the skin and says where each logo goes on it. The logos
are yours: RetroDeck ships none, and this tool never fetches any. Import the ZIP on the head unit with
Settings -> Personalization -> Vehicle Branding -> Import/Export Branding Pack -> Import a pack.

    python3 tools/make_pack.py custom-themes/300zx/theme.json \
        --manufacturer car-maker.png --model audio-brand.png -o 300zx.zip

Logos: PNG (best: light artwork on a transparent background), JPEG, WebP or SVG, at most 4096 pixels
a side and 8 MB. Only share a pack with people who may use the logos in it.

The pack gets a screenshot, assets/preview.png, so whoever you send it to can see it before importing.
It is drawn by RetroDeck's own importer and renderer (PackRenderTest), which needs this repository's
build setup (JDK 17 in JAVA_HOME and the Android SDK). Without it, or with --no-preview, the pack is
made without one. RetroDeck ignores the screenshot when importing.
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

ROLES = ("manufacturer", "model")
MAX_BYTES = 8 * 1024 * 1024
MAX_SIDE = 4096


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


PREVIEW = "assets/preview.png"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def write_zip(path, manifest, packed):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        for name, data in packed.items():
            z.writestr(name, data)


def render_preview(pack):
    """Draws the pack with RetroDeck's own importer and renderer; returns the PNG bytes or None."""
    out = os.path.join(REPO, "branding", "build", "branding-renders")
    for f in os.listdir(out) if os.path.isdir(out) else []:
        if f.startswith("pack-"):
            os.remove(os.path.join(out, f))
    gradlew = os.path.join(REPO, "gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [gradlew, "-q", ":branding:testDebugUnitTest", "--tests", "*PackRenderTest*", "--rerun",
           "-Ppack=" + os.path.abspath(pack), "-Prender.size=1920x1080"]
    print("Drawing the screenshot (the first time takes a few minutes)...")
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
    with open(os.path.join(out, shots[0]), "rb") as f:
        return f.read()


def main():
    ap = argparse.ArgumentParser(description="Build a RetroDeck branding pack from a theme file and your own logos.")
    ap.add_argument("preset", help="a theme file, e.g. custom-themes/300zx/theme.json")
    ap.add_argument("--manufacturer", help="logo file for the manufacturer role")
    ap.add_argument("--model", help="logo file for the model role")
    ap.add_argument("-o", "--out", required=True, help="the .zip to write")
    ap.add_argument("--no-preview", action="store_true", help="don't add a screenshot")
    a = ap.parse_args()

    with open(a.preset, encoding="utf-8") as f:
        preset = json.load(f)
    profile = dict(preset["profile"])
    files = {"manufacturer": a.manufacturer, "model": a.model}
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
        "formatVersion": 1,
        "name": preset.get("name", "Branding pack")[:40],
        "createdWith": "RetroDeck tools/make_pack.py",
        "compatibleSkins": [profile["skin"]],
        "profiles": [profile],
    }
    if os.path.exists(a.out):
        sys.exit(f"{a.out} already exists; pick another name")
    write_zip(a.out, manifest, packed)
    if not a.no_preview:
        shot = render_preview(a.out)
        if shot:
            manifest["preview"] = PREVIEW
            packed[PREVIEW] = shot
            write_zip(a.out, manifest, packed)
            beside = os.path.splitext(a.out)[0] + "-preview.png"
            with open(beside, "wb") as f:
                f.write(shot)
            print(f"Added a screenshot: {PREVIEW} inside the pack, and {beside} next to it.")
    print(f"Wrote {a.out}: {preset.get('name')}, skin {profile['skin']}, logos: {', '.join(assets) or 'none'}")
    print("Import it on the head unit: Vehicle Branding -> Import/Export Branding Pack -> Import a pack.")
    print("Only share it with people who may use the logos in it.")


if __name__ == "__main__":
    main()

# Custom themes

Your own themes for real radios: the skin, your logos and where each one sits, built into a branding pack you import on the head unit.

**Everything in this folder stays on your computer.** Git ignores it, except this guide, so your logos and packs are never committed or pushed to GitHub. RetroDeck ships no real logos; the ones you put here are yours to use. Only share a pack with people who may use the logos in it, and never post one in this repository or its issues.

## Make one

1. Make a folder here for your car, e.g. `custom-themes/my-car/`. It holds the working files: the theme file and your logos. The finished pack goes next to it, so the folder and the ZIP sit side by side:

   ```
   custom-themes/
     my-car/          theme.json and logos (to rebuild the pack)
     my-car.zip       the pack: the one file you send
   ```

2. Copy `tools/template-z32.json` into it as `theme.json`, and fill in the manufacturer, model and year. The template uses Z32 Factory 1990; change `"skin"` and the placements for another skin (skin ids are on each page in [themes](../themes/)).
3. Put your logos in the folder, preferably PNGs with light artwork on a transparent background. There are three: `manufacturer` (the car maker), `model` (a model badge) and `audio` (the stereo maker on a factory radio). Use the ones you need.
4. Build the pack:

   ```
   python3 tools/make_pack.py custom-themes/my-car/theme.json \
       --manufacturer custom-themes/my-car/maker.png --model custom-themes/my-car/badge.png \
       --audio custom-themes/my-car/audio.png \
       -o custom-themes/my-car.zip
   ```

   The pack gets five screenshots, the same ones a pack exported in the app carries: the stereo by day and by night, the full-screen visualizer by day and by night, and the startup screen. They're drawn by RetroDeck's own importer and renderer, and stored inside the ZIP as `assets/p0-screenshot-NAME.png` (`--save-screenshots` also saves copies next to it). When the full-screen visualizer looks the same by day and night, there's one picture instead of two copies, so whoever you send it to can see it before importing. RetroDeck ignores them when importing. The screenshots show a song playing: set `previewSong` (`title` and `artist`) in your theme file to choose which. Song titles in any script show on the segment displays (Japanese, Chinese, Korean, Hebrew, Arabic, Russian, Greek...), and a title too long for the display scrolls; the screenshot shows its end. To change the startup greeting, set `"greeting"` under `"startup"` in the profile, e.g. `"おかえり {car}"` (`{car}` becomes the vehicle's name). A pack with an `audio` logo needs RetroDeck 0.18.0 or newer. This needs the repository's build setup (JDK 17 in `JAVA_HOME` and the Android SDK) and takes a few minutes the first time; `--no-preview` skips it.
5. Send the ZIP (`custom-themes/my-car.zip`) to the head unit and import it. With [Amaze File Manager](https://github.com/TeamAmaze/AmazeFileManager) (or [LocalSend](https://github.com/localsend/localsend)) on the head unit: open the ZIP's ⋮ menu → Open with → Other → **Import into RetroDeck**, then Import (from LocalSend, Share → **Import into RetroDeck**). Or in RetroDeck: Settings → Personalization → Vehicle Branding → Import/Export Branding Pack → Import a pack.

6. To put a video in the pack, record one on the head unit (Vehicle Branding → Record a Video), keep it in the theme's folder and add `--video custom-themes/my-car/my-car-tour.mp4` when building. People see it inside the ZIP; RetroDeck checks it but doesn't import it. A pack with a video needs RetroDeck 0.20.0 or newer.

## Placements

Each zone (`header`, `faceplate`, `corner`, `lcd`, `visualizer`, `startup`, `standby`) has a placement for each logo (`manufacturer`, `model`, `audio`): `visible`, `scale` (0.1 to 1 of the largest size that fits the zone) and `x`, `y` (the logo's centre, 0 to 1 across the zone). On Z32 Factory 1990, `header` is the tape door and `faceplate` is above PWR, the changer's eject key on the real unit.

If this folder is inside Dropbox, iCloud Drive or another synced folder, that service still uploads it: set it to skip this folder if you want it kept off the cloud.

# Custom themes

Your own themes for real radios: the skin, your logos and where each one sits, built into a branding pack you import on the head unit.

**Everything in this folder stays on your computer.** Git ignores it, except this guide and the template, so your logos and packs are never committed or pushed to GitHub. RetroDeck ships no real logos; the ones you put here are yours to use. Only share a pack with people who may use the logos in it, and never post one in this repository or its issues.

## Make one

1. Make a folder here for your car, e.g. `custom-themes/my-car/`.
2. Copy `template-z32.json` into it as `theme.json`, and fill in the manufacturer, model and year. The template uses Z32 Factory 1990; change `"skin"` and the placements for another skin (skin ids are on each page in [themes](../themes/)).
3. Put your logos in the folder, preferably PNGs with light artwork on a transparent background. `manufacturer` is the car maker; `model` is the second mark (a model badge, or the audio brand on a factory radio).
4. Build the pack:

   ```
   python3 tools/make_pack.py custom-themes/my-car/theme.json \
       --manufacturer custom-themes/my-car/maker.png --model custom-themes/my-car/brand.png \
       -o custom-themes/my-car/my-car.zip
   ```

   The pack gets a screenshot, `assets/preview.png`, drawn by RetroDeck's own importer and renderer, (a copy is saved next to the ZIP as `my-car-preview.png`), so whoever you send it to can see it before importing (RetroDeck ignores it when importing). This needs the repository's build setup (JDK 17 in `JAVA_HOME` and the Android SDK) and takes a few minutes the first time; `--no-preview` skips it.
5. Copy the ZIP to the head unit and import it: Settings → Personalization → Vehicle Branding → Import/Export Branding Pack → Import a pack.

## Placements

Each zone (`header`, `faceplate`, `corner`, `lcd`, `visualizer`, `startup`, `standby`) has a placement for each logo: `visible`, `scale` (0.1 to 1 of the largest size that fits the zone) and `x`, `y` (the logo's centre, 0 to 1 across the zone). On Z32 Factory 1990, `header` is the tape door and `faceplate` is above PWR, the changer's eject key on the real unit.

If this folder is inside Dropbox, iCloud Drive or another synced folder, that service still uploads it: set it to skip this folder if you want it kept off the cloud.

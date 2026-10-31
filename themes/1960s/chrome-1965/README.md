# Chrome 1965

**Era:** 1960s  ·  **Size:** Single DIN  ·  **Display:** Segment display lit blue-white, whatever the dash lighting  ·  **Skin id:** `chrome65`

A radio in the style of the 1960s, as made again today for classic cars, in one DIN: a chrome frame round a ribbed black grille, a chrome knob either side, a small window lit blue-white and a row of chiclet keys under it. Modelled in 3D and rendered with Blender (tools/blender/skins.py). Like every RetroDeck skin, it copies no real unit and has no brand names on it.

On a single-DIN head unit's own wide screen (about 8:3) the radio fills the screen; on a bigger one it sits across the lower half under a screen for the visualizer, the song and the clock.

## Screenshots

Recorded from the running app (v0.15.0) on an Android TV box at 1920×1080 (240 dpi), playing a song with the TV muted: the visualizer follows the music, and the display shows MUTE where the artist would be. The faceplate is the 3D render; the display and knob pointer are drawn live on top.

![Chrome 1965 playing, fading from day to night](day-night.webp)

![By day](tv-day.jpg)
<sub>By day (Key Lighting Off)</sub>

![At night](tv-night.jpg)
<sub>At night (Key Lighting Always on)</sub>

## Day and night

Rendered, not taken on a device: the faceplate is modelled in 3D in Blender, and the display and knob pointer are drawn by the app on top in the render tests (no logos).

| Day | Night |
|---|---|
| ![Day, legends printed](render-day.jpg) | ![Night, legends backlit](render-night.jpg) |

On a single-DIN unit's own wide screen (1920×720):

| Day | Night |
|---|---|
| ![Day, wide screen](render-wide-day.jpg) | ![Night, wide screen](render-wide-night.jpg) |

## Keys

**Working:**

- The knob on the left: volume
- PWR, SRC, |<<, PLAY/PAUSE, >>|, DISP and MENU (the chiclet keys)

**For the look only** (they do nothing):

- The tuning knob on the right

## Branding zones

Header (the top of the frame, above the display), faceplate (left of the keys), corner (right of the keys), LCD, visualizer. A logo stays inside its zone and never covers a key.

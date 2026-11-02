# Chrome 1965 Modern

**Era:** 1960s  ·  **Size:** Single DIN  ·  **Display:** Segment display lit blue-white, whatever the dash lighting  ·  **Skin id:** `chrome65m`

The same radio as [Chrome 1965](../chrome-1965/), as made again today for classic cars: a thick chrome frame round a black face ribbed in silver, a big knurled chrome knob either side, and black keys in a chrome channel in place of the old chrome push-buttons. Being modern, it has a visualizer: bars under the song in its blue-white window, and on the screen above it. Modelled in 3D and rendered with Blender (tools/blender/skins.py). Like every RetroDeck skin, it copies no real unit and has no brand names on it.

On a single-DIN head unit's own wide screen (about 8:3) the radio fills the screen; on a bigger one it sits across the lower half under a screen for the visualizer, the song and the clock.

## Screenshots

Recorded from the running app (v0.16.0) on an Android TV box at 1920×1080 (240 dpi), playing a song with the TV muted: the visualizer follows the music, on the radio and on the screen above, and the display shows MUTE where the artist would be. The faceplate is the 3D render; the display and knob pointer are drawn live on top.

![Chrome 1965 Modern playing, fading from day to night](day-night.webp)

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

Header (the top of the frame, above the display), faceplate (under the left knob), corner (under the right knob), LCD (left of the song) and visualizer (under it). A logo stays inside its zone and never covers a key.

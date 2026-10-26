# Contributing to RetroDeck

Thank you for your interest in contributing to RetroDeck, a retro car stereo for Android head units that you can brand to match your own vehicle! Contributions are welcome, and this page keeps the process clear and simple.

## Ways to Contribute

- Report bugs or request features using [GitHub Issues](../../issues).
- Improve documentation (README, code comments, the docs folder).
- Submit Pull Requests (PRs) with bug fixes, improvements, new skins or new features.

## Where Things Are

- `core/`: skins and their branding zones (`Skins.java`), the faceplates (`Faceplate`), the stereo renderer (`StereoView`), illumination colours, the module interface and the shared settings look (`Ui`).
- `branding/`: the Vehicle Branding module: profiles and their storage, logo import and editing, placement, startup animations and branding packs.
- `app/`: the RetroDeck shell: the stereo screen, the music player and Settings. Modules are registered in `RetroDeckApp`.

## Adding a Skin

A skin is a `Skin` in `Skins.java`: colours, a box for each control, and boxes for the branding zones. How it looks (metal, plastic, glass, shadows) is drawn in `Faceplate.java`; check it with `./gradlew :core:testDebugUnitTest -Prender`, which writes PNGs of every skin to `core/build/skin-renders`. Every box is a fraction of the screen. Zones must stay clear of the buttons, knob, presets and track text; `SkinTest` checks this for every skin, so run the tests.

## Rendered Skins

Every skin is modelled in 3D and rendered with [Blender](https://www.blender.org/) (free), instead of being drawn in code: its faceplate is a set of pictures in `core/src/main/assets/skins/<id>/` (day, night, and both with every key pressed in). The model reads the skin's layout from the app, so keys always sit on their touch areas. Z32 Factory 1990 has its own model, `tools/blender/z32.py`; the others are in `tools/blender/skins.py`, built from the parts in `tools/blender/rd.py`:

```bash
./gradlew :core:testDebugUnitTest -Prender -Prender.size=1920x1080   # writes core/build/skin-renders/<id>.json
blender -b --factory-startup -P tools/blender/skins.py -- core/build/skin-renders/hifi84.json /tmp/hifi84
python3 tools/blender/finish.py /tmp/hifi84 hifi84
```

Add `ONLY=day PCT=50` in front of `blender` for a quick half-size look while you work. The display, logos and the knob's pointer are still drawn by the app on top.

Skins whose keys light up in the dash colour at night also get `glow.webp`: the backlit legends alone, in white. At night the app tints it with the chosen colour and adds it to the night picture, so the Illumination setting still works. Z32 Factory 1990 has white legends of its own, rendered into its night pictures.

No skin copies a real unit: each is what car stereos of its era were generally made of. If you have photos of a unit you'd like a skin to look more like, open an issue with them.

## Logos and Brand Names

- Never add real brand logos, badges or trademarked artwork: not in skins, screenshots, demo packs, tests or docs.
- Skins may be modelled on real stereos, but copy only the look (materials, layout, key shapes). Leave off brand names and logos; where the unit had a maker's badge, add a branding zone instead.
- Demo and test logos must be your own original designs, like Halcyon in `docs/demo`. Don't make look-alikes of real badges: a confusingly similar logo can infringe as well.
- Screenshots in pull requests use the demo brand or no logos.

## Code Style

- Plain Java 17 and the Android SDK, no extra libraries unless they really pay for themselves (AndroidSVG is the one so far).
- minSdk is 19 (Android 4.4). Guard newer calls with `Build.VERSION.SDK_INT`; lint fails the build otherwise.
- Match the code around you: its naming, comment style and size.
- Comments say why, not what.
- Keep things simple (KISS principle). Head units are slow and short on memory.

## Pull Request Guidelines

1. **Fork** the repository.
2. Create a new branch for your feature or bugfix:
   ```bash
   git checkout -b feature/my-new-feature
   ```
3. Commit changes with clear, descriptive messages.
4. Make sure it builds and the tests pass: `./gradlew assembleRelease testReleaseUnitTest lintRelease`.
5. Open a Pull Request and describe your changes.

## Tests

- The JVM tests run with `./gradlew testReleaseUnitTest`. Add tests for logic that doesn't need a device.
- Test on a real head unit or a 1024x600 emulator when you can. Say what you tested in the PR, and what you couldn't.
- Never add real manufacturer logos to the repo, tests or screenshots. Use the made-up Halcyon demo art in `docs/demo/`.
- Screenshots in issues and PRs: hide device names, accounts and anything personal.

## Community

We value respectful collaboration. By contributing, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

Thank you for helping make this project welcoming!

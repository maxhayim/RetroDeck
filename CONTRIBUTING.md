# Contributing to RetroDeck

Thank you for your interest in contributing to RetroDeck, a retro car stereo for Android head units that you can brand to match your own vehicle! Contributions are welcome, and this page keeps the process clear and simple.

## Ways to Contribute

- Report bugs or request features using [GitHub Issues](../../issues).
- Improve documentation (README, code comments, the docs folder).
- Submit Pull Requests (PRs) with bug fixes, improvements, new skins or new features.

## Where Things Are

- `core/`: skins and their branding zones (`Skins.java`), the stereo renderer (`StereoView`), illumination colours, the module interface and the shared settings look (`Ui`).
- `branding/`: the Vehicle Branding module: profiles and their storage, logo import and editing, placement, startup animations and branding packs.
- `app/`: the RetroDeck shell: the stereo screen, the music player and Settings. Modules are registered in `RetroDeckApp`.

## Adding a Skin

A skin is a `Skin` in `Skins.java`: colours, a box for each control, and boxes for the branding zones. Every box is a fraction of the screen. Zones must stay clear of the buttons, knob, presets and track text; `SkinTest` checks this for every skin, so run the tests.

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

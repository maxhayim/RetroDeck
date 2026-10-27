# Security Policy – RetroDeck

RetroDeck imports images and branding packs (ZIP files) that may come from other people, so everything it reads
from them is treated as untrusted.

---

## Reporting a Vulnerability

If you discover a security vulnerability:

- Do NOT open a public issue.
- Use GitHub's [Private Vulnerability Reporting](../../security/advisories/new) for this repo.
- Include:
  - A clear description of the issue
  - Steps to reproduce (a sample pack or image helps; use made-up artwork)
  - Affected version(s)
  - Any logs or screenshots (sanitized)

Please allow reasonable time for investigation and patching before public disclosure.

Responsible disclosure is required.

---

## Branding Packs

- A pack may contain only `manifest.json` and images in `assets/` (`.png`, `.jpg`, `.jpeg`, `.webp`, `.svg`). Any
  other file, such as a script, an APK or a DEX file, makes the whole pack fail. Nothing in a pack is ever run.
- Paths with `..`, a leading `/`, backslashes, drive letters or empty parts are refused, so a pack can't write
  outside RetroDeck's own folder (zip slip). Files from a pack are never written to disk under their own names.
- Sizes are counted while unpacking, not taken from the ZIP header: at most 24 MB packed, 8 MB per file, 32 MB in
  all, 256 KB for the manifest and 40 files (zip bombs).
- Every image is checked by its content, not its name, and must match its extension. Width and height are read
  from the header and limited to 4096 pixels a side before anything is decoded.
- Images are then decoded at a reduced size and saved again as new PNG files made by RetroDeck; the original
  bytes are never kept.
- Every value in the manifest is checked and put back in range; unknown skins, presets and colours get defaults.
  Packs from a newer format version are refused rather than half-read.

---

## SVG Logos

- SVG files with a DOCTYPE or entity declarations (the "billion laughs" attack), scripts, embedded HTML
  (`foreignObject`) or links to files or the network are refused.
- SVGs are drawn by [AndroidSVG](https://github.com/BigBadaboom/androidsvg), which doesn't run scripts and, as
  RetroDeck sets it up, doesn't load external files.

---

## Privacy

- No account and no cloud service. The `INTERNET` permission is used only by GIPHY search, while its screen is
  open: the search words and the API key go to GIPHY's API, and results and the chosen GIF come from GIPHY's media
  servers, over HTTPS with TLS 1.2 or newer. Links anywhere else are never followed.
- GIFs, from GIPHY, a file or a pack, are size-checked (8 MB, 800 pixels a side, 500 frames) and decoded by
  RetroDeck's own decoder, never by anything that could run code.
- Profiles and logos stay in the app's private storage. Exporting a pack is the only way they leave the device,
  and only to where you choose.

---

## Supported Versions

Security updates are applied to:

- The latest tagged release

Older versions may not receive patches.

---

## Secure Development Practices

RetroDeck development guidelines:

- No hardcoded credentials, no analytics, no remote logging
- No proprietary logos or trademarks bundled in the app or the repo
- Never trust a file name, extension or size header from an imported file
- Validate every manifest value; never pass imported data to a shell or a class loader

Pull Requests touching pack reading, image decoding or file paths will receive additional review.

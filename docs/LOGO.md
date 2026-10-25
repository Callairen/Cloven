# Cloven logo assets

The official artwork lives, unmodified, in `branding/source/`. Packaged Android resources are
generated from it by `tools/generate_brand_assets.py`; the script only resizes images and places
the launcher artwork on the adaptive-icon canvas. It never redraws or recolours anything.

| Source (`branding/source/`) | Content | Used for | Packaged as |
|---|---|---|---|
| `inapp_logo.png` (1400×405, transparent) | "Cloven" wordmark with the clover as the "o" | Library header | `res/drawable-nodpi/inapp_logo.png` (copied unchanged) |
| `logo.png` (1500×1536, transparent) | Standalone clover | Widget; compact in-app clover (empty states, Activity screen, reader, Settings, Activity tab icon) | `res/drawable-nodpi/logo.png` (resized to 384 px wide) |
| `icon.png` (3000×3072, opaque cream) | Clover on cream | Launcher icon (app drawer, home screen, splash screen) | `res/mipmap-*dpi/ic_launcher_foreground.png` + `@color/launcher_background` |
| `cloven_logo.png` (3000×3072, opaque cream) | Small wordmark on cream | Not used (superseded by the three assets above); kept as supplied | — |

Notes:

* **`drawable-nodpi`**: plain `drawable/` is treated as mdpi, so large PNGs placed there are upscaled
  ~2.75× on a typical phone. A 3000 px image would decode to roughly 8000 px (~280 MB).
* **Launcher icon**: minSdk is 26, so every device uses the adaptive icon in
  `mipmap-anydpi-v26/`. The clover in `icon.png` reaches 87 % of the half-width from the centre, but launchers
  only guarantee a 66/108 dp circle survives masking. The artwork is therefore scaled to 70 % of the
  108 dp canvas, on a background colour sampled from the icon itself (`#FBEDD3`), so there is no visible
  seam or white square. There is no monochrome (themed-icon) layer, because that would need separate silhouette artwork.
* **Tinting**: `CloverMark` draws `logo.png` in its original colours. A tint is used only where a
  one-colour silhouette is wanted (Activity tab, cover fallback, reader accents). That only works
  because `logo.png` is transparent: tinting an opaque image fills its whole square.
* **Dark theme**: the wordmark's letters are dark forest green on transparency. On the dark theme the
  header shows it on a buttercream plate so it stays legible. A light-on-dark variant of `inapp_logo.png`
  would allow dropping the plate.

## Replacing artwork

1. Put the new file in `branding/source/` with the same name.
2. Run `python tools/generate_brand_assets.py` from the project root (needs Pillow).
3. If the new `icon.png` has a different background colour, update `launcher_background` in
   `res/values/colors.xml`. If its artwork is larger or smaller, adjust `ICON_ART_SCALE` in the script.
4. Rebuild and reinstall. Launchers cache icons; if the old icon persists, remove and re-add the
   home-screen shortcut or restart the launcher.

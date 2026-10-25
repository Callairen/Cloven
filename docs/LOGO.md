# Cloven logo assets

**Status: no official logo was supplied with the project.** Cloven currently ships a
*temporary placeholder* four-leaf clover drawn as a vector in Clover Green `#588157`. It is
not the official Cloven logo and should be replaced.

## Where the mark is used

| Resource | Used for |
|---|---|
| `app/src/main/res/drawable/cloven_logo.xml` | In-app branding (library header, empty states, activity screen, reader), the Activity tab icon, the widget clover, and the widget picker preview |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive launcher icon foreground and themed (monochrome) icon |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | Adaptive icon: Buttercream `#FFF1D2` background + the foreground above |

`cloven_logo.xml` colours itself with `@color/clover_mark`, which is `#588157` in light mode
and a lighter green (`values-night/colors.xml`) in dark mode for contrast on dark surfaces.

## Replacing the placeholder

1. Keep the original file(s) you receive in `branding/source/` (unmodified).
2. **Vector (SVG) logo** — preferred. In Android Studio use *File → New → Vector Asset* to
   import it, then overwrite `drawable/cloven_logo.xml`. Change its fill colours to
   `@color/clover_mark` if you want the automatic dark-mode tint; otherwise leave them as supplied.
3. **Launcher icon** — use *File → New → Image Asset* (Launcher Icons, adaptive), pick the logo
   as the foreground layer, background colour `#FFF1D2`, and keep the mark inside the 66 dp safe zone.
   This regenerates `ic_launcher_foreground` and the mipmaps.
4. **Raster (PNG) logo** — put a transparent PNG at `drawable-nodpi/cloven_logo.png` and delete
   `cloven_logo.xml` (same resource name, so no code changes are needed). Provide at least 192×192 px.
5. Rebuild. No Kotlin changes are required: every usage references these resource names.

"""Generates Cloven's packaged brand resources from the untouched originals in branding/source/.

Artwork is never redrawn or recoloured; it is only resized and, for the launcher icon, placed on
the adaptive-icon canvas. Run from the project root after replacing any source image:

    python tools/generate_brand_assets.py
"""
from pathlib import Path
import shutil

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding" / "source"
RES = ROOT / "app" / "src" / "main" / "res"

# Adaptive icons are 108 dp; launchers only guarantee a 66 dp circle survives the mask.
# Measured: the clover in icon.png reaches 0.869 of the half-width from the centre, so it is
# scaled to 70 % of the canvas to keep every leaf inside the safe circle.
ICON_ART_SCALE = 0.70
ICON_DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# Standalone clover: largest on-screen use is 64 dp (Activity card) -> 256 px at xxxhdpi.
LOGO_WIDTH_PX = 384


def launcher_foregrounds() -> None:
    art = Image.open(SRC / "icon.png").convert("RGBA")
    for density, canvas in ICON_DENSITIES.items():
        target_h = round(canvas * ICON_ART_SCALE)
        target_w = round(target_h * art.width / art.height)
        scaled = art.resize((target_w, target_h), Image.LANCZOS)
        layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        layer.alpha_composite(scaled, ((canvas - target_w) // 2, (canvas - target_h) // 2))
        out = RES / f"mipmap-{density}" / "ic_launcher_foreground.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        layer.save(out, optimize=True)
        print("wrote", out.relative_to(ROOT), layer.size)


def standalone_logo() -> None:
    logo = Image.open(SRC / "logo.png").convert("RGBA")
    height = round(LOGO_WIDTH_PX * logo.height / logo.width)
    out = RES / "drawable-nodpi" / "logo.png"
    logo.resize((LOGO_WIDTH_PX, height), Image.LANCZOS).save(out, optimize=True)
    print("wrote", out.relative_to(ROOT), (LOGO_WIDTH_PX, height))


def in_app_logo() -> None:
    # 1400x405 is already a sensible size; it is copied unchanged into a density-independent
    # folder so Android doesn't upscale it as an mdpi resource.
    out = RES / "drawable-nodpi" / "inapp_logo.png"
    shutil.copyfile(SRC / "inapp_logo.png", out)
    print("copied", out.relative_to(ROOT))


if __name__ == "__main__":
    launcher_foregrounds()
    standalone_logo()
    in_app_logo()

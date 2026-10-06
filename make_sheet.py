"""Build the Rogue Social sprite sheet from Noto Emoji images.

Usage: python make_sheet.py <noto png dir> <output dir>

Reads cast.json (next to this script), which lists each sprite's name and the
Unicode code point of the emoji that stands in for it. Writes:

  spritesheet.png  one row of normal tiles, one row of red-tinted "elite" tiles
  sprites.json     where each sprite sits in the sheet, in tile coordinates
  preview.png      the sheet on a dark floor with labels, for humans to look at

Replacing the emoji with real art later means repainting spritesheet.png and
keeping the same layout. Nothing else has to change.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

TILE = 64
ELITE_TINT = (255, 70, 70)
ELITE_STRENGTH = 0.55


def load_tile(src_dir: Path, codepoint: str) -> Image.Image:
    img = Image.open(src_dir / f"emoji_u{codepoint}.png").convert("RGBA")
    return img.resize((TILE, TILE), Image.LANCZOS)


def tint(tile: Image.Image) -> Image.Image:
    """Shift a tile toward red, keeping its shading and transparency."""
    solid = Image.new("RGBA", tile.size, ELITE_TINT + (255,))
    red = ImageChops.multiply(tile, solid)
    out = Image.blend(tile, red, ELITE_STRENGTH)
    out.putalpha(tile.getchannel("A"))
    return out


def main() -> None:
    src_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    cast = json.loads((Path(__file__).parent / "cast.json").read_text())

    sheet = Image.new("RGBA", (TILE * len(cast), TILE * 2), (0, 0, 0, 0))
    index = {"tileSize": TILE, "image": "spritesheet.png", "sprites": {}}

    for col, entry in enumerate(cast):
        tile = load_tile(src_dir, entry["codepoint"])
        sheet.paste(tile, (col * TILE, 0))
        index["sprites"][entry["name"]] = {"col": col, "row": 0}
        if entry["kind"] == "monster":
            sheet.paste(tint(tile), (col * TILE, TILE))
            index["sprites"][entry["name"] + "_elite"] = {"col": col, "row": 1}

    sheet.save(out_dir / "spritesheet.png", optimize=True)
    (out_dir / "sprites.json").write_text(json.dumps(index, indent=2) + "\n")

    # Preview: tiles on a dark checkered floor, names underneath.
    label_h, pad = 22, 12
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    w = pad * 2 + TILE * len(cast)
    h = pad * 2 + (TILE + label_h) * 2
    preview = Image.new("RGBA", (w, h), (18, 18, 24, 255))
    draw = ImageDraw.Draw(preview)
    for row in range(2):
        for col, entry in enumerate(cast):
            x, y = pad + col * TILE, pad + row * (TILE + label_h)
            shade = (38, 38, 48, 255) if (col + row) % 2 else (30, 30, 39, 255)
            draw.rectangle([x, y, x + TILE - 1, y + TILE - 1], fill=shade)
            if row == 1 and entry["kind"] != "monster":
                continue
            label = "elite" if row else entry["name"]
            tw = draw.textlength(label, font=font)
            draw.text((x + (TILE - tw) / 2, y + TILE + 4), label, font=font,
                      fill=(200, 200, 210, 255))
    preview.alpha_composite(sheet, (pad, pad), (0, 0, sheet.width, TILE))
    preview.alpha_composite(sheet, (pad, pad + TILE + label_h), (0, TILE, sheet.width, TILE * 2))
    preview.save(out_dir / "preview.png", optimize=True)

    print(f"{len(index['sprites'])} sprites, sheet {sheet.width}x{sheet.height}")


if __name__ == "__main__":
    main()

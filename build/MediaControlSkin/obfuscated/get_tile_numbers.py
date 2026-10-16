from PIL import Image, ImageDraw
import os

# === Настройки ===
INPUT_PATH = "atlas.png"
OUTPUT_PATH_SEQ = "atlas_numbered.png"
OUTPUT_PATH_XY  = "atlas_numbered_XY.png"
TILE_SIZE = 16

# Если True — рисуем "0xAF", если False — просто "AF" (влезает крупнее)
INCLUDE_0X_PREFIX = False

# === Пиксельный шрифт 3x5 ===
PIXEL_FONT = {
    '0': ["111", "101", "101", "101", "111"],
    '1': ["010", "110", "010", "010", "111"],
    '2': ["111", "001", "111", "100", "111"],
    '3': ["111", "001", "111", "001", "111"],
    '4': ["101", "101", "111", "001", "001"],
    '5': ["111", "100", "111", "001", "111"],
    '6': ["111", "100", "111", "101", "111"],
    '7': ["111", "001", "001", "001", "001"],
    '8': ["111", "101", "111", "101", "111"],
    '9': ["111", "101", "111", "001", "111"],
    'A': ["111", "101", "111", "101", "101"],
    'B': ["110", "101", "110", "101", "110"],
    'C': ["111", "100", "100", "100", "111"],
    'D': ["110", "101", "101", "101", "110"],
    'E': ["111", "100", "110", "100", "111"],
    'F': ["111", "100", "110", "100", "100"],
    'x': ["000", "101", "010", "101", "000"],
    'X': ["101", "101", "010", "101", "101"],
}

GLYPH_W = 3
GLYPH_H = 5

def measure_text(text, scale):
    spacing = scale  # 1 "пиксель" между глифами
    w = len(text) * GLYPH_W * scale + (len(text) - 1) * spacing
    h = GLYPH_H * scale
    return w, h

def best_scale(text, tile_size, pad=2):
    """Максимальный масштаб, при котором текст влезает в тайл."""
    for s in (3, 2, 1):
        w, h = measure_text(text, s)
        if w + 2 * pad <= tile_size and h + 2 * pad <= tile_size:
            return s
    return 1

def draw_pixel_label(img, tile_x, tile_y, text, tile_size):
    scale = best_scale(text, tile_size)
    w, h = measure_text(text, scale)
    pad = 1

    # центр тайла
    cx = tile_x + tile_size // 2
    cy = tile_y + tile_size // 2

    # подложка (тёмный полупрозрачный прямоугольник)
    bw, bh = w + 2 * pad, h + 2 * pad
    bx = cx - bw // 2
    by = cy - bh // 2
    bg = Image.new("RGBA", (bw, bh), (0, 0, 0, 200))
    img.alpha_composite(bg, (bx, by))

    # сами "пиксели" текста
    draw = ImageDraw.Draw(img)
    x = cx - w // 2
    y = cy - h // 2
    spacing = scale
    for ch in text:
        glyph = PIXEL_FONT.get(ch, PIXEL_FONT.get(ch.upper(), PIXEL_FONT['0']))
        for row_i, row_str in enumerate(glyph):
            for col_i, c in enumerate(row_str):
                if c == '1':
                    px = x + col_i * scale
                    py = y + row_i * scale
                    draw.rectangle(
                        [px, py, px + scale - 1, py + scale - 1],
                        fill=(255, 255, 255, 255),
                    )
        x += GLYPH_W * scale + spacing

def main():
    if not os.path.exists(INPUT_PATH):
        raise FileNotFoundError(f"Не найден файл: {INPUT_PATH}")

    base = Image.open(INPUT_PATH).convert("RGBA")
    W, H = base.size
    cols = W // TILE_SIZE
    rows = H // TILE_SIZE

    img_seq = base.copy()
    img_xy  = base.copy()

    if cols > 16 or rows > 16:
        print(f"⚠ Сетка {cols}x{rows}: один hex-разряд переполнится (>15).")

    index = 0
    for row in range(rows):
        for col in range(cols):
            x0 = col * TILE_SIZE
            y0 = row * TILE_SIZE

            # 1) последовательный номер
            draw_pixel_label(img_seq, x0, y0, str(index), TILE_SIZE)

            # 2) hex-координаты
            hex_part = f"{col:X}{row:X}"
            label_xy = f"0x{hex_part}" if INCLUDE_0X_PREFIX else hex_part
            draw_pixel_label(img_xy, x0, y0, label_xy, TILE_SIZE)

            index += 1

    img_seq.save(OUTPUT_PATH_SEQ)
    img_xy.save(OUTPUT_PATH_XY)
    print(f"Готово: {index} тайлов ({cols}x{rows})")
    print(f"  {OUTPUT_PATH_SEQ}")
    print(f"  {OUTPUT_PATH_XY}")

if __name__ == "__main__":
    main()
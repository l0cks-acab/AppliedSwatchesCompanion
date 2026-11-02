"""Swatch Lens item texture (16x16): AE2-style white and grey frame, fluix glass, colour swatch ring."""
import math
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/appliedswatches_companion/textures/item/swatch_lens.png"

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

OUTLINE = (40, 44, 52, 255)
FRAME = (226, 230, 236, 255)
FRAME_DARK = (150, 156, 166, 255)
HANDLE = (110, 116, 126, 255)
HANDLE_LIGHT = (170, 176, 186, 255)
SWATCH = [(222, 74, 66), (236, 180, 60), (98, 196, 92), (70, 150, 230), (160, 96, 220), (230, 110, 180)]

cx, cy, r_out, r_in = 6.0, 6.0, 5.6, 3.7
for y in range(16):
    for x in range(16):
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if d <= r_in:
            # fluix glass: purple with a light streak
            t = (x - y) / 10
            base = (120 + int(30 * t), 90 + int(20 * t), 200 + int(20 * t))
            if abs((x + 0.5) - (y + 0.5) + 1.5) < 0.9 and d < r_in - 0.6:
                base = (228, 216, 255)
            px[x, y] = base + (255,)
        elif d <= r_in + 1.0:
            ang = (math.atan2(y + 0.5 - cy, x + 0.5 - cx) + math.pi) / (2 * math.pi)
            px[x, y] = SWATCH[int(ang * len(SWATCH)) % len(SWATCH)] + (255,)
        elif d <= r_out:
            px[x, y] = FRAME if (x + y) < 12 else FRAME_DARK
        elif d <= r_out + 0.9:
            px[x, y] = OUTLINE

# handle, angled down-right
for i in range(5):
    x, y = 10 + i, 10 + i
    for dx, dy, c in ((0, 0, HANDLE), (1, 0, HANDLE_LIGHT), (0, 1, HANDLE)):
        if 0 <= x + dx < 16 and 0 <= y + dy < 16:
            px[x + dx, y + dy] = c
    for dx, dy in ((2, 0), (-1, 1), (0, 2), (1, -1)):
        xx, yy = x + dx, y + dy
        if 0 <= xx < 16 and 0 <= yy < 16 and px[xx, yy][3] == 0:
            px[xx, yy] = OUTLINE

img.save(OUT)

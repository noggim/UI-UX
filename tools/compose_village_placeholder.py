"""
마을 임시 배경 그림을 만든다 (GPT 가 진짜 그림을 주면 버린다, docs/ART_REQUESTS.md 1번).

선우 버전(main 브랜치 3963690)의 마을 구역을 그대로 옮겨 그린다:
  바닥·벽 = assets/maps/world.tmx 의 ground·walls 층 (tiles.png), 장식 = decor 오브젝트 (sprites/*.png).
마을 사람·수정은 상태(마왕 전/후)에 따라 바뀌므로 그리지 않는다 (게임이 그 위에 따로 그린다).

실행: python tools/compose_village_placeholder.py <선우 버전 폴더> <출력 png>
결과는 768x672 을 두 배로 키운 1536x1344 (게임 안 가로 24칸).
"""
import json
import os
import re
import sys

from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
X0, Y0, W, H = 64, 3744, 768, 672

tmx = open(os.path.join(SRC, "assets/maps/world.tmx"), encoding="utf-8").read()
mw = int(re.search(r'<map[^>]* width="(\d+)"', tmx).group(1))
tiles = Image.open(os.path.join(SRC, "assets/maps/tiles.png")).convert("RGBA")
manifest = json.load(open(os.path.join(SRC, "assets/sprites/manifest.json"), encoding="utf-8"))

img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
for layer in ("ground", "walls"):
    csv = re.search(r'<layer[^>]*name="%s".*?<data encoding="csv">(.*?)</data>' % layer, tmx, re.S).group(1)
    cells = [int(c) for c in csv.replace("\n", "").split(",") if c.strip()]
    for ty in range(Y0 // 32, (Y0 + H) // 32):
        for tx in range(X0 // 32, (X0 + W) // 32):
            gid = cells[ty * mw + tx]
            if gid == 0:
                continue
            t = tiles.crop(((gid - 1) * 32, 0, gid * 32, 32))
            img.alpha_composite(t, ((tx * 32) - X0, (ty * 32) - Y0))

decor = []
for m in re.finditer(r'<object id="\d+" name="[^"]*" type="decor" x="([\d.]+)" y="([\d.]+)"[^>]*>(.*?)</object>', tmx, re.S):
    x, y = float(m.group(1)), float(m.group(2))
    if not (X0 <= x <= X0 + W and Y0 <= y <= Y0 + H + 40):
        continue
    props = dict(re.findall(r'<property name="([^"]+)"(?: type="[^"]+")? value="([^"]*)"', m.group(3)))
    decor.append((y, x, props))
decor.sort()
for y, x, p in decor:
    name = p["sprite"]
    sheet = Image.open(os.path.join(SRC, "assets/sprites", name + ".png")).convert("RGBA")
    n = manifest.get(name, 1)
    fw = sheet.width // n
    frame = sheet.crop((0, 0, fw, sheet.height))
    lift = 4 if p.get("flat") == "true" else 3
    px, py = round(x - fw / 2 - X0), round(y - Y0 + lift - sheet.height)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    layer.paste(frame, (px, py), frame)
    img = Image.alpha_composite(img, layer)

img = img.resize((W * 2, H * 2), Image.NEAREST)
img.save(OUT)
print("saved", OUT, img.size, len(decor), "decor")

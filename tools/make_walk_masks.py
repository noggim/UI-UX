"""지금 바닥 도형(walkable - blocked)으로 이동 마스크 PNG 초안을 만들고 JSON 에 walkMask 를 넣는다.

저장소 루트에서 실행: python tools/make_walk_masks.py [--force] [id ...]
이미 있는 마스크는 덮어쓰지 않는다 (사람이 칠한 마스크 보호). --force 로 다시 만든다.
필요: Pillow, OpenCV, numpy.
"""
import json, os, re, sys
import cv2, numpy as np

A = 'assets/'
force = '--force' in sys.argv
only = {a for a in sys.argv[1:] if not a.startswith('--')}

def poly(flat, w, h):
    pts = np.array([[flat[i] * w, flat[i + 1] * h] for i in range(0, len(flat), 2)])
    return np.round(pts * 16).astype(np.int32)  # 1/16 픽셀 정밀도

def raster(walk, blocked, w, h):
    m = np.zeros((h, w), np.uint8)
    cv2.fillPoly(m, [poly(walk, w, h)], 255, lineType=cv2.LINE_8, shift=4)
    for b in blocked:
        cv2.fillPoly(m, [poly(b, w, h)], 0, lineType=cv2.LINE_8, shift=4)
    return m

def items():
    m = json.load(open(A + 'data/map.json', encoding='utf-8'))
    s = json.load(open(A + 'data/natural-room-scenes.json', encoding='utf-8'))
    for sc in s['scenes']:
        yield 'natural-room-scenes.json', 'rooms', sc['id'], sc['walkable'], sc.get('blocked', []), sc['pixelWidth'], sc['pixelHeight']
    for j in m.get('joints', []):
        h, w = cv2.imread(A + j['image'], cv2.IMREAD_UNCHANGED).shape[:2]
        yield 'map.json', 'joints', j['id'], j['walkable'], j.get('blocked', []), w, h
    for p in m.get('passageScenes', []):
        yield 'map.json', 'passages', p['id'], p['walkable'], [], p['pixelWidth'], p['pixelHeight']

made = {}
for fn, kind, id_, walk, blocked, w, h in items():
    if only and id_ not in only: continue
    rel = f'walkmask/{kind}/{id_}.png'
    made.setdefault(fn, {})[id_] = rel
    if os.path.exists(A + rel) and not force: continue
    os.makedirs(os.path.dirname(A + rel), exist_ok=True)
    cv2.imwrite(A + rel, raster(walk, blocked, w, h), [cv2.IMWRITE_PNG_BILEVEL, 1])

for fn, ids in made.items():
    lines = open(A + 'data/' + fn, encoding='utf-8').read().split('\n')
    out, cur = [], None
    for l in lines:
        mm = re.match(r'      "id": "([^"]+)"', l)
        if mm: cur = mm.group(1)
        if '"walkMask"' in l and cur in ids: continue
        if '"walkable": [' in l and cur in ids:
            out.append(l[:l.index('"walkable"')] + f'"walkMask": "{ids[cur]}",')
        out.append(l)
    open(A + 'data/' + fn, 'w', encoding='utf-8').write('\n'.join(out))
    print(fn, len(ids))

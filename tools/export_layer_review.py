"""Export existing foreground layers to the portable reviewer without changing game art.

Usage: python tools/export_layer_review.py ../yongsa-mask-review
The reviewer remains an independent, offline review package. Personal feedback is never overwritten.
"""
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def export(destination):
    dest = Path(destination).resolve()
    manifest = json.loads((dest / 'manifest.js').read_text(encoding='utf8').split('=', 1)[1].strip().rstrip(';'))
    scenes = json.loads((ROOT / 'assets/data/natural-room-scenes.json').read_text(encoding='utf8'))['scenes']
    world = json.loads((ROOT / 'assets/data/map.json').read_text(encoding='utf8'))
    occlusion = json.loads((ROOT / 'assets/data/scene-occlusion.json').read_text(encoding='utf8'))['scenes']
    records = {}
    for item in manifest['items']:
        source = ROOT / 'assets' / item['image']
        if hashlib.sha256(source.read_bytes()).hexdigest() != item['artSha256']:
            raise ValueError(f"Artwork changed: {item['id']}")
        record = {'polygons': [], 'raster': None}
        if item['kind'] == 'rooms':
            scene = next(s for s in scenes if s['id'] == item['id'])
            masks = occlusion.get(item['id'], [])
            front = source.with_name(source.stem + '-front.png')
            record['worldWidth'] = scene['suggestedWidthTiles'] * 32
            if front.exists():
                relative = front.relative_to(ROOT / 'assets').as_posix()
                target = dest / 'assets' / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(front, target)
                record['raster'] = {'id': 'existing-front', 'image': relative, 'exclude': scene['walkable'], 'mode': 'always', 'depth': 1}
        elif item['kind'] == 'joints':
            joint = next(j for j in world['joints'] if j['id'] == item['id'])
            masks = joint.get('foregrounds', [])
            record['worldWidth'] = joint['rect'][2]
        else:
            masks = []
            placements = [l['painted'] for l in world['links'] if l.get('painted', {}).get('scene') == item['id']]
            record['worldWidth'] = placements[0]['width'] * 32 if placements else 1024
        for mask in masks:
            record['polygons'].append({**mask, 'mode': mask.get('mode', 'always' if mask['depth'] >= .95 else 'behind'), 'source': 'game'})
        records[item['kind'] + '/' + item['id']] = record
    hd = json.loads((ROOT / 'assets/data/hd-art.json').read_text(encoding='utf8'))['sprites']['world_hero_down']
    target = dest / 'assets' / hd['sheet']
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / 'assets' / hd['sheet'], target)
    data = {'version': 1, 'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), 'hero': hd, 'items': records}
    (dest / 'layers-manifest.js').write_text('window.LAYER_REVIEW_MANIFEST = ' + json.dumps(data, ensure_ascii=False, separators=(',', ':')) + ';\n', encoding='utf8')
    count = sum(bool(r['polygons'] or r['raster']) for r in records.values())
    print(f'{len(records)} images; {count} with existing foreground drafts; empty images remain explicitly unmarked.')

if __name__ == '__main__':
    export(sys.argv[1])

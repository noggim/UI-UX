"""Inspect deterministic collision masks against artwork (Pillow only).

python tools/review_walk_masks.py [--output design/walkmask-audit/before]
The artwork and runtime masks are read only; review sheets are never game assets.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]

def entries():
    scenes = json.loads((ROOT / 'assets/data/natural-room-scenes.json').read_text(encoding='utf-8'))
    world = json.loads((ROOT / 'assets/data/map.json').read_text(encoding='utf-8'))
    for kind, items in [('rooms', scenes['scenes']), ('passages', world['passageScenes']), ('joints', world['joints'])]:
        for item in items:
            yield dict(kind=kind, id=item['id'], image=item['image'], mask=item['walkMask'])

def overlay(item, width=640, grid=False):
    art = Image.open(ROOT / 'assets' / item['image']).convert('RGB')
    mask = Image.open(ROOT / 'assets' / item['mask']).convert('L')
    assert art.size == mask.size, item['id']
    art.thumbnail((width, int(width * .7)))
    mask = mask.resize(art.size, Image.Resampling.NEAREST)
    tinted = Image.blend(art, Image.new('RGB', art.size, (24, 240, 158)), .23)
    art = Image.composite(tinted, art, mask)
    edge = mask.filter(ImageFilter.FIND_EDGES).point(lambda p: 255 if p > 0 else 0)
    art.paste((100,255,190), mask=edge)
    if grid:
        d=ImageDraw.Draw(art)
        for i in range(1,10):
            x=round(art.width*i/10); y=round(art.height*i/10)
            d.line((x,0,x,art.height),fill=(120,140,145),width=1)
            d.line((0,y,art.width,y),fill=(120,140,145),width=1)
            d.text((x+2,2),str(i*10),fill='white',stroke_width=1,stroke_fill='black')
            d.text((2,y+2),str(i*10),fill='white',stroke_width=1,stroke_fill='black')
    return art

def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',default='design/walkmask-audit/before'); p.add_argument('--id'); args=p.parse_args()
    out=ROOT/args.output; out.mkdir(parents=True,exist_ok=True)
    items=list(entries())
    if args.id:
        item=next(x for x in items if x['id']==args.id)
        overlay(item,1400,True).save(out/(args.id+'.png'))
        return
    (out/'inventory.json').write_text(json.dumps(items,indent=2),encoding='utf-8')
    for kind in ['rooms','passages','joints']:
        group=[x for x in items if x['kind']==kind]
        for start in range(0,len(group),4):
            sheet=Image.new('RGB',(1280,960),(18,22,28)); d=ImageDraw.Draw(sheet)
            for n,item in enumerate(group[start:start+4]):
                x=n%2*640; y=n//2*480
                d.text((x+8,y+8),f"{start+n+1:02d} {kind}/{item['id']}",fill='white')
                sheet.paste(overlay(item), (x,y+30))
            sheet.save(out/f'{kind}-{start//4+1:02d}.jpg',quality=92)
    print(f'{len(items)} masks; review sheets: {out}')

if __name__=='__main__': main()

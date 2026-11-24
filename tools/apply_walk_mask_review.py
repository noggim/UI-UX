"""Apply reviewed collision-data polygons, without editing any scene artwork.

The coordinates in docs/walk-mask-review.json are measured against each original
painting, normalized from the top left. White/black edits are idempotent; no
resizing, antialiasing, dilation or image generation is involved. Pillow required.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw
from review_walk_masks import ROOT, entries

def main():
    review=json.loads((ROOT/'docs/walk-mask-review.json').read_text(encoding='utf-8'))
    known={x['mask']:x for x in entries()}
    changed=[]
    for item in review['masks']:
        if not item['edits']: continue
        ref=known[item['mask']]
        art=ROOT/'assets'/ref['image']; target=ROOT/'assets'/ref['mask']
        if hashlib.sha256(art.read_bytes()).hexdigest()!=item['artSha256']:
            raise ValueError(f"Artwork changed; re-review {item['mask']}")
        original=Image.open(target).convert('L'); w,h=original.size
        assert [w,h]==item['size'], item['mask']
        if set(original.tobytes())-{0,255}: raise ValueError('Non-binary input mask')
        result=original.copy(); draw=ImageDraw.Draw(result)
        for edit in item['edits']:
            points=edit['points']; assert len(points)>=3
            assert edit['floor'] in (True,False)
            assert all(0<=u<=1 and 0<=v<=1 for u,v in points)
            draw.polygon([(round(u*(w-1)),round(v*(h-1))) for u,v in points],fill=255 if edit['floor'] else 0)
        if result.tobytes()!=original.tobytes():
            result.convert('1',dither=Image.Dither.NONE).save(target)
            changed.append(item['mask'])
    print(f'Changed {len(changed)} collision masks')
    for path in changed: print(path)

if __name__=='__main__': main()

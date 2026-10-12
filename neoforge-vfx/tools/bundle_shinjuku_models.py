from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image
import hashlib, json

root=Path(__file__).resolve().parents[1]
art=root/'art/shinjuku-characters'
out=art/'新宿之战模型与动画.zip'
files=[p for p in art.iterdir() if p.suffix in ('.bbmodel','.png','.json','.md')]
files+=list((art/'references').iterdir())
files += [art/'previews'/n for n in ['overview.png','views-review.png','actions-review.png',
          'mahoraga-head.png','characters-animated.gif','gojo-signature.gif','sukuna-signature.gif','mahoraga-wheel.gif']]
for name in ['characters-animated.gif','gojo-signature.gif','sukuna-signature.gif','mahoraga-wheel.gif']:
    im=Image.open(art/'previews'/name)
    assert getattr(im,'n_frames',1)>10, name
with ZipFile(out,'w',ZIP_DEFLATED,compresslevel=9) as z:
    for p in files:
        if p.is_file(): z.write(p,p.relative_to(art).as_posix())
    for name in ['generate_shinjuku_characters.mjs','shinjuku_mesh.mjs','build_mahoraga_shinjuku.mjs']:
        z.write(root/'tools'/name,'generator/'+name)
with ZipFile(out) as z:
    assert z.testzip() is None
report={'file':str(out),'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),
        'models':3,'clips':13,'actualBlockbenchPreviewGifs':4}
(root/'build/shinjuku-bundle.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(report,ensure_ascii=False))

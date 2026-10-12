"""Overlay the reviewed character classes/assets onto the exact preceding release."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib, json, struct

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
RES = ROOT / 'src/main/resources'
REVIEW = json.loads((BUILD/'shinjuku-isolated-067/release-class-review.json').read_text('utf-8'))
BASE = Path(REVIEW['baselineJar'])
OUT = BUILD/'libs/jujutsu-vfx-0.67.0-neoforge-1.21.1.jar'
sha = lambda b: hashlib.sha256(b).hexdigest()
assert REVIEW['baselineParityPassed'] and REVIEW['compiledAgainstInstalledBaselineOnly']
assert sha(BASE.read_bytes()) == REVIEW['baselineSha256'], 'Baseline changed'
with ZipFile(BASE) as z:
    old = {i.filename:z.read(i) for i in z.infolist()}
    entries = {i.filename:i for i in z.infolist()}
overlay = {}
for row in REVIEW['classOverlay']:
    data = (BUILD/'shinjuku-isolated-067/classes'/row['class']).read_bytes()
    assert sha(data) == row['sha256'], row['class']
    overlay[row['class']] = data
for who in ['gojo','sukuna','mahoraga']:
    for rel in [f'assets/jujutsuvfx/models/entity/shinjuku/{who}_shinjuku.json',
                f'assets/jujutsuvfx/textures/entity/shinjuku/{who}_shinjuku.png',
                f'assets/jujutsuvfx/models/item/{who}_shinjuku_spawn_egg.json']:
        data = (RES/rel).read_bytes()
        if rel.endswith('.json'): json.loads(data)
        overlay[rel] = data
assets=json.loads((BUILD/'shinjuku-art-assets-review.json').read_text('utf-8'))
for model in assets['models']:
    rel=f"assets/jujutsuvfx/models/entity/shinjuku/{model['id']}.json"
    assert sha(overlay[rel]) == model['runtimeSha256']
language_keys = [f'entity.jujutsuvfx.{w}_shinjuku' for w in ['gojo','sukuna','mahoraga']]
language_keys += [f'item.jujutsuvfx.{w}_shinjuku_spawn_egg' for w in ['gojo','sukuna','mahoraga']]
language_keys += ['entity.jujutsuvfx.shinjuku.display','entity.jujutsuvfx.shinjuku.wander']
for lang in ['zh_cn','en_us']:
    rel=f'assets/jujutsuvfx/lang/{lang}.json'
    baseline=json.loads(old[rel]); current=json.loads((RES/rel).read_text('utf-8'))
    baseline.update({k:current[k] for k in language_keys})
    overlay[rel]=(json.dumps(baseline,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
rel='META-INF/neoforge.mods.toml'
metadata=old[rel].decode('utf-8')
assert 'version="0.66.0"' in metadata
overlay[rel]=metadata.replace('version="0.66.0"','version="0.67.0"',1).encode('utf-8')
final=old|overlay
for name,data in final.items():
    if not name.endswith('.class'): continue
    count=struct.unpack_from('>H',data,8)[0]; pool={}; refs=[]; offset=10; index=1
    while index<count:
        tag=data[offset]; offset+=1
        if tag==1:
            n=struct.unpack_from('>H',data,offset)[0]; offset+=2
            pool[index]=data[offset:offset+n].decode('utf-8',errors='replace'); offset+=n
        elif tag==7: refs.append(struct.unpack_from('>H',data,offset)[0]); offset+=2
        elif tag in (3,4,9,10,11,12,17,18): offset+=4
        elif tag in (5,6): offset+=8; index+=1
        elif tag in (8,16,19,20): offset+=2
        elif tag==15: offset+=3
        else: raise AssertionError((name,tag))
        index+=1
    for ref in refs:
        klass=pool[ref].lstrip('[').removeprefix('L').removesuffix(';')
        if klass.startswith('dev/jujutsuvfx/'): assert klass+'.class' in final,(name,klass)
OUT.parent.mkdir(exist_ok=True,parents=True)
with ZipFile(OUT,'w',ZIP_DEFLATED,compresslevel=9) as z:
    for name,data in final.items():
        z.writestr(entries[name] if name in entries else name,data)
with ZipFile(OUT) as z:
    assert z.testzip() is None
    actual={i.filename:z.read(i) for i in z.infolist()}
assert actual == final
changed=sorted(k for k in old if actual[k]!=old[k])
added=sorted(set(actual)-set(old))
assert set(changed+added) == set(overlay)
report={'version':'0.67.0','source':'reviewed isolated character overlay',
        'baseline':str(BASE),'baselineSha256':sha(BASE.read_bytes()),
        'output':str(OUT),'sha256':sha(OUT.read_bytes()),'bytes':OUT.stat().st_size,
        'changedEntries':changed,'addedEntries':added,'removedEntries':[],
        'allOtherEntriesIdentical':True,'languageKeysAdded':language_keys,
        'ownClassLinkagePassed':True,'blockbenchVisualReview':True,'inGameTested':False}
(BUILD/'release-067-package.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

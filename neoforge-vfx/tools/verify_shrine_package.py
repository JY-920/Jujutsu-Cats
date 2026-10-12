"""Static artifact audit only; does not start Minecraft or invoke game tests."""
import hashlib
import json
import math
import struct
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
art=root/'art/malevolent-shrine'
compiled=root/'build/libs/jujutsu-vfx-0.63.0-neoforge-1.21.1.jar'
jar=root/'build/libs/jujutsu-vfx-0.63.0-shrine-neoforge-1.21.1.jar'
previous=Path('D:/PCL2/PCL/.minecraft/versions/1.21.1-NeoForge模组开发/mods/jujutsu-vfx-0.62.0-neoforge-1.21.1.jar')
if not previous.exists():
    previous=Path(json.loads((root/'build/release-063-shrine-install.json').read_text('utf-8-sig'))['backup'])
mesh_path='assets/jujutsuvfx/models/domain/malevolent_shrine.mesh'
allowed={'META-INF/neoforge.mods.toml','dev/jujutsuvfx/client/DomainRenderer.class','dev/jujutsuvfx/client/DomainRenderer$State.class','dev/jujutsuvfx/client/ShrineMesh.class','dev/jujutsuvfx/client/ShrineMesh$Builder.class','dev/jujutsuvfx/client/ShrineMesh$P.class','dev/jujutsuvfx/client/ShrineMesh$Vertex.class'}
# The shared checkout has another active task's unfinished hand-renderer changes.
# Preserve the installed release and replace only this task's compiled classes/assets.
with zipfile.ZipFile(compiled) as built,zipfile.ZipFile(previous) as baseline,zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as patched:
    for info in baseline.infolist():
        patched.writestr(info,built.read(info.filename) if info.filename in allowed else baseline.read(info.filename))
    patched.writestr(mesh_path,built.read(mesh_path))
mesh=(root/'src/main/resources'/mesh_path).read_bytes()
magic,version,count=struct.unpack_from('>III',mesh)
assert (magic,version)==(0x5348524e,1) and count%3==0 and len(mesh)==12+count*20
model=json.loads((art/'伏魔御厨子_漫画还原.bbmodel').read_text('utf-8'))
offset=12
minimum=[math.inf]*3;maximum=[-math.inf]*3
for element in model['elements']:
    for face in element['faces'].values():
        assert len(face['vertices'])==3 and face['texture']==0
        points=[]
        for key in face['vertices']:
            x,y,z,color,material=struct.unpack_from('>fffIf',mesh,offset);offset+=20
            actual=(x,y,z);expected=[v/16 for v in element['vertices'][key]]
            assert all(math.isfinite(v) for v in actual) and math.isfinite(material)
            assert all(abs(a-b)<1e-5 for a,b in zip(actual,expected))
            assert color>>24==255 and material in (0,1,2)
            for j,v in enumerate(actual):minimum[j]=min(minimum[j],v);maximum[j]=max(maximum[j],v)
            points.append(actual)
        a,b,c=points;u=[b[j]-a[j] for j in range(3)];v=[c[j]-a[j] for j in range(3)]
        area2=math.hypot(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        assert area2>1e-9
assert offset==len(mesh)
with zipfile.ZipFile(jar) as new,zipfile.ZipFile(previous) as old:
    new_names=set(new.namelist());old_names=set(old.namelist())
    changed=sorted(n for n in new_names&old_names if new.read(n)!=old.read(n))
    added=sorted(new_names-old_names);removed=sorted(old_names-new_names)
    assert set(changed)<=allowed, changed
    assert {n for n in added if not n.endswith('/')}=={mesh_path}, added
    assert not removed,removed
    assert new.read(mesh_path)==mesh
    resources=0;json_count=0
    for p in (root/'src/main/resources').rglob('*'):
        if p.is_file():
            relative=p.relative_to(root/'src/main/resources').as_posix()
            if relative in new_names:
                assert new.read(relative)==p.read_bytes(),relative
                resources+=1
    for n in new_names:
        if n.endswith('.json'):json.loads(new.read(n));json_count+=1
report={'artifact':str(jar),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'meshVertices':count,'meshTriangles':count//3,'blockbenchGroups':len(model['outliner']),'blockbenchRuntimePositionsMatch':True,'bounds':{'min':minimum,'max':maximum},'jsonFiles':json_count,'matchingPackagedSourceResources':resources,'changed':changed,'added':added,'removed':removed,'packaging':'Installed 0.62.0 plus only shrine classes, mesh and version metadata. Concurrent unfinished hand-renderer changes excluded.','validation':'Java 21 assemble --offline; static geometry, resource and package verification; six Blockbench MCP views inspected. No game or runtime GLSL compiler launched.'}
(root/'build/release-063-shrine-package.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

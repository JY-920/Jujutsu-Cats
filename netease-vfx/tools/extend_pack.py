"""Format conversions and authored Bedrock resources for the full technique pack."""
import json
import math
import struct
from PIL import Image, ImageDraw


def extend(b):
    BP, RP, A, write = b.BP, b.RP, b.ASSETS, b.write
    labels = {'blue':'术式顺转·苍', 'red':'术式反转·赫', 'purple':'虚式·茈',
        'fuga':'灶·开', 'dismantle':'解', 'cleave':'捌', 'malevolent_shrine':'领域展开·伏魔御厨子',
        'unlimited_void':'领域展开·无量空处', 'cursed_fist':'咒力缠拳', 'infinity':'无下限术式',
        'reverse_technique':'反转术式', 'technique_amplifier':'术式增幅·60秒',
        'information_fragment':'§k信息碎片§r'}
    charged = {'blue','red','purple','fuga','dismantle','cleave','cursed_fist'}
    skill_names = set(labels)-{'technique_amplifier','information_fragment'}
    colors = {'white':(255,255,255,255),'pink':(249,127,210,255),'magenta':(181,45,223,255),
        'purple':(90,31,181,255),'black':(17,14,29,255),'cyan':(20,220,237,255),
        'blue':(44,59,223,255),'light_blue':(81,203,252,255),'red':(230,25,55,255),
        'orange':(255,122,24,255),'yellow':(255,220,94,255),'gray':(67,65,77,255),
        'lime':(103,255,147,255),'green':(29,180,89,255)}
    for name in labels:
        desc = {'identifier':'jujutsuvfx:'+name,'category':'Equipment','register_to_create_menu':name!='information_fragment'}
        components = {'minecraft:max_stack_size':1}
        if name in charged:
            desc['custom_item_type']='ranged_weapon'
            components['minecraft:use_duration']=72000
        write(BP/('netease_items_beh/'+name+'.json'),{'format_version':'1.10','minecraft:item':{
            'description':desc,'components':components}})
        resource_components = {'minecraft:icon':'jujutsuvfx:'+name}
        if name in skill_names:
            resource_components['netease:show_in_hand'] = {'value':False}
        write(RP/('netease_items_res/'+name+'.json'),{'format_version':'1.10','minecraft:item':{
            'description':{'identifier':'jujutsuvfx:'+name},'components':resource_components}})
        path=A/('models/item/'+name+'.json')
        im=Image.new('RGBA',(32,32));draw=ImageDraw.Draw(im)
        if path.exists():
            m=json.loads(path.read_text('utf-8'))
            if not m.get('elements') and 'layer0' in m.get('textures',{}):
                texture=m['textures']['layer0'].split(':')[-1]
                source=A/('textures/'+texture+'.png')
                if source.exists():
                    im=Image.open(source).convert('RGBA').resize((32,32),Image.Resampling.NEAREST)
            for e in m.get('elements',[]):
                face=e.get('faces',{}).get('north',{})
                tex=m.get('textures',{}).get(face.get('texture','').lstrip('#'),'')
                col=next((v for k,v in sorted(colors.items(),key=lambda x:-len(x[0])) if tex.endswith('/'+k+'_concrete')),(150,130,220,255))
                a,z=e['from'],e['to']
                x0,y0,x1,y1=int(a[0]*2),int((16-z[1])*2),int(z[0]*2)-1,int((16-a[1])*2)-1
                if x1>=x0 and y1>=y0: draw.rectangle((x0,y0,x1,y1),fill=col)
        else:
            if name=='infinity':
                draw.ellipse((3,9,17,23),outline='white',width=3);draw.ellipse((15,9,29,23),outline='white',width=3)
            elif name=='reverse_technique':
                draw.rounded_rectangle((3,3,28,28),5,fill=(15,74,35),outline=(89,252,129),width=2)
                draw.rectangle((13,7,18,24),fill='white');draw.rectangle((7,13,24,18),fill='white')
            else:
                draw.polygon([(12,3),(20,3),(20,10),(25,23),(22,29),(9,29),(6,23),(12,10)],fill=(137,45,240),outline='white')
                draw.rectangle((13,1,19,5),fill=(254,207,71))
        if not im.getbbox():
            draw=ImageDraw.Draw(im);draw.polygon([(4,10),(14,2),(28,14),(18,30),(5,24)],fill=(118,50,210))
        dest=RP/('textures/items/'+name+'.png');dest.parent.mkdir(parents=True,exist_ok=True);im.save(dest)
    write(RP/'textures/item_texture.json',{'resource_pack_name':'jujutsuvfx','texture_name':'atlas.items',
        'texture_data':{'jujutsuvfx:'+n:{'textures':'textures/items/'+n} for n in labels}})
    lang='\n'.join('item.jujutsuvfx:'+n+'.name='+v for n,v in labels.items())+'\n'
    lang+='entity.jujutsuvfx:gojo_cat.name=五条猫\nitem.spawn_egg.entity.jujutsuvfx:gojo_cat.name=五条猫 生成蛋\n'
    for language in ('zh_CN','en_US'): (RP/('texts/'+language+'.lang')).write_text(lang,encoding='utf-8')
    names=['blue_form','blue_loop','blue_close','red_air','red_release','red_cannon','red_impact',
        'purple_charge','purple_release','purple_flight','purple_end','furnace_charge','furnace_release','furnace_burst',
        'shrine_finish','shrine_focus_open','void_air_friction']
    names+=['shrine_slash_'+str(i) for i in range(1,6)]
    names+=['shrine_focus_cut_'+str(i) for i in range(1,4)]
    # Source files are copied verbatim; no synthesised substitutes for authored sounds.
    definitions={}
    for name in names:
        source=A/('sounds/'+name+'.ogg')
        if not source.exists():
            # The Java registry's filename differs from its event prefix.
            source=A/('sounds/'+name.replace('shrine_slash_','slash_').replace('shrine_focus_cut_','focus_cut_')+'.ogg')
        if not source.exists(): raise FileNotFoundError(source)
        b.copy_asset(source,RP/('sounds/jujutsuvfx/'+name+'.ogg'))
        definitions['jujutsuvfx.'+name]={'category':'neutral','sounds':[{'name':'sounds/jujutsuvfx/'+name,'stream':False}]}
    write(RP/'sounds/sound_definitions.json',{'format_version':'1.14.0','sound_definitions':definitions})
    hands(b)
    shrine(b)
    materials={}
    for name in ('purple','void','technique','shrine','slash','aura'):
        materials['jujutsuvfx_'+name]={'vertexShader':'shaders/jujutsuvfx_fullscreen.vertex',
            'fragmentShader':'shaders/jujutsuvfx_'+name+'.fragment','vertexFields':[{'field':'Position'},{'field':'UV0'}],
            'samplerStates':[{'samplerIndex':0,'textureFilter':'Bilinear','textureWrap':'Clamp'},
                             {'samplerIndex':1,'textureFilter':'Bilinear','textureWrap':'Clamp'},
                             {'samplerIndex':2,'textureFilter':'Point','textureWrap':'Clamp'}],
            'states':['DisableDepthTest'],'msaaSupport':'Both'}
    write(RP/'materials/postprocess.material',materials)


def hands(b):
    # Native geometry carries the selected skin and the engine's first-person
    # bone palette. The old isolated mesh was accepted but culled in the game.
    b.player_hands()
    return


def shrine(b):
    data=(b.ASSETS/'models/domain/malevolent_shrine.mesh').read_bytes()
    magic,version,count=struct.unpack_from('>III',data)
    assert magic==0x5348524E and version==1 and len(data)==12+20*count
    vertices=[struct.unpack_from('>fffIf',data,12+20*i) for i in range(count)]
    palette=sorted({v[3] for v in vertices});lookup={c:i for i,c in enumerate(palette)}
    size=math.ceil(math.sqrt(len(palette)));im=Image.new('RGBA',(size,size))
    for c,i in lookup.items(): im.putpixel((i%size,i//size),((c>>16)&255,(c>>8)&255,c&255,255))
    im.save(b.RP/'textures/entity/shrine_palette.png')
    materials={}
    for emissive in (False,True):
        suffix='light' if emissive else 'body'
        mesh={'normalized_uvs':True,'positions':[],'normals':[],'uvs':[],'polys':[]}
        indices_by_field={key:{} for key in ('positions','normals','uvs')}
        def intern(field,value):
            key=tuple(round(x,6) for x in value)
            if key not in indices_by_field[field]:
                indices_by_field[field][key]=len(mesh[field]);mesh[field].append(value)
            return indices_by_field[field][key]
        for start in range(0,count,3):
            tri=vertices[start:start+3]
            if (tri[0][4]>.5)!=emissive: continue
            pos=[[-v[0]*16,v[1]*16,v[2]*16] for v in tri]
            ab=[pos[1][i]-pos[0][i] for i in range(3)];ac=[pos[2][i]-pos[0][i] for i in range(3)]
            n=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
            norm=math.sqrt(sum(x*x for x in n)) or 1
            ni=intern('normals',[-x/norm for x in n])
            indices=[]
            for p,v in zip(pos,tri):
                vi=intern('positions',p);ci=lookup[v[3]]
                ui=intern('uvs',[(ci%size+.5)/size,1-(ci//size+.5)/size])
                indices.append([vi,ni,ui])
            # Axis reflection reverses winding.
            mesh['polys'].append([indices[2],indices[1],indices[0],indices[0]])
        b.write(b.RP/('models/entity/shrine_'+suffix+'.geo.json'),{'format_version':'1.12.0','minecraft:geometry':[{
            'description':{'identifier':'geometry.jujutsuvfx.shrine.'+suffix,'texture_width':size,'texture_height':size,
                'visible_bounds_width':40,'visible_bounds_height':24,'visible_bounds_offset':[0,8,0]},
            'bones':[{'name':'shrine','pivot':[0,0,0],'poly_mesh':mesh}]}]})
        materials[suffix]='entity_emissive_alpha' if emissive else 'entity_alphatest'
    b.write(b.BP/'entities/shrine_model.json',{'format_version':'1.16.0','minecraft:entity':{
        'description':{'identifier':'jujutsuvfx:shrine_model','is_spawnable':False,'is_summonable':True,'is_experimental':False},
        'components':{'minecraft:type_family':{'family':['jujutsuvfx_visual']},'minecraft:physics':{'has_gravity':False,'has_collision':False},
            'minecraft:collision_box':{'width':0,'height':0},'minecraft:pushable':{'is_pushable':False,'is_pushable_by_piston':False},
            'minecraft:damage_sensor':{'triggers':[{'deals_damage':False}]},'minecraft:timer':{'looping':False,'time':120,'time_down_event':{'event':'expire','target':'self'}}},
        'component_groups':{'expire':{'minecraft:instant_despawn':{}}},'events':{'expire':{'add':{'component_groups':['expire']}}}}})
    b.write(b.RP/'entity/shrine_model.entity.json',{'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':'jujutsuvfx:shrine_model','materials':materials,
        'textures':{'default':'textures/entity/shrine_palette'},'geometry':{k:'geometry.jujutsuvfx.shrine.'+k for k in materials},
        'render_controllers':['controller.render.jujutsuvfx.shrine_'+k for k in materials]}}})
    b.write(b.RP/'render_controllers/shrine.render_controllers.json',{'format_version':'1.8.0','render_controllers':{
        'controller.render.jujutsuvfx.shrine_'+k:{'geometry':'Geometry.'+k,'materials':[{'*':'Material.'+k}],'textures':['Texture.default']} for k in materials}})
    print('Converted original shrine:',count//3,'triangles,',len(palette),'palette entries')

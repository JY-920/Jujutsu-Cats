import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const stage=path.join(project,'build/release-066-input/java/dev/jujutsuvfx');
const isolated=path.join(project,'build/shinjuku-isolated-067');
const shared=path.join(project,'src/main/java/dev/jujutsuvfx');
const snapshot=JSON.parse(fs.readFileSync(path.join(project,'build/release-066-source-snapshot.json'),'utf8'));
const sha=data=>createHash('sha256').update(data).digest('hex');
const must=(condition,message)=>{if(!condition)throw Error(message);};
const get=(s,re,label)=>{const m=s.match(re);must(m,label);return m[0];};
const insert=(s,anchor,addition,label)=>{must(s.includes(anchor),label);return s.replace(anchor,addition+anchor);};
const records=[];

for(const file of ['client/ClientHooks.java','entity/VfxEntities.java','item/VfxItems.java']){
 const original=fs.readFileSync(path.join(stage,file));
 must(sha(original)===snapshot['java/dev/jujutsuvfx/'+file],'0.66 stage changed: '+file);
 const baseline=original.toString('utf8').replaceAll('\r\n','\n');
 const ours=fs.readFileSync(path.join(shared,file),'utf8').replaceAll('\r\n','\n');
 let output=baseline;
 if(file.endsWith('ClientHooks.java')){
  const rows=ours.split('\n').filter(l=>l.includes('registerEntityRenderer(VfxEntities.')&&l.includes('_SHINJUKU')).join('\n')+'\n';
  must(rows.split('\n').length===4,'Expected three character render registrations');
  const anchor=get(baseline,/^        event\.registerEntityRenderer\(VfxEntities\.SUKUNA_CAT[^\n]+\n/m,'Missing baseline cat renderer');
  output=output.replace(anchor,anchor+rows);
  const reload=get(ours,/    @SubscribeEvent\n    public static void characterResources\(RegisterClientReloadListenersEvent event\) \{[\s\S]*?\n    }\n/,'Missing character reload method');
  output=insert(output,'    @SubscribeEvent\n    public static void setup(',reload,'Missing client setup anchor');
  for(const importName of ['net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent','net.minecraft.server.packs.resources.ResourceManagerReloadListener'])
   if(!output.includes('import '+importName+';'))output=insert(output,'import java.io.IOException;','import '+importName+';\n','Missing import anchor');
 }else if(file.endsWith('VfxEntities.java')){
  const fields=ours.split('\n').filter(l=>l.includes('public static final')&&l.includes('EntityType<ShinjukuActor>')).join('\n')+'\n';
  must(fields.split('\n').length===4,'Expected three character entity fields');
  const method=get(ours,/    private static DeferredHolder<EntityType<\?>, EntityType<ShinjukuActor>> actor\([\s\S]*?\n    }\n/,'Missing entity registration helper');
  output=insert(output,'    private VfxEntities()',fields+'\n'+method+'\n','Missing entity constructor anchor');
  const rows=ours.split('\n').filter(l=>l.includes('event.put(')&&l.includes('_SHINJUKU')).join('\n')+'\n';
  const anchor=get(baseline,/^        event\.put\(SUKUNA_CAT[^\n]+\n/m,'Missing baseline cat attributes');
  output=output.replace(anchor,anchor+rows);
 }else{
  const fields=[...ours.matchAll(/^    public static final DeferredItem<DeferredSpawnEggItem> \w+_SHINJUKU_SPAWN_EGG = ITEMS\.register\([^\n]+\n            \(\) -> new DeferredSpawnEggItem\([^\n]+\n/gm)].map(m=>m[0]).join('');
  must(fields.includes('MAHORAGA_SHINJUKU'),'Missing character spawn eggs');
  output=insert(output,'    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB',fields,'Missing creative tab anchor');
  const rows=ours.split('\n').filter(l=>l.includes('output.accept(')&&l.includes('_SHINJUKU_SPAWN_EGG')).join('\n')+'\n';
  const anchor=get(baseline,/^                output\.accept\(GOJO_CAT_DEBUG_STICK[^\n]+\n/m,'Missing creative content anchor');
  output=output.replace(anchor,anchor+rows);
 }
 for(const [folder,text]of [['baseline-source',baseline],['release-source',output]]){
  const target=path.join(isolated,folder,'dev/jujutsuvfx',file);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,text);
 }
 records.push({file,stageSha256:sha(original),releaseSha256:sha(output),sharedSha256:sha(ours),sharedSourceMatches:sha(output)===sha(ours)});
}
for(const file of ['entity/ShinjukuActor.java','client/ShinjukuActorGeometry.java','client/ShinjukuActorModel.java','client/ShinjukuActorRenderer.java']){
 const target=path.join(isolated,'release-source/dev/jujutsuvfx',file);fs.mkdirSync(path.dirname(target),{recursive:true});
 fs.copyFileSync(path.join(shared,file),target);
}
const report={baseline:'0.66.0 immutable release source snapshot',target:'0.67.0 character overlay',records,foreignStageEdited:false,sharedSourcesEdited:false};
fs.writeFileSync(path.join(isolated,'source-merge-review.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));

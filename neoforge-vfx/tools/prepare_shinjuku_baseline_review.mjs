import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(project,'build/shinjuku-isolated/baseline-source');
const release=path.join(project,'build/shinjuku-isolated/release-source');
const files=['entity/VfxEntities.java','item/VfxItems.java','client/ClientHooks.java'];
const excluded=[];
for(const file of files){
 let source=fs.readFileSync(path.join(project,'src/main/java/dev/jujutsuvfx',file),'utf8');
 if(file.endsWith('ClientHooks.java')){
  source=source.replace(/^        event\.registerShader\(new ShaderInstance\(event\.getResourceProvider\(\),\r?\n                        ResourceLocation\.fromNamespaceAndPath\(JujutsuVfx\.ID, "unlimited_purple_(?:volume|composite)"\),\r?\n                        DefaultVertexFormat\.POSITION_TEX_COLOR\), UnlimitedPurpleEffects::(?:setVolume|setComposite)\);\r?\n/gm,match=>{excluded.push({file,source:match.trim()});return '';});
 }else if(file.endsWith('VfxItems.java')){
  source=source.replace(/^                output\.accept\(dev\.jujutsuvfx\.effect\.VfxEffects\.amplificationPotion\(\)\);\r?\n/gm,match=>{excluded.push({file,source:match.trim()});return '';});
 }
 const releaseTarget=path.join(release,'dev/jujutsuvfx',file);fs.mkdirSync(path.dirname(releaseTarget),{recursive:true});fs.writeFileSync(releaseTarget,source);
 if(file.endsWith('VfxEntities.java')){
  source=source.replace(/^    public static final DeferredHolder<EntityType<\?>, EntityType<ShinjukuActor>>.*\r?\n/gm,'');
  source=source.replace(/\r?\n    private static DeferredHolder<EntityType<\?>, EntityType<ShinjukuActor>> actor\([\s\S]*?\r?\n    }\r?\n/,'\n');
  source=source.replace(/^        event\.put\((?:GOJO|SUKUNA|MAHORAGA)_SHINJUKU\.get\(\), ShinjukuActor\.createAttributes\(\)\.build\(\)\);\r?\n/gm,'');
 }else if(file.endsWith('VfxItems.java')){
  source=source.replace(/^    public static final DeferredItem<DeferredSpawnEggItem> (?:GOJO|SUKUNA|MAHORAGA)_SHINJUKU_SPAWN_EGG = ITEMS\.register\([^\r\n]+\r?\n            \(\) -> new DeferredSpawnEggItem\([^\r\n]+\r?\n/gm,'');
  source=source.replace(/^                output\.accept\((?:GOJO|SUKUNA|MAHORAGA)_SHINJUKU_SPAWN_EGG\.get\(\)\);\r?\n/gm,'');
 }else{
  source=source.replace(/^import net\.neoforged\.neoforge\.client\.event\.RegisterClientReloadListenersEvent;\r?\n/m,'');
  source=source.replace(/^import net\.minecraft\.server\.packs\.resources\.ResourceManagerReloadListener;\r?\n/m,'');
  source=source.replace(/^        event\.registerEntityRenderer\(VfxEntities\.(?:GOJO|SUKUNA|MAHORAGA)_SHINJUKU\.get\(\),[^\r\n]+\r?\n/gm,'');
  source=source.replace(/    @SubscribeEvent\r?\n    public static void characterResources\(RegisterClientReloadListenersEvent event\) \{[\s\S]*?\r?\n    }\r?\n/,'');
 }
 if(/ShinjukuActor|_SHINJUKU/.test(source))throw Error('Could not isolate all character additions in '+file);
 const target=path.join(output,'dev/jujutsuvfx',file);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,source);
}
for(const file of ['entity/ShinjukuActor.java','client/ShinjukuActorGeometry.java','client/ShinjukuActorModel.java','client/ShinjukuActorRenderer.java']){
 const target=path.join(release,'dev/jujutsuvfx',file);fs.mkdirSync(path.dirname(target),{recursive:true});
 fs.copyFileSync(path.join(project,'src/main/java/dev/jujutsuvfx',file),target);
}
fs.writeFileSync(path.join(project,'build/shinjuku-isolated/excluded-concurrent-source.json'),JSON.stringify({excluded,sharedSourcesEdited:false},null,2)+'\n');
console.log('Prepared three baseline-equivalent sources under '+output+'; original sources were not edited.');

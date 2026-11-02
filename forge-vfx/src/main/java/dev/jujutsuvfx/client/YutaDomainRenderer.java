package dev.jujutsuvfx.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** A bounded, seeded sword graveyard. No entities, terrain edits, or screen-covering light pass. */
final class YutaDomainRenderer {
 private static final double RADIUS=dev.jujutsuvfx.core.YutaDomainRules.RADIUS;
 private static final int MAX_DOMAINS=4,SWORDS=144;
 private static final ResourceLocation KATANA=new ResourceLocation("jujutsuvfx","textures/entity/yuta_katana.png");
 private static final LinkedHashMap<Long,Domain> DOMAINS=new LinkedHashMap<>();
 private static final LinkedHashMap<Long,Pulse> LADDERS=new LinkedHashMap<>();
 private static final List<Pulse> CUTS=new ArrayList<>();
 private static final CastAudio AUDIO=new CastAudio();
 private static ShaderInstance shader;
 private static ClientLevel world;
 private static long lastLadderSound=-100;
 private static List<float[][]> blade;
 private static float[] dome,floor;
 private record Pulse(CatCombatCue cue,long born){}
 private static final class Domain {
  final Vec3 center;final int seed;final long born;long deadline;boolean clash;final float[] stone,swords;
  Domain(CatCombatCue cue,long now){center=cue.from();seed=cue.seed();born=now-cue.first();deadline=now+cue.second();clash=cue.from().distanceToSqr(cue.to())>2.25;stone=architecture(seed);swords=swordField(seed);}
 }
 record CameraDomain(Vec3 center,float age,double radius) {}
 /** Read the same live snapshot as the sword field; no server-only domain lookup on the client. */
 static CameraDomain cameraDomain(int actor,float partial){
  ensure();long now=ClientEffectClock.ticks();
  Domain latest=null;
  for(var entry:DOMAINS.entrySet())if((int)(entry.getKey()>>32)==actor&&now<=entry.getValue().deadline
    &&(latest==null||entry.getValue().born>latest.born))latest=entry.getValue();
  return latest==null?null:new CameraDomain(latest.center,(float)(now-latest.born+partial),RADIUS);
 }
 static void setShader(ShaderInstance value){shader=value;blade=null;DOMAINS.clear();}
 static void clear(){DOMAINS.clear();LADDERS.clear();CUTS.clear();AUDIO.stop();world=null;lastLadderSound=-100;}
 private static void ensure(){ClientLevel level=Minecraft.getInstance().level;if(world!=level){clear();world=level;}}
 static void tick(){ensure();long now=ClientEffectClock.ticks();DOMAINS.values().removeIf(d->now>d.deadline+12);LADDERS.values().removeIf(p->now-p.born>24);CUTS.removeIf(p->now-p.born>13);}
 static void receive(CatCombatCue cue){
  ensure();if(world==null||!finite(cue.from())||!finite(cue.to()))return;
  long now=ClientEffectClock.ticks(),key=((long)cue.actor()<<32)^(cue.other()&0xffffffffL);
  if(cue.effect()==CatCombatCue.YUTA_DOMAIN){
   if(cue.first()<0||cue.first()>2800||cue.second()<0||cue.second()>2800)return;
   if(cue.second()==0){Domain d=DOMAINS.get(key);if(d!=null)d.deadline=Math.min(d.deadline,now);return;}
   Domain old=DOMAINS.get(key);if(old!=null){old.deadline=now+cue.second();old.clash=cue.from().distanceToSqr(cue.to())>2.25;return;}
   if(DOMAINS.size()>=MAX_DOMAINS)DOMAINS.remove(DOMAINS.keySet().iterator().next());
   DOMAINS.put(key,new Domain(cue,now));
   // Late tracking snapshots establish the same age without replaying the opening.
   if(cue.first()<5)AUDIO.play(sound("yuta_domain_open"),cue.from(),1.0f,1);
  }else if(cue.effect()==CatCombatCue.YUTA_LADDER){
   LADDERS.put(key,new Pulse(cue,now));
   while(LADDERS.size()>16)LADDERS.remove(LADDERS.keySet().iterator().next());
   if(now-lastLadderSound>=12){lastLadderSound=now;AUDIO.play(sound("yuta_ladder"),cue.from(),.65f,1);}
  }else if(cue.effect()==CatCombatCue.YUTA_SWORD&&cue.first()==3){
   AUDIO.play(sound("yuta_sword"),cue.from(),.65f,1);
   if(CUTS.size()>=24)CUTS.remove(0);CUTS.add(new Pulse(cue,now));
  }else if(cue.effect()==CatCombatCue.YUTA_SWORD&&cue.first()>=0&&cue.first()<=2){
   if(CUTS.size()>=24)CUTS.remove(0);CUTS.add(new Pulse(cue,now));
  }
 }
 private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(new ResourceLocation("jujutsuvfx",name));}
 static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
  ensure();if(shader==null||world==null||(DOMAINS.isEmpty()&&LADDERS.isEmpty()&&CUTS.isEmpty()))return;
  long now=ClientEffectClock.ticks();float partial=event.getPartialTick();
  float time=(now%24000+partial)*.05f;Vec3 camera=event.getCamera().getPosition();
  Domain visible=null;double nearest=Double.MAX_VALUE;
  for(Domain d:DOMAINS.values()){double distance=camera.distanceToSqr(d.center);if(distance<RADIUS*RADIUS&&distance<nearest){visible=d;nearest=distance;}}
  int previousTexture=RenderSystem.getShaderTexture(0);
  try(DomainRenderer.State ignored=new DomainRenderer.State();SceneSnapshot.Target target=new SceneSnapshot.Target()){
   RenderSystem.setShader(()->shader);shader.safeGetUniform("ViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
   shader.safeGetUniform("Time").set(time);RenderSystem.setShaderTexture(0,KATANA);
   RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();RenderSystem.enableBlend();
   RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
   if(visible!=null){
    Domain d=visible;float age=now-d.born+partial;
    float alpha=smooth(age/20)*(1-smooth((now-d.deadline+partial)/12));
    shader.safeGetUniform("Seed").set((d.seed&65535)/65535f);
    shader.safeGetUniform("Clash").set(d.clash?1f:0f);
    shader.safeGetUniform("Reveal").set(smooth(age/32));
    origin(d.center.subtract(camera));shader.safeGetUniform("Opacity").set(alpha);
    if(dome==null){dome=sky();floor=ground();}
    RenderSystem.depthMask(false);draw(dome,0);draw(floor,1);
    // Solid geometry occludes its own back faces and distant swords, with a smooth entry dissolve.
    RenderSystem.depthMask(true);draw(d.stone,2);draw(d.swords,3);RenderSystem.depthMask(false);
   }
   origin(Vec3.ZERO);shader.safeGetUniform("Reveal").set(1f);shader.safeGetUniform("Opacity").set(1f);
   Mesh beams=new Mesh(),seals=new Mesh(),ribbons=new Mesh(),pickups=new Mesh(),mist=new Mesh(),motes=new Mesh();
   if(visible!=null){float fade=smooth((now-visible.born+partial)/25)*(1-smooth((now-visible.deadline+partial)/12));atmosphere(mist,motes,visible.center,camera,visible.seed,time,fade);}
   for(Pulse p:LADDERS.values()){
    float age=now-p.born+partial;if(age<0||age>24||camera.distanceToSqr(p.cue.from())>112*112)continue;
    float fade=(1-smooth((age-20)/4));Vec3 base=p.cue.from().subtract(camera).add(0,.06,0);
    double height=Math.max(12,Math.min(38,p.cue.to().y-p.cue.from().y));
    // A narrow white core and six separated gold shafts keep targets readable.
    for(int i=0;i<4;i++){double a=i*Math.PI/4;Vec3 right=new Vec3(Math.cos(a),0,Math.sin(a));
     beams.card(base.add(0,height*.5,0),right.scale(.4),new Vec3(0,height*.5,0),1,.94f,.75f,fade*.64f);
     beams.card(base.add(0,height*.5,0),right.scale(1.4),new Vec3(0,height*.5,0),1,.67f,.2f,fade*.23f);
    }
    for(int i=0;i<6;i++){double a=i*Math.PI/3+time*.24;Vec3 at=base.add(Math.cos(a)*1.2,0,Math.sin(a)*1.2);
     ribbons.card(at.add(0,height*.5,0),new Vec3(Math.cos(a)*.16,0,Math.sin(a)*.16),new Vec3(0,height*.5,0),1,.82f,.4f,fade*.7f);
    }
    seals.card(base,new Vec3(3.4,0,0),new Vec3(0,0,3.4),1,.79f,.32f,fade*.85f);
    for(int ring=0;ring<3;ring++){
     double y=3+((time*2.5+ring*5)%15);seals.card(base.add(0,y,0),new Vec3(1.45,0,0),new Vec3(0,0,1.45),1,.87f,.55f,fade*.45f);
    }
   }
   for(Pulse p:CUTS){
    float age=now-p.born+partial;if(age<0||age>13||camera.distanceToSqr(p.cue.to())>10000)continue;
    if(p.cue.first()==3){
     if(age>11)continue;
     var caster=world.getEntity(p.cue.actor());
     Vec3 forward=caster==null?new Vec3(0,0,1):caster.getViewVector(partial);
     forward=new Vec3(forward.x,0,forward.z).normalize();if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);
     Vec3 side=new Vec3(forward.z,0,-forward.x),eye=caster==null?p.cue.to():caster.getEyePosition(partial);
     float t=smooth(age/8),fade=1-smooth((age-8)/3);
     Vec3 grip=p.cue.from().add(forward.scale(.95)).add(0,1.5,0).lerp(eye.add(forward.scale(.45)).add(0,-.12,0),t).subtract(camera);
     Vec3 axis=new Vec3(0,-1,0).scale(1-t).add(forward.scale(t)).normalize(),normal=side.cross(axis).normalize();
     float r=p.cue.second()==0?.7f:p.cue.second()==1?.55f:1,g=p.cue.second()==0?.48f:p.cue.second()==1?.85f:.88f,b=p.cue.second()==2?.54f:1;
     if(blade!=null)for(float[][] face:blade)for(float[] v:face)pickups.vertex(grip.add(side.scale(v[0]*1.6)).add(axis.scale((v[1]-.12)*1.6)).add(normal.scale(v[2]*1.6)),v[3],v[4],1,1,1,fade);
     Vec3 tip=grip.add(axis.scale(1.3)),wide=axis.cross(grip.scale(-1)).normalize();
     beams.card(grip.lerp(tip,.5),wide.scale(.065),axis.scale(.65),r,g,b,fade*.7f);
     continue;
    }
    Vec3 a=p.cue.from().subtract(camera),b=p.cue.to().subtract(camera),axis=b.subtract(a);
    Vec3 side=axis.cross(a.scale(-1)).normalize();if(side.lengthSqr()<.1)side=new Vec3(1,0,0);
    float fade=1-smooth(age/13);boolean ice=p.cue.first()==1;
    beams.card(a.lerp(b,.5),side.scale((ice?.5:.13)*fade),axis.scale(.5),ice?.64f:1,ice?.82f:.9f,1,fade*.85f);
    if(ice)seals.card(b,side.scale(2.2),axis.normalize().cross(side).scale(2.2),.72f,.84f,1,fade*.6f);
   }
   RenderSystem.depthMask(true);draw(pickups.array(),7);RenderSystem.depthMask(false);
   draw(beams.array(),4);draw(seals.array(),5);draw(ribbons.array(),6);draw(mist.array(),8);draw(motes.array(),9);
  }finally{RenderSystem.setShaderTexture(0,previousTexture);}
 }
 private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
 private static void origin(Vec3 p){shader.safeGetUniform("Origin").set((float)p.x,(float)p.y,(float)p.z);}
 private static void draw(float[] vertices,int material){
  if(vertices.length==0)return;shader.safeGetUniform("Material").set((float)material);
  BufferBuilder b=dev.jujutsuvfx.platform.RenderBuffers.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
  for(int i=0;i<vertices.length;i+=9)b.vertex(vertices[i],vertices[i+1],vertices[i+2]).uv(vertices[i+3],vertices[i+4]).color(vertices[i+5],vertices[i+6],vertices[i+7],vertices[i+8]).endVertex();
  BufferBuilder.RenderedBuffer mesh=b.end();if(!mesh.isEmpty())BufferUploader.drawWithShader(mesh);else mesh.release();
 }
 private static float[] sky(){Mesh m=new Mesh();for(int row=0;row<20;row++)for(int col=0;col<64;col++){
  double a=col*Math.PI/32,b=(col+1)*Math.PI/32,c=-Math.PI/2+row*Math.PI/20,d=-Math.PI/2+(row+1)*Math.PI/20;
  m.quad(sphere(a,c),sphere(b,c),sphere(b,d),sphere(a,d),1,1,1,1);
 }return m.array();}
 private static Vec3 sphere(double a,double b){return new Vec3(Math.cos(a)*Math.cos(b)*39.7,Math.sin(b)*39.7,Math.sin(a)*Math.cos(b)*39.7);}
 private static float[] ground(){Mesh m=new Mesh();for(int i=0;i<96;i++){
  double a=i*Math.PI/48,b=(i+1)*Math.PI/48;m.quad(new Vec3(0,.035,0),new Vec3(Math.cos(a)*39.4,.035,Math.sin(a)*39.4),new Vec3(Math.cos(b)*39.4,.035,Math.sin(b)*39.4),new Vec3(0,.035,0),1,1,1,1);
 }return m.array();}
 private static float[] architecture(int seed){
  Mesh m=new Mesh();Random random=new Random(seed);
  // An irregular forest of fractured ivory gate frames, rather than a ring of repeated monuments.
  for(int i=0;i<38;i++){
   double angle=i*2.39996323+.18,rad=12+Math.sqrt((i+.5)/38.0)*23;
   Vec3 center=new Vec3(Math.cos(angle)*rad,0,Math.sin(angle)*rad),side=new Vec3(-Math.sin(angle),0,Math.cos(angle));
   double height=7+random.nextDouble()*12,width=.38+random.nextDouble()*.36,gap=1.4+random.nextDouble()*1.9;
   double lean=(random.nextDouble()-.5)*4.4;
   for(int sign:new int[]{-1,1}){
    Vec3 base=center.add(side.scale(sign*gap)).add(0,-.3,0),top=base.add(side.scale(lean+(sign==1?.5:-.5))).add(0,height*(sign==1?.78:1),0);
    fracturedBeam(m,base,top,width,width*.82,seed+i*31+sign);
   }
   double level=height*(.58+random.nextDouble()*.24);
   Vec3 across=center.add(side.scale(lean*level/height)).add(0,level,0);
   fracturedBeam(m,across.subtract(side.scale(gap+1.4)),across.add(side.scale(gap+1.8)).add(0,(random.nextDouble()-.5)*1.5,0),width*.85,width*.8,seed+i*71);
   if(i%3==0){
    Vec3 foot=center.add(side.scale(-gap));
    fracturedBeam(m,foot.add(side.scale(-2.2)).add(0,-.1,0),foot.add(side.scale(2.8)).add(0,height*.88,0),width*.75,width*.8,seed+i*103);
   }
  }
  // Flattened, many-strand red-and-white ceremonial knots and their long suspended tails.
  for(int i=0;i<6;i++){
   double angle=i*Math.PI/3+.15;Vec3 center=new Vec3(Math.cos(angle)*27,18+(i%3)*1.6,Math.sin(angle)*27);
   Vec3 side=new Vec3(-Math.sin(angle),0,Math.cos(angle)),normal=side.cross(new Vec3(0,1,0));
   for(int color=0;color<2;color++){
    Vec3[] loop=new Vec3[81];double tilt=color==0?.72:-.72;
    for(int step=0;step<=80;step++){
     double t=step*Math.PI/40,x=Math.cos(t)*2.7,y=Math.sin(t)*1.12;
     double px=x*Math.cos(tilt)-y*Math.sin(tilt)+(color==0?-.48:.48),py=x*Math.sin(tilt)+y*Math.cos(tilt);
     loop[step]=center.add(side.scale(px)).add(0,py,0).add(normal.scale(Math.sin(t*2)*(color==0?.38:-.38)));
    }
    ribbon(m,loop,normal,color==0?.72f:.98f,color==0?.035f:.94f,color==0?.09f:.88f);
    Vec3[] tail=new Vec3[37];
    for(int j=0;j<=36;j++){
     double t=j/36.0,theta=angle+t*Math.PI/3;
     tail[j]=new Vec3(Math.cos(theta)*(27+color*.75),center.y-3.8*Math.sin(t*Math.PI)+(color==0?.25:-.55),Math.sin(theta)*(27+color*.75));
    }
    ribbon(m,tail,new Vec3(0,1,0),color==0?.72f:.98f,color==0?.035f:.94f,color==0?.09f:.88f);
    Vec3[] end=new Vec3[25];
    for(int j=0;j<=24;j++){
     double t=j/24.0;end[j]=center.add(side.scale((color==0?-1:1)*(1.0+t*2.3))).add(0,t*7.5,0).add(normal.scale(Math.sin(t*2)*2));
    }
    ribbon(m,end,normal,color==0?.72f:.98f,color==0?.035f:.94f,color==0?.09f:.88f);
   }
  }return m.array();
 }
 private static void fracturedBeam(Mesh m,Vec3 a,Vec3 b,double width,double depth,int seed){
  Vec3 axis=b.subtract(a).normalize(),u=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=axis.cross(u);
  double[][] shape={{1,.68},{.68,1},{-.68,1},{-1,.68},{-1,-.68},{-.68,-1},{.68,-1},{1,-.68}};
  Vec3[][] rings=new Vec3[5][8];Random r=new Random(seed);
  for(int h=0;h<5;h++)for(int i=0;i<8;i++){
   double uneven=1+(r.nextDouble()-.5)*.19,end=h==4?(r.nextDouble()-.5)*.65:0;
   rings[h][i]=a.lerp(b,h/4.0).add(axis.scale(end)).add(u.scale(shape[i][0]*width*uneven)).add(v.scale(shape[i][1]*depth*uneven));
  }
  for(int h=0;h<4;h++)for(int i=0;i<8;i++)m.quad(rings[h][i],rings[h][(i+1)%8],rings[h+1][(i+1)%8],rings[h+1][i],.91f,.86f,.94f,1);
  for(int i=1;i<7;i++)m.quad(rings[4][0],rings[4][i],rings[4][i+1],rings[4][0],.94f,.9f,.96f,1);
 }
 private static void ribbon(Mesh m,Vec3[] path,Vec3 normal,float r,float g,float b){
  // Weld both ends of every strip so distant bands remain continuous instead of turning into a dotted lattice.
  Vec3[] sides=new Vec3[path.length];
  for(int i=0;i<path.length;i++)sides[i]=normal.cross(path[Math.min(path.length-1,i+1)].subtract(path[Math.max(0,i-1)])).normalize();
  for(int i=0;i<path.length-1;i++)for(int strand=0;strand<7;strand++){
   double left=(strand-3)*.16-.084,right=(strand-3)*.16+.084;
   m.quad(path[i].add(sides[i].scale(left)),path[i].add(sides[i].scale(right)),path[i+1].add(sides[i+1].scale(right)),path[i+1].add(sides[i+1].scale(left)),r,g,b,1);
  }
 }
 private static void atmosphere(Mesh mist,Mesh motes,Vec3 center,Vec3 camera,int seed,float time,float alpha){
  Random random=new Random(seed^0x5e817);
  for(int i=0;i<44;i++){
   double radius=4+Math.sqrt(random.nextDouble())*33,angle=random.nextDouble()*Math.PI*2;
   Vec3 c=center.add(Math.cos(angle)*radius,.7+random.nextDouble()*1.1,Math.sin(angle)*radius).subtract(camera);
   Vec3 right=new Vec3(-c.z,0,c.x).normalize();
   mist.card(c,right.scale(4.5+random.nextDouble()*3.0),new Vec3(0,.9+random.nextDouble()*.65,0),.96f,.69f,1,alpha*.4f);
  }
  for(int i=0;i<76;i++){
   double radius=5+Math.sqrt(random.nextDouble())*31,angle=random.nextDouble()*Math.PI*2,y=.4+Math.pow(random.nextDouble(),1.8)*23;
   Vec3 c=center.add(Math.cos(angle)*radius,y+Math.sin(time*.35+i)*.28,Math.sin(angle)*radius).subtract(camera);
   Vec3 right=c.cross(new Vec3(0,1,0)).normalize(),up=right.cross(c).normalize();double size=.12+random.nextDouble()*.19;
   motes.card(c,right.scale(size*2.4),up.scale(size*2.4),1,.48f,.93f,alpha*(.55f+(float)Math.sin(time*.8+i)*.2f));
  }
 }
 private static float[] swordField(int seed){
  if(blade==null){List<float[][]> faces=new ArrayList<>();
   try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation("jujutsuvfx","meshes/yuta_katana_field.json"))){
    for(var face:JsonParser.parseReader(reader).getAsJsonArray()){
     if(faces.size()>=256)break;float[][] v=new float[4][5];for(int i=0;i<4;i++)for(int j=0;j<5;j++)v[i][j]=face.getAsJsonArray().get(i).getAsJsonArray().get(j).getAsFloat();faces.add(v);
    }
   }catch(Exception ignored){/* The procedural silhouette below remains usable if a resource pack removes the source mesh. */}
   blade=List.copyOf(faces);
  }
  Random random=new Random(seed^0x7a3b5e11);Mesh m=new Mesh();
  for(int i=0;i<SWORDS;i++){
   double radius=3+Math.sqrt((i+.4)/SWORDS)*33,angle=i*2.39996323+random.nextDouble()*.3;
   Vec3 base=new Vec3(Math.cos(angle)*radius,-.12,Math.sin(angle)*radius);
   double yaw=random.nextDouble()*Math.PI*2,lean=(random.nextDouble()-.5)*.62,scale=i<24?2.9+random.nextDouble()*1.3:1.55+random.nextDouble()*1.25;
   if(blade.isEmpty()){Vec3 tip=base.add(Math.sin(lean)*scale,Math.cos(lean)*scale,0);beam(m,base,tip,.055,.025,.78f,.81f,.83f);beam(m,tip.add(-.23,-.45,0),tip.add(.23,-.45,0),.04,.06,.76f,.59f,.3f);continue;}
   for(float[][] face:blade)for(float[] v:face){
    // The supplied mesh points blade-up; invert it so the tip enters the ground and the wrapped hilt remains above it.
    double x=v[0]*scale,y=(.94-v[1])*scale,z=v[2]*scale;
    double lx=x*Math.cos(lean)+y*Math.sin(lean),ly=y*Math.cos(lean)-x*Math.sin(lean);
    m.vertex(base.add(lx*Math.cos(yaw)-z*Math.sin(yaw),ly,lx*Math.sin(yaw)+z*Math.cos(yaw)),v[3],v[4],1,1,1,1);
   }
  }return m.array();
 }
 private static void beam(Mesh m,Vec3 a,Vec3 b,double width,double depth,float r,float g,float blue){
  Vec3 dir=b.subtract(a).normalize(),u=dir.cross(Math.abs(dir.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width),v=dir.cross(u).normalize().scale(depth);
  Vec3[] p={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v)};
  Vec3[] q={b.add(u).add(v),b.subtract(u).add(v),b.subtract(u).subtract(v),b.add(u).subtract(v)};
  for(int i=0;i<4;i++)m.quad(p[i],p[(i+1)%4],q[(i+1)%4],q[i],r,g,blue,1);
  m.quad(p[3],p[2],p[1],p[0],r,g,blue,1);m.quad(q[0],q[1],q[2],q[3],r,g,blue,1);
 }
 private static void tube(Mesh m,Vec3 a,Vec3 b,double radius,int sides,float r,float g,float blue){
  Vec3 d=b.subtract(a).normalize(),u=d.cross(Math.abs(d.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=d.cross(u);
  for(int i=0;i<sides;i++){double x=i*Math.PI*2/sides,y=(i+1)*Math.PI*2/sides;Vec3 p=u.scale(Math.cos(x)*radius).add(v.scale(Math.sin(x)*radius)),q=u.scale(Math.cos(y)*radius).add(v.scale(Math.sin(y)*radius));m.quad(a.add(p),a.add(q),b.add(q),b.add(p),r,g,blue,1);}
 }
 private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
 private static final class Mesh {
  private float[] data=new float[4096];private int size;
  void vertex(Vec3 p,float u,float v,float r,float g,float b,float a){if(size+9>data.length)data=Arrays.copyOf(data,data.length*2);data[size++]=(float)p.x;data[size++]=(float)p.y;data[size++]=(float)p.z;data[size++]=u;data[size++]=v;data[size++]=r;data[size++]=g;data[size++]=b;data[size++]=a;}
  void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha){vertex(a,0,0,r,g,blue,alpha);vertex(b,1,0,r,g,blue,alpha);vertex(c,1,1,r,g,blue,alpha);vertex(d,0,1,r,g,blue,alpha);}
  void card(Vec3 c,Vec3 r,Vec3 u,float red,float g,float b,float a){quad(c.subtract(r).subtract(u),c.add(r).subtract(u),c.add(r).add(u),c.subtract(r).add(u),red,g,b,a);}
  float[] array(){return Arrays.copyOf(data,size);}
 }
}

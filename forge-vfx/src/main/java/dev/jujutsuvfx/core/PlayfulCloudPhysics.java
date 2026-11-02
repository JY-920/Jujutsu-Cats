package dev.jujutsuvfx.core;
import java.util.*;

/** Two free rigid batons and two flexible sockets driven by the real hand transform.
 * World-space Verlet integration keeps inertia when the hand or camera changes direction.
 * Pure visual rig: authoritative combat timing remains on the server. */
public final class PlayfulCloudPhysics {
 public record Vec(double x,double y,double z){
  public Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}
  public Vec sub(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
  public Vec mul(double a){return new Vec(x*a,y*a,z*a);}
  public double dot(Vec b){return x*b.x+y*b.y+z*b.z;}
  public Vec cross(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
  public double length(){return Math.sqrt(dot(this));}
  public Vec unit(){double n=length();return n>1e-9?mul(1/n):new Vec(0,-1,0);}
  public Vec mix(Vec b,double t){return mul(1-t).add(b.mul(t));}
 }
 public record Frame(Vec origin,Vec x,Vec y,Vec z){
  public Vec world(Vec local){return origin.add(x.mul(local.x)).add(y.mul(local.y)).add(z.mul(local.z));}
  public Vec local(Vec world){return localDirection(world.sub(origin));}
  public Vec localDirection(Vec d){
   double det=x.dot(y.cross(z));
   if(Math.abs(det)<1e-12)return new Vec(0,0,0);
   return new Vec(d.dot(y.cross(z))/det,d.dot(z.cross(x))/det,d.dot(x.cross(y))/det);
  }
  /** Cancel the render pass's camera/GUI root before introducing a double-precision world origin. */
  public static Frame relative(Frame root,Frame hand,Vec worldRoot){
   return new Frame(worldRoot.add(root.local(hand.origin)),root.localDirection(hand.x),
       root.localDirection(hand.y),root.localDirection(hand.z));
  }
  Frame mix(Frame b,double t){
   // Quaternion interpolation preserves a full basis even across a 180-degree wrist reversal.
   // Raw axis interpolation shortens rods and can collapse two axes to zero at the midpoint.
   Rotation q=Rotation.of(this).mix(Rotation.of(b),t);
   double handed=x.cross(y).dot(z)<0?-1:1;
   return new Frame(origin.mix(b.origin,t),q.apply(new Vec(1,0,0)).mul(x.length()+(b.x.length()-x.length())*t),
       q.apply(new Vec(0,1,0)).mul(y.length()+(b.y.length()-y.length())*t),
       q.apply(new Vec(0,0,1)).mul(handed*(z.length()+(b.z.length()-z.length())*t)));
  }
  private record Rotation(double x,double y,double z,double w){
   static Rotation of(Frame f){
    Vec a=f.x.unit(),b=f.y.unit(),c=a.cross(b).unit();double trace=a.x+b.y+c.z;
    if(trace>0){double s=2*Math.sqrt(trace+1);return new Rotation((b.z-c.y)/s,(c.x-a.z)/s,(a.y-b.x)/s,s*.25);}
    if(a.x>b.y&&a.x>c.z){double s=2*Math.sqrt(1+a.x-b.y-c.z);return new Rotation(s*.25,(b.x+a.y)/s,(c.x+a.z)/s,(b.z-c.y)/s);}
    if(b.y>c.z){double s=2*Math.sqrt(1+b.y-a.x-c.z);return new Rotation((b.x+a.y)/s,s*.25,(c.y+b.z)/s,(c.x-a.z)/s);}
    double s=2*Math.sqrt(1+c.z-a.x-b.y);return new Rotation((c.x+a.z)/s,(c.y+b.z)/s,s*.25,(a.y-b.x)/s);
   }
   Rotation mix(Rotation b,double t){
    double sign=x*b.x+y*b.y+z*b.z+w*b.w<0?-1:1;
    double xx=x*(1-t)+b.x*t*sign,yy=y*(1-t)+b.y*t*sign,zz=z*(1-t)+b.z*t*sign,ww=w*(1-t)+b.w*t*sign;
    double n=Math.sqrt(xx*xx+yy*yy+zz*zz+ww*ww);return new Rotation(xx/n,yy/n,zz/n,ww/n);
   }
   Vec apply(Vec v){Vec u=new Vec(x,y,z),cross=u.cross(v);return v.add(cross.mul(2*w)).add(u.cross(cross).mul(2));}
  }
 }
 public record Trace(Vec near,Vec tip){}
 private static final double STEP=1.0/120;
 private final Vec[] p=new Vec[5],old=new Vec[5];
 private final ArrayDeque<Trace> trail=new ArrayDeque<>();
 private double stamp=Double.NaN,simTime;
 private Frame last,stepFrame;
 private double lastDrive;
 private double ground=Double.NEGATIVE_INFINITY;
 /** Optional ground plane for mouth-held NPC rigs; player weapons retain their existing solver. */
 public void ground(double y){ground=Double.isFinite(y)?y:Double.NEGATIVE_INFINITY;}
 private void groundContacts(){if(!Double.isFinite(ground))return;for(int i=1;i<5;i++)if(p[i].y<ground){p[i]=new Vec(p[i].x,ground,p[i].z);old[i]=new Vec(old[i].x,Math.max(old[i].y,ground),old[i].z);}}

 public Vec point(int i){return p[i];}
 public List<Trace> history(){return List.copyOf(trail);}
 public void update(double time,Frame frame){
  update(time,frame,0);
 }
 public void update(double time,Frame frame,double drive){
  drive=Double.isFinite(drive)?Math.max(0,Math.min(1.5,drive)):0;
  Vec anchor=frame.world(new Vec(0,PlayfulCloudMotion.ROD_LENGTH-PlayfulCloudMotion.GRIP,0));
  if(!Double.isFinite(time)||!Double.isFinite(anchor.length()))return;
  if(last!=null&&time<=stamp+1e-7&&time>stamp-1)return;
  if(last==null||time<stamp||time-stamp>.25||anchor.sub(p[0]).length()>4){reset(time,frame);lastDrive=drive;return;}
  if(time<=stamp+1e-7)return; // shader/shadow/mirror passes must never tick the rig twice.
  double previous=stamp;
  while(simTime+STEP<=time+1e-8){
   simTime+=STEP;double t=Math.max(0,Math.min(1,(simTime-previous)/(time-previous)));Frame f=last.mix(frame,t);
   step(f,lastDrive+(drive-lastDrive)*t);trail.addLast(new Trace(p[3].mix(p[4],.62),p[4]));while(trail.size()>18)trail.removeFirst();
  }
  last=frame;stamp=time;lastDrive=drive;
 }
 private void reset(double time,Frame f){
  p[0]=f.world(new Vec(0,PlayfulCloudMotion.ROD_LENGTH-PlayfulCloudMotion.GRIP,0));
  // Start just outside the held rod, hanging instead of springing from a rigid folded pose.
  Vec down=new Vec(.18,-1,.06).unit();
  for(int i=1;i<5;i++)p[i]=p[i-1].add(down.mul(length(i)*f.y.length()));
  System.arraycopy(p,0,old,0,5);projectGround(p[0],f.y.length());System.arraycopy(p,0,old,0,5);trail.clear();last=stepFrame=f;stamp=simTime=time;
 }
 private static double length(int i){return i%2==1?PlayfulCloudMotion.CHAIN_LENGTH:PlayfulCloudMotion.ROD_LENGTH;}
 private static Vec limited(Vec v,double max){double n=v.length();return n>max?v.mul(max/n):v;}
 private void step(Frame f,double drive){
  Vec anchor=f.world(new Vec(0,PlayfulCloudMotion.ROD_LENGTH-PlayfulCloudMotion.GRIP,0));
  double scale=f.y.length();
  Vec anchorVelocity=anchor.sub(stepFrame.world(new Vec(0,PlayfulCloudMotion.ROD_LENGTH-PlayfulCloudMotion.GRIP,0))).mul(1/STEP);
  Vec angularVelocity=stepFrame.x.unit().cross(f.x.unit()).add(stepFrame.y.unit().cross(f.y.unit()))
      .add(stepFrame.z.unit().cross(f.z.unit())).mul(.5/STEP);
  stepFrame=f;
  double drag=Math.exp(-(4-3.1*Math.min(1,drive))*STEP),transfer=1-Math.exp(-6*drive*STEP);
  p[0]=anchor;
  double reach=0;
  for(int i=1;i<5;i++){
   reach+=length(i)*scale;
   Vec velocity=p[i].sub(old[i]).mul(drag);
   // Transfer the wrist's actual tangential momentum only during the strike. Free rods still solve
   // as rigid bodies, then coast instead of being interpolated back to a prescribed folded pose.
   if(transfer>0){
    Vec tangent=anchorVelocity.add(angularVelocity.cross(p[i].sub(anchor)));
    velocity=velocity.add(limited(tangent,30*scale).mul(transfer*STEP));
   }
   // A cast opens the chain along the held rod instead of merely shaking a hanging pendulum.
   // This is acceleration, not a snapped pose; the rods keep their momentum after the cast.
   Vec castTarget=anchor.add(f.y.unit().mul(reach));
   velocity=velocity.add(limited(castTarget.sub(p[i]),2.4*scale).mul(140*drive*STEP*STEP));
   velocity=limited(velocity,.26*scale);
   old[i]=p[i];p[i]=p[i].add(velocity).add(new Vec(0,-14*STEP*STEP*scale,0));
  }
  for(int pass=0;pass<24;pass++){
   p[0]=anchor;
   for(int i=1;i<5;i++){
    Vec d=p[i].sub(p[i-1]);double n=d.length();if(n<1e-9)d=new Vec(.001,-.001,0);
    Vec correction=d.unit().mul(d.length()-length(i)*scale);
    if(i==1)p[i]=p[i].sub(correction);
    else{p[i-1]=p[i-1].add(correction.mul(.5));p[i]=p[i].sub(correction.mul(.5));}
   }
   groundContacts();
   Vec gripEnd=anchor.sub(f.y.mul(PlayfulCloudMotion.ROD_LENGTH));
   collide(gripEnd,anchor,-1,-1,p[1],p[2],1,2,.115*scale,true);
   collide(gripEnd,anchor,-1,-1,p[3],p[4],3,4,.115*scale,false);
   collide(p[1],p[2],1,2,p[3],p[4],3,4,.115*scale,true);
  }
  // Finish outward so both sockets and both solid rods retain exact length even during a sharp flick.
  for(int i=1;i<5;i++)p[i]=p[i-1].add(p[i].sub(p[i-1]).unit().mul(length(i)*scale));
  projectGround(anchor,scale);
 }
 private void projectGround(Vec anchor,double scale){
  if(Double.isFinite(ground))for(int pass=0;pass<12;pass++){
   groundContacts();p[0]=anchor;
   for(int i=1;i<5;i++){
    Vec d=p[i].sub(p[i-1]).unit();double len=length(i)*scale;
    Vec q=p[i-1].add(d.mul(len));
    if(q.y<ground){double dy=Math.max(-len,Math.min(len,ground-p[i-1].y));Vec h=new Vec(d.x,0,d.z);if(h.length()<1e-6)h=new Vec(1,0,0);q=p[i-1].add(h.unit().mul(Math.sqrt(Math.max(0,len*len-dy*dy)))).add(new Vec(0,dy,0));}
    p[i]=q;
   }
  }
 }
 private static double clamp(double v){return Math.max(0,Math.min(1,v));}
 /** Capsule contact keeps solid batons from passing through one another at a fast reversal. */
 private void collide(Vec a,Vec b,int ai,int bi,Vec c,Vec d,int ci,int di,double radius,boolean adjacent){
  Vec u=b.sub(a),v=d.sub(c),w=a.sub(c);double aa=u.dot(u),bb=u.dot(v),cc=v.dot(v),dd=u.dot(w),ee=v.dot(w),den=aa*cc-bb*bb;
  double s=den>1e-10?clamp((bb*ee-cc*dd)/den):0,t=(bb*s+ee)/Math.max(1e-10,cc);
  if(t<0){t=0;s=clamp(-dd/aa);}else if(t>1){t=1;s=clamp((bb-dd)/aa);}
  if(adjacent&&s>.88&&t<.12)return;
  Vec delta=c.add(v.mul(t)).sub(a.add(u.mul(s)));double distance=delta.length();if(distance>=radius)return;
  Vec normal=distance>1e-6?delta.mul(1/distance):new Vec(0,0,1);
  double wa=ai<0?0:1-s,wb=bi<0?0:s,wc=1-t,wd=t,weight=wa*wa+wb*wb+wc*wc+wd*wd;
  Vec correction=normal.mul((radius-distance)/Math.max(.01,weight));
  if(ai>=0)p[ai]=p[ai].sub(correction.mul(wa));if(bi>=0)p[bi]=p[bi].sub(correction.mul(wb));
  p[ci]=p[ci].add(correction.mul(wc));p[di]=p[di].add(correction.mul(wd));
 }
}

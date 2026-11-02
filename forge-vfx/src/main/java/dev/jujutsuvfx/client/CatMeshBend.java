package dev.jujutsuvfx.client;
import org.joml.Vector3f;
/** Continuous arc skinning; optional proportional pose adjustment and straight forearm after the elbow. */
record CatMeshBend(float pivotY,float pivotZ,float curvature,boolean above,float lengthScale,float arcLength) {
 CatMeshBend(float y,float z,float k,boolean above){this(y,z,k,above,1,Float.POSITIVE_INFINITY);}
 private float q(float y){return (y-pivotY)*lengthScale;}
 private float arc(float q){return (above?q>0:q<0)?0:Math.copySign(Math.min(Math.abs(q),arcLength),q);}
 float angle(float y){return curvature*arc(q(y));}
 Vector3f position(Vector3f v){
  float q=q(v.y),a=curvature*arc(q),z=v.z-pivotZ;
  if(Math.abs(a)<1e-7f)return v.set(v.x,pivotY+q,v.z);
  float s=(float)Math.sin(a),c=(float)Math.cos(a),tail=q-arc(q);
  return v.set(v.x,pivotY+s/curvature-z*s+tail*c,pivotZ+(1-c)/curvature+z*c+tail*s);
 }
 Vector3f normal(Vector3f n,float y,float z){
  float q=q(y),a=angle(y),s=(float)Math.sin(a),c=(float)Math.cos(a);
  boolean curved=Math.abs(q)>0&&Math.abs(q)<arcLength&&(above?q<0:q>0);
  float ny=n.y/(lengthScale*(curved?1-curvature*(z-pivotZ):1));
  return n.set(n.x,ny*c-n.z*s,ny*s+n.z*c).normalize();
 }
}

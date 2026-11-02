package dev.jujutsuvfx.core;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
/** Native arm coordinates: fixed shoulder anchor, +Y reaches the fist. */
public final class WorldCutHandPose {
 private WorldCutHandPose(){}
 private static final float[][] KEYS={
  {0,.15f,.15f,-1,0}, {.16f,-.82f,.45f,-.75f,-.18f},
  {.24f,-.90f,.40f,-.67f,-.25f}, {.31f,.48f,.34f,-1,.10f},
  {.40f,.90f,.24f,-.78f,.26f}, {.58f,.50f,.15f,-.90f,.15f}, {1,.15f,.15f,-1,0}
 };
 public static Matrix4f matrix(float age,float side){
  age=Math.max(0,Math.min(1,age));int i=1;while(i<KEYS.length-1&&age>KEYS[i][0])i++;
  float[] a=KEYS[i-1],b=KEYS[i];float t=Math.max(0,Math.min(1,(age-a[0])/(b[0]-a[0])));t=t*t*(3-2*t);
  Vector3f direction=new Vector3f(side*mix(a[1],b[1],t),mix(a[2],b[2],t),mix(a[3],b[3],t)).normalize();
  Quaternionf rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),direction);
  // Anatomical skin orientation is retained; only the pivot and fist trajectory change.
  return new Matrix4f().translation(side*.30f,-.42f,-.35f).rotate(rotation)
   .rotateY((float)Math.PI+side*mix(a[4],b[4],t)).translate(side*.375f,-.125f,0);
 }
 private static float mix(float a,float b,float t){return a+(b-a)*t;}
}

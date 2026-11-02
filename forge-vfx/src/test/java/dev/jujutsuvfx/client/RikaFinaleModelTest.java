package dev.jujutsuvfx.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;

/** Loads each actual BBModel clip through the production geometry importer. */
public final class RikaFinaleModelTest {
 public static void main(String[] args)throws Exception{
  JsonArray frames=new JsonArray();float[] previousEnd=null;int count=0;
  String[] names={"vow","charge","clash","breakthrough","recovery"};int[] lengths={70,110,90,40,65};
  for(int n=0;n<names.length;n++){
   var mesh=new SorcererCatGeometry(YutaGetoPoseModelTest.resources(),"rika_finale","animation.rika."+names[n]);
   float[] previous=null;
   for(int tick=0;tick<=lengths[n];tick++){
    var capture=new CutMeshCapture();var p=new PoseStack();p.scale(3.1f,3.1f,3.1f);p.translate(0,-1.5,0);
    mesh.render(p,capture.record(null,true),15728880,0,-1,0,null,tick/(float)lengths[n]);capture.finish();
    if(capture.faces.size()<306)throw new AssertionError("Missing Rika feline geometry");
    float[] points=new float[capture.faces.size()*12];int index=0;
    double floor=Double.NEGATIVE_INFINITY;JsonArray faces=new JsonArray();
    for(var f:capture.faces){JsonArray vertices=new JsonArray();
     for(var v:f.points()){
      floor=Math.max(floor,v.y());
      if(!Double.isFinite(v.x()+v.y()+v.z())||Math.max(Math.abs(v.x()),Math.max(Math.abs(v.y()),Math.abs(v.z())))>8)
       throw new AssertionError("Unbounded finale mesh "+names[n]+" tick "+tick);
      if(v.u()<0||v.u()>1||v.v()<0||v.v()>1)throw new AssertionError("Rika finale atlas UV escaped");
      points[index++]=(float)v.x();points[index++]=(float)v.y();points[index++]=(float)v.z();
      JsonArray point=new JsonArray();for(double value:new double[]{v.x(),v.y(),v.z(),v.u(),v.v()})point.add(value);vertices.add(point);
     }faces.add(vertices);
    }
    if(Math.abs(floor)>.018)throw new AssertionError("Rika support detached from ground "+names[n]+" tick "+tick+" floor="+floor);
    if(tick==0&&previousEnd!=null&&maxDelta(previousEnd,points)>.002)throw new AssertionError("Rika clip seam before "+names[n]);
    if(previous!=null&&maxDelta(previous,points)>.15)throw new AssertionError("Rika one-tick pop "+names[n]+" tick "+tick);
    previous=points;count++;
    if(tick==0||tick==12||tick==lengths[n]/2||tick==lengths[n]){
     JsonObject frame=new JsonObject();frame.addProperty("clip",names[n]);frame.addProperty("age",tick);frame.add("faces",faces);frames.add(frame);
    }
   }
   previousEnd=previous;
  }
  Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/rika-finale.json"),new Gson().toJson(frames));
  System.out.println("Rika finale: "+count+" production geometry frames, grounded tail, five continuous clips, complete UVs");
 }
 private static double maxDelta(float[] a,float[] b){
  if(a.length!=b.length)throw new AssertionError("Rika topology changes between clips");double max=0;
  for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;
 }
}

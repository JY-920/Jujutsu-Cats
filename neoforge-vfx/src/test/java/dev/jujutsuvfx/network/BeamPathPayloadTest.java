package dev.jujutsuvfx.network;

import dev.jujutsuvfx.core.BeamPath;
import dev.jujutsuvfx.core.SustainedBeam;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class BeamPathPayloadTest {
 public static void main(String[] args){
  var steering=new BeamPath.Steering();var origin=new SustainedBeam.P(12345678.25,72.5,-23456789.75);
  steering.update(origin,new SustainedBeam.P(0,0,1),false);
  var points=steering.update(origin,new SustainedBeam.P(1,0,0),true).stream().map(p->new Vec3(p.x(),p.y(),p.z())).toList();
  UUID owner=UUID.randomUUID(),session=UUID.randomUUID();
  var state=new UzumakiState(43,owner,100,140,true,points.get(0),new Vec3(0,0,1),48,1,0,-1,Vec3.ZERO,Vec3.ZERO,points,java.util.List.of());
  var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{
   UzumakiState.CODEC.encode(b,state);int bytes=b.writerIndex();
   var decoded=UzumakiState.CODEC.decode(b);
   if(!decoded.owner().equals(owner)||decoded.session()!=100||decoded.kind()!=1||decoded.path().size()!=9)throw new AssertionError("Beam identity/path not preserved");
   for(int i=0;i<points.size();i++)if(points.get(i).distanceTo(decoded.path().get(i))>1e-5)throw new AssertionError("Relative beam coordinates corrupted at large world coordinates");
   int countIndex=bytes-2-9*12;
   for(int bad:new int[]{0,10}){
    b.setByte(countIndex,bad);b.readerIndex(0);boolean rejected=false;
    try{UzumakiState.CODEC.decode(b);}catch(IllegalArgumentException expected){rejected=true;}
    if(!rejected)throw new AssertionError("Unbounded beam point count accepted");
   }
   b.clear();var other=points.stream().map(p->p.add(-10,0,0)).toList();
   var clash=new UzumakiState(43,owner,100,140,true,points.get(0),new Vec3(0,0,1),48,1,1000,5,other.get(0),points.get(8),points,other);
   UzumakiState.CODEC.encode(b,clash);var clashCopy=UzumakiState.CODEC.decode(b);
   if(clashCopy.otherPath().size()!=9||clashCopy.clashId()!=1000)throw new AssertionError("Collision lost the opposing curve");
   for(int i=0;i<other.size();i++)if(other.get(i).distanceTo(clashCopy.otherPath().get(i))>1e-5)throw new AssertionError("Clash bends were not preserved");
   b.clear();var edit=new GetoCatEdit(session,GetoCatEdit.POSE,5);GetoCatEdit.CODEC.encode(b,edit);
   if(!GetoCatEdit.CODEC.decode(b).equals(edit))throw new AssertionError("Geto edit token or pose lost");
   b.clear();var view=new GetoCatView(43,owner,session,true,5,0);GetoCatView.CODEC.encode(b,view);
   if(!GetoCatView.CODEC.decode(b).equals(view))throw new AssertionError("Geto preview state lost");
  }finally{b.release();}
  System.out.println("Curved beam and Geto preview packets: round trips, coordinate precision and bounded allocations passed");
 }
}

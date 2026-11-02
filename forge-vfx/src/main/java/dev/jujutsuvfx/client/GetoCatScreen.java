package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.GetoDebugPose;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.GetoCatEdit;
import dev.jujutsuvfx.network.GetoCatView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.jujutsuvfx.platform.PacketDistributor;

/** Pose-only previews; closing the screen keeps the selected pose until Auto is selected. */
public final class GetoCatScreen extends Screen {
 private GetoCatView view;
 private final Button[] poses=new Button[GetoDebugPose.COUNT];
 private Button dance;
 private int left,top,panelWidth,panelHeight,ticks;
 private float scale;
 private GetoCatScreen(GetoCatView view){super(text("title"));this.view=view;}
 private static Component text(String key){return Component.translatable("gui.jujutsuvfx.geto_cat."+key);}
 public static void receive(GetoCatView packet){
  Minecraft mc=Minecraft.getInstance();
  if(mc.level==null||!(mc.level.getEntity(packet.entityId()) instanceof GetoCat cat)||!cat.getUUID().equals(packet.entityUuid()))return;
  if(packet.open())mc.setScreen(new GetoCatScreen(packet));
  else if(mc.screen instanceof GetoCatScreen screen&&screen.view.session().equals(packet.session())){screen.view=packet;screen.refresh();}
 }
 private int y(int local){return top+Math.round(local*scale);}
 @Override protected void init(){
  panelWidth=Math.min(350,width-16);panelHeight=Math.min(218,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;scale=panelHeight/218f;
  int h=Math.max(14,Math.round(20*scale)),w=(panelWidth-28)/2;
  for(int i=0;i<poses.length;i++){
   final int pose=i;
   poses[i]=addRenderableWidget(Button.builder(text("pose."+i),b->send(GetoCatEdit.POSE,pose)).bounds(left+12+(i%2)*(w+4),y(38+(i/2)*27),w,h).build());
  }
  dance=addRenderableWidget(Button.builder(Component.empty(),b->send(GetoCatEdit.DANCE,(view.dancing()+1)%3)).bounds(left+12,y(124),panelWidth-24,h).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+12,y(188),panelWidth-24,h).build());refresh();
 }
 private void refresh(){
  if(dance==null)return;
  for(int i=0;i<poses.length;i++)poses[i].setMessage(Component.literal(view.pose()==i&&view.dancing()==0?"● ":"").append(text("pose."+i)).withStyle(view.pose()==i&&view.dancing()==0?ChatFormatting.AQUA:ChatFormatting.WHITE));
  dance.setMessage(Component.translatable("gui.jujutsuvfx.dance."+view.dancing()));
 }
 private void send(int action,int value){if(minecraft!=null&&minecraft.getConnection()!=null)PacketDistributor.sendToServer(new GetoCatEdit(view.session(),action,value));}
 @Override public void tick(){
  if(minecraft==null||minecraft.level==null||minecraft.player==null){onClose();return;}
  var entity=minecraft.level.getEntity(view.entityId());
  if(!(entity instanceof GetoCat cat)||!cat.getUUID().equals(view.entityUuid())||!cat.isAlive()||minecraft.player.isSpectator()||minecraft.player.distanceToSqr(cat)>256
    ||!minecraft.player.getMainHandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())&&!minecraft.player.getOffhandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())){onClose();return;}
  if(++ticks%40==0)send(GetoCatEdit.QUERY,0);
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){send(GetoCatEdit.CLOSE,0);super.onClose();}
 @Override public void renderBackground(GuiGraphics gui){
  super.renderBackground(gui);gui.fill(0,0,width,height,0x74010810);
  gui.fillGradient(left,top,left+panelWidth,top+panelHeight,0xf20c172a,0xf2170d29);gui.renderOutline(left,top,panelWidth,panelHeight,0xffae82d8);
 }
 @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
  renderBackground(gui);super.render(gui,mouseX,mouseY,partial);gui.drawString(font,title,left+12,y(12),0xffecf6ff);
  gui.drawString(font,text("hint"),left+12,y(156),0xffbdc7d8);gui.drawString(font,text("restore"),left+12,y(170),0xffbdc7d8);
 }
}

package dev.jujutsuvfx.client;

import dev.jujutsuvfx.entity.YutaCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.YutaCatEdit;
import dev.jujutsuvfx.network.YutaCatView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative, non-pausing controls for Yuta's independent entity type. */
public final class YutaCatScreen extends Screen {
 private YutaCatView view;
 private Button dance,drawn,energy;
 private final Button[] behaviors=new Button[3];
 private int left,top,panelWidth,panelHeight,ticks;
 private float scale;
 private YutaCatScreen(YutaCatView view){super(text("title"));this.view=view;}
 private static Component text(String key){return Component.translatable("gui.jujutsuvfx.yuta_cat."+key);}
 public static void receive(YutaCatView packet){
  Minecraft mc=Minecraft.getInstance();
  if(mc.level==null||!(mc.level.getEntity(packet.entityId()) instanceof YutaCat cat)||!cat.getUUID().equals(packet.entityUuid()))return;
  if(packet.open())mc.setScreen(new YutaCatScreen(packet));
  else if(mc.screen instanceof YutaCatScreen screen&&screen.view.session().equals(packet.session())){screen.view=packet;screen.refresh();}
 }
 private int y(int local){return top+Math.round(local*scale);}
 @Override protected void init(){
  panelWidth=Math.min(350,width-16);panelHeight=Math.min(218,height-12);
  left=(width-panelWidth)/2;top=(height-panelHeight)/2;scale=panelHeight/218f;
  int h=Math.max(14,Math.round(20*scale));
  dance=addRenderableWidget(Button.builder(text("dance.off"),b->send(YutaCatEdit.DANCE,(view.dancing()+1)%3)).bounds(left+12,y(35),panelWidth-24,h).build());
  drawn=addRenderableWidget(Button.builder(text("blade.sheathed"),b->send(YutaCatEdit.DRAWN,view.drawn()?0:1)).bounds(left+12,y(61),panelWidth-24,h).build());
  energy=addRenderableWidget(Button.builder(text("energy.0"),b->send(YutaCatEdit.ENERGY,(view.energy()+1)%3)).bounds(left+12,y(87),panelWidth-24,h).build());
  int w=(panelWidth-32)/3;
  for(int i=0;i<3;i++){
   final int mode=i;
   behaviors[i]=addRenderableWidget(Button.builder(Component.empty(),b->send(YutaCatEdit.BEHAVIOR,mode)).bounds(left+12+i*(w+4),y(147),w,h).build());
  }
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+12,y(188),panelWidth-24,h).build());
  refresh();
 }
 private void refresh(){
  if(dance==null)return;
  dance.setMessage(Component.translatable("gui.jujutsuvfx.dance."+view.dancing()).copy().withStyle(view.dancing()!=0?ChatFormatting.AQUA:ChatFormatting.WHITE));
  drawn.setMessage(text(view.drawn()?"blade.drawn":"blade.sheathed"));
  energy.setMessage(text("energy."+view.energy()).copy().withStyle(view.energy()==2?ChatFormatting.GRAY:ChatFormatting.DARK_AQUA));
  for(int i=0;i<3;i++)behaviors[i].setMessage(Component.literal(view.behavior()==i?"● ":"").append(Component.translatable("gui.jujutsuvfx.gojo_cat.behavior."+i)).withStyle(view.behavior()==i?ChatFormatting.AQUA:ChatFormatting.WHITE));
 }
 private void send(int action,int value){if(minecraft!=null&&minecraft.getConnection()!=null)PacketDistributor.sendToServer(new YutaCatEdit(view.session(),action,value));}
 private YutaCat cat(){
  if(minecraft==null||minecraft.level==null)return null;
  var entity=minecraft.level.getEntity(view.entityId());
  return entity instanceof YutaCat cat&&cat.getUUID().equals(view.entityUuid())?cat:null;
 }
 @Override public void tick(){
  YutaCat cat=cat();
  if(cat==null||!cat.isAlive()||minecraft.player==null||minecraft.player.isSpectator()||minecraft.player.distanceToSqr(cat)>256
    ||!minecraft.player.getMainHandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())&&!minecraft.player.getOffhandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())){onClose();return;}
  if(++ticks%40==0)send(YutaCatEdit.QUERY,0);
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){send(YutaCatEdit.CLOSE,0);super.onClose();}
 @Override public void renderBackground(GuiGraphics gui,int mouseX,int mouseY,float partial){
  super.renderBackground(gui,mouseX,mouseY,partial);
  gui.fill(0,0,width,height,0x74010810);
  gui.fillGradient(left,top,left+panelWidth,top+panelHeight,0xf20c172a,0xf2170d29);
  gui.renderOutline(left,top,panelWidth,panelHeight,0xff65b8aa);
 }
 @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
  super.render(gui,mouseX,mouseY,partial);
  gui.drawString(font,title,left+12,y(12),0xffecf6ff);
  gui.drawString(font,text("hint"),left+12,y(117),0xffa9d9dc);
  gui.drawString(font,Component.translatable("gui.jujutsuvfx.gojo_cat.behavior"),left+12,y(134),0xff9eafc8);
 }
}

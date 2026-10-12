package dev.jujutsuvfx.client;

import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.GojoCatView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Synced, read-only knowledge. The existing debug session remains subject to its distance/item checks. */
final class CatAdaptationScreen extends Screen {
    private final GojoCatScreen parent;
    private int left, top, panelWidth, panelHeight;
    private float scale;
    CatAdaptationScreen(GojoCatScreen parent) {
        super(text("title")); this.parent = parent;
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("gui.jujutsuvfx.adaptation." + key, args);
    }
    void receive(GojoCatView packet) { parent.updateView(packet); }
    private int y(int local) { return top + Math.round(local * scale); }
    @Override protected void init() {
        panelWidth = Math.min(418, width - 16); panelHeight = Math.min(300, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2; scale = panelHeight / 300f;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 12, y(274), panelWidth - 24, Math.max(14, Math.round(20 * scale))).build());
    }
    @Override public void tick() { parent.tick(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.renderBackground(gui, mouseX, mouseY, partial);
        gui.fill(0, 0, width, height, 0x74010810);
        gui.fillGradient(left, top, left + panelWidth, top + panelHeight, 0xf2181822, 0xf22a2017);
        gui.renderOutline(left, top, panelWidth, panelHeight, 0xffa68c56);
        gui.fill(left + 1, top + 1, left + panelWidth - 1, top + 3, 0xffe6c575);
    }
    private void label(GuiGraphics gui, Component value, int x, int y, int width, int color) {
        gui.drawString(font, font.substrByWidth(value, Math.max(8, width)).getString(), x, y, color);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        SorcererCat cat = parent.cat();
        label(gui, title, left + 12, y(10), panelWidth - 24, 0xffffe4a7);
        label(gui, text("reductions"), left + 12, y(28), panelWidth - 24, 0xffd2c3a1);
        label(gui, text("hint"), left + 12, y(43), panelWidth - 24, 0xffa9adb9);
        var known = new java.util.ArrayList<CompoundTag>();
        if (cat != null) {
            var rows = cat.adaptationView().getList("Rows", 10);
            for (int i = 0; i < Math.min(rows.size(), 9); i++) {
                known.add(rows.getCompound(i));
            }
        }
        Component hover = null;
        if(known.isEmpty())label(gui,text("unseen"),left+12,y(91),panelWidth-24,0xff9297a3);
        for(int i=0;i<known.size();i++){
            CompoundTag entry=known.get(known.size()-1-i);String kind=entry.getString("Kind");
            int hits=Math.max(0,entry.getInt("Hits")),reduction=Mth.clamp(entry.getInt("Reduction"),0,90);
            int x=left+12,row=63+i*21,lineWidth=panelWidth-24;
            int color=reduction==90?0xffedce7b:0xffd9e1ed;
            label(gui,text("kind."+kind),x,y(row),lineWidth/2,color);
            String status=text("count",hits,reduction).getString();
            gui.drawString(font,status,x+lineWidth-font.width(status),y(row),color);
            gui.fill(x,y(row+14),x+lineWidth,y(row+14)+2,0xff363339);
            gui.fill(x,y(row+14),x+lineWidth*reduction/90,y(row+14)+2,0xffebc76f);
            if(mouseX>=x&&mouseX<x+lineWidth&&mouseY>=y(row)&&mouseY<y(row+20))
                hover=text("progress",hits,reduction);
        }
        label(gui, text("persistent"), left + 12, y(260), panelWidth - 24, 0xffb4a686);
        if (hover != null) gui.renderTooltip(font, hover, mouseX, mouseY);
    }
}

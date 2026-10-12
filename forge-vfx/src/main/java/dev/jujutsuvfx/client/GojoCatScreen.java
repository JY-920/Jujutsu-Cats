package dev.jujutsuvfx.client;

import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.GojoCatEdit;
import dev.jujutsuvfx.network.GojoCatView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import dev.jujutsuvfx.platform.PacketDistributor;

/** Small live controls; simulation continues so the selected animation can finish while editing. */
public final class GojoCatScreen extends Screen {
    private GojoCatView view;
    private final boolean sukuna, mahoraga;
    private final Button[] appearances = new Button[3], behaviors = new Button[3];
    private Button arm, blue, red, cursed, blackFlash, automatic;
    private int left, top, panelWidth, panelHeight;
    private float verticalScale;
    private int ticks;
    private GojoCatScreen(GojoCatView view, boolean sukuna, boolean mahoraga) {
        super(Component.translatable("gui.jujutsuvfx." + (mahoraga ? "mahoraga_cat" : sukuna ? "sukuna_cat" : "gojo_cat") + ".title"));
        this.view = view; this.sukuna = sukuna; this.mahoraga = mahoraga;
    }
    private static Component text(String key, Object... args) { return Component.translatable("gui.jujutsuvfx.gojo_cat." + key, args); }
    public static void receive(GojoCatView packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.entityId()) instanceof SorcererCat cat)
                || !cat.getUUID().equals(packet.entityUuid())) return;
        if (packet.open()) mc.setScreen(new GojoCatScreen(packet, cat.isSukuna(), cat.isMahoraga()));
        else if (mc.screen instanceof GojoCatScreen screen && screen.view.session().equals(packet.session())) {
            screen.view = packet; screen.refresh();
        } else if (mc.screen instanceof CatAdaptationScreen screen) {
            screen.receive(packet);
        }
    }
    void updateView(GojoCatView packet) {
        if (view.session().equals(packet.session())) { view = packet; refresh(); }
    }
    private int y(int local) { return top + Math.round(local * verticalScale); }
    @Override protected void init() {
        panelWidth = Math.min(368, width - 16); panelHeight = Math.min(282, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2; verticalScale = panelHeight / 282f;
        int column = (panelWidth - 38) / 2, x1 = left + 12, x2 = left + panelWidth / 2 + 7;
        int buttonHeight = Math.max(14, Math.round(20 * verticalScale));
        for (int i = 0; i < appearances.length; i++) {
            int state = i;
            appearances[i] = addRenderableWidget(Button.builder(text("appearance." + i), b -> send(GojoCatEdit.APPEARANCE, state))
                    .bounds(x1, y(65 + i * 24), column, buttonHeight).build());
            appearances[i].visible = !mahoraga && (!sukuna || i == 0);
        }
        arm = addRenderableWidget(Button.builder(text("arm.full"), b -> send(GojoCatEdit.ARM, 0))
                .bounds(x1, y(sukuna ? 93 : 137), column, buttonHeight).build());
        cursed = addRenderableWidget(Button.builder(text("cursed"), b -> send(GojoCatEdit.CURSED, 0))
                .bounds(x2, y(65), column, buttonHeight).build());
        blackFlash = addRenderableWidget(Button.builder(text("black_flash"), b -> send(GojoCatEdit.BLACK_FLASH, 0))
                .bounds(x2, y(89), column, buttonHeight).build());
        blue = addRenderableWidget(Button.builder(text("blue"), b -> send(GojoCatEdit.BLUE, 0))
                .bounds(x2, y(113), column, buttonHeight).build());
        red = addRenderableWidget(Button.builder(text("red"), b -> send(GojoCatEdit.RED, 0))
                .bounds(x2, y(137), column, buttonHeight).build());
        blue.visible = red.visible = !sukuna && !mahoraga;
        cursed.visible = blackFlash.visible = arm.visible = !mahoraga;
        SorcererCat selected = cat();
        if (selected != null && selected.hasWheel())
            addRenderableWidget(Button.builder(Component.translatable("gui.jujutsuvfx.adaptation.open"),
                            b -> minecraft.setScreen(new CatAdaptationScreen(this)))
                    .bounds(x1, y(mahoraga ? 95 : 141), column, buttonHeight).build());
        automatic = addRenderableWidget(Button.builder(text("automatic.on"), b -> send(GojoCatEdit.AUTOMATIC, 0))
                .bounds(left + 12, y(168), panelWidth - 24, buttonHeight).build());
        automatic.visible = !mahoraga;
        int behaviorWidth = (panelWidth - 32) / 3;
        for (int i = 0; i < behaviors.length; i++) {
            int mode = i;
            behaviors[i] = addRenderableWidget(Button.builder(text("behavior." + i), b -> send(GojoCatEdit.BEHAVIOR, mode))
                    .bounds(left + 12 + i * (behaviorWidth + 4), y(230), behaviorWidth, buttonHeight).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 12, y(258), panelWidth - 24, buttonHeight).build());
        refresh();
    }
    private void refresh() {
        if (blue == null) return;
        for (int i = 0; i < appearances.length; i++) appearances[i].setMessage(selected("appearance." + i, view.appearance() == i));
        if (sukuna) appearances[0].setMessage(Component.literal(view.broken() ? "" : "● ")
                .append(Component.translatable("gui.jujutsuvfx.sukuna_cat.normal")));
        for (int i = 0; i < behaviors.length; i++) behaviors[i].setMessage(selected("behavior." + i, view.behavior() == i));
        arm.setMessage(text(view.broken() ? "arm.broken" : "arm.full").copy().withStyle(view.broken() ? ChatFormatting.RED : ChatFormatting.GRAY));
        blue.setMessage(text("energy", text("blue"), text("hands." + view.blue())).copy().withStyle(ChatFormatting.AQUA));
        red.setMessage(text("energy", text("red"), text("hands." + view.red())).copy().withStyle(ChatFormatting.RED));
        cursed.setMessage(text("energy", text("cursed"), text("hands." + view.cursed())).copy().withStyle(ChatFormatting.DARK_AQUA));
        blackFlash.setMessage(text("energy", text("black_flash"), text("hands." + view.blackFlash())).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        automatic.setMessage(text(view.automatic() ? "automatic.on" : "automatic.off").copy().withStyle(view.automatic() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
    private static Component selected(String key, boolean selected) {
        return Component.literal(selected ? "● " : "").append(text(key)).withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.WHITE);
    }
    private void send(int action, int value) {
        if (minecraft != null && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new GojoCatEdit(view.session(), action, value));
    }
    SorcererCat cat() {
        if (minecraft == null || minecraft.level == null) return null;
        var entity = minecraft.level.getEntity(view.entityId());
        return entity instanceof SorcererCat cat && cat.getUUID().equals(view.entityUuid()) ? cat : null;
    }
    @Override public void tick() {
        SorcererCat cat = cat();
        if (cat == null || !cat.isAlive() || minecraft.player == null || minecraft.player.distanceToSqr(cat) > 16 * 16
                || !minecraft.player.getMainHandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())
                && !minecraft.player.getOffhandItem().is(VfxItems.GOJO_CAT_DEBUG_STICK.get())) { onClose(); return; }
        if (++ticks % 40 == 0) send(GojoCatEdit.QUERY, 0);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { send(GojoCatEdit.CLOSE, 0); super.onClose(); }
    @Override public void renderBackground(GuiGraphics gui) {
        // Match the vanilla screen pipeline so Modern UI cannot blur foreground labels.
        super.renderBackground(gui);
        gui.fill(0, 0, width, height, 0x74010810);
        gui.fillGradient(left, top, left + panelWidth, top + panelHeight, 0xf20c172a, 0xf2170d29);
        gui.renderOutline(left, top, panelWidth, panelHeight, 0xff397ea9);
        gui.fill(left + 1, top + 1, left + panelWidth / 2, top + 3, 0xff4be3ff);
        gui.fill(left + panelWidth / 2, top + 1, left + panelWidth - 1, top + 3, 0xffec537b);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        renderBackground(gui);
        super.render(gui, mouseX, mouseY, partial);
        gui.drawString(font, title, left + 12, y(10), 0xffecf6ff);
        SorcererCat cat = cat();
        if (cat != null) {
            float ratio = Mth.clamp(cat.getHealth() / cat.getMaxHealth(), 0, 1);
            gui.fill(left + 12, y(28), left + panelWidth - 12, y(28) + 3, 0xff273b4c);
            gui.fill(left + 12, y(28), left + 12 + Math.round((panelWidth - 24) * ratio), y(28) + 3, 0xff65edcf);
            gui.drawString(font, text("health", Math.round(cat.getHealth()), Math.round(cat.getMaxHealth())), left + 12, y(36), 0xffa9d9dc);
            if (cat.readyHand() != 0) gui.drawString(font, text("next", text("hands." + cat.readyHand()), text("kind." + cat.readyKind())),
                    left + panelWidth / 2 + 7, y(36), 0xffedd7a0);
        }
        if (mahoraga) {
            gui.drawString(font,Component.translatable("gui.jujutsuvfx.mahoraga_cat.hint"),left+12,y(65),0xffe6d5a0);
            gui.drawString(font,text("behavior"),left+12,y(214),0xff9eafc8);return;
        }
        gui.drawString(font, text("appearance"), left + 12, y(53), 0xff9eafc8);
        gui.drawString(font, text("aura"), left + panelWidth / 2 + 7, y(53), 0xff9eafc8);
        gui.drawString(font, text(view.automatic() ? "automatic.hint" : "fixed.hint"), left + 12, y(196), 0xffbdc7dd);
        gui.drawString(font, sukuna ? Component.translatable("gui.jujutsuvfx.sukuna_cat.behavior") : text("behavior"), left + 12, y(214), 0xff9eafc8);
        if (sukuna) gui.drawString(font, Component.translatable("gui.jujutsuvfx.sukuna_cat.enemy"), left + 12, y(124), 0xffd78697);
    }
}

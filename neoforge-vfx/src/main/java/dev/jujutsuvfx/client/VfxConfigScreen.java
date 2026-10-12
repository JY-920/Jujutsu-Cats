package dev.jujutsuvfx.client;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.network.VfxSettingsRequest;
import dev.jujutsuvfx.network.VfxSettingsState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client options save locally. The terrain button submits a request and displays only the server's reply. */
@EventBusSubscriber(modid = JujutsuVfx.ID, value = Dist.CLIENT)
public final class VfxConfigScreen extends Screen {
    private static boolean openRequested, serverCinematic=true;
    private static int syncTicks;
    public static boolean cinematic(){return serverCinematic;}
    private static KeyMapping openConfigKey;
    private final Screen parent;
    private VfxSettingsState state;
    private Button flashes, terrain, physics, cinematic, unlimited, music, clientTab, serverTab;
    private LightSlider lighting;
    private MusicSlider musicVolume;
    private boolean serverPage;
    private boolean pending;
    private int left, top, panelWidth, ticks;

    public VfxConfigScreen(Screen parent) { super(text("title")); this.parent = parent; }
    private static Component text(String key, Object... args) { return Component.translatable("gui.jujutsuvfx.config." + key, args); }
    private static Component onOff(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    static void registerKeys(RegisterKeyMappingsEvent event) {
        openConfigKey = new KeyMapping("key.jujutsuvfx.open_config", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.jujutsuvfx");
        event.register(openConfigKey);
    }
    public static int requestOpen() { openRequested = true; return 1; }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&++syncTicks%40==1&&mc.getConnection().hasChannel(VfxSettingsRequest.TYPE))
            PacketDistributor.sendToServer(new VfxSettingsRequest(-1));
        DomainSettingsScreen.sync(mc, syncTicks);
        CinematicMusic.tick();
        // Drain all queued presses, but never open over chat, another menu, or a loading screen.
        while (openConfigKey != null && openConfigKey.consumeClick()) {
            if (mc.level != null && mc.player != null && mc.screen == null) openRequested = true;
        }
        if (!openRequested) return;
        openRequested = false;
        if (mc.player != null) mc.setScreen(new VfxConfigScreen(null));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { openRequested = false; serverCinematic=true; syncTicks=0; DomainSettingsScreen.clear(); CinematicMusic.clear(); }
    public static void receive(VfxSettingsState packet) {
        if(serverCinematic&&!packet.cinematic())CatCinematicCamera.disableAutomatic();
        serverCinematic=packet.cinematic();
        if (Minecraft.getInstance().screen instanceof VfxConfigScreen screen) {
            screen.state = packet; screen.pending = false; screen.refresh();
        }
    }
    private boolean connected() {
        return minecraft != null && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(VfxSettingsRequest.TYPE)
                && minecraft.getConnection().hasChannel(VfxSettingsState.TYPE);
    }
    private boolean showPhysics() {
        return connected() && state != null && state.destruction() && state.physicsAvailable()
                && dev.jujutsuvfx.compat.CleaveCompatibility.aeronauticsAvailable();
    }
    @Override protected void init() {
        panelWidth = Math.min(410, width - 24); left = (width - panelWidth) / 2; top = Math.max(0,(height - 238) / 2);
        int tabWidth=(panelWidth-34)/2;
        clientTab=addRenderableWidget(Button.builder(text("tab.client"),b->{serverPage=false;refresh();})
                .bounds(left+14,top+34,tabWidth,20).build());
        serverTab=addRenderableWidget(Button.builder(text("tab.server"),b->{serverPage=true;refresh();})
                .bounds(left+20+tabWidth,top+34,tabWidth,20).build());
        lighting=addRenderableWidget(new LightSlider(left+14,top+86,panelWidth-28));
        music=addRenderableWidget(Button.builder(Component.empty(),b->{VfxConfig.music(!VfxConfig.music());refresh();})
                .bounds(left+14,top+134,panelWidth-28,20).build());
        music.setTooltip(Tooltip.create(text("music.tooltip")));
        musicVolume=addRenderableWidget(new MusicSlider(left+14,top+158,panelWidth-28));
        flashes = addRenderableWidget(Button.builder(Component.empty(), b -> {
            VfxConfig.flashes(!VfxConfig.flashes()); refresh();
        }).bounds(left + 14, top + 110, panelWidth - 28, 20).build());
        flashes.setTooltip(Tooltip.create(text("flashes.tooltip")));
        cinematic = addRenderableWidget(Button.builder(Component.empty(), b -> editServer(state==null? -1:state.cinematic()?4:5))
                .bounds(left+14,top+62,panelWidth-28,20).build());
        cinematic.setTooltip(Tooltip.create(text("cinematic.tooltip")));
        unlimited = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if(state!=null&&state.destruction())editServer(state.unlimitedDestruction()?6:7);
        }).bounds(left+14,top+110,panelWidth-28,20).build());
        unlimited.setTooltip(Tooltip.create(text("unlimited.tooltip")));
        terrain = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (connected() && state != null && state.editable() && !pending) {
                pending = true; refresh();
                PacketDistributor.sendToServer(new VfxSettingsRequest(state.destruction() ? 0 : 1));
            }
        }).bounds(left + 14, top + 62, panelWidth - 28, 20).build());
        terrain.setTooltip(Tooltip.create(text("terrain.tooltip")));
        physics = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (showPhysics() && state.editable() && !pending) {
                pending = true; refresh();
                PacketDistributor.sendToServer(new VfxSettingsRequest(state.cleavePhysics() ? 2 : 3));
            }
        }).bounds(left + 14, top + 86, panelWidth - 28, 20).build());
        physics.setTooltip(Tooltip.create(text("cleave_physics.tooltip")));
        addRenderableWidget(Button.builder(text("damage"), b -> minecraft.setScreen(new TechniqueDamageScreen(this)))
                .bounds(left + 14, top + 186, (panelWidth - 34) / 2, 20).build())
                .setTooltip(Tooltip.create(text("damage.tooltip")));
        addRenderableWidget(Button.builder(text("domains"), b -> minecraft.setScreen(new DomainSettingsScreen(this)))
                .bounds(left + 20 + (panelWidth - 34) / 2, top + 186, (panelWidth - 34) / 2, 20).build())
                .setTooltip(Tooltip.create(text("domains.tooltip")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth / 2 - 60, top + 214, 120, 20).build());
        refresh(); query();
    }
    private void editServer(int action){
        if(action>=0&&connected()&&state!=null&&state.editable()&&!pending){
            pending=true;refresh();PacketDistributor.sendToServer(new VfxSettingsRequest(action));
        }
    }
    private void query() { if (connected()) PacketDistributor.sendToServer(new VfxSettingsRequest(-1)); }
    private void refresh() {
        if (flashes == null || terrain == null || physics == null) return;
        clientTab.active=serverPage;serverTab.active=!serverPage;
        lighting.visible=flashes.visible=music.visible=musicVolume.visible=!serverPage;
        lighting.active=flashes.active=music.active=!serverPage;
        musicVolume.active=!serverPage&&VfxConfig.music();
        music.setMessage(text("music",onOff(VfxConfig.music())));
        terrain.visible=serverPage;cinematic.visible=!serverPage;
        flashes.setMessage(text("flashes", onOff(VfxConfig.flashes())));
        terrain.setMessage(text("terrain", state == null ? text(connected() ? "loading" : "unavailable") : onOff(state.destruction())));
        terrain.active = serverPage && connected() && state != null && state.editable() && !pending;
        physics.setMessage(text("cleave_physics", onOff(state == null || state.cleavePhysics())));
        cinematic.setMessage(text("cinematic",onOff(state==null||state.cinematic())));
        cinematic.active=!serverPage&&connected()&&state!=null&&state.editable()&&!pending;
        unlimited.setMessage(text("unlimited",onOff(state!=null&&state.unlimitedDestruction())));
        unlimited.visible=serverPage&&connected()&&state!=null&&state.destruction();
        unlimited.active=unlimited.visible&&state.editable()&&!pending;
        unlimited.setY(top+(showPhysics()?110:86));
        if(!unlimited.visible&&getFocused()==unlimited)setFocused(null);
        physics.visible = serverPage && showPhysics();
        physics.active = physics.visible && state.editable() && !pending;
        if (!physics.visible && getFocused() == physics) setFocused(null);
    }
    @Override public void tick() {
        if (!connected()) { state = null; pending = false; refresh(); }
        if (++ticks % 40 == 0) query(); // Also notices permission changes and external server config edits.
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        // Screen.render calls this before widgets. Blur only the world, never our panel or text.
        super.renderBackground(gui, mouseX, mouseY, partial);
        gui.fill(0, 0, width, height, 0xb0080c16);
        gui.fillGradient(left, top, left + panelWidth, top + 238, 0xf2162338, 0xf219102c);
        gui.renderOutline(left, top, panelWidth, 238, 0xff4a719a);
        gui.fill(left + 1, top + 1, left + panelWidth / 2, top + 3, 0xff49d9ee);
        gui.fill(left + panelWidth / 2, top + 1, left + panelWidth - 1, top + 3, 0xffc162e0);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        gui.drawString(font, title, left + 14, top + 13, 0xffedf7ff);

        if(serverPage){
            String hint = !connected() ? "terrain.offline" : pending ? "terrain.saving"
                    : state == null ? "terrain.loading" : state.editable() ? "access.admin" : "terrain.readonly";
            gui.drawWordWrap(font,text(hint),left+14,top+148,panelWidth-28,0xffc5afd5);
        }
    }
    private static final class MusicSlider extends AbstractSliderButton {
        MusicSlider(int x,int y,int width){
            super(x,y,width,20,Component.empty(),VfxConfig.musicVolume()/100.0);
            setTooltip(Tooltip.create(text("music.volume.tooltip")));updateMessage();
        }
        @Override protected void updateMessage(){setMessage(text("music.volume",(int)Math.round(value*100)));}
        @Override protected void applyValue(){
            int volume=(int)Math.round(value*100);
            if(volume!=VfxConfig.musicVolume())VfxConfig.musicVolume(volume);
        }
        @Override public boolean keyPressed(int key,int scanCode,int modifiers){
            if(key==262||key==263||key==268||key==269){
                int volume=key==268?0:key==269?100:Math.max(0,Math.min(100,VfxConfig.musicVolume()+(key==262?5:-5)));
                value=volume/100.0;applyValue();updateMessage();return true;
            }
            return super.keyPressed(key,scanCode,modifiers);
        }
    }
    private static final class LightSlider extends AbstractSliderButton {
        LightSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), VfxConfig.lighting() / 5.0);
            setTooltip(Tooltip.create(text("lighting.tooltip"))); updateMessage();
        }
        @Override protected void updateMessage() { setMessage(text("lighting", VfxConfig.lighting())); }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == 262 || key == 263 || key == 268 || key == 269) {
                int level = key == 268 ? 0 : key == 269 ? 5 : Math.clamp(VfxConfig.lighting() + (key == 262 ? 1 : -1), 0, 5);
                value = level / 5.0; applyValue(); updateMessage(); return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        @Override protected void applyValue() {
            int level = (int) Math.round(value * 5);
            // Quantize saved values, but do not quantize the drag handle (keyboard steps must accumulate).
            if (level != VfxConfig.lighting()) TechniqueLighting.setStrength(level);
        }
    }
}

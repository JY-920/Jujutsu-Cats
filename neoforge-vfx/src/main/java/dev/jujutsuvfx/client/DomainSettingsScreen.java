package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.DomainDuration;
import dev.jujutsuvfx.network.DomainSettingsRequest;
import dev.jujutsuvfx.network.DomainSettingsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Numeric drafts and acknowledged server state are kept separately. */
public final class DomainSettingsScreen extends Screen {
    private static int nextRequest, cachedVoid = DomainDuration.DEFAULT_SECONDS, cachedShrine = DomainDuration.DEFAULT_SECONDS;
    private final Screen parent;
    private final String[] draft = {"5", "5"};
    private final EditBox[] inputs = new EditBox[2];
    private DomainSettingsState state;
    private Button apply, defaults, back;
    private boolean pending, querying;
    private int requestId, waitingTicks, left, top, panelWidth;
    private String status = "loading";

    public DomainSettingsScreen(Screen parent) { super(text("title")); this.parent = parent; }
    private static Component text(String key, Object... args) { return Component.translatable("gui.jujutsuvfx.domains." + key, args); }
    public static int seconds(boolean gojo) { return gojo ? cachedVoid : cachedShrine; }
    public static void clear() { cachedVoid = cachedShrine = DomainDuration.DEFAULT_SECONDS; }
    public static void sync(Minecraft mc, int ticks) {
        if (mc.player != null && mc.level != null && ticks % 40 == 1 && mc.getConnection() != null
                && mc.getConnection().hasChannel(DomainSettingsRequest.TYPE)
                && mc.getConnection().hasChannel(DomainSettingsState.TYPE))
            PacketDistributor.sendToServer(new DomainSettingsRequest(0, false, 5, 5));
    }
    private static int nextId() { nextRequest = nextRequest == Integer.MAX_VALUE ? 1 : nextRequest + 1; return nextRequest; }
    private boolean connected() {
        return minecraft != null && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(DomainSettingsRequest.TYPE)
                && minecraft.getConnection().hasChannel(DomainSettingsState.TYPE);
    }
    private boolean editable() { return connected() && state != null && state.editable() && !pending; }
    @Override protected void init() {
        panelWidth = Math.min(410, width - 24); left = (width - panelWidth) / 2; top = Math.max(0, (height - 228) / 2);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            EditBox input = new EditBox(font, left + panelWidth - 98, top + 65 + i * 34, 60, 20, text(i == 0 ? "void" : "shrine"));
            input.setMaxLength(3);
            input.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,3}"));
            input.setValue(draft[i]);
            input.setResponder(value -> { draft[index] = value; status = "unsaved"; refresh(); });
            input.setTooltip(Tooltip.create(text("value.tooltip")));
            inputs[i] = addRenderableWidget(input);
        }
        int bw = (panelWidth - 40) / 3;
        defaults = addRenderableWidget(Button.builder(text("defaults"), b -> {
            draft[0] = draft[1] = "5"; status = "unsaved"; rebuildWidgets();
        }).bounds(left + 14, top + 202, bw, 20).build());
        apply = addRenderableWidget(Button.builder(text("apply"), b -> save())
                .bounds(left + 20 + bw, top + 202, bw, 20).build());
        back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 26 + bw * 2, top + 202, bw, 20).build());
        back.setTooltip(Tooltip.create(text("back.tooltip")));
        refresh();
        if (state == null && !querying && connected()) {
            querying = true; waitingTicks = 0; requestId = nextId();
            PacketDistributor.sendToServer(new DomainSettingsRequest(requestId, false, 5, 5));
        }
    }
    private int parsed(int index) {
        try { int value = Integer.parseInt(draft[index]); return DomainDuration.valid(value) ? value : -1; }
        catch (NumberFormatException invalid) { return -1; }
    }
    private boolean dirty() { return state != null && (parsed(0) != state.voidSeconds() || parsed(1) != state.shrineSeconds()); }
    private void refresh() {
        if (apply == null) return;
        for (int i = 0; i < 2; i++) {
            inputs[i].setEditable(editable()); inputs[i].setTextColor(parsed(i) < 0 ? 0xffff7777 : 0xffedf7ff);
        }
        defaults.active = editable(); apply.active = editable() && parsed(0) >= 0 && parsed(1) >= 0 && dirty(); back.active = !pending;
    }
    private void save() {
        if (!editable() || parsed(0) < 0 || parsed(1) < 0 || !dirty()) return;
        pending = true; waitingTicks = 0; requestId = nextId(); status = "saving"; refresh();
        PacketDistributor.sendToServer(new DomainSettingsRequest(requestId, true, parsed(0), parsed(1)));
    }
    public static void receive(DomainSettingsState packet) {
        if (!packet.validValues()) return;
        cachedVoid = packet.voidSeconds(); cachedShrine = packet.shrineSeconds();
        if (Minecraft.getInstance().screen instanceof DomainSettingsScreen screen) screen.accept(packet);
    }
    private void accept(DomainSettingsState packet) {
        if (packet.requestId() != 0 && packet.requestId() != requestId) return;
        boolean hadDraft = dirty(), ours = packet.requestId() == requestId, first = state == null;
        state = packet;
        if (ours) { querying = pending = false; waitingTicks = 0; }
        if (first || ours && packet.result() == DomainSettingsState.SAVED || !hadDraft && !pending) {
            draft[0] = Integer.toString(packet.voidSeconds()); draft[1] = Integer.toString(packet.shrineSeconds());
            // Polling must not recreate focused inputs or reset an unchanged cursor/selection.
            for (int i = 0; i < 2; i++)
                if (!inputs[i].getValue().equals(draft[i])) inputs[i].setValue(draft[i]);
        }
        if (ours) status = switch (packet.result()) {
            case DomainSettingsState.SAVED -> "saved";
            case DomainSettingsState.DENIED -> "denied";
            case DomainSettingsState.INVALID -> "invalid";
            case DomainSettingsState.FAILED -> "failed";
            default -> packet.editable() ? "ready" : "readonly";
        };
        else if (first) status = packet.editable() ? "ready" : "readonly";
        else if (hadDraft) status = "external";
        refresh();
    }
    @Override public void tick() {
        if (!connected()) { pending = querying = false; status = "unavailable"; refresh(); return; }
        if ((pending || querying) && ++waitingTicks > 200) {
            requestId = nextId(); pending = querying = false; status = "timeout"; refresh();
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return !pending; }
    @Override public void onClose() { if (!pending) minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.renderBackground(gui, mouseX, mouseY, partial);
        gui.fill(0, 0, width, height, 0xb0080c16);
        gui.fillGradient(left, top, left + panelWidth, top + 228, 0xf2162338, 0xf219102c);
        gui.renderOutline(left, top, panelWidth, 228, 0xff4a719a);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        gui.drawString(font, title, left + 14, top + 13, 0xffedf7ff);
        gui.drawString(font, text("hint"), left + 14, top + 33, 0xffa8bacf);
        for (int i = 0; i < 2; i++) {
            gui.drawString(font, text(i == 0 ? "void" : "shrine"), left + 14, top + 71 + i * 34, 0xffedf7ff);
            gui.drawString(font, text("seconds"), left + panelWidth - 30, top + 71 + i * 34, 0xffb6c9df);
        }
        gui.drawWordWrap(font, text("notes"), left + 14, top + 130, panelWidth - 28, 0xffa8bacf);
        String hint = !connected() ? "unavailable" : (parsed(0) < 0 || parsed(1) < 0) && state != null ? "invalid" : status;
        gui.drawWordWrap(font, text(hint), left + 14, top + 170, panelWidth - 28, 0xffc5afd5);
    }
}

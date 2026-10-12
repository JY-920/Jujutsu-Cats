package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.core.TechniqueDamagePreview;
import dev.jujutsuvfx.network.DamageSettingsRequest;
import dev.jujutsuvfx.network.DamageSettingsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** Server-authoritative settings; drafts survive page changes, resizing and server broadcasts. */
public final class TechniqueDamageScreen extends Screen {
    private static final TechniqueDamage[] TECHNIQUES = TechniqueDamage.values();
    private static int nextRequest;
    private final Screen parent;
    private final String[] draft = new String[DamageSettingsRequest.COUNT];
    private final List<EditBox> inputs = new ArrayList<>();
    private DamageSettingsState state;
    private Button apply, defaults, back;
    private boolean pending, querying;
    private int requestId, waitingTicks, page, rows, left, top, panelWidth, panelHeight, reference;
    private static final int ROW_HEIGHT = 34;
    private String status = "loading";

    public TechniqueDamageScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        java.util.Arrays.fill(draft, "100");
    }
    private static Component text(String key, Object... args) { return Component.translatable("gui.jujutsuvfx.damage." + key, args); }
    private boolean connected() {
        return minecraft != null && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(DamageSettingsRequest.TYPE)
                && minecraft.getConnection().hasChannel(DamageSettingsState.TYPE);
    }
    private boolean editable() { return connected() && state != null && state.editable() && !pending; }
    private static int nextId() { nextRequest = nextRequest == Integer.MAX_VALUE ? 1 : nextRequest + 1; return nextRequest; }

    @Override protected void init() {
        inputs.clear();
        panelWidth = Math.min(470, width - 20);
        panelHeight = Math.min(322, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        rows = Math.max(1, Math.min(5, (panelHeight - 144) / ROW_HEIGHT));
        page = Math.min(page, (TECHNIQUES.length - 1) / rows);
        addRenderableWidget(Button.builder(text("reference." + reference), b -> {
            reference = (reference + 1) % 4; rebuildWidgets();
        }).bounds(left + 14, top + 44, panelWidth - 28, 18).build())
                .setTooltip(Tooltip.create(text("reference.tooltip")));
        for (int row = 0; row < rows; row++) {
            final int index = page * rows + row;
            if (index >= TECHNIQUES.length) break;
            EditBox input = new EditBox(font, left + panelWidth - 92, top + 70 + row * ROW_HEIGHT, 60, 18,
                    Component.translatable(TECHNIQUES[index].translationKey()));
            input.setMaxLength(4);
            input.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,4}"));
            input.setValue(draft[index]);
            input.setResponder(value -> { draft[index] = value; status = "unsaved"; refresh(); });
            input.setTooltip(Tooltip.create(text("value.tooltip")));
            inputs.add(addRenderableWidget(input));
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(left + 14, top + panelHeight - 78, 32, 18).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(left + panelWidth - 46, top + panelHeight - 78, 32, 18).build());
        next.active = (page + 1) * rows < TECHNIQUES.length;
        int buttonWidth = (panelWidth - 40) / 3;
        defaults = addRenderableWidget(Button.builder(text("defaults"), b -> {
            java.util.Arrays.fill(draft, "100"); status = "unsaved"; rebuildWidgets();
        }).bounds(left + 14, top + panelHeight - 26, buttonWidth, 20).build());
        defaults.setTooltip(Tooltip.create(text("defaults.tooltip")));
        apply = addRenderableWidget(Button.builder(text("apply"), b -> save())
                .bounds(left + 20 + buttonWidth, top + panelHeight - 26, buttonWidth, 20).build());
        back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 26 + buttonWidth * 2, top + panelHeight - 26, buttonWidth, 20).build());
        back.setTooltip(Tooltip.create(text("back.tooltip")));
        refresh();
        if (state == null && !querying && connected()) {
            querying = true; waitingTicks = 0; requestId = nextId();
            PacketDistributor.sendToServer(new DamageSettingsRequest(requestId, false, new int[DamageSettingsRequest.COUNT]));
        }
    }
    private void changePage(int delta) { page += delta; rebuildWidgets(); }
    private int percent(int index) {
        try { int value = Integer.parseInt(draft[index]); return value >= 0 && value <= 1000 ? value : -1; }
        catch (NumberFormatException invalid) { return -1; }
    }
    private Component preview(int index) {
        if (state == null) return text("loading");
        int value = percent(index);
        if (value < 0) return text("invalid");
        boolean cat = (reference & 1) != 0, player = (reference & 2) != 0;
        var original = TechniqueDamagePreview.sample(TECHNIQUES[index], cat, player, VfxConfigScreen.cinematic(), 100);
        var adjusted = TechniqueDamagePreview.sample(TECHNIQUES[index], cat, player, VfxConfigScreen.cinematic(), value);
        return text("preview", original.text(), adjusted.text());
    }
    private Component details(int index) {
        float special = 6 * Math.max(0, percent(index)) / 100f;
        return text("details." + TECHNIQUES[index].key(), TechniqueDamagePreview.number(special)).copy()
                .append(Component.literal("\n")).append(text("reference.tooltip"));
    }
    private int[] parsed() {
        int[] values = new int[draft.length];
        try { for (int i = 0; i < values.length; i++) values[i] = Integer.parseInt(draft[i]); }
        catch (NumberFormatException failure) { return null; }
        return DamageSettingsRequest.validValues(values) ? values : null;
    }
    private boolean dirty() {
        return state != null && !java.util.Arrays.equals(parsed(), state.percents());
    }
    private void refresh() {
        if (apply == null) return;
        for (int row = 0; row < inputs.size(); row++) {
            EditBox input = inputs.get(row); input.setEditable(editable());
            String value = draft[page * rows + row];
            boolean valid;
            try { int number = Integer.parseInt(value); valid = number >= 0 && number <= 1000; }
            catch (NumberFormatException failure) { valid = false; }
            input.setTextColor(valid ? 0xffedf7ff : 0xffff7777);
        }
        defaults.active = editable(); apply.active = editable() && parsed() != null && dirty(); back.active = !pending;
    }
    private void save() {
        int[] values = parsed();
        if (!editable() || values == null || !dirty()) return;
        pending = true; waitingTicks = 0; requestId = nextId(); status = "saving"; refresh();
        PacketDistributor.sendToServer(new DamageSettingsRequest(requestId, true, values));
    }
    public static void receive(DamageSettingsState packet) {
        if (Minecraft.getInstance().screen instanceof TechniqueDamageScreen screen) screen.accept(packet);
    }
    private void accept(DamageSettingsState packet) {
        if (!DamageSettingsRequest.validValues(packet.percents())) return;
        // Zero is an unsolicited server broadcast. Other IDs belong to a particular open screen/request.
        if (packet.requestId() != 0 && packet.requestId() != requestId) return;
        boolean hadDraft = dirty(), ours = packet.requestId() == requestId;
        boolean first = state == null;
        state = packet;
        if (ours) { querying = false; pending = false; waitingTicks = 0; }
        if (first || ours && packet.result() == DamageSettingsState.SAVED || !hadDraft && !pending) {
            int[] values = packet.percents();
            for (int i = 0; i < values.length; i++) draft[i] = Integer.toString(values[i]);
            rebuildWidgets();
        }
        if (ours) status = switch (packet.result()) {
            case DamageSettingsState.SAVED -> "saved";
            case DamageSettingsState.DENIED -> "denied";
            case DamageSettingsState.INVALID -> "invalid";
            case DamageSettingsState.FAILED -> "failed";
            default -> packet.editable() ? "ready" : "readonly";
        };
        else if (hadDraft) status = "external";
        refresh();
    }
    @Override public void tick() {
        if (!connected()) { pending = querying = false; status = "unavailable"; refresh(); return; }
        if ((pending || querying) && ++waitingTicks > 200) {
            // Retire this transaction: a late save reply must not overwrite edits made after the timeout.
            requestId = nextId();
            pending = querying = false; status = "timeout"; refresh();
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return !pending; }
    @Override public void onClose() { if (!pending) minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.renderBackground(gui, mouseX, mouseY, partial);
        gui.fill(0, 0, width, height, 0xb0080c16);
        gui.fillGradient(left, top, left + panelWidth, top + panelHeight, 0xf2162338, 0xf219102c);
        gui.renderOutline(left, top, panelWidth, panelHeight, 0xff4a719a);
        gui.fill(left + 1, top + 1, left + panelWidth / 2, top + 3, 0xff49d9ee);
        gui.fill(left + panelWidth / 2, top + 1, left + panelWidth - 1, top + 3, 0xffc162e0);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        gui.drawString(font, title, left + 14, top + 13, 0xffedf7ff);
        gui.drawString(font, text("preview.hint"), left + 14, top + 29, 0xffa8bacf);
        for (int row = 0; row < inputs.size(); row++) {
            int index = page * rows + row;
            gui.drawString(font, Component.translatable(TECHNIQUES[index].translationKey()), left + 14, top + 74 + row * ROW_HEIGHT, 0xffedf7ff);
            gui.drawString(font, "%", left + panelWidth - 25, top + 74 + row * ROW_HEIGHT, 0xffb6c9df);
            gui.drawString(font, preview(index), left + 14, top + 91 + row * ROW_HEIGHT, 0xffa8d8df);
        }
        gui.drawCenteredString(font, text("page", page + 1, (TECHNIQUES.length + rows - 1) / rows), left + panelWidth / 2, top + panelHeight - 72, 0xffb6c9df);
        String hint = !connected() ? "unavailable" : parsed() == null && state != null ? "invalid" : status;
        gui.drawWordWrap(font, text(hint), left + 14, top + panelHeight - 53, panelWidth - 28, 0xffc5afd5);
        if (mouseX >= left + 14 && mouseX < left + panelWidth - 14 && mouseY >= top + 27 && mouseY < top + 40)
            gui.renderTooltip(font, font.split(text("notes"), 280), mouseX, mouseY);
        for (int row = 0; row < inputs.size(); row++) {
            int y = top + 70 + row * ROW_HEIGHT;
            if (mouseX >= left + 14 && mouseX < left + panelWidth - 100 && mouseY >= y && mouseY < y + 31)
                gui.renderTooltip(font, font.split(details(page * rows + row), Math.min(340, width - 24)), mouseX, mouseY);
        }
    }
}

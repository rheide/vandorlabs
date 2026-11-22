package com.vandorlabs.neoforge.builders.client;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.builders.NativeDriveBindings;
import com.vandorlabs.neoforge.builders.NativeDriveMenu;
import com.vandorlabs.neoforge.builders.NativeDriveSettings;
import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.client.NativeDialogTabButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.DialogStyle;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NativeDriveScreen extends AbstractContainerScreen<NativeDriveMenu> {
    private static final String[] TABS = {"Displays", "Keyboard", "Engine", "Redstone"};
    private static final String[] EXHAUST = {"Off", "Light", "Medium", "Heavy"};
    private static final String[] SPEED = {"Slow", "Normal", "Fast"};
    private int page;
    private NativeDriveSettings settings;
    private NativeMaterialPicker picker;
    private EditBox channelField;
    private String channels;

    public NativeDriveScreen(NativeDriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 312;
        imageHeight = 238;
        settings = menu.settings();
        if (menu.maxwell()) page = 2;
        channels = menu.channels().toString();
    }

    @Override protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        previous.clear();
        picker = null;
        channelField = null;
        var design=menu.design();
        int[] pages=menu.maxwell()?new int[]{2,3}:design.engine()?new int[]{0,1,2,3}:design.computer()?new int[]{0,3}:new int[]{0,1,3};
        int tabWidth = (imageWidth - 24) / pages.length;
        for (int i = 0; i < pages.length; i++) {
            int next = pages[i];
            var tab = new NativeDialogTabButton(leftPos + 12 + i * tabWidth, topPos + 30, tabWidth, 20,
                    Component.literal(TABS[next]), button -> {
                        if (send()) {
                            page = next;
                            rebuild();
                        }
                    });
            tab.active = next != page;
            addRenderableWidget(tab);
        }
        if (page < 2) {
            picker = new NativeMaterialPicker(page == 0 ? settings.display() : settings.keyboard(),
                    NativeDriveSettings::material, this::selectMaterial)
                    .specialMaterial(-2, "Original artwork", "Screens", "vandorlabs:blocks/core_flat")
                    .specialMaterial(-1, "Black", "Screens", "minecraft:block/black_concrete");
            picker.layout(leftPos + 12, topPos + 57, imageWidth - 31, 4, true);
            addRenderableWidget(picker.searchField());
            option(180, settings.animated() ? "Animated" : "Static", direction -> animation(!settings.animated(), settings.speed()));
            option(201, "Speed " + SPEED[settings.speed()], direction -> animation(settings.animated(), cycle(settings.speed(), direction, 3)));
        } else if (page == 2 && menu.maxwell()) {
            addRenderableWidget(NativeDialogButton.builder(Component.literal("Purge Tank"), ignored -> {
                if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, NativeDriveMenu.PURGE_TANK);
            }).bounds(leftPos + 12, topPos + 107, imageWidth - 24, 18).build());
        } else if (page == 2 && menu.design().exhaust) {
            option(66, "Particles " + EXHAUST[settings.particles()], direction -> controls(settings.redstone(), settings.inverted(),
                    settings.levels(), cycle(settings.particles(), direction, 4), settings.threshold()));
        } else if (page == 3) {
            channelField = new EditBox(font, leftPos + 12, topPos + 73, imageWidth - 24, 18, Component.literal("Receive channels"));
            channelField.setMaxLength(ChannelList.MAX_TEXT_LENGTH);
            channelField.setValue(channels);
            channelField.setResponder(text -> { channels = text; send(); });
            addRenderableWidget(channelField);
            option(99, settings.redstone() ? "Activation Redstone" : menu.maxwell() ? "Activation Automatic" : "Activation Always", direction ->
                    controls(!settings.redstone(), settings.inverted(), settings.levels(), settings.particles(), settings.threshold()));
            if(!menu.design().engine())return;
            option(122, settings.inverted() ? "Engine Redstone Off" : "Engine Redstone On", direction ->
                    controls(settings.redstone(), !settings.inverted(), settings.levels(), settings.particles(), settings.threshold()));
            option(145, menu.maxwell() ? (settings.levels() ? "Throttle Analog" : "Throttle Digital") : (settings.levels() ? "Brightness Signal" : "Brightness On/Off"), direction ->
                    controls(settings.redstone(), settings.inverted(), !settings.levels(), settings.particles(), settings.threshold()));
            if (!menu.maxwell() && menu.design().exhaust) option(168, "Particles at level " + settings.threshold(), direction ->
                    controls(settings.redstone(), settings.inverted(), settings.levels(), settings.particles(), cycle(settings.threshold(), direction, 16)));
        }
    }

    private final java.util.Map<net.minecraft.client.gui.components.Button, IntConsumer> previous = new java.util.HashMap<>();

    private void option(int y, String text, IntConsumer action) {
        var button = addRenderableWidget(NativeDialogButton.builder(Component.literal(text), ignored -> action.accept(1))
                .bounds(leftPos + 12, topPos + y, imageWidth - 24, 18).build());
        previous.put(button, action);
    }

    private void selectMaterial(int choice) {
        settings = new NativeDriveSettings(settings.redstone(), settings.inverted(), settings.levels(), settings.particles(),
                settings.threshold(), page == 0 ? choice : settings.display(), page == 1 ? choice : settings.keyboard(),
                settings.animated(), settings.speed());
        send();
    }

    private void animation(boolean animated, int speed) {
        settings = new NativeDriveSettings(settings.redstone(), settings.inverted(), settings.levels(), settings.particles(),
                settings.threshold(), settings.display(), settings.keyboard(), animated, speed);
        updateOptions();
    }

    private void controls(boolean redstone, boolean inverted, boolean levels, int particles, int threshold) {
        settings = new NativeDriveSettings(redstone, inverted, levels, particles, threshold,
                settings.display(), settings.keyboard(), settings.animated(), settings.speed());
        updateOptions();
    }

    private void updateOptions() {
        send();
        rebuild();
    }

    private boolean send() {
        var value = ChannelList.parse(channels);
        if (channelField != null) channelField.setTextColor(value == null ? 0xff7777 : 0xffffff);
        if (value == null) return false;
        PacketDistributor.sendToServer(new NativeDriveBindings.Edit(menu.containerId, menu.root(), settings, value));
        return true;
    }

    private static int cycle(int value, int direction, int size) { return Math.floorMod(value + direction, size); }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        NativeDialogVisuals.panel(graphics, font, title, leftPos, topPos, imageWidth, imageHeight, DialogStyle.SYNTHESIS);
        if (picker != null) picker.render(graphics, x, y);
        if (page == 2 && menu.maxwell()) {
            graphics.drawString(font, "Fuel " + menu.fuel() + " / " + MaxwellEngine.CAPACITY + " mB",
                    leftPos + 12, topPos + 66, 0xabcfe8, false);
            graphics.drawString(font, "Throttle " + menu.throttle() + " / 15",
                    leftPos + 12, topPos + 84, 0xabcfe8, false);
        } else if (page == 2) graphics.drawString(font, menu.design().exhaust ? "Redstone controls the lights and exhaust."
                : "Redstone controls the lights.", leftPos + 12, topPos + 96, 0xabcfe8, false);
        if (page == 3) graphics.drawString(font, "Receive channels", leftPos + 12, topPos + 60, 0xb7cadb, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {}

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (picker != null && picker.click(x, y, button)) return true;
        if (button == 1) {
            for (var entry : java.util.List.copyOf(previous.entrySet())) {
                if (entry.getKey().isMouseOver(x, y)) {
                    entry.getValue().accept(-1);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return picker != null && picker.drag(x, y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (picker != null) picker.release();
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return picker != null && picker.wheel(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        return picker != null && picker.keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { if (send()) super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}

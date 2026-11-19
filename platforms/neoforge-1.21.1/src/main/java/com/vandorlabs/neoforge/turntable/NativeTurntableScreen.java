package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.turntable.TurntableSettings;
import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Interaction for the development capture and manual quarter-turn restoration. */
public final class NativeTurntableScreen extends AbstractContainerScreen<NativeTurntableMenu> {
    private NativeIntegerSlider height;
    private NativeIntegerSlider angle;
    private NativeIntegerSlider speed;
    private Button scan;
    private Button restore;
    private Button turn;
    private int heading;

    public NativeTurntableScreen(NativeTurntableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        imageWidth = Math.min(360, width - 12);
        imageHeight = 168;
        super.init();
        int x = leftPos + 12;
        height = addRenderableWidget(new NativeIntegerSlider(x, topPos + 28, imageWidth - 24, 20,
                TurntableSettings.MIN_HEIGHT, TurntableSettings.MAX_HEIGHT, menu.height(),
                h -> Component.literal("Height: " + h), h -> send(NativeTurntableMenu.HEIGHT + h)));
        speed = addRenderableWidget(new NativeIntegerSlider(x, topPos + 52, imageWidth - 24, 20,
                1, 20, (int) Math.round(menu.rpm() * 2),
                steps -> Component.literal("RPM: " + steps / 2D), steps -> send(NativeTurntableMenu.SPEED + steps)));
        angle = addRenderableWidget(new NativeIntegerSlider(x, topPos + 76, imageWidth - 24, 20,
                0, 3, heading, quarter -> Component.literal("Target angle: " + quarter * 90 + " degrees"),
                quarter -> heading = quarter));
        int button = (imageWidth - 36) / 4;
        scan = addRenderableWidget(NativeDialogButton.builder(Component.literal("Scan"), ignored -> send(NativeTurntableMenu.SCAN))
                .bounds(x, topPos + 136, button, 20).build());
        turn = addRenderableWidget(NativeDialogButton.builder(Component.literal("Turn"), ignored -> send(NativeTurntableMenu.TURN + heading))
                .bounds(x + button + 4, topPos + 136, button, 20).build());
        restore = addRenderableWidget(NativeDialogButton.builder(Component.literal("Restore"), ignored -> send(NativeTurntableMenu.RESTORE))
                .bounds(x + (button + 4) * 2, topPos + 136, button, 20).build());
        addRenderableWidget(NativeDialogButton.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(x + (button + 4) * 3, topPos + 136, button, 20).build());
        updateControls();
    }

    private void send(int value) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, value); }

    private void updateControls() {
        boolean idle = !menu.busy();
        speed.active = idle;
        height.active = scan.active = idle && !menu.captured();
        angle.active = turn.active = idle && menu.captured();
        restore.active = idle && menu.captured() && !menu.moving();
    }

    @Override protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        NativeDialogVisuals.panel(graphics, font, title, leftPos, topPos, imageWidth, imageHeight, DialogStyle.COPY);
        graphics.drawWordWrap(font, Component.literal(menu.status()), leftPos + 12, topPos + 102, imageWidth - 24, 0xffb9d5df);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return NativeIntegerSlider.dragFocused(getFocused(), x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        NativeIntegerSlider.releaseFocused(getFocused(), x, y, button);
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}

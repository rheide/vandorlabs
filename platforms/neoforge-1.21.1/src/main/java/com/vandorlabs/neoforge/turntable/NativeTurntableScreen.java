package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.client.NativeDialogVisuals;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableSettings;
import com.vandorlabs.ui.DialogStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Interaction for the development capture and manual quarter-turn restoration. */
public final class NativeTurntableScreen extends AbstractContainerScreen<NativeTurntableMenu> {
    private static final String[] LABELS = {"Scan", "Rotation", "Direction", "Restore"};
    private final EditBox[] channelFields = new EditBox[LABELS.length];
    private NativeIntegerSlider height;
    private NativeIntegerSlider angle;
    private NativeIntegerSlider speed;
    private Button scan;
    private Button restore;
    private Button turn;
    private int heading;
    private boolean signalPage;
    private int scanMode;
    private int rotationMode;
    private int snapSteps;
    private boolean clockwise;
    private Button scanChoice;
    private Button rotationChoice;
    private Button directionChoice;
    private Button saveSignals;

    public NativeTurntableScreen(NativeTurntableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        imageWidth = Math.min(360, width - 12);
        imageHeight = signalPage ? 218 : 168;
        super.init();
        if (signalPage) {
            initSignals();
            return;
        }
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
        int button = (imageWidth - 40) / 5;
        scan = addRenderableWidget(NativeDialogButton.builder(Component.literal("Scan"), ignored -> send(NativeTurntableMenu.SCAN))
                .bounds(x, topPos + 136, button, 20).build());
        turn = addRenderableWidget(NativeDialogButton.builder(Component.literal("Turn"), ignored -> send(NativeTurntableMenu.TURN + heading))
                .bounds(x + button + 4, topPos + 136, button, 20).build());
        restore = addRenderableWidget(NativeDialogButton.builder(Component.literal("Restore"), ignored -> send(NativeTurntableMenu.RESTORE))
                .bounds(x + (button + 4) * 2, topPos + 136, button, 20).build());
        addRenderableWidget(NativeDialogButton.builder(Component.literal("Redstone"), ignored -> showSignals(true))
                .bounds(x + (button + 4) * 3, topPos + 136, button, 20).build());
        addRenderableWidget(NativeDialogButton.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(x + (button + 4) * 4, topPos + 136, button, 20).build());
        updateControls();
    }

    private void showSignals(boolean show) {
        signalPage = show;
        rebuildWidgets();
    }

    private void initSignals() {
        var settings = menu.signalSettings();
        scanMode = settings.scanMode;
        rotationMode = settings.rotationMode;
        clockwise = settings.clockwise;
        snapSteps = (int) Math.round(settings.snapTolerance * 10);
        int x = leftPos + 12;
        int option = Math.min(83, (imageWidth - 106) / 2);
        int inputX = x + 57;
        int inputWidth = imageWidth - 24 - 57 - option - 4;
        var banks = new ChannelList[]{settings.scanChannels, settings.rotationChannels,
                settings.directionChannels, settings.restoreChannels};
        for (int i = 0; i < banks.length; i++) {
            var input = new EditBox(font, inputX, topPos + 30 + i * 25, inputWidth, 18,
                    Component.literal(LABELS[i] + " channels"));
            input.setMaxLength(ChannelList.MAX_TEXT_LENGTH);
            input.setValue(banks[i].toString());
            input.setResponder(value -> {
                input.setTextColor(ChannelList.parse(value) == null ? 0xff7777 : 0xffffff);
                updateControls();
            });
            channelFields[i] = addRenderableWidget(input);
        }
        int optionsX = inputX + inputWidth + 4;
        scanChoice = addRenderableWidget(new NativeDialogButton(optionsX, topPos + 29, option, 20,
                modeLabel(scanMode), ignored -> changeMode(true, 1)));
        rotationChoice = addRenderableWidget(new NativeDialogButton(optionsX, topPos + 54, option, 20,
                modeLabel(rotationMode), ignored -> changeMode(false, 1)));
        directionChoice = addRenderableWidget(new NativeDialogButton(optionsX, topPos + 79, option, 20,
                directionLabel(), ignored -> changeDirection()));
        addRenderableWidget(new NativeIntegerSlider(x, topPos + 134, imageWidth - 24, 20, 0, 50, snapSteps,
                steps -> Component.literal("Auto-snap: " + steps / 10D + " degrees"), steps -> snapSteps = steps));
        int button = (imageWidth - 28) / 2;
        addRenderableWidget(new NativeDialogButton(x, topPos + 185, button, 20,
                Component.literal("Back"), ignored -> showSignals(false)));
        saveSignals = addRenderableWidget(new NativeDialogButton(x + button + 4, topPos + 185, button, 20,
                Component.literal("Save Redstone"), ignored -> saveSignals()));
        updateControls();
    }

    private static Component modeLabel(int mode) {
        return Component.literal(switch (mode) {
            case TurntableSettings.SIGNAL_ON -> "Signal ON";
            case TurntableSettings.SIGNAL_OFF -> "Signal OFF";
            default -> "Disabled";
        });
    }

    private Component directionLabel() {
        return Component.literal(clockwise ? "Clockwise" : "Counterclockwise");
    }

    private void changeMode(boolean scan, int step) {
        if (scan) {
            scanMode = Math.floorMod(scanMode + step, 3);
            scanChoice.setMessage(modeLabel(scanMode));
        } else {
            rotationMode = 1 + Math.floorMod(rotationMode - 1 + step, 2);
            rotationChoice.setMessage(modeLabel(rotationMode));
        }
    }

    private void changeDirection() {
        clockwise = !clockwise;
        directionChoice.setMessage(directionLabel());
    }

    private void saveSignals() {
        var banks = new ChannelList[channelFields.length];
        for (int i = 0; i < banks.length; i++) {
            banks[i] = ChannelList.parse(channelFields[i].getValue());
            if (banks[i] == null) return;
        }
        PacketDistributor.sendToServer(new NativeTurntableSignalsPayload(menu.containerId, scanMode, rotationMode,
                snapSteps, clockwise, banks[0], banks[1], banks[2], banks[3]));
        showSignals(false);
    }

    private void send(int value) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, value); }

    private void updateControls() {
        boolean idle = !menu.busy();
        if (signalPage) {
            if (saveSignals != null) {
                for (var input : channelFields) idle &= input != null && ChannelList.parse(input.getValue()) != null;
                saveSignals.active = idle;
            }
            return;
        }
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
        if (signalPage) {
            for (int i = 0; i < LABELS.length; i++) {
                graphics.drawString(font, LABELS[i], leftPos + 12, topPos + 35 + i * 25, 0xffb9d5df);
            }
            graphics.drawWordWrap(font, Component.literal("Empty Scan and Rotation use adjacent redstone."),
                    leftPos + 12, topPos + 161, imageWidth - 24, 0xffb9d5df);
        } else {
            graphics.drawWordWrap(font, Component.literal(menu.status()), leftPos + 12, topPos + 102, imageWidth - 24, 0xffb9d5df);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (signalPage && button == 1) {
            if (scanChoice.isMouseOver(x, y)) {
                changeMode(true, -1);
                return true;
            }
            if (rotationChoice.isMouseOver(x, y)) {
                changeMode(false, -1);
                return true;
            }
            if (directionChoice.isMouseOver(x, y)) {
                changeDirection();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return NativeIntegerSlider.dragFocused(getFocused(), x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        NativeIntegerSlider.releaseFocused(getFocused(), x, y, button);
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean isPauseScreen() { return false; }
}

package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTurntableController;
import com.vandorlabs.network.MessageTurntableController;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Configuration dialog for a Turntable Controller. */
public class GuiTurntableController extends GuiContainer {
    private final TileEntityTurntableController controller;
    private GuiTextField heightField, speedField, channelField;
    private int trigger, direction, lastUpdate;
    private int lastHeight;
    private int lastSpeed;

    public GuiTurntableController(TileEntityTurntableController controller) {
        super(new ContainerTurntableController(controller));
        this.controller = controller;
        trigger = controller.trigger;
        direction = controller.rotationDirection;
        lastHeight = controller.height;
        lastSpeed = (int) Math.round(controller.speed);
        lastUpdate = controller.clientUpdates;
        xSize = 260;
        ySize = 216;
    }

    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        buttonList.add(new GuiButton(1, guiLeft + 12, guiTop + 30, 236, 20, ""));
        buttonList.add(new GuiButton(2, guiLeft + 12, guiTop + 54, 20, 20, "-"));
        heightField = new GuiTextField(10, fontRenderer, guiLeft + 36, guiTop + 55, 40, 18);
        heightField.setMaxStringLength(2);
        heightField.setValidator(text -> text.matches("[0-9]{0,2}"));
        heightField.setText(Integer.toString(controller.height));
        buttonList.add(new GuiButton(3, guiLeft + 80, guiTop + 54, 20, 20, "+"));
        buttonList.add(new GuiButton(4, guiLeft + 12, guiTop + 78, 20, 20, "-"));
        speedField = new GuiTextField(11, fontRenderer, guiLeft + 36, guiTop + 79, 40, 18);
        speedField.setMaxStringLength(3);
        speedField.setValidator(text -> text.matches("[0-9]{0,3}"));
        speedField.setText(Integer.toString((int) Math.round(controller.speed)));
        buttonList.add(new GuiButton(5, guiLeft + 80, guiTop + 78, 20, 20, "+"));
        buttonList.add(new GuiButton(6, guiLeft + 12, guiTop + 102, 236, 20, ""));
        buttonList.add(new GuiButton(8, guiLeft + 12, guiTop + 126, 236, 20, "Scan turntable"));
        channelField = new GuiTextField(12, fontRenderer, guiLeft + 74, guiTop + 147, 174, 18);
        ChannelFields.configure(channelField);
        channelField.setText(controller.getRedstoneChannels().toString());
        buttonList.add(new GuiButton(7, guiLeft + 12, guiTop + 172, 236, 20, "Done"));
        refresh();
    }

    private void refresh() {
        for (GuiButton button : buttonList) {
            if (button.id == 1) button.displayString = "Trigger: "
                    + new String[] { "Disabled", "Redstone ON", "Redstone OFF" }[trigger];
            if (button.id == 6) button.displayString = "Rotation: "
                    + (direction == TurntableGeometry.COUNTER_CLOCKWISE ? "Counter-clockwise" : "Clockwise");
            if (button.id == 7) button.displayString = "Done";
        }
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) trigger = GuiOptionCycle.next(trigger, 3);
        if (button.id == 6) direction = GuiOptionCycle.next(direction, 2);
        if (button.id == 2 || button.id == 3) {
            int value = parse(heightField, controller.height);
            value += button.id == 3 ? 1 : -1;
            heightField.setText(Integer.toString(TurntableGeometry.clampHeight(value)));
        }
        if (button.id == 4 || button.id == 5) {
            int value = parse(speedField, (int) Math.round(controller.speed));
            value += button.id == 5 ? 15 : -15;
            speedField.setText(Integer.toString((int) TurntableGeometry.clampSpeed(value)));
        }
        if (button.id == 8) {
            PacketHandler.INSTANCE.sendToServer(MessageTurntableController.scan(controller.getPos()));
            refresh();
            return;
        }
        if (button.id == 7) { mc.player.closeScreen(); return; }
        submit();
        refresh();
    }

    private static int parse(GuiTextField field, int fallback) {
        try { return Integer.parseInt(field.getText()); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    private void submit() {
        int height = TurntableGeometry.clampHeight(parse(heightField, controller.height));
        int speed = (int) TurntableGeometry.clampSpeed(parse(speedField, (int) Math.round(controller.speed)));
        lastHeight = height;
        lastSpeed = speed;
        com.vandorlabs.redstone.ChannelList channels = ChannelFields.parse(channelField);
        if (channels == null) return;
        PacketHandler.INSTANCE.sendToServer(MessageTurntableController.apply(controller.getPos(), trigger,
                height, speed, direction, channels));
    }

    @Override public void updateScreen() {
        super.updateScreen();
        heightField.updateCursorCounter();
        speedField.updateCursorCounter();
        channelField.updateCursorCounter();
        if (lastUpdate != controller.clientUpdates) {
            lastUpdate = controller.clientUpdates;
            trigger = controller.trigger;
            direction = controller.rotationDirection;
            lastHeight = controller.height;
            lastSpeed = (int) Math.round(controller.speed);
            heightField.setText(Integer.toString(controller.height));
            speedField.setText(Integer.toString((int) Math.round(controller.speed)));
            channelField.setText(controller.getRedstoneChannels().toString());
            refresh();
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { super.keyTyped(typedChar, keyCode); return; }
        String heightBefore = heightField.getText(), speedBefore = speedField.getText(),
                channelBefore = channelField.getText();
        if (!heightField.textboxKeyTyped(typedChar, keyCode)
                && !speedField.textboxKeyTyped(typedChar, keyCode)
                && !channelField.textboxKeyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        if (!heightBefore.equals(heightField.getText())
                || !speedBefore.equals(speedField.getText())
                || !channelBefore.equals(channelField.getText())) submit();
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (GuiOptionCycle.rightClick(mc, buttonList, x, y, button, this::actionPerformed, 1, 6)) return;
        super.mouseClicked(x, y, button);
        heightField.mouseClicked(x, y, button);
        speedField.mouseClicked(x, y, button);
        channelField.mouseClicked(x, y, button);
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF19232C);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 28, 0xFF304858);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString("Turntable Controller", 12, 10, 0xFFFFFF);
        fontRenderer.drawString("Height (1-16)", 112, 59, 0xDAE8F0);
        fontRenderer.drawString("Speed (\u00B0/s)", 112, 83, 0xDAE8F0);
        fontRenderer.drawString("Channels", 12, 151, 0xDAE8F0);
        String detail = controller.isValidTurntable()
                ? "Turntable " + controller.size() + "x" + controller.size()
                        + (controller.isActive() ? " turning" : " ready")
                : "Scan to verify the square";
        fontRenderer.drawString(detail, 112, 105, 0xADBECA);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        heightField.drawTextBox();
        speedField.drawTextBox();
        channelField.drawTextBox();
        String status = (controller.error ? "Error: " : "") + controller.status;
        fontRenderer.drawSplitString(status, guiLeft + 12, guiTop + 197, xSize - 24,
                controller.error ? 0xFF9988 : 0xE5C76B);
    }
}

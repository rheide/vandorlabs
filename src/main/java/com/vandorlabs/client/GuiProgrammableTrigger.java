package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageProgrammableTrigger;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Two housing finish lists, selected by the trigger's redstone state. */
public final class GuiProgrammableTrigger extends GuiContainer {
    private final TileEntityProgrammableTrigger tile;
    private HousingTextureList offList;
    private HousingTextureList onList;
    private GuiTextField channelField;
    private int off;
    private int on;

    public GuiProgrammableTrigger(InventoryPlayer inventory,
            TileEntityProgrammableTrigger tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        off = tile.getHousingTexture();
        on = tile.getOnTexture();
        xSize = 380;
        ySize = 196;
    }

    @Override public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        offList = new HousingTextureList(guiLeft + 12, guiTop + 41, 160, off);
        onList = new HousingTextureList(guiLeft + 200, guiTop + 41, 160, on);
        channelField = new GuiTextField(0, fontRenderer, guiLeft + 132,
                guiTop + 144, 110, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(value -> value.isEmpty() || value.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(tile.getRedstoneChannel()));
        buttonList.add(new GuiButton(100, guiLeft + 14, guiTop + 169,
                xSize - 28, 20, "Done"));
    }

    private int channel() {
        try {
            long value = Long.parseLong(channelField.getText());
            return value <= Integer.MAX_VALUE ? (int) value : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private void send() {
        int value = channel();
        if (value < 0) return;
        tile.configure(off, on, value);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrigger(
                tile.getPos(), off, on, value));
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (offList.click(x, y, button)) {
            if (off != offList.selected()) { off = offList.selected(); send(); }
            return;
        }
        if (onList.click(x, y, button)) {
            if (on != onList.selected()) { on = onList.selected(); send(); }
            return;
        }
        super.mouseClicked(x, y, button);
        channelField.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (offList.drag(y) || onList.drag(y)) return;
        super.mouseClickMove(x, y, button, elapsed);
    }

    @Override protected void mouseReleased(int x, int y, int button) {
        offList.release();
        onList.release();
        super.mouseReleased(x, y, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        offList.wheel(x, y, wheel);
        onList.wheel(x, y, wheel);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 100) { send(); mc.player.closeScreen(); }
    }

    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            send(); mc.player.closeScreen(); return;
        }
        if (channelField.textboxKeyTyped(typed, key)) send();
        else super.keyTyped(typed, key);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        channelField.updateCursorCounter();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX,
            int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 18, 0xFF202028);
        fontRenderer.drawString("Programmable Trigger Block", guiLeft + 8,
                guiTop + 5, 0xFFFFFFFF);
        fontRenderer.drawString("Redstone Off", guiLeft + 12, guiTop + 27, 0xFFD8D8D8);
        fontRenderer.drawString("Redstone On", guiLeft + 200, guiTop + 27, 0xFFD8D8D8);
        offList.draw(fontRenderer, mouseX, mouseY);
        onList.draw(fontRenderer, mouseX, mouseY);
        fontRenderer.drawString("Redstone Channel", guiLeft + 12,
                guiTop + 149, 0xFFD8D8D8);
        super.drawScreen(mouseX, mouseY, partial);
        channelField.drawTextBox();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}

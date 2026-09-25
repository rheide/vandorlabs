package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Two peer input lists for the half-console incline and flat deck. */
public class GuiProgrammableHalfConsole extends GuiContainer {

    private static final int ROW_H = 12;
    private static final int ROWS = 8;
    private final TileEntityAnimatedScreenSelector te;
    private String topPanel;
    private String bottomPanel;
    private int topScroll;
    private int bottomScroll;
    private int draggingList;
    private int scrollbarDragOffset;
    private int left;
    private int right;
    private int top;
    private int displayMode;
    private int speedIndex;
    private boolean redstoneEnabled;
    private int housingTexture;
    private GuiTextField channelField;
    private GuiButton housingButton;

    public GuiProgrammableHalfConsole(InventoryPlayer inventory,
            TileEntityAnimatedScreenSelector te) {
        super(new ContainerAnimatedScreenSelector(inventory, te));
        this.te = te;
        topPanel = te.getSecondaryInputPanel();
        bottomPanel = te.getInputPanel();
        displayMode = te.getDisplayMode();
        speedIndex = te.getAnimationSpeedIndex();
        redstoneEnabled = te.isRedstoneEnabled();
        housingTexture = te.getHousingTexture();
        xSize = 420;
        ySize = 268;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        left = x + 8;
        right = x + 172;
        top = y + 34;
        buttonList.add(new GuiButton(1, x + 8, y + 146, 84, 18,
                I18n.format("gui.vandorlabs.selector.off")));
        buttonList.add(new GuiButton(2, x + 98, y + 146, 84, 18,
                I18n.format("gui.vandorlabs.selector.static")));
        buttonList.add(new GuiButton(3, x + 188, y + 146, 84, 18,
                I18n.format("gui.vandorlabs.selector.animated")));
        buttonList.add(new GuiButton(10, x + 8, y + 174, 100, 18,
                I18n.format("gui.vandorlabs.selector.slow")));
        buttonList.add(new GuiButton(11, x + 115, y + 174, 100, 18,
                I18n.format("gui.vandorlabs.selector.normal")));
        buttonList.add(new GuiButton(12, x + 222, y + 174, 100, 18,
                I18n.format("gui.vandorlabs.selector.fast")));
        buttonList.add(new GuiButton(0, x + 278, y + 146, 134, 18, ""));
        channelField = new GuiTextField(40,fontRenderer,x+338,y+174,74,18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(te.getRedstoneChannel()));
        housingButton = new GuiButton(50, x + 8, y + 208, xSize - 16, 20, "");
        buttonList.add(housingButton);
        buttonList.add(new GuiButton(20, x + (xSize - 200) / 2, y + 242, 200, 20,
                I18n.format("gui.done")));
        topScroll = reveal(topPanel);
        bottomScroll = reveal(bottomPanel);
        refreshButtons();
    }

    private void refreshButtons() {
        for (GuiButton button : buttonList) {
            if (button.id == 0) {
                button.displayString = I18n.format("gui.vandorlabs.selector.redstone") + ": "
                        + I18n.format(redstoneEnabled
                                ? "gui.vandorlabs.selector.on"
                                : "gui.vandorlabs.selector.off_state");
            }
            if (button.id >= 1 && button.id <= 3) button.enabled = displayMode != button.id - 1;
            if (button.id >= 10 && button.id <= 12) button.enabled = speedIndex != button.id - 10;
            if (button.id == 50) button.displayString =
                    I18n.format("gui.vandorlabs.selector.housing") + ": "
                    + I18n.format("tile.vandorlabs."
                            + ScreenHousingTextures.IDS[housingTexture] + ".name");
        }
    }

    private int maxScroll() {
        return Math.max(0, TileEntityAnimatedScreenSelector.INPUT_PANELS.length - ROWS);
    }

    private int reveal(String id) {
        for (int i = 0; i < TileEntityAnimatedScreenSelector.INPUT_PANELS.length; i++) {
            if (id.equals(TileEntityAnimatedScreenSelector.INPUT_PANELS[i])) {
                return Math.max(0, Math.min(i, maxScroll()));
            }
        }
        return 0;
    }

    private void sendUpdate() {
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(te.getPos(),
                te.getSelectedScreen(), redstoneEnabled, displayMode,
                te.isFramed(), speedIndex, bottomPanel, topPanel, false, channel(),
                housingTexture));
    }

    private int channel() {
        try { long value=Long.parseLong(channelField.getText()); return value<=Integer.MAX_VALUE?(int)value:-1; }
        catch (NumberFormatException e) { return -1; }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 1 && housingButton.mousePressed(mc, mouseX, mouseY)) {
            housingButton.playPressSound(mc.getSoundHandler());
            housingTexture = ScreenHousingTextures.cycle(housingTexture, -1);
            refreshButtons();
            sendUpdate();
            return;
        }
        if (button == 0 && mouseY >= top && mouseY < top + ROW_H * ROWS) {
            int list = mouseX >= left + 144 && mouseX < left + 150 ? 1
                    : mouseX >= right + 144 && mouseX < right + 150 ? 2 : 0;
            if (list != 0 && maxScroll() > 0) {
                int scroll = list == 1 ? bottomScroll : topScroll;
                int thumbY = scrollbarThumbY(scroll);
                int thumbH = scrollbarThumbHeight();
                scrollbarDragOffset = mouseY >= thumbY && mouseY < thumbY + thumbH
                        ? mouseY - thumbY : thumbH / 2;
                draggingList = list;
                dragScrollbarTo(mouseY);
                return;
            }
        }
        if (button == 0 && mouseY >= top && mouseY < top + ROW_H * ROWS) {
            boolean first = mouseX >= left && mouseX < left + 142;
            boolean second = mouseX >= right && mouseX < right + 142;
            if (first || second) {
                int index = (first ? bottomScroll : topScroll) + (mouseY - top) / ROW_H;
                if (index < TileEntityAnimatedScreenSelector.INPUT_PANELS.length) {
                    if (first) bottomPanel = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
                    else topPanel = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
                    sendUpdate();
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX,mouseY,button);
    }

    @Override protected void mouseReleased(int mouseX,int mouseY,int state) {
        draggingList = 0;
        super.mouseReleased(mouseX,mouseY,state);
    }

    @Override protected void mouseClickMove(int mouseX,int mouseY,int button,long elapsed) {
        if (draggingList != 0) dragScrollbarTo(mouseY);
        else super.mouseClickMove(mouseX,mouseY,button,elapsed);
    }

    private void dragScrollbarTo(int mouseY) {
        int scroll = GuiProgrammableInput.scrollForDrag(mouseY, top, ROW_H * ROWS,
                scrollbarThumbHeight(), maxScroll(), scrollbarDragOffset);
        if (draggingList == 1) bottomScroll = scroll;
        else if (draggingList == 2) topScroll = scroll;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 20) { if (channel()>=0) sendUpdate(); mc.player.closeScreen(); return; }
        if (button.id == 0) redstoneEnabled = !redstoneEnabled;
        else if (button.id >= 1 && button.id <= 3) displayMode = button.id - 1;
        else if (button.id >= 10 && button.id <= 12) speedIndex = button.id - 10;
        else if (button.id == 50)
            housingTexture = ScreenHousingTextures.cycle(housingTexture, 1);
        else return;
        refreshButtons();
        sendUpdate();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int delta = wheel > 0 ? -1 : 1;
            if (mouseX >= left && mouseX < left + 150)
                bottomScroll = clamp(bottomScroll + delta);
            else if (mouseX >= right && mouseX < right + 150)
                topScroll = clamp(topScroll + delta);
        }
    }

    private int clamp(int value) { return Math.max(0, Math.min(maxScroll(), value)); }

    private int scrollbarThumbHeight() {
        return Math.max(8, ROW_H * ROWS * ROWS
                / TileEntityAnimatedScreenSelector.INPUT_PANELS.length);
    }

    private int scrollbarThumbY(int scroll) {
        return top + (ROW_H * ROWS - scrollbarThumbHeight()) * scroll
                / Math.max(1,maxScroll());
    }

    private void drawList(int x, int scroll, String selected) {
        drawRect(x - 1, top - 1, x + 151, top + ROW_H * ROWS + 1, 0xFF000000);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= TileEntityAnimatedScreenSelector.INPUT_PANELS.length) break;
            String id = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
            int yy = top + row * ROW_H;
            if (id.equals(selected)) drawRect(x, yy, x + 142, yy + ROW_H, 0xFF2A4A6A);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(
                    I18n.format("gui.vandorlabs.console.input." + id), 136),
                    x + 3, yy + 2, id.equals(selected) ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        int thumbH = scrollbarThumbHeight();
        int thumbY = scrollbarThumbY(scroll);
        drawRect(x + 144, top, x + 150, top + ROW_H * ROWS, 0xFF303038);
        drawRect(x + 144, thumbY, x + 150, thumbY + thumbH, 0xFF808090);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawRect(x, y, x + xSize, y + ySize, 0xFF101012);
        drawRect(x, y, x + xSize, y + 16, 0xFF202028);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.title"),
                x + 8, y + 5, 0xFFFFFFFF);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.bottom"),
                left, y + 22, 0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.top"),
                right, y + 22, 0xFFA0A0A8);
        drawList(left, bottomScroll, bottomPanel);
        drawList(right, topScroll, topPanel);
        int previewX = x + 334;
        String suffix = displayMode == TileEntityAnimatedScreenSelector.MODE_OFF
                ? "_off.png" : "_static.png";
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.top"),
                previewX, y + 22, 0xFFA0A0A8);
        drawRect(previewX - 1, y + 33, previewX + 79, y + 74, 0xFF000000);
        mc.getTextureManager().bindTexture(new ResourceLocation("vandorlabs",
                "textures/blocks/console_inputs/" + topPanel + suffix));
        GlStateManager.color(1, 1, 1, 1);
        drawScaledCustomSizeModalRect(previewX, y + 34, 0, 0, 512, 256,
                78, 39, 512, 256);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.half_console.bottom"),
                previewX, y + 82, 0xFFA0A0A8);
        drawRect(previewX - 1, y + 93, previewX + 79, y + 134, 0xFF000000);
        mc.getTextureManager().bindTexture(new ResourceLocation("vandorlabs",
                "textures/blocks/console_inputs/" + bottomPanel + suffix));
        GlStateManager.color(1, 1, 1, 1);
        drawScaledCustomSizeModalRect(previewX, y + 94, 0, 0, 512, 256,
                78, 39, 512, 256);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),
                left, y + 136, 0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),
                left, y + 164, 0xFFA0A0A8);
        fontRenderer.drawString("Channel", x + 338, y + 164, 0xFFA0A0A8);
        super.drawScreen(mouseX, mouseY, partialTicks);
        channelField.drawTextBox();
    }

    @Override protected void keyTyped(char c,int key) throws IOException {
        if (channelField.textboxKeyTyped(c,key)) { if (channel()>=0) sendUpdate(); }
        else super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override protected void drawGuiContainerBackgroundLayer(float p, int x, int y) {}
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {}
    @Override public boolean doesGuiPauseGame() { return false; }
}

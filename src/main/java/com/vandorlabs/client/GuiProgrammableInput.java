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

/** Compact selector containing only the input-surface list and preview. */
public class GuiProgrammableInput extends GuiContainer {

    private static final int ROW_H = 12;
    private static final int ROWS = 8;
    private final TileEntityAnimatedScreenSelector te;
    private String selected;
    private int scroll;
    private int listX;
    private int listY;
    private int displayMode;
    private int speedIndex;
    private boolean redstoneEnabled;
    private boolean smallInput;
    private int housingTexture;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private GuiTextField channelField;
    private HousingTextureList housingList;
    private boolean housingOpen;

    public GuiProgrammableInput(InventoryPlayer inventory,
            TileEntityAnimatedScreenSelector te) {
        super(new ContainerAnimatedScreenSelector(inventory, te));
        this.te = te;
        this.selected = te.getInputPanel();
        this.displayMode = te.getDisplayMode();
        this.speedIndex = te.getAnimationSpeedIndex();
        this.redstoneEnabled = te.isRedstoneEnabled();
        this.smallInput = te.isSmallInput();
        this.housingTexture = te.getHousingTexture();
        this.xSize = 250;
        this.ySize = 260;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        listX = x + 8;
        listY = y + 22;
        buttonList.add(new GuiButton(1, x + 8, y + 124, 74, 18,
                I18n.format("gui.vandorlabs.selector.off")));
        buttonList.add(new GuiButton(2, x + 88, y + 124, 74, 18,
                I18n.format("gui.vandorlabs.selector.static")));
        buttonList.add(new GuiButton(3, x + 168, y + 124, 74, 18,
                I18n.format("gui.vandorlabs.selector.animated")));
        buttonList.add(new GuiButton(10, x + 8, y + 150, 74, 18,
                I18n.format("gui.vandorlabs.selector.slow")));
        buttonList.add(new GuiButton(11, x + 88, y + 150, 74, 18,
                I18n.format("gui.vandorlabs.selector.normal")));
        buttonList.add(new GuiButton(12, x + 168, y + 150, 74, 18,
                I18n.format("gui.vandorlabs.selector.fast")));
        buttonList.add(new GuiButton(30, x + 8, y + 178, 114, 18,
                I18n.format("gui.vandorlabs.input.small")));
        buttonList.add(new GuiButton(31, x + 128, y + 178, 114, 18,
                I18n.format("gui.vandorlabs.selector.normal")));
        buttonList.add(new GuiButton(0, x + 8, y + 202, 234, 18, ""));
        channelField = new GuiTextField(40, fontRenderer, x + 168, y + 92, 74, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(te.getRedstoneChannel()));
        housingList = new HousingTextureList(x + 8, y + 22, 226,
                housingTexture);
        buttonList.add(new GuiButton(21, x + 8, y + 228, 114, 20,
                I18n.format("gui.vandorlabs.selector.housing")));
        buttonList.add(new GuiButton(20, x + 128, y + 228, 114, 20,
                I18n.format("gui.done")));
        revealSelection();
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
            if (button.id == 30) button.enabled = !smallInput;
            if (button.id == 31) button.enabled = smallInput;
        }
    }

    private int maxScroll() {
        return Math.max(0, TileEntityAnimatedScreenSelector.INPUT_PANELS.length - ROWS);
    }

    private int scrollbarThumbHeight() {
        return Math.max(8, ROW_H * ROWS * ROWS
                / TileEntityAnimatedScreenSelector.INPUT_PANELS.length);
    }

    private int scrollbarThumbY() {
        return listY + (ROW_H * ROWS - scrollbarThumbHeight()) * scroll / maxScroll();
    }

    private void revealSelection() {
        for (int i = 0; i < TileEntityAnimatedScreenSelector.INPUT_PANELS.length; i++) {
            if (selected.equals(TileEntityAnimatedScreenSelector.INPUT_PANELS[i])) {
                scroll = Math.max(0, Math.min(i, maxScroll()));
                return;
            }
        }
    }

    private void sendUpdate() {
        PacketHandler.INSTANCE.sendToServer(new MessageSyncScreenSelector(te.getPos(),
                te.getSelectedScreen(), redstoneEnabled, displayMode,
                te.isFramed(), speedIndex, selected, selected, smallInput, channel(),
                housingTexture));
    }

    private int channel() {
        try { long value=Long.parseLong(channelField.getText()); return value<=Integer.MAX_VALUE?(int)value:-1; }
        catch (NumberFormatException e) { return -1; }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (housingOpen && housingList.click(mouseX, mouseY, button)) {
            if (housingTexture != housingList.selected()) {
                housingTexture = housingList.selected();
                sendUpdate();
            }
            return;
        }
        if (housingOpen) { housingOpen = false; return; }
        if (button == 0 && maxScroll() > 0 && mouseX >= listX + 144
                && mouseX < listX + 150 && mouseY >= listY
                && mouseY < listY + ROW_H * ROWS) {
            int thumbY = scrollbarThumbY();
            int thumbH = scrollbarThumbHeight();
            scrollbarDragOffset = mouseY >= thumbY && mouseY < thumbY + thumbH
                    ? mouseY - thumbY : thumbH / 2;
            draggingScrollbar = true;
            dragScrollbarTo(mouseY);
            return;
        }
        if (button == 0 && mouseX >= listX && mouseX < listX + 142
                && mouseY >= listY && mouseY < listY + ROW_H * ROWS) {
            int index = scroll + (mouseY - listY) / ROW_H;
            if (index < TileEntityAnimatedScreenSelector.INPUT_PANELS.length) {
                selected = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
                sendUpdate();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScrollbar = false;
        if (housingList != null) housingList.release();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
            long timeSinceLastClick) {
        if (housingList != null && housingList.drag(mouseY)) return;
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
        } else {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    private void dragScrollbarTo(int mouseY) {
        scroll = scrollForDrag(mouseY, listY, ROW_H * ROWS,
                scrollbarThumbHeight(), maxScroll(), scrollbarDragOffset);
    }

    static int scrollForDrag(int mouseY, int trackTop, int trackHeight,
            int thumbHeight, int maximum, int dragOffset) {
        int travel = trackHeight - thumbHeight;
        if (maximum <= 0 || travel <= 0) {
            return 0;
        }
        int thumbTop = Math.max(0, Math.min(travel,
                mouseY - trackTop - dragOffset));
        return Math.round((float) thumbTop * maximum / travel);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 20) { if (channel()>=0) sendUpdate(); mc.player.closeScreen(); return; }
        if (button.id == 21) { housingOpen = !housingOpen; return; }
        if (button.id == 0) redstoneEnabled = !redstoneEnabled;
        else if (button.id >= 1 && button.id <= 3) displayMode = button.id - 1;
        else if (button.id >= 10 && button.id <= 12) speedIndex = button.id - 10;
        else if (button.id == 30) smallInput = true;
        else if (button.id == 31) smallInput = false;
        else return;
        refreshButtons();
        sendUpdate();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (housingOpen && housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel)) return;
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawRect(x, y, x + xSize, y + ySize, 0xFF101012);
        drawRect(x, y, x + xSize, y + 16, 0xFF202028);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.input.title"), x + 8, y + 5,
                0xFFFFFFFF);
        drawRect(listX - 1, listY - 1, listX + 151, listY + ROW_H * ROWS + 1,
                0xFF000000);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= TileEntityAnimatedScreenSelector.INPUT_PANELS.length) break;
            String id = TileEntityAnimatedScreenSelector.INPUT_PANELS[index];
            int yy = listY + row * ROW_H;
            if (id.equals(selected)) drawRect(listX, yy, listX + 142, yy + ROW_H,
                    0xFF2A4A6A);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(
                    I18n.format("gui.vandorlabs.console.input." + id), 136),
                    listX + 3, yy + 2, id.equals(selected) ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        if (maxScroll() > 0) {
            int thumbH = scrollbarThumbHeight();
            int thumbY = scrollbarThumbY();
            drawRect(listX + 144, listY, listX + 150, listY + ROW_H * ROWS, 0xFF303038);
            drawRect(listX + 144, thumbY, listX + 150, thumbY + thumbH, 0xFF808090);
        }
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.preview"),
                x + 164, y + 22, 0xFFA0A0A8);
        mc.getTextureManager().bindTexture(new ResourceLocation("vandorlabs",
                "textures/blocks/console_inputs/" + selected
                        + (displayMode == TileEntityAnimatedScreenSelector.MODE_OFF
                                ? "_off.png" : "_static.png")));
        GlStateManager.color(1, 1, 1, 1);
        int previewW = smallInput ? 55 : 78;
        int previewH = smallInput ? 27 : 39;
        drawScaledCustomSizeModalRect(x + 164 + (78 - previewW) / 2,
                y + 38 + (39 - previewH) / 2, 0, 0, 512, 256,
                previewW, previewH, 512, 256);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.display"),
                x + 8, y + 114, 0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.speed"),
                x + 8, y + 142, 0xFFA0A0A8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.input.size"),
                x + 8, y + 170, 0xFFA0A0A8);
        fontRenderer.drawString("Channel", x + 168, y + 80, 0xFFA0A0A8);
        super.drawScreen(mouseX, mouseY, partialTicks);
        channelField.drawTextBox();
        if (housingOpen) housingList.draw(fontRenderer, mouseX, mouseY);
    }

    @Override protected void keyTyped(char c,int key) throws IOException {
        if (channelField.textboxKeyTyped(c,key)) { if (channel()>=0) sendUpdate(); }
        else super.keyTyped(c,key);
    }
    @Override public void updateScreen() { super.updateScreen(); channelField.updateCursorCounter(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    public boolean doesGuiPauseGame() { return false; }
}

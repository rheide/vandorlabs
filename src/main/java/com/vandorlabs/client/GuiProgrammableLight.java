package com.vandorlabs.client;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageProgrammableLight;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Texture picker with a live preview and a 0 to 15 light level slider. */
public final class GuiProgrammableLight extends GuiContainer {
    private final TileEntityProgrammableLight tile;
    private int selected;
    private int level;
    private boolean join;
    private int housing;
    private HousingTextureList housingList;
    private GuiTextField channelField;
    private boolean draggingLevel;
    private static final int SLIDER_W = 286;

    public GuiProgrammableLight(InventoryPlayer inventory, TileEntityProgrammableLight tile) {
        super(new ContainerAnimatedScreenSelector(inventory, tile));
        this.tile = tile;
        selected = tile.getTexture();
        level = tile.getLightLevel();
        join = tile.isJoin();
        housing = tile.getHousingTexture();
        xSize = 420;
        ySize = 240;
    }

    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        channelField = new GuiTextField(0, fontRenderer, guiLeft + 150,
                guiTop + 188, 164, 18);
        channelField.setMaxStringLength(10);
        channelField.setValidator(text -> text.isEmpty() || text.matches("[0-9]{1,10}"));
        channelField.setText(Integer.toString(tile.getRedstoneChannel()));
        housingList = new HousingTextureList(guiLeft + 252, guiTop + 40, 148, housing);
        buttonList.add(new GuiButton(101, guiLeft + 12, guiTop + 214,
                196, 20, joinLabel()));
        buttonList.add(new GuiButton(100, guiLeft + 212, guiTop + 214,
                196, 20, I18n.format("gui.done")));
    }

    private String joinLabel() {
        return I18n.format("gui.vandorlabs.light.join") + ": "
                + (join ? I18n.format("options.on") : I18n.format("options.off"));
    }

    private int channel() {
        try {
            long value = Long.parseLong(channelField.getText());
            return value <= Integer.MAX_VALUE ? (int) value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void send() {
        int channel = channel();
        if (channel < 0) return;
        tile.configure(selected, level, join, channel, housing);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableLight(
                tile.getPos(), selected, level, join, channel, housing));
    }

    private void setLevelFromMouse(int mouseX) {
        int next = Math.max(0, Math.min(15,
                Math.round(15F * (mouseX - guiLeft - 16) / SLIDER_W)));
        if (next != level) {
            level = next;
            send();
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        if (housingList.click(mouseX, mouseY, button)) {
            if (housing != housingList.selected()) {
                housing = housingList.selected();
                send();
            }
            return;
        }
        if (button == 0) {
            int x = guiLeft + 12;
            int y = guiTop + 27;
            if (mouseX >= x && mouseX < x + 140
                    && mouseY >= y && mouseY < y + ProgrammableLightTextures.IDS.length * 20) {
                int choice = (mouseY - y) / 20;
                if (choice != selected) {
                    selected = choice;
                    send();
                }
                return;
            }
            if (mouseX >= guiLeft + 12 && mouseX <= guiLeft + 318
                    && mouseY >= guiTop + 168 && mouseY <= guiTop + 182) {
                draggingLevel = true;
                setLevelFromMouse(mouseX);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        channelField.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY,
            int button, long elapsed) {
        if (housingList.drag(mouseY)) return;
        if (draggingLevel && button == 0) setLevelFromMouse(mouseX);
        else super.mouseClickMove(mouseX, mouseY, button, elapsed);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int button) {
        draggingLevel = false;
        housingList.release();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        housingList.wheel(Mouse.getEventX() * width / mc.displayWidth,
                height - Mouse.getEventY() * height / mc.displayHeight - 1,
                Mouse.getEventDWheel());
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 101) {
            join = !join;
            button.displayString = joinLabel();
            send();
        }
        if (button.id == 100) {
            send();
            mc.player.closeScreen();
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            send();
            mc.player.closeScreen();
            return;
        }
        if (channelField.textboxKeyTyped(typedChar, keyCode)) send();
        else super.keyTyped(typedChar, keyCode);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        channelField.updateCursorCounter();
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) { }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101012);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 18, 0xFF202028);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.title"),
                guiLeft + 8, guiTop + 5, 0xFFFFFFFF);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.selector.housing"),
                guiLeft + 252, guiTop + 27, 0xFFD8D8D8);
        housingList.draw(fontRenderer, mouseX, mouseY);
        for (int i = 0; i < ProgrammableLightTextures.IDS.length; i++) {
            int y = guiTop + 27 + i * 20;
            boolean hovered = mouseX >= guiLeft + 12 && mouseX < guiLeft + 152
                    && mouseY >= y && mouseY < y + 20;
            drawRect(guiLeft + 12, y, guiLeft + 152, y + 19,
                    selected == i ? 0xFF2A4A6A : hovered ? 0xFF1A1A20 : 0xFF0A0A0C);
            fontRenderer.drawStringWithShadow(I18n.format("tile.vandorlabs."
                    + ProgrammableLightTextures.IDS[i] + ".name"),
                    guiLeft + 17, y + 7, selected == i ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.preview"),
                guiLeft + 168, guiTop + 28, 0xFFD8D8D8);
        drawRect(guiLeft + 166, guiTop + 41, guiLeft + 238,
                guiTop + 113, 0xFF505058);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(
                ProgrammableLightTextures.texture(selected, tile.isOn() && level > 0));
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawTexturedModalRect(guiLeft + 169, guiTop + 44, sprite, 66, 66);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.level") + ": " + level,
                guiLeft + 13, guiTop + 156, 0xFFD8D8D8);
        drawRect(guiLeft + 16, guiTop + 173, guiLeft + 16 + SLIDER_W,
                guiTop + 179, 0xFF555560);
        int thumb = guiLeft + 16 + Math.round(SLIDER_W * level / 15F);
        drawRect(thumb - 3, guiTop + 168, thumb + 3, guiTop + 182, 0xFFB8D7E8);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.light.channel"),
                guiLeft + 13, guiTop + 193, 0xFFD8D8D8);
        super.drawScreen(mouseX, mouseY, partialTicks);
        channelField.drawTextBox();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}

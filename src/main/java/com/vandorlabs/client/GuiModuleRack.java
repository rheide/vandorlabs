package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModuleRack;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.client.gui.GuiButton;
import java.io.IOException;

/** Standard housing picker and authenticated inline transmission controls. */
public final class GuiModuleRack extends GuiMaterialPicker {
    private final TileEntityModuleRack tile;
    private HousingTextureList textures;
    private int page, housing;
    private boolean emit;
    public GuiModuleRack(TileEntityModuleRack tile) {
        super(new ContainerModuleRack(tile)); this.tile = tile;
        housing = tile.getHousingTexture(); emit = tile.emitsRedstone(); xSize = 348; ySize = 250;
    }
    @Override protected HousingTextureList materialPicker() { return page == 0 ? textures : null; }
    @Override public void initGui() {
        HousingTextureList previous = textures;
        xSize = Math.min(348, width - 12); ySize = Math.min(250, height - 12);
        super.initGui(); buttonList.clear();
        int tab = (xSize - 28) / 2;
        buttonList.add(new DialogTabButton(100, guiLeft + 12, guiTop + 32, tab, 20, "Housing"));
        buttonList.add(new DialogTabButton(101, guiLeft + 16 + tab, guiTop + 32, tab, 20, "Redstone"));
        for (GuiButton button : buttonList) button.enabled = button.id != 100 + page;
        if (page == 0) textures = HousingTextureList.forHousing(guiLeft + 12, guiTop + 64, xSize - 24, housing,
                Math.max(2, (ySize - 110) / HousingTextureList.ROW_HEIGHT), "vandorlabs:blocks/module_rack/housing")
                .custom(value -> { housing = value; send(); }).keepView(previous);
        else {
            buttonList.add(new GuiButton(0, guiLeft + 12, guiTop + 64, xSize - 24, 20, "Emit Redstone: " + (emit ? "On" : "Off")));
            buttonList.add(new GuiButton(90, guiLeft + 12, guiTop + 92, xSize - 24, 20, "Transmitter..."));
        }
        buttonList.add(new GuiButton(1, guiLeft + 12, guiTop + ySize - 30, xSize - 24, 20, "Done"));
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    GuiButton control(int id) { for (GuiButton button : buttonList) if (button.id == id) return button; return null; }
    private void send() { PacketHandler.INSTANCE.sendToServer(new MessageModuleRack(inventorySlots.windowId, tile.getPos(), housing, emit)); }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 100 || button.id == 101) { page = button.id - 100; initGui(); return; }
        if (button.id == 0) { emit = !emit; send(); initGui(); }
        else if (button.id == 1) mc.player.closeScreen();
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (page == 0 && textures != null) {
            int before = textures.selected();
            if (textures.click(x, y, button)) { if (before != textures.selected()) { housing = textures.selected(); send(); } return; }
        }
        if (!GuiOptionCycle.rightClick(mc, buttonList, x, y, button, this::actionPerformed, 0)) super.mouseClicked(x, y, button);
    }
    @Override protected void keyTyped(char character, int key) throws IOException {
        if (page != 0 || textures == null || !textures.searchKey(character, key)) super.keyTyped(character, key);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xEE171B23); DialogHeader.logo(guiLeft, guiTop, xSize);
        if (page == 0 && textures != null) textures.draw(fontRenderer, x, y);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRenderer.drawString("Module Rack", 12, 10, 0xFFFFFF);
        if (page == 0) fontRenderer.drawString("Wall texture", 12, 54, 0xDAE8F0);
    }
    @Override public void drawScreen(int x, int y, float partial) { drawDefaultBackground(); super.drawScreen(x, y, partial); }
    @Override public void onGuiClosed() { super.onGuiClosed(); org.lwjgl.input.Keyboard.enableRepeatEvents(false); }
}

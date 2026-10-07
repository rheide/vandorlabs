package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableArmor;
import com.vandorlabs.items.ItemProgrammableArmor;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;

/** The same categorized material list used by Programmable Blocks. */
public final class GuiProgrammableArmor extends GuiContainer {
    private final ContainerProgrammableArmor armor;
    private HousingTextureList textures;
    public GuiProgrammableArmor(ContainerProgrammableArmor armor) {
        super(armor); this.armor = armor; xSize = 284; ySize = 268;
    }
    @Override public void initGui() {
        super.initGui();
        textures = new HousingTextureList(guiLeft + 12, guiTop + 38, 250,
                ItemProgrammableArmor.texture(armor.armor)).visibleRows(8);
        buttonList.add(new GuiButton(0, guiLeft + 92, guiTop + 238, 100, 20, "Done"));
    }
    void select(int choice) {
        if (!ItemProgrammableArmor.validTexture(choice)) return;
        textures.setSelected(choice);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableArmor(inventorySlots.windowId, choice));
    }
    @Override protected void actionPerformed(GuiButton button) { mc.player.closeScreen(); }
    @Override protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        if (textures.click(x,y,button)) { if (textures.picked()) select(textures.selected()); return; }
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long time) { textures.drag(y); }
    @Override protected void mouseReleased(int x,int y,int button) { textures.release(); super.mouseReleased(x,y,button); }
    @Override public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        textures.wheel(Mouse.getEventX()*width/mc.displayWidth,
                height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF18242F);
        fontRenderer.drawString(armor.armor.getDisplayName(),guiLeft+12,guiTop+12,0xFFFFFF);
        fontRenderer.drawString("Armor texture",guiLeft+12,guiTop+26,0xCCDDEE);
        textures.draw(fontRenderer,x,y);
        itemRender.renderItemAndEffectIntoGUI(armor.armor,guiLeft+262,guiTop+10);
    }
    @Override public void drawScreen(int x,int y,float partial) { drawDefaultBackground(); super.drawScreen(x,y,partial); }
}

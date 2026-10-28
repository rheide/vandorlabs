package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableArmor;
import com.vandorlabs.items.ItemProgrammableArmor;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.GuiButton;

/** The same categorized material list used by Programmable Blocks. */
public final class GuiProgrammableArmor extends GuiMaterialPicker {
    private final ContainerProgrammableArmor armor;
    private HousingTextureList textures;
    public GuiProgrammableArmor(ContainerProgrammableArmor armor) {
        super(armor); this.armor = armor; xSize = 284; ySize = 268;
    }
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui() {
        xSize=Math.min(284,width-16);
        ySize=Math.min(268,height-16);
        super.initGui();
        textures = HousingTextureList.forArmor(armor.stack(mc.player),guiLeft + 12, guiTop + 38, xSize - 34,
                ItemProgrammableArmor.texture(armor.stack(mc.player))).visibleRows(Math.max(2,(ySize-82)/22)).custom(this::select);
        buttonList.add(new GuiButton(0, guiLeft + (xSize-100)/2, guiTop + ySize-30, 100, 20, "Done"));
    }
    void select(int choice) {
        if (!ItemProgrammableArmor.validTexture(armor.stack(mc.player),choice)) return;
        ItemProgrammableArmor.setTexture(armor.stack(mc.player),choice);
        textures.setSelected(choice);
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableArmor(inventorySlots.windowId, choice));
    }
    @Override protected void actionPerformed(GuiButton button) { mc.player.closeScreen(); }
    @Override protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        if (textures.click(x,y,button)) { if (textures.picked()) select(textures.selected()); return; }
        super.mouseClicked(x,y,button);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF18242F);
        DialogHeader.logo(guiLeft,guiTop,xSize);
        fontRenderer.drawString(armor.stack(mc.player).getDisplayName(),guiLeft+12,guiTop+12,0xFFFFFF);
        fontRenderer.drawString("Armor texture",guiLeft+12,guiTop+26,0xCCDDEE);
        textures.draw(fontRenderer,x,y);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        itemRender.renderItemAndEffectIntoGUI(armor.stack(mc.player),guiLeft+xSize-22,guiTop+10);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    }
    @Override public void drawScreen(int x,int y,float partial) { drawDefaultBackground(); super.drawScreen(x,y,partial); }
}

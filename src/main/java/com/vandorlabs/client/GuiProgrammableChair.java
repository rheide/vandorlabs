package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerProgrammableChair;
import com.vandorlabs.network.MessageProgrammableChair;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

/** Five chair choices with an inventory-model preview. */
public final class GuiProgrammableChair extends GuiContainer {
    private final TileEntityProgrammableChair tile;
    private int selected;
    private int height;
    private static final String[] HEIGHTS = {"Low (-2 px)", "Middle", "High (+2 px)"};

    public GuiProgrammableChair(TileEntityProgrammableChair tile) {
        super(new ContainerProgrammableChair(tile));
        this.tile = tile;
        this.selected = tile.getStyle();
        this.height = tile.getHeight();
        xSize = 256;
        ySize = 210;
    }

    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        for (int i = 0; i < 5; i++) {
            String id = com.vandorlabs.blocks.BlockBridgeChair.Style.byIndex(i).id;
            buttonList.add(new GuiButton(i, guiLeft + 14, guiTop + 25 + i * 24,
                    126, 20, I18n.format("gui.vandorlabs.chair." + id)));
        }
        buttonList.add(new GuiButton(11, guiLeft + 14, guiTop + 151, 126, 20,
                heightLabel()));
        buttonList.add(new GuiButton(10, guiLeft + 154, guiTop + 175, 88, 20,
                I18n.format("gui.done")));
    }

    private String heightLabel() { return "Height: " + HEIGHTS[height]; }

    private void sendSettings() {
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableChair(
                tile.getPos(), selected, height));
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 0 && button.id < 5) {
            selected = button.id;
            tile.setStyle(selected);
            sendSettings();
        } else if (button.id == 11) {
            height = (height + 1) % HEIGHTS.length;
            tile.setHeight(height);
            button.displayString = heightLabel();
            sendSettings();
        } else if (button.id == 10) mc.displayGuiScreen(null);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,
            int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xDD171B23);
        drawRect(guiLeft + 6, guiTop + 6, guiLeft + xSize - 6,
                guiTop + ySize - 6, 0xFF343C49);
        drawRect(guiLeft + 151, guiTop + 26, guiLeft + 243, guiTop + 135,
                0xFF111820);
        ItemStack preview = new ItemStack(ModBlocks.PROGRAMMABLE_CHAIR);
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("ChairStyle", selected);
        data.setInteger("ChairHeight", height);
        preview.setTagInfo("BlockEntityTag", data);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(guiLeft + 169,
                guiTop + 57, 0);
        net.minecraft.client.renderer.GlStateManager.scale(3.5F, 3.5F, 1);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(preview, 0, 0);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.vandorlabs.programmable_chair.name"),
                14, 11, 0xFFFFFF);
        fontRenderer.drawString(I18n.format("gui.vandorlabs.chair.preview"),
                159, 33, 0xDDDDDD);
        fontRenderer.drawString("\u2713", 141, 31 + selected * 24, 0x66FFAA);
    }
}

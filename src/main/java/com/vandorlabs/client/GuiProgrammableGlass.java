package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.block.state.IBlockState;
import java.io.IOException;

/** Live controls for size and translucent glass tint. */
public final class GuiProgrammableGlass extends GuiContainer {
    private static final String[] SIZES={"Small", "Medium", "Large"};
    private static final String[] SHADES={"Clear", "Cyan", "Dark Grey"};
    private final TileEntityProgrammableGlass tile;
    private int size,shade;
    public GuiProgrammableGlass(TileEntityProgrammableGlass tile) {
        super(new ContainerProgrammableGlass(tile));
        this.tile=tile;
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        size=state.getValue(BlockProgrammableGlass.SIZE);
        shade=tile.getShade();
        xSize=210; ySize=108;
    }
    @Override public void initGui() {
        super.initGui();
        buttonList.clear();
        int x=guiLeft+20;
        buttonList.add(new GuiButton(0,x,guiTop+28,170,20,""));
        buttonList.add(new GuiButton(1,x,guiTop+54,170,20,""));
        buttonList.add(new GuiButton(2,guiLeft+145,guiTop+81,45,20,"Done"));
        updateLabels();
    }
    private void updateLabels() {
        buttonList.get(0).displayString="Size: "+SIZES[size];
        buttonList.get(1).displayString="Glass: "+SHADES[shade];
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id==2) { mc.player.closeScreen(); return; }
        if (button.id==0) size=(size+1)%3;
        if (button.id==1) shade=(shade+1)%3;
        updateLabels();
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableGlass(tile.getPos(),size,shade));
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE17232D);
        drawRect(guiLeft+3,guiTop+3,guiLeft+xSize-3,guiTop+ySize-3,0xFF344956);
        drawRect(guiLeft+6,guiTop+6,guiLeft+xSize-6,guiTop+ySize-6,0xFF18252E);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString("Programmable Glass",20,12,0xD5EDF3);
    }
}

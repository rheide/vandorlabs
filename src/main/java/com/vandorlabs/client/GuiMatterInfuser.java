package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityMatterInfuser;
import com.vandorlabs.container.ContainerMatterInfuser;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.client.gui.inventory.GuiFurnace;

public final class GuiMatterInfuser extends GuiFurnace {
    private final TileEntityMatterInfuser tile;
    public GuiMatterInfuser(InventoryPlayer player,TileEntityMatterInfuser tile){super(player,tile);this.tile=tile;inventorySlots=new ContainerMatterInfuser(player,tile);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Matter Infuser",8,6,0x404040);
        fontRenderer.drawString(tile.hasSunlight()?"Sunlight":"Needs sunlight",8,72,tile.hasSunlight()?0x406820:0x884020);
    }
}

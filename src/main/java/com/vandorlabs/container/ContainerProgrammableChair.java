package com.vandorlabs.container;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public final class ContainerProgrammableChair extends Container {
    public final TileEntityProgrammableChair tile;
    public ContainerProgrammableChair(TileEntityProgrammableChair tile) { this.tile = tile; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld() != null && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && tile.getWorld().getBlockState(tile.getPos()).getBlock()
                == ModBlocks.PROGRAMMABLE_CHAIR
                && player.getDistanceSq(tile.getPos()) <= 64;
    }
}

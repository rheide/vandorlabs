package com.vandorlabs.container;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public final class ContainerProgrammableGlass extends Container {
    public final TileEntityProgrammableGlass tile;
    public ContainerProgrammableGlass(TileEntityProgrammableGlass tile) { this.tile = tile; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld() != null && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof BlockProgrammableGlass
                && player.getDistanceSq(tile.getPos()) <= 64;
    }
}

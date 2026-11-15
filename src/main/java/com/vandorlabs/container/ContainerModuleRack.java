package com.vandorlabs.container;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.entity.player.EntityPlayer;

/** Configuration has no inventory slots and remains bound to the exact installed Rack. */
public final class ContainerModuleRack extends ContainerRedstoneChannel {
    public final TileEntityModuleRack tile;
    public ContainerModuleRack(TileEntityModuleRack tile) { super(tile.output); this.tile = tile; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return !tile.inert() && !tile.isInvalid() && tile.getWorld() != null && tile.getWorld().isBlockLoaded(tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && ConfigurationAccess.canKeepDialogOpen(player, tile.getWorld(), tile.getPos());
    }
}

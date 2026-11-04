package com.vandorlabs.segmented;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public final class SegmentedDoorContainer extends Container {
    public final SegmentedDoorTile tile;
    public SegmentedDoorContainer(SegmentedDoorTile tile){this.tile=tile;}
    @Override public boolean canInteractWith(EntityPlayer player){return tile.getWorld()!=null&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&player.getDistanceSq(tile.getPos())<=64&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
}

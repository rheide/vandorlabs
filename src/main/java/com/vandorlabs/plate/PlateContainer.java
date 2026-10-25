package com.vandorlabs.plate;

public final class PlateContainer extends net.minecraft.inventory.Container {
    public final PlateTile tile;
    public PlateContainer(PlateTile tile){this.tile=tile;}
    @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return tile.getWorld()!=null&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
}

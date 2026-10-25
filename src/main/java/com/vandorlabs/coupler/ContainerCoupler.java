package com.vandorlabs.coupler;

public final class ContainerCoupler extends net.minecraft.inventory.Container {
    public final TileCoupler tile;
    public ContainerCoupler(TileCoupler tile){this.tile=tile;}
    @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return tile.getWorld()!=null&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
}

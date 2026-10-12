package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.inventory.Container;
import net.minecraft.entity.player.EntityPlayer;
public final class ContainerLargeCanopy extends Container {
    public final TileEntityCanopy tile;
    public ContainerLargeCanopy(TileEntityCanopy tile){this.tile=tile.owner();}
    public boolean canInteractWith(EntityPlayer player){return tile.getWorld()!=null && tile.getWorld().getTileEntity(tile.getPos())==tile && tile.configurable() && player.getDistanceSqToCenter(tile.getPos())<=64 && !player.isSpectator() && tile.getWorld().isBlockModifiable(player,tile.getPos()) && player.canPlayerEdit(tile.getPos(),net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand());}
}

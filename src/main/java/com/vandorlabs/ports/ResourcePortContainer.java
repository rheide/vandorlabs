package com.vandorlabs.ports;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.vehicle.VehicleService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/** Settings-only container. The dialog cannot acquire or replace stored resources. */
public final class ResourcePortContainer extends Container {
    public final ResourcePortTile tile;
    public ResourcePortContainer(ResourcePortTile tile){this.tile=tile;}
    @Override public boolean canInteractWith(EntityPlayer player){return tile!=null&&!tile.isInvalid()&&tile.getWorld()!=null&&tile.getWorld()==player.world&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&ConfigurationAccess.canKeepDialogOpen(player,tile.getWorld(),tile.getPos());}
    public void update(EntityPlayerMP player,ResourcePortSettings settings,int mode,ChannelList channels){
        if(!canInteractWith(player)||!ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos()))return;
        VehicleService.permission(player,tile.getPos());tile.configure(settings,mode,channels);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){return ItemStack.EMPTY;}
}

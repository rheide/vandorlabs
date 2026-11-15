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
    public final int[] batteryValues=new int[1];
    private final com.vandorlabs.container.WindowPropertyDeltas<net.minecraft.inventory.IContainerListener> batteryDeltas=new com.vandorlabs.container.WindowPropertyDeltas<>((listener,id,value)->listener.sendWindowProperty(this,id,value));
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();if(tile instanceof com.vandorlabs.tiles.TileEntityBattery){batteryValues[0]=((com.vandorlabs.tiles.TileEntityBattery)tile).stored();for(net.minecraft.inventory.IContainerListener listener:listeners)batteryDeltas.sendChanges(listener,batteryValues);}}
    @Override public void removeListener(net.minecraft.inventory.IContainerListener listener){super.removeListener(listener);batteryDeltas.removeListener(listener);}
    @Override public void updateProgressBar(int id,int value){com.vandorlabs.container.WindowPropertyDeltas.receive(batteryValues,id,value);}
    public ResourcePortContainer(ResourcePortTile tile){this.tile=tile;}
    @Override public boolean canInteractWith(EntityPlayer player){return tile!=null&&!tile.inert()&&!tile.isInvalid()&&tile.getWorld()!=null&&tile.getWorld()==player.world&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&ConfigurationAccess.canKeepDialogOpen(player,tile.getWorld(),tile.getPos());}
    public void update(EntityPlayerMP player,ResourcePortSettings settings,int mode,ChannelList channels){
        if(!canInteractWith(player)||!ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos()))return;
        VehicleService.permission(player,tile.getPos());tile.configure(settings,mode,channels);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){return ItemStack.EMPTY;}
}

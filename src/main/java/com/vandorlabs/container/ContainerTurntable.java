package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityTurntableController;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ContainerTurntable extends Container {
    public final TileEntityTurntableController tile;
    public ContainerTurntable(TileEntityTurntableController tile){this.tile=tile;}
    public static ContainerTurntable resolve(EntityPlayer player,World world,int x,int y,int z){if(!world.isBlockLoaded(new BlockPos(x,y,z)))return null;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(new BlockPos(x,y,z));if(!(raw instanceof TileEntityTurntableController))return null;ContainerTurntable result=new ContainerTurntable((TileEntityTurntableController)raw);return result.canInteractWith(player)?result:null;}
    @Override public boolean canInteractWith(EntityPlayer player){return !tile.isInvalid()&&player.world==tile.getWorld()&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
    public void update(EntityPlayerMP player,int operation,NBTTagCompound values)throws Exception{if(!canInteractWith(player))return;if(operation!=0)tile.clearError();if(operation==0)tile.settings(values);else if(operation==1)tile.requestScan(player);else if(operation==2)tile.requestRestore(player);else if(operation==3){tile.settings(values);tile.requestScan(player);}else if(operation==4)tile.turnTo(values.getDouble("Angle"));}
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int index){return net.minecraft.item.ItemStack.EMPTY;}
}

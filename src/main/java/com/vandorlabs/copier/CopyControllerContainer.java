package com.vandorlabs.copier;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CopyControllerContainer extends Container {
    public final CopyControllerTile tile;
    public CopyControllerContainer(CopyControllerTile tile){this.tile=tile;}
    public static CopyControllerContainer resolve(EntityPlayer player,World world,int x,int y,int z){BlockPos pos=new BlockPos(x,y,z);if(!world.isBlockLoaded(pos))return null;TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof CopyControllerTile))return null;CopyControllerContainer result=new CopyControllerContainer((CopyControllerTile)raw);if(!result.canInteractWith(player))return null;com.vandorlabs.tiles.WorldActionAuthorization.authorize(result.tile,player);result.tile.actor=player.getUniqueID();return result;}
    @Override public boolean canInteractWith(EntityPlayer player){return !tile.isInvalid()&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
    public void update(EntityPlayerMP player,int operation,int height,int increment,int mode,ChannelList channels)throws Exception{if(!canInteractWith(player))return;if(operation<0||operation>2)throw new IllegalArgumentException("Invalid copy operation");tile.settings(height,increment,mode,channels);com.vandorlabs.tiles.WorldActionAuthorization.authorize(tile,player);tile.actor=player.getUniqueID();if(operation==1)tile.requestCopy(player);else if(operation==2)tile.refreshPads(player);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int slot){return ItemStack.EMPTY;}
}

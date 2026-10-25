package com.vandorlabs.container;

import com.vandorlabs.gear.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ContainerModularGear extends Container {
    public final GearTileEntity tile;
    private final BlockPos access;
    public ContainerModularGear(GearTileEntity tile){this(tile,tile.getPos());}
    private ContainerModularGear(GearTileEntity tile,BlockPos access){this.tile=tile;this.access=access.toImmutable();}
    public static ContainerModularGear resolve(EntityPlayer player,World world,int x,int y,int z){BlockPos pos=new BlockPos(x,y,z);if(!world.isBlockLoaded(pos))return null;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof GearTileEntity))return null;GearTileEntity root=((GearTileEntity)raw).loadedController();if(root==null&&raw instanceof com.vandorlabs.gear.track.TrackTile)root=(GearTileEntity)raw;if(root==null)return null;ContainerModularGear result=new ContainerModularGear(root,pos);return result.canInteractWith(player)?result:null;}
    @Override public boolean canInteractWith(EntityPlayer player){net.minecraft.tileentity.TileEntity at=tile.getWorld().isBlockLoaded(access)?tile.getWorld().getTileEntity(access):null;return !tile.isInvalid()&&(tile.isController()||tile instanceof com.vandorlabs.gear.track.TrackTile)&&tile.getWorld().getTileEntity(tile.getPos())==tile&&at instanceof GearTileEntity&&(at==tile||((GearTileEntity)at).loadedController()==tile)&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),access);}
    public void update(EntityPlayerMP player,NBTTagCompound settings){if(canInteractWith(player)){for(BlockPos cell:tile.members())com.vandorlabs.vehicle.VehicleService.permission(player,cell);tile.operator(player);tile.configure(settings);}}
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int index){return net.minecraft.item.ItemStack.EMPTY;}
}

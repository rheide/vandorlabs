package com.vandorlabs.container;

import com.vandorlabs.gear.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ContainerModularGear extends Container {
    public final GearTileEntity tile;
    public final com.vandorlabs.gear.track.TrackTile track;
    private final BlockPos access;
    public ContainerModularGear(GearTileEntity tile){this(tile,tile.getPos(),tile instanceof com.vandorlabs.gear.track.TrackTile?(com.vandorlabs.gear.track.TrackTile)tile:null);}
    private ContainerModularGear(GearTileEntity tile,BlockPos access,com.vandorlabs.gear.track.TrackTile track){this.tile=tile;this.access=access.toImmutable();this.track=track;}
    public static ContainerModularGear resolve(EntityPlayer player,World world,int x,int y,int z){BlockPos pos=new BlockPos(x,y,z);if(!world.isBlockLoaded(pos))return null;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof GearTileEntity))return null;GearTileEntity root=((GearTileEntity)raw).loadedController();if(root==null&&raw instanceof com.vandorlabs.gear.track.TrackTile)root=(GearTileEntity)raw;if(root==null)return null;ContainerModularGear result=new ContainerModularGear(root,pos,raw instanceof com.vandorlabs.gear.track.TrackTile?(com.vandorlabs.gear.track.TrackTile)raw:null);return result.canInteractWith(player)?result:null;}
    @Override public boolean canInteractWith(EntityPlayer player){net.minecraft.tileentity.TileEntity at=tile.getWorld().isBlockLoaded(access)?tile.getWorld().getTileEntity(access):null;return (track==null||at==track)&&!tile.isInvalid()&&(tile.isController()||tile instanceof com.vandorlabs.gear.track.TrackTile)&&tile.getWorld().getTileEntity(tile.getPos())==tile&&at instanceof GearTileEntity&&(at==tile||((GearTileEntity)at).loadedController()==tile)&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),access);}
    public void update(EntityPlayerMP player,NBTTagCompound settings){if(canInteractWith(player)){
        if(track!=null){
            if(settings.getKeySet().size()!=1||!settings.hasKey("TrackStyle",3))throw new IllegalArgumentException("Track dialog accepts only track style");
            int style=settings.getInteger("TrackStyle");
            if(tile.isController()&&!tile.fullyDeployed()&&com.vandorlabs.gear.track.TrackStyles.style(tile)!=style)throw new IllegalArgumentException("Deploy fully before changing track style");
            for(BlockPos cell:com.vandorlabs.gear.track.TrackStyles.positions(tile))com.vandorlabs.vehicle.VehicleService.permission(player,cell);
            com.vandorlabs.gear.track.TrackStyles.apply(tile,style);return;
        }
        for(BlockPos cell:tile.members())com.vandorlabs.vehicle.VehicleService.permission(player,cell);tile.operator(player);tile.configure(settings);
    }}
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int index){return net.minecraft.item.ItemStack.EMPTY;}
}

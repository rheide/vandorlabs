package com.vandorlabs.container;

import com.vandorlabs.gear.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ContainerModularGear extends Container {
    public GearTileEntity tile;
    public final com.vandorlabs.gear.track.TrackTile track;
    private final BlockPos access;
    private final GearTileEntity accessTile;
    private final World world;
    public ContainerModularGear(GearTileEntity tile){this(tile,tile.getPos(),tile instanceof com.vandorlabs.gear.track.TrackTile?(com.vandorlabs.gear.track.TrackTile)tile:null);}
    private ContainerModularGear(GearTileEntity tile,BlockPos access,com.vandorlabs.gear.track.TrackTile track){this.tile=tile;this.access=access.toImmutable();this.track=track;this.world=tile.getWorld();net.minecraft.tileentity.TileEntity raw=world.getTileEntity(access);this.accessTile=raw instanceof GearTileEntity?(GearTileEntity)raw:tile;}
    public static ContainerModularGear resolve(EntityPlayer player,World world,int x,int y,int z){BlockPos pos=new BlockPos(x,y,z);if(!world.isBlockLoaded(pos))return null;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof GearTileEntity))return null;GearTileEntity root=((GearTileEntity)raw).loadedController();if(root==null&&raw instanceof com.vandorlabs.gear.track.TrackTile)root=(GearTileEntity)raw;if(root==null)return null;ContainerModularGear result=new ContainerModularGear(root,pos,raw instanceof com.vandorlabs.gear.track.TrackTile?(com.vandorlabs.gear.track.TrackTile)raw:null);if(!result.canInteractWith(player))return null;root.operator(player);return result;}
    /** A joined group may choose a new root without replacing the block that was opened. */
    public GearTileEntity currentTile(){
        if(!world.isBlockLoaded(access))return null;
        if(accessTile.isInvalid()||world.getTileEntity(access)!=accessTile){
            // A native endpoint handoff can replace the clicked arm section while
            // the authenticated controller continues to own this assembly.
            if(tile!=null&&!tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile&&tile.members().contains(access)&&tile.hasRetainedPayload())return tile;
            return null;
        }
        GearTileEntity root=accessTile.loadedController();if(root==null&&accessTile instanceof com.vandorlabs.gear.track.TrackTile)root=accessTile;
        if(root==null||root.isInvalid()||!world.isBlockLoaded(root.getPos())||world.getTileEntity(root.getPos())!=root)return null;
        tile=root;return root;
    }
    @Override public boolean canInteractWith(EntityPlayer player){return currentTile()!=null&&com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(player,world,access);}
    public void update(EntityPlayerMP player,NBTTagCompound settings){if(canInteractWith(player)&&!ArmTransactions.locked(world,access)&&!ArmTransactions.locked(world,tile.getPos())){
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

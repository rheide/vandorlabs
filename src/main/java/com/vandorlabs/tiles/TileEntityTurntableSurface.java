package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.UUID;

/** Only the deterministic first member renders the shared active face. */
public final class TileEntityTurntableSurface extends TileEntity implements net.minecraft.util.ITickable {
    public BlockPos owner;
    public UUID ownerKey;
    private TileEntityTurntableController claimedController(){
        if(world==null || owner==null || !world.isBlockLoaded(owner))return null;
        TileEntity raw=world.getTileEntity(owner);
        if(!(raw instanceof TileEntityTurntableController))return null;
        TileEntityTurntableController controller=(TileEntityTurntableController)raw;
        return controller.key.equals(ownerKey)&&controller.layout!=null&&controller.layout.cells.contains(pos)?controller:null;
    }
    public TileEntityTurntableController controller(){TileEntityTurntableController c=claimedController();return c!=null&&c.enabled?c:null;}
    public boolean locked(){if(owner!=null&&world!=null&&!world.isBlockLoaded(owner))return true;TileEntityTurntableController c=claimedController();return c!=null&&c.captureId!=null;}
    public void bind(TileEntityTurntableController controller){owner=controller.getPos().toImmutable();ownerKey=controller.key;changed();}
    public void release(){owner=null;ownerKey=null;changed();}
    private void changed(){markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}}
    @Override public void update(){if(world!=null&&!world.isRemote&&world.getTotalWorldTime()%20==0&&owner!=null&&world.isBlockLoaded(owner)&&claimedController()==null)release();}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(owner!=null && ownerKey!=null){tag.setLong("Owner",owner.toLong());tag.setUniqueId("OwnerKey",ownerKey);}return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);owner=tag.hasKey("Owner",4)?BlockPos.fromLong(tag.getLong("Owner")):null;ownerKey=tag.hasUniqueId("OwnerKey")?tag.getUniqueId("OwnerKey"):null;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,1,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public AxisAlignedBB getRenderBoundingBox(){TileEntityTurntableController controller=controller();return controller!=null && controller.layout!=null && pos.equals(controller.layout.root)?new AxisAlignedBB(controller.layout.pivot.x-17,controller.layout.pivot.y-17,controller.layout.pivot.z-17,controller.layout.pivot.x+17,controller.layout.pivot.y+17,controller.layout.pivot.z+17):new AxisAlignedBB(pos);}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
}

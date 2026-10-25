package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Relative ownership survives structure copies that relocate only vanilla tile coordinates. */
public final class TileEntityCockpitPart extends TileEntity {
    public BlockPos anchor=BlockPos.ORIGIN;
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setLong("Anchor",anchor.toLong());tag.setLong("AnchorOffset",anchor.subtract(pos).toLong());return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);anchor=tag.hasKey("AnchorOffset",4)?pos.add(BlockPos.fromLong(tag.getLong("AnchorOffset"))):BlockPos.fromLong(tag.getLong("Anchor"));}
    @Override public void onLoad(){
        super.onLoad();
        if(world!=null&&!world.isRemote)DeferredTileLoad.schedule(this,()->{
            TileEntityCockpit owner=com.vandorlabs.cockpit.CockpitAssembly.root(world,pos);
            if(owner!=null&&!anchor.equals(owner.getPos())){anchor=owner.getPos().toImmutable();markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
        });
    }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());world.markBlockRangeForRenderUpdate(pos,pos);}
}

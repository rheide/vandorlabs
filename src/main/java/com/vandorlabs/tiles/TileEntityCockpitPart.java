package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Absolute anchor uses the same relocation codec as other captured multiblocks. */
public final class TileEntityCockpitPart extends TileEntity {
    public BlockPos anchor=BlockPos.ORIGIN;
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setLong("Anchor",anchor.toLong());return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);anchor=BlockPos.fromLong(tag.getLong("Anchor"));}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());world.markBlockRangeForRenderUpdate(pos,pos);}
}

package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;

/** Selected chair model, shared by the lower and upper chair cells. */
public final class TileEntityProgrammableChair extends TileEntity {
    private int style;

    public int getStyle() { return style; }

    public void setStyle(int value) {
        int next = Math.max(0, Math.min(4, value));
        if (style == next) return;
        style = next;
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            IBlockState upper = world.getBlockState(pos.up());
            world.notifyBlockUpdate(pos.up(), upper, upper, 3);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ChairStyle", style);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        style = Math.max(0, Math.min(4, tag.getInteger("ChairStyle")));
    }

    @Override public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.markBlockRangeForRenderUpdate(pos, pos.up());
        }
    }
}

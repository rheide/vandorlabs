package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.math.AxisAlignedBB;

/** Selected chair model, shared by the lower and upper chair cells. */
public final class TileEntityProgrammableChair extends TileEntity {
    private int style;
    private int height = 1;

    public int getStyle() { return style; }
    public int getHeight() { return height; }
    public int getHeightOffsetPixels() { return (height - 1) * 2; }

    public void setHeight(int value) {
        int next = Math.max(0, Math.min(2, value));
        if (height == next) return;
        height = next;
        notifyChairChanged();
    }

    public void setStyle(int value) {
        int next = Math.max(0, Math.min(4, value));
        if (style == next) return;
        style = next;
        notifyChairChanged();
    }

    private void notifyChairChanged() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            IBlockState upper = world.getBlockState(pos.up());
            world.notifyBlockUpdate(pos.up(), upper, upper, 3);
            if (!world.isRemote) {
                double seatY = BlockBridgeChair.Style.byIndex(style).seatY
                        + getHeightOffsetPixels() / 16D;
                for (EntityChairSeat seat : world.getEntitiesWithinAABB(
                        EntityChairSeat.class, new AxisAlignedBB(pos).grow(0.25D, 1D, 0.25D)))
                    if (pos.equals(seat.getChairPos())) seat.setSeatY(seatY);
            }
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ChairStyle", style);
        tag.setInteger("ChairHeight", height);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        style = Math.max(0, Math.min(4, tag.getInteger("ChairStyle")));
        height = tag.hasKey("ChairHeight", 3)
                ? Math.max(0, Math.min(2, tag.getInteger("ChairHeight"))) : 1;
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

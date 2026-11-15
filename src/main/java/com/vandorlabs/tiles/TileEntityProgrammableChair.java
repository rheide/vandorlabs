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
public class TileEntityProgrammableChair extends TileEntity {
    private int style;
    private int height = 1;
    private int angle;
    public int getAngle(){return angle;}
    public int angleDegrees(){return com.vandorlabs.persistence.SeatPose.angleDegrees(angle);}
    public void setAngle(int value){int next=com.vandorlabs.persistence.ChairSettings.angle(value);if(next!=angle){angle=next;notifyChairChanged();}}

    public int getStyle() { return style; }
    public int getHeight() { return height; }
    public int getHeightOffsetPixels() { return com.vandorlabs.persistence.ChairSettings.heightOffsetPixels(height,false); }

    public void setHeight(int value) {
        int next = com.vandorlabs.persistence.ChairSettings.height(true,value);
        if (height == next) return;
        height = next;
        notifyChairChanged();
    }

    public void setStyle(int value) {
        int next = com.vandorlabs.persistence.ChairSettings.style(value);
        if (style == next) return;
        style = next;
        notifyChairChanged();
    }

    protected double seatHeight() { return com.vandorlabs.persistence.ChairSettings.cushion(style,height); }

    protected void notifyChairChanged() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            IBlockState upper = world.getBlockState(pos.up());
            world.notifyBlockUpdate(pos.up(), upper, upper, 3);
            if (!world.isRemote) {
                double seatY = seatHeight();
                for (EntityChairSeat seat : world.getEntitiesWithinAABB(
                        EntityChairSeat.class, new AxisAlignedBB(pos).grow(0.25D, 1D, 0.25D)))
                    if (pos.equals(seat.getChairPos())) {seat.setSeatY(seatY);if(state.getBlock() instanceof BlockBridgeChair)seat.rotationYaw=state.getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING).getHorizontalAngle()+angleDegrees();}
            }
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ChairStyle", style);
        tag.setInteger("ChairHeight", height);
        if(angle!=0)tag.setInteger("ChairAngle",angle);else tag.removeTag("ChairAngle");
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        angle=com.vandorlabs.persistence.ChairSettings.angle(tag.getInteger("ChairAngle"));
        style = com.vandorlabs.persistence.ChairSettings.style(tag.getInteger("ChairStyle"));
        height = com.vandorlabs.persistence.ChairSettings.height(tag.hasKey("ChairHeight",3),tag.getInteger("ChairHeight"));
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

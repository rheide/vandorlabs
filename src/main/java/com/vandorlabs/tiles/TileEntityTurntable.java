package com.vandorlabs.tiles;

import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Marker and animation state for one Turntable platform block. The controller
 * pushes the turning parameters here so the block's top face can show a
 * synchronized rotation indicator.
 */
public class TileEntityTurntable extends TileEntity {
    public BlockPos controller = BlockPos.ORIGIN;
    public boolean active;
    public double startAngle;
    public long startTick;
    public double speed = 90;
    public int rotationDirection = TurntableGeometry.CLOCKWISE;
    public int axis = TurntableGeometry.AXIS_Y;

    public void configure(BlockPos controller, boolean active, double startAngle, long startTick,
            double speed, int direction, int axis) {
        this.controller = controller;
        this.active = active;
        this.startAngle = startAngle;
        this.startTick = startTick;
        this.speed = speed;
        this.rotationDirection = direction;
        this.axis = axis;
        markDirty();
    }

    public double angle(float partial) {
        if (!active || world == null) return TurntableGeometry.normalize(startAngle);
        return TurntableGeometry.angle(startAngle, world.getTotalWorldTime(), startTick, partial,
                speed, rotationDirection);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            world.scheduleUpdate(pos, world.getBlockState(pos).getBlock(), 1);
        }
    }

    /** A platform block whose controller vanished restores itself as an ordinary block. */
    public void validateOwnership() {
        if (world == null || world.isRemote) return;
        TileEntity owner = world.getTileEntity(controller);
        if (owner instanceof TileEntityTurntableController
                && ((TileEntityTurntableController) owner).platformCells().contains(pos)) return;
        active = false;
        controller = BlockPos.ORIGIN;
        markDirty();
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("Controller", controller.toLong());
        tag.setBoolean("Active", active);
        tag.setDouble("StartAngle", startAngle);
        tag.setLong("StartTick", startTick);
        tag.setDouble("Speed", speed);
        tag.setInteger("Direction", rotationDirection);
        tag.setInteger("Axis", axis);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        controller = tag.hasKey("Controller") ? BlockPos.fromLong(tag.getLong("Controller")) : BlockPos.ORIGIN;
        active = tag.getBoolean("Active");
        startAngle = tag.getDouble("StartAngle");
        startTick = tag.getLong("StartTick");
        speed = tag.getDouble("Speed");
        rotationDirection = tag.getInteger("Direction");
        axis = TurntableGeometry.validAxis(tag.getInteger("Axis"))
                ? tag.getInteger("Axis") : TurntableGeometry.AXIS_Y;
    }

    @Override public void invalidate() { super.invalidate(); }

    @Override public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState before,
            net.minecraft.block.state.IBlockState after) {
        return before.getBlock() != after.getBlock();
    }
}

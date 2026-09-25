package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Glass tint is synchronized independently of the baked frame geometry. */
public final class TileEntityProgrammableGlass extends TileEntity {
    private int shade;
    private int size = 1;
    private boolean legacySize;
    private boolean restored;
    public int getShade() { return shade; }
    public int getSize() { return size; }
    public void setSize(int size) {
        if (size < 0 || size > 2 || size == this.size) return;
        this.size = size;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setShade(int shade) {
        if (shade < 0 || shade > 2 || shade == this.shade) return;
        this.shade = shade;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("GlassShade", shade);
        tag.setInteger("GlassSize", size);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        restored = true;
        shade = Math.max(0, Math.min(2, tag.getInteger("GlassShade")));
        legacySize = !tag.hasKey("GlassSize");
        size = legacySize ? 1 : Math.max(0, Math.min(2, tag.getInteger("GlassSize")));
    }
    @Override public void onLoad() {
        super.onLoad();
        if ((!restored || legacySize) && world != null) {
            size = world.getBlockState(pos).getValue(
                    com.vandorlabs.blocks.BlockProgrammableGlass.SIZE);
            legacySize = false;
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
    @Override public boolean shouldRefresh(World world, BlockPos pos,
            IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
    @Override public boolean shouldRenderInPass(int pass) { return pass == 1; }
}

package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.nbt.NBTTagCompound;

public final class TileEntityLandingGear extends TileEntity implements ITickable {
    public float progress, previous;
    public boolean powered;
    public void update() {
        if (!(getBlockType() instanceof BlockTelescopicLandingGear)) return;
        boolean extended=world.getBlockState(pos).getValue(BlockTelescopicLandingGear.EXTENDED);
        previous=progress;
        progress=Math.max(0,Math.min(1,progress+(extended?.05F:-.05F)));
        if (!world.isRemote && progress!=previous) markDirty();
        if (!world.isRemote && progress==0 && !extended) {
            net.minecraft.block.state.IBlockState below=world.getBlockState(pos.down());
            if (below.getBlock()==getBlockType() && below.getValue(BlockTelescopicLandingGear.LOWER)) {
                // The lower-cell cleanup recognizes a completed retraction.
                world.removeTileEntity(pos.down());
                world.setBlockState(pos.down(),net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
        }
    }
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setFloat("Progress",progress);tag.setBoolean("Powered",powered);return tag;
    }
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);progress=Math.max(0,Math.min(1,tag.getFloat("Progress")));previous=progress;powered=tag.getBoolean("Powered");
    }
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos.down(),pos.add(1,1,1)); }
}

package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockShipSystem;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class TileEntityShipSystem extends TileEntity implements ITickable {
    public BlockPos anchor;
    public boolean removing;
    public static TileEntityShipSystem at(IBlockAccess w, BlockPos p) {
        if (w instanceof World && !((World)w).isBlockLoaded(p)) return null;
        TileEntity tile = w.getTileEntity(p);
        return tile instanceof TileEntityShipSystem ? (TileEntityShipSystem)tile : null;
    }
    public BlockShipSystem block() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlockShipSystem ? (BlockShipSystem)world.getBlockState(pos).getBlock() : null;
    }
    public String model() { return block().kind; }
    public EnumFacing facing() { return world.getBlockState(pos).getValue(BlockShipSystem.FACING); }
    public boolean draws() { return anchor == null || anchor.equals(pos); }
    public void sync() { markDirty(); IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }
    public void removeAssembly(IBlockState original) {
        if (removing) return;
        BlockPos owner = anchor == null ? pos : anchor;
        BlockShipSystem b = (BlockShipSystem)original.getBlock();
        // Mark every loaded member first to prevent recursive teardown and duplicate drops.
        for (BlockPos cell : b.cells(owner, original.getValue(BlockShipSystem.FACING))) {
            TileEntityShipSystem member = at(world, cell);
            if (member != null && (cell.equals(pos) || owner.equals(member.anchor))) member.removing = true;
        }
        for (BlockPos cell : b.cells(owner, original.getValue(BlockShipSystem.FACING))) {
            if (cell.equals(pos)) continue;
            TileEntityShipSystem member = at(world, cell);
            if (member != null && member.removing && world.getBlockState(cell).getBlock() == b) world.setBlockToAir(cell);
        }
        // The harvested tile still supplies the one survival drop.
        removing = false;
    }
    public void update() {
        if (world.isRemote || removing || block() == null || world.getTotalWorldTime() % 20 != 0) return;
        if (!draws()) {
            if (world.isBlockLoaded(anchor)) {
                TileEntityShipSystem owner = at(world, anchor);
                if (owner == null || !owner.draws() || owner.block() != block() || owner.facing() != facing()) {
                    removing = true; world.setBlockToAir(pos);
                }
            }
        } else {
            for (BlockPos cell : block().cells(pos, facing())) if (world.isBlockLoaded(cell)) {
                TileEntityShipSystem member = at(world, cell);
                if (member == null || member.block() != block() || !pos.equals(member.anchor)) {
                    removeAssembly(world.getBlockState(pos)); removing = true; world.setBlockToAir(pos); return;
                }
            }
        }
    }
    public NBTTagCompound writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        if (anchor != null) n.setLong("OwnerOffset", anchor.subtract(pos).toLong());
        return n;
    }
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        anchor = n.hasKey("OwnerOffset") ? pos.add(BlockPos.fromLong(n.getLong("OwnerOffset"))) : null;
    }
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
    public AxisAlignedBB getRenderBoundingBox() {
        if (block() == null) return new AxisAlignedBB(pos);
        AxisAlignedBB result = new AxisAlignedBB(pos);
        for (AxisAlignedBB box : ShipSystemMesh.boxes(model(), facing(), pos)) result = result.union(box);
        return result;
    }
}

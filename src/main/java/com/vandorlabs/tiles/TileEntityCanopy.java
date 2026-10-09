package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyMesh;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.util.*;

public final class TileEntityCanopy extends TileEntity implements ITickable {
    public BlockPos anchor, pair;
    public boolean targetOpen, removing;
    public float previous, progress;
    private List<AxisAlignedBB> cached;
    private String cachedKey;

    public static TileEntityCanopy at(IBlockAccess w, BlockPos p) {
        if (w instanceof World && !((World) w).isBlockLoaded(p)) return null;
        TileEntity t = w.getTileEntity(p);
        return t instanceof TileEntityCanopy ? (TileEntityCanopy) t : null;
    }

    public BlockCanopy block() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlockCanopy
                ? (BlockCanopy) world.getBlockState(pos).getBlock()
                : null;
    }

    public EnumFacing facing() {
        return world.getBlockState(pos).getValue(BlockCanopy.FACING);
    }

    public TileEntityCanopy constituent() {
        TileEntityCanopy t = anchor == null || anchor.equals(pos) ? this : at(world, anchor);
        return t == null ? this : t;
    }

    public TileEntityCanopy owner() {
        TileEntityCanopy t = constituent();
        if (t.pair != null && !t.pair.equals(t.pos)) {
            TileEntityCanopy owner = at(world, t.pair);
            if (owner != null) return owner;
        }
        return t;
    }

    public boolean draws() {
        return (anchor == null || anchor.equals(pos))
                && (pair == null || pair.equals(pos))
                && owner() == this;
    }

    public int width() {
        return block().width * (pair != null && block().width == 1 ? 2 : 1);
    }

    public String model() {
        String id = block().kind;
        if (id.equals("regular_canopy_glass"))
            return String.format(java.util.Locale.ROOT, "regular_1x1_%02x", mask());
        if (id.startsWith("angled_")) {
            int mask = mask() & 11;
            return mask == 0 ? id : String.format(java.util.Locale.ROOT, "%s_%02x", id, mask);
        }
        if (block().opening() && width() == 2) return id.replace("1x2", "2x2");
        return id;
    }

    public int mask() {
        BlockCanopy b = block();
        if (b == null) return 0;
        int result = 0;
        EnumFacing f = facing();
        for (int bit : new int[] {1, 2, 4, 8}) {
            EnumFacing dir =
                    bit == 1
                            ? f.rotateYCCW()
                            : bit == 2 ? f.rotateY() : bit == 4 ? f : f.getOpposite();
            TileEntityCanopy t = at(world, pos.offset(dir, bit == 8 ? b.length : 1));
            if (bit == 4 && b.kind.equals("regular_canopy_glass") && t != null) t = t.constituent();
            if (t == null || t.block() == null || t.facing() != f || t.constituent() != t) continue;
            BlockCanopy other = t.block();
            boolean same = b.kind.equals(other.kind);
            boolean seam =
                    bit == 8
                                    && b.kind.startsWith("angled_")
                                    && other.kind.equals("regular_canopy_glass")
                            || bit == 4
                                    && b.kind.equals("regular_canopy_glass")
                                    && other.kind.startsWith("angled_");
            if (same || seam) result |= bit;
        }
        return result;
    }

    public void sync() {
        markDirty();
        if (world != null) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    public void tryPair() {
        BlockCanopy b = block();
        if (b == null
                || !b.opening()
                || b.width != 1
                || pair != null
                || targetOpen
                || progress != 0) return;
        for (EnumFacing dir : new EnumFacing[] {facing().rotateYCCW(), facing().rotateY()}) {
            TileEntityCanopy t = at(world, pos.offset(dir));
            if (t == null
                    || t.constituent() != t
                    || t.block() != b
                    || t.facing() != facing()
                    || t.pair != null
                    || t.targetOpen
                    || t.progress != 0) continue;
            TileEntityCanopy left = dir == facing().rotateYCCW() ? t : this,
                    right = left == this ? t : this;
            left.pair = left.pos;
            right.pair = left.pos;
            left.sync();
            right.sync();
            return;
        }
    }

    public void toggle() {
        if (!draws() || !block().opening()) return;
        if (!targetOpen && !clearance()) return;
        targetOpen = !targetOpen;
        sync();
    }

    public boolean clearance() {
        EnumFacing f = facing();
        BlockCanopy b = block();
        int w = width();
        AxisAlignedBB local =
                b.kind.contains("slide")
                        ? new AxisAlignedBB(0, 0, 0, w, 1, 4)
                        : new AxisAlignedBB(0, 0, 0, w, 3.25, 3.5);
        Vec3d a = CanopyMesh.rotate(new Vec3d(local.minX, local.minY, local.minZ), f),
                c = CanopyMesh.rotate(new Vec3d(local.maxX, local.maxY, local.maxZ), f);
        AxisAlignedBB sweep = new AxisAlignedBB(a, c).offset(pos).shrink(.005);
        for (BlockPos p :
                BlockPos.getAllInBox(
                        new BlockPos(sweep.minX, sweep.minY, sweep.minZ),
                        new BlockPos(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            if (!world.isBlockLoaded(p)) return false;
            TileEntityCanopy t = at(world, p);
            if (t != null) {
                if (t.owner() == this) continue;
                return false;
            }
            List<AxisAlignedBB> boxes = new ArrayList<>();
            IBlockState s = world.getBlockState(p);
            s.addCollisionBoxToList(world, p, sweep, boxes, null, false);
            if (!boxes.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void update() {
        previous = progress;
        if (!world.isRemote
                && block() != null
                && world.getTotalWorldTime() % 20 == 0
                && repairMembership()) return;
        if (!draws() || block() == null || !block().opening()) return;
        float next = Math.max(0, Math.min(1, progress + (targetOpen ? 1 : -1) / 16F));
        if (next != progress) {
            progress = next;
            if (!world.isRemote) markDirty();
        }
    }

    /** Resolve broken cross-chunk footprints after both sides become loaded again. */
    private boolean repairMembership() {
        if (anchor != null && !anchor.equals(pos)) {
            if (world.isBlockLoaded(anchor)) {
                TileEntityCanopy parent = at(world, anchor);
                if (parent == null || parent.block() != block() || !anchor.equals(parent.anchor)) {
                    removing = true;
                    world.setBlockToAir(pos);
                    return true;
                }
            }
            return false;
        }
        if (pair != null) {
            BlockPos other = pair.equals(pos) ? pos.offset(facing().rotateY()) : pair;
            if (world.isBlockLoaded(other)) {
                TileEntityCanopy member = at(world, other);
                if (member == null
                        || member.block() != block()
                        || member.facing() != facing()
                        || !pair.equals(member.pair)) {
                    pair = null;
                    progress = previous = 0;
                    targetOpen = false;
                    sync();
                }
            }
        }
        for (BlockPos cell : block().cells(pos, facing()))
            if (world.isBlockLoaded(cell)) {
                TileEntityCanopy member = at(world, cell);
                if (member == null || member.block() != block() || !pos.equals(member.anchor)) {
                    removeAssembly();
                    world.setBlockToAir(pos);
                    return true;
                }
            }
        return false;
    }

    public List<AxisAlignedBB> boxes() {
        if (!draws() || block() == null) return Collections.emptyList();
        String key = model() + ":" + progress + ":" + facing();
        if (!key.equals(cachedKey)) {
            cached =
                    CanopyMesh.collision(
                            model(),
                            block().kind,
                            width(),
                            block().opening() ? progress : 0,
                            facing(),
                            pos);
            cachedKey = key;
        }
        return cached;
    }

    public RayTraceResult trace(Vec3d start, Vec3d end) {
        RayTraceResult best = null;
        for (AxisAlignedBB box : boxes()) {
            RayTraceResult hit = box.calculateIntercept(start, end);
            if (hit != null
                    && (best == null
                            || start.squareDistanceTo(hit.hitVec)
                                    < start.squareDistanceTo(best.hitVec)))
                best = new RayTraceResult(hit.hitVec, hit.sideHit, pos);
        }
        return best;
    }

    public void removeAssembly() {
        TileEntityCanopy base = constituent();
        if (base.removing || base.anchor != null && !base.anchor.equals(base.pos)) return;
        base.removing = true;
        TileEntityCanopy owner = base.owner();
        if (owner.pair != null) {
            BlockPos right = owner.pos.offset(owner.facing().rotateY());
            TileEntityCanopy partner = at(world, base == owner ? right : owner.pos);
            if (partner != null) {
                partner.pair = null;
                partner.progress = partner.previous = 0;
                partner.targetOpen = false;
                partner.sync();
            }
            owner.pair = null;
        }
        BlockCanopy b = base.block();
        if (b != null)
            for (BlockPos cell : b.cells(base.pos, base.facing()))
                if (!cell.equals(pos)) {
                    TileEntityCanopy t = at(world, cell);
                    if (t != null && base.pos.equals(t.anchor)) {
                        t.removing = true;
                        world.setBlockToAir(cell);
                    }
                }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        if (anchor != null) {
            n.setLong("Anchor", anchor.toLong());
            n.setLong("AnchorOffset", anchor.subtract(pos).toLong());
        }
        if (pair != null) {
            n.setLong("Pair", pair.toLong());
            n.setLong("PairOffset", pair.subtract(pos).toLong());
        }
        if (block() != null) n.setInteger("CanopyMask", mask());
        n.setBoolean("Open", targetOpen);
        n.setFloat("Progress", progress);
        return n;
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        anchor =
                n.hasKey("AnchorOffset")
                        ? pos.add(BlockPos.fromLong(n.getLong("AnchorOffset")))
                        : n.hasKey("Anchor") ? BlockPos.fromLong(n.getLong("Anchor")) : null;
        pair =
                n.hasKey("PairOffset")
                        ? pos.add(BlockPos.fromLong(n.getLong("PairOffset")))
                        : n.hasKey("Pair") ? BlockPos.fromLong(n.getLong("Pair")) : null;
        targetOpen = n.getBoolean("Open");
        progress = previous = Math.max(0, Math.min(1, n.getFloat("Progress")));
        cachedKey = null;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(5);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536;
    }
}

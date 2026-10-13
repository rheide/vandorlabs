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
    public boolean centeredPlacement,hollowPlacement;
    public final com.vandorlabs.canopy.LargeCanopySettings large=new com.vandorlabs.canopy.LargeCanopySettings();
    public boolean configurable(){return block()!=null;}
    public boolean resizable(){return block()!=null && com.vandorlabs.canopy.LargeCanopySettings.supports(block().kind);}
    public List<CanopyMesh.Face> faces(){
        if(block()==null)return Collections.emptyList();
        List<CanopyMesh.Face> source=resizable()?com.vandorlabs.canopy.LargeCanopyGeometry.faces(block().width,large,centeredPlacement):com.vandorlabs.canopy.CanopyVariants.faces(this,CanopyMesh.MODELS.get(model()));
        return com.vandorlabs.canopy.CanopyFinishes.faces(source,large.shade,large.tint);
    }
    public List<BlockPos> cells(){return cells(large);}
    public List<BlockPos> cells(com.vandorlabs.canopy.LargeCanopySettings settings){
        if(block()==null)return Collections.emptyList();
        if(resizable() && hollowPlacement){
            String key=pos+":"+facing()+":"+centeredPlacement+":"+settings.depth+":"+settings.scale;
            if(!key.equals(cachedCellsKey)){cachedCells=com.vandorlabs.canopy.LargeCanopyOwnership.shellCells(block(),pos,facing(),settings,centeredPlacement);cachedCellsKey=key;}
            return cachedCells;
        }
        return block().cells(pos,facing(),resizable()?settings.length():block().length,block().kind.equals("space_visor_2x1") && centeredPlacement?3:block().width);
    }
    private List<BlockPos> cachedCells;
    private String cachedCellsKey;
    private int joinedWidth=1;
    public boolean targetOpen, removing;
    public float previous, progress;
    private List<AxisAlignedBB> cached;
    private String cachedKey;
    // Chunk#setBlockState installs the replacement before invoking breakBlock.
    // Retain its supplied old state only for the synchronous teardown window.
    private IBlockState removalState;

    public static TileEntityCanopy at(IBlockAccess w, BlockPos p) {
        if (w instanceof World && !((World) w).isBlockLoaded(p)) return null;
        TileEntity t;
        if(w instanceof World && !(w instanceof com.vandorlabs.vehicle.VehicleWorld)) {
            // Connection masks are also serialized inside chunk packets. World#getTileEntity
            // creates missing tiles, which would mutate the map that packet is iterating.
            net.minecraft.world.chunk.Chunk chunk=((World)w).getChunkProvider().getLoadedChunk(p.getX()>>4,p.getZ()>>4);
            t=chunk==null?null:chunk.getTileEntityMap().get(p);
        }else t=w.getTileEntity(p);
        return t instanceof TileEntityCanopy && !t.isInvalid() ? (TileEntityCanopy) t : null;
    }

    public BlockCanopy block() {
        IBlockState state=removalState!=null?removalState:world==null?null:world.getBlockState(pos);
        return state!=null && state.getBlock() instanceof BlockCanopy
                ? (BlockCanopy) state.getBlock()
                : null;
    }

    public EnumFacing facing() {
        IBlockState state=removalState!=null?removalState:world==null?null:world.getBlockState(pos);
        return state!=null && state.getBlock() instanceof BlockCanopy?state.getValue(BlockCanopy.FACING):EnumFacing.NORTH;
    }

    public TileEntityCanopy constituent() {
        TileEntityCanopy t = anchor == null || anchor.equals(pos) ? this : at(world, anchor);
        return t == null || t.block()==null ? this : t;
    }

    public TileEntityCanopy owner() {
        TileEntityCanopy t = constituent();
        if (t.pair != null && !t.pair.equals(t.pos)) {
            TileEntityCanopy owner = at(world, t.pair);
            if (owner != null && owner.block()!=null && owner.block()==t.block()) return owner;
        }
        return t;
    }

    public boolean draws() {
        return block()!=null && !removing && (anchor == null || anchor.equals(pos))
                && (pair == null || pair.equals(pos))
                && owner() == this;
    }

    public int width() {
        TileEntityCanopy owner=owner();
        return owner==this?(block()==null?1:block().width)*(pair==null?1:joinedWidth):owner.width();
    }
    private List<TileEntityCanopy> members() {
        TileEntityCanopy owner=owner();List<TileEntityCanopy> result=new ArrayList<>();
        for(int i=0;i<(owner.pair==null?1:owner.joinedWidth);i++) {
            TileEntityCanopy member=at(world,owner.pos.offset(owner.facing().rotateY(),i));
            if(member!=null && member.constituent()==member && member.block()==owner.block()
                    && (member==owner || owner.pos.equals(member.pair)))result.add(member);
        }
        return result;
    }

    public String model() {
        if(block()==null)return "";
        String id = block().kind;
        if (id.equals("regular_canopy_glass"))
            return String.format(java.util.Locale.ROOT, "regular_1x1_%02x", mask());
        if (id.startsWith("angled_")) {
            int mask = mask() & 11;
            return mask == 0 ? id : String.format(java.util.Locale.ROOT, "%s_%02x", id, mask);
        }
        if (block().opening() && width() >= 2) return id.replace("1x2", "2x2");
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
            boolean regular=b.kind.equals("regular_canopy_glass");
            if(regular && t!=null)t=t.constituent();
            if(t==null || t.block()==null || t.constituent()!=t)continue;
            BlockCanopy other=t.block();
            boolean same=b.kind.equals(other.kind) && (regular || t.facing()==f);
            boolean seam=bit==8 && b.kind.startsWith("angled_") && other.kind.equals("regular_canopy_glass")
                    || regular && other.kind.startsWith("angled_")
                    && t.getPos().offset(t.facing().getOpposite(),other.length).equals(pos);
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
        BlockCanopy b=block();
        if(b==null || !b.opening() || b.width!=1 || removing || constituent()!=this)return;
        int limit=3;
        TileEntityCanopy owner=owner();
        if(owner.targetOpen || owner.progress!=0)return;
        for(EnumFacing dir:new EnumFacing[]{facing().rotateYCCW(),facing().rotateY()}) {
            BlockPos edge=dir==facing().rotateYCCW()?owner.pos.offset(dir):owner.pos.offset(dir,owner.width());
            TileEntityCanopy adjacent=at(world,edge);
            if(adjacent==null || adjacent.constituent()!=adjacent || adjacent.removing || adjacent.block()!=b || adjacent.facing()!=facing())continue;
            TileEntityCanopy other=adjacent.owner();
            if(other==owner || other.removing || other.targetOpen || other.progress!=0 || owner.width()+other.width()>limit)continue;
            List<TileEntityCanopy> group=owner.members();group.addAll(other.members());
            TileEntityCanopy left=dir==facing().rotateYCCW()?other:owner;
            for(TileEntityCanopy member:group){member.pair=left.pos;member.joinedWidth=group.size();member.sync();}
            owner=left;
        }
    }

    public void toggle() {
        if (!draws() || !block().opening()) return;
        if (!targetOpen && !clearance()) return;
        targetOpen = !targetOpen;
        sync();
    }

    /** Opening shells intentionally pass through the vehicle's rear bodywork. */
    public boolean clearance() {return block()!=null && block().opening();}

    @Override
    public void update() {
        if(world==null || removing)return;
        if(block()==null) {
            // A failed older teardown may have left a tile on air/another block.
            // Remove only this orphan tile; never erase its replacement block.
            if(!world.isRemote && at(world,pos)==this)world.removeTileEntity(pos);
            return;
        }
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
        if(pair!=null) {
            TileEntityCanopy owner=at(world,pair);
            boolean broken=world.isBlockLoaded(pair) && (owner==null || owner.block()!=block() || owner.facing()!=facing() || !pair.equals(owner.pair));
            if(pair.equals(pos))for(int i=1;i<joinedWidth;i++) {
                BlockPos other=pos.offset(facing().rotateY(),i);
                if(world.isBlockLoaded(other)) {
                    TileEntityCanopy member=at(world,other);
                    if(member==null || member.block()!=block() || member.facing()!=facing() || !pos.equals(member.pair))broken=true;
                }
            }
            if(broken){for(TileEntityCanopy member:members()){member.pair=null;member.joinedWidth=1;member.progress=member.previous=0;member.targetOpen=false;member.sync();}}
        }
        for (BlockPos cell : cells())
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
        String key = model() + ":" + centeredPlacement + ":" + (configurable()?large.key():"") + ":" + width() + ":" + progress + ":" + facing();
        if (!key.equals(cachedKey)) {
            cached =
                    CanopyMesh.collision(
                            faces(),
                            block().kind,
                            width(),
                            block().opening() ? progress : 0,
                            facing(),
                            pos);
            cachedKey = key;
        }
        return cached;
    }

    public AxisAlignedBB selectionBounds() {
        AxisAlignedBB bounds=null;
        for(AxisAlignedBB box:boxes())bounds=bounds==null?box:bounds.union(box);
        return bounds==null?new AxisAlignedBB(pos):bounds;
    }

    public RayTraceResult trace(Vec3d start, Vec3d end) {
        return !draws() || block()==null?null:CanopyMesh.trace(faces(),block().kind,width(),block().opening()?progress:0,facing(),pos,start,end);
    }

    public void removeAssembly() {
        removeAssembly(world==null?null:world.getBlockState(pos));
    }

    public void removeAssembly(IBlockState oldState) {
        IBlockState previousState=removalState;
        removalState=oldState!=null && oldState.getBlock() instanceof BlockCanopy?oldState:null;
        try { removeAssemblyWithState(); }
        finally {removalState=previousState;}
    }

    private void removeAssemblyWithState() {
        TileEntityCanopy base = constituent();
        if (base.removing || base.anchor != null && !base.anchor.equals(base.pos)) return;
        base.removing = true;
        TileEntityCanopy owner = base.owner();
        if(owner.pair!=null) {
            List<TileEntityCanopy> group=owner.members();
            for(TileEntityCanopy member:group){member.pair=null;member.joinedWidth=1;member.progress=member.previous=0;member.targetOpen=false;member.sync();}
            for(TileEntityCanopy member:group)if(member!=base)member.tryPair();
        }
        BlockCanopy b = base.block();
        if (b != null)
            for (BlockPos cell : base.cells())
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
        if (block() != null) {n.setInteger("CanopyMask", mask());n.setInteger("CanopyWidth",width());}
        if(configurable())large.write(n);
        n.setBoolean("CenteredPlacement",centeredPlacement);
        n.setBoolean("HollowPlacement",hollowPlacement);
        n.setBoolean("Open", targetOpen);
        n.setFloat("Progress", progress);
        return n;
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        large.read(n);
        centeredPlacement=n.getBoolean("CenteredPlacement");
        hollowPlacement=n.getBoolean("HollowPlacement");
        anchor =
                n.hasKey("AnchorOffset")
                        ? pos.add(BlockPos.fromLong(n.getLong("AnchorOffset")))
                        : n.hasKey("Anchor") ? BlockPos.fromLong(n.getLong("Anchor")) : null;
        pair =
                n.hasKey("PairOffset")
                        ? pos.add(BlockPos.fromLong(n.getLong("PairOffset")))
                        : n.hasKey("Pair") ? BlockPos.fromLong(n.getLong("Pair")) : null;
        joinedWidth=n.hasKey("CanopyWidth",3)?Math.max(1,Math.min(3,n.getInteger("CanopyWidth"))):pair==null?1:2;
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

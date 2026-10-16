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

public final class TileEntityCanopy extends TileEntity implements ITickable, com.vandorlabs.redstone.RedstoneChannelMember {
    @Override public boolean shouldRenderInPass(int pass){return (pass==0||pass==1)&&draws();}
    public BlockPos anchor, pair;
    public boolean centeredPlacement,hollowPlacement;
    public final com.vandorlabs.canopy.LargeCanopySettings large=new com.vandorlabs.canopy.LargeCanopySettings();
    public boolean configurable(){return block()!=null;}
    public boolean resizable(){return block()!=null && com.vandorlabs.canopy.LargeCanopySettings.supports(block().kind);}
    private List<CanopyMesh.Face> posedFaces;
    private String posedKey;
    private List<CanopyMesh.Face> baseFaces;
    private String baseKey;
    public boolean opening(){return block()!=null && (block().opening() || resizable() && large.openable);}
    public List<CanopyMesh.Face> faces(){return faces(progress);}
    /** Appearance and connection geometry stay constant while the mechanism moves. */
    public List<CanopyMesh.Face> baseFaces(){
        if(block()==null)return Collections.emptyList();
        int connections=mask(),width=width();String model=model();
        String key=model+":"+large.key()+":"+centeredPlacement+":"+width+":"+connections+":"+(block().kind.equals("regular_canopy_glass")?roofMask():connections);
        if(!key.equals(baseKey)){
            List<CanopyMesh.Face> source=resizable()?com.vandorlabs.canopy.LargeCanopyGeometry.faces(block().width,large,centeredPlacement):com.vandorlabs.canopy.CanopyVariants.faces(this,CanopyMesh.MODELS.get(model));
            if(block().opening())source=com.vandorlabs.canopy.OpeningCanopyGeometry.faces(width,block().length,Math.min(block().length==3?3:2,large.angle),(connections&8)!=0);
            baseFaces=com.vandorlabs.canopy.CanopyFinishes.faces(source,large.shade,large.tint,large.material,large.tileMaterial);baseKey=key;
        }
        return baseFaces;
    }
    public List<CanopyMesh.Face> faces(double pose){

        if(block()==null)return Collections.emptyList();
        List<CanopyMesh.Face> source=baseFaces();String key=baseKey+":"+pose;
        if(!key.equals(posedKey)){
            posedFaces=com.vandorlabs.canopy.CanopyPose.faces(source,block().kind,width(),large,opening()?pose:0);posedKey=key;
        }
        return posedFaces;
    }
    private List<BlockPos> ownedCells;
    private final Set<Integer> releasedHelpers=new HashSet<>();
    public boolean waitingForReservations;
    private boolean reservationReview=true,closeReservationRetry=true;
    public List<TileEntityCanopy> reservationMembers(){return members();}
    public boolean isReleasedCell(BlockPos cell){int index=cells().indexOf(cell);return index>=0&&releasedHelpers.contains(index);}
    public boolean hasReleasedCells(){return !releasedHelpers.isEmpty();}
    public boolean hasSavedFootprint(){return ownedCells!=null&&!ownedCells.isEmpty();}
    public List<BlockPos> reservedCells(){List<BlockPos> result=new ArrayList<>();for(BlockPos cell:cells())if(!isReleasedCell(cell))result.add(cell);return result;}
    public void releasedCell(BlockPos cell,boolean released){int index=cells().indexOf(cell);if(index>=0){if(released)releasedHelpers.add(index);else releasedHelpers.remove(index);markDirty();}}
    public void retryReservationClose(){closeReservationRetry=true;}
    public void reservationGeometryChanged(){reservationReview=true;closeReservationRetry=true;}

    public void setFootprint(List<BlockPos> cells){ownedCells=new ArrayList<>(cells);releasedHelpers.clear();reservationGeometryChanged();markDirty();}
    public List<BlockPos> cells(){return cells(large);}
    public List<BlockPos> cells(com.vandorlabs.canopy.LargeCanopySettings settings){
        if(block()==null)return Collections.emptyList();
        if(settings==large && ownedCells!=null)return Collections.unmodifiableList(ownedCells);
        if(resizable() && hollowPlacement){
            String key=pos+":"+facing()+":"+centeredPlacement+":"+settings.depth+":"+settings.scale+":"+settings.mount;
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
    private com.vandorlabs.canopy.CanopyCollisionIndex collisionIndex;
    private List<AxisAlignedBB> indexedBoxes;
    private static final java.util.concurrent.ThreadPoolExecutor INDEX_WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(2),task->{Thread thread=new Thread(task,"Vandor canopy collision index");thread.setDaemon(true);return thread;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private java.util.concurrent.Future<com.vandorlabs.canopy.CanopyCollisionIndex> pendingIndex;
    private boolean indexFailed;
    private AxisAlignedBB collisionBounds;
    private List<CanopyMesh.Face> boundsFaces;
    private BlockPos boundsPos;
    private EnumFacing boundsFacing;
    private AxisAlignedBB posedBounds;
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
        return t == null || t.block()==null || t.block()!=block() ? this : t;
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
            TileEntityCanopy t = com.vandorlabs.canopy.CanopyReservations.logicalAt(world, pos.offset(dir, bit == 8 ? b.length : 1));
            boolean regular=b.kind.equals("regular_canopy_glass");
            if(regular && t!=null)t=t.constituent();
            if(b.opening() && bit==8 && t!=null && t.facing()==f.getOpposite())t=t.constituent();
            if(t==null || t.block()==null || t.constituent()!=t)continue;
            BlockCanopy other=t.block();
            boolean same=b.kind.equals(other.kind) && (regular || t.facing()==f);
            boolean seam=bit==8 && b.kind.startsWith("angled_") && other.kind.equals("regular_canopy_glass")
                    || regular && other.kind.startsWith("angled_")
                    && t.getPos().offset(t.facing().getOpposite(),other.length).equals(pos);
            seam |= regular && other.opening() && t.getPos().offset(t.facing().getOpposite(),other.length).equals(pos)
                    || b.opening() && bit==8 && other.kind.equals("regular_canopy_glass");
            seam |= b.opening() && bit==8 && other.opening() && t.facing()==f.getOpposite()
                    && t.getPos().offset(f,other.length-1).equals(pos.offset(dir,b.length));
            if (large.mount==t.large.mount && (same || seam)) result |= bit;
        }
        return result;
    }

    /** Fixed glazing keeps a roof frame against a shell that can move away. */
    public int roofMask(){
        int result=mask();EnumFacing f=facing();
        for(int bit:new int[]{1,2,4,8})if((result&bit)!=0){
            EnumFacing dir=bit==1?f.rotateYCCW():bit==2?f.rotateY():bit==4?f:f.getOpposite();
            TileEntityCanopy neighbor=com.vandorlabs.canopy.CanopyReservations.logicalAt(world,pos.offset(dir));
            if(neighbor!=null && neighbor.constituent().block()!=null && neighbor.constituent().block().opening())result&=~bit;
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
        int limit=9;
        TileEntityCanopy owner=owner();
        if(owner.targetOpen || owner.progress!=0)return;
        for(EnumFacing dir:new EnumFacing[]{facing().rotateYCCW(),facing().rotateY()}) {
            BlockPos edge=dir==facing().rotateYCCW()?owner.pos.offset(dir):owner.pos.offset(dir,owner.width());
            TileEntityCanopy adjacent=at(world,edge);
            if(adjacent==null || adjacent.constituent()!=adjacent || adjacent.removing || adjacent.block()!=b || adjacent.facing()!=facing() || adjacent.large.mount!=large.mount || adjacent.large.motion!=large.motion || large.motion==0 && adjacent.large.slideForward!=large.slideForward || adjacent.large.angle!=large.angle)continue;
            TileEntityCanopy other=adjacent.owner();
            if(other==owner || other.removing || other.targetOpen || other.progress!=0 || owner.width()+other.width()>limit)continue;
            List<TileEntityCanopy> group=owner.members();group.addAll(other.members());
            TileEntityCanopy left=dir==facing().rotateYCCW()?other:owner;
            for(TileEntityCanopy member:group){member.pair=left.pos;member.joinedWidth=group.size();member.sync();}
            owner=left;
        }
    }

    public void toggle() {
        if (!draws() || !opening()) return;
        if (!targetOpen && !clearance()) return;
        targetOpen = !targetOpen;
        reservationGeometryChanged();
        com.vandorlabs.canopy.CanopyReservations.targetChanged(this);
        sync();
    }

    /** Opening shells intentionally pass through the vehicle's bodywork. */
    public boolean clearance() {return opening();}

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
        if(!world.isRemote && draws() && opening() && large.redstone && !powerInitialized){powerInitialized=true;com.vandorlabs.redstone.RedstoneChannels.register(this);refreshPower();}
        if (!world.isRemote
                && block() != null
                && world.getTotalWorldTime() % 20 == 0
                && repairMembership()) return;
        if (!draws() || block() == null || !opening()) return;
        if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld)){
            if(!world.isRemote){
                if(targetOpen && waitingForReservations){waitingForReservations=false;reservationReview=true;com.vandorlabs.canopy.CanopyReservations.clearWait(this);sync();}
                if(targetOpen && progress==1 && reservationReview){reservationReview=false;com.vandorlabs.canopy.CanopyReservations.releaseOpen(this);}
                if(!targetOpen && closeReservationRetry){
                    closeReservationRetry=false;boolean blocked=!com.vandorlabs.canopy.CanopyReservations.prepareClose(this);
                    // A closed snapshot parked after moving in vehicle mode may
                    // lack its released helpers. Reopen safely if foreign cells
                    // prevent restoring the closed footprint, then wait to close.
                    if(blocked&&progress==0){previous=progress=1;waitingForReservations=true;com.vandorlabs.canopy.CanopyReservations.releaseOpen(this);sync();}
                    else if(blocked!=waitingForReservations){waitingForReservations=blocked;sync();}
                }
            }
            if(waitingForReservations)return;
        }
        float next = Math.max(0, Math.min(1, progress + (targetOpen ? 1 : -1) / 16F));
        if (next != progress) {
            progress = next;
            if (!world.isRemote){markDirty();if(progress==1){reservationReview=false;com.vandorlabs.canopy.CanopyReservations.releaseOpen(this);}if(progress==0||progress==1){com.vandorlabs.canopy.CanopyReservations.targetChanged(this);sync();}}

        }
    }

    /** Recovery runs during tile ticks, when newly restored helpers may still be pending. */
    private TileEntityCanopy membershipTile(BlockPos cell) {
        if(!world.isBlockLoaded(cell))return null;
        TileEntity raw=world.getTileEntity(cell);
        return raw instanceof TileEntityCanopy&&!raw.isInvalid()
                &&world.getBlockState(cell).getBlock()==block()?(TileEntityCanopy)raw:null;
    }

    /** Resolve broken cross-chunk footprints after both sides become loaded again. */
    private boolean repairMembership() {
        if (anchor != null && !anchor.equals(pos)) {
            if (world.isBlockLoaded(anchor)) {
                TileEntityCanopy parent = membershipTile(anchor);
                if (parent == null || parent.block() != block() || !anchor.equals(parent.anchor) || !parent.cells().contains(pos)) {
                    removing = true;
                    world.setBlockToAir(pos);
                    return true;
                }
            }
            return false;
        }
        if(pair!=null) {
            TileEntityCanopy owner=membershipTile(pair);
            boolean broken=world.isBlockLoaded(pair) && (owner==null || owner.block()!=block() || owner.facing()!=facing() || !pair.equals(owner.pair));
            if(pair.equals(pos))for(int i=1;i<joinedWidth;i++) {
                BlockPos other=pos.offset(facing().rotateY(),i);
                if(world.isBlockLoaded(other)) {
                    TileEntityCanopy member=membershipTile(other);
                    if(member==null || member.block()!=block() || member.facing()!=facing() || !pos.equals(member.pair))broken=true;
                }
            }
            if(broken){for(TileEntityCanopy member:members()){member.pair=null;member.joinedWidth=1;member.progress=member.previous=0;member.targetOpen=false;member.sync();}}
        }
        for (BlockPos cell : reservedCells())
            if (world.isBlockLoaded(cell)) {
                TileEntityCanopy member = membershipTile(cell);
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
        List<CanopyMesh.Face> geometry=faces();
        String key = pos + ":" + baseKey + ":" + model() + ":" + mask() + ":" + centeredPlacement + ":" + hollowPlacement + ":" + (configurable()?large.key():"") + ":" + width() + ":" + progress + ":" + facing();
        if (!key.equals(cachedKey)) {
            cached = resizable()?CanopyMesh.windowCollision(geometry,facing(),pos):
                    CanopyMesh.collision(
                            geometry,
                            "posed",
                            width(),
                            0,
                            facing(),
                            pos);
            collisionIndex=null;cachedKey = key;
        }
        return cached;
    }

    private com.vandorlabs.canopy.CanopyCollisionIndex collisionIndex(){
        List<AxisAlignedBB> current=boxes();
        if(indexedBoxes!=current){cancelIndex();indexedBoxes=current;collisionIndex=null;collisionBounds=null;indexFailed=false;}
        if(collisionIndex==null&&!indexFailed){
            if(pendingIndex==null&&INDEX_WORKER.getQueue().remainingCapacity()>0){
                // The captured list and its boxes are immutable; workers never access this tile or world.
                try{pendingIndex=INDEX_WORKER.submit(()->new com.vandorlabs.canopy.CanopyCollisionIndex(current));}catch(java.util.concurrent.RejectedExecutionException busy){}
            }
            if(pendingIndex!=null&&pendingIndex.isDone()){
                try{collisionIndex=pendingIndex.get();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();indexFailed=true;}catch(java.util.concurrent.ExecutionException|java.util.concurrent.CancellationException failed){indexFailed=true;}
                pendingIndex=null;
            }
        }
        return collisionIndex;
    }
    private void cancelIndex(){if(pendingIndex!=null){pendingIndex.cancel(false);if(pendingIndex instanceof Runnable)INDEX_WORKER.remove((Runnable)pendingIndex);pendingIndex=null;}}
    /** Exact region query for small entities and particles; geometry and ordering are unchanged. */
    public int addCollisionBoxes(AxisAlignedBB query,List<AxisAlignedBB> output){
        if(!draws()||block()==null)return 0;
        // A miss should not tessellate collision geometry just to reject it.
        AxisAlignedBB geometry=geometryBounds();if(geometry==null||!geometry.grow(resizable()?1D/16+.0020001:.0020001).intersects(query))return 0;
        com.vandorlabs.canopy.CanopyCollisionIndex index=collisionIndex();
        if(index!=null)return index.addIntersecting(query,output);
        // Exact, ordered collision remains available under queue pressure; never wait for an index.
        for(AxisAlignedBB box:indexedBoxes)if(box.intersects(query))output.add(box);
        return indexedBoxes.size();
    }
    private AxisAlignedBB geometryBounds(){
        List<CanopyMesh.Face> geometry=faces();EnumFacing direction=facing();
        if(boundsFaces!=geometry||boundsFacing!=direction||!pos.equals(boundsPos)){
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(CanopyMesh.Face face:geometry)for(Vec3d point:face.vertices){minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);}
            posedBounds=geometry.isEmpty()?null:new AxisAlignedBB(CanopyMesh.rotate(new Vec3d(minX,minY,minZ),direction),CanopyMesh.rotate(new Vec3d(maxX,maxY,maxZ),direction)).offset(pos);
            boundsFaces=geometry;boundsFacing=direction;boundsPos=pos.toImmutable();
        }
        return posedBounds;
    }
    public AxisAlignedBB selectionBounds() {
        com.vandorlabs.canopy.CanopyCollisionIndex index=collisionIndex();
        if(index!=null){AxisAlignedBB bounds=index.bounds();return bounds==null?new AxisAlignedBB(pos):bounds;}
        if(collisionBounds==null&&!indexedBoxes.isEmpty()){
            AxisAlignedBB first=indexedBoxes.get(0);double x0=first.minX,y0=first.minY,z0=first.minZ,x1=first.maxX,y1=first.maxY,z1=first.maxZ;
            for(int i=1;i<indexedBoxes.size();i++){AxisAlignedBB box=indexedBoxes.get(i);x0=Math.min(x0,box.minX);y0=Math.min(y0,box.minY);z0=Math.min(z0,box.minZ);x1=Math.max(x1,box.maxX);y1=Math.max(y1,box.maxY);z1=Math.max(z1,box.maxZ);}
            collisionBounds=new AxisAlignedBB(x0,y0,z0,x1,y1,z1);
        }
        return collisionBounds==null?new AxisAlignedBB(pos):collisionBounds;
    }

    public RayTraceResult trace(Vec3d start, Vec3d end) {
        // faces() already contains the animation pose, shared with the renderer.
        if(!draws() || block()==null)return null;
        AxisAlignedBB bounds=geometryBounds();if(bounds==null||!CanopyMesh.intersectsRay(bounds.grow(1e-6),start,end))return null;
        RayTraceResult hit=CanopyMesh.trace(faces(),"posed",width(),0,facing(),pos,start,end);
        if(hit==null || !block().opening() || width()<=block().width)return hit;
        // A joined shell draws once, but harvesting must address the constituent
        // under the visible surface. Opening and ceiling transforms retain local X.
        Vec3d right=new Vec3d(facing().rotateY().getDirectionVec());
        double localX=hit.hitVec.subtract(new Vec3d(pos).addVector(.5,0,.5)).dotProduct(right)+.5;
        int column=MathHelper.clamp(MathHelper.floor(localX),0,width()-1);
        BlockPos selected=pos.offset(facing().rotateY(),column);
        TileEntityCanopy member=at(world,selected);
        return member!=null && member.constituent()==member && member.owner()==this
                ?new RayTraceResult(hit.hitVec,hit.sideHit,selected):null;
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
        if(removing)return; // Deliberate helper release/rollback never delegates teardown to its live root.
        TileEntityCanopy base = constituent();
        if (base.removing || base.anchor != null && !base.anchor.equals(base.pos)) return;
        base.removing = true;
        com.vandorlabs.canopy.CanopyReservations.removed(base);
        TileEntityCanopy owner = base.owner();
        if(owner.pair!=null) {
            List<TileEntityCanopy> group=owner.members();
            for(TileEntityCanopy member:group){member.pair=null;member.joinedWidth=1;member.progress=member.previous=0;member.targetOpen=false;member.sync();}
            for(TileEntityCanopy member:group)if(member!=base)member.tryPair();
        }
        BlockCanopy b = base.block();
        if(b==null)return;
        // Older copies/resizes can retain matching helpers outside the recorded
        // footprint. Collect before erasing the anchor, without loading chunks or
        // treating foreign blocks (including ramp reservations) as canopy cells.
        Set<BlockPos> footprint=new LinkedHashSet<>(base.cells());
        int radius=Math.max(8,b.width+b.length+b.height);
        for(int cx=(base.pos.getX()-radius)>>4;cx<=(base.pos.getX()+radius)>>4;cx++)
            for(int cz=(base.pos.getZ()-radius)>>4;cz<=(base.pos.getZ()+radius)>>4;cz++){
                net.minecraft.world.chunk.Chunk chunk=world.getChunkProvider().getLoadedChunk(cx,cz);
                if(chunk==null)continue;
                for(TileEntity raw:new ArrayList<>(chunk.getTileEntityMap().values()))if(raw instanceof TileEntityCanopy){
                    TileEntityCanopy member=(TileEntityCanopy)raw;
                    if(base.pos.equals(member.anchor) && member.block()==b)footprint.add(member.pos);
                }
            }
        for(BlockPos cell:footprint){
            TileEntityCanopy member=at(world,cell);
            if(member!=null && base.pos.equals(member.anchor) && member.block()==b)member.removing=true;
        }
        for(BlockPos cell:footprint)if(!cell.equals(pos)){
            TileEntityCanopy member=at(world,cell);
            if(member!=null && base.pos.equals(member.anchor) && world.getBlockState(cell).getBlock()==b)
                world.setBlockToAir(cell);
        }

    }

    private boolean channelPowered,powerInitialized;
    private boolean effectivePowerKnown,lastEffectivePower,lastPowerPolarity;
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return large.channels.first();}
    public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return draws() && opening() && large.redstone?large.channels:com.vandorlabs.redstone.ChannelList.EMPTY;}
    public void setRedstoneChannel(int channel){setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(Math.max(0,channel)));}
    public void setRedstoneChannels(com.vandorlabs.redstone.ChannelList channels){effectivePowerKnown=false;large.channels=channels;com.vandorlabs.redstone.RedstoneChannels.register(this);sync();}
    public boolean hasLocalRedstoneSignal(){
        if(!draws() || !opening() || !large.redstone)return false;
        for(TileEntityCanopy member:members())for(BlockPos cell:member.cells())if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,cell))return true;
        return false;
    }
    public void setChannelSignal(boolean powered){channelPowered=powered;refreshPower();}
    public void refreshPower(){
        if(world==null || world.isRemote || !draws() || !opening() || !large.redstone)return;
        com.vandorlabs.redstone.RedstoneChannels.inputChanged(this);
        boolean powered=channelPowered || hasLocalRedstoneSignal();
        // Footprint updates notify neighbors, but an unchanged input is not a new command.
        // Manual opening/closing therefore lasts until the next genuine power edge.
        if(effectivePowerKnown && powered==lastEffectivePower && lastPowerPolarity==large.activateOnPower)return;
        effectivePowerKnown=true;lastEffectivePower=powered;lastPowerPolarity=large.activateOnPower;
        markDirty();
        boolean next=powered==large.activateOnPower;if(next!=targetOpen){targetOpen=next;reservationGeometryChanged();com.vandorlabs.canopy.CanopyReservations.targetChanged(this);sync();}
    }
    @Override public void onLoad(){super.onLoad();reservationGeometryChanged();com.vandorlabs.redstone.RedstoneChannels.register(this);com.vandorlabs.canopy.CanopyReservations.loaded(this);}
    @Override public void invalidate(){cancelIndex();com.vandorlabs.canopy.CanopyReservations.removed(this);com.vandorlabs.redstone.RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){cancelIndex();com.vandorlabs.canopy.CanopyReservations.removed(this);com.vandorlabs.redstone.RedstoneChannels.unregister(this);super.onChunkUnload();}
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
        if (block() != null) {n.setInteger("CanopyMask", mask());n.setInteger("CanopyRoofMask",roofMask());n.setInteger("CanopyWidth",width());}
        if(configurable())large.write(n);
        if(ownedCells!=null){int[] offsets=new int[ownedCells.size()*3];int i=0;for(BlockPos cell:ownedCells){BlockPos delta=cell.subtract(pos);offsets[i++]=delta.getX();offsets[i++]=delta.getY();offsets[i++]=delta.getZ();}n.setIntArray("CanopyFootprint",offsets);}
        n.setIntArray("CanopyReleasedHelpers",releasedHelpers.stream().mapToInt(Integer::intValue).toArray());n.setBoolean("CanopyWaitingForReservations",waitingForReservations);
        n.setBoolean("CenteredPlacement",centeredPlacement);
        n.setBoolean("HollowPlacement",hollowPlacement);
        n.setBoolean("CanopyPowerKnown",effectivePowerKnown);n.setBoolean("CanopyLastPower",lastEffectivePower);n.setBoolean("CanopyLastPolarity",lastPowerPolarity);
        n.setBoolean("Open", targetOpen);
        n.setFloat("Progress", progress);
        return n;
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        large.read(n);
        ownedCells=null;
        if(n.hasKey("CanopyFootprint",11)){int[] offsets=n.getIntArray("CanopyFootprint");if(offsets.length>0 && offsets.length<=3000 && offsets.length%3==0){ownedCells=new ArrayList<>();for(int i=0;i<offsets.length;i+=3)if(Math.abs(offsets[i])<=8 && Math.abs(offsets[i+1])<=8 && Math.abs(offsets[i+2])<=8)ownedCells.add(pos.add(offsets[i],offsets[i+1],offsets[i+2]));}}
        releasedHelpers.clear();for(int index:n.getIntArray("CanopyReleasedHelpers"))if(index>=0&&index<1000)releasedHelpers.add(index);waitingForReservations=n.getBoolean("CanopyWaitingForReservations");reservationGeometryChanged();
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
        joinedWidth=n.hasKey("CanopyWidth",3)?Math.max(1,Math.min(9,n.getInteger("CanopyWidth"))):pair==null?1:2;
        effectivePowerKnown=n.getBoolean("CanopyPowerKnown");lastEffectivePower=n.getBoolean("CanopyLastPower");lastPowerPolarity=n.getBoolean("CanopyLastPolarity");
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
        return new AxisAlignedBB(pos).grow(resizable()?block().width+large.length()+2:Math.max(5,width()+2));
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536;
    }
}

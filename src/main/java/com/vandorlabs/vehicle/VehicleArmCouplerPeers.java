package com.vandorlabs.vehicle;
import com.vandorlabs.coupler.CouplerAssociation;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.redstone.ChannelList;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
/** Detached passive capture recipes. This helper never installs a payload or grants publication authority. */
public final class VehicleArmCouplerPeers {
    public static final int MAX_ROOTS=64,MAX_ROWS=2*VehicleStructure.MAX_BLOCKS;
    public static final long MAX_RETAINED_BYTES=64L*1024*1024;
    public static final class Peer {
        private final BlockPos root;
        private final byte[] encoded;
        private final Set<BlockPos> heads;
        private Peer(BlockPos root,byte[] encoded,Set<BlockPos> heads){
            this.root=root.toImmutable();
            this.encoded=encoded;
            this.heads=Collections.unmodifiableSet(new LinkedHashSet<>(heads));
        }
        public BlockPos root(){
            return root;
        }
        public Set<BlockPos> nativeHeads(){
            return heads;
        }
        /** Metadata-only defensive decode. Ordinary ArmPayload.read would instantiate nested tiles/caps. */
        public ArmPayload payload(){
            try{
                return ArmPayload.readForCouplerReceipt(CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(encoded)),new NBTSizeTracker(VehicleStructure.MAX_NBT_BYTES*4L)));
            }
            catch(IOException invalid){
                throw new IllegalStateException("Frozen peer recipe became unreadable",invalid);
            }
        }
    }
    public static final class Prepared {
        private final WeakReference<EntityGroundVehicle> craft;
        private final WeakReference<VehicleWorld> view;
        private final WeakReference<VehicleStructure> source;
        private final UUID owner,generation;
        private final long epoch;
        private final List<Stamp> roots;
        private final Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads;
        private final List<Peer> peers;
        private final long bytes;
        private final int rows;
        private Prepared(EntityGroundVehicle c,List<Stamp> roots,Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads,List<Peer> peers,long bytes,int rows){
            craft=new WeakReference<>(c);
            view=new WeakReference<>(c.view);
            source=new WeakReference<>(c.structure);
            owner=c.getUniqueID();
            generation=c.couplerAssociationGeneration();
            epoch=c.transferEpoch;
            this.roots=Collections.unmodifiableList(new ArrayList<>(roots));
            this.heads=Collections.unmodifiableMap(new LinkedHashMap<>(heads));
            this.peers=Collections.unmodifiableList(new ArrayList<>(peers));
            this.bytes=bytes;
            this.rows=rows;
        }
        public List<Peer> peers(){
            return peers;
        }
        public long retainedBytes(){
            return bytes;
        }
        public int examinedRows(){
            return rows;
        }
        /** Recheck before the caller's ONE existing batch installer; a changed Source invalidates every peer. */
        public boolean recheck(){
            EntityGroundVehicle c=craft.get();
            if(c==null)return false;
            thread(c);
            if(!installed(c)||c.view!=view.get()||c.structure!=source.get()||!owner.equals(c.getUniqueID())||epoch!=c.transferEpoch||!Objects.equals(generation,c.couplerAssociationGeneration()))return false;
            for(Stamp root:roots)if(!root.current(c))return false;
            Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> now;
            try { now=index(VehicleArmCouplers.observe(c)); } catch(IllegalArgumentException stale) { return false; }
            if(now.size()!=heads.size())return false;
            for(Map.Entry<VehicleArmCouplers.Address,VehicleArmCouplers.Head> e:heads.entrySet())if(!same(e.getValue(),now.get(e.getKey()),true))return false;
            return true;
        }
    }
    private static final class Stamp {
        final BlockPos at,owner;
        final WeakReference<GearTileEntity> tile;
        final WeakReference<GearTopology.Assembly> assembly;
        final WeakReference<ArmPayload> payload;
        final boolean hadPayload,materialized;
        final IBlockState state;
        final Set<BlockPos> members;
        final List<BlockPos> columns;
        final ChannelList channels;
        final int flags,trigger,range,height,size,width,revision;
        final GearMotion.Mode mode;
        final GearMotion.Direction direction;
        final net.minecraft.util.EnumFacing mount,foot;
        final double[] numbers;
        Stamp(EntityGroundVehicle c,GearTileEntity g){
            at=g.getPos().toImmutable();
            owner=g.owner()==null?null:g.owner().toImmutable();
            tile=new WeakReference<>(g);
            assembly=new WeakReference<>(g.assembly());
            payload=new WeakReference<>(g.payload());
            hadPayload=g.payload()!=null;
            materialized=hadPayload&&g.payload().materialized();
            state=c.view.getBlockState(at);
            members=new LinkedHashSet<>();
            for(BlockPos p:g.members())members.add(p.toImmutable());
            columns=new ArrayList<>();
            for(BlockPos p:g.columns())columns.add(p.toImmutable());
            channels=g.getRedstoneChannels();
            flags=flags(g);
            trigger=g.triggerMode;
            revision=g.motionRevision();
            range=g.joinRange();
            height=g.loadHeight();
            size=g.armSize();
            width=g.armWidth();
            mode=g.mode();
            direction=g.direction();
            mount=g.mountDirection();
            foot=g.footFacing();
            numbers=new double[8];
            for(int i=0;i<numbers.length;i++)numbers[i]=number(g,i);
        }
        boolean current(EntityGroundVehicle c){
            GearTileEntity g=tile.get();
            if(g==null||c.view.tiles.get(at)!=g||g.isInvalid()||g.getWorld()!=c.view||!g.getPos().equals(at)||c.view.getBlockState(at)!=state||g.assembly()!=assembly.get()||g.payload()!=payload.get()||hadPayload!=(g.payload()!=null)||materialized!=(g.payload()!=null&&g.payload().materialized())||!Objects.equals(owner,g.owner())||!members.equals(g.members())||!columns.equals(g.columns())||!channels.equals(g.getRedstoneChannels())||flags!=flags(g)||trigger!=g.triggerMode||revision!=g.motionRevision()||range!=g.joinRange()||height!=g.loadHeight()||size!=g.armSize()||width!=g.armWidth()||mode!=g.mode()||direction!=g.direction()||mount!=g.mountDirection()||foot!=g.footFacing())return false;
            for(int i=0;i<numbers.length;i++)if(Double.doubleToLongBits(numbers[i])!=Double.doubleToLongBits(number(g,i)))return false;
            return true;
        }
    }
    private static int flags(GearTileEntity g){
        return(g.isOperational()?1:0)|(g.target()?2:0)|(g.pushBlocks()?4:0)|(g.rotatePayload()?8:0)|(g.joinEnabled()?16:0)|(g.carryLoad()?32:0)|(g.usesLegacyInput()?64:0)|(g.armRetractionStroke()?128:0)|(g.trackRetractionStroke()?256:0)|(g.parkingHeld()?512:0)|(g.hasStoredSections()?1024:0)|(g.hasTaxiData()?2048:0)|(g.hasFailedArmSnapshot()?4096:0)|(g.recoveryDropped()?8192:0)|(g.couplerPassive()?16384:0);
    }
    private static double number(GearTileEntity g,int i){
        switch(i){
            case 0:return g.progress();
            case 1:return g.configuredEndAngle();
            case 2:return g.startAngle();
            case 3:return g.physicalStartAngle();
            case 4:return g.activeAngle();
            case 5:return g.configuredActiveExtension();
            case 6:return g.footMountOffset();
            default:return g.activeExtension();
        }
    }
    private static void thread(EntityGroundVehicle c){
        if(c==null||!(c.world instanceof WorldServer)||!((WorldServer)c.world).getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Arm peer preparation requires the physical server thread");
    }
    private static boolean installed(EntityGroundVehicle c){
        return !c.isDead&&c.structure!=null&&c.view!=null&&c.view.craft==c&&c.view.parent==c.world&&c.view.structure==c.structure&&!c.transferInProgress&&!c.ownershipBlocked&&c.couplerScopeReady()&&((WorldServer)c.world).getEntityFromUuid(c.getUniqueID())==c&&c.world.isBlockLoaded(c.getPosition(),false)&&!VehicleCouplings.preparingArmRelease(c);
    }
    private static Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> index(VehicleArmCouplers.Snapshot observed){
        Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> result=new LinkedHashMap<>();
        for(VehicleArmCouplers.Head h:observed.heads)if(result.put(h.address,h)!=null)throw new IllegalArgumentException("Duplicate current peer head address");
        return result;
    }
    private static boolean proof(CouplerAssociation a,CouplerAssociation b){
        return a==null?b==null:b!=null&&a.state==b.state&&a.head.equals(b.head)&&a.vehicle.equals(b.vehicle)&&a.generation.equals(b.generation);
    }
    private static boolean same(VehicleArmCouplers.Head a,VehicleArmCouplers.Head b,boolean points){
        return a!=null&&b!=null&&a.address.equals(b.address)&&a.carrier.equals(b.carrier)&&Objects.equals(a.nativeCell,b.nativeCell)&&a.locked==b.locked&&a.released==b.released&&a.articulated==b.articulated&&proof(a.association,b.association)&&(!points||a.requested==b.requested&&a.x==b.x&&a.y==b.y&&a.z==b.z&&a.nx==b.nx&&a.ny==b.ny&&a.nz==b.nz);
    }
    private static boolean generic(GearTileEntity g){
        return g.assembly()!=null&&g.assembly().kind()==GearTopology.FootKind.NONE&&g.mode()!=GearMotion.Mode.STATIC;
    }
    private static void healthy(GearTileEntity g){
        if(!g.isOperational()||g.isInvalid()||g.hasFailedArmSnapshot()||g.hasTaxiData()||g.recoveryDropped()||g.hasStoredSections()||g.parkingHeld()||g.payload()!=null&&g.payload().hasOwnershipJournal())throw new IllegalArgumentException("Peer arm needs its existing ownership recovery first");
    }
    private static Set<BlockPos> cells(ArmPayload p,BlockPos root){
        Set<BlockPos> result=new LinkedHashSet<>();
        for(int i=0;i<p.blocks.cells.size();i++)if(!result.add(p.placedCellPosition(root,i)))throw new IllegalArgumentException("Duplicate peer native correspondence");
        return result;
    }
    private static void claim(Map<BlockPos,BlockPos> owners,Set<BlockPos> positions,BlockPos root){
        for(BlockPos p:positions){
            BlockPos old=owners.put(p,root);
            if(old!=null&&!old.equals(root))throw new IllegalArgumentException("Independent arm payloads overlap at "+p);
        }
    }
    private static byte[] freeze(ArmPayload payload,long remaining,int rows)throws IOException{
        long fixed=4096L+rows*256L;
        if(fixed>=remaining)throw new IllegalArgumentException("Peer retained metadata budget");
        int limit=(int)Math.min(VehicleStructure.MAX_NBT_BYTES,(remaining-fixed)/16);
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream bounded=new OutputStream(){
            int count;
            public void write(int value)throws IOException{
                if(++count>limit)throw new IOException("Peer retained byte budget");
                bytes.write(value);
            }
            public void write(byte[] b,int offset,int length)throws IOException{
                if(length>limit-count)throw new IOException("Peer retained byte budget");
                count+=length;
                bytes.write(b,offset,length);
            }
        };
        CompressedStreamTools.write(payload.write(),new DataOutputStream(bounded));
        return bytes.toByteArray();
    }
    public static Prepared prepare(EntityGroundVehicle craft,VehicleArmCouplerPairs.Snapshot supplied){
        thread(craft);
        Objects.requireNonNull(supplied);
        if(!installed(craft))throw new IllegalArgumentException("Installed ready peer owner required");
        if(supplied.pairs.size()>VehicleArmCouplers.MAX_HEADS/2)throw new IllegalArgumentException("Peer pair budget");
        Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads=index(VehicleArmCouplers.observe(craft));
        Map<BlockPos,VehicleArmCouplers.Head> wanted=new HashMap<>();
        Set<VehicleArmCouplers.Address> used=new HashSet<>();
        Set<BlockPos> rigid=new HashSet<>();
        for(VehicleArmCouplerPairs.Pair p:supplied.pairs){
            VehicleArmCouplers.Head a=heads.get(p.first.address),b=heads.get(p.second.address);
            if(!same(p.first,a,false)||!same(p.second,b,false)||!used.add(a.address)||!used.add(b.address)||!a.locked||!b.locked||a.released||b.released||!a.requested||!b.requested)throw new IllegalArgumentException("Closed peer pair changed or release is pending");
            for(VehicleArmCouplers.Head h:Arrays.asList(a,b)){
                if(craft.couplerAssociationGeneration()!=null&&(h.association==null||h.association.state!=CouplerAssociation.EXPECTED_PAIR||!h.association.scope(h.association.head,craft.getUniqueID(),craft.couplerAssociationGeneration())))throw new IllegalArgumentException("Peer pair lacks actual owner scope");
                if(h.nativeCell!=null){
                    if(wanted.put(h.nativeCell,h)!=null)throw new IllegalArgumentException("Ambiguous native peer head");
                    if(!a.articulated&&!b.articulated)rigid.add(h.nativeCell);
                }
            }
        }
        List<GearTileEntity> controllers=new ArrayList<>();
        for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&generic((GearTileEntity)tile))controllers.add((GearTileEntity)tile);
        controllers.sort(Comparator.comparingLong(g->g.getPos().toLong()));
        if(controllers.size()>MAX_ROOTS)throw new IllegalArgumentException("Peer root budget");
        List<Stamp> stamps=new ArrayList<>();
        long retained=512L+heads.size()*512L;
        int examined=0;
        Map<BlockPos,BlockPos> owners=new HashMap<>();
        for(GearTileEntity g:controllers){
            if(g.getWorld()!=craft.view||craft.view.tiles.get(g.getPos())!=g)throw new IllegalArgumentException("Peer controller is not installed");
            int count=g.members().size()+g.columns().size();
            if(count>MAX_ROWS-examined)throw new IllegalArgumentException("Peer membership row budget");
            examined+=count;
            retained+=1024L+count*64L+g.getRedstoneChannels().size()*8L;
            if(retained>MAX_RETAINED_BYTES)throw new IllegalArgumentException("Peer metadata byte budget");
            stamps.add(new Stamp(craft,g));
            ArmPayload existing=g.payload();
            if(existing!=null){
                if(existing.hasOwnershipJournal())throw new IllegalArgumentException("Existing arm ownership is pending");
                if(existing.blocks.cells.size()>MAX_ROWS-examined)throw new IllegalArgumentException("Existing peer payload row budget");
                examined+=existing.blocks.cells.size();
                claim(owners,cells(existing,g.getPos()),g.getPos());
            }
        }
        List<Peer> peers=new ArrayList<>();
        for(GearTileEntity g:controllers){
            ArmPayload old=g.payload();
            if(old!=null&&!old.materialized())continue;
            healthy(g);
            int worst=old!=null?old.blocks.cells.size():g.joinEnabled()&&g.columns().size()>1?Math.min(ArmPayload.MAX_BLOCKS,craft.structure.cells.size()):64;
            if(worst>MAX_ROWS-examined)throw new IllegalArgumentException("Peer discovery work budget");
            ArmPayload payload=old==null?ArmPayload.discover(g,null):old.snapshotPlaced(g.getPos(),craft.structure);
            if(payload==null)continue;
            if(!payload.materialized()||payload.hasOwnershipJournal())throw new IllegalArgumentException("Native peer recipe unavailable");
            if(payload.blocks.cells.size()>MAX_ROWS-examined)throw new IllegalArgumentException("Peer discovery row budget");
            examined+=payload.blocks.cells.size();
            Set<BlockPos> positions=cells(payload,g.getPos()),matching=new LinkedHashSet<>();
            for(BlockPos at:positions)if(wanted.containsKey(at)){
                VehicleArmCouplers.Head head=wanted.get(at);
                if(head.address.arm!=null&&!head.address.arm.equals(g.getPos()))throw new IllegalArgumentException("Peer head already belongs to another arm");
                if(rigid.contains(at))throw new IllegalArgumentException("Captured nonarticulated peer unsupported");
                matching.add(at);
            }
            if(matching.isEmpty())continue;
            for(VehicleArmCouplerPairs.Pair pair:supplied.pairs){VehicleArmCouplers.Head a=heads.get(pair.first.address),b=heads.get(pair.second.address);if(a.nativeCell!=null&&b.nativeCell!=null&&positions.contains(a.nativeCell)&&positions.contains(b.nativeCell)){VehicleCouplerTopology topology=VehicleCouplerTopology.forParking(craft.structure,craft.view,supplied);if(!Objects.equals(topology.section.get(a.carrier),topology.section.get(b.carrier)))throw new IllegalArgumentException("Peer discovery crossed an articulated mating face");}}
            ArmGridMapping mapping=payload.gridMapping(g,g.progress());
            if(mapping==null)throw new IllegalArgumentException("Native peer does not represent its actual current phase");
            for(int i=0;i<payload.blocks.cells.size();i++){
                VehicleStructure.Cell row=payload.blocks.cells.get(i);
                BlockPos at=payload.placedCellPosition(g.getPos(),i);
                VehicleStructure.Cell nativeRow=craft.structure.at(at);
                if(!mapping.cell(row.pos).equals(at)||mapping.state(row)!=payload.placedCellState(i)||nativeRow==null||nativeRow.state!=payload.placedCellState(i))throw new IllegalArgumentException("Native peer phase/placed correspondence differs");
            }
            for(VehicleStructure.Cell row:payload.blocks.cells)if(row.state.getBlock() instanceof GearBlock)throw new IllegalArgumentException("Nested peer arm frames unsupported");
            claim(owners,positions,g.getPos());
            try{
                byte[] encoded=freeze(payload,MAX_RETAINED_BYTES-retained,payload.blocks.cells.size());
                long cost=4096L+payload.blocks.cells.size()*256L+encoded.length*16L;
                if(cost>MAX_RETAINED_BYTES-retained)throw new IllegalArgumentException("Peer retained byte budget");
                retained+=cost;
                peers.add(new Peer(g.getPos(),encoded,matching));
            }
            catch(IOException tooLarge){
                throw new IllegalArgumentException("Peer recipe exceeds retained budget",tooLarge);
            }
        }
        Prepared result=new Prepared(craft,stamps,heads,peers,retained,examined);
        if(!result.recheck())throw new IllegalArgumentException("Arm peer source changed during preparation");
        return result;
    }
    private VehicleArmCouplerPeers(){
    }
}

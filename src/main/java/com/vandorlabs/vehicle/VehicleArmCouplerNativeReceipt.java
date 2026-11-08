package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.physics.RigidTransform;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;

/** Detached chosen physical ownership boundary. No publication, synthetic tiles, or capability factories. */
public final class VehicleArmCouplerNativeReceipt {
    private static final String ID="CouplerControlIdentity";
    private static final class Witness {
        final BlockPos at;final IBlockState state;final TileEntity tile;
        Witness(BlockPos at,IBlockState state,TileEntity tile){this.at=at;this.state=state;this.tile=tile;}
    }
    private static final class HeadRecord {
        final VehicleArmCouplers.Head head;final NBTTagCompound data;
        HeadRecord(VehicleArmCouplers.Head head,NBTTagCompound data){this.head=head;this.data=data;}
    }
    public static final class Prepared {
        private final World world;private final VehicleStructure original,chosen;private final BlockPos origin;private final UUID generation;
        private final VehicleArmCouplers.Snapshot observations;private final List<Witness> witnesses;
        private Prepared(World world,VehicleStructure original,VehicleStructure chosen,BlockPos origin,UUID generation,VehicleArmCouplers.Snapshot observations,List<Witness> witnesses){this.world=world;this.original=original;this.chosen=chosen;this.origin=origin;this.generation=generation;this.observations=observations;this.witnesses=Collections.unmodifiableList(new ArrayList<>(witnesses));}
        public VehicleStructure chosenSource(){return chosen;}
        public UUID generation(){return generation;}
        public boolean changed(){return chosen!=original;}
        /** Original scalar observations have UNKNOWN external scope; this is not a publication receipt. */
        public VehicleArmCouplers.Snapshot observations(){return observations;}
        public void recheck(){
            thread(world);
            for(Witness witness:witnesses){if(!world.isBlockLoaded(witness.at)||world.getBlockState(witness.at)!=witness.state||loadedTile(world,witness.at)!=witness.tile||witness.tile!=null&&(witness.tile.isInvalid()||witness.tile.getWorld()!=world||!witness.tile.getPos().equals(witness.at)))throw new IllegalArgumentException("Native receipt state or tile identity changed at "+witness.at);}
            // Actual physical serializers/canonical matching only, never a VehicleWorld or prototype.
            if(!original.matches(world,origin))throw new IllegalArgumentException("Native receipt original contents changed");
        }
    }
    private static void thread(World world){if(!(world instanceof WorldServer)||world instanceof VehicleWorld||world.isRemote||!((WorldServer)world).getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Native Coupler receipt requires physical server thread");}
    private static TileEntity loadedTile(World world,BlockPos at){Chunk chunk=world.getChunkProvider().getLoadedChunk(at.getX()>>4,at.getZ()>>4);return chunk==null?null:chunk.getTileEntityMap().get(at);}
    private static boolean zero(UUID value){return value.getMostSignificantBits()==0&&value.getLeastSignificantBits()==0;}
    private static UUID identity(NBTTagCompound data){boolean any=data.hasKey(ID+"Most")||data.hasKey(ID+"Least");if(!any)return null;if(!data.hasKey(ID+"Most",4)||!data.hasKey(ID+"Least",4))throw new IllegalArgumentException("Malformed native head identity");UUID result=data.getUniqueId(ID);if(zero(result))throw new IllegalArgumentException("Zero native head identity");return result;}
    private static boolean sameProof(CouplerAssociation a,CouplerAssociation b){return a==null?b==null:b!=null&&a.state==b.state&&a.head.equals(b.head)&&a.vehicle.equals(b.vehicle)&&a.generation.equals(b.generation);}
    private static void header(VehicleStructure.Cell template,VehicleStructure.Cell nativeCell){
        NBTTagCompound a=template.tileData(),b=nativeCell.tileData();net.minecraft.util.ResourceLocation expected=TileEntity.getKey(TileCoupler.class);if(a==null||b==null||expected==null||!expected.toString().equals(a.getString("id"))||!expected.toString().equals(b.getString("id"))||a.getBoolean("VehicleArmCouplerReleased")!=b.getBoolean("VehicleArmCouplerReleased")||!Objects.equals(identity(a),identity(b))||!sameProof(CouplerAssociationData.read(a),CouplerAssociationData.read(b)))throw new IllegalArgumentException("Materialized native/template head ownership differs");
    }
    private static NBTTagCompound patch(NBTTagCompound data,UUID identity,UUID owner,UUID generation,boolean paired){
        NBTTagCompound result=data.copy();result.setUniqueId(ID,identity);result=CouplerAssociationData.chosenPatch(result,owner,generation,CouplerAssociation.Boundary.NEW_ASSEMBLY,true,paired);
        if(paired&&result.getBoolean("VehicleArmCouplerReleased")){result.removeTag("VehicleArmCouplerReleased");if("Released".equals(result.getString("CouplerStatus")))result.setString("CouplerStatus","Ready");}return result;
    }
    /** A stored native section may have an empty construction cell after endpoint edits. */
    private static void nativeMembers(World physical,VehicleStructure source,BlockPos origin,GearTileEntity root,List<Witness> witnesses,Set<BlockPos> vacancies){
        Map<BlockPos,VehicleStructure.Cell> stored=new HashMap<>();
        for(VehicleStructure.Cell cell:root.storedSectionCells())stored.put(root.getPos().add(cell.pos),cell);
        for(BlockPos member:root.members()){
            if(source.at(member.subtract(origin))!=null)continue;
            VehicleStructure.Cell retained=stored.get(member);
            if(retained==null||retained.state.getBlock()!=root.getBlockType()||!physical.isBlockLoaded(member)||physical.getBlockState(member)!=net.minecraft.init.Blocks.AIR.getDefaultState()||loadedTile(physical,member)!=null||vacancies.size()>=VehicleStructure.MAX_BLOCKS||!vacancies.add(member.toImmutable()))throw new IllegalArgumentException("Native arm member is outside the complete source");
            // The root's exact original own data authenticates the retained section. Its
            // empty physical cell is a separate witness, since Source omits air cells.
            witnesses.add(new Witness(member.toImmutable(),net.minecraft.init.Blocks.AIR.getDefaultState(),null));
        }
    }
    public static Prepared prepare(World physical,VehicleStructure original,BlockPos origin,UUID prospectiveOwner,UUID suppliedGeneration){
        thread(physical);Objects.requireNonNull(original);Objects.requireNonNull(origin);Objects.requireNonNull(prospectiveOwner);if(zero(prospectiveOwner)||suppliedGeneration!=null&&zero(suppliedGeneration)||original.cells.size()>VehicleStructure.MAX_BLOCKS)throw new IllegalArgumentException("Invalid native receipt owner or source");
        origin=origin.toImmutable();List<Witness> witnesses=new ArrayList<>();Map<BlockPos,GearTileEntity> gears=new LinkedHashMap<>();Map<BlockPos,TileCoupler> nativeTiles=new LinkedHashMap<>();
        for(VehicleStructure.Cell cell:original.cells){BlockPos at=origin.add(cell.pos);if(!physical.isBlockLoaded(at))throw new IllegalArgumentException("Native receipt chunk unavailable");TileEntity tile=loadedTile(physical,at);if((cell.hasTileData()||physical.getBlockState(at).getBlock().hasTileEntity(physical.getBlockState(at)))&&tile==null)throw new IllegalArgumentException("Native receipt tile unavailable");if(tile!=null&&(tile.isInvalid()||tile.getWorld()!=physical||!tile.getPos().equals(at)))throw new IllegalArgumentException("Native receipt tile identity unavailable");witnesses.add(new Witness(at,physical.getBlockState(at),tile));if(cell.state.getBlock() instanceof BlockCoupler){if(!(tile instanceof TileCoupler)||((TileCoupler)tile).quarantined())throw new IllegalArgumentException("Native Coupler retained or unavailable");nativeTiles.put(cell.pos,(TileCoupler)tile);}if(cell.state.getBlock() instanceof GearBlock){if(!(tile instanceof GearTileEntity))throw new IllegalArgumentException("Native arm unavailable");gears.put(cell.pos,(GearTileEntity)tile);}}
        if(!original.matches(physical,origin))throw new IllegalArgumentException("Native receipt original snapshot changed");
        Map<BlockPos,ArmPayload> payloads=new LinkedHashMap<>();Map<BlockPos,VehicleArmCouplers.Address> materialized=new HashMap<>();long rows=original.cells.size();int arms=0;Set<BlockPos> vacancies=new HashSet<>();
        for(Map.Entry<BlockPos,GearTileEntity> entry:gears.entrySet()){
            BlockPos rootLocal=entry.getKey();GearTileEntity gear=entry.getValue();if(gear.hasFailedArmSnapshot())throw new IllegalArgumentException("Retained native arm failure");if(gear.payload()==null&&!original.at(rootLocal).hasTileKey("ArmPayload"))continue;BlockPos owner=gear.owner();GearTileEntity root=owner==null?null:gears.get(owner.subtract(origin));if(root==null||gear.loadedController()!=root||root.hasFailedArmSnapshot())throw new IllegalArgumentException("Incomplete native arm membership");if(root!=gear)continue;nativeMembers(physical,original,origin,root,witnesses,vacancies);
            if(gear.parkingHeld()&&!gear.validateParkingHoldInstalled(true))throw new IllegalArgumentException("Native arm hold is pending or invalid");
            VehicleStructure.Cell rootCell=original.at(rootLocal);ArmPayload payload=gear.payload();if(payload==null){if(rootCell.hasTileKey("ArmPayload"))throw new IllegalArgumentException("Retained native arm payload unavailable");continue;}
            if(!gear.isOperational()||payload.hasOwnershipJournal()||!rootCell.hasTileKey("ArmPayload",10))throw new IllegalArgumentException("Native arm requires recovery");
            if(!payload.write().equals(rootCell.tileData().getCompoundTag("ArmPayload")))throw new IllegalArgumentException("Native arm retained snapshot changed");
            rows+=payload.blocks.cells.size()+gear.storedSectionCells().size();if(rows>2L*VehicleStructure.MAX_BLOCKS)throw new IllegalArgumentException("Native receipt metadata budget");for(VehicleStructure.Cell nested:payload.blocks.cells)if(nested.state.getBlock() instanceof GearBlock)throw new IllegalArgumentException("Nested native arm frames unsupported");
            if(payload.materialized()){
                for(int i=0;i<payload.blocks.cells.size();i++){VehicleStructure.Cell nested=payload.blocks.cells.get(i);if(!(nested.state.getBlock() instanceof BlockCoupler))continue;BlockPos at=payload.placedCellPosition(rootLocal,i);VehicleStructure.Cell live=original.at(at);if(live==null||live.state.getBlock()!=nested.state.getBlock()||!nativeTiles.containsKey(at))throw new IllegalArgumentException("Materialized head native owner unavailable");header(nested,live);VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(rootLocal,nested.pos);if(materialized.put(at,address)!=null)throw new IllegalArgumentException("Multiple arm templates own one native head");}
                payload=payload.snapshotPlaced(rootLocal,original);
            }else if(!payload.blocks.couplers.isEmpty())arms++;
            payloads.put(rootLocal,payload);
        }
        List<VehicleArmCouplers.Head> heads=new ArrayList<>();Map<VehicleArmCouplers.Address,HeadRecord> records=new LinkedHashMap<>();
        for(Map.Entry<BlockPos,TileCoupler> entry:nativeTiles.entrySet()){BlockPos local=entry.getKey();VehicleArmCouplers.Address address=materialized.get(local);if(address==null)address=VehicleArmCouplers.Address.nativeCell(local);VehicleArmCouplers.Head head=VehicleArmCouplers.nativeReceiptHead(address,address.arm==null?local:address.arm,local,original.at(local).state,entry.getValue());heads.add(head);records.put(address,new HeadRecord(head,original.at(local).tileData()));}
        for(Map.Entry<BlockPos,ArmPayload> entry:payloads.entrySet())if(!entry.getValue().materialized()){
            GearTileEntity gear=gears.get(entry.getKey());RigidTransform frame=VehicleArmCouplers.payloadFrame(entry.getValue(),gear,gear.progress());frame=new RigidTransform(frame.r,frame.x-origin.getX(),frame.y-origin.getY(),frame.z-origin.getZ());
            for(int i=0;i<entry.getValue().blocks.cells.size();i++){VehicleStructure.Cell cell=entry.getValue().blocks.cells.get(i);if(!(cell.state.getBlock() instanceof BlockCoupler))continue;VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(entry.getKey(),cell.pos);VehicleArmCouplers.Head head=VehicleArmCouplers.capturedReceiptHead(address,entry.getKey(),cell,frame,entry.getValue().placedCellPosition(entry.getKey(),i),entry.getValue().placedCellState(i));if(records.put(address,new HeadRecord(head,cell.tileData()))!=null)throw new IllegalArgumentException("Duplicate native receipt address");heads.add(head);}
        }
        if(heads.size()>VehicleArmCouplers.MAX_HEADS)throw new IllegalArgumentException("Native receipt head budget");VehicleArmCouplers.Snapshot observed=VehicleArmCouplers.nativeReceiptSnapshot(heads,arms);
        VehicleArmCouplers.Snapshot geometry=VehicleArmCouplers.nativeReceiptGeometry(observed);Map<VehicleArmCouplers.Head,VehicleArmCouplers.Head> actual=new IdentityHashMap<>();for(int i=0;i<heads.size();i++)actual.put(geometry.heads.get(i),heads.get(i));Map<VehicleArmCouplers.Address,VehicleArmCouplers.Address> mates=new HashMap<>();for(VehicleArmCouplerPairs.Pair pair:VehicleArmCouplerPairs.discoverForNativeReceipt(geometry).pairs){VehicleArmCouplers.Address a=actual.get(pair.first).address,b=actual.get(pair.second).address;mates.put(a,b);mates.put(b,a);}
        Map<UUID,VehicleArmCouplers.Address> unique=new HashMap<>();Map<VehicleArmCouplers.Address,UUID> identities=new HashMap<>();
        for(HeadRecord record:records.values()){
            UUID id=identity(record.data);CouplerAssociation prior=CouplerAssociationData.read(record.data);if(prior!=null&&(id==null||!prior.head.equals(id)||zero(prior.vehicle)||zero(prior.generation)))throw new IllegalArgumentException("Prior native proof identity mismatch");
            VehicleArmCouplers.Address mate=mates.get(record.head.address);if(prior!=null&&prior.state==CouplerAssociation.EXPECTED_PAIR){if(record.head.released)throw new IllegalArgumentException("Expected pair has inconsistent release marker");HeadRecord partner=records.get(mate);CouplerAssociation other=partner==null?null:CouplerAssociationData.read(partner.data);if(other==null||other.state!=CouplerAssociation.EXPECTED_PAIR||!prior.vehicle.equals(other.vehicle)||!prior.generation.equals(other.generation))throw new IllegalArgumentException("Previously paired native head lost its complete prior mate");}
            if(record.head.address.arm!=null&&record.head.nativeCell==null&&mate==null&&(prior==null||prior.state!=CouplerAssociation.UNPAIRED))throw new IllegalArgumentException("Captured legacy head has no authenticated unpaired proof");
            if(id!=null){VehicleArmCouplers.Address before=unique.put(id,record.head.address);if(before!=null&&!before.equals(record.head.address))throw new IllegalArgumentException("Different native heads share one identity");}identities.put(record.head.address,id);
        }
        if(heads.isEmpty())return new Prepared(physical,original,original,origin,suppliedGeneration,observed,witnesses);
        // Complete observation and prior-claim validation finished. Mint only this chosen detached recipe.
        UUID generation=suppliedGeneration==null?UUID.randomUUID():suppliedGeneration;for(Map.Entry<VehicleArmCouplers.Address,UUID> entry:identities.entrySet())if(entry.getValue()==null)entry.setValue(UUID.randomUUID());
        Map<VehicleArmCouplers.Address,NBTTagCompound> patches=new HashMap<>();for(HeadRecord record:records.values())patches.put(record.head.address,patch(record.data,identities.get(record.head.address),prospectiveOwner,generation,mates.containsKey(record.head.address)));
        List<VehicleStructure.Cell> chosen=new ArrayList<>();for(VehicleStructure.Cell cell:original.cells){NBTTagCompound data=null;TileCoupler nativeTile=nativeTiles.get(cell.pos);if(nativeTile!=null){VehicleArmCouplers.Address address=materialized.get(cell.pos);if(address==null)address=VehicleArmCouplers.Address.nativeCell(cell.pos);data=patches.get(address);}
            ArmPayload payload=payloads.get(cell.pos);if(payload!=null){Map<BlockPos,NBTTagCompound> nested=new LinkedHashMap<>();for(VehicleStructure.Cell head:payload.blocks.couplers){NBTTagCompound patch=patches.get(VehicleArmCouplers.Address.payloadCell(cell.pos,head.pos));if(patch==null)throw new IllegalArgumentException("Native receipt omitted retained head");NBTTagCompound local=patch.copy();local.setInteger("x",head.pos.getX());local.setInteger("y",head.pos.getY());local.setInteger("z",head.pos.getZ());nested.put(head.pos,local);}if(!nested.isEmpty()){data=cell.tileData();data.setTag("ArmPayload",payload.withRejoinedCouplerAssociations(nested).write());}}
            chosen.add(data==null?cell:new VehicleStructure.Cell(cell.pos,cell.state,data));}
        VehicleStructure result=original.displayOnly?VehicleStructure.display(chosen):new VehicleStructure(chosen,original.seat,original.facing,original.gearCount);Prepared prepared=new Prepared(physical,original,result,origin,generation,observed,witnesses);prepared.recheck();return prepared;
    }
    private VehicleArmCouplerNativeReceipt(){}
}

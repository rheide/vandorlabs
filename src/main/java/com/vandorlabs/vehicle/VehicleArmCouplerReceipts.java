package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Detached chosen-partition proof mapping. No world mutation, identity commit or authority mint.
 * A caller must freeze the complete prior observations and publish the result through the existing
 * authorized durable transfer. Candidate coordinates equal prior coordinates minus origin. */
public final class VehicleArmCouplerReceipts {
    private static final String ID="CouplerControlIdentity";
    private static final class Occurrence {
        final NBTTagCompound data;
        Occurrence(NBTTagCompound data){this.data=data;}
    }
    public static VehicleStructure partition(VehicleArmCouplers.Snapshot prior,VehicleStructure original,VehicleStructure candidate,
            BlockPos origin,UUID owner,UUID generation,Set<VehicleArmCouplers.Address> released){
        Objects.requireNonNull(prior);Objects.requireNonNull(original);Objects.requireNonNull(candidate);Objects.requireNonNull(origin);
        Objects.requireNonNull(owner);Objects.requireNonNull(generation);Objects.requireNonNull(released);
        VehicleArmCouplerPairs.Snapshot pairs=VehicleArmCouplerPairs.discoverForParking(prior);
        Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads=new HashMap<>();
        Map<BlockPos,VehicleArmCouplers.Head> nativeHeads=new HashMap<>();
        Map<VehicleArmCouplers.Address,VehicleArmCouplers.Address> partners=new HashMap<>();
        for(VehicleArmCouplers.Head head:prior.heads){
            if(heads.put(head.address,head)!=null)throw new IllegalArgumentException("Duplicate prior Coupler address");
            if(head.nativeCell!=null&&nativeHeads.put(head.nativeCell,head)!=null)throw new IllegalArgumentException("Duplicate native Coupler ownership");
        }
        for(VehicleArmCouplerPairs.Pair pair:pairs.pairs){partners.put(pair.first.address,pair.second.address);partners.put(pair.second.address,pair.first.address);}
        if(released.size()>VehicleArmCouplers.MAX_HEADS)throw new IllegalArgumentException("Release receipt exceeds head budget");
        for(VehicleArmCouplers.Address address:released){VehicleArmCouplers.Address mate=partners.get(address);if(mate==null||!released.contains(mate))throw new IllegalArgumentException("Release receipt must name both actual prior partners");}
        Map<VehicleArmCouplers.Address,List<Occurrence>> occurrences=new LinkedHashMap<>();
        Map<BlockPos,ArmPayload> payloads=new LinkedHashMap<>();
        Map<BlockPos,VehicleArmCouplers.Address> nativeAddresses=new HashMap<>();
        Map<BlockPos,ArmPayload> originalPayloads=new HashMap<>();
        for(VehicleStructure.Cell cell:candidate.cells){
            if(cell.state.getBlock() instanceof BlockCoupler){VehicleArmCouplers.Head head=nativeHeads.get(cell.pos.add(origin));if(head==null)throw new IllegalArgumentException("Chosen native Coupler has no prior ownership address");validatePreimage(original.at(head.nativeCell),cell,released.contains(head.address));add(occurrences,head,cell.tileData());nativeAddresses.put(cell.pos,head.address);}
            if(!(cell.state.getBlock() instanceof GearBlock)||!cell.hasTileKey("ArmPayload",10))continue;
            ArmPayload payload=ArmPayload.readForCouplerReceipt(cell.tileData().getCompoundTag("ArmPayload"));
            if(payload.hasOwnershipJournal())throw new IllegalArgumentException("Recover arm ownership before partitioning");
            for(VehicleStructure.Cell nested:payload.blocks.cells){
                if(nested.state.getBlock() instanceof GearBlock)throw new IllegalArgumentException("Nested arm frames require their own partition receipt");
                if(!(nested.state.getBlock() instanceof BlockCoupler))continue;
                VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(cell.pos.add(origin),nested.pos);
                VehicleArmCouplers.Head head=heads.get(address);if(head==null)throw new IllegalArgumentException("Chosen captured Coupler has no prior ownership address");
                if(payload.materialized()&&(head.nativeCell==null||candidate.at(head.nativeCell.subtract(origin))==null))throw new IllegalArgumentException("Materialized Coupler lost its native owner");
                ArmPayload priorPayload=originalPayloads.get(address.arm);
                if(priorPayload==null){VehicleStructure.Cell root=original.at(address.arm);if(root==null||!root.hasTileKey("ArmPayload",10))throw new IllegalArgumentException("Prior arm recipe unavailable");priorPayload=ArmPayload.readForCouplerReceipt(root.tileData().getCompoundTag("ArmPayload"));originalPayloads.put(address.arm,priorPayload);}
                validatePreimage(priorPayload.blocks.at(address.cell),nested,released.contains(address));add(occurrences,head,nested.tileData());
            }
            payloads.put(cell.pos,payload);
        }
        Map<VehicleArmCouplers.Address,UUID> identities=new HashMap<>();Map<UUID,VehicleArmCouplers.Address> unique=new HashMap<>();
        Map<VehicleArmCouplers.Address,Boolean> paired=new HashMap<>();
        for(Map.Entry<VehicleArmCouplers.Address,List<Occurrence>> entry:occurrences.entrySet()){
            VehicleArmCouplers.Address address=entry.getKey(),mate=partners.get(address);boolean wasReleased=released.contains(address);
            if(mate!=null&&!wasReleased&&!occurrences.containsKey(mate))throw new IllegalArgumentException("Chosen partition loses a retained Coupler partner");
            paired.put(address,mate!=null&&!wasReleased);
            VehicleArmCouplers.Head frozen=heads.get(address);NBTTagCompound originalData;
            if(address.arm==null){VehicleStructure.Cell nativeBefore=original.at(frozen.nativeCell);if(nativeBefore==null)throw new IllegalArgumentException("Prior native Coupler recipe unavailable");originalData=nativeBefore.tileData();}
            else{ArmPayload originalPayload=originalPayloads.get(address.arm);if(originalPayload==null){VehicleStructure.Cell root=original.at(address.arm);if(root==null||!root.hasTileKey("ArmPayload",10))throw new IllegalArgumentException("Prior captured Coupler recipe unavailable");originalPayload=ArmPayload.readForCouplerReceipt(root.tileData().getCompoundTag("ArmPayload"));originalPayloads.put(address.arm,originalPayload);}VehicleStructure.Cell originalCell=originalPayload.blocks.at(address.cell);if(originalCell==null)throw new IllegalArgumentException("Prior captured Coupler cell unavailable");originalData=originalCell.tileData();}
            if(originalData==null)throw new IllegalArgumentException("Prior Coupler own data unavailable");
            boolean originalIdentity=originalData.hasKey(ID+"Most")||originalData.hasKey(ID+"Least");
            if(originalIdentity&&(!originalData.hasKey(ID+"Most",4)||!originalData.hasKey(ID+"Least",4)))throw new IllegalArgumentException("Malformed prior Coupler identity");
            UUID identity=originalIdentity?originalData.getUniqueId(ID):null;
            for(Occurrence occurrence:entry.getValue()){
                NBTTagCompound data=occurrence.data;
                if(wasReleased&&!data.getBoolean("VehicleArmCouplerReleased"))throw new IllegalArgumentException("Chosen released head lacks actual release state");
                if(data.hasKey(ID+"Most")||data.hasKey(ID+"Least")){
                    if(!data.hasKey(ID+"Most",4)||!data.hasKey(ID+"Least",4))throw new IllegalArgumentException("Malformed chosen Coupler identity");
                    UUID current=data.getUniqueId(ID);if(!originalIdentity||!identity.equals(current))throw new IllegalArgumentException("Materialized Coupler identity differs from retained header");identity=current;
                }
                else if(originalIdentity)throw new IllegalArgumentException("Chosen Coupler lost its prior identity");
                CouplerAssociation proof=CouplerAssociationData.read(data);
                if(proof!=null&&(!proof.scope(proof.head,prior.vehicle,prior.generation)||identity==null||!proof.head.equals(identity)))throw new IllegalArgumentException("Chosen Coupler proof does not match prior external receipt");
            }
            if(identity==null)identity=UUID.randomUUID();
            VehicleArmCouplers.Address previous=unique.put(identity,address);if(previous!=null&&!previous.equals(address))throw new IllegalArgumentException("Different Coupler heads share a control identity");identities.put(address,identity);
        }
        List<VehicleStructure.Cell> result=new ArrayList<>(candidate.cells.size());
        for(VehicleStructure.Cell cell:candidate.cells){
            NBTTagCompound data=null;VehicleArmCouplers.Address nativeAddress=nativeAddresses.get(cell.pos);
            if(nativeAddress!=null)data=patch(cell.tileData(),nativeAddress,identities,paired,owner,generation);
            ArmPayload payload=payloads.get(cell.pos);
            if(payload!=null){Map<BlockPos,NBTTagCompound> patches=new LinkedHashMap<>();for(VehicleStructure.Cell nested:payload.blocks.couplers){VehicleArmCouplers.Address address=VehicleArmCouplers.Address.payloadCell(cell.pos.add(origin),nested.pos);patches.put(nested.pos,patch(nested.tileData(),address,identities,paired,owner,generation));}if(!patches.isEmpty()){data=cell.tileData();data.setTag("ArmPayload",payload.withCouplerAssociations(patches).write());}}
            result.add(data==null?cell:new VehicleStructure.Cell(cell.pos,cell.state,data));
        }
        return candidate.displayOnly?VehicleStructure.display(result):new VehicleStructure(result,candidate.seat,candidate.facing,candidate.gearCount);
    }
    private static void validatePreimage(VehicleStructure.Cell original,VehicleStructure.Cell chosen,boolean released){
        if(original==null||!(original.state.getBlock() instanceof BlockCoupler)||original.state.getBlock()!=chosen.state.getBlock()||original.state.withProperty(BlockCoupler.LOCKED,chosen.state.getValue(BlockCoupler.LOCKED))!=chosen.state||!released&&original.state!=chosen.state)throw new IllegalArgumentException("Chosen Coupler geometry changed outside release");
        NBTTagCompound before=original.tileData(),after=chosen.tileData();if(before==null||after==null)throw new IllegalArgumentException("Chosen Coupler original own fields unavailable");
        for(String key:new String[]{"x","y","z"}){before.removeTag(key);after.removeTag(key);}
        if(released)for(String key:new String[]{"VehicleArmCouplerReleased","CouplerStatus"}){before.removeTag(key);after.removeTag(key);}
        if(!before.equals(after))throw new IllegalArgumentException("Chosen Coupler contents changed outside release");
    }
    private static void add(Map<VehicleArmCouplers.Address,List<Occurrence>> occurrences,VehicleArmCouplers.Head head,NBTTagCompound data){
        if(data==null)throw new IllegalArgumentException("Chosen Coupler own data unavailable");
        List<Occurrence> list=occurrences.computeIfAbsent(head.address,key->new ArrayList<>());
        if(list.size()>=2||!list.isEmpty()&&head.nativeCell==null)throw new IllegalArgumentException("Duplicate chosen Coupler ownership");
        list.add(new Occurrence(data));
    }
    private static NBTTagCompound patch(NBTTagCompound original,VehicleArmCouplers.Address address,Map<VehicleArmCouplers.Address,UUID> identities,Map<VehicleArmCouplers.Address,Boolean> paired,UUID owner,UUID generation){
        UUID identity=identities.get(address);if(identity==null)throw new IllegalArgumentException("Missing chosen Coupler identity");
        NBTTagCompound result=original.copy();result.setUniqueId(ID,identity);
        return CouplerAssociationData.chosenPatch(result,owner,generation,CouplerAssociation.Boundary.DURABLE_RELEASE,true,Boolean.TRUE.equals(paired.get(address)));
    }
    private VehicleArmCouplerReceipts(){}
}

package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Strict chosen-geometry/native-preimage binding for durable scoped attachment rollback. */
public final class VehicleArmCouplerFragmentReceipt {
 private static final String ID="CouplerControlIdentity",PAYLOAD="ArmPayload";
 private static void require(boolean condition,String message){VehicleCouplingPlan.require(condition,message);}
 /** Pure own-data validation; never descends into ForgeCaps or creates tiles. */
 public static void validateProjection(VehicleStructure original,VehicleStructure chosen,VehicleWorld.CouplerScope scope){
  if(original==chosen){chosen.requireCouplerScope(scope);return;}
  require(scope!=null,"Fragment validation requires saved external ownership");chosen.requireCouplerScope(scope);require(original.cells.size()==chosen.cells.size()&&original.displayOnly==chosen.displayOnly,"Fragment validation footprint changed");
  long rows=original.cells.size();for(VehicleStructure.Cell cell:original.cells){VehicleStructure.Cell next=chosen.at(cell.pos);require(next!=null&&cell.state==next.state&&cell.hasTileData()==next.hasTileData(),"Fragment validation changed native cells");if(!cell.hasTileData())continue;NBTTagCompound a=cell.tileData(),b=next.tileData();
   if(cell.state.getBlock() instanceof BlockCoupler)head(a,b);
   if(cell.state.getBlock() instanceof GearBlock&&cell.hasTileKey(PAYLOAD,10)){
    require(next.hasTileKey(PAYLOAD,10),"Fragment validation lost arm recipe");ArmPayload prior=ArmPayload.readForCouplerReceipt(a.getCompoundTag(PAYLOAD));rows+=prior.blocks.cells.size();require(rows<=2L*VehicleStructure.MAX_BLOCKS,"Fragment receipt metadata exceeds bounded ownership");NBTTagCompound selected=ArmPayload.readForCouplerReceipt(b.getCompoundTag(PAYLOAD)).write();
    if(prior.materialized())for(int i=0;i<prior.blocks.cells.size();i++){VehicleStructure.Cell own=prior.blocks.cells.get(i);if(own.state.getBlock() instanceof BlockCoupler){VehicleStructure.Cell physical=original.at(prior.placedCellPosition(cell.pos,i));require(physical!=null,"Original materialized head lost native occurrence");VehicleArmCouplerNativeReceipt.header(own,physical);}}
    NBTTagCompound normalized;try{normalized=payload(prior.write(),selected.copy());}catch(IllegalArgumentException changed){if(!prior.materialized())throw changed;normalized=payload(prior.snapshotPlaced(cell.pos,original).write(),selected.copy());}a.setTag(PAYLOAD,normalized);b.setTag(PAYLOAD,normalized.copy());
   }
   require(a.equals(b),"Fragment validation changed native own data or opaque capabilities");
  }
 }
 private static NBTTagCompound payload(NBTTagCompound raw,NBTTagCompound chosen){nested(raw,chosen,"Blocks");require(raw.hasKey("PlacedBlocks",10)==chosen.hasKey("PlacedBlocks",10),"Fragment validation changed endpoint mapping");if(raw.hasKey("PlacedBlocks",10))nested(raw,chosen,"PlacedBlocks");require(raw.equals(chosen),"Fragment validation changed arm ownership or opaque header");return raw;}
 private static void nested(NBTTagCompound raw,NBTTagCompound chosen,String key){VehicleStructure a=VehicleStructure.readDisplay(raw.getCompoundTag(key)),b=VehicleStructure.readDisplay(chosen.getCompoundTag(key));require(a.cells.size()==b.cells.size(),"Fragment validation changed payload count");List<VehicleStructure.Cell> left=new ArrayList<>(),right=new ArrayList<>();
  for(VehicleStructure.Cell cell:a.cells){VehicleStructure.Cell next=b.at(cell.pos);require(next!=null&&cell.state==next.state&&cell.hasTileData()==next.hasTileData()&&!(cell.state.getBlock() instanceof GearBlock),"Fragment validation changed payload geometry");NBTTagCompound x=cell.tileData(),y=next.tileData();if(cell.state.getBlock() instanceof BlockCoupler){require(x!=null&&y!=null,"Missing payload Coupler data");head(x,y);}require(Objects.equals(x,y),"Fragment validation changed payload own data");left.add(new VehicleStructure.Cell(cell.pos,cell.state,x));right.add(new VehicleStructure.Cell(next.pos,next.state,y));}
  raw.setTag(key,VehicleStructure.display(left).write());chosen.setTag(key,VehicleStructure.display(right).write());
 }
 private static void head(NBTTagCompound original,NBTTagCompound chosen){
  CouplerAssociation prior=CouplerAssociationData.read(original),next=CouplerAssociationData.read(chosen);require(next!=null&&chosen.hasUniqueId(ID)&&next.head.equals(chosen.getUniqueId(ID)),"Fragment chosen head identity unavailable");boolean had=original.hasKey(ID+"Most")||original.hasKey(ID+"Least");require(!had||original.hasUniqueId(ID)&&original.getUniqueId(ID).equals(next.head),"Fragment head lifetime changed");require(prior==null||had&&prior.head.equals(original.getUniqueId(ID)),"Original fragment proof identity mismatch");
  boolean oldReleased=original.getBoolean("VehicleArmCouplerReleased"),newReleased=chosen.getBoolean("VehicleArmCouplerReleased");if(oldReleased!=newReleased){require(oldReleased&&!newReleased&&!chosen.hasKey("VehicleArmCouplerReleased")&&next.state==CouplerAssociation.EXPECTED_PAIR,"Fragment release state changed without chosen pair");original.removeTag("VehicleArmCouplerReleased");if("Released".equals(original.getString("CouplerStatus"))&&"Ready".equals(chosen.getString("CouplerStatus")))original.setString("CouplerStatus","Ready");}
  for(String key:new String[]{CouplerAssociationData.KEY,ID+"Most",ID+"Least"}){original.removeTag(key);chosen.removeTag(key);}
 }
 /** Loaded validation of prior mate authority after pure journal admission, before native publication. */
 public static void validatePairs(VehicleStructure original,VehicleStructure chosen,VehicleWorld view,VehicleWorld.CouplerScope scope){
  if(scope==null)return;VehicleArmCouplers.Snapshot observed=VehicleArmCouplers.observe(chosen,view).ownership(scope.owner,scope.generation);Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads=new HashMap<>();Map<VehicleArmCouplers.Address,VehicleArmCouplers.Address> mates=new HashMap<>();Set<UUID> identities=new HashSet<>();Map<BlockPos,ArmPayload> payloads=new HashMap<>();
  for(VehicleArmCouplers.Head head:observed.heads){require(head.association!=null&&identities.add(head.association.head),"Fragment heads need distinct chosen identities");heads.put(head.address,head);}for(VehicleArmCouplerPairs.Pair pair:VehicleArmCouplerPairs.discoverForParking(observed).pairs){mates.put(pair.first.address,pair.second.address);mates.put(pair.second.address,pair.first.address);}
  for(VehicleArmCouplers.Head head:observed.heads){NBTTagCompound raw=originalHead(original,head.address,payloads);CouplerAssociation prior=CouplerAssociationData.read(raw);if(prior!=null&&prior.state==CouplerAssociation.EXPECTED_PAIR){VehicleArmCouplers.Address mate=mates.get(head.address);require(mate!=null&&!raw.getBoolean("VehicleArmCouplerReleased"),"Original fragment lost expected partner");CouplerAssociation other=CouplerAssociationData.read(originalHead(original,mate,payloads));require(other!=null&&other.state==CouplerAssociation.EXPECTED_PAIR&&prior.vehicle.equals(other.vehicle)&&prior.generation.equals(other.generation),"Original fragment prior partners disagree");}if(head.address.arm!=null&&head.nativeCell==null&&!mates.containsKey(head.address))require(prior!=null&&prior.state==CouplerAssociation.UNPAIRED,"Captured legacy orphan cannot gain fragment authority");}
 }
 private static NBTTagCompound originalHead(VehicleStructure source,VehicleArmCouplers.Address address,Map<BlockPos,ArmPayload> cache){if(address.arm==null)return source.at(address.cell).tileData();ArmPayload payload=cache.get(address.arm);if(payload==null){VehicleStructure.Cell root=source.at(address.arm);payload=ArmPayload.readForCouplerReceipt(root.tileData().getCompoundTag(PAYLOAD));if(payload.materialized())payload=payload.snapshotPlaced(root.pos,source);cache.put(address.arm,payload);}return payload.blocks.at(address.cell).tileData();}
 private VehicleArmCouplerFragmentReceipt(){}
}

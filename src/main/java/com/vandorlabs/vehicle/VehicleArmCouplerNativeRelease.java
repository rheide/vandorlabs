package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.BlockCoupler;
import com.vandorlabs.gear.ArmPayload;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Detached release preimage over the complete actual native pair; the durable caller is the publisher. */
final class VehicleArmCouplerNativeRelease {
 final VehicleArmCouplers.Snapshot prior;
 final Set<VehicleArmCouplers.Address> released;
 final VehicleStructure source;
 static VehicleWorld.CouplerScope scope(EntityGroundVehicle craft){UUID generation=craft.couplerAssociationGeneration();return generation==null?null:VehicleWorld.CouplerScope.external(craft.getUniqueID(),generation);}
 static boolean required(EntityGroundVehicle craft){return VehicleArmCouplers.runtime()||craft.couplerAssociationGeneration()!=null;}
 VehicleArmCouplerNativeRelease(EntityGroundVehicle craft,VehicleStructure proposed,BlockPos primary){
  VehicleCouplingPlan.require(craft.couplerScopeReady()&&craft.generalPoseReady()&&craft.currentGeneralPose().bodies.isEmpty(),"General body poses need a complete release rebase");prior=VehicleArmCouplers.observe(craft);VehicleArmCouplerPairs.Pair selected=null;
  for(VehicleArmCouplerPairs.Pair pair:VehicleArmCouplerPairs.discoverForParking(prior).pairs)if(primary.equals(pair.first.nativeCell)||primary.equals(pair.second.nativeCell)){VehicleCouplingPlan.require(selected==null,"Ambiguous native release pair");selected=pair;}
  VehicleCouplingPlan.require(selected!=null&&selected.first.nativeCell!=null&&selected.second.nativeCell!=null&&(!selected.first.requested||!selected.second.requested),"Native release requires the actual requested complete pair");
  released=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(selected.first.address,selected.second.address)));Map<BlockPos,VehicleStructure.Cell> patches=new HashMap<>();Map<BlockPos,ArmPayload> payloads=new HashMap<>();
  for(VehicleArmCouplers.Head head:new VehicleArmCouplers.Head[]{selected.first,selected.second}){
   VehicleStructure.Cell cell=proposed.at(head.nativeCell);VehicleCouplingPlan.require(cell!=null&&cell.state.getBlock() instanceof BlockCoupler&&cell.hasTileData(),"Native release head changed");NBTTagCompound data=cell.tileData();data.setBoolean("VehicleArmCouplerReleased",true);data.setString("CouplerStatus","Released");patches.put(cell.pos,new VehicleStructure.Cell(cell.pos,cell.state,data));
   if(head.address.arm!=null){VehicleStructure.Cell root=proposed.at(head.address.arm);VehicleCouplingPlan.require(root!=null&&root.hasTileKey("ArmPayload",10),"Materialized native release carrier disappeared");ArmPayload payload=payloads.get(head.address.arm);if(payload==null)payload=ArmPayload.readForCouplerReceipt(root.tileData().getCompoundTag("ArmPayload"));VehicleCouplingPlan.require(payload.materialized(),"Captured release must use its actual transformed contact");VehicleStructure.Cell own=payload.blocks.at(head.address.cell);VehicleCouplingPlan.require(own!=null&&own.hasTileData(),"Materialized release header disappeared");payloads.put(head.address.arm,payload.releaseMaterializedCoupler(head.address.cell,cell.state.getValue(BlockCoupler.LOCKED),own.tileData()));}
  }
  for(Map.Entry<BlockPos,ArmPayload> entry:payloads.entrySet()){VehicleStructure.Cell root=proposed.at(entry.getKey());NBTTagCompound data=root.tileData();data.setTag("ArmPayload",entry.getValue().write());patches.put(root.pos,new VehicleStructure.Cell(root.pos,root.state,data));}
  List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:proposed.cells)cells.add(patches.getOrDefault(cell.pos,cell));source=new VehicleStructure(cells,proposed.seat,proposed.facing,proposed.gearCount);
 }
 private VehicleArmCouplerNativeRelease(){throw new AssertionError();}
}

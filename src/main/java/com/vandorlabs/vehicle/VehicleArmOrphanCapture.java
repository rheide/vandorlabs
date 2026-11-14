package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Same-owner native/captured relocation only. This cannot adopt a head or renew an ownership proof. */
final class VehicleArmOrphanCapture {
 static void require(EntityGroundVehicle craft,GearTileEntity arm,ArmPayload captured,VehicleArmCouplers.Snapshot observed){
  if(craft==null||craft.world==null||craft.world.isRemote||craft.world.getMinecraftServer()==null||!craft.world.getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Orphan recapture requires the owning server thread");
  if(craft.world.getEntityByID(craft.getEntityId())!=craft||craft.structure==null||craft.view==null||craft.view.craft!=craft||craft.view.structure!=craft.structure||arm==null||craft.view.tiles.get(arm.getPos())!=arm||arm.getWorld()!=craft.view||craft.transferInProgress||craft.ownershipBlocked||!craft.couplerScopeReady())throw new IllegalArgumentException("Installed ready orphan owner required");
  VehicleWorld.CouplerScope scope=craft.view.couplerScope();if(scope==null||!scope.owner.equals(craft.getUniqueID())||!scope.generation.equals(craft.couplerAssociationGeneration()))throw new IllegalArgumentException("Actual installed orphan scope required");
  craft.view.requireCouplerScope();selected(scope,arm.getPos(),captured,observed);
 }
 /** Detached comparison seam. Supplying scalar headers here never grants gameplay authority. */
 static void selected(VehicleWorld.CouplerScope scope,BlockPos root,ArmPayload captured,VehicleArmCouplers.Snapshot observed){
  if(scope==null||root==null||captured==null||captured.materialized()||captured.hasOwnershipJournal()||observed==null||captured.blocks.couplers.isEmpty()||captured.blocks.couplers.size()>VehicleArmCouplers.MAX_HEADS)throw new IllegalArgumentException("Complete unpaired recapture descriptor required");
  Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> heads=new HashMap<>();int selected=0;for(VehicleArmCouplers.Head head:observed.heads){if(heads.put(head.address,head)!=null)throw new IllegalArgumentException("Duplicate observed orphan address");if(root.equals(head.address.arm))selected++;}
  if(selected!=0&&selected!=captured.blocks.couplers.size())throw new IllegalArgumentException("Selected orphan ownership changed");
  for(VehicleStructure.Cell cell:captured.blocks.couplers){NBTTagCompound data=cell.tileData();CouplerAssociation proof=CouplerAssociationData.read(data);VehicleArmCouplers.Head actual=heads.get(VehicleArmCouplers.Address.payloadCell(root,cell.pos));boolean nativeAlias=actual==null&&selected==0;BlockPos nativeCell=captured.placedCellPosition(root,captured.blocks.cells.indexOf(cell));if(nativeAlias)actual=heads.get(VehicleArmCouplers.Address.nativeCell(nativeCell));if(proof==null||actual==null||actual.nativeCell==null||!(nativeAlias?nativeCell.equals(actual.nativeCell)&&nativeCell.equals(actual.carrier):root.equals(actual.carrier))||actual.association==null||!proof.permitsUnpaired(proof.head,scope.owner,scope.generation)||!actual.association.permitsUnpaired(proof.head,scope.owner,scope.generation)||!observed.permitsUnpaired(actual)||!data.hasUniqueId("CouplerControlIdentity")||!data.getUniqueId("CouplerControlIdentity").equals(proof.head))throw new IllegalArgumentException("Selected orphan lacks the unchanged external owner proof");}
 }
 private VehicleArmOrphanCapture(){}
}

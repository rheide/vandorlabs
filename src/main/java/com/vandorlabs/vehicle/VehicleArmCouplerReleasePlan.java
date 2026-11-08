package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.coupler.BlockCoupler;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Detached captured-head split preparation. Existing VehicleCouplingTransfer remains the sole publisher. */
public final class VehicleArmCouplerReleasePlan {
 public enum Status {PENDING,READY,REFUSED,CURRENT_GRID_REQUIRED}
 public static final class Prepared {
  private final EntityGroundVehicle craft;private final EntityPlayerMP actor;private NBTTagCompound before;private VehicleStructure source,proposed;private VehicleWorld originalView,view;private VehicleScan component,retained,detached;private VehicleArmCouplerReleaseContact contact;private BlockPos primary;private double x,y,z;private float yaw;private VehicleCouplingPlan plan;private Status status=Status.PENDING;private String error="Checking separated vehicle validity";
  private Prepared(EntityGroundVehicle craft,EntityPlayerMP actor){this.craft=craft;this.actor=actor;}
  public Status status(){return status;}public String error(){return error;}public VehicleCouplingPlan couplingPlan(){if(status!=Status.READY)throw new IllegalStateException("Captured release is not ready");return plan;}
  private void initialize(VehicleArmCouplers.Address selected)throws Exception{
   thread();VehicleCouplingPlan.require(selected!=null&&available(),"Captured release owner or authorizer unavailable");VehicleCouplingPlan.require(VehicleCouplings.authorizer(craft).getUniqueID().equals(actor.getUniqueID()),"Captured release authorizer changed");VehicleCouplingPlan.require(craft.stopped(),"Stop the vehicle before detaching; section remains attached");
   // This ordinary entity writer first flushes pending known controls. No ownership is moved.
   before=craft.writeToNBT(new NBTTagCompound());source=craft.structure;originalView=craft.view;x=craft.posX;y=craft.posY;z=craft.posZ;yaw=craft.rotationYaw;
   VehicleArmCouplerPairs.Pair chosen=null;for(VehicleArmCouplerPairs.Pair pair:VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(craft)).pairs)if(pair.first.address.equals(selected)||pair.second.address.equals(selected)){VehicleCouplingPlan.require(chosen==null,"Ambiguous captured release pair");chosen=pair;}
   VehicleCouplingPlan.require(chosen!=null&&(chosen.first.address.arm!=null||chosen.second.address.arm!=null),"Selected head has no closed captured partner");VehicleCouplingPlan.require(!chosen.first.released&&!chosen.second.released&&(!chosen.first.requested||!chosen.second.requested),"A closed pair must actually request release");contact=VehicleArmCouplerReleaseContact.capture(craft,chosen);
   VehicleArmCouplers.Head clicked=chosen.first.address.equals(selected)?chosen.first:chosen.second;Vec3d clickedCenter=craft.toWorld(new Vec3d(clicked.x,clicked.y,clicked.z),clicked.carrier);VehicleService.permission(actor,new BlockPos(clickedCenter));
   int owned=source.cells.size();for(TileEntity tile:originalView.tiles.values())if(tile instanceof GearTileEntity){ArmPayload payload=((GearTileEntity)tile).payload();if(payload!=null&&!payload.materialized()){owned=Math.addExact(owned,payload.blocks.cells.size());VehicleCouplingPlan.require(owned<=VehicleStructure.MAX_BLOCKS,"Released native/captured ownership exceeds bounded split capacity");}}
   Map<BlockPos,VehicleStructure.Cell> patches=new HashMap<>();Map<BlockPos,ArmPayload> payloads=new HashMap<>();patch(chosen.first,patches,payloads);patch(chosen.second,patches,payloads);
   for(Map.Entry<BlockPos,ArmPayload> entry:payloads.entrySet()){GearTileEntity arm=(GearTileEntity)originalView.tiles.get(entry.getKey());VehicleStructure.Cell root=source.at(entry.getKey());NBTTagCompound data=arm.currentData(root.tileData());data.setTag("ArmPayload",entry.getValue().write());patches.put(root.pos,new VehicleStructure.Cell(root.pos,root.state,data));}
   List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:source.cells)cells.add(patches.getOrDefault(cell.pos,cell));proposed=new VehicleStructure(cells,source.seat,source.facing,source.gearCount);proposed.encode();
   VehicleCouplingPlan.require(source.hasDedicatedController(),"Keep a dedicated Vehicle Controller with the retained driver");primary=source.controller().pos;view=new VehicleWorld(craft.world,proposed,craft.currentCouplerScope());view.hover=craft.hover;component=VehicleScan.attachment(view,primary,Collections.emptySet());
  }
  private void patch(VehicleArmCouplers.Head head,Map<BlockPos,VehicleStructure.Cell> patches,Map<BlockPos,ArmPayload> payloads){
   if(head.address.arm==null){VehicleStructure.Cell cell=source.at(head.address.cell);VehicleCouplingPlan.require(cell!=null&&cell.state.getBlock() instanceof BlockCoupler&&cell.hasTileData(),"Native release head changed");NBTTagCompound data=cell.tileData();data.setBoolean("VehicleArmCouplerReleased",true);data.setString("CouplerStatus","Released");patches.put(cell.pos,new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockCoupler.LOCKED,head.requested),data));}
   else{TileEntity tile=originalView.tiles.get(head.address.arm);VehicleCouplingPlan.require(tile instanceof GearTileEntity,"Captured release carrier disappeared");GearTileEntity arm=(GearTileEntity)tile;ArmPayload payload=payloads.getOrDefault(head.address.arm,arm.payload());VehicleCouplingPlan.require(payload!=null&&!payload.materialized()&&!payload.hasOwnershipJournal(),"Captured release ownership unavailable");VehicleStructure.Cell cell=payload.blocks.at(head.address.cell);VehicleCouplingPlan.require(cell!=null&&cell.hasTileData(),"Captured release head disappeared");NBTTagCompound data=cell.tileData();data.setString("CouplerStatus","Released");payloads.put(head.address.arm,payload.releaseCoupler(head.address.cell,head.requested,data));}
  }
  /** Total scan work is split between the three scans; no pending operation is a gameplay refusal. */
  public Status step(int budget){if(budget<3||budget>8192)throw new IllegalArgumentException("Release scan budget");thread();if(status!=Status.PENDING)return status;try{
   if(!quick())return refused(Status.REFUSED,"Vehicle pose, controls, contents or authorizer changed during release preparation");int each=budget/3;
   if(!component.step(each))return status;if(component.error!=null)return refused(Status.REFUSED,component.error);
   if(retained==null){Set<BlockPos> kept=new HashSet<>();for(VehicleStructure.Cell cell:component.result.cells)kept.add(cell.pos.add(component.origin));if(kept.size()==source.cells.size())return refused(Status.CURRENT_GRID_REQUIRED,"Another connection still joins the sections; captured physical-only release is not prepared");BlockPos controller=null,seat=null;for(VehicleStructure.Cell cell:proposed.cells)if(!kept.contains(cell.pos)){if(cell.state.getBlock() instanceof BlockVehicleController&&controller==null)controller=cell.pos;if(cell.state.getBlock() instanceof BlockPilotSeat&&!cell.state.getValue(BlockPilotSeat.UPPER)&&seat==null)seat=cell.pos;}if(controller==null||seat==null)return refused(Status.CURRENT_GRID_REQUIRED,"The released section is not a complete vehicle; native-grid fallback required");retained=new VehicleScan(view,source.seat);detached=new VehicleScan(view,controller);}
   boolean left=retained.step(each),right=detached.step(each);if(!left||!right)return status;if(retained.error!=null||detached.error!=null)return refused(Status.CURRENT_GRID_REQUIRED,"Two complete separated vehicles are required: "+retained.error+" / "+detached.error);if(!same())return refused(Status.REFUSED,"Vehicle changed before split admission");
   plan=VehicleCouplingPlan.split(craft,actor,primary,proposed,retained,detached,contact);status=Status.READY;error="Ready";return status;
  }catch(Exception|LinkageError failure){return refused(Status.REFUSED,message(failure));}}
  /** Recheck immediately before submitting the returned plan to the existing durable publisher. */
  public boolean recheck(){thread();if(status!=Status.READY)return false;try{if(!same()){refused(Status.REFUSED,"Vehicle changed before release submission");return false;}plan.validate(actor);return true;}catch(Exception|LinkageError failure){refused(Status.REFUSED,message(failure));return false;}}
  private boolean available(){return craft!=null&&actor!=null&&craft.world instanceof WorldServer&&!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress&&craft.couplerScopeReady()&&craft.generalPoseReady()&&craft.structure!=null&&craft.view!=null&&craft.view.craft==craft&&craft.view.structure==craft.structure&&craft.world.getEntityByID(craft.getEntityId())==craft&&!VehicleCouplingTransfer.busy(craft)&&actor.world==craft.world&&!actor.isDead&&!actor.isSpectator()&&actor.connection!=null&&actor.connection.netManager.isChannelOpen();}
  private boolean quick(){return available()&&craft.view==originalView&&craft.structure==source&&craft.stopped()&&craft.posX==x&&craft.posY==y&&craft.posZ==z&&craft.rotationYaw==yaw;}
  private boolean same(){if(!quick())return false;NBTTagCompound now=craft.writeToNBT(new NBTTagCompound());for(String key:new String[]{"Craft","Pos","Rotation","CouplerJointPose","OccupiedPilotSeats","HoverMode","HoverTarget","AutopilotNavigation","CouplerBodyPose",EntityGroundVehicle.COUPLER_GENERATION+"Most",EntityGroundVehicle.COUPLER_GENERATION+"Least"})if(!Objects.equals(before.getTag(key),now.getTag(key)))return false;return true;}
  private void thread(){if(craft==null||!(craft.world instanceof WorldServer)||!((WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Captured release requires owning server thread");}
  private Status refused(Status result,String reason){status=result;error=reason==null?"Captured release refused":reason.length()>256?reason.substring(0,256):reason;plan=null;return status;}
 }
 public static Prepared prepare(EntityGroundVehicle craft,EntityPlayerMP actor,VehicleArmCouplers.Address selected){Prepared result=new Prepared(craft,actor);try{result.initialize(selected);}catch(Exception|LinkageError failure){result.refused(Status.REFUSED,message(failure));}return result;}
 private static String message(Throwable failure){return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
 private VehicleArmCouplerReleasePlan(){}
}

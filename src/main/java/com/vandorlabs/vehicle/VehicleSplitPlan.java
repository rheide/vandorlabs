package com.vandorlabs.vehicle;

import com.vandorlabs.physics.RigidTransform;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;

/** Two complete vehicles retain mode and heading; only a bounded clear suspension settling is allowed. */
public final class VehicleSplitPlan {
    final EntityGroundVehicle craft;
    final NBTTagCompound before,first,second;
    final VehicleStructure retained,detached;
    final BlockPos primary,retainedOrigin,detachedOrigin;
    final UUID actor;
    VehicleSplitPlan(EntityGroundVehicle craft,EntityPlayerMP actor,BlockPos primary,VehicleStructure proposed,VehicleScan left,VehicleScan right)throws Exception{
        this.craft=craft;this.actor=actor.getUniqueID();this.primary=primary;before=craft.writeToNBT(new NBTTagCompound());retainedOrigin=left.origin;detachedOrigin=right.origin;
        retained=preserve(proposed,left);detached=preserve(proposed,right);Set<BlockPos> members=new HashSet<>();for(VehicleStructure.Cell c:retained.cells)VehicleCouplingPlan.require(members.add(c.pos.add(left.origin)),"Retained vehicle contains duplicate cells");for(VehicleStructure.Cell c:detached.cells)VehicleCouplingPlan.require(members.add(c.pos.add(right.origin)),"Split vehicles overlap in saved ownership");VehicleCouplingPlan.require(members.size()==proposed.cells.size(),"Release one connection at a time; both halves must account for every vehicle block");
        VehicleCouplingPlan.require(retained.seat.add(left.origin).equals(craft.structure.seat),"Keep the main Pilot Seat with the retained Vehicle Controller");
        for(Entity rider:craft.getPassengers())VehicleCouplingPlan.require(retained.at(craft.seatForPassenger(rider).subtract(left.origin))!=null,"Empty the detached section's seats before releasing it");
        first=entity(retained,left.origin,before.copy(),craft.getUniqueID());EntityGroundVehicle fresh=new EntityGroundVehicle(craft.world);
        try{NBTTagCompound clean=fresh.writeToNBT(new NBTTagCompound());clean.setBoolean("HoverMode",craft.hover);clean.setUniqueId("CouplingActor",actor.getUniqueID());second=entity(detached,right.origin,clean,fresh.getUniqueID());}finally{fresh.setDead();}
        validate(actor);
    }
    private static VehicleStructure preserve(VehicleStructure original,VehicleScan scan){
        VehicleCouplingPlan.require(scan.result!=null&&!scan.result.displayOnly,"Both halves need complete vehicle scans: "+scan.error);List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:scan.result.cells){VehicleStructure.Cell source=original.at(cell.pos.add(scan.origin));VehicleCouplingPlan.require(source!=null&&source.state.getBlock()==cell.state.getBlock(),"Vehicle changed during split scan");cells.add(new VehicleStructure.Cell(cell.pos,source.state,source.hasTileData()?VehicleTileData.translated(source.tileData(),BlockPos.ORIGIN.subtract(scan.origin)):null));}
        return VehicleControlSettings.migrate(new VehicleStructure(cells,scan.result.seat,scan.result.facing,scan.result.gearCount));
    }
    private NBTTagCompound entity(VehicleStructure structure,BlockPos origin,NBTTagCompound tag,UUID id)throws Exception{
        VehicleCouplingPlan.require(structure.hasDedicatedController(),"Both halves need a dedicated Vehicle Controller");VehicleWorld view=new VehicleWorld(craft.world,structure);MovingBlockSupport.validate(structure,view);structure.encode();
        if(craft.hover){VehicleFlight.requireThruster(structure);VehicleCouplingPlan.require(VehicleFlight.canHover(structure),"Both Hover vehicles need sufficient lift in every connected section");}else VehicleCouplingPlan.require(!structure.activeNonWheeledGear()||structure.activeSkisOnly(),"Both Taxi vehicles need wheels or skis");
        BlockPos oldRoot=structure.controller().pos.add(origin);RigidTransform root=craft.articulation.body(craft.articulation.bodyAt(oldRoot),1);VehicleCouplingPlan.require(Math.toDegrees(Math.acos(MathHelper.clamp(root.r[4],-1,1)))<=3,"Level the detached driver's segment before separating; its vehicle root must be horizontal");
        double px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2;Vec3d center=craft.toWorld(new Vec3d(origin.getX()+px,origin.getY(),origin.getZ()+pz),oldRoot);float yaw=craft.rotationYaw+(float)Math.toDegrees(Math.atan2(root.r[6],root.r[0]));
        tag.setUniqueId("UUID",id);tag.setTag("Craft",structure.write());tag.setTag("Pos",doubles(center.x-px,center.y,center.z-pz));NBTTagList rotation=new NBTTagList();rotation.appendTag(new NBTTagFloat(yaw));rotation.appendTag(new NBTTagFloat(0));tag.setTag("Rotation",rotation);tag.setTag("Motion",doubles(0,0,0));tag.setDouble("HoverTarget",center.y);tag.setBoolean("TaxiReturnPending",false);tag.removeTag("TaxiReturnSeat");tag.removeTag("TaxiReturnActorMost");tag.removeTag("TaxiReturnActorLeast");tag.removeTag("CouplerJointPose");
        NBTTagCompound seats=tag.getCompoundTag("OccupiedPilotSeats");for(String name:new HashSet<>(seats.getKeySet())){BlockPos local=BlockPos.fromLong(seats.getLong(name)).subtract(origin);if(structure.at(local)==null)seats.removeTag(name);else seats.setLong(name,local.toLong());}
        EntityGroundVehicle check=new EntityGroundVehicle(craft.world);
        try{tag.setTag("AutopilotNavigation",check.autopilot.write());check.readFromNBT(tag);VehicleCouplingPlan.require(check.structure!=null,"Split vehicle snapshot cannot be loaded");NBTTagCompound pose=check.articulation.splitPose(craft,origin);check.articulation.read(pose);if(pose.hasKey("CouplerJointPose",10))tag.setTag("CouplerJointPose",pose.getCompoundTag("CouplerJointPose"));
            boolean settle=false;for(VehicleStructure.Cell c:structure.cells){BlockPos old=c.pos.add(origin);for(int n=0;n<8;n++){Vec3d a=new Vec3d(c.pos).addVector((n&1)==0?0:1,(n&2)==0?0:1,(n&4)==0?0:1);double distance=check.toWorld(a,c.pos).squareDistanceTo(craft.toWorld(a.add(new Vec3d(origin)),old));VehicleCouplingPlan.require(distance<=.75*.75,"Level the detached driver's segment before separating");settle|=distance>=1e-8;}}
            if(settle||!id.equals(craft.getUniqueID())){craft.articulation.alignSplit(check,origin,primary);tag.setTag("Pos",doubles(check.posX,check.posY,check.posZ));tag.setDouble("HoverTarget",check.posY);}
            VehicleCouplingPlan.require(check.articulation.loaded(check.posX,check.posY,check.posZ,0,0,0,check.rotationYaw),"Load both complete vehicles before releasing the coupler");
        }finally{check.setDead();}
        return tag;
    }
    static NBTTagList doubles(double... values){NBTTagList list=new NBTTagList();for(double v:values)list.appendTag(new NBTTagDouble(v));return list;}
    void validate(EntityPlayerMP player){
        VehicleCouplingPlan.require(player!=null&&player.getUniqueID().equals(actor)&&player.world==craft.world&&!player.isDead&&!player.isSpectator()&&player.connection!=null&&player.connection.netManager.isChannelOpen(),"The player authorizing separation must remain available");
        VehicleCouplingPlan.require(!craft.isDead&&!craft.ownershipBlocked&&craft.structure!=null&&craft.stopped(),"Stop both sections before separating");NBTTagCompound now=craft.writeToNBT(new NBTTagCompound());for(String key:new String[]{"Craft","Pos","Rotation","CouplerJointPose","OccupiedPilotSeats","HoverMode"})VehicleCouplingPlan.require(Objects.equals(before.getTag(key),now.getTag(key)),"Vehicle pose, occupants or contents changed while preparing separation; retry");
        for(VehicleStructure.Cell c:craft.structure.cells){BlockPos at=new BlockPos(craft.toWorld(new Vec3d(c.pos).addVector(.5,.5,.5),c.pos));VehicleCouplingPlan.require(craft.world.isBlockLoaded(at,false),"Load both complete sections");VehicleService.permission(player,at);}
        EntityGroundVehicle check=new EntityGroundVehicle(craft.world);try{check.readFromNBT(second);VehicleCouplingPlan.require(check.structure!=null,"Detached vehicle data became unavailable");double x=check.posX,y=check.posY,z=check.posZ;craft.articulation.alignSplit(check,detachedOrigin,primary);VehicleCouplingPlan.require(Math.abs(check.posX-x)+Math.abs(check.posY-y)+Math.abs(check.posZ-z)<1e-7,"Separation clearance changed while preparing; retry");}finally{check.setDead();}
    }
}

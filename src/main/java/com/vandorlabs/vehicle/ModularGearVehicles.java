package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import java.util.*;

/** Captured gear uses the same local pose and source membership as stationary assemblies. */
public final class ModularGearVehicles {
    private ModularGearVehicles(){}
    private static final class Correction {final double progress;final int revision;Correction(double p,int r){progress=p;revision=r;}}
    private static final Map<EntityGroundVehicle,Map<BlockPos,Correction>> corrections=new WeakHashMap<>();
    public static void correct(EntityGroundVehicle craft,Map<BlockPos,Double> desired){Map<BlockPos,Correction> pending=corrections.computeIfAbsent(craft,k->new LinkedHashMap<>());for(Map.Entry<BlockPos,Double> entry:desired.entrySet()){TileEntity raw=craft.view.getTileEntity(entry.getKey());if(raw instanceof GearTileEntity)pending.put(entry.getKey(),new Correction(entry.getValue(),((GearTileEntity)raw).motionRevision()));}}
    public static boolean extended(EntityGroundVehicle craft){if(craft.view==null)return false;for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()&&!((GearTileEntity)tile).fullyDeployed())return false;return true;}
    public static void tick(EntityGroundVehicle craft){
        if(craft.view==null||craft.transferInProgress||craft.ownershipBlocked)return;Map<BlockPos,Double> desired=new LinkedHashMap<>();Set<BlockPos> notify=new LinkedHashSet<>();VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);
        Map<BlockPos,Correction> corrected=craft.world.isRemote?corrections.get(craft):null;
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;if(!gear.isOperational())continue;double before=gear.progress();
            if(!craft.world.isRemote)gear.input();double next=GearMotion.advance(before,gear.target(),1,GearTileEntity.ANIMATION_TICKS);
            if(craft.world.isRemote){Correction correction=corrected==null?null:corrected.get(gear.getPos());if(correction!=null&&correction.revision==gear.motionRevision())next=correction.progress;else if(gear.motionBlocked())next=before;}
            if(!craft.hover&&next<before)next=before;
            if(next==before){gear.captureProgress(before);continue;}
            if(!craft.world.isRemote){List<AxisAlignedBB> old=GearGeometry.boxes(gear,before),after=GearGeometry.boxes(gear,next);boolean blocked=false;
                for(int i=0;i<after.size()&&!blocked;i++){if(old.get(i).equals(after.get(i))||after.get(i).getAverageEdgeLength()==0)continue;AxisAlignedBB swept=GearGeometry.swept(gear,i,old.get(i),after.get(i),before,next);
                    if(!GearGeometry.foreign(craft.view,gear,swept).isEmpty()){blocked=true;break;}
                    AxisAlignedBB at=pose.world(swept,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
                    if(!craft.world.isAreaLoaded(new BlockPos(at.minX,at.minY,at.minZ),new BlockPos(at.maxX,at.maxY,at.maxZ),false)||!craft.world.getCollisionBoxes(craft,at).isEmpty())blocked=true;
                }if(gear.captureBlocked(blocked))notify.add(gear.getPos());
                if(blocked){gear.captureProgress(before);continue;}
            }
            desired.put(gear.getPos(),next);
        }
        if(!notify.isEmpty())com.vandorlabs.network.MessageGearMotion.send(craft,notify);
        if(desired.isEmpty())return;Map<BlockPos,Float> geometry=new LinkedHashMap<>();for(Map.Entry<BlockPos,Double> entry:desired.entrySet())geometry.put(entry.getKey(),entry.getValue().floatValue());
        if(!craft.collisionCache.readyForCanopyPose(craft.view,geometry))return;
        if(!craft.world.isRemote)for(Map.Entry<BlockPos,Double> entry:desired.entrySet()){GearTileEntity gear=(GearTileEntity)craft.view.getTileEntity(entry.getKey());List<AxisAlignedBB> before=new ArrayList<>(),after=new ArrayList<>();for(AxisAlignedBB box:GearGeometry.boxes(gear,gear.progress()))before.add(pose.world(box,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2));for(AxisAlignedBB box:GearGeometry.boxes(gear,entry.getValue()))after.add(pose.world(box,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2));GearGeometry.pushEntities(craft.world,before,after,craft);}
        for(Map.Entry<BlockPos,Double> entry:desired.entrySet())((GearTileEntity)craft.view.getTileEntity(entry.getKey())).captureProgress(entry.getValue());
        if(craft.world.isRemote)corrections.remove(craft);
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(desired.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,craft.view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound())):cell);
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),true);
        if(!craft.world.isRemote)com.vandorlabs.network.MessageGearMotion.send(craft,desired.keySet());
    }
}

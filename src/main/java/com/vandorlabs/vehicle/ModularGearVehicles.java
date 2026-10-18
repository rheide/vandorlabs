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
    public static boolean extended(EntityGroundVehicle craft){if(craft.view==null)return false;for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()&&!((GearTileEntity)tile).fullyDeployed())return false;return true;}
    public static void tick(EntityGroundVehicle craft){
        if(craft.view==null||craft.transferInProgress||craft.ownershipBlocked)return;Map<BlockPos,Double> desired=new LinkedHashMap<>();VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;if(!gear.isOperational())continue;double before=gear.progress();
            if(!craft.world.isRemote)gear.input();double next=GearMotion.advance(before,gear.target(),1,GearTileEntity.ANIMATION_TICKS);if(!craft.hover&&next<before)next=before;
            if(next==before){gear.captureProgress(before);continue;}
            if(!craft.world.isRemote){List<AxisAlignedBB> old=GearGeometry.boxes(gear,before),after=GearGeometry.boxes(gear,next);boolean blocked=false;
                for(int i=0;i<after.size()&&!blocked;i++){if(old.get(i).equals(after.get(i))||after.get(i).getAverageEdgeLength()==0)continue;AxisAlignedBB swept=GearGeometry.swept(gear,i,old.get(i),after.get(i),before,next);
                    if(!GearGeometry.foreign(craft.view,gear,swept).isEmpty()){blocked=true;break;}
                    AxisAlignedBB at=pose.world(swept,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
                    if(!craft.world.isAreaLoaded(new BlockPos(at.minX,at.minY,at.minZ),new BlockPos(at.maxX,at.maxY,at.maxZ),false)||!craft.world.getCollisionBoxes(craft,at).isEmpty())blocked=true;
                }if(blocked){gear.captureProgress(before);continue;}
            }
            desired.put(gear.getPos(),next);
        }
        if(desired.isEmpty())return;Map<BlockPos,Float> geometry=new LinkedHashMap<>();for(Map.Entry<BlockPos,Double> entry:desired.entrySet())geometry.put(entry.getKey(),entry.getValue().floatValue());
        if(!craft.collisionCache.readyForCanopyPose(craft.view,geometry))return;
        for(Map.Entry<BlockPos,Double> entry:desired.entrySet())((GearTileEntity)craft.view.getTileEntity(entry.getKey())).captureProgress(entry.getValue());
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(desired.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,craft.view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound())):cell);
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),true);if(!craft.world.isRemote)for(BlockPos p:desired.keySet())craft.signals.changed(p);
    }
}

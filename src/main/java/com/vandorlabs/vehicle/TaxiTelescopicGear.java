package com.vandorlabs.vehicle;

import java.util.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;

/** Taxi keeps matched telescopic feet stationary while the chassis changes height. */
final class TaxiTelescopicGear {
    private TaxiTelescopicGear(){}
    private static boolean integral(double value){return Math.abs(value-Math.rint(value))<1e-5;}
    static List<TileEntityLandingGear> supports(EntityGroundVehicle craft){
        List<TileEntityLandingGear> roots=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells)if(craft.structure.isActive(cell.pos)){
            TileEntity tile=craft.view.getTileEntity(cell.pos);
            if(tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).isLandingAssembly())return Collections.emptyList();
            if(cell.state.getBlock() instanceof BlockLandingGear&&!(cell.state.getBlock() instanceof BlockTelescopicLandingGear))return Collections.emptyList();
            if(tile instanceof TileEntityLandingGear&&((TileEntityLandingGear)tile).isRoot())roots.add((TileEntityLandingGear)tile);
        }
        return roots;
    }
    static boolean canTarget(EntityGroundVehicle craft,boolean extend){
        if(!craft.stopped()||!integral(craft.rotationYaw/90D)||!integral(craft.posX)||!integral(craft.posZ))return false;
        List<TileEntityLandingGear> roots=supports(craft);if(roots.size()<3)return false;
        double distance=Double.NaN;List<VehicleStructure.Cell> cells=new ArrayList<>();Map<BlockPos,TileEntityLandingGear> byPos=new HashMap<>();
        for(TileEntityLandingGear gear:roots){
            double target=extend?gear.getExtensionPixels()/16D:0,shift=target-gear.progress;
            if(!integral(target)||!integral(craft.posY+shift)||!Double.isNaN(distance)&&Math.abs(distance-shift)>1e-5)return false;
            distance=shift;byPos.put(gear.getPos(),gear);
        }
        for(VehicleStructure.Cell cell:craft.structure.cells){TileEntityLandingGear gear=byPos.get(cell.pos);if(gear==null){cells.add(cell);continue;}
            NBTTagCompound tag=gear.writeToNBT(new NBTTagCompound());tag.setBoolean("TaxiGroundedGear",true);tag.setFloat("Progress",extend?gear.getExtensionPixels()/16F:0);
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockTelescopicLandingGear.EXTENDED,extend),tag));
        }
        try{
            VehicleStructure next=new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);
            VehicleStructure placed=VehicleRotation.rotate(next,new VehicleWorld(craft.world,next),VehicleRotation.turns(craft.rotationYaw));
            double x=craft.posX+craft.structure.bounds.maxX/2-placed.bounds.maxX/2,z=craft.posZ+craft.structure.bounds.maxZ/2-placed.bounds.maxZ/2;
            if(!integral(x)||!integral(z))return false;
            VehicleRotation.Plan plan=VehicleLandingGear.parking(placed,new BlockPos(Math.round(x),Math.round(craft.posY+distance),Math.round(z)));
            VehicleWorld view=new VehicleWorld(craft.world,plan.structure);VehicleCollision geometry=new VehicleCollision(view);
            if(!geometry.loaded(craft.world,plan.origin.getX(),plan.origin.getY(),plan.origin.getZ(),0,0,0,0))return false;
            for(VehicleStructure.Cell cell:plan.structure.cells)if(!craft.world.isAirBlock(plan.origin.add(cell.pos)))return false;
            for(AxisAlignedBB box:geometry.boxes)if(!craft.world.getCollisionBoxes(craft,box.offset(plan.origin).shrink(1e-6)).isEmpty())return false;
            return true;
        }catch(RuntimeException refused){return false;}
    }
    static void requireParkable(EntityGroundVehicle craft){
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityLandingGear&&craft.structure.isActive(raw.getPos())){
            TileEntityLandingGear gear=(TileEntityLandingGear)raw;if(!gear.isRoot()||!gear.taxiGrounded)continue;
            double target=craft.view.getBlockState(gear.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED)?gear.getExtensionPixels()/16D:0;
            if(Math.abs(gear.progress-target)>1e-5)throw new IllegalArgumentException("Wait for the Taxi landing height adjustment to finish before parking");
        }
    }
    static double shift(EntityGroundVehicle craft,Map<BlockPos,Float> desired){
        List<TileEntityLandingGear> roots=supports(craft);if(roots.size()<3)return Double.NaN;
        double shift=Double.NaN;Map<TileEntityLandingGear,Float> before=new LinkedHashMap<>();
        for(TileEntityLandingGear gear:roots){Float next=desired.get(gear.getPos());if(next==null)return Double.NaN;double endpoint=craft.view.getBlockState(gear.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED)?gear.getExtensionPixels()/16D:0;if(!integral(craft.posY+endpoint-gear.progress))return Double.NaN;double delta=next-gear.progress;if(!Double.isNaN(shift)&&Math.abs(shift-delta)>1e-5)return Double.NaN;shift=delta;before.put(gear,gear.progress);}
        VehiclePose oldPose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw),nextPose=new VehiclePose(craft.posX,craft.posY+shift,craft.posZ,craft.rotationYaw);
        List<AxisAlignedBB> old=VehicleCollision.capture(craft.view).boxes;
        try{
            for(TileEntityLandingGear gear:roots)gear.progress=desired.get(gear.getPos());
            VehicleCollision.Raw raw=VehicleCollision.capture(craft.view);if(!raw.complete||old.size()!=raw.boxes.size())return Double.NaN;
            for(int i=0;i<old.size();i++){
                AxisAlignedBB a=oldPose.world(old.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),b=nextPose.world(raw.boxes.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),swept=a.union(b).shrink(1e-6);
                if(!craft.world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false)||!craft.world.getCollisionBoxes(craft,swept).isEmpty())return Double.NaN;
            }
            return shift;
        }finally{for(Map.Entry<TileEntityLandingGear,Float> entry:before.entrySet())entry.getKey().progress=entry.getValue();}
    }
}

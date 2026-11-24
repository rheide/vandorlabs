package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPropulsionLight;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Capability checks and loaded-only clearance beneath the craft's collision footprint. */
public final class VehicleFlight {
    private VehicleFlight(){}
    public static boolean lift(net.minecraft.block.Block block){
        if(!(block instanceof BlockPropulsionLight) || block.getRegistryName()==null)return false;
        String name=block.getRegistryName().getResourcePath();
        return name.startsWith("antigravity_plate") || name.startsWith("repulsor_array") || name.startsWith("vertical_hover_thruster");
    }
    public static boolean exhaust(net.minecraft.block.Block block){
        if(!(block instanceof BlockPropulsionLight)||block.getRegistryName()==null)return false;
        String name=block.getRegistryName().getResourcePath();
        return !name.startsWith("antigravity_plate")&&!name.startsWith("repulsor_array");
    }
    /** Facing is the exhaust direction, expressed in the craft local frame. */
    public static boolean opposes(net.minecraft.block.state.IBlockState state,Vec3d movement){
        net.minecraft.util.EnumFacing face=state.getValue(BlockPropulsionLight.FACING);
        return movement.x*face.getFrontOffsetX()+movement.y*face.getFrontOffsetY()+movement.z*face.getFrontOffsetZ()<-.002;
    }
    public static void requireThruster(VehicleStructure structure){
        if(structure.activeThruster())return;
        throw new IllegalArgumentException("Hover requires at least one Rocket Thruster, Ion Drive, Plasma Vent or Impulse Engine.");
    }
    /** Gross weight units supported in Hover, including carried stores. */
    public static int hoverLift(net.minecraft.block.Block block){
        if(!lift(block))return 0;
        return block.getRegistryName().getResourcePath().startsWith("repulsor_array")?72:36;
    }
    public static int hoverCapacity(VehicleStructure structure){
        if(structure.activeLiftCapacity>=0)return structure.activeLiftCapacity;
        if(structure.needsComponentView()&&structure.componentView()==null)return 0;
        int capacity=0,count=0;
        for(VehicleStructure.Cell cell:structure.liftCells){
            if(!structure.isActive(cell.pos))continue;
            capacity+=hoverLift(cell.state.getBlock());
            count++;
        }
        structure.activeLiftCount=count;
        return structure.activeLiftCapacity=capacity;
    }
    static double measure(VehicleStructure structure,VehicleWorld view){
        VehicleWeight weight=new VehicleWeight(structure,view);
        while(!weight.step(4096,Long.MAX_VALUE)){}
        return weight.result().units;
    }
    private static double snapshotMass(VehicleStructure structure){
        if(structure.measuredMass<0&&structure.componentView()!=null)
            structure.measuredMass=measure(structure,structure.componentView());
        return structure.measuredMass;
    }
    public static boolean canHover(VehicleStructure structure){
        return structure.liftCells.size()>=3&&canHover(structure,snapshotMass(structure));
    }
    public static boolean canHover(VehicleStructure structure,double mass){
        if(!Double.isFinite(mass)||mass<0||mass>hoverCapacity(structure)||structure.activeLiftCount<3)return false;
        if(structure.couplers.isEmpty())return true;
        VehicleCouplerTopology topology=VehicleCouplerTopology.of(structure);
        return topology!=null&&topology.sufficientLift(structure);
    }
    public static String hoverRequirement(VehicleStructure structure){
        return hoverRequirement(structure,structure.measuredMass);
    }
    public static String hoverRequirement(VehicleStructure structure,double mass){
        int capacity=hoverCapacity(structure);
        if(mass>capacity)return String.format(java.util.Locale.ROOT,"Hover lift supports %d weight units. This craft weighs %.1f.",capacity,mass);
        return structure.couplers.isEmpty()?"Hover requires three lift blocks and a Vehicle Thruster.":"Hover requires three lift blocks in each connected coupler section and a Vehicle Thruster.";
    }
    public static boolean canParkHover(EntityGroundVehicle craft){return VehicleLandingGear.extended(craft)&&clearance(craft)<=3;}
    public static double clearance(EntityGroundVehicle craft){
        if(craft.collision==null||craft.flightAltitude()>=com.vandorlabs.physics.FlightEnvelope.WORLD_CEILING)return Double.POSITIVE_INFINITY;
        AxisAlignedBB box=craft.flightTilted()?craft.getEntityBoundingBox():craft.articulation.active()?craft.articulation.worldBounds(craft.posX,craft.posY,craft.posZ,craft.rotationYaw):craft.collision.bounds(craft.rotationYaw).offset(craft.posX,craft.posY,craft.posZ);
        double bottom=box.minY,highest=Double.NEGATIVE_INFINITY;
        int minX=net.minecraft.util.math.MathHelper.floor(box.minX+.001),maxX=net.minecraft.util.math.MathHelper.floor(box.maxX-.001);
        int minZ=net.minecraft.util.math.MathHelper.floor(box.minZ+.001),maxZ=net.minecraft.util.math.MathHelper.floor(box.maxZ-.001);
        BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos();
        int topY=Math.min(craft.world.getHeight()-1,MathHelper.floor(bottom));
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
            Chunk chunk=craft.world.getChunkProvider().getLoadedChunk(cx,cz);
            if(chunk==null)return Double.POSITIVE_INFINITY;
            ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();
            int x0=Math.max(minX,cx<<4),x1=Math.min(maxX,(cx<<4)+15);
            int z0=Math.max(minZ,cz<<4),z1=Math.min(maxZ,(cz<<4)+15);
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                for(int y=topY;y>=0&&y+1>highest;y--){
                    ExtendedBlockStorage section=sections[y>>4];
                    if(section==null||section.isEmpty()){y&=~15;continue;}
                    position.setPos(x,y,z);
                    net.minecraft.block.state.IBlockState state=chunk.getBlockState(position);
                    if(state.getMaterial().isLiquid()||state.getBlock().isAir(state,craft.world,position))continue;
                    AxisAlignedBB solid=state.getCollisionBoundingBox(craft.world,position);
                    if(solid!=null&&y+solid.maxY<=bottom+.001){highest=Math.max(highest,y+solid.maxY);break;}
                }
                if(highest>=bottom-.001)return Math.max(0,bottom-highest);
            }
        }
        return highest==Double.NEGATIVE_INFINITY?Double.POSITIVE_INFINITY:Math.max(0,bottom-highest);
    }
}

package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPropulsionLight;
import net.minecraft.util.math.*;

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
        if(structure.hasThruster)return;
        throw new IllegalArgumentException("Taxi and Hover require at least one Rocket Thruster, Ion Drive, Plasma Vent or Impulse Engine.");
    }
    public static boolean canHover(VehicleStructure structure){
        return structure.liftCells.size()>=3;
    }
    public static double clearance(EntityGroundVehicle craft){
        if(craft.collision==null)return Double.POSITIVE_INFINITY;
        AxisAlignedBB box=craft.collision.bounds(craft.rotationYaw).offset(craft.posX,craft.posY,craft.posZ);
        double bottom=box.minY,highest=Double.NEGATIVE_INFINITY;
        int minX=net.minecraft.util.math.MathHelper.floor(box.minX+.001),maxX=net.minecraft.util.math.MathHelper.floor(box.maxX-.001);
        int minZ=net.minecraft.util.math.MathHelper.floor(box.minZ+.001),maxZ=net.minecraft.util.math.MathHelper.floor(box.maxZ-.001);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Math.min(255,MathHelper.floor(bottom));y>=Math.max(0,MathHelper.floor(bottom)-32);y--){
            BlockPos p=new BlockPos(x,y,z);if(!craft.world.isBlockLoaded(p))return Double.POSITIVE_INFINITY;
            net.minecraft.block.state.IBlockState state=craft.world.getBlockState(p);
            if(state.getMaterial().isLiquid())continue;
            AxisAlignedBB solid=state.getCollisionBoundingBox(craft.world,p);
            if(solid!=null){double top=p.getY()+solid.maxY;if(top<=bottom+.001){highest=Math.max(highest,top);break;}}
        }
        return highest==Double.NEGATIVE_INFINITY?Double.POSITIVE_INFINITY:Math.max(0,bottom-highest);
    }
}

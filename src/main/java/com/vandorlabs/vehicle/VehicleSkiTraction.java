package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.GearBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Ski-only craft may taxi while every runner remains above loaded ice. */
public final class VehicleSkiTraction {
    private VehicleSkiTraction(){}
    public static boolean allowed(EntityGroundVehicle craft){return !craft.structure.hasNonWheeledGear||craft.structure.hasSkisOnly&&onIce(craft,craft.posX,craft.posY,craft.posZ,craft.rotationYaw);}
    public static boolean canStart(World world,VehicleStructure structure,BlockPos origin){return !structure.hasNonWheeledGear||structure.hasSkisOnly&&onIce(world,structure,origin.getX(),origin.getY(),origin.getZ(),0);}
    public static boolean onIce(EntityGroundVehicle craft,double x,double y,double z,float yaw){return onIce(craft.world,craft.structure,x,y,z,yaw);}
    private static boolean onIce(World world,VehicleStructure structure,double x,double y,double z,float yaw){
        if(!structure.hasSkisOnly)return false;
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw)),px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2;boolean found=false;
        for(VehicleStructure.Cell cell:structure.cells){
            boolean modular=cell.state.getBlock() instanceof GearBlock&&((GearBlock)cell.state.getBlock()).kind()==GearBlock.Kind.SKI;
            boolean legacy=cell.state.getBlock() instanceof BlockNonWheeledLandingGear&&((BlockNonWheeledLandingGear)cell.state.getBlock()).ski&&!cell.state.getValue(BlockTelescopicLandingGear.LOWER);
            if(!modular&&!legacy)continue;found=true;
            double fy=cell.pos.getY()+.02-(legacy&&cell.tileData()!=null?cell.tileData().getFloat("Progress"):0);
            for(double[] offset:new double[][]{{0,0},{-.25,-.25},{-.25,.25},{.25,-.25},{.25,.25}}){
                double lx=cell.pos.getX()+.5+offset[0]-px,lz=cell.pos.getZ()+.5+offset[1]-pz;
                if(!iceBelow(world,new Vec3d(x+px+c*lx-s*lz,y+fy,z+pz+s*lx+c*lz)))return false;
            }
        }
        return found;
    }
    private static boolean iceBelow(World world,Vec3d foot){
        BlockPos start=new BlockPos(foot).down();
        for(int depth=0;depth<4;depth++){
            BlockPos at=start.down(depth);if(!world.isBlockLoaded(at,false))return false;
            IBlockState state=world.getBlockState(at);AxisAlignedBB box=state.getCollisionBoundingBox(world,at);
            if(box==null)continue;
            return foot.y-(at.getY()+box.maxY)<=3.05&&(state.getMaterial()==Material.ICE||state.getBlock()==Blocks.PACKED_ICE||state.getBlock()==Blocks.FROSTED_ICE);
        }
        return false;
    }
}

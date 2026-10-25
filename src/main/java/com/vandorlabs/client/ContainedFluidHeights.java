package com.vandorlabs.client;

import com.vandorlabs.blocks.WaterloggedParts;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.*;

/** The same four-column surface average used by vanilla liquid, with contained columns included. */
final class ContainedFluidHeights {
    static IBlockState state(net.minecraft.world.World world,BlockPos pos,Fluid fluid){
        IBlockState state=world.getBlockState(pos);
        if(WaterloggedParts.supports(state))for(int side=0;side<2;side++){
            FluidStack stored=WaterloggedParts.fluid(world,pos,side);
            if(stored!=null&&stored.getFluid()==fluid)return fluid.getBlock().getDefaultState();
        }
        return state;
    }
    static double corner(net.minecraft.world.World world,BlockPos pos,Fluid fluid,int x,int z){
        Material material=fluid.getBlock().getDefaultState().getMaterial();java.util.List<com.vandorlabs.fluids.FluidSurfaceMesh.Column> columns=new java.util.ArrayList<>();
        for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){
            BlockPos at=pos.add(x-dx,0,z-dz);if(state(world,at.up(),fluid).getMaterial()==material)return 1;IBlockState state=state(world,at,fluid);
            boolean same=state.getMaterial()==material&&state.getPropertyKeys().contains(BlockLiquid.LEVEL);
            columns.add(new com.vandorlabs.fluids.FluidSurfaceMesh.Column(false,same,same?state.getValue(BlockLiquid.LEVEL):0,state.getMaterial().isSolid()));
        }
        return com.vandorlabs.fluids.FluidSurfaceMesh.cornerHeight(columns);
    }

    static double height(net.minecraft.world.World world,BlockPos pos,Fluid fluid,double x,double z){
        double a=corner(world,pos,fluid,0,0),b=corner(world,pos,fluid,0,1),c=corner(world,pos,fluid,1,1),d=corner(world,pos,fluid,1,0);
        return com.vandorlabs.fluids.FluidSurfaceMesh.height(a,b,c,d,x,z);
    }
}

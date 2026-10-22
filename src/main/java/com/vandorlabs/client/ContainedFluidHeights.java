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
        Material material=fluid.getBlock().getDefaultState().getMaterial();float depth=0;int count=0;
        for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){
            BlockPos at=pos.add(x-dx,0,z-dz);
            if(state(world,at.up(),fluid).getMaterial()==material)return 1;
            IBlockState state=state(world,at,fluid);
            if(state.getMaterial()==material&&state.getPropertyKeys().contains(BlockLiquid.LEVEL)){
                int level=state.getValue(BlockLiquid.LEVEL);float d=BlockLiquid.getLiquidHeightPercent(level);
                if(level>=8||level==0){depth+=d*10;count+=10;}depth+=d;count++;
            }else if(!state.getMaterial().isSolid()){depth++;count++;}
        }
        return count==0?8D/9:1-depth/count;
    }
    static double height(net.minecraft.world.World world,BlockPos pos,Fluid fluid,double x,double z){
        double a=corner(world,pos,fluid,0,0),b=corner(world,pos,fluid,0,1),c=corner(world,pos,fluid,1,1),d=corner(world,pos,fluid,1,0);
        return (a*(1-x)+d*x)*(1-z)+(b*(1-x)+c*x)*z-.001;
    }
}

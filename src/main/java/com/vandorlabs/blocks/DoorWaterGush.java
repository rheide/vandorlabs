package com.vandorlabs.blocks;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

/** Opening a one-sided waterlogged door releases a pulse of ordinary flowing water. */
public final class DoorWaterGush {
    private DoorWaterGush(){}
    public static void opened(World world,BlockPos lower){
        if(world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        for(int half=0;half<2;half++){
            BlockPos pos=lower.up(half);IBlockState state=world.getBlockState(pos);
            if(!(state.getBlock() instanceof BlockConfigurableSpaceDoor))continue;
            ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));if(data==null)continue;
            FluidStack first=data.get(0),second=data.get(1);int wet=first!=null&&first.getFluid()==FluidRegistry.WATER?0:second!=null&&second.getFluid()==FluidRegistry.WATER?1:-1;
            if(wet<0||data.get(1-wet)!=null)continue;
            double[] plane=OpeningFluids.plane(world,pos);double sign=wet==0?1:-1;
            for(EnumFacing face:EnumFacing.HORIZONTALS)if(sign*(plane[0]*face.getFrontOffsetX()+plane[2]*face.getFrontOffsetZ())>.5){
                BlockPos out=pos.offset(face);if(!world.isBlockLoaded(out))continue;
                IBlockState target=world.getBlockState(out);
                if(target.getMaterial().isLiquid()||!target.getBlock().isReplaceable(world,out))continue;
                world.setBlockState(out,Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,1),3);
                world.scheduleUpdate(out,Blocks.FLOWING_WATER,Blocks.FLOWING_WATER.tickRate(world));
            }
        }
    }
}

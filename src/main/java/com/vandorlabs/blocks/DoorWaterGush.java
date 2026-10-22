package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

/** A one-sided wet door supplies vanilla outlets while open, then lets them drain. */
public final class DoorWaterGush {
    private DoorWaterGush(){}
    public static void schedule(World world,BlockPos pos){
        if(world==null||world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockConfigurableSpaceDoor))return;
        BlockPos lower=state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if(world.isBlockLoaded(lower))world.scheduleUpdate(lower,state.getBlock(),1);
    }
    public static void opened(World world,BlockPos lower){update(world,lower,false);}
    public static void stop(World world,BlockPos lower){update(world,lower,true);}
    private static void update(World world,BlockPos lower,boolean stop){
        if(world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        IBlockState base=world.getBlockState(lower);
        if(!stop&&(!(base.getBlock() instanceof BlockConfigurableSpaceDoor)||base.getValue(BlockVandorDoor.HALF)!=BlockDoor.EnumDoorHalf.LOWER))return;
        if(!(world.getTileEntity(lower) instanceof TileEntitySpaceDoor))return;
        TileEntitySpaceDoor owner=(TileEntitySpaceDoor)world.getTileEntity(lower);
        int previous=owner.waterOutlets(),retained=0;boolean supplying=false;
        if(!stop&&base.getValue(BlockVandorDoor.OPEN))for(int half=0;half<2;half++){
            BlockPos pos=lower.up(half);IBlockState state=world.getBlockState(pos);
            if(!(state.getBlock() instanceof BlockConfigurableSpaceDoor))continue;
            ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));if(data==null)continue;
            FluidStack first=data.get(0),second=data.get(1);int wet=first!=null&&first.getFluid()==FluidRegistry.WATER?0:second!=null&&second.getFluid()==FluidRegistry.WATER?1:-1;
            if(wet<0||data.get(1-wet)!=null)continue;
            supplying=true;
            double[] plane=OpeningFluids.plane(world,pos);double sign=wet==0?1:-1;
            for(EnumFacing face:EnumFacing.HORIZONTALS)if(sign*(plane[0]*face.getFrontOffsetX()+plane[2]*face.getFrontOffsetZ())>.5){
                int bit=1<<(half*4+face.getHorizontalIndex());BlockPos out=pos.offset(face);
                if(!world.isBlockLoaded(out)){retained|=previous&bit;continue;}
                IBlockState target=world.getBlockState(out);
                if(target.getMaterial()==net.minecraft.block.material.Material.WATER){
                    if(target.getValue(BlockLiquid.LEVEL)==0){retained|=previous&bit;continue;}
                }else if(target.getMaterial().isLiquid()||!target.getBlock().isReplaceable(world,out))continue;
                retained|=bit;
                world.setBlockState(out,Blocks.FLOWING_WATER.getDefaultState(),3);
                world.scheduleUpdate(out,Blocks.FLOWING_WATER,Blocks.FLOWING_WATER.tickRate(world));
            }
        }
        for(int half=0;half<2;half++)for(EnumFacing face:EnumFacing.HORIZONTALS){
            int bit=1<<(half*4+face.getHorizontalIndex());if((previous&bit)==0||(retained&bit)!=0)continue;
            BlockPos out=lower.up(half).offset(face);
            // A destroyed owner cannot retry later. Resolve its owned boundary
            // outlet now; ordinary supply ticks never load neighboring chunks.
            if(!stop&&!world.isBlockLoaded(out)){retained|=bit;continue;}
            IBlockState state=world.getBlockState(out);
            if(state.getMaterial()==net.minecraft.block.material.Material.WATER&&state.getValue(BlockLiquid.LEVEL)==0){
                world.setBlockState(out,Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,1),3);
                world.scheduleUpdate(out,Blocks.FLOWING_WATER,Blocks.FLOWING_WATER.tickRate(world));
            }
        }
        owner.setWaterOutlets(retained);
        if(!stop&&(supplying||retained!=0))world.scheduleUpdate(lower,base.getBlock(),5);
    }
}

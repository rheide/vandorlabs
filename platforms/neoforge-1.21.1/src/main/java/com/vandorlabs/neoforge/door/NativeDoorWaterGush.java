package com.vandorlabs.neoforge.door;

import com.vandorlabs.fluid.DoorWaterOutlets;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;

/** Owned vanilla outlets, independent of the reversible effective fluid/render overlay. */
public final class NativeDoorWaterGush {
    private NativeDoorWaterGush(){}
    public static void update(NativeProgrammableDoorEntity owner,boolean stop){
        Level level=owner.getLevel();if(level==null||level.isClientSide)return;
        if(owner instanceof NativeLargeDoorEntity largeOwner&&largeOwner.isHorizontal()){
            horizontal(largeOwner,level,stop);
            return;
        }
        BlockPos lower=owner.getBlockPos();var base=owner.getBlockState();
        Block block=base.getBlock();boolean large=block instanceof NativeLargeDoorBlock;
        if((!large&&!(block instanceof NativeProgrammableDoorBlock))||base.getValue(NativeProgrammableDoorBlock.HALF)!=DoubleBlockHalf.LOWER)return;
        var root=large?NativeLargeDoorLifecycle.root(level,lower):owner;
        if(!stop&&(!NativeFluidWorld.installed(owner)||root==null||(large?!NativeLargeDoorLifecycle.complete(level,(NativeLargeDoorEntity)root):!NativeDoorLifecycle.complete(level,lower,(NativeProgrammableDoorBlock)block))||root.placementRecovery()))return;
        if(!owner.beginWaterGush())return;try{
        int previous=owner.waterOutlets(),retained=0;boolean supplying=false;
        if(!stop&&base.getValue(NativeProgrammableDoorBlock.OPEN))for(int half=0;half<2;half++){
            BlockPos pos=lower.above(half);if(!NativeFluidWorld.loaded(level,pos)||!level.getBlockState(pos).is(block)||!(level.getBlockEntity(pos) instanceof NativeProgrammableDoorEntity part))continue;
            var data=part.containedFluids().snapshot();var first=data.side(0).fluid();var second=data.side(1).fluid();int wet=DoorWaterOutlets.wetSide(first.is(Fluids.WATER),second.is(Fluids.WATER),!first.isEmpty(),!second.isEmpty());if(wet<0)continue;supplying=true;
            Direction face=(large&&root!=null?root.getBlockState():base).getValue(NativeProgrammableDoorBlock.FACING);Direction exit=wet==0?face:face.getOpposite();int bit=1<<(half*4+exit.get2DDataValue());BlockPos out=pos.relative(exit);
            if(supply(level,out,(previous&bit)!=0))retained|=bit;
        }
        for(int half=0;half<2;half++)for(Direction face:Direction.Plane.HORIZONTAL){
            int bit=1<<(half*4+face.get2DDataValue());
            if((previous&bit)!=0&&(retained&bit)==0&&retainOrDrain(level,lower.above(half).relative(face),stop))retained|=bit;
        }
        owner.setWaterOutlets(retained);owner.scheduleWaterGush(!stop&&(supplying||retained!=0));
        }finally{owner.endWaterGush();}
    }

    /** Each horizontal member owns the outlets immediately above and below its own stored fluid. */
    private static void horizontal(NativeLargeDoorEntity owner,Level level,boolean stop){
        BlockPos pos=owner.getBlockPos();
        var root=stop?null:NativeLargeDoorLifecycle.root(level,pos);
        if(!stop&&(root==null||root.placementRecovery()||!NativeFluidWorld.installed(owner)))return;
        if(!owner.beginWaterGush())return;
        try {
            int previous=owner.waterOutlets(),retained=0,wet=-1;
            if(root!=null&&root.getBlockState().getValue(NativeLargeDoorBlock.OPEN)){
                var data=owner.containedFluids().snapshot();
                var first=data.side(0).fluid();var second=data.side(1).fluid();
                wet=DoorWaterOutlets.wetSide(first.is(Fluids.WATER),second.is(Fluids.WATER),!first.isEmpty(),!second.isEmpty());
                if(wet>=0){
                    Direction exit=wet==0?Direction.DOWN:Direction.UP;
                    int bit=1<<wet;
                    if(supply(level,pos.relative(exit),(previous&bit)!=0))retained|=bit;
                }
            }
            for(int slot=0;slot<2;slot++){
                int bit=1<<slot;
                if((previous&bit)!=0&&(retained&bit)==0&&retainOrDrain(level,slot==0?pos.below():pos.above(),stop))retained|=bit;
            }
            owner.setWaterOutlets(retained);
            owner.scheduleWaterGush(!stop&&(wet>=0||retained!=0));
        } finally {
            owner.endWaterGush();
        }
    }
    private static boolean supply(Level level,BlockPos out,boolean previous){
        if(!NativeFluidWorld.loaded(level,out))return previous;
        var target=level.getBlockState(out);
        boolean water=target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER);
        int action=DoorWaterOutlets.supply(previous,true,water,water&&target.getFluidState().isSource(),!target.getFluidState().isEmpty(),!water&&target.getFluidState().isEmpty()&&target.canBeReplaced());
        if(action==DoorWaterOutlets.PUBLISH){
            level.setBlock(out,Blocks.WATER.defaultBlockState(),Block.UPDATE_ALL);
            level.scheduleTick(out,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        }
        return action==DoorWaterOutlets.PUBLISH||action==DoorWaterOutlets.RETAIN;
    }
    private static boolean retainOrDrain(Level level,BlockPos out,boolean stop){
        // A destroyed upright owner has no future tick, matching the released outlet cleanup.
        if(!stop&&!NativeFluidWorld.loaded(level,out))return true;
        var target=level.getBlockState(out);
        boolean source=target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER)&&target.getFluidState().isSource();
        if(source){
            level.setBlock(out,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),Block.UPDATE_ALL);
            level.scheduleTick(out,Fluids.FLOWING_WATER,Fluids.FLOWING_WATER.getTickDelay(level));
        }
        return false;
    }
}

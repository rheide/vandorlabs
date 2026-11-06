package com.vandorlabs.neoforge.door;

import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Loaded ordinary-door structure. All queries and mutations run on the owning level thread. */
public final class NativeDoorLifecycle {
    private NativeDoorLifecycle(){}
    public static BlockPos lower(BlockState state,BlockPos pos){return state.getValue(NativeProgrammableDoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;}
    public static boolean loaded(LevelReader level,BlockPos pos){return (!(level instanceof net.minecraft.server.level.ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&level.hasChunkAt(pos);}
    public static boolean complete(LevelReader level,BlockPos root,NativeProgrammableDoorBlock block){
        if(!loaded(level,root)||!loaded(level,root.above()))return false;
        var lower=level.getBlockState(root);var upper=level.getBlockState(root.above());return lower.is(block)&&upper.is(block)&&lower.getValue(NativeProgrammableDoorBlock.HALF)==DoubleBlockHalf.LOWER&&upper.getValue(NativeProgrammableDoorBlock.HALF)==DoubleBlockHalf.UPPER;
    }
    public static NativeProgrammableDoorEntity root(Level level,BlockPos pos){
        if(!loaded(level,pos)||!(level.getBlockState(pos).getBlock() instanceof NativeProgrammableDoorBlock block))return null;
        BlockPos lower=lower(level.getBlockState(pos),pos);return complete(level,lower,block)&&level.getBlockEntity(lower) instanceof NativeProgrammableDoorEntity host&&!host.isRemoved()&&!host.placementRecovery()?host:null;
    }
    public static BlockState resolved(LevelReader level,BlockPos pos,BlockState state){
        BlockPos root=lower(state,pos);if(!(state.getBlock() instanceof NativeProgrammableDoorBlock block)||!complete(level,root,block))return state;
        BlockState bottom=level.getBlockState(root),top=level.getBlockState(root.above());return state.setValue(NativeProgrammableDoorBlock.FACING,bottom.getValue(NativeProgrammableDoorBlock.FACING)).setValue(NativeProgrammableDoorBlock.HINGE,top.getValue(NativeProgrammableDoorBlock.HINGE)).setValue(NativeProgrammableDoorBlock.POWERED,top.getValue(NativeProgrammableDoorBlock.POWERED));
    }
    public static BlockPos mate(Level level,BlockPos root,NativeProgrammableDoorBlock block){
        if(!complete(level,root,block))return null;var state=resolved(level,root,level.getBlockState(root));Direction facing=state.getValue(NativeProgrammableDoorBlock.FACING);DoorHingeSide hinge=state.getValue(NativeProgrammableDoorBlock.HINGE);
        BlockPos other=root.relative(hinge==DoorHingeSide.LEFT?facing.getClockWise():facing.getCounterClockWise());if(!complete(level,other,block))return null;var candidate=resolved(level,other,level.getBlockState(other));return candidate.getValue(NativeProgrammableDoorBlock.FACING)==facing&&candidate.getValue(NativeProgrammableDoorBlock.HINGE)!=hinge?other:null;
    }
    static BlockPos mateForPlacement(Level level,BlockPos root,NativeProgrammableDoorBlock block,BlockState state){Direction facing=state.getValue(NativeProgrammableDoorBlock.FACING);var hinge=state.getValue(NativeProgrammableDoorBlock.HINGE);BlockPos other=root.relative(hinge==DoorHingeSide.LEFT?facing.getClockWise():facing.getCounterClockWise());if(!complete(level,other,block))return null;var adjacent=resolved(level,other,level.getBlockState(other));return adjacent.getValue(NativeProgrammableDoorBlock.FACING)==facing&&adjacent.getValue(NativeProgrammableDoorBlock.HINGE)!=hinge?other:null;}
    public static boolean supported(LevelReader level,BlockPos root){
        BlockPos below=root.below();if(!loaded(level,below))return false;BlockState support=level.getBlockState(below);
        return support.isFaceSturdy(level,below,Direction.UP)||support.getBlock() instanceof NativeProgrammableSlab||support.getBlock() instanceof NativeDiagonalWallBlock&&level.getBlockEntity(below) instanceof NativeWallEntity wall&&!wall.wallSettings().halfHeight&&wall.wallSettings().fill!=0;
    }
    public static DoorHingeSide placementHinge(Level level,BlockPos pos,NativeProgrammableDoorBlock block,Direction facing,double x,double z){
        boolean left=complete(level,pos.relative(facing.getClockWise()),block)&&level.getBlockState(pos.relative(facing.getClockWise())).getValue(NativeProgrammableDoorBlock.FACING)==facing;
        boolean right=complete(level,pos.relative(facing.getCounterClockWise()),block)&&level.getBlockState(pos.relative(facing.getCounterClockWise())).getValue(NativeProgrammableDoorBlock.FACING)==facing;
        if(left&&!right)return DoorHingeSide.LEFT;if(right&&!left)return DoorHingeSide.RIGHT;
        boolean visualLeft=switch(facing){case NORTH->x>.5;case SOUTH->x<.5;case EAST->z>.5;case WEST->z<.5;default->true;};return visualLeft?DoorHingeSide.RIGHT:DoorHingeSide.LEFT;
    }
    public static com.vandorlabs.render.SpaceDoorControlPanel.Side panelSide(NativeProgrammableDoorEntity host,BlockState actual){
        if(!host.settings().door().panel)return com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE;
        Direction face=actual.getValue(NativeProgrammableDoorBlock.FACING);Level level=host.getLevel();BlockPos root=host.getBlockPos();
        boolean left=beside(level,root.relative(face.getClockWise()),face),right=beside(level,root.relative(face.getCounterClockWise()),face);
        return com.vandorlabs.render.SpaceDoorControlPanel.side(actual.getValue(NativeProgrammableDoorBlock.HINGE)==DoorHingeSide.RIGHT,host.settings().door().sliding,actual.getValue(NativeProgrammableDoorBlock.PAIRED),left,right);
    }
    private static boolean beside(Level level,BlockPos pos,Direction facing){return loaded(level,pos)&&level.getBlockState(pos).getBlock() instanceof NativeProgrammableDoorBlock block&&complete(level,pos,block)&&level.getBlockState(pos).getValue(NativeProgrammableDoorBlock.FACING)==facing;}
    public static int localPower(Level level,BlockPos root){return Math.max(power(level,root),power(level,root.above()));}
    private static int power(Level level,BlockPos pos){int result=0;for(Direction side:Direction.values()){BlockPos next=pos.relative(side);if(!loaded(level,next))continue;BlockState state=level.getBlockState(next);if(state.shouldCheckWeakPower(level,next,side)){for(Direction strong:Direction.values()){BlockPos source=next.relative(strong);if(loaded(level,source))result=Math.max(result,level.getBlockState(source).getDirectSignal(level,source,strong));}}else result=Math.max(result,state.getSignal(level,next,side));if(result>=15)return 15;}return Math.min(15,result);}
    public static void updatePower(NativeProgrammableDoorEntity host){
        Level level=host.getLevel();if(level==null||level.isClientSide||root(level,host.getBlockPos())!=host)return;
        boolean powered=host.channelSignal()||localPower(level,host.getBlockPos())>0;var settings=host.settings();
        // Credential modes are supplied by the actual reader service, never by the raw stored toggle alone.
        boolean open=settings.securityMode()>0?((NativeProgrammableDoorBlock)host.getBlockState().getBlock()).securityOpen(host):settings.door().trigger==SpaceDoorData.TRIGGER_DISABLED?host.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN):SpaceDoorData.openForSignal(settings.door().trigger,powered);
        var actual=resolved(level,host.getBlockPos(),host.getBlockState());if(actual.getValue(NativeProgrammableDoorBlock.POWERED)!=powered||host.getBlockState().getValue(NativeProgrammableDoorBlock.OPEN)!=open)set(level,host.getBlockPos(),powered,open,true);
    }
    static void set(Level level,BlockPos root,boolean powered,boolean open,boolean updatePower){
        if(!(level.getBlockState(root).getBlock() instanceof NativeProgrammableDoorBlock block)||!complete(level,root,block))return;boolean changed=false;
        for(BlockPos pos:new BlockPos[]{root,root.above()}){var old=level.getBlockState(pos);var next=old.setValue(NativeProgrammableDoorBlock.OPEN,open);if(updatePower)next=next.setValue(NativeProgrammableDoorBlock.POWERED,powered);if(next!=old){changed|=old.getValue(NativeProgrammableDoorBlock.OPEN)!=open;level.setBlock(pos,next,Block.UPDATE_CLIENTS);com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);}}
        var host=root(level,root);if(host!=null)host.refreshVisual();
        if(changed)level.playSound(null,root,open?SoundEvents.IRON_DOOR_OPEN:SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,1,1);
    }
    public static void toggle(Level level,BlockPos pos){
        var host=root(level,pos);if(host==null||host.settings().securityMode()!=0||host.settings().door().trigger!=SpaceDoorData.TRIGGER_DISABLED)return;
        BlockPos root=host.getBlockPos();var state=resolved(level,root,host.getBlockState());boolean open=!state.getValue(NativeProgrammableDoorBlock.OPEN);set(level,root,false,open,false);var block=(NativeProgrammableDoorBlock)state.getBlock();Direction facing=state.getValue(NativeProgrammableDoorBlock.FACING);
        for(Direction side:new Direction[]{facing.getClockWise(),facing.getCounterClockWise()}){BlockPos other=root.relative(side);if(!complete(level,other,block))continue;var neighbor=NativeDoorLifecycle.root(level,other);var ns=resolved(level,other,level.getBlockState(other));if(neighbor!=null&&ns.getValue(NativeProgrammableDoorBlock.FACING)==facing&&ns.getValue(NativeProgrammableDoorBlock.HINGE)!=state.getValue(NativeProgrammableDoorBlock.HINGE)&&neighbor.settings().securityMode()==0&&neighbor.settings().door().trigger==0)set(level,other,false,open,false);}
    }
}

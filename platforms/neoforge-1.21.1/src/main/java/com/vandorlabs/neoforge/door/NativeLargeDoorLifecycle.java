package com.vandorlabs.neoforge.door;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
/** Loaded 3x3 roles. No query or repair loads a neighboring chunk. */
public final class NativeLargeDoorLifecycle {
    private NativeLargeDoorLifecycle(){}
    private record Removal(Level level,BlockPos anchor){}private static final ThreadLocal<Removal> REMOVING=new ThreadLocal<>();
    public static boolean loaded(LevelReader level,BlockPos pos){return NativeDoorLifecycle.loaded(level,pos);}
    public static NativeLargeDoorEntity root(Level level,BlockPos pos){
        if(!loaded(level,pos)||!(level.getBlockState(pos).getBlock() instanceof NativeLargeDoorBlock block)||!(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part))return null;BlockPos anchor=part.anchorPos();
        return loaded(level,anchor)&&level.getBlockState(anchor).is(block)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&root.isAnchor()?root:null;
    }
    public static boolean complete(Level level,NativeLargeDoorEntity root){
        BlockPos anchor=root.getBlockPos();if(!loaded(level,anchor)||!root.isAnchor()||!(level.getBlockState(anchor).getBlock() instanceof NativeLargeDoorBlock block)||level.getBlockEntity(anchor)!=root)return false;Direction width=level.getBlockState(anchor).getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=anchor.relative(width,x).above(y);if(!loaded(level,cell)||!level.getBlockState(cell).is(block)||!(level.getBlockEntity(cell) instanceof NativeLargeDoorEntity part)||!part.anchorPos().equals(anchor))return false;}return true;
    }
    public static BlockState resolved(Level level,BlockPos pos,BlockState fallback){var root=root(level,pos);return (root==null?fallback:root.getBlockState()).setValue(NativeLargeDoorBlock.PAIRED,true).setValue(NativeLargeDoorBlock.HINGE,DoorHingeSide.RIGHT).setValue(NativeLargeDoorBlock.HALF,root!=null&&pos.equals(root.getBlockPos())?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);}
    public static boolean supported(Level level,BlockPos anchor,Direction facing){Direction width=facing.getCounterClockWise();for(int x=0;x<3;x++){BlockPos support=anchor.relative(width,x).below();if(!loaded(level,support)||!level.getBlockState(support).isFaceSturdy(level,support,Direction.UP))return false;}return true;}
    public static boolean removing(Level level){return REMOVING.get()!=null&&REMOVING.get().level()==level;}
    public static void removed(Level level,BlockPos pos,BlockState state,NativeLargeDoorBlock block){
        if(removing(level)||NativeLargeDoorPlacement.mutating(level,pos))return;var raw=level.getBlockEntity(pos);BlockPos anchor=raw instanceof NativeLargeDoorEntity part?part.anchorPos():pos;Direction width=state.getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();Removal previous=REMOVING.get();REMOVING.set(new Removal(level,anchor));
        try{if(!level.isClientSide&&loaded(level,anchor)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&root.isAnchor())block.dropOwnedContents(level,pos,root);for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=anchor.relative(width,x).above(y);if(cell.equals(pos)||!loaded(level,cell))continue;if(level.getBlockState(cell).is(block)&&level.getBlockEntity(cell) instanceof NativeLargeDoorEntity part&&part.anchorPos().equals(anchor))level.removeBlock(cell,false);}}finally{if(previous==null)REMOVING.remove();else REMOVING.set(previous);}
    }
    public static void neighbor(Level level,BlockPos pos,NativeLargeDoorBlock block){
        if(level.isClientSide||removing(level)||NativeLargeDoorPlacement.mutating(level,pos))return;var root=root(level,pos);if(root==null){if(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part&&loaded(level,part.anchorPos()))level.removeBlock(pos,false);return;}
        BlockPos anchor=root.getBlockPos();Direction width=root.getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();for(int x=0;x<3;x++)for(int y=-1;y<3;y++)if(!loaded(level,anchor.relative(width,x).above(y)))return;
        if(!complete(level,root)||!supported(level,anchor,root.getBlockState().getValue(NativeLargeDoorBlock.FACING))){level.destroyBlock(anchor,true);return;}root.localInputChanged();
    }
    /** Shared structural OPEN/POWERED state; trigger and credential decisions remain outside this foundation. */
    public static boolean setState(Level level,NativeLargeDoorEntity root,boolean powered,boolean open){
        if(!complete(level,root)||root.placementRecovery())return false;BlockPos anchor=root.getBlockPos();Direction width=root.getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();boolean changed=root.getBlockState().getValue(NativeLargeDoorBlock.OPEN)!=open;
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=anchor.relative(width,x).above(y);var old=level.getBlockState(cell);var next=old.setValue(NativeLargeDoorBlock.OPEN,open).setValue(NativeLargeDoorBlock.POWERED,powered);if(old!=next){level.setBlock(cell,next,Block.UPDATE_CLIENTS);com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,cell);}}
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(level.getBlockEntity(anchor.relative(width,x).above(y)) instanceof NativeLargeDoorEntity part)part.refreshFluidGeometry();
        if(changed)level.playSound(null,anchor,open?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);return true;
    }

    public static int localPower(Level level,NativeLargeDoorEntity root){if(!root.isAnchor())return 0;int result=0;Direction width=root.getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=root.getBlockPos().relative(width,x).above(y);if(loaded(level,cell))result=Math.max(result,NativeDoorLifecycle.power(level,cell));}return result;}
    public static void updatePower(NativeLargeDoorEntity root){Level level=root.getLevel();if(level==null||level.isClientSide||root(level,root.getBlockPos())!=root||!complete(level,root)||root.placementRecovery())return;boolean powered=root.channelSignal()||root.controlLocalSignal()>0;var settings=root.settings();boolean open=settings.securityMode()>0?root.securityOpen():settings.door().trigger==com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED?root.getBlockState().getValue(NativeLargeDoorBlock.OPEN):com.vandorlabs.persistence.SpaceDoorData.openForSignal(settings.door().trigger,powered);setState(level,root,powered,open);}
    public static boolean authorized(Level level,NativeLargeDoorEntity root,net.minecraft.world.entity.player.Player player){if(root.placementRecovery()||!complete(level,root)||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,root))return false;Direction width=root.getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=root.getBlockPos().relative(width,x).above(y);if(!loaded(level,cell)||!level.mayInteract(player,cell)||!player.mayUseItemAt(cell,Direction.UP,player.getMainHandItem()))return false;}return true;}
    public static void toggle(Level level,BlockPos pos){var root=root(level,pos);if(root==null||root.settings().securityMode()!=0||root.settings().door().trigger!=com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED)return;setState(level,root,root.getBlockState().getValue(NativeLargeDoorBlock.POWERED),!root.getBlockState().getValue(NativeLargeDoorBlock.OPEN));}

}

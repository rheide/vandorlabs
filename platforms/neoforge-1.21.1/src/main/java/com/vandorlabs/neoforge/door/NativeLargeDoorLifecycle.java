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
    static Direction rows(Direction face,boolean horizontal){return horizontal?face:Direction.UP;}
    static BlockPos cell(BlockPos anchor,Direction face,boolean horizontal,int column,int row){
        return anchor.relative(face.getCounterClockWise(),column).relative(rows(face,horizontal),row);
    }
    static int distance(BlockPos anchor,BlockPos cell,Direction axis){
        return (cell.getX()-anchor.getX())*axis.getStepX()
                +(cell.getY()-anchor.getY())*axis.getStepY()
                +(cell.getZ()-anchor.getZ())*axis.getStepZ();
    }
    public static boolean loaded(LevelReader level,BlockPos pos){return NativeDoorLifecycle.loaded(level,pos);}
    public static NativeLargeDoorEntity root(Level level,BlockPos pos){
        if(!loaded(level,pos)||!(level.getBlockState(pos).getBlock() instanceof NativeLargeDoorBlock block)||!(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part))return null;BlockPos anchor=part.anchorPos();
        return loaded(level,anchor)&&level.getBlockState(anchor).is(block)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&root.isAnchor()?root:null;
    }
    public static boolean isOpenHorizontal(Level level,BlockPos pos,BlockState state){
        if(!(state.getBlock() instanceof NativeLargeDoorBlock)||!state.getValue(NativeLargeDoorBlock.OPEN))return false;
        NativeLargeDoorEntity root=root(level,pos);
        return root!=null&&root.isHorizontal()&&root.getBlockState().getValue(NativeLargeDoorBlock.OPEN);
    }
    public static boolean complete(Level level,NativeLargeDoorEntity root){
        BlockPos anchor=root.getBlockPos();if(!loaded(level,anchor)||!root.isAnchor()||!(level.getBlockState(anchor).getBlock() instanceof NativeLargeDoorBlock block)||level.getBlockEntity(anchor)!=root)return false;Direction face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=cell(anchor,face,root.isHorizontal(),x,y);if(!loaded(level,cell)||!level.getBlockState(cell).is(block)||!(level.getBlockEntity(cell) instanceof NativeLargeDoorEntity part)||!part.anchorPos().equals(anchor))return false;}return true;
    }
    public static BlockState resolved(Level level,BlockPos pos,BlockState fallback){var root=root(level,pos);return (root==null?fallback:root.getBlockState()).setValue(NativeLargeDoorBlock.PAIRED,true).setValue(NativeLargeDoorBlock.HINGE,DoorHingeSide.RIGHT).setValue(NativeLargeDoorBlock.HALF,root!=null&&pos.equals(root.getBlockPos())?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);}
    public static boolean supported(Level level,BlockPos anchor,Direction face){return supported(level,anchor,face,false);}
    public static boolean supported(Level level,BlockPos anchor,Direction face,boolean horizontal){
        return solidSupport(level,anchor,face,horizontal)||supportDoor(level,anchor,face,horizontal)!=null;
    }
    static boolean solidSupport(Level level,BlockPos anchor,Direction face,boolean horizontal){
        Direction side=rows(face,horizontal);
        for(int x=0;x<3;x++){
            BlockPos support=cell(anchor,face,horizontal,x,-1);
            if(!loaded(level,support)||!level.getBlockState(support).isFaceSturdy(level,support,side))return false;
        }
        return true;
    }
    static NativeLargeDoorEntity supportDoor(Level level,BlockPos anchor,Direction face,boolean horizontal){
        var below=cell(anchor,face,horizontal,0,-3);
        var support=NativeLargeDoorJoins.anchor(level,below,face,horizontal);
        return support!=null&&solidSupport(level,below,face,horizontal)?support:null;
    }
    static boolean placementSupported(Level level,BlockPos anchor,Direction face,boolean horizontal,
                                      NativeDoorSettings settings,boolean join){
        if(solidSupport(level,anchor,face,horizontal))return true;
        if(!join)return false;
        var support=supportDoor(level,anchor,face,horizontal);
        return support!=null&&support.joinEnabled()&&NativeLargeDoorJoins.compatible(support.settings(),settings)
                &&NativeLargeDoorJoins.mutable(level,support);
    }
    public static boolean removing(Level level){return REMOVING.get()!=null&&REMOVING.get().level()==level;}
    public static void removed(Level level,BlockPos pos,BlockState state,NativeLargeDoorBlock block){
        if(NativeLargeDoorPlacement.mutating(level,pos))return;
        var raw=level.getBlockEntity(pos);
        NativeLargeDoorEntity removed=raw instanceof NativeLargeDoorEntity part?part:null;
        if(removing(level)){
            if(!level.isClientSide&&removed!=null)NativeDoorWaterGush.update(removed,true);
            return;
        }
        BlockPos anchor=removed==null?pos:removed.anchorPos();
        Direction face=state.getValue(NativeLargeDoorBlock.FACING);
        boolean horizontal=removed!=null&&removed.isHorizontal();
        if(removed!=null)NativeLargeDoorJoins.invalidate(removed);
        Removal previous=REMOVING.get();
        REMOVING.set(new Removal(level,anchor));
        try {
            if(!level.isClientSide&&removed!=null)NativeDoorWaterGush.update(removed,true);
            if(!level.isClientSide&&loaded(level,anchor)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&root.isAnchor())
                block.dropOwnedContents(level,pos,root);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                BlockPos at=cell(anchor,face,horizontal,x,y);
                if(at.equals(pos)||!loaded(level,at))continue;
                if(level.getBlockState(at).is(block)&&level.getBlockEntity(at) instanceof NativeLargeDoorEntity part&&part.anchorPos().equals(anchor))
                    level.removeBlock(at,false);
            }
        } finally {
            if(previous==null)REMOVING.remove();else REMOVING.set(previous);
        }
    }
    public static void neighbor(Level level,BlockPos pos,NativeLargeDoorBlock block){
        if(level.isClientSide||removing(level)||NativeLargeDoorPlacement.mutating(level,pos))return;var root=root(level,pos);if(root==null){if(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part&&loaded(level,part.anchorPos()))level.removeBlock(pos,false);return;}
        BlockPos anchor=root.getBlockPos();Direction face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);for(int x=0;x<3;x++)for(int y=-1;y<3;y++)if(!loaded(level,cell(anchor,face,root.isHorizontal(),x,y)))return;
        if(!complete(level,root)||!supported(level,anchor,face,root.isHorizontal())){if(!com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,anchor))level.destroyBlock(anchor,true);return;}root.localInputChanged();
    }
    /** Shared structural state after every physical owner has passed loaded and transfer-hold checks. */
    public static boolean setState(Level level,NativeLargeDoorEntity root,boolean powered,boolean open){
        return setGroupState(level,NativeLargeDoorJoins.group(root),powered,open);
    }
    private static boolean setGroupState(Level level,com.vandorlabs.render.LargeDoorJoin.Group<NativeLargeDoorEntity> group,
                                         boolean powered,boolean open){
        for(var member:group.members)if(!NativeLargeDoorJoins.mutable(level,member))return false;
        boolean changed=false;
        for(var member:group.members)changed|=writeState(level,member,powered,open);
        if(changed)level.playSound(null,group.anchor.getBlockPos(),open?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:
                net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        for(var member:group.members){
            var anchor=member.getBlockPos();
            var face=member.getBlockState().getValue(NativeLargeDoorBlock.FACING);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)
                if(level.getBlockEntity(cell(anchor,face,member.isHorizontal(),x,y)) instanceof NativeLargeDoorEntity part){
                    if(changed&&member.isHorizontal())part.openingChanged();
                    part.refreshFluidGeometry();
                }
        }
        return true;
    }
    private static boolean writeState(Level level,NativeLargeDoorEntity root,boolean powered,boolean open){
        var anchor=root.getBlockPos();
        var face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
        boolean changed=root.getBlockState().getValue(NativeLargeDoorBlock.OPEN)!=open;
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            var pos=cell(anchor,face,root.isHorizontal(),x,y);
            var old=level.getBlockState(pos);
            var next=old.setValue(NativeLargeDoorBlock.OPEN,open).setValue(NativeLargeDoorBlock.POWERED,powered);
            if(old==next)continue;
            level.setBlock(pos,next,Block.UPDATE_CLIENTS);
            com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);
        }
        return changed;
    }

    public static int localPower(Level level,NativeLargeDoorEntity root){if(!root.isAnchor())return 0;int result=0;Direction face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=cell(root.getBlockPos(),face,root.isHorizontal(),x,y);if(loaded(level,cell))result=Math.max(result,NativeDoorLifecycle.power(level,cell));}return result;}
    public static void updatePower(NativeLargeDoorEntity root){
        Level level=root.getLevel();
        if(level==null||level.isClientSide||root(level,root.getBlockPos())!=root||!complete(level,root)||root.placementRecovery())return;
        var group=NativeLargeDoorJoins.group(root);
        boolean powered=false;
        for(var member:group.members)powered|=member.channelSignal()||member.controlLocalSignal()>0;
        var leader=group.anchor;
        var settings=leader.settings();
        boolean open=settings.securityMode()>0?leader.securityOpen():
                settings.door().trigger==com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED
                        ?leader.getBlockState().getValue(NativeLargeDoorBlock.OPEN)
                        :com.vandorlabs.persistence.SpaceDoorData.openForSignal(settings.door().trigger,powered);
        setGroupState(level,group,powered,open);
    }
    public static boolean authorized(Level level,NativeLargeDoorEntity root,net.minecraft.world.entity.player.Player player){
        return com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,root)&&permitted(level,root,player);
    }
    static boolean permitted(Level level,NativeLargeDoorEntity root,net.minecraft.world.entity.player.Player player){
        if(!NativeLargeDoorJoins.mutable(level,root))return false;
        var face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
        var side=rows(face,root.isHorizontal());
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            var pos=cell(root.getBlockPos(),face,root.isHorizontal(),x,y);
            if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,side,player.getMainHandItem()))return false;
        }
        return true;
    }
    public static void toggle(Level level,BlockPos pos){toggle(level,pos,null);}
    public static boolean toggle(Level level,BlockPos pos,net.minecraft.world.entity.player.Player player){
        var root=root(level,pos);
        if(root==null||root.settings().securityMode()!=0||root.settings().door().trigger!=com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED)return false;
        var group=NativeLargeDoorJoins.group(root);
        if(player!=null)for(var member:group.members)if(!permitted(level,member,player))return false;
        var state=group.anchor.getBlockState();
        return setGroupState(level,group,state.getValue(NativeLargeDoorBlock.POWERED),!state.getValue(NativeLargeDoorBlock.OPEN));
    }

}

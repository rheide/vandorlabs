package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.fluid.DoorWaterOutlets;
import com.vandorlabs.neoforge.door.NativeDoorOpeningFluids;
import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Stationary per-cell ownership and deferred outlet work. Animated panels never carry or serialize these slots. */
public final class NativeSegmentedFluids {
    public static final String OUTLETS="SegmentedWaterOutlets";
    private static final NativeFluidSlots.Snapshot DRY=new NativeFluidSlots().snapshot();
    private static final NativeFluidVolume EMPTY=NativeFluidVolume.build(java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),NativeFluidVolume.SINGLE_SIDE);
    private record Geometry(Direction face,int direction,int position,int height,int row,int column,int width,boolean closed,NativeSegmentedFluidGeometry.Snapshot snapshot,long revision){}
    private final NativeSegmentedDoorEntity owner;
    private final NativeSavedFluidSlots savedSlots=new NativeSavedFluidSlots();
    private final NativeFluidSlots slots=savedSlots.storage();
    private volatile Geometry geometry;
    private volatile NativeFluidSlots.Snapshot effective=slots.snapshot();
    private volatile NativeFluidRenderSnapshot render=NativeFluidRenderSnapshot.EMPTY;
    private NativeFluidSlots.Snapshot overlayBase;private int overlay=-1;private long effectiveRevision;
    private Object published;private volatile long modelRevision;
    private int outlets;private boolean updating;private long next=Long.MAX_VALUE;
    NativeSegmentedFluids(NativeSegmentedDoorEntity owner){this.owner=owner;}
    public NativeFluidSlots slots(){return slots;}public NativeFluidSlots.Snapshot effective(){return effective;}
    public NativeFluidVolume volume(){var state=geometry;return state==null?EMPTY:state.snapshot().volume();}
    public int side(double x,double y,double z){var state=geometry;return state==null?0:state.snapshot().plane().side(x,y,z);}
    public long revision(){var state=geometry;return state==null?0:state.revision();}public long modelRevision(){return modelRevision;}
    public NativeFluidRenderSnapshot render(){return render;}public void render(NativeFluidRenderSnapshot snapshot){render=snapshot;}
    public int outlets(){return outlets;}long deadline(){return next;}
    void read(CompoundTag tag,HolderLookup.Provider registries){savedSlots.read(tag,registries);outlets=tag.getInt(OUTLETS)&15;overlayBase=null;next=Long.MAX_VALUE;if(slots.any()||outlets!=0)schedule(1);}
    void write(CompoundTag tag){savedSlots.write(tag);if(outlets!=0)tag.putInt(OUTLETS,outlets);else if(!tag.contains(OUTLETS)||tag.contains(OUTLETS,Tag.TAG_ANY_NUMERIC))tag.remove(OUTLETS);}
    void writePublic(CompoundTag tag,HolderLookup.Provider registries){savedSlots.writePublic(tag,registries);}
    /** Local-only lifecycle publication: no owner lookup, neighbour access or immediate gameplay. */
    void localChanged(){var publication=owner.publication();if(publication!=published){published=publication;modelRevision++;}NativeFluidWorld.queue(owner);}
    void loaded(){if(next==Long.MIN_VALUE)next=Long.MAX_VALUE;localChanged();if(slots.any()||outlets!=0)schedule(1);}
    void motionChanged(){localChanged();if(slots.any()||outlets!=0)schedule(1);}
    void changed(){localChanged();schedule(1);owner.setChanged();var level=owner.getLevel();if(level!=null&&!level.isClientSide&&!owner.inert())level.sendBlockUpdated(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState(),2);}
    private void schedule(int ticks){var level=owner.getLevel();if(level!=null&&!level.isClientSide)next=Math.min(next,level.getGameTime()+Math.max(1,ticks));else if(level==null)next=Long.MIN_VALUE;}
    void tick(){var level=owner.getLevel();if(level!=null&&!level.isClientSide&&!owner.inert()&&level.getGameTime()>=next)update(false);}
    void refresh(){
        var level=owner.getLevel();if(level==null)return;if(owner.inert()){geometry=null;effective=DRY;return;}
        var root=owner.root();boolean closed=!root.targetOpen()&&root.pose(0)<=0;var face=owner.facing();var settings=owner.settings();var before=geometry;
        if(before==null||before.face()!=face||before.direction()!=settings.direction||before.position()!=settings.position||before.height()!=owner.height()||before.row()!=owner.topOffset()||before.column()!=owner.column()||before.width()!=owner.columnsCount()||before.closed()!=closed){var built=NativeSegmentedFluidGeometry.build(face,settings.direction,settings.position,owner.height(),owner.topOffset(),owner.column(),owner.columnsCount(),closed,owner.getBlockPos());geometry=new Geometry(face,settings.direction,settings.position,owner.height(),owner.topOffset(),owner.column(),owner.columnsCount(),closed,built,before==null?1:before.revision()+1);}
        var stored=slots.snapshot();int water=NativeDoorOpeningFluids.sources(level,owner.getBlockPos(),face,!closed,stored);if(overlayBase==null||overlayBase.revision!=stored.revision||overlay!=water){overlayBase=stored;overlay=water;effective=stored.withOpeningWater(water,++effectiveRevision);}
    }
    /** Placement derives only physical fluids; the item never supplies slots or runtime masks. No packet is emitted here. */
    void capturePlacement(BlockState previous){
        var level=owner.getLevel();if(level==null||level.isClientSide||owner.inert()||!NativeFluidWorld.installed(owner))return;refresh();var fallback=NativeFluidWorld.fromState(previous.getFluidState());if(NativeFluidWorld.gaseous(fallback))fallback=FluidStack.EMPTY;var adjacent=new FluidStack[]{FluidStack.EMPTY,FluidStack.EMPTY};boolean boundary=false;
        for(Direction direction:new Direction[]{owner.facing().getOpposite(),owner.facing()}){var pos=owner.getBlockPos().relative(direction);if(!NativeFluidWorld.loaded(level,pos))continue;var fluid=NativeFluidWorld.fromState(level.getFluidState(pos));if(fluid.isEmpty()||NativeFluidWorld.gaseous(fluid))continue;int side=side(.5+direction.getStepX()*.51,.5,.5+direction.getStepZ()*.51);adjacent[side]=fluid;boundary=true;}
        long before=slots.revision();for(int side=0;side<2;side++){var fluid=boundary?adjacent[side]:fallback;if(!fluid.isEmpty()&&!slots.occupied(side))slots.set(side,fluid,level.registryAccess());}if(before!=slots.revision()){schedule(1);refresh();}
    }
    /** Per-cell four-bit source policy, including retained unloaded bits even during stop. */
    public void update(boolean stop){
        var level=owner.getLevel();if(level==null||level.isClientSide||owner.inert()||updating||!NativeFluidWorld.installed(owner))return;updating=true;next=Long.MAX_VALUE;try{
            int previous=outlets,retained=0;boolean supplying=false;var root=owner.root();boolean open=root.targetOpen()||root.pose(0)>0;
            if(!stop&&open){var a=slots.get(0);var b=slots.get(1);int wet=DoorWaterOutlets.wetSide(a.is(Fluids.WATER),b.is(Fluids.WATER),!a.isEmpty(),!b.isEmpty());if(wet>=0){supplying=true;Direction exit=wet==0?owner.facing():owner.facing().getOpposite();int bit=1<<exit.get2DDataValue();var out=owner.getBlockPos().relative(exit);if(!NativeFluidWorld.loaded(level,out))retained|=previous&bit;else{var target=level.getBlockState(out);boolean water=target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER);int action=DoorWaterOutlets.supply((previous&bit)!=0,true,water,water&&target.getFluidState().isSource(),!target.getFluidState().isEmpty(),!water&&target.getFluidState().isEmpty()&&target.canBeReplaced());if(action==DoorWaterOutlets.RETAIN)retained|=bit;else if(action==DoorWaterOutlets.PUBLISH){retained|=bit;publishWater(level,out,Blocks.WATER.defaultBlockState());level.scheduleTick(out,Fluids.WATER,Fluids.WATER.getTickDelay(level));}}}}
            for(Direction face:Direction.Plane.HORIZONTAL){int bit=1<<face.get2DDataValue();if((previous&bit)==0||(retained&bit)!=0)continue;var out=owner.getBlockPos().relative(face);if(!NativeFluidWorld.loaded(level,out)){retained|=bit;continue;}var target=level.getBlockState(out);if(target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER)&&target.getFluidState().isSource()){publishWater(level,out,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1));level.scheduleTick(out,Fluids.FLOWING_WATER,Fluids.FLOWING_WATER.getTickDelay(level));}}
            if(outlets!=retained){outlets=retained;owner.setChanged();}if(!stop&&(supplying||retained!=0))schedule(5);else if(stop)next=Long.MAX_VALUE;
        }finally{updating=false;}
    }
    /** Vanilla's blanket neighbour walk requests cold chunks; source outlets only notify loaded cells. */
    private static void publishWater(net.minecraft.world.level.Level level,BlockPos pos,BlockState state){
        level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        for(Direction direction:Direction.values()){var neighbor=pos.relative(direction);if(NativeFluidWorld.loaded(level,neighbor)){level.neighborShapeChanged(direction.getOpposite(),state,neighbor,pos,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE,512);level.neighborChanged(neighbor,Blocks.WATER,pos);}}
    }

}

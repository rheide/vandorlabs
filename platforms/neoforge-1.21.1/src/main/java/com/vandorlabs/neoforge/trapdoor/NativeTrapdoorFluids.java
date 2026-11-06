package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.render.TrapdoorLeafPose;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/** Stationary ownership and reversible fill. Trapdoors do not own or schedule vanilla water outlets. */
public final class NativeTrapdoorFluids {
    private static final NativeFluidSlots.Snapshot DRY=new NativeFluidSlots().snapshot();
    private static final NativeFluidVolume EMPTY=NativeFluidVolume.build(java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),NativeFluidVolume.SINGLE_SIDE);
    private record Geometry(TrapdoorLeafPose pose,boolean open,NativeTrapdoorFluidGeometry.Plane plane,NativeTrapdoorFluidGeometry.Snapshot snapshot,long revision){}
    private final NativeTrapdoorEntity owner;private final NativeSavedFluidSlots saved=new NativeSavedFluidSlots();private volatile Geometry geometry;
    private volatile NativeFluidSlots.Snapshot effective=saved.storage().snapshot();private NativeFluidSlots.Snapshot overlayBase;private int overlay=-1;private long effectiveRevision;private volatile NativeFluidRenderSnapshot render=NativeFluidRenderSnapshot.EMPTY;
    NativeTrapdoorFluids(NativeTrapdoorEntity owner){this.owner=owner;}
    private ContainedFluidHost host(){return (ContainedFluidHost)owner;}
    public NativeFluidSlots slots(){return saved.storage();}public NativeFluidSlots.Snapshot effective(){return effective;}public NativeFluidVolume volume(){var state=geometry;return state==null||state.snapshot()==null?EMPTY:state.snapshot().volume();}public long revision(){var state=geometry;return state==null?0:state.revision();}
    public int side(double x,double y,double z){var state=geometry;return state==null?0:state.plane().side(x,y,z);}
    public NativeFluidRenderSnapshot render(){return render;}public void render(NativeFluidRenderSnapshot snapshot){render=snapshot;}
    void disable(){geometry=null;effective=DRY;render=NativeFluidRenderSnapshot.EMPTY;overlayBase=null;}
    void read(CompoundTag tag,HolderLookup.Provider registries){saved.read(tag,registries);overlayBase=null;}
    void write(CompoundTag tag){saved.write(tag);}void writePublic(CompoundTag tag,HolderLookup.Provider registries){saved.writePublic(tag,registries);}
    /** Only queue locally. Model workers read immutable prepared values and never scan owners or neighbors. */
    void localChanged(){NativeFluidWorld.queue(host());}
    void changed(){localChanged();owner.setChanged();var level=owner.getLevel();if(level!=null&&!level.isClientSide&&!owner.inert())level.sendBlockUpdated(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState(),2);}
    /** Dry apertures prepare only their closed plane; exact fluid cells materialize when a resolved fluid is present. */
    void refresh(){var level=owner.getLevel();if(level==null)return;if(owner.inert()||owner.trapdoorVisual()==null){disable();return;}var visual=owner.trapdoorVisual();var before=geometry;if(before==null||!before.pose().equals(visual.pose())||before.open()!=visual.open()){var plane=before!=null&&before.pose().equals(visual.pose())?before.plane():NativeTrapdoorFluidGeometry.plane(visual.pose());geometry=new Geometry(visual.pose(),visual.open(),plane,null,before==null?1:before.revision()+1);}var stored=slots().snapshot();int water=NativeTrapdoorOpeningFluids.sources(level,owner.getBlockPos(),geometry.plane(),visual.open(),stored);if(overlayBase!=stored||overlay!=water){overlayBase=stored;overlay=water;effective=stored.withOpeningWater(water,++effectiveRevision);}boolean wet=effective.side(0).fluidType()!=null||effective.side(1).fluidType()!=null;var state=geometry;if(wet&&state.snapshot()==null){var pos=owner.getBlockPos();var built=NativeTrapdoorFluidGeometry.build(visual.pose(),visual.open(),pos.getX(),pos.getY(),pos.getZ());geometry=new Geometry(state.pose(),state.open(),state.plane(),built,state.revision()+1);}else if(!wet&&state.snapshot()!=null)geometry=new Geometry(state.pose(),state.open(),state.plane(),null,state.revision()+1);}
    public net.minecraft.core.Direction[] placementFaces(){var state=geometry;var visual=owner.trapdoorVisual();var plane=state!=null?state.plane():visual==null?new NativeTrapdoorFluidGeometry.Plane(0,1,0,0,1):NativeTrapdoorFluidGeometry.plane(visual.pose());int axis=Math.abs(plane.x())>Math.abs(plane.y())?0:1;double chosen=axis==0?plane.x():plane.y();if(Math.abs(plane.z())>Math.abs(chosen))axis=2;return axis==0?new net.minecraft.core.Direction[]{net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST}:axis==1?new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.UP}:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH};}
    /** Derive only physical fluid after sanitized item choices have installed; both opening slots are eligible. */
    public void capturePlacement(BlockState previous){var level=owner.getLevel();if(level==null||level.isClientSide||owner.inert()||!NativeFluidWorld.installed(host()))return;refresh();var fallback=NativeFluidWorld.fromState(previous.getFluidState());if(NativeFluidWorld.gaseous(fallback))fallback=FluidStack.EMPTY;var adjacent=new FluidStack[]{FluidStack.EMPTY,FluidStack.EMPTY};boolean boundary=false;for(var direction:placementFaces()){var pos=owner.getBlockPos().relative(direction);if(!NativeFluidWorld.loaded(level,pos))continue;var fluid=NativeFluidWorld.fromState(level.getFluidState(pos));if(fluid.isEmpty()||NativeFluidWorld.gaseous(fluid))continue;int side=side(.5+direction.getStepX()*.51,.5+direction.getStepY()*.51,.5+direction.getStepZ()*.51);adjacent[side]=fluid;boundary=true;}long revision=slots().revision();for(int side=0;side<2;side++){var fluid=boundary?adjacent[side]:fallback;if(!fluid.isEmpty()&&!slots().occupied(side))slots().set(side,fluid,level.registryAccess());}if(revision!=slots().revision())refresh();}
}

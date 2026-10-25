package com.vandorlabs.neoforge.shape;

import com.vandorlabs.fluids.FluidVolume;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import com.vandorlabs.neoforge.fluid.ContainedFluidHost;
import com.vandorlabs.neoforge.fluid.NativeContainedFluids;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.SlabStairDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Housing storage reused with authored side/stair settings and opaque contained fluid slots. */
public final class ProgrammableShapeEntity extends ProgrammableBlockEntity implements ContainedFluidHost {
    public static final ModelProperty<SlabStairDialog.State> SHAPE_SETTINGS=new ModelProperty<>();
    private int side=-1;
    private StairState stairs=new StairState(8,0);
    private final NativeContainedFluids fluids=new NativeContainedFluids(this);
    private volatile FluidVolume volume;
    private long geometryRevision;
    private String geometryKey="";
    public ProgrammableShapeEntity(BlockPos pos,BlockState state){super(ShapeRegistration.ENTITY.get(),pos,state);refreshGeometryLocal();}
    public boolean isStair(){return getBlockState().getBlock() instanceof ProgrammableStairsBlock;}
    public int sideTexture(){return side;}
    public StairState stairState(){return stairs;}
    public SlabStairDialog.State shapeSettings(){var s=settings();return new SlabStairDialog.State(s.mainTexture,side,s.layout,s.facesEnabled,s.faces(),-1,stairs);}
    public boolean applyShape(SlabStairDialog.State next){
        if(next==null||next.main<0||next.side<-1&&!(next.side==-3&&side==-3)||next.layout<0||next.layout>2||!StairState.valid(next.stairs.tread,next.stairs.roll))return false;
        side=next.side<0?next.side:ModernFinishCatalog.storedChoice(next.side);stairs=next.stairs;
        apply(new ProgrammableBlockDialog.State(next.main,next.layout,next.facesEnabled,next.faces(),-1));
        if(level!=null&&!level.isClientSide&&getBlockState().getBlock() instanceof ProgrammableStairsBlock block){BlockState actual=block.connected(getBlockState(),level,worldPosition);if(actual!=getBlockState())level.setBlock(worldPosition,actual,3);}
        refreshGeometryLocal();containedFluidsChanged();return true;
    }
    /** Only block state and already-loaded settings are sampled here; no neighbours or gameplay updates. */
    public void refreshGeometryLocal(){refreshGeometry(getBlockState());}
    /** Deferred main-thread refresh after installed identity and loaded chunks have been checked. */
    @Override public void refreshFluidGeometry(){refreshGeometry(level==null?getBlockState():ShapeGeometry.connected(getBlockState(),level,worldPosition,stairs));}
    private void refreshGeometry(BlockState pose){
        if(stairs==null)return;String key=pose+"/"+stairs.packed();if(key.equals(geometryKey))return;
        java.util.List<FluidVolume.Box> solids=new java.util.ArrayList<>();
        for(var b:ShapeGeometry.boxes(pose,stairs))solids.add(new FluidVolume.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        volume=FluidVolume.build(solids,null,(x,y,z)->0);geometryKey=key;geometryRevision++;if(fluids!=null)fluids.invalidateGeometry();
    }
    @Override public void setBlockState(BlockState state){super.setBlockState(state);refreshGeometryLocal();}
    @Override public void onLoad(){super.onLoad();refreshGeometryLocal();fluids.onLoad();}
    @Override public void setRemoved(){fluids.removed();super.setRemoved();}
    @Override public void onChunkUnloaded(){fluids.onChunkUnloaded();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);stairs=StairState.read(new NbtPrimitiveData(tag));
        side=com.vandorlabs.persistence.HousingSideTexture.read(new NbtPrimitiveData(tag),ModernFinishCatalog::storedChoice);
        fluids.read(tag,registries);refreshGeometryLocal();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);stairs.write(new NbtPrimitiveData(tag));tag.putInt("SideTexture",side);fluids.write(tag);}
    @Override public ModelData getModelData(){return super.getModelData().derive().with(SHAPE_SETTINGS,shapeSettings()).with(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA,fluids.renderSnapshot()).build();}
    @Override public BlockEntity fluidOwner(){return this;}
    @Override public NativeContainedFluids containedFluids(){return fluids;}
    @Override public FluidVolume fluidVolumeSnapshot(){return volume;}
    @Override public long fluidGeometryRevision(){return geometryRevision;}
}

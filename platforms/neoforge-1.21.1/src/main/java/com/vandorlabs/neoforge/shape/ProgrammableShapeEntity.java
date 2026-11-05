package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Native shape housing and immutable geometry; owning-thread fluid work is deferred. */
public class ProgrammableShapeEntity extends ProgrammableBlockEntity implements com.vandorlabs.neoforge.fluid.ContainedFluidHost {
    public record Appearance(ProgrammableBlockDialog.State housing,int sideTexture,StairState stairs) {
        public boolean valid(){
            if(housing==null||stairs==null||!StairState.valid(stairs.tread,stairs.roll)
                    ||housing.layout<0||housing.layout>2||!ModernFinishCatalog.validChoice(housing.mainTexture)
                    ||sideTexture!=-1&&!ModernFinishCatalog.validChoice(sideTexture))return false;
            for(int i=0;i<6;i++)if(housing.face(i)!=-1&&!ModernFinishCatalog.validChoice(housing.face(i)))return false;
            return true;
        }
    }
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<Appearance> APPEARANCE=new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private final com.vandorlabs.neoforge.fluid.NativeFluidSlots fluids=new com.vandorlabs.neoforge.fluid.NativeFluidSlots();
    private static final com.vandorlabs.neoforge.fluid.NativeFluidVolume EMPTY_VOLUME=com.vandorlabs.neoforge.fluid.NativeFluidVolume.build(java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),com.vandorlabs.neoforge.fluid.NativeFluidVolume.SINGLE_SIDE);
    private volatile com.vandorlabs.neoforge.fluid.NativeFluidVolume volume=EMPTY_VOLUME;
    private volatile com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot fluidRender=com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.EMPTY;
    private BlockState volumeState;private long volumeSettings,geometryRevision;
    private final boolean stair;
    private StairState stairState=new StairState(8,0);
    private volatile Appearance appearance;
    public ProgrammableShapeEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean stair){
        this(type,pos,state,stair,defaults());
    }
    protected ProgrammableShapeEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean stair,com.vandorlabs.ui.ProgrammableBlockDialog.State initial){
        super(type,pos,state,initial);this.stair=stair;appearance=new Appearance(settings(),sideTexture(),stairState);
    }
    public final boolean isStair(){return stair;}
    public final StairState stairState(){return stairState;}
    /** Owned slots; gameplay mutations and publication use the contained-fluid service. */
    public final com.vandorlabs.neoforge.fluid.NativeFluidSlots containedFluids(){return fluids;}
    public final Appearance appearance(){return appearance;}
    private void publishAppearance(){appearance=new Appearance(settings(),sideTexture(),stairState);appearancePublished();}
    /** Subclasses publish their own immutable projection here, before any notification. Never called by this constructor. */
    protected void appearancePublished(){ }
    /** End a family transaction after all of its local fields have been installed. */
    protected final void publishShapeChange(){publishAppearance();refresh();com.vandorlabs.neoforge.fluid.NativeFluidWorld.queue(this);}
    /** Only the authenticated server handler may invoke live edits. No saved-value normalization here. */
    public boolean applyShape(Appearance next){
        if(next==null||!next.valid())return false;
        if(level!=null&&(level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()))return false;
        int side=next.sideTexture<0?-1:ModernFinishCatalog.storedChoice(next.sideTexture);
        boolean changed=sideTexture()!=side||stairState.tread!=next.stairs.tread||stairState.roll!=next.stairs.roll;
        setSideTexture(side);
        if(stairState.tread!=next.stairs.tread||stairState.roll!=next.stairs.roll)stairState=next.stairs;
        boolean housingChanged=updateSettings(next.housing);
        if(changed||housingChanged)publishShapeChange();
        return true;
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        stairState=StairState.read(new ModernHousingTags(tag));fluids.read(tag,registries);
        super.loadAdditional(tag,registries);com.vandorlabs.neoforge.fluid.NativeFluidWorld.queue(this);
    }
    @Override public void apply(ProgrammableBlockDialog.State value){applyShape(new Appearance(value,sideTexture(),stairState));}
    @Override protected void housingLoaded(){publishAppearance();}
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return super.getModelData().derive().with(APPEARANCE,appearance).with(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA,fluidRender).build();}
    @Override public net.minecraft.world.level.block.entity.BlockEntity fluidOwner(){return this;}
    @Override public com.vandorlabs.neoforge.fluid.NativeFluidVolume fluidVolumeSnapshot(){return volume;}
    @Override public com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot fluidRenderSnapshot(){return fluidRender;}
    @Override public void publishFluidRenderSnapshot(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot snapshot){fluidRender=java.util.Objects.requireNonNull(snapshot);}
    @Override public long fluidGeometryRevision(){return geometryRevision;}
    @Override public int containedFluidSide(double x,double y,double z){return 0;}
    @Override public void containedFluidsChanged(){refresh();}
    /** A local key; topology-aware families include their prepared topology revision here. */
    protected long fluidGeometryKey(){return stairState.packed();}
    /** Called on the deferred owning thread; returned solids must be local unit-cell geometry. */
    protected java.util.List<net.minecraft.world.phys.AABB> fluidSolidBoxes(BlockState state,long key){
        return state.getBlock() instanceof NativeProgrammableSlab?NativeProgrammableSlab.solidBoxes(state):state.getBlock() instanceof NativeProgrammableStairs?NativeProgrammableStairs.solidBoxes(state,(int)key):EMPTY_VOLUME.solids();
    }
    protected com.vandorlabs.neoforge.fluid.NativeFluidVolume.Side fluidSidePartition(){return com.vandorlabs.neoforge.fluid.NativeFluidVolume.SINGLE_SIDE;}
    @Override public void refreshFluidGeometry(){
        BlockState state=getBlockState();long key=fluidGeometryKey();
        if(volumeState==state&&volumeSettings==key)return;
        volume=com.vandorlabs.neoforge.fluid.NativeFluidVolume.build(fluidSolidBoxes(state,key),com.vandorlabs.neoforge.fluid.NativeFluidVolume.SINGLE_SIDE);volumeState=state;volumeSettings=key;geometryRevision++;
    }
    @Override public void onLoad(){super.onLoad();com.vandorlabs.neoforge.fluid.NativeFluidWorld.queue(this);}
    @Override public void setRemoved(){com.vandorlabs.neoforge.fluid.NativeFluidWorld.removed(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){com.vandorlabs.neoforge.fluid.NativeFluidWorld.unloaded(this);super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);StairState.write(new ModernHousingTags(tag),stairState.tread,stairState.roll);fluids.write(tag);
    }
}

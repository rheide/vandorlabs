package com.vandorlabs.neoforge.door;
import net.minecraft.core.*;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.render.SpaceDoorControlPanel;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.client.model.data.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
/** Inactive Large roles and immutable geometry/fluid/model projections; production registration is deferred. */
public class NativeLargeDoorEntity extends NativeProgrammableDoorEntity {
    private boolean assigned;private int column,row;
    public record LargeVisual(Visual shared,BlockPos anchor,int column,int row,NativeLargeDoorGeometry.Snapshot geometry){}
    public static final ModelProperty<LargeVisual> LARGE_VISUAL=new ModelProperty<>();
    private volatile LargeVisual largeVisual;
    private record RootModel(Visual shared,NativeLargeDoorGeometry.Snapshot geometry,long version){}
    private static final class RootPublication {volatile RootModel value;}
    private final RootPublication rootPublication=new RootPublication();private volatile RootPublication binding;private long rootVersion;
    private record ControlSignature(com.vandorlabs.redstone.ChannelList channels,int trigger,int security,int panel,boolean readerOpen,boolean signal){}
    private ControlSignature controls;
    private volatile NativeLargeDoorGeometry.Snapshot rootGeometry;private NativeDoorSettings rootGeometrySettings;private BlockState rootGeometryState;
    private static final NativeFluidVolume EMPTY_VOLUME=NativeFluidVolume.build(java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),NativeFluidVolume.SINGLE_SIDE);
    private record FluidPose(NativeLargeDoorGeometry.Snapshot geometry,int column,int row,NativeDoorFluidGeometry.Plane plane,NativeFluidVolume volume,long revision){}
    private volatile FluidPose fluidPose=new FluidPose(null,0,0,null,EMPTY_VOLUME,0);
    private NativeFluidSlots.Snapshot effectiveBase;private volatile NativeFluidSlots.Snapshot effective;
    private int openingWater=-1;private long effectiveRevision,modelRevision;
    public NativeLargeDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state,true,false);effective=containedFluids().snapshot();publishProjection(settings(),state,pos,null);}
    public final void assign(BlockPos anchor){column=Math.abs(worldPosition.getX()-anchor.getX())+Math.abs(worldPosition.getZ()-anchor.getZ());row=worldPosition.getY()-anchor.getY();assigned=true;NativeLargeDoorTargets.add(this);if(level!=null&&!level.isClientSide&&isAnchor())com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(this);publishProjection(settings(),getBlockState(),anchor,null);NativeFluidWorld.queue(this);setChanged();}
    public final boolean isAnchor(){return assigned&&column==0&&row==0;}
    public final int column(){return column;}public final int row(){return row;}
    public final BlockPos anchorPos(){return level==null||!(getBlockState().getBlock() instanceof NativeLargeDoorBlock)?worldPosition:worldPosition.relative(getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise(),-column).below(row);}
    private void writeRole(CompoundTag tag){tag.putBoolean("LargeDoorAssigned",assigned);tag.putInt("LargeDoorColumn",column);tag.putInt("LargeDoorRow",row);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        // Released Large parts all use the rotating block prototype when the motion schema is absent.
        // Normalize locally before publishing; followers deliberately never enter the channel-registration hook.
        if(!tag.contains("SpaceDoorSchema")&&getBlockState().getBlock() instanceof NativeLargeDoorBlock){tag=tag.copy();tag.putBoolean("SpaceDoorSliding",false);tag.putInt("SpaceDoorSchema",2);}
        assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInt("LargeDoorColumn");row=tag.getInt("LargeDoorRow");if(column<0||column>2||row<0||row>2)assigned=false;super.loadAdditional(tag,registries);NativeLargeDoorTargets.add(this);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);writeRole(tag);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);writeRole(tag);return tag;}
    private synchronized void publishProjection(NativeDoorSettings settings,BlockState raw,BlockPos anchor,NativeLargeDoorGeometry.Snapshot geometry){
        var resolved=raw.setValue(NativeLargeDoorBlock.PAIRED,true).setValue(NativeLargeDoorBlock.HINGE,DoorHingeSide.RIGHT).setValue(NativeLargeDoorBlock.HALF,geometry!=null&&worldPosition.equals(anchor)?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);var previous=largeVisual;
        if(previous!=null&&previous.shared().settings()==settings&&previous.shared().state()==resolved&&previous.shared().recovery()==placementRecovery()&&previous.shared().readers()==(binding==null||binding.value==null?readerProjection():binding.value.shared().readers())&&previous.anchor().equals(anchor)&&previous.column()==column&&previous.row()==row&&previous.geometry()==geometry)return;
        var side=settings.door().panel?(settings.door().sliding?SpaceDoorControlPanel.Side.RIGHT:SpaceDoorControlPanel.Side.LEFT):SpaceDoorControlPanel.Side.NONE;
        largeVisual=new LargeVisual(new Visual(settings,resolved,side,true,placementRecovery(),binding==null||binding.value==null?readerProjection():binding.value.shared().readers()),anchor.immutable(),column,row,geometry);modelRevision++;requestModelDataUpdate();
    }
    public final LargeVisual largeVisual(){var current=largeVisual;var link=binding;var published=link==null?null:link.value;if(current==null||published==null)return current;var common=published.shared();var state=common.state().setValue(NativeLargeDoorBlock.HALF,worldPosition.equals(current.anchor())?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);if(current.shared().settings()==common.settings()&&current.shared().state()==state&&current.shared().readers()==common.readers()&&current.shared().recovery()==common.recovery()&&current.geometry()==published.geometry())return current;var observed=current;current=new LargeVisual(new Visual(common.settings(),state,common.side(),true,common.recovery(),common.readers()),current.anchor(),current.column(),current.row(),published.geometry());synchronized(this){if(largeVisual==observed&&binding==link&&link.value==published)largeVisual=current;else current=largeVisual;}return current;}
    private void publishRoot(NativeLargeDoorGeometry.Snapshot geometry){var state=getBlockState().setValue(NativeLargeDoorBlock.PAIRED,true).setValue(NativeLargeDoorBlock.HINGE,DoorHingeSide.RIGHT).setValue(NativeLargeDoorBlock.HALF,DoubleBlockHalf.LOWER);var profile=settings();var readers=readerProjection();var before=rootPublication.value;if(before==null||before.shared().settings()!=profile||before.shared().state()!=state||before.shared().readers()!=readers||before.shared().recovery()!=placementRecovery()||before.geometry()!=geometry){var side=profile.door().panel?(profile.door().sliding?SpaceDoorControlPanel.Side.RIGHT:SpaceDoorControlPanel.Side.LEFT):SpaceDoorControlPanel.Side.NONE;rootPublication.value=new RootModel(new Visual(profile,state,side,true,placementRecovery(),readers),geometry,++rootVersion);}binding=rootPublication;}
    @Override public ModelData getModelData(){var p=largeVisual();if(p==null)return super.getModelData();return ModelData.builder().with(SETTINGS,p.shared().settings()).with(VISUAL,p.shared()).with(LARGE_VISUAL,p).with(NativeFluidRenderSnapshot.DATA,fluidRenderSnapshot()).build();}
    @Override public long modelDataRevision(){var link=binding;return modelRevision+(link==null||link.value==null?0:link.value.version());}
    private void queueAssembly(BlockPos anchor,Direction face){if(level==null)return;Direction width=face.getCounterClockWise();for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos pos=anchor.relative(width,x).above(y);if(!pos.equals(worldPosition)&&NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part&&part.anchorPos().equals(anchor))NativeFluidWorld.queue(part);}}
    private NativeLargeDoorGeometry.Snapshot rootGeometry(){var state=getBlockState();var settings=settings();if(rootGeometry!=null&&rootGeometrySettings==settings&&rootGeometryState==state)return rootGeometry;var key=NativeLargeDoorGeometry.key(settings(),state.getValue(NativeLargeDoorBlock.FACING),state.getValue(NativeLargeDoorBlock.OPEN));var before=rootGeometry;if(before==null||!before.key().equals(key)){rootGeometry=NativeLargeDoorGeometry.build(settings(),key.face(),key.open(),worldPosition);queueAssembly(worldPosition,key.face());}rootGeometrySettings=settings;rootGeometryState=state;return rootGeometry;}
    final NativeLargeDoorGeometry.Snapshot rootGeometrySnapshot(){return rootGeometry();}
    @Override public void refreshFluidGeometry(){
        var before=fluidPose;var root=level==null?null:NativeLargeDoorLifecycle.root(level,worldPosition);
        if(root==null||column<0||column>2||row<0||row>2){if(before.geometry()!=null){queueAssembly(anchorPos(),getBlockState().getValue(NativeLargeDoorBlock.FACING));fluidPose=new FluidPose(null,column,row,null,EMPTY_VOLUME,before.revision()+1);}publishEffective(0);binding=null;publishProjection(settings(),getBlockState(),anchorPos(),null);return;}
        var geometry=root.rootGeometry();if(before.geometry()!=geometry||before.column()!=column||before.row()!=row){var built=NativeLargeDoorFluidGeometry.build(root.settings(),geometry,column,row);fluidPose=new FluidPose(geometry,column,row,built.plane(),built.volume(),before.revision()+1);}
        publishEffective(NativeDoorOpeningFluids.sources(level,worldPosition,geometry.key().face(),geometry.key().open(),containedFluids().snapshot()));
        root.publishRoot(geometry);binding=root.rootPublication;publishProjection(root.settings(),root.getBlockState(),root.getBlockPos(),geometry);largeVisual();
    }
    private void publishEffective(int water){var stored=containedFluids().snapshot();if(effectiveBase!=null&&effectiveBase.revision==stored.revision&&openingWater==water)return;effectiveBase=stored;openingWater=water;effective=stored.withOpeningWater(water,++effectiveRevision);}
    @Override public NativeFluidSlots.Snapshot effectiveFluidSnapshot(){var value=effective;return value==null?containedFluids().snapshot():value;}
    @Override public NativeFluidVolume fluidVolumeSnapshot(){return fluidPose.volume();}
    @Override public long fluidGeometryRevision(){return fluidPose.revision();}
    @Override public boolean openingFluidVolume(){return fluidPose.geometry()!=null;}
    @Override public int containedFluidSide(double x,double y,double z){var p=fluidPose.plane();return p==null?0:p.side(x,y,z);}
    @Override public Direction[] fluidPlacementFaces(){var p=fluidPose.plane();return p==null?new Direction[0]:new Direction[]{fluidPose.geometry().key().face(),fluidPose.geometry().key().face().getOpposite()};}
    @Override public void containedFluidsChanged(){super.containedFluidsChanged();if(level!=null&&!level.isClientSide)markWaterGushDirty();NativeFluidWorld.queue(this);}
    @Override public void onLoad(){super.onLoad();NativeLargeDoorTargets.add(this);}
    @Override public void setRemoved(){NativeLargeDoorTargets.remove(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeLargeDoorTargets.remove(this);super.onChunkUnloaded();}
    @Override protected Boolean legacySlidingModel(){return getBlockState().getBlock() instanceof NativeLargeDoorBlock?Boolean.FALSE:null;}
    @Override protected NativeProgrammableDoorEntity controlRoot(){return level==null?null:NativeLargeDoorLifecycle.root(level,worldPosition);}
    @Override protected boolean controlRole(){return getBlockState().getBlock() instanceof NativeLargeDoorBlock&&isAnchor();}
    @Override protected boolean controlStructureComplete(){return level!=null&&NativeLargeDoorLifecycle.complete(level,this);}
    @Override protected int controlLocalSignal(){return NativeLargeDoorLifecycle.localPower(level,this);}
    @Override protected void controlPowerChanged(){NativeLargeDoorLifecycle.updatePower(this);controls=controlSignature();}
    @Override protected NativeProgrammableDoorEntity credentialMate(){return null;}
    @Override protected boolean readerUsable(net.minecraft.server.level.ServerPlayer player){return level!=null&&NativeLargeDoorLifecycle.authorized(level,this,player);}
    @Override protected void queueFluidParts(){NativeFluidWorld.queue(this);}
    @Override protected void preparePublication(){var before=rootGeometry;boolean update=!java.util.Objects.equals(controls,controlSignature());if(update)controlPowerChanged();refreshFluidGeometry();if(!update&&before!=rootGeometry&&level!=null){Direction width=getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise();for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos pos=worldPosition.relative(width,x).above(y);if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part&&part.anchorPos().equals(worldPosition))part.refreshFluidGeometry();}}}
    private ControlSignature controlSignature(){var d=settings().door();return new ControlSignature(getRedstoneChannels(),d.trigger,settings().securityMode(),settings().panelMode(),settings().securityMode()>0&&securityOpen(),channelSignal());}
    @Override protected void publicationCompleted(){if(!java.util.Objects.equals(controls,controlSignature()))localInputChanged();}

}

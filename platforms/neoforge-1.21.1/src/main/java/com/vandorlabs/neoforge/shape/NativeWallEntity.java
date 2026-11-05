package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.persistence.*;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.*;

/** Shared wall save/editor host. Discovery publishes immutable geometry on the owning thread. */
public class NativeWallEntity extends ProgrammableShapeEntity {
    public record WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,PanelGlassSettings glass,NativeWallGeometry geometry,NativePortholeLayout aperture,PanelPlayback playback,boolean chunkBody){
        public WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,PanelGlassSettings glass,NativeWallGeometry geometry,NativePortholeLayout aperture,PanelPlayback playback){this(base,walls,frames,glass,geometry,aperture,playback,true);}
        public WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,PanelGlassSettings glass,NativeWallGeometry geometry,NativePortholeLayout aperture){this(base,walls,frames,glass,geometry,aperture,PanelPlayback.DEFAULT);}
        public WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,PanelGlassSettings glass,NativeWallGeometry geometry){this(base,walls,frames,glass,geometry,NativePortholeLayout.unit(walls==null||walls.portholeShape<0||walls.portholeShape>3?0:walls.portholeShape));}
        public WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,NativeWallGeometry geometry){this(base,walls,frames,PanelGlassSettings.fresh(),geometry);}
    }
    public static final ModelProperty<WallAppearance> WALL_APPEARANCE=new ModelProperty<>();
    NativeWallLayouts.Group textureGroup;
    NativeWallLayouts.Group apertureGroup;
    private NativePortholeLayout aperture=NativePortholeLayout.unit(0);
    private final boolean porthole;
    private WallSettings walls;
    private FrameSettings frames=defaultFrames();
    private PanelGlassSettings glass=PanelGlassSettings.fresh();
    private PanelPlayback playback=PanelPlayback.DEFAULT;
    private NativeWallGeometry geometry;
    private NativeFluidVolume.Side partition;
    private long geometryGeneration,topologyRevision,modelRevision;
    private volatile WallAppearance wallAppearance;
    private volatile AABB renderBounds;
    public NativeWallEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean porthole){
        super(type,pos,state,false,new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1));this.porthole=porthole;walls=new WallSettings(false,false,0,0,porthole,0);
        geometry=initialGeometry(state);partition=geometry::side;renderBounds=geometry.selectionBounds().move(pos);publishWallAppearance();
    }
    private static FrameSettings defaultFrames(){return new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false);}
    private static NativeWallGeometry initialGeometry(BlockState state){
        if(state.getBlock() instanceof NativeDiagonalWallBlock)return NativeDiagonalWallGeometry.build(NativeDiagonalWallTopology.initial(state,0));
        Direction facing=Direction.NORTH;int depth=0;
        for(Property<?> property:state.getProperties()){
            Comparable<?> value=state.getValue(property);
            if(property.getName().equals("facing")&&value instanceof Direction direction&&direction.getAxis()!=Direction.Axis.Y)facing=direction;
            if(property.getName().equals("depth")&&value instanceof Integer number&&number>=0&&number<=2)depth=number;
        }
        return NativeFlatWallGeometry.straight(facing,depth);
    }
    public final boolean isPorthole(){return porthole;}
    public final WallSettings wallSettings(){return walls;}
    public final FrameSettings frameSettings(){return frames;}
    public final PanelGlassSettings panelGlassSettings(){return glass;}
    public final PanelPlayback panelPlayback(){return playback;}
    public final NativePortholeLayout apertureLayout(){return aperture;}
    /** Deferred topology calls this before the owning pass publishes model data. */
    boolean publishApertureLayout(NativePortholeLayout next){
        Objects.requireNonNull(next);if(aperture.equals(next))return false;
        aperture=next;publishWallAppearance();return true;
    }
    public final WallAppearance wallAppearance(){return wallAppearance;}
    public long modelDataRevision(){return modelRevision;}
    public final long topologyRevision(){return topologyRevision;}
    public final AABB renderBounds(){return renderBounds;}
    private boolean chunkBody(){var state=getBlockState();return !(state.getBlock() instanceof NativeDiagonalWallBlock)||settings().layout==0&&com.vandorlabs.render.DiagonalChunkPolicy.contained(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),state.getValue(NativeDiagonalWallBlock.FACING).get3DDataValue(),state.getValue(NativeDiagonalWallBlock.INVERTED),walls.halfHeight,walls.fullWidth);}
    private void publishWallAppearance(){modelRevision++;wallAppearance=new WallAppearance(appearance(),walls,frames,glass,geometry,aperture,playback,chunkBody());}
    @Override protected void appearancePublished(){publishWallAppearance();}
    public static boolean validFrame(int choice){if(choice==-1)return true;if(!ModernFinishCatalog.validChoice(choice))return false;String category=ModernFinishCatalog.option(choice).category();return category.equals("Frame")||category.startsWith("Frame/");}
    public static boolean validSurface(int choice){return validFrame(choice)||ModernFinishCatalog.validChoice(choice)&&ModernFinishCatalog.option(choice).general();}
    public static boolean valid(WallSettings value){return value!=null&&value.fill>=0&&value.fill<=3&&value.join>=0&&value.join<=11&&value.portholeShape>=0&&value.portholeShape<=3;}
    public static boolean valid(FrameSettings value){return value!=null&&validFrame(value.front)&&validSurface(value.interior)&&(value.exterior==-2||validSurface(value.exterior))&&(value.artworkVersion==0||value.artworkVersion==1)&&(value.splitTop==-1||ModernFinishCatalog.validChoice(value.splitTop))&&(value.splitBottom==-1||ModernFinishCatalog.validChoice(value.splitBottom));}
    private static boolean same(WallSettings a,WallSettings b){return a.fullWidth==b.fullWidth&&a.halfHeight==b.halfHeight&&a.fill==b.fill&&a.join==b.join&&a.joinPortholes==b.joinPortholes&&a.portholeShape==b.portholeShape;}
    private static boolean same(FrameSettings a,FrameSettings b){return a.front==b.front&&a.interior==b.interior&&a.exterior==b.exterior&&a.artworkVersion==b.artworkVersion&&a.splitTop==b.splitTop&&a.splitBottom==b.splitBottom&&a.tile==b.tile&&a.displayFit==b.displayFit&&a.split==b.split&&a.bare==b.bare;}
    /** Validate the complete immediate edit before changing any base, wall or frame field. */
    public boolean configure(Appearance base,WallSettings nextWalls,FrameSettings nextFrames){
        if(base==null||!base.valid()||!valid(nextWalls)||!valid(nextFrames)||level!=null&&(level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()))return false;
        boolean wallChanged=!same(walls,nextWalls),frameChanged=!same(frames,nextFrames);
        if(wallChanged)walls=nextWalls;if(frameChanged)frames=nextFrames;
        Appearance before=appearance();super.applyShape(base);
        if((wallChanged||frameChanged)&&appearance()==before)publishShapeChange();
        if(wallChanged||frameChanged||appearance()!=before)NativeWallLayouts.invalidate(this);
        return true;
    }
    public static boolean validPanelGlass(PanelGlassSettings value){return value!=null&&com.vandorlabs.tiles.GlassTextures.valid(value.shade)&&com.vandorlabs.tiles.GlassTextures.validTint(value.tint)&&(value.material==-1||ModernFinishCatalog.validChoice(value.material))&&(value.layout==-1||com.vandorlabs.render.TextureLayoutMode.valid(value.layout));}
    private static int panelMaterial(int value){return value==-1||ModernFinishCatalog.validChoice(value)?value:-1;}
    /** Atomic equivalent of the current panel appearance/layout setter order. */
    public boolean configurePanelGlass(PanelGlassSettings next,boolean joinPortholes,int portholeShape){return configurePanelGlass(next,playback,joinPortholes,portholeShape);}
    public boolean configurePanelGlass(PanelGlassSettings next,PanelPlayback nextPlayback,boolean joinPortholes,int portholeShape){
        if(nextPlayback==null||!validPanelGlass(next)||portholeShape<0||portholeShape>3||level!=null&&(level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()))return false;
        int raw=glass.layout;boolean stretch=next.stretch;
        if(glass.stretch!=stretch||glass.textureJoin!=next.textureJoin)raw=-1;
        int layout=next.textureLayout();if(PanelGlassSettings.textureLayout(raw,stretch)!=layout){raw=layout;stretch=layout!=1;}
        boolean changed=glass.shade!=next.shade||glass.tint!=next.tint||glass.material!=next.material||glass.layout!=raw||glass.stretch!=stretch||glass.textureJoin!=next.textureJoin||glass.framed!=next.framed;
        boolean wallChanged=walls.joinPortholes!=joinPortholes||walls.portholeShape!=portholeShape;
        if(changed)glass=new PanelGlassSettings(next.shade,next.material,raw,stretch,next.tint,next.framed,next.textureJoin);
        if(wallChanged)walls=new WallSettings(walls.fullWidth,walls.halfHeight,walls.fill,walls.join,joinPortholes,portholeShape);
        boolean playbackChanged=!playback.equals(nextPlayback);if(playbackChanged)playback=nextPlayback;
        if(changed||wallChanged||playbackChanged){NativeWallLayouts.invalidate(this);publishShapeChange();}
        return true;
    }
    /** Stage without neighbour/client notification so the complete projection is published first. */
    protected boolean stageDiagonalState(BlockState next){if(level==null){setBlockState(next);return true;}return level.setBlock(worldPosition,next,net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);}
    public boolean configureDiagonal(int mode,int fill,int join,Direction facing,boolean inverted){
        var beforeState=getBlockState();if(!(beforeState.getBlock() instanceof NativeDiagonalWallBlock)||mode<0||mode>2||fill<0||fill>3||join<0||join>11||facing==null||facing.getAxis()==Direction.Axis.Y||level!=null&&(level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=this))return false;
        WallSettings before=walls,next=new WallSettings(mode!=0,mode==2,fill,join,before.joinPortholes,before.portholeShape);var nextState=beforeState.setValue(NativeDiagonalWallBlock.FACING,facing).setValue(NativeDiagonalWallBlock.INVERTED,inverted);boolean changed=!same(before,next),stateChanged=beforeState!=nextState;if(!changed&&!stateChanged)return true;
        walls=next;
        if(stateChanged){
            boolean staged;try{staged=stageDiagonalState(nextState);}catch(RuntimeException failure){walls=before;if(level==null)setBlockState(beforeState);throw failure;}
            if(!staged||level!=null&&level.getBlockEntity(worldPosition)!=this){walls=before;if(level==null)setBlockState(beforeState);else if(level.getBlockEntity(worldPosition)==this&&level.getBlockState(worldPosition)!=beforeState)level.setBlock(worldPosition,beforeState,net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);return false;}
        }
        NativeWallLayouts.invalidate(this);
        if(stateChanged){publishWallAppearance();setChanged();requestModelDataUpdate();NativeFluidWorld.queue(this);if(level!=null)level.markAndNotifyBlock(worldPosition,level.getChunkAt(worldPosition),beforeState,nextState,net.minecraft.world.level.block.Block.UPDATE_ALL,512);}
        else publishShapeChange();return true;
    }
    /** Derived topology is not saved: no dirty flag or NBT packet is sent here. Equal snapshots do nothing. */
    public boolean publishGeometry(NativeWallGeometry next,long revision){
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Wall geometry requires owning server thread");
        Objects.requireNonNull(next);if(next.equals(geometry))return false;
        geometry=next;partition=next::side;renderBounds=next.selectionBounds().move(worldPosition);topologyRevision=revision;geometryGeneration++;publishWallAppearance();NativeFluidWorld.queue(this);return true;
    }
    @Override public void refreshFluidGeometry(){NativeWallTopology.refresh(this);super.refreshFluidGeometry();}
    @Override protected long fluidGeometryKey(){return geometryGeneration;}
    @Override protected List<AABB> fluidSolidBoxes(BlockState state,long key){return geometry.fluidBoxes();}
    @Override protected NativeFluidVolume.Side fluidSidePartition(){return partition;}
    @Override public NativeFluidRegions fluidSurfaceRegions(){return geometry.fluidRegions(porthole);}
    @Override public boolean acceptsPlacementFluid(int side){return side>=0&&side<2;}
    @Override public Direction[] fluidPlacementFaces(){return geometry.placementFaces();}
    @Override public int containedFluidSide(double x,double y,double z){return geometry.side(x,y,z);}
    private static int frameLight(int choice){if(choice<0)return 0;var frame=ModernFinishCatalog.option(choice).frame();return frame==null?0:Math.max(0,Math.min(5,frame.light()));}
    @Override public int intrinsicContainedLight(){return Math.max(porthole||frames.bare?0:frameLight(frames.front),Math.max(frameLight(frames.interior),frameLight(frames.exteriorChoice())));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        NativeWallLayouts.invalidate(this);
        playback=PanelPlayback.read(tag);
        glass=PanelGlassSettings.read(new ModernHousingTags(tag),NativeWallEntity::panelMaterial);
        walls=WallSettings.read(new ModernHousingTags(tag));frames=FrameSettings.read(new ModernHousingTags(tag),NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice);
        super.loadAdditional(tag,registries);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);var data=new ModernHousingTags(tag);WallSettings.write(data,walls.fullWidth,walls.halfHeight,walls.fill,walls.join,walls.joinPortholes,walls.portholeShape);frames.write(data);glass.write(data);playback.write(tag);
    }
    @Override public void setRemoved(){NativeWallLayouts.invalidate(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeWallLayouts.invalidate(this);super.onChunkUnloaded();}
    @Override public ModelData getModelData(){return super.getModelData().derive().with(WALL_APPEARANCE,wallAppearance).build();}
}

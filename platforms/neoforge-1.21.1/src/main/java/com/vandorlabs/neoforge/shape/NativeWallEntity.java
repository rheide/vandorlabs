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
    public record WallAppearance(Appearance base,WallSettings walls,FrameSettings frames,NativeFlatWallGeometry.Geometry geometry){}
    public static final ModelProperty<WallAppearance> WALL_APPEARANCE=new ModelProperty<>();
    NativeWallLayouts.Group textureGroup;
    private final boolean porthole;
    private WallSettings walls;
    private FrameSettings frames=defaultFrames();
    private NativeFlatWallGeometry.Geometry geometry;
    private NativeFluidVolume.Side partition;
    private long geometryGeneration,topologyRevision;
    private volatile WallAppearance wallAppearance;
    public NativeWallEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean porthole){
        super(type,pos,state,false,new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1));this.porthole=porthole;walls=new WallSettings(false,false,0,0,porthole,0);
        geometry=initialGeometry(state);partition=geometry::side;publishWallAppearance();
    }
    private static FrameSettings defaultFrames(){return new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false);}
    private static NativeFlatWallGeometry.Geometry initialGeometry(BlockState state){
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
    public final WallAppearance wallAppearance(){return wallAppearance;}
    public final long topologyRevision(){return topologyRevision;}
    private void publishWallAppearance(){wallAppearance=new WallAppearance(appearance(),walls,frames,geometry);}
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
    /** Derived topology is not saved: no dirty flag or NBT packet is sent here. Equal snapshots do nothing. */
    public boolean publishGeometry(NativeFlatWallGeometry.Geometry next,long revision){
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Wall geometry requires owning server thread");
        Objects.requireNonNull(next);if(next.equals(geometry))return false;
        geometry=next;partition=next::side;topologyRevision=revision;geometryGeneration++;publishWallAppearance();NativeFluidWorld.queue(this);return true;
    }
    @Override public void refreshFluidGeometry(){NativeWallTopology.refresh(this);super.refreshFluidGeometry();}
    @Override protected long fluidGeometryKey(){return geometryGeneration;}
    @Override protected List<AABB> fluidSolidBoxes(BlockState state,long key){return geometry.boxes();}
    @Override protected NativeFluidVolume.Side fluidSidePartition(){return partition;}
    @Override public NativeFluidRegions fluidSurfaceRegions(){return NativeFluidRegions.flat(geometry.facing(),geometry.depth(),porthole);}
    @Override public boolean acceptsPlacementFluid(int side){return side>=0&&side<2;}
    @Override public Direction[] fluidPlacementFaces(){return new Direction[]{geometry.facing(),geometry.facing().getOpposite()};}
    @Override public int containedFluidSide(double x,double y,double z){return geometry.side(x,y,z);}
    private static int frameLight(int choice){if(choice<0)return 0;var frame=ModernFinishCatalog.option(choice).frame();return frame==null?0:Math.max(0,Math.min(5,frame.light()));}
    @Override public int intrinsicContainedLight(){return Math.max(porthole||frames.bare?0:frameLight(frames.front),Math.max(frameLight(frames.interior),frameLight(frames.exteriorChoice())));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        NativeWallLayouts.invalidate(this);
        walls=WallSettings.read(new ModernHousingTags(tag));frames=FrameSettings.read(new ModernHousingTags(tag),NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice);
        super.loadAdditional(tag,registries);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);var data=new ModernHousingTags(tag);WallSettings.write(data,walls.fullWidth,walls.halfHeight,walls.fill,walls.join,walls.joinPortholes,walls.portholeShape);frames.write(data);
    }
    @Override public void setRemoved(){NativeWallLayouts.invalidate(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeWallLayouts.invalidate(this);super.onChunkUnloaded();}
    @Override public ModelData getModelData(){return super.getModelData().derive().with(WALL_APPEARANCE,wallAppearance).build();}
}

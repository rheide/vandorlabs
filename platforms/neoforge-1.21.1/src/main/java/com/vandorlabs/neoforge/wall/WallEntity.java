package com.vandorlabs.neoforge.wall;

import com.vandorlabs.fluids.FluidVolume;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.fluid.ContainedFluidHost;
import com.vandorlabs.neoforge.fluid.NativeContainedFluids;
import com.vandorlabs.neoforge.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.HousingSideTexture;
import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.render.FlatPanelPlacement;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.WallDialog;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Shared wall appearance over existing housing persistence and contained-fluid lifecycle adapters. */
public final class WallEntity extends ProgrammableBlockEntity implements ContainedFluidHost {
    public static final ModelProperty<WallDialog.State> WALL_SETTINGS=new ModelProperty<>();
    private int side=-1;
    private WallAppearance appearance;
    private final NativeContainedFluids fluids=new NativeContainedFluids(this);
    private volatile FluidVolume volume;
    private volatile WallRenderSnapshot wallRenderSnapshot=WallRenderSnapshot.EMPTY;
    private String geometryKey="";
    private long geometryRevision;
    private long receivedSettingsRevision;
    /** Local receipt evidence; optimistic UI updates never advance this value. */
    public long receivedSettingsRevision(){return receivedSettingsRevision;}
    public WallEntity(BlockPos pos,BlockState state){super(WallRegistration.ENTITY.get(),pos,state);var fresh=WallDialog.fresh(profile());appearance=fresh.appearance;apply(new ProgrammableBlockDialog.State(fresh.main,fresh.layout,false,null,-1));refreshLocal();}
    public WallRenderSnapshot wallRenderSnapshot(){return wallRenderSnapshot;}
    public boolean publishWallRenderSnapshot(WallRenderSnapshot next){if(wallRenderSnapshot.equals(next))return false;wallRenderSnapshot=java.util.Objects.requireNonNull(next);requestModelDataUpdate();return true;}
    public WallDialog.Profile profile(){return ((WallBlock)getBlockState().getBlock()).profile();}
    public WallDialog.State wallSettings(){return new WallDialog.State(settings().mainTexture,side,settings().layout,-1,appearance);}
    public boolean applyWall(WallDialog.State next){
        if(next==null||!ModernFinishCatalog.validChoice(next.main)||!TextureLayoutMode.valid(next.layout))return false;
        if(profile().porthole()){
            if(next.side!=-1&&!ModernFinishCatalog.validChoice(next.side)&&!(next.side==-3&&side==-3))return false;
            var a=next.appearance;
            if(a==null||!WallAppearance.validShape(a.shape)||a.shade<0||a.shade>=com.vandorlabs.tiles.GlassTextures.IDS.length||a.tint<0||a.tint>2
                    ||!WallAppearance.validMaterial(a.material,ModernFinishCatalog::validChoice)||!TextureLayoutMode.valid(a.textureLayout()))return false;
            side=next.side<0?next.side:ModernFinishCatalog.storedChoice(next.side);appearance=a;
        }
        var old=settings();apply(new ProgrammableBlockDialog.State(next.main,next.layout,old.facesEnabled,old.faces(),-1));
        fluids.invalidateGeometry();return true;
    }
    private void refreshLocal(){if(appearance!=null)refreshGeometry(new WallGeometry.Pose(getBlockState().getValue(WallBlock.FACING),getBlockState().getValue(WallBlock.DEPTH),profile()==WallDialog.Profile.FULL_PORTHOLE,null));}
    @Override public void refreshFluidGeometry(){refreshGeometry(level==null?new WallGeometry.Pose(getBlockState().getValue(WallBlock.FACING),getBlockState().getValue(WallBlock.DEPTH),profile()==WallDialog.Profile.FULL_PORTHOLE,null):WallGeometry.live(getBlockState(),level,worldPosition));}
    private void refreshGeometry(WallGeometry.Pose pose){
        String key=WallGeometry.signature(pose);if(key.equals(geometryKey))return;
        var solids=new ArrayList<FluidVolume.Box>();for(var box:WallGeometry.boxes(pose))solids.add(new FluidVolume.Box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        volume=FluidVolume.build(solids,null,(x,y,z)->FlatPanelPlacement.side(pose.facing().get3DDataValue(),FlatPanelPlacement.near(pose.depth()),x,z));
        geometryKey=key;geometryRevision++;
    }
    @Override public void setBlockState(BlockState state){super.setBlockState(state);refreshLocal();if(fluids!=null)fluids.invalidateGeometry();}
    @Override public void onLoad(){super.onLoad();refreshLocal();fluids.onLoad();}
    @Override public void setRemoved(){fluids.removed();super.setRemoved();}
    @Override public void onChunkUnloaded(){fluids.onChunkUnloaded();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);var data=new NbtPrimitiveData(tag);
        side=HousingSideTexture.read(data,ModernFinishCatalog::storedChoice);
        appearance=WallAppearance.read(data,choice->WallAppearance.clampMaterial(choice,ModernFinishCatalog::validChoice));
        fluids.read(tag,registries);refreshLocal();
        if(level!=null&&level.isClientSide)receivedSettingsRevision++;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("SideTexture",side);appearance.write(new NbtPrimitiveData(tag));fluids.write(tag);}
    @Override public ModelData getModelData(){return super.getModelData().derive().with(WALL_SETTINGS,wallSettings()).with(WallRenderSnapshot.DATA,wallRenderSnapshot).with(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA,fluids.renderSnapshot()).build();}
    @Override public BlockEntity fluidOwner(){return this;}
    @Override public NativeContainedFluids containedFluids(){return fluids;}
    @Override public FluidVolume fluidVolumeSnapshot(){return volume;}
    @Override public long fluidGeometryRevision(){return geometryRevision;}
    @Override public int containedFluidSide(double x,double y,double z){return FlatPanelPlacement.side(getBlockState().getValue(WallBlock.FACING).get3DDataValue(),FlatPanelPlacement.near(getBlockState().getValue(WallBlock.DEPTH)),x,z);}
    @Override public Direction[] fluidPlacementFaces(){Direction facing=getBlockState().getValue(WallBlock.FACING);return new Direction[]{facing,facing.getOpposite()};}
}

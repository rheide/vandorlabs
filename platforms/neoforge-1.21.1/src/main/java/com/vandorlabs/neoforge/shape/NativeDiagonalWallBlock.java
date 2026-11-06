package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.render.DiagonalPanelContact;
import com.vandorlabs.persistence.WallSettings;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Diagonal body adapter; appearance and topology share the ordinary panel host. */
public final class NativeDiagonalWallBlock extends NativePanelBlock {
    public static final BooleanProperty INVERTED=BlockStateProperties.INVERTED;
    private final NativePanelKind kind;
    private final MapCodec<NativeDiagonalWallBlock> codec;
    public NativeDiagonalWallBlock(Properties properties,boolean porthole){this(properties,porthole?NativePanelKind.PORTHOLE:NativePanelKind.WALL);}
    public NativeDiagonalWallBlock(Properties properties,NativePanelKind kind){super(properties);this.kind=java.util.Objects.requireNonNull(kind);codec=simpleCodec(p->new NativeDiagonalWallBlock(p,kind));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(INVERTED,false));}
    public NativePanelKind panelKind(){return kind;}
    public boolean isPorthole(){return kind==NativePanelKind.PORTHOLE;}
    public boolean isGlass(){return kind==NativePanelKind.GLASS;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,INVERTED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,kind);}
    private boolean continuation(BlockState other){return other.is(this)||kind==NativePanelKind.WALL&&other.getBlock() instanceof NativeDiagonalWallBlock block&&block.kind==NativePanelKind.WALL;}
    static boolean configured(ItemStack stack){
        if(stack.has(DataComponents.BLOCK_ENTITY_DATA))return true;
        var custom=stack.get(DataComponents.CUSTOM_DATA);return custom!=null&&custom.copyTag().contains("BlockEntityTag",10);
    }
    public int placementMode(BlockPlaceContext context){
        BlockPos support=context.getClickedPos().relative(context.getClickedFace().getOpposite());BlockState previous=context.getLevel().getBlockState(support);
        ItemStack stack=context.getItemInHand();var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if(data!=null){var tag=data.copyTag();return WallSettings.geometry(tag.getBoolean(WallSettings.HALF),tag.getBoolean(WallSettings.FULL));}
        var custom=stack.get(DataComponents.CUSTOM_DATA);
        if(custom!=null){var tag=custom.copyTag();if(tag.contains("BlockEntityTag",10)){tag=tag.getCompound("BlockEntityTag");return WallSettings.geometry(tag.getBoolean(WallSettings.HALF),tag.getBoolean(WallSettings.FULL));}}
        return continuation(previous)?NativeDiagonalWallTopology.mode(context.getLevel(),support):0;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos pos=context.getClickedPos(),support=pos.relative(context.getClickedFace().getOpposite());var level=context.getLevel();BlockState previous=level.getBlockState(support);int mode=placementMode(context);
        if(continuation(previous)&&NativeDiagonalWallTopology.mode(level,support)==mode){
            Direction old=previous.getValue(FACING);for(Direction facing:new Direction[]{old,old.getOpposite()})for(boolean inverted:new boolean[]{false,true}){
                if(DiagonalPanelContact.samePlane(mode,previous.getValue(INVERTED),inverted,facing!=old,pos.getY()-support.getY(),NativeDiagonalWallTopology.axis(pos.subtract(support),old.getOpposite())))return defaultBlockState().setValue(FACING,facing).setValue(INVERTED,inverted);
            }
            if(context.getClickedFace().getAxis()==Direction.Axis.Y)return defaultBlockState().setValue(FACING,old).setValue(INVERTED,previous.getValue(INVERTED));
        }
        Direction facing=context.getHorizontalDirection().getOpposite();Vec3 hit=context.getClickLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        boolean inverted=context.getClickedFace()==Direction.DOWN||context.getClickedFace().getAxis().isHorizontal()&&hit.y>.5;
        if(mode!=2){float normal=(float)(facing.getAxis()==Direction.Axis.X?hit.x:hit.z);if(normal<1F/3F)facing=facing.getAxis()==Direction.Axis.X?Direction.WEST:Direction.NORTH;else if(normal>2F/3F)facing=facing.getAxis()==Direction.Axis.X?Direction.EAST:Direction.SOUTH;}
        return defaultBlockState().setValue(FACING,facing).setValue(INVERTED,inverted);
    }
    public static NativeDiagonalWallGeometry.Geometry geometry(BlockState state,BlockGetter level,BlockPos pos){
        if(level.getBlockEntity(pos) instanceof NativeWallEntity host&&host.wallAppearance().geometry() instanceof NativeDiagonalWallGeometry.Geometry diagonal&&diagonal.facing()==state.getValue(FACING)&&diagonal.pose().inverted()==state.getValue(INVERTED))return diagonal;
        return NativeDiagonalWallGeometry.build(NativeDiagonalWallTopology.initial(state,NativeDiagonalWallTopology.mode(level,pos)));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return NativeFlatWallGeometry.collision(geometry(state,level,pos));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
}

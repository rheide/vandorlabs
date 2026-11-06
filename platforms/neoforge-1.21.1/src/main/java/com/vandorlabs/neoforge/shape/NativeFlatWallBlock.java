package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.CubeConfiguration;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Current wall/porthole panel body. Joined geometry is published locally after loaded-neighbour checks. */
public final class NativeFlatWallBlock extends NativePanelBlock {
    public static final BooleanProperty INVERTED=BlockStateProperties.INVERTED;
    public static final IntegerProperty DEPTH=IntegerProperty.create("depth",0,2);
    private final boolean porthole,fullDepth;
    private final MapCodec<NativeFlatWallBlock> codec;
    public NativeFlatWallBlock(Properties properties,boolean porthole){this(properties,porthole,false);}
    public NativeFlatWallBlock(Properties properties,boolean porthole,boolean fullDepth){super(properties);if(fullDepth&&!porthole)throw new IllegalArgumentException("Full depth is a porthole profile");this.porthole=porthole;this.fullDepth=fullDepth;codec=simpleCodec(p->new NativeFlatWallBlock(p,porthole,fullDepth));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(DEPTH,0).setValue(INVERTED,false));}
    public boolean isFullDepth(){return fullDepth;}
    public boolean isPorthole(){return porthole;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,DEPTH,INVERTED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,porthole);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos support=context.getClickedPos().relative(context.getClickedFace().getOpposite());var clicked=context.getLevel().getBlockState(support);
        Direction facing=clicked.is(this)?clicked.getValue(FACING):context.getHorizontalDirection().getOpposite();
        if(fullDepth)return defaultBlockState().setValue(FACING,facing);
        Vec3 hit=com.vandorlabs.neoforge.placement.NativePlacementHit.hit(context);
        return defaultBlockState().setValue(FACING,facing).setValue(DEPTH,PanelDepthPolicy.fromHit(facing.get3DDataValue(),(float)hit.x,(float)hit.z));
    }
    public static NativeFlatWallGeometry.Geometry geometry(BlockState state,BlockGetter level,BlockPos pos){
        if(level.getBlockEntity(pos) instanceof NativeWallEntity host){var g=host.wallAppearance().geometry();if(g instanceof NativeFlatWallGeometry.Geometry flat&&flat.facing()==state.getValue(FACING)&&flat.depth()==state.getValue(DEPTH)&&flat.fullDepth()==((NativeFlatWallBlock)state.getBlock()).fullDepth)return flat;}
        return ((NativeFlatWallBlock)state.getBlock()).fullDepth?NativeFlatWallGeometry.full(state.getValue(FACING),state.getValue(DEPTH)):NativeFlatWallGeometry.straight(state.getValue(FACING),state.getValue(DEPTH));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return NativeFlatWallGeometry.collision(geometry(state,level,pos));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return true;}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return NativeFluidWorld.light(level,pos);}
}

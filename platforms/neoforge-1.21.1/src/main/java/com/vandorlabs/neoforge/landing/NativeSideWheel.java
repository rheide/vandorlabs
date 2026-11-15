package com.vandorlabs.neoforge.landing;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.gear.core.LandingWheelLayout;
import com.vandorlabs.neoforge.gear.NativeGearGeometry;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Source side-wheel footprint: only the root draws, every reserved cell collides and mines. */
public final class NativeSideWheel extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
    private final int size;
    private volatile VoxelShape[][] shapes;
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    public NativeSideWheel(int size,Properties properties){super(properties);this.size=size;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
    @Override protected MapCodec<? extends Block> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART);}
    public int size(){return size;}
    public BlockPos root(BlockPos pos,BlockState state){int f=state.getValue(FACING).get2DDataValue(),p=state.getValue(PART);return pos.offset(-LandingWheelLayout.x(f,p),-LandingWheelLayout.y(p),-LandingWheelLayout.z(f,p));}
    public BlockPos cell(BlockPos root,Direction face,int part){int f=face.get2DDataValue();return root.offset(LandingWheelLayout.x(f,part),LandingWheelLayout.y(part),LandingWheelLayout.z(f,part));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction side=context.getClickedFace();if(!side.getAxis().isHorizontal())return null;var level=context.getLevel();var at=context.getClickedPos();var support=at.relative(side.getOpposite());if(!level.hasChunkAt(support)||!(NativeSlabSupport.slab(level.getBlockState(support))))return null;for(int n=0;n<(size==2?4:1);n++){var p=cell(at,side,n);if(!level.hasChunkAt(p)||!level.getBlockState(p).canBeReplaced(context))return null;}return defaultBlockState().setValue(FACING,side);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide&&size==2)for(int n=1;n<4;n++)level.setBlock(cell(pos,state.getValue(FACING),n),state.setValue(PART,n),Block.UPDATE_CLIENTS);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!level.isClientSide&&state.getBlock()!=next.getBlock()&&size==2&&!REMOVING.get()){var root=root(pos,state);REMOVING.set(true);try{for(int n=0;n<4;n++){var p=cell(root,state.getValue(FACING),n);if(!p.equals(pos)&&level.hasChunkAt(p)){var other=level.getBlockState(p);if(other.is(this)&&other.getValue(FACING)==state.getValue(FACING)&&other.getValue(PART)==n)level.removeBlock(p,false);}}}finally{REMOVING.remove();}}super.onRemove(state,level,pos,next,moving);}
    private VoxelShape shape(BlockState state){if(shapes==null){var built=new VoxelShape[4][4];var boxes=NativeGearGeometry.model("vandorlabs:block/wheel_"+(size==0?"small":size==1?"medium":"large"),false);for(int f=0;f<4;f++)for(int p=0;p<4;p++){VoxelShape shape=Shapes.empty();for(var box:boxes){var b=box.move(-(p&1),-(p>>1),0);double x=Math.max(0,b.minX),xx=Math.min(1,b.maxX),y=Math.max(0,b.minY),yy=Math.min(1,b.maxY);if(x>=xx||y>=yy)continue;var clipped=new AABB(x,y,b.minZ,xx,yy,b.maxZ);var turned=switch(f){case 0->new AABB(1-clipped.maxX,clipped.minY,1-clipped.maxZ,1-clipped.minX,clipped.maxY,1-clipped.minZ);case 1->new AABB(clipped.minZ,clipped.minY,1-clipped.maxX,clipped.maxZ,clipped.maxY,1-clipped.minX);case 3->new AABB(1-clipped.maxZ,clipped.minY,clipped.minX,1-clipped.minZ,clipped.maxY,clipped.maxX);default->clipped;};shape=Shapes.or(shape,Shapes.create(turned));}built[f][p]=shape;}shapes=built;}return shapes[state.getValue(FACING).get2DDataValue()][state.getValue(PART)];}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state);}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}

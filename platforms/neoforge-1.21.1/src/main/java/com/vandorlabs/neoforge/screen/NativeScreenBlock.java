package com.vandorlabs.neoforge.screen;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.NativeVoxelShapes;
import com.vandorlabs.neoforge.placement.NativePlacementHit;
import com.vandorlabs.neoforge.shape.NativeProgrammableSlab;
import com.vandorlabs.screen.*;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.function.Supplier;
/** Unregistered state/geometry foundation. Configured item ownership and player menus bind separately. */
public class NativeScreenBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=BlockStateProperties.FACING,HORIZONTAL_FACING=BlockStateProperties.HORIZONTAL_FACING;
 public static final BooleanProperty KEYBOARD=BooleanProperty.create("keyboard"),UPPER=BooleanProperty.create("upper"),INVERTED=BooleanProperty.create("inverted");
 private final ScreenFamily family;private final Supplier<? extends BlockEntityType<?>> entities;
 private record Key(ScreenFamily family,int meta,boolean small,int wall,boolean ceiling,int ceilingPosition){}
 private record ShapesFor(NativeScreenGeometry geometry,VoxelShape outline,VoxelShape collision,int retained){}
 private static final int BUDGET=2*1024*1024;
 private static final com.google.common.cache.Cache<Key,ShapesFor> SHAPES=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((Key key,ShapesFor shapes)->shapes.retained).build();
 protected NativeScreenBlock(ScreenFamily family,Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(properties(family,properties));this.family=java.util.Objects.requireNonNull(family);this.entities=java.util.Objects.requireNonNull(entities);BlockState state=stateDefinition.any().setValue(facingProperty(),Direction.NORTH);if(family.keyboard)state=state.setValue(KEYBOARD,false);if(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)state=state.setValue(UPPER,false);if(family==ScreenFamily.DIAGONAL)state=state.setValue(INVERTED,false);registerDefaultState(state);}
 private static Properties properties(ScreenFamily family,Properties properties){properties.dynamicShape();return family==ScreenFamily.SCREEN?properties:properties.noOcclusion();}
 public static NativeScreenBlock create(ScreenFamily family,Properties properties,Supplier<? extends BlockEntityType<?>> entities){return switch(family){case INPUT,HALF_INPUT->new Input(family,properties,entities);case DIAGONAL->new Diagonal(properties,entities);case HALF_DIAGONAL->new HalfDiagonal(properties,entities);default->new NativeScreenBlock(family,properties,entities);};}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
 public final ScreenFamily family(){return family;}
 protected DirectionProperty facingProperty(){return FACING;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
 public final ScreenFamily.State screenState(BlockState state){return new ScreenFamily.State(state.getValue(facingProperty()).get3DDataValue(),family.keyboard&&state.getValue(KEYBOARD),(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)&&state.getValue(UPPER),family==ScreenFamily.DIAGONAL&&state.getValue(INVERTED));}
 public final BlockState state(ScreenFamily.State value){BlockState state=defaultBlockState().setValue(facingProperty(),Direction.from3DDataValue(value.facing));if(family.keyboard)state=state.setValue(KEYBOARD,value.keyboard);if(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)state=state.setValue(UPPER,value.upper);if(family==ScreenFamily.DIAGONAL)state=state.setValue(INVERTED,value.inverted);return state;}
 public final ScreenPlacement.Result placement(BlockPlaceContext context){BlockState support=context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));Vec3 hit=NativePlacementHit.hit(context);return ScreenPlacement.place(family,context.getClickedFace().get3DDataValue(),context.getHorizontalDirection().get3DDataValue(),context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown(),(float)hit.x,(float)hit.y,(float)hit.z,support.is(this)?support.getValue(facingProperty()).get3DDataValue():-1,slabHalf(support));}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return state(placement(context).state);}
 private static int slabHalf(BlockState state){if(state.getBlock() instanceof NativeProgrammableSlab)return state.getValue(NativeProgrammableSlab.HALF)==Half.TOP?1:0;if(state.getBlock() instanceof SlabBlock){SlabType type=state.getValue(SlabBlock.TYPE);return type==SlabType.DOUBLE?-1:type==SlabType.TOP?1:0;}return -1;}
 private ShapesFor shapes(BlockState state,BlockGetter level,BlockPos pos){NativeScreenSettings settings=level.getBlockEntity(pos) instanceof NativeScreenEntity owner&&owner.family()==family?owner.screenSettings():null;Key key=new Key(family,family.encode(screenState(state)),settings!=null&&settings.scalar().smallInput,settings==null?-1:settings.scalar().wallPosition,settings!=null&&settings.ceilingMounted(),settings==null?-1:settings.ceilingPosition());ShapesFor cached=SHAPES.getIfPresent(key);if(cached!=null)return cached;NativeScreenGeometry geometry=NativeScreenGeometry.of(family,family.decode(key.meta),key.small,key.wall,key.ceiling,key.ceilingPosition);VoxelShape outline=net.minecraft.world.phys.shapes.Shapes.create(geometry.bounds()),collision=NativeVoxelShapes.union(geometry.collision(),BUDGET);ShapesFor result=new ShapesFor(geometry,outline,collision,NativeVoxelShapes.retainedCost(outline,1)+NativeVoxelShapes.retainedCost(collision,geometry.collision().size())+256+geometry.collision().size()*64);SHAPES.put(key,result);return result;}
 public final NativeScreenGeometry geometry(BlockState state,BlockGetter level,BlockPos pos){return shapes(state,level,pos).geometry;}
 public final com.vandorlabs.render.LegacyBoxTrace.Hit trace(BlockState state,BlockGetter level,BlockPos pos,Vec3 localStart,Vec3 localEnd){return NativeScreenPicking.trace(family,screenState(state),geometry(state,level,pos),localStart,localEnd);}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes(state,level,pos).outline;}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes(state,level,pos).collision;}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){if(level.getBlockEntity(pos) instanceof NativeScreenEntity owner&&owner.family()==family){NativeScreenPowerService.queryLight(owner);return owner.visual().appearance().light;}return family.keyboard||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.HALF_DIAGONAL?0:11;}
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeScreenEntity owner)owner.localInputChanged();}
 @Override protected boolean isSignalSource(BlockState state){return true;}
 @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction face){return level.getBlockEntity(pos) instanceof NativeScreenEntity owner?owner.localOutput():0;}
 @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction face){return getSignal(state,level,pos,face);}
 @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(facingProperty(),rotation.rotate(state.getValue(facingProperty())));}
 @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(facingProperty())));}
 private static final class Input extends NativeScreenBlock {Input(ScreenFamily family,Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(family,properties,entities);}@Override protected DirectionProperty facingProperty(){return HORIZONTAL_FACING;}@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HORIZONTAL_FACING,KEYBOARD,UPPER);}}
 private static final class Diagonal extends NativeScreenBlock {Diagonal(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(ScreenFamily.DIAGONAL,properties,entities);}@Override protected DirectionProperty facingProperty(){return HORIZONTAL_FACING;}@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HORIZONTAL_FACING,INVERTED);}}
 private static final class HalfDiagonal extends NativeScreenBlock {HalfDiagonal(Properties properties,Supplier<? extends BlockEntityType<?>> entities){super(ScreenFamily.HALF_DIAGONAL,properties,entities);}@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,UPPER);}}
}

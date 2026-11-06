package com.vandorlabs.neoforge.door;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/** Unregistered Large geometry/fluid adapter. Rendering and control/contents services remain activation prerequisites. */
public abstract class NativeLargeDoorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING=NativeProgrammableDoorBlock.FACING;public static final BooleanProperty OPEN=NativeProgrammableDoorBlock.OPEN,POWERED=NativeProgrammableDoorBlock.POWERED,PAIRED=NativeProgrammableDoorBlock.PAIRED;
    public static final EnumProperty<DoubleBlockHalf> HALF=NativeProgrammableDoorBlock.HALF;public static final EnumProperty<DoorHingeSide> HINGE=NativeProgrammableDoorBlock.HINGE;
    private final Supplier<BlockEntityType<NativeLargeDoorEntity>> entities;private final Supplier<Item> canonical;
    protected NativeLargeDoorBlock(Properties properties,Supplier<BlockEntityType<NativeLargeDoorEntity>> entities,Supplier<Item> canonical){super(properties.noOcclusion().dynamicShape().isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));this.entities=entities;this.canonical=canonical;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(POWERED,false).setValue(PAIRED,false).setValue(HALF,DoubleBlockHalf.LOWER).setValue(HINGE,DoorHingeSide.LEFT));}
    protected VoxelShape largeShape(Level level,BlockPos pos,boolean collision){if(!(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity part))return collision?Shapes.empty():Shapes.block();var p=part.largeVisual();if(p==null||p.geometry()==null)return collision?Shapes.empty():Shapes.block();var shape=p.geometry().part(p.column(),p.row());return collision?shape.collisionShape():shape.outlineShape();}
    protected abstract void dropOwnedContents(Level level,BlockPos broken,NativeLargeDoorEntity root);
    protected void publishPart(Level level,BlockPos pos,BlockState state){level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);level.updateNeighborsAt(pos,this);}
    protected boolean stagePart(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restorePart(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,POWERED,PAIRED,HALF,HINGE);}
    public static BlockState fromLegacyMetadata(NativeLargeDoorBlock block,int meta){return block.defaultBlockState().setValue(FACING,Direction.from2DDataValue(meta&3)).setValue(OPEN,(meta&4)!=0).setValue(POWERED,(meta&8)!=0);}
    public static int legacyMetadata(BlockState state){return state.getValue(FACING).get2DDataValue()|(state.getValue(OPEN)?4:0)|(state.getValue(POWERED)?8:0);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeLargeDoorEntity(entities.get(),pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter getter,BlockPos pos,CollisionContext context){return getter instanceof Level level?largeShape(level,pos,false):Shapes.empty();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter getter,BlockPos pos,CollisionContext context){return getter instanceof Level level?largeShape(level,pos,true):Shapes.empty();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,BlockPos from,boolean moving){com.vandorlabs.neoforge.fluid.NativeFluidWorld.queueAt(level,pos);NativeLargeDoorLifecycle.neighbor(level,pos,this);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock()))NativeLargeDoorLifecycle.removed(level,pos,state,this);super.onRemove(state,level,pos,next,moving);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){return List.of(new ItemStack(canonical.get()));}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader reader,BlockPos pos,Player player){ItemStack result=new ItemStack(canonical.get());if(reader instanceof Level level){var root=NativeLargeDoorLifecycle.root(level,pos);if(root!=null){var tag=new net.minecraft.nbt.CompoundTag();tag.put("SpaceDoorSettings",root.itemSettings());result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}}return result;}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
    @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    @Override protected float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos){return 1;}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.light(level,pos);}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState fluid){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return com.vandorlabs.neoforge.fluid.NativeFluidWorld.pickupBlock(level,pos);}

}

package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.ArmResourceIndicator;

import com.mojang.serialization.MapCodec;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Source component cells remain native; the controller supplies animated render, ray and collision geometry. */
public class NativeGearBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ASSEMBLED=BooleanProperty.create("assembled");
    public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
    private final NativeGearDefinition definition;
    private final Supplier<BlockEntityType<NativeGearEntity>> entities;
    public NativeGearBlock(NativeGearDefinition definition,Properties properties,Supplier<BlockEntityType<NativeGearEntity>> entities){super(properties);this.definition=definition;this.entities=entities;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ASSEMBLED,false).setValue(PART,0));}
    public NativeGearDefinition definition(){return definition;}
    public BlockEntityType<NativeGearEntity> entityType(){return entities.get();}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ASSEMBLED,PART);}
    @Override protected boolean triggerEvent(BlockState state,Level level,BlockPos pos,int id,int value){
        var mode=ArmResourceIndicator.eventMode(value);
        if(id!=ArmResourceIndicator.BLOCK_EVENT||mode==ArmResource.DISABLED)return super.triggerEvent(state,level,pos,id,value);
        if(level.isClientSide&&level.getBlockEntity(pos) instanceof NativeGearEntity tile)tile.resourcePulse(mode);
        return true;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return entities.get().create(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world,BlockState state,BlockEntityType<T> type){return world.isClientSide?null:createTickerHelper(type,entities.get(),NativeGearEntity::tick);}
    public BlockState legacyState(int metadata){return defaultBlockState().setValue(FACING,Direction.from2DDataValue(metadata&3)).setValue(ASSEMBLED,definition.size==1&&(metadata&4)!=0).setValue(PART,definition.size==2?(metadata>>2)&3:0);}
    public int legacyMeta(BlockState state){return state.getValue(FACING).get2DDataValue()|(definition.size==2?state.getValue(PART)<<2:state.getValue(ASSEMBLED)?4:0);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){if(definition.foot()&&NativeGearComponents.placementFacing()!=null)return defaultBlockState().setValue(FACING,NativeGearComponents.placementFacing());Direction facing=context.getHorizontalDirection().getOpposite();BlockPos support=context.getClickedPos().relative(context.getClickedFace().getOpposite());var neighbor=NativeGearFormation.at(context.getLevel(),support);if(neighbor!=null&&(definition.foot()&&neighbor.definition()==definition||definition.size==2&&neighbor.definition().size==2))facing=neighbor.getBlockState().getValue(FACING);return defaultBlockState().setValue(FACING,facing);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    private static VoxelShape loose(BlockState state,NativeGearEntity tile){var definition=((NativeGearBlock)state.getBlock()).definition;if(definition==NativeGearDefinition.LARGE_WHEEL){var frame=tile==null?NativeGearComponents.placementFrame():tile.componentFrame();int part=state.getValue(PART);var offset=NativeGearComponents.offset(frame,state.getValue(FACING),part&1,part>>1);var wheel=NativeGearGeometry.yaw(NativeGearGeometry.model("wheel_large",false).stream().reduce(AABB::minmax).orElseThrow(),Math.floorMod(state.getValue(FACING).get2DDataValue()+2,4));return Shapes.create(NativeGearOrientation.box(frame,wheel).move(-offset.getX(),-offset.getY(),-offset.getZ()));}AABB box=switch(definition.kind){case PLATE->new AABB(0,0,0,1,3/16D,1);case SKI->new AABB(0,0,0,1,5/16D,1);case WHEEL->new AABB(.15,0,.15,.85,.75,.85);default->new AABB(3/16D,0,3/16D,13/16D,1,13/16D);};return Shapes.create(definition.foot()?NativeGearOrientation.box(tile==null?NativeGearComponents.placementFrame():tile.mount(),box):box);}
    private static VoxelShape shape(BlockState state,BlockGetter world,BlockPos pos,boolean collision){if(!(world.getBlockEntity(pos) instanceof NativeGearEntity tile))return loose(state,null);if(NativeGearTargets.excluded(tile))return Shapes.empty();var root=tile.controllerEntity();if(root==null||root.assembly()==null||root.inert())return loose(state,tile);return NativeGearShapeCache.shape(root,pos,collision);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state,world,pos,false);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return shape(state,world,pos,true);}
    @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
    @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter world,BlockPos pos){return true;}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,BlockHitResult hit){if(!player.getMainHandItem().isEmpty())return InteractionResult.PASS;if(world.isClientSide)return InteractionResult.SUCCESS;if(!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,world,pos,hit.getDirection()))return InteractionResult.FAIL;var member=NativeGearFormation.at(world,pos);if(member==null)return InteractionResult.FAIL;var root=member.controllerEntity();if(root==null){member.requestRebuild();player.displayClientMessage(net.minecraft.network.chat.Component.literal(member.error()),true);return InteractionResult.CONSUME;}if(player instanceof ServerPlayer actor)root.authorize(actor);return root.command(!root.target())?InteractionResult.CONSUME:InteractionResult.FAIL;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand!=InteractionHand.MAIN_HAND)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeGearBindings.open(player,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override protected void neighborChanged(BlockState state,Level world,BlockPos pos,Block block,BlockPos from,boolean moving){var tile=NativeGearFormation.at(world,pos);if(tile!=null)tile.requestRebuild();}
    @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(!NativeGearComponents.removing()&&state.getBlock()!=next.getBlock()&&!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeGearEntity member){var root=member.controllerEntity();if(root!=null&&!NativeGearJournal.ownsMutation(root,pos))root.release();if(definition==NativeGearDefinition.LARGE_WHEEL&&(root==null||!NativeGearJournal.ownsMutation(root,pos))){var origin=NativeGearComponents.root(pos,state,member.componentFrame());var cells=NativeGearComponents.cells(origin,member.componentFrame(),state.getValue(FACING));NativeGearComponents.withoutBreak(()->{for(var cell:cells)if(!cell.equals(pos)){var part=NativeGearFormation.at(world,cell);if(part!=null&&part.definition()==definition&&part.componentFrame()==member.componentFrame()&&NativeGearComponents.root(world,cell).equals(origin))world.removeBlock(cell,false);}});}}super.onRemove(state,world,pos,next,moving);}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader world,BlockPos pos,Player player){return NativeGearItem.pick(asItem(),world.getBlockEntity(pos) instanceof NativeGearEntity tile?tile:null);}
    @Override protected List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder context){var tile=context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);return List.of(NativeGearItem.pick(asItem(),tile instanceof NativeGearEntity owner?owner:null));}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}

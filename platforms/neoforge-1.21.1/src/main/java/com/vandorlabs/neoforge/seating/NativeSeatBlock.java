package com.vandorlabs.neoforge.seating;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.persistence.ChairSettings;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/** Two-cell stationary furniture. Only the lower cell owns choices; upper tiles are loot proxies. */
public class NativeSeatBlock extends HorizontalDirectionalBlock implements EntityBlock,com.vandorlabs.neoforge.NativeAuthoredBlockRay {
    public static final BooleanProperty UPPER=BooleanProperty.create("upper");
    private static final VoxelShape[][] CHAIR_TOP=chairShapes();
    private static final VoxelShape[][] CONNECTED_TOP=connectedShapes();
    private static final VoxelShape[] PILOT_SHAPES=pilotShapes();
    private static VoxelShape[][] chairShapes(){var result=new VoxelShape[5][3];for(int style=0;style<5;style++)for(int height=0;height<3;height++)result[style][height]=Shapes.box(0,0,0,1,ChairSettings.back(style,height)-1,1);return result;}
    private static VoxelShape[][] connectedShapes(){var result=new VoxelShape[2][3];for(int kind=0;kind<2;kind++)for(int height=0;height<3;height++)result[kind][height]=Shapes.box(0,0,0,1,(kind==0?1.5:1.25)+ChairSettings.heightOffsetPixels(height,true)/16D-1,1);return result;}
    private static VoxelShape[] pilotShapes(){var result=new VoxelShape[64];for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int height=0;height<4;height++)for(int canopy=0;canopy<2;canopy++)for(int upper=0;upper<2;upper++){double top=upper==1?.5+height/8D:canopy==1?1.5+height/8D:Math.min(1,.75+height/8D);result[height*16+face.get2DDataValue()*4+canopy*2+upper]=rotateBox(-.125,0,0,1.125,top,1.125,face);}return result;}
    public final NativeSeatKind kind;
    public NativeSeatBlock(NativeSeatKind kind,BlockBehaviour.Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(UPPER,false));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(properties->new NativeSeatBlock(kind,properties));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,UPPER);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativeChairEntity(pos,state);}
    public static BlockPos lower(BlockState state,BlockPos pos){return state.getBlock() instanceof NativeAdvancedChairBlock?NativeAdvancedChairBlock.root(state,pos):state.getValue(UPPER)?pos.below():pos;}
    public static boolean proxy(BlockState state){return state.getValue(UPPER)||state.getBlock() instanceof NativeAdvancedChairBlock assembly&&assembly.partIndex(state)!=0;}
    public static boolean loaded(BlockGetter world,BlockPos pos){if(world instanceof Level level)return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);return !(world instanceof LevelReader reader)||reader.hasChunkAt(pos);}
    public static NativeChairEntity rawOwner(BlockGetter world,BlockPos pos){if(!loaded(world,pos))return null;var state=world.getBlockState(pos);if(!(state.getBlock() instanceof NativeSeatBlock))return null;var at=lower(state,pos);return loaded(world,at)&&world.getBlockEntity(at) instanceof NativeChairEntity owner&&owner.getBlockState().is(state.getBlock())&&!proxy(owner.getBlockState())&&owner.getBlockState().getValue(FACING)==state.getValue(FACING)?owner:null;}
    public static NativeChairEntity owner(BlockGetter world,BlockPos pos){var owner=rawOwner(world,pos);return owner!=null&&!owner.inert()?owner:null;}
    public static boolean canopy(BlockState state){return state.getBlock() instanceof com.vandorlabs.neoforge.canopy.NativeCanopyBlock block&&(block.kind().equals("regular_canopy_glass")||block.kind().equals("canopy")||block.kind().equals("long_canopy"));}
    public boolean complete(BlockGetter world,BlockPos lower){if(!loaded(world,lower.above()))return false;var state=world.getBlockState(lower.above());return state.is(this)&&state.getValue(UPPER)&&state.getValue(FACING)==world.getBlockState(lower).getValue(FACING)||kind==NativeSeatKind.PILOT&&canopy(state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var level=context.getLevel();var pos=context.getClickedPos();if(pos.getY()>=level.getMaxBuildHeight()-1||!loaded(level,pos.above())||!level.getBlockState(pos.above()).canBeReplaced(context)&&!(kind==NativeSeatKind.PILOT&&canopy(level.getBlockState(pos.above()))))return null;return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity actor,ItemStack stack){super.setPlacedBy(level,pos,state,actor,stack);if(!level.isClientSide){if(!complete(level,pos))level.setBlock(pos.above(),state.setValue(UPPER,true),3);if(level.getBlockEntity(pos) instanceof NativeChairEntity owner)owner.placed();}}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){var owner=owner(world,pos);int height=owner==null?1:owner.settings().height;
        if(kind==NativeSeatKind.PILOT){int h=owner==null?0:owner.pilotHeight();boolean shared=!state.getValue(UPPER)&&loaded(world,pos.above())&&canopy(world.getBlockState(pos.above()));return PILOT_SHAPES[h*16+state.getValue(FACING).get2DDataValue()*4+(shared?2:0)+(state.getValue(UPPER)?1:0)];}
        if(!state.getValue(UPPER))return Shapes.block();return kind==NativeSeatKind.CHAIR?CHAIR_TOP[owner==null?0:owner.settings().style][height]:CONNECTED_TOP[kind==NativeSeatKind.LUXURY?0:1][height];
    }
    private static VoxelShape rotateBox(double x0,double y0,double z0,double x1,double y1,double z1,net.minecraft.core.Direction facing){int turns=facing==net.minecraft.core.Direction.EAST?1:facing==net.minecraft.core.Direction.SOUTH?2:facing==net.minecraft.core.Direction.WEST?3:0;for(int i=0;i<turns;i++){double a=x0,b=x1;x0=1-z1;x1=1-z0;z0=a;z1=b;}return Shapes.box(x0,y0,z0,x1,y1,z1);}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public VoxelShape getVisualShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){if(context instanceof EntityCollisionContext entity&&entity.getEntity()!=null&&entity.getEntity().getVehicle() instanceof NativeChairMount mount&&lower(state,pos).equals(mount.chairPos()))return Shapes.empty();return getShape(state,world,pos,context);}
    @Override public BlockHitResult traceAuthored(BlockState state,BlockGetter world,BlockPos pos,Vec3 start,Vec3 end){
        var owner=owner(world,pos);
        if(owner!=null&&owner.cockpitTouch()!=null){
            if(kind==NativeSeatKind.ADVANCED){
                var grip=NativeAdvancedChairGeometry.grip(owner,state.getValue(FACING),pos,start,end);
                if(grip!=null)return grip.hit();
            }else if(kind==NativeSeatKind.COCKPIT){
                var control=NativeCockpitGeometry.button(lower(state,pos),state.getValue(FACING),pos,start,end);
                if(control!=null)return control;
            }
            var panel=owner.cockpitTouch().pick(pos,start,end);
            if(panel!=null)return panel.hit();
        }
        if((kind.adjustablePilotHeight()||kind==NativeSeatKind.COCKPIT)&&world instanceof Level level){var lower=lower(state,pos);var players=level.players();for(int i=0;i<players.size();i++){var player=players.get(i);if(player.getVehicle() instanceof NativeChairMount mount&&lower.equals(mount.chairPos())&&start.distanceToSqr(player.getEyePosition())<1e-8)return null;}}return getShape(state,world,pos,CollisionContext.empty()).clip(start,end,pos);}
    @Override public boolean canBeReplaced(BlockState state,BlockPlaceContext context){return NativeSeatBackrests.replacing(context,context.getClickedPos());}
    @Override protected int getLightBlock(BlockState state,BlockGetter world,BlockPos pos){return 0;}
    private InteractionResult interact(BlockState state,Level world,BlockPos pos,Player actor,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;var at=lower(state,pos);if(actor.isShiftKeyDown())return actor.isCreative()&&NativeSeatBindings.open(actor,world,pos)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.PASS;if(!actor.isAlive()||actor.isSpectator()||actor.level()!=world||!loaded(world,at)||actor.distanceToSqr(at.getCenter())>64||!world.mayInteract(actor,at)||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,at))return InteractionResult.FAIL;var owner=owner(world,at);if(owner==null||!complete(world,at))return InteractionResult.FAIL;
        if(kind==NativeSeatKind.ADVANCED){
            Vec3 start=actor.getEyePosition();
            Vec3 end=start.add(actor.getViewVector(1).scale(Math.min(8,actor.blockInteractionRange())));
            var grip=NativeAdvancedChairGeometry.grip(owner,state.getValue(FACING),pos,start,end);
            if(grip!=null){
                if(world instanceof net.minecraft.server.level.ServerLevel server
                    &&com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server)
                    &&world.mayInteract(actor,pos)&&actor.mayUseItemAt(pos,grip.hit().getDirection(),actor.getMainHandItem())){
                    var first=world.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));
                    if(!first.getBlockPos().equals(pos)||first.getLocation().distanceToSqr(grip.hit().getLocation())>1e-8)
                        return InteractionResult.FAIL;
                    owner.armrest(grip.index()).cycle();
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
        if(kind==NativeSeatKind.COCKPIT){
            Vec3 start=actor.getEyePosition(),end=start.add(actor.getViewVector(1).scale(Math.min(8,actor.blockInteractionRange())));
            var control=NativeCockpitGeometry.button(at,state.getValue(FACING),pos,start,end);
            if(control!=null){
                if(!world.isClientSide){
                    var first=world.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));
                    if(first.getLocation().distanceToSqr(control.getLocation())>1e-8||!first.getBlockPos().equals(pos)
                        ||!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,control.getDirection(),actor.getMainHandItem()))
                        return InteractionResult.FAIL;
                    owner.pressLift(actor);
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
        var touch=owner.cockpitTouch();
        if(touch!=null){
            var start=actor.getEyePosition();
            var end=start.add(actor.getViewVector(1).scale(Math.min(8,actor.blockInteractionRange())));
            var panel=touch.pick(pos,start,end);
            if(panel!=null){
                if(!touch.usesRow(panel.slot(),panel.row()))
                    return NativeSeatBindings.openSurface(actor,owner,pos,panel)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.FAIL;
                if(!world.isClientSide){
                    if(!matchesAuthoredHit(world,actor,pos,panel.hit())||!world.mayInteract(actor,pos)
                        ||!actor.mayUseItemAt(pos,panel.hit().getDirection(),actor.getMainHandItem()))
                        return InteractionResult.FAIL;
                    touch.use(panel.slot(),panel.row(),panel.segment());
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
        if(world.isClientSide)return InteractionResult.SUCCESS;
        for(var seat:world.getEntitiesOfClass(NativeChairMount.class,new AABB(at).inflate(2)))if(at.equals(seat.chairPos())){if(seat.getPassengers().isEmpty())actor.startRiding(seat,true);return InteractionResult.CONSUME;}
        var seat=new NativeChairMount(NativeSeatBindings.MOUNT,world);seat.updatePose(owner);if(world.addFreshEntity(seat)){if(!actor.startRiding(seat,true))seat.discard();}return InteractionResult.CONSUME;
    }
    static boolean matchesAuthoredHit(Level world,Player actor,BlockPos pos,BlockHitResult panel){
        var start=actor.getEyePosition();
        var end=start.add(actor.getViewVector(1).scale(Math.min(8,actor.blockInteractionRange())));
        var first=world.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));
        return first.getBlockPos().equals(pos)&&first.getLocation().distanceToSqr(panel.getLocation())<=1e-8;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){return interact(state,world,pos,actor,InteractionHand.MAIN_HAND);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player actor,InteractionHand hand,BlockHitResult hit){if(kind==NativeSeatKind.PILOT&&stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof com.vandorlabs.neoforge.canopy.NativeCanopyBlock canopy&&NativeSeatBackrests.supports(canopy.kind()))return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;if(stack.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return NativeSeatBindings.open(actor,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;return switch(interact(state,world,pos,actor,hand)){case SUCCESS,CONSUME,CONSUME_PARTIAL->ItemInteractionResult.SUCCESS;case FAIL->ItemInteractionResult.FAIL;default->ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};}
    @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player){if(!world.isClientSide&&world.getBlockEntity(pos) instanceof NativeChairEntity clicked){var owner=rawOwner(world,pos);if(owner!=null)clicked.prepareRemoval(player.isCreative()?ItemStack.EMPTY:configured(owner));}return super.playerWillDestroy(world,pos,state,player);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder context){var captured=context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(captured instanceof NativeChairEntity tile){var prepared=tile.removalDrop();if(prepared!=null)return prepared.isEmpty()?List.of():List.of(prepared);if(!proxy(state))return List.of(configured(tile));}var origin=context.getOptionalParameter(LootContextParams.ORIGIN);var owner=origin==null?null:owner(context.getLevel(),BlockPos.containing(origin));return owner==null?List.of():List.of(configured(owner));}
    private ItemStack configured(NativeChairEntity owner){var stack=new ItemStack(this);owner.writeItem(stack);return stack;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult target,LevelReader world,BlockPos pos,Player player){var owner=rawOwner(world,pos);return owner==null?new ItemStack(this):configured(owner);}
    @Override protected void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){if(this instanceof NativeAdvancedChairBlock advanced){if(!state.is(next.getBlock()))advanced.removeAssembly(world,pos,state,moving);super.onRemove(state,world,pos,next,moving);return;}if(!state.is(next.getBlock())&&!NativeSeatItem.mutating(world,pos)&&!(state.getValue(UPPER)&&kind==NativeSeatKind.PILOT&&canopy(next))){
        var lower=lower(state,pos);if(!world.isClientSide){for(var mount:world.getEntitiesOfClass(NativeChairMount.class,new AABB(lower).inflate(2)))if(lower.equals(mount.chairPos())){mount.ejectPassengers();mount.discard();}}
        var other=state.getValue(UPPER)?pos.below():pos.above();if(!(state.getValue(UPPER)&&kind==NativeSeatKind.PILOT&&canopy(next))&&loaded(world,other)&&world.getBlockState(other).is(this))world.removeBlock(other,moving);
    }super.onRemove(state,world,pos,next,moving);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(this instanceof NativeAdvancedChairBlock){if(!NativeAdvancedChairBlock.mutating(level,pos)&&level.getBlockEntity(pos) instanceof NativeChairEntity owner)NativeSeatUpdates.queue(owner);return;}if(level.isClientSide){level.sendBlockUpdated(pos,state,state,2);return;}if(NativeSeatItem.mutating(level,pos))return;if(level.getBlockEntity(pos) instanceof NativeChairEntity owner&&!state.getValue(UPPER)){owner.refreshAppearance();if(kind==NativeSeatKind.PILOT)NativeSeatUpdates.queue(owner);}var other=state.getValue(UPPER)?pos.below():pos.above();if(!loaded(level,other))return;if(state.getValue(UPPER)&&(!level.getBlockState(other).is(this)||level.getBlockState(other).getValue(UPPER)))level.removeBlock(pos,false);}
}

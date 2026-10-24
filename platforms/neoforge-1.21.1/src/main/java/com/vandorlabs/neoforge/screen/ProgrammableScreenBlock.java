package com.vandorlabs.neoforge.screen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public class ProgrammableScreenBlock extends DirectionalBlock implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT=net.minecraft.world.level.block.state.properties.IntegerProperty.create("light",0,15);
    public static final MapCodec<ProgrammableScreenBlock> CODEC=simpleCodec(ProgrammableScreenBlock::new);
    public ProgrammableScreenBlock(BlockBehaviour.Properties properties){
        super(properties.lightLevel(state->state.getValue(LIGHT)));
        BlockState state=stateDefinition.any().setValue(facingProperty(),Direction.NORTH).setValue(LIGHT,emitsLight()?11:0);
        if(inputFlags())state=state.setValue(ScreenFamilyBlocks.KEYBOARD,false).setValue(ScreenFamilyBlocks.UPPER,false);else if(upperFlag())state=state.setValue(ScreenFamilyBlocks.UPPER,false);
        if(invertedFlag())state=state.setValue(ScreenFamilyBlocks.INVERTED,false);registerDefaultState(state);
    }
    @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    public com.vandorlabs.render.ScreenFamilyPolicy.Kind family(){return com.vandorlabs.render.ScreenFamilyPolicy.Kind.FLAT;}
    protected net.minecraft.world.level.block.state.properties.DirectionProperty facingProperty(){return FACING;}
    protected boolean inputFlags(){return false;}protected boolean upperFlag(){return false;}protected boolean invertedFlag(){return false;}
    public boolean emitsLight(){return family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.FLAT||family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.CONSOLE||family()==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL;}
    public Direction facing(BlockState state){return state.getValue(facingProperty());}
    public boolean keyboard(BlockState state){return inputFlags()&&state.getValue(ScreenFamilyBlocks.KEYBOARD);}
    public boolean upper(BlockState state){return (inputFlags()||upperFlag())&&state.getValue(ScreenFamilyBlocks.UPPER);}
    public boolean inverted(BlockState state){return invertedFlag()&&state.getValue(ScreenFamilyBlocks.INVERTED);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(facingProperty(),LIGHT);if(inputFlags())builder.add(ScreenFamilyBlocks.KEYBOARD,ScreenFamilyBlocks.UPPER);else if(upperFlag())builder.add(ScreenFamilyBlocks.UPPER);if(invertedFlag())builder.add(ScreenFamilyBlocks.INVERTED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos pos=context.getClickedPos();Direction clicked=context.getClickedFace();BlockState adjacent=context.getLevel().getBlockState(pos.relative(clicked.getOpposite()));
        int inherited=com.vandorlabs.render.ScreenFamilyPolicy.inheritedFacing(adjacent.is(this)?facing(adjacent).get3DDataValue():-1,context.getHorizontalDirection().get3DDataValue());
        Boolean supportUpper=adjacent.getBlock() instanceof SlabBlock&&adjacent.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)!=net.minecraft.world.level.block.state.properties.SlabType.DOUBLE?adjacent.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)==net.minecraft.world.level.block.state.properties.SlabType.TOP:null;
        var placement=com.vandorlabs.render.ScreenFamilyPolicy.placement(family(),clicked.get3DDataValue(),(float)(context.getClickLocation().y-pos.getY()),context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown(),inherited,supportUpper);
        BlockState state=defaultBlockState().setValue(facingProperty(),Direction.from3DDataValue(placement.facing));
        if(inputFlags())state=state.setValue(ScreenFamilyBlocks.KEYBOARD,placement.keyboard).setValue(ScreenFamilyBlocks.UPPER,placement.upper);else if(upperFlag())state=state.setValue(ScreenFamilyBlocks.UPPER,placement.upper);
        if(invertedFlag())state=state.setValue(ScreenFamilyBlocks.INVERTED,placement.inverted);return state;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(facingProperty(),rotation.rotate(facing(state)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(facing(state)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProgrammableScreenEntity(pos,state);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player){
        var stack=new net.minecraft.world.item.ItemStack(this.asItem());if(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen)screen.saveToItem(stack,level.registryAccess());return stack;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.isShiftKeyDown()&&!player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get())&&level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen&&screen.hasOverlay())return RedstoneScreenInteraction.activate(screen,player)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity editable)||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEdit(player,editable)||!(player.isCreative()&&player.isShiftKeyDown()||player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get())))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,p)->new ScreenMenu(id,inventory,pos),Component.translatable(ScreenFamilyBlocks.titleKey(family()))),buffer->{buffer.writeBlockPos(pos);buffer.writeBoolean(false);buffer.writeByte(0);});
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return ScreenShapes.shape(state,level,pos,false);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return ScreenShapes.shape(state,level,pos,true);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity player,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,player,stack);if(level.isClientSide&&level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen&&stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)){var tag=screen.saveWithoutMetadata(level.registryAccess());tag.merge(stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag());screen.loadWithComponents(tag,level.registryAccess());}}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen)screen.refreshSignal();}
}

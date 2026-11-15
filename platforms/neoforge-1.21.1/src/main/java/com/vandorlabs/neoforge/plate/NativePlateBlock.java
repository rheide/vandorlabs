package com.vandorlabs.neoforge.plate;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.persistence.PressurePlateSettings;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Vanilla support, travel and event-driven entity sensing; output mode controls channels only. */
public final class NativePlateBlock extends BasePressurePlateBlock implements EntityBlock {
    public static final MapCodec<NativePlateBlock> CODEC=simpleCodec(NativePlateBlock::new);
    public static final IntegerProperty POWER=BlockStateProperties.POWER;
    public NativePlateBlock(BlockBehaviour.Properties properties){super(properties,BlockSetType.OAK);registerDefaultState(stateDefinition.any().setValue(POWER,0));}
    @Override protected MapCodec<? extends BasePressurePlateBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(POWER);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NativePlateEntity(pos,state);}
    @Override protected int getSignalForState(BlockState state){return state.getValue(POWER);}
    @Override protected BlockState setSignalForState(BlockState state,int value){return state.setValue(POWER,PressurePlateSettings.level(value));}
    private static NativePlateEntity tile(BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativePlateEntity tile?tile:null;}
    public int releaseTicks(BlockGetter level,BlockPos pos){var tile=tile(level,pos);return PressurePlateSettings.releaseTicks(tile==null?0:tile.sensitivity());}
    @Override protected int getSignalStrength(Level level,BlockPos pos){
        var tile=tile(level,pos);int sensitivity=tile==null?0:tile.sensitivity();
        var entities=level.getEntities((Entity)null,TOUCH_AABB.move(pos));
        boolean trigger=false;if(sensitivity==0)for(var entity:entities)if(!entity.isIgnoringBlockTriggers()){trigger=true;break;}
        return PressurePlateSettings.measure(sensitivity,entities.size(),trigger);
    }
    public void resample(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos)||!level.getBlockState(pos).is(this))return;
        var before=level.getBlockState(pos);int old=before.getValue(POWER),next=getSignalStrength(level,pos);
        if(old!=next){var state=before.setValue(POWER,next);level.setBlock(pos,state,2);updateNeighbours(level,pos);level.setBlocksDirty(pos,before,state);
            if((old==0)!=(next==0)){boolean wood=tile(level,pos)==null||tile(level,pos).sensitivity()==0;
                var sound=wood?(next==0?net.minecraft.sounds.SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF:net.minecraft.sounds.SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON):(next==0?net.minecraft.sounds.SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF:net.minecraft.sounds.SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON);
                level.playSound(null,pos,sound,net.minecraft.sounds.SoundSource.BLOCKS,.3F,wood?(next==0?.7F:.8F):(next==0?.75F:.90000004F));
                level.gameEvent(null,next==0?net.minecraft.world.level.gameevent.GameEvent.BLOCK_DEACTIVATE:net.minecraft.world.level.gameevent.GameEvent.BLOCK_ACTIVATE,pos);
            }
        }
        var tile=tile(level,pos);if(tile!=null)tile.sample(next);
        if(next>0)level.scheduleTick(pos,this,releaseTicks(level,pos));
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){resample(level,pos);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){if(!level.isClientSide&&state.getValue(POWER)==0)resample((ServerLevel)level,pos);}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction face){var tile=tile(level,pos);return tile==null||tile.localRedstone()?state.getValue(POWER):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction face){return face==Direction.UP?getSignal(state,level,pos,face):0;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()&&NativePlateBindings.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    public boolean openConfiguration(Player player,Level level,BlockPos pos){return NativePlateBindings.open(player,level,pos);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,LevelReader level,BlockPos pos,Player player){var result=new net.minecraft.world.item.ItemStack(this);var tile=tile(level,pos);if(tile!=null)tile.writeItem(result);return result;}
}

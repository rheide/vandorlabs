package com.vandorlabs.neoforge.ports;

import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** One configured resource owner, installed before item consumption or neighbour publication. */
public class NativePortItem extends BlockItem {
    public NativePortItem(NativePortBlock block,Properties properties){super(block,properties);}
    public static CompoundTag snapshot(ItemStack stack){var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);return data!=null?data.copyTag():stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("BlockEntityTag").copy();}
    protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restore(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected void install(NativeResourcePortEntity owner,CompoundTag saved){owner.loadWithComponents(saved.copy(),owner.getLevel().registryAccess());if(owner.inert())throw new IllegalStateException("Invalid configured port owner");}
    @Override public InteractionResult place(BlockPlaceContext context){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var block=(NativePortBlock)getBlock();
        if(player==null||stack.isEmpty()||!context.canPlace()||!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
        if(level instanceof net.minecraft.server.level.ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
        var old=level.getBlockState(pos);if(old.hasBlockEntity()||level.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
        var state=block.getStateForPlacement(context);if(state==null||!canPlace(context,state))return InteractionResult.FAIL;
        var saved=snapshot(stack);var draft=(NativeResourcePortEntity)block.newBlockEntity(pos,state);
        if(saved.contains("id")&&!saved.getString("id").equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(draft.getType()).toString()))return InteractionResult.FAIL;
        draft.loadWithComponents(saved,level.registryAccess());if(draft.inert())return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;var placed=stack.copy();
        boolean committed=NativeStationaryPlacement.commit(level,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativeResourcePortEntity>(){
            public boolean stage(){return NativePortItem.this.stage(level,pos,state);}
            public boolean restore(){return NativePortItem.this.restore(level,pos,old);}
            public NativeResourcePortEntity owner(){return level.getBlockEntity(pos) instanceof NativeResourcePortEntity owner?owner:null;}
            public void install(NativeResourcePortEntity owner){NativePortItem.this.install(owner,saved);}
            public NativeResourcePortEntity retainRecovery(){if(level.getBlockState(pos)!=state)return null;var owner=owner();if(owner==null){owner=(NativeResourcePortEntity)block.newBlockEntity(pos,state);level.setBlockEntity(owner);}var retained=saved.copy();retained.putBoolean(NativeResourcePortEntity.RECOVERY,true);owner.loadWithComponents(retained,level.registryAccess());if(!owner.inert())throw new IllegalStateException("Port recovery became active");owner.setChanged();return level.getBlockEntity(pos)==owner?owner:null;}
            public String label(){return "Configured resource port";}
        });
        if(!committed)return InteractionResult.FAIL;
        level.markAndNotifyBlock(pos,level.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(level,pos,state,player,placed);var sound=state.getSoundType(level,pos,player);
        level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,pos);
        if(player instanceof ServerPlayer actor)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(actor,pos,placed);
        return InteractionResult.CONSUME;
    }
}

package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Inactive specialized configured Screen placement; all choices precede inventory transfer and observers. */
public class NativeScreenItem extends BlockItem {
    public NativeScreenItem(NativeScreenBlock block,Properties properties){super(block,properties);}
    public static boolean mutating(Level level,BlockPos pos){return NativeStationaryPlacement.mutating(level,pos);}
    protected boolean stage(Level level,BlockPos pos,BlockState state){return level.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restore(Level level,BlockPos pos,BlockState old){return level.setBlock(pos,old,Block.UPDATE_KNOWN_SHAPE);}
    private static NativeScreenEntity recovery(Level level,BlockPos pos,BlockState state,NativeScreenItemSettings.Prepared prepared){
        if(level.getBlockState(pos)!=state)return null;var owner=level.getBlockEntity(pos) instanceof NativeScreenEntity tile?tile:null;
        if(owner==null){owner=new NativeScreenEntity(prepared.type(),pos,state,prepared.family(),NativeScreenCatalog.INSTANCE);level.setBlockEntity(owner);if(level.getBlockEntity(pos)!=owner)return null;}
        var original=prepared.snapshot();original.putBoolean(NativeScreenItemSettings.PLACEMENT_RECOVERY,true);owner.loadWithComponents(original,prepared.registries());
        if(!owner.inert())throw new IllegalStateException("Screen recovery owner became active");owner.setChanged();return owner;
    }
    @Override public InteractionResult place(BlockPlaceContext context){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var block=(NativeScreenBlock)getBlock();
        if(player==null||stack.isEmpty()||!context.canPlace()||!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
        if(level instanceof ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
        var old=level.getBlockState(pos);if(old.hasBlockEntity()||level.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
        var placement=block.placement(context);var state=block.state(placement.state);if(!canPlace(context,state))return InteractionResult.FAIL;
        final NativeScreenItemSettings.Prepared choices;
        try{var blank=block.newBlockEntity(pos,state);if(!(blank instanceof NativeScreenEntity draft))return InteractionResult.FAIL;choices=NativeScreenItemSettings.mounting(NativeScreenItemSettings.prepare(draft.getType(),pos,state,block.family(),NativeScreenItemSettings.configuration(stack),level.registryAccess()),placement);}catch(IllegalArgumentException invalid){return InteractionResult.FAIL;}
        if(level.isClientSide)return InteractionResult.SUCCESS;var placed=stack.copy();
        boolean committed=NativeStationaryPlacement.commit(level,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativeScreenEntity>(){
            public boolean stage(){return NativeScreenItem.this.stage(level,pos,state);}public boolean restore(){return NativeScreenItem.this.restore(level,pos,old);}
            public NativeScreenEntity owner(){return level.getBlockEntity(pos) instanceof NativeScreenEntity tile?tile:null;}
            public void install(NativeScreenEntity owner){NativeScreenItemSettings.install(owner,choices);}
            public NativeScreenEntity retainRecovery(){return recovery(level,pos,state,choices);}public String label(){return "Screen";}
        });
        if(!committed)return InteractionResult.FAIL;
        level.markAndNotifyBlock(pos,level.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(level,pos,state,player,placed);
        var sound=state.getSoundType(level,pos,player);level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);level.gameEvent(player,GameEvent.BLOCK_PLACE,pos);
        if(player instanceof ServerPlayer server)net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(server,pos,placed);return InteractionResult.CONSUME;
    }
}

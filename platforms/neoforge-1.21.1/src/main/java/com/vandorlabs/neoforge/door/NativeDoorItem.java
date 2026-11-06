package com.vandorlabs.neoforge.door;

import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Ordinary two-cell placement publishes after both halves and settings exist; configured picks keep their own choices. */
public final class NativeDoorItem extends BlockItem {
    private static final class Ownership implements Runnable {
        final net.minecraft.world.entity.player.Player player;final ItemStack stack;boolean transferred;
        Ownership(net.minecraft.world.entity.player.Player player,ItemStack stack){this.player=player;this.stack=stack;}
        public void run(){if(transferred)return;transferred=true;if(!player.getAbilities().instabuild)stack.shrink(1);}
    }
    public NativeDoorItem(NativeProgrammableDoorBlock block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var block=(NativeProgrammableDoorBlock)getBlock();var level=context.getLevel();BlockPos root=context.getClickedPos();var player=context.getPlayer();var stack=context.getItemInHand();
        if(level instanceof net.minecraft.server.level.ServerLevel server&&!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))return InteractionResult.FAIL;
        if(player==null||!context.canPlace()||!level.hasChunkAt(root)||!level.hasChunkAt(root.above())||root.getY()<level.getMinBuildHeight()||root.getY()+1>=level.getMaxBuildHeight()||!NativeDoorLifecycle.supported(level,root))return InteractionResult.FAIL;
        for(BlockPos pos:new BlockPos[]{root,root.above()})if(!level.getBlockState(pos).canBeReplaced(context)||level.getBlockState(pos).hasBlockEntity()||level.getBlockEntity(pos)!=null||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack))return InteractionResult.FAIL;
        BlockState state=block.getStateForPlacement(context);if(state==null||!canPlace(context,state))return InteractionResult.FAIL;
        CompoundTag configured=null,recovery=null;var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom!=null){var tag=custom.copyTag();if(tag.contains("SpaceDoorSettings",Tag.TAG_COMPOUND))configured=tag.getCompound("SpaceDoorSettings");if(tag.contains(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY)){if(!tag.contains(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY,Tag.TAG_COMPOUND))return InteractionResult.FAIL;recovery=tag.getCompound(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY);if(!com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.validOpaqueRecovery(recovery,level.registryAccess()))return InteractionResult.FAIL;}}
        var hit=context.getClickLocation().subtract(root.getX(),root.getY(),root.getZ());int depth=PanelDepthPolicy.fromHit(state.getValue(NativeProgrammableDoorBlock.FACING).get3DDataValue(),(float)hit.x,(float)hit.z);
        // Validate malformed item choices before staging any native state.
        try{NativeDoorPlacement.choices(NativeDoorSettings.fresh(false,block.slidingModel()),configured,null,depth);}catch(IllegalArgumentException malformed){return InteractionResult.FAIL;}
        if(recovery!=null&&!recovery.isEmpty()){
            // Opaque ownership cannot be published into a block whose local factory is unavailable.
            try{for(int half=0;half<2;half++)if(!(block.newBlockEntity(half==0?root:root.above(),state.setValue(NativeProgrammableDoorBlock.HALF,half==0?net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER:net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)) instanceof NativeProgrammableDoorEntity))return InteractionResult.FAIL;}catch(RuntimeException unavailable){return InteractionResult.FAIL;}
        }
        if(level.isClientSide)return InteractionResult.SUCCESS;
        Ownership ownership=new Ownership(player,stack);boolean success=false;try{success=NativeDoorPlacement.install(context,block,state,configured,recovery,depth,ownership);}finally{
            boolean ownsRoot=level.getBlockState(root).is(block)&&level.getBlockState(root).getValue(NativeProgrammableDoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&(level.getBlockEntity(root) instanceof NativeProgrammableDoorEntity owner?owner.placementRecovery():level.getBlockState(root)==state);
            // A bare surviving state cannot own opaque inventory bytes if its factory vanished after probing.
            if(recovery!=null&&!recovery.isEmpty()&&(!(level.getBlockEntity(root) instanceof NativeProgrammableDoorEntity owner)||!owner.opaqueCredentialRecovery().equals(recovery)))ownsRoot=false;
            if(success||ownsRoot){ownership.run();if(!success&&level.getBlockEntity(root) instanceof NativeProgrammableDoorEntity retained)retained.retainPlacementRecovery();}
        }
        if(!success)return InteractionResult.FAIL;var sound=state.getSoundType(level,root,player);level.playSound(null,root,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8f);return InteractionResult.CONSUME;
    }
}

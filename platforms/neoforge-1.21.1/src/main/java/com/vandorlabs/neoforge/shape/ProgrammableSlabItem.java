package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.render.SlabMergePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.Shapes;

/** Merge into the full programmable cube while retaining the installed slab's complete snapshot. */
public final class ProgrammableSlabItem extends BlockItem {
    public ProgrammableSlabItem(Block block,Item.Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var player=context.getPlayer();if(player==null)return super.useOn(context);
        BlockPos clicked=context.getClickedPos(),adjacent=clicked.relative(context.getClickedFace());if(!level.hasChunkAt(clicked))return InteractionResult.FAIL;var state=level.getBlockState(clicked);
        var target=SlabMergePolicy.target(state.is(getBlock()),state.is(getBlock())&&state.getValue(ProgrammableSlabBlock.HALF)==Half.TOP,context.getClickedFace().get3DDataValue(),false);
        if(target==SlabMergePolicy.Target.NONE)target=SlabMergePolicy.target(false,false,context.getClickedFace().get3DDataValue(),level.hasChunkAt(adjacent)&&level.getBlockState(adjacent).is(getBlock()));
        if(target==SlabMergePolicy.Target.NONE)return super.useOn(context);BlockPos pos=target==SlabMergePolicy.Target.DIRECT?clicked:adjacent;
        var stack=context.getItemInHand();if(stack.isEmpty()||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack)||!level.isUnobstructed(null,Shapes.block().move(pos.getX(),pos.getY(),pos.getZ()))||!(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile))return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var saved=tile.saveWithoutMetadata(level.registryAccess());var full=VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,level.getBlockState(pos).getValue(ProgrammableSlabBlock.FACING));
        if(!level.setBlock(pos,full,3))return InteractionResult.FAIL;
        if(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity restored){restored.loadWithComponents(saved,level.registryAccess());restored.setChanged();}
        level.sendBlockUpdated(pos,full,full,3);var sound=full.getSoundType(level,pos,player);level.playSound(null,pos,sound.getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
        if(!player.isCreative())stack.shrink(1);return InteractionResult.SUCCESS;
    }
}

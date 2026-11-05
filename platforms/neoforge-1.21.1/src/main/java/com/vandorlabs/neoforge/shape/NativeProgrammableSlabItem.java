package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.render.SlabMergePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.Shapes;

/** Combining keeps the placed slab's complete saved settings; the incoming item's draft is ignored. */
public final class NativeProgrammableSlabItem extends BlockItem {
    public NativeProgrammableSlabItem(NativeProgrammableSlab block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        if(state.is(getBlock())&&SlabMergePolicy.directMerge(state.getValue(NativeProgrammableSlab.HALF)==Half.TOP,context.getClickedFace().get3DDataValue()))return combine(context,pos);
        var next=pos.relative(context.getClickedFace());if(level.getBlockState(next).is(getBlock()))return combine(context,next);
        return super.useOn(context);
    }
    private InteractionResult combine(UseOnContext context,BlockPos pos){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();
        if(player==null||stack.isEmpty()||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack)||!level.isUnobstructed(null,Shapes.block().move(pos.getX(),pos.getY(),pos.getZ()))||!(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile))return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var saved=tile.saveWithFullMetadata(level.registryAccess());var full=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,level.getBlockState(pos).getValue(NativeProgrammableSlab.FACING));
        if(!level.setBlock(pos,full,3))return InteractionResult.FAIL;
        if(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity result){result.loadWithComponents(saved,level.registryAccess());result.setChanged();level.sendBlockUpdated(pos,full,full,3);}
        var sound=full.getSoundType(level,pos,player);level.playSound(player,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);if(!player.isCreative())stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}

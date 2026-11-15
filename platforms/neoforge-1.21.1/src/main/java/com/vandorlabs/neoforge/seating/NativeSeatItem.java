package com.vandorlabs.neoforge.seating;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
/** Stage both reserved cells before BlockItem transfers the one configured item. */
public final class NativeSeatItem extends BlockItem {
    private record Scope(Level level,BlockPos lower){}
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    public NativeSeatItem(NativeSeatBlock block,Item.Properties properties){super(block,properties);}
    static boolean mutating(Level level,BlockPos pos){var active=ACTIVE.get();return active!=null&&active.level==level&&(active.lower.equals(pos)||active.lower.above().equals(pos));}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState next){
        var level=context.getLevel();var lower=context.getClickedPos();var upper=lower.above();var actor=context.getPlayer();var block=(NativeSeatBlock)getBlock();
        if(actor==null||!NativeSeatBlock.loaded(level,lower)||!NativeSeatBlock.loaded(level,upper)||!level.mayInteract(actor,upper)||!actor.mayUseItemAt(upper,context.getClickedFace(),context.getItemInHand())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,upper))return false;
        var oldLower=level.getBlockState(lower);var oldUpper=level.getBlockState(upper);boolean shared=block.kind==NativeSeatKind.PILOT&&NativeSeatBlock.canopy(oldUpper);
        if(oldLower.hasBlockEntity()||!shared&&oldUpper.hasBlockEntity())return false;
        var previous=ACTIVE.get();ACTIVE.set(new Scope(level,lower));boolean success=false;
        try{if(!level.setBlock(lower,next,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)||level.getBlockState(lower)!=next)return false;
            if(!shared&&(!level.setBlock(upper,next.setValue(NativeSeatBlock.UPPER,true),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)||!block.complete(level,lower)))return false;
            success=level.getBlockEntity(lower) instanceof NativeChairEntity;return success;
        }finally{
            try{if(!success){boolean restoredUpper=shared||level.setBlock(upper,oldUpper,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)||level.getBlockState(upper)==oldUpper;boolean restoredLower=level.setBlock(lower,oldLower,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)||level.getBlockState(lower)==oldLower;if(!restoredUpper||!restoredLower)throw new IllegalStateException("Seat placement rollback refused");}}
            finally{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
        }
    }
}

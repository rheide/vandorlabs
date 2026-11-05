package com.vandorlabs.neoforge.shape;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Plain items continue the clicked diagonal proportions; configured components keep their own data. */
public final class NativeDiagonalWallItem extends BlockItem {
    public NativeDiagonalWallItem(NativeDiagonalWallBlock block,Properties properties){super(block,properties);}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){
        int mode=((NativeDiagonalWallBlock)getBlock()).placementMode(context);boolean configured=NativeDiagonalWallBlock.configured(context.getItemInHand());
        if(!super.placeBlock(context,state))return false;
        if(!configured&&!context.getLevel().isClientSide&&context.getLevel().getBlockEntity(context.getClickedPos()) instanceof NativeWallEntity host)
            host.configureDiagonal(mode,0,host.wallSettings().join,state.getValue(NativePanelBlock.FACING),state.getValue(NativeDiagonalWallBlock.INVERTED));
        return true;
    }
}

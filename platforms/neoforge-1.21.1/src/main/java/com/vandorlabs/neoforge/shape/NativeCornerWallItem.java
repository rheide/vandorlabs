package com.vandorlabs.neoforge.shape;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Places a Corner Wall; a Diagonal Corner Wall keeps the size it took from the wall it was placed against. */
public final class NativeCornerWallItem extends BlockItem {
    public NativeCornerWallItem(NativeCornerWallBlock block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var block=(NativeCornerWallBlock)getBlock();var level=context.getLevel();
        int size=block.sloped()?block.placementSize(level,context.getClickedPos().relative(context.getClickedFace().getOpposite()),context.getItemInHand()):-1;
        var pos=context.getClickedPos();
        InteractionResult result=super.place(context);
        if(size>=0&&result.consumesAction()&&!level.isClientSide&&level.getBlockEntity(pos) instanceof NativeWallEntity wall&&level.getBlockState(pos).getBlock()==block)
            wall.configureCornerWall(size,level.getBlockState(pos));
        return result;
    }
}

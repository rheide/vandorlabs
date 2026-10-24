package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.ScreenFamilyPolicy;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Native item placement transports the authored underside and wall hit into shared mount state. */
final class ScreenBlockItem extends BlockItem {
    ScreenBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        InteractionResult result=super.place(context);if(!result.consumesAction())return result;
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen))return result;
        var family=screen.family();boolean input=ScreenFamilyPolicy.input(family),halfConsole=family==ScreenFamilyPolicy.Kind.HALF_CONSOLE;
        if(!input&&!halfConsole)return result;
        Direction face=context.getClickedFace();var hit=context.getClickLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        boolean ceiling=face==Direction.DOWN;int wall=-1;
        if(input&&face.getAxis().isHorizontal()&&(context.getPlayer()==null||!context.getPlayer().isShiftKeyDown())&&!screen.keyboard()){
            var support=level.getBlockState(pos.relative(face.getOpposite()));
            wall=support.getBlock() instanceof SlabBlock&&support.getValue(BlockStateProperties.SLAB_TYPE)!=SlabType.DOUBLE?support.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.TOP?2:0:ScreenFamilyPolicy.wallPosition((float)hit.y);
        }
        screen.placementMount(ceiling,ScreenFamilyPolicy.ceilingPosition(screen.facing().get3DDataValue(),(float)hit.x,(float)hit.z),wall,input);
        return result;
    }
}

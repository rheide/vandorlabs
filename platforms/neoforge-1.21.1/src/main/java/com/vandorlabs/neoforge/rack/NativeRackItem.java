package com.vandorlabs.neoforge.rack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;

/** A normal rack item owns its shell only; failed-data recovery items stay inert. */
public final class NativeRackItem extends BlockItem {
    public NativeRackItem(NativeRackBlock block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var stack=context.getItemInHand();var raw=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(stack.has(DataComponents.BLOCK_ENTITY_DATA)||raw.contains("BlockEntityTag")||com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(stack))return InteractionResult.FAIL;
        return super.place(context);
    }
}

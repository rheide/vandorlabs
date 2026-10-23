package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableStairs;
import net.minecraft.item.ItemBlock;

/** Placement follows vanilla stairs; saved dialog settings are retained by ItemBlock. */
public final class ItemProgrammableStairs extends ItemBlock {
    public ItemProgrammableStairs(BlockProgrammableStairs block){super(block);}
}

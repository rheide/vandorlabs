package com.vandorlabs.items;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;

/** Keep hidden compatibility blocks registered without advertising them to item browsers. */
public final class ItemCatalogBlock extends ItemBlock {
    public ItemCatalogBlock(Block block) { super(block); }

    @Override public CreativeTabs[] getCreativeTabs() {
        // JEI treats the default {null} array as an uncategorized item and adds it.
        return getCreativeTab() == null ? new CreativeTabs[0] : super.getCreativeTabs();
    }
}

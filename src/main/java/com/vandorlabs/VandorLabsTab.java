package com.vandorlabs;

import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class VandorLabsTab extends CreativeTabs {

    public VandorLabsTab() {
        super("vandorlabs");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack getTabIconItem() {
        return new ItemStack(ModBlocks.TRITANIUM_HULL);
    }
}

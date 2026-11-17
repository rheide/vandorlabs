package com.vandorlabs.items;

import com.vandorlabs.navigation.NavigationModuleData;
import net.minecraft.item.*;

public final class ItemNavigationDataModule extends Item {
    public ItemNavigationDataModule(){setRegistryName("vandorlabs","navigation_data_module");setUnlocalizedName("vandorlabs.navigation_data_module");setMaxStackSize(1);setCreativeTab(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB);}
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override public void addInformation(ItemStack stack,net.minecraft.world.World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
        if(NavigationModuleData.bound(stack)){lines.add("Dimension: "+NavigationModuleData.dimension(stack));lines.add("Recorded areas: "+NavigationModuleData.count(stack)+" / "+NavigationModuleData.MAX_AREAS);}else lines.add("Binds to a dimension on its first scan.");lines.add("Records linked Survey Dish and Sentinel Array coverage.");
    }
}

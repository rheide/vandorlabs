package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class ModItems {
    public static final Item PROGRAMMABLE_MATTER_INGOT = new Item()
            .setRegistryName(VandorLabs.MODID, "programmable_matter_ingot")
            .setUnlocalizedName("vandorlabs.programmable_matter_ingot")
            .setCreativeTab(VandorLabs.VANDOR_LABS_TAB);

    private ModItems() { }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(PROGRAMMABLE_MATTER_INGOT);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(PROGRAMMABLE_MATTER_INGOT, 0,
                new ModelResourceLocation(PROGRAMMABLE_MATTER_INGOT.getRegistryName(), "inventory"));
    }
}

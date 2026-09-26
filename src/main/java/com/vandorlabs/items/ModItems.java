package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
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
    public static final Item INDUSTRIAL_ALLOY_INGOT = new Item()
            .setRegistryName(VandorLabs.MODID, "industrial_alloy_ingot")
            .setUnlocalizedName("vandorlabs.industrial_alloy_ingot")
            .setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
    public static final Item CONFIGURIZER = new ItemConfigurizer();
    public static final Item DUPLIFIER = new ItemDuplifier();

    private ModItems() { }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(PROGRAMMABLE_MATTER_INGOT);
        event.getRegistry().register(INDUSTRIAL_ALLOY_INGOT);
        event.getRegistry().register(CONFIGURIZER);
        event.getRegistry().register(DUPLIFIER);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(PROGRAMMABLE_MATTER_INGOT, 0,
                new ModelResourceLocation(PROGRAMMABLE_MATTER_INGOT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(INDUSTRIAL_ALLOY_INGOT, 0,
                new ModelResourceLocation(INDUSTRIAL_ALLOY_INGOT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(CONFIGURIZER, 0,
                new ModelResourceLocation(CONFIGURIZER.getRegistryName(), "inventory"));
        ResourceLocation off = new ResourceLocation(VandorLabs.MODID, "duplifier_off");
        ResourceLocation on = new ResourceLocation(VandorLabs.MODID, "duplifier_on");
        ModelLoader.registerItemVariants(DUPLIFIER, off, on);
        ModelLoader.setCustomMeshDefinition(DUPLIFIER, stack ->
                new ModelResourceLocation(ItemDuplifier.hasCopy(stack) ? on : off,
                        "inventory"));
    }
}

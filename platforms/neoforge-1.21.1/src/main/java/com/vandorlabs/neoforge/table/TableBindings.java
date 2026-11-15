package com.vandorlabs.neoforge.table;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Canonical native table binding with an explicit false baseline override. */
@EventBusSubscriber(modid="vandorlabs", bus=EventBusSubscriber.Bus.MOD)
public final class TableBindings {
    public static TableBlock BLOCK;
    public static BlockItem ITEM;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.industrialTable","true"));}
    private static ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath("vandorlabs","industrial_table");}
    @SubscribeEvent public static void register(RegisterEvent event){
        if(!enabled())return;
        event.register(Registries.BLOCK,helper->{
            BLOCK=new TableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape());
            helper.register(id(),BLOCK);
        });
        event.register(Registries.ITEM,helper->{ITEM=new BlockItem(BLOCK,new Item.Properties());helper.register(id(),ITEM);});
    }
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){
        if(enabled()&&event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("vandorlabs","vandorlabs")))event.accept(ITEM);
    }
    private TableBindings(){}
}

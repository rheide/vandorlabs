package com.vandorlabs.neoforge.landing;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Released standalone wheel IDs, staged separately from modular wheel feet. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeLandingBindings {
    public static final Map<String,NativeSideWheel> WHEELS=new LinkedHashMap<>();
    public static final Map<String,BlockItem> ITEMS=new LinkedHashMap<>();
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.legacyLanding");}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs",name);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,helper->{String[] names={"wheel_small","wheel_medium","wheel_large"};for(int n=0;n<names.length;n++){var block=new NativeSideWheel(n,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape());WHEELS.put(names[n],block);helper.register(id(names[n]),block);}});
        event.register(Registries.ITEM,helper->{WHEELS.forEach((name,block)->{var item=new BlockItem(block,new Item.Properties());ITEMS.put(name,item);helper.register(id(name),item);});});
    }
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs")))ITEMS.values().forEach(event::accept);}
    private NativeLandingBindings(){}
}

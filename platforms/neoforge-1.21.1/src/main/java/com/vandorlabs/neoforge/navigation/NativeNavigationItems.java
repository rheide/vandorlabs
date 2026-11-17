package com.vandorlabs.neoforge.navigation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeNavigationItems {
 public static Item MODULE;
 @SubscribeEvent public static void register(RegisterEvent e){e.register(Registries.ITEM,h->{MODULE=new Item(new Item.Properties().stacksTo(1)){@Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> lines,TooltipFlag flag){lines.add(Component.literal(NativeNavigationModuleData.bound(stack)?NativeNavigationModuleData.count(stack)+" saved survey areas":"Records linked survey coverage"));}};h.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","navigation_data_module"),MODULE);NativeNavigationRuntime.install();});}
 @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("vandorlabs","vandorlabs"))&&MODULE!=null)e.accept(MODULE);}
 private NativeNavigationItems(){}
}

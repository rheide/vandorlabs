package com.vandorlabs.neoforge.copifier;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Read-only component view: icon predicates must not clone the clipboard on every draw. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCopifierClient {
    public static final ResourceLocation MODE=ResourceLocation.parse("vandorlabs:duplifier_mode");
    public static int mode(ItemStack stack){
        var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();
        return !NativeCopifierClipboard.hasCopy(root)?0:NativeCopifierOptions.connected(root)?2:1;
    }
    public static void bind(Item item){ItemProperties.register(item,MODE,(stack,level,actor,seed)->mode(stack)*.5F);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:duplifier"));
        if(item instanceof NativeCopifierItem)bind(item);
    });}
    private NativeCopifierClient(){}
}

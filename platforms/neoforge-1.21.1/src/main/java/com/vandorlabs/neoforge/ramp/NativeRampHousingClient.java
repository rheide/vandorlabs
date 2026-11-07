package com.vandorlabs.neoforge.ramp;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Authored vanilla models consume listed states; item mode reads only the released outer envelope. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeRampHousingClient {
    public static int mode(ItemStack stack){
        var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();
        if(!root.contains("RampSettings",Tag.TAG_COMPOUND))return 0;
        var settings=root.getCompound("RampSettings");int escalator=settings.getInt("EscalatorMode");
        return settings.getBoolean("MoatMode")?6:escalator!=0?Math.max(1,Math.min(2,escalator))+3
            :settings.getBoolean("Elevator")?(settings.getBoolean("ExtendSegments")?3:2)
            :settings.getBoolean("ExtendSegments")?1:0;
    }
    public static void bind(Item item){ItemProperties.register(item,ResourceLocation.parse("vandorlabs:ramp_mode"),(stack,level,actor,seed)->mode(stack)/6F);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        for(String name:new String[]{"programmable_ramp","programmable_ramp_slab"}){
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",name));
            if(item instanceof BlockItem)bind(item);
        }
    });}
    private NativeRampHousingClient(){}
}

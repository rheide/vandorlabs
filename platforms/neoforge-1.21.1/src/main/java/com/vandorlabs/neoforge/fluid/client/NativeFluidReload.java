package com.vandorlabs.neoforge.fluid.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeFluidReload {
    private NativeFluidReload(){}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener(new SimplePreparableReloadListener<Boolean>(){
        @Override protected Boolean prepare(ResourceManager resources,ProfilerFiller profiler){return Boolean.TRUE;}
        @Override protected void apply(Boolean prepared,ResourceManager resources,ProfilerFiller profiler){NativeFluidClient.reload();}
    });}
}

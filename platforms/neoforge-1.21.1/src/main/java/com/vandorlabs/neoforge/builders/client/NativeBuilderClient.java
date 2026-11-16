package com.vandorlabs.neoforge.builders.client;

import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Exactly the three builder cores. No drive, navigation or radar registration. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBuilderClient {
    private static boolean active(){return NativeBuilderBindings.enabled()||"true".equals(System.getenv("VANDOR_BUILDER_RENDER_CHECK"));}
    @SubscribeEvent public static void sprites(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event){event.register(net.minecraft.resources.ResourceLocation.parse("vandorlabs:builder_sprites"),NativeBuilderSpriteSource.TYPE);}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){if(active())for(String id:NativeBuilderModelCache.IDS)for(boolean core:new boolean[]{false,true})event.register(ModelResourceLocation.standalone(NativeBuilderModelCache.model(id,core)));}
    @SubscribeEvent public static void cores(ModelEvent.ModifyBakingResult event){if(active())event.getModels().replaceAll((key,source)->{
        for(String id:NativeBuilderModelCache.IDS)if((key.id().toString().equals("vandorlabs:"+id+"_core")&&!key.getVariant().contains("formed=true"))||key.id().equals(NativeBuilderModelCache.model(id,true)))return new NativeCoreModel(source,id,event.getTextureGetter().apply(new net.minecraft.client.resources.model.Material(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,net.minecraft.resources.ResourceLocation.parse("vandorlabs:blocks/core_flat"))));
        return source;
    });}
    @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){if(active()){NativeBuilderModelCache.bake(event.getModels());NativeBuilderRenderer.clear();}}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(NativeBuilderBindings.enabled())event.registerBlockEntityRenderer(NativeBuilderBindings.ENTITY,NativeBuilderRenderer::new);}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT) public static final class Lifecycle {
        @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())NativeBuilderRenderer.clear();}
    }
    private NativeBuilderClient(){}
}

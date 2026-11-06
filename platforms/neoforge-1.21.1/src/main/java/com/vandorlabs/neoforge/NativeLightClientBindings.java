package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.vandorlabs.neoforge.client.NativeLightBlockEntityRenderer;
import com.vandorlabs.neoforge.client.NativeLightScreen;
import com.vandorlabs.neoforge.light.NativeLightMenu;
import com.vandorlabs.neoforge.light.NativeLightRegistrations;

/** Authored inventory templates are loaded only for installed canonical Light blocks. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeLightClientBindings {
    static NativeLightBlock.Kind kind(ResourceLocation id){
        if(!id.getNamespace().equals("vandorlabs"))return null;
        return switch(id.getPath()){case "programmable_light"->NativeLightBlock.Kind.FULL;case "programmable_light_frame"->NativeLightBlock.Kind.FRAME;case "programmable_light_slab"->NativeLightBlock.Kind.SLAB;default->null;};
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){
        for(String name:new String[]{"programmable_light","programmable_light_frame","programmable_light_slab"}){
            if(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",name)))continue;
            for(String style:ProgrammableLightTextures.IDS)for(String suffix:new String[]{"_on","_off"})event.register(ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:item/configured/"+name+"_"+style+suffix)));
        }
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeLightRegistrations.ID)))event.registerBlockEntityRenderer(NativeLightRegistrations.ENTITY.get(),NativeLightBlockEntityRenderer::new);
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){
        if(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeLightRegistrations.ID)))event.<NativeLightMenu,NativeLightScreen>register(NativeLightRegistrations.MENU.get(),NativeLightScreen::new);
    }
    private NativeLightClientBindings(){}
}

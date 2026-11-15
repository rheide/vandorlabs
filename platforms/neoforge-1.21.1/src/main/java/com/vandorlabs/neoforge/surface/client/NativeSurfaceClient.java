package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.surface.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeSurfaceClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeSurfaceBindings.enabled())event.register(NativeSurfaceBindings.MENU,NativeSurfaceScreen::new);}
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){if(!NativeSurfaceBindings.enabled())return;var facings=new java.util.EnumMap<Direction,NativeSurfaceModel>(Direction.class);event.getModels().replaceAll((id,original)->{if(!id.id().equals(NativeSurfaceBindings.id("turntable_surface"))||id.getVariant().equals("inventory"))return original;Direction facing=null;for(String property:id.getVariant().split(","))if(property.startsWith("facing="))facing=Direction.byName(property.substring(7));if(facing==null)throw new IllegalStateException("Surface facing model variant");var found=facings.get(facing);if(found==null){found=new NativeSurfaceModel(original,facing);facings.put(facing,found);}return found;});}
    private NativeSurfaceClient(){}
}

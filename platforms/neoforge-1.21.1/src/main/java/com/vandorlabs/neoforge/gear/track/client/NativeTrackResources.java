package com.vandorlabs.neoforge.gear.track.client;

import com.vandorlabs.gear.track.core.TrackMeshPolicy;
import com.vandorlabs.neoforge.gear.track.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeTrackResources {
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(!NativeTracks.enabled())return;var names=new java.util.HashSet<String>();for(int n=0;n<TrackMeshPolicy.templateCount();n++){var part=TrackMeshPolicy.template(n);if(names.add(part.name))event.register(ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:block/track/"+part.name)));}}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){if(!NativeTracks.enabled())return;NativeTrackModel[] wrapper={null};event.getModels().replaceAll((id,model)->{if(!id.id().getNamespace().equals("vandorlabs")||!java.util.Set.of("compact_track","armoured_track","bogie_track").contains(id.id().getPath())||id.getVariant().equals("inventory"))return model;if(wrapper[0]==null)wrapper[0]=new NativeTrackModel(model,event.getModels());return wrapper[0];});}
    @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){if(NativeTracks.enabled())event.register(NativeTracks.MENU,NativeTrackScreen::new);}
    private NativeTrackResources(){}
}

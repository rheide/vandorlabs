package com.vandorlabs.neoforge.rack.client;

import com.vandorlabs.neoforge.rack.*;
import java.util.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Same authored parts and atlas-generation cache for terrain and active modules. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeRackClient {
    private static final NativeRackModel[] MODELS=new NativeRackModel[6];
    public static ResourceLocation model(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs","block/module_rack/"+name);}
    public static List<String> parts(){var result=new ArrayList<String>();result.add("shell");for(int slot=0;slot<4;slot++)for(String prefix:new String[]{"slot_","slot_seated_","slot_empty_","slot_autopilot_","slot_seated_autopilot_","indicator_"}){if(prefix.equals("indicator_")){result.add(prefix+slot+"_off");result.add(prefix+slot+"_on");}else result.add(prefix+slot);}return List.copyOf(result);}
    public static NativeRackModel facing(Direction facing){return MODELS[facing.get3DDataValue()];}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeRackBindings.enabled())event.register(NativeRackBindings.MENU,NativeRackScreen::new);}
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(NativeRackBindings.enabled())for(String name:parts())event.register(ModelResourceLocation.standalone(model(name)));}
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){if(!NativeRackBindings.enabled())return;var sources=Map.copyOf(event.getModels());event.getModels().replaceAll((id,source)->{
        if(!id.id().equals(ResourceLocation.parse("vandorlabs:module_rack"))||id.getVariant().equals("inventory"))return source;
        var facing=Direction.byName(id.getVariant().substring("facing=".length()));if(facing==null)throw new IllegalStateException("Rack facing variant");var model=new NativeRackModel(source,facing,sources);MODELS[facing.get3DDataValue()]=model;return model;
    });}
    @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){NativeRackAnimation.reload();NativeRackEntity.CLIENT_UPDATES=NativeRackAnimation::updated;}
    private NativeRackClient(){}
}

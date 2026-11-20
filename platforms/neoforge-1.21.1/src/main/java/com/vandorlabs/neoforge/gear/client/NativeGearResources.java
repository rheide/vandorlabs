package com.vandorlabs.neoforge.gear.client;

import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.ArmResourceIndicator;
import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** One atlas-generation publication of the authored arm parts. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeGearResources implements SpriteSource {
    private static final String[] BASE_MODELS={"empty","extender","extender_large_icon","fixed_mount","fixed_mount_compact","fixed_mount_large_icon","mount","plate_0","plate_1","plate_10","plate_11","plate_12","plate_13","plate_14","plate_15","plate_2","plate_3","plate_4","plate_5","plate_6","plate_7","plate_8","plate_9","rod","ski_0","ski_1","ski_10","ski_11","ski_12","ski_13","ski_14","ski_15","ski_2","ski_3","ski_4","ski_5","ski_6","ski_7","ski_8","ski_9","sleeve","socket","swing_arm","swing_arm_large_icon","swing_linkage","swing_linkage_lower","swing_linkage_upper","swing_linkage_upper_body","swing_linkage_elbow","swivel","wheel","wheel_bracket","wheel_connector","wheel_large","wheel_large_bracket","wheel_large_connector","wheel_large_tyre","wheel_tyre"};
    public static final String[] MODELS=models();
    private static String[] models(){
        var names=new ArrayList<String>(Arrays.asList(BASE_MODELS));
        for(var mode:ArmResource.values())if(mode!=ArmResource.DISABLED){
            names.add(ArmResourceIndicator.model(mode,false));
            names.add(ArmResourceIndicator.model(mode,true));
            for(boolean out:new boolean[]{false,true})for(boolean lit:new boolean[]{false,true})for(boolean tip:new boolean[]{false,true})names.add(ArmResourceIndicator.faceModel(mode,out,lit,tip));
        }
        if(com.vandorlabs.neoforge.gear.track.NativeTracks.enabled())
            for(int n=0;n<com.vandorlabs.gear.track.core.TrackMeshPolicy.templateCount();n++)
                names.add("track/"+com.vandorlabs.gear.track.core.TrackMeshPolicy.template(n).name);
        return names.toArray(String[]::new);
    }
    public static final MapCodec<NativeGearResources> CODEC=MapCodec.unit(NativeGearResources::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    private static volatile NativeGearMeshes bake;
    public static NativeGearMeshes bake(){return bake;}
    public static ResourceLocation model(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs",name.startsWith("track/")?"block/"+name:"block/gear/"+name);}
    @SubscribeEvent public static void types(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.parse("vandorlabs:gear_sprites"),TYPE);}
    @Override public SpriteSourceType type(){return TYPE;}
    @Override public void run(net.minecraft.server.packs.resources.ResourceManager manager,Output output){if(!NativeGearBindings.enabled())return;for(String name:new String[]{"gear/steel","gear/steel_top","gear/housing","gear/piston","gear/sole","gear/indicator","landing_gear/metal","landing_gear/strut","landing_gear/panel","landing_gear/rubber","landing_gear/hub"}){var id=ResourceLocation.parse("vandorlabs:blocks/"+name);var file=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");var resource=manager.getResource(file).orElseThrow();output.add(id,loader->loader.loadSprite(id,resource,(sprite,size,image,metadata)->new net.minecraft.client.renderer.texture.SpriteContents(sprite,size,image,metadata)));}}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){if(NativeGearBindings.enabled())for(String name:MODELS)event.register(ModelResourceLocation.standalone(model(name)));}
    @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){if(NativeGearBindings.enabled()){bake=new NativeGearMeshes(event.getModels());NativeGearVisualClock.clear();NativeGearPayloadRenderer.clear();}}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(NativeGearBindings.enabled())event.registerBlockEntityRenderer(NativeGearBindings.ENTITY,NativeGearRenderer::new);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeGearBindings.enabled())event.register(NativeGearBindings.MENU,NativeGearScreen::new);}
}

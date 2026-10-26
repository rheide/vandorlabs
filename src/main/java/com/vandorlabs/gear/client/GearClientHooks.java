package com.vandorlabs.gear.client;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GearClientHooks {
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){for(String texture:new String[]{"steel","steel_top","housing","piston","sole","indicator"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/gear/"+texture));for(String texture:new String[]{"steel","trim","dark","rubber","steel_tread","sole","status","wheel"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/track/"+texture));}
    @SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)GearVisualMotion.beginFrame();}
    @SubscribeEvent public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event){if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)ArmPayloadRenderer.cleanup();}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->{ArmPayloadRenderer.clear();GearVisualMotion.clear();});}
    @SubscribeEvent public static void bake(ModelBakeEvent event){GearModels.reload();com.vandorlabs.gear.track.client.TrackModels.reload(event);}
}

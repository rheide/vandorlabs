package com.vandorlabs.gear.client;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GearClientHooks {
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){for(String texture:new String[]{"steel","steel_top","housing","piston","sole","indicator"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/gear/"+texture));}
    @SubscribeEvent public static void bake(ModelBakeEvent event){GearModels.reload();}
}

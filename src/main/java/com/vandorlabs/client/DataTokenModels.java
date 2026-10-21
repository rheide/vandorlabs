package com.vandorlabs.client;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
/** Ordinary cuboid props and shared, subdued reader-logo atlas sprites. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class DataTokenModels {
 @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){for(String design:com.vandorlabs.items.ItemDesignToken.DESIGNS)event.getMap().registerSprite(new net.minecraft.util.ResourceLocation("vandorlabs:blocks/access_reader/logos/"+design));}
}

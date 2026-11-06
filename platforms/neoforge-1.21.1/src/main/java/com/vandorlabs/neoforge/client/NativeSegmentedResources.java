package com.vandorlabs.neoforge.client;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.atlas.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Atlas request only; never activates the Segmented Door block or controller. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeSegmentedResources implements SpriteSource {
 public static final com.mojang.serialization.MapCodec<NativeSegmentedResources> CODEC=com.mojang.serialization.MapCodec.unit(NativeSegmentedResources::new);
 public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
 @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented_sprites"),TYPE);}
 @Override public SpriteSourceType type(){return TYPE;}
 @Override public void run(net.minecraft.server.packs.resources.ResourceManager manager,Output output){
  if(!"true".equals(System.getenv("VANDOR_SEGMENTED_RENDER_CHECK"))&&!"true".equals(System.getenv("VANDOR_SEGMENTED_SCREEN_CHECK"))&&!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented_door")))return;
  var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented-artwork/panel");var path=ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/segmented-artwork/panel.png");output.add(id,loader->loader.loadSprite(id,manager.getResource(path).orElseThrow()));
 }
}

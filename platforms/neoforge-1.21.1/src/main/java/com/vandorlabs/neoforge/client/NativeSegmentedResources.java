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
 public static boolean active(){return "true".equals(System.getenv("VANDOR_SEGMENTED_GATE_RENDER_CHECK"))||"true".equals(System.getenv("VANDOR_SEGMENTED_RENDER_CHECK"))||"true".equals(System.getenv("VANDOR_SEGMENTED_SCREEN_CHECK"))||net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented_door"));}
 @SubscribeEvent public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event){if(active())for(int kind=0;kind<3;kind++)for(int mask:new int[]{0,2,3,1})event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.parse(com.vandorlabs.segmented.SegmentedGateJoin.model(kind,mask))));}
 @Override public SpriteSourceType type(){return TYPE;}
 @Override public void run(net.minecraft.server.packs.resources.ResourceManager manager,Output output){
  if(!active())return;
  var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented-artwork/panel");var path=ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/segmented-artwork/panel.png");output.add(id,loader->loader.loadSprite(id,manager.getResource(path).orElseThrow()));
  for(String name:new String[]{"amber","black","dark","lattice","red","white","zinc"})add(manager,output,"segmented_gates/"+name);
  for(String name:new String[]{"raised_black_panels","ribbed_blue_steel","spiral_aluminum_glazing"})add(manager,output,"segmented_doors/"+name);
 }
 private static void add(net.minecraft.server.packs.resources.ResourceManager manager,Output output,String name){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","blocks/"+name);var path=ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/blocks/"+name+".png");output.add(id,loader->loader.loadSprite(id,manager.getResource(path).orElseThrow()));}
}

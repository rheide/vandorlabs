package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.credentials.NativeReaderAssets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Only requests exact authored models; does not activate reader or Door block registrations. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeReaderResources implements net.minecraft.client.renderer.texture.atlas.SpriteSource {
 public static final com.mojang.serialization.MapCodec<NativeReaderResources> CODEC=com.mojang.serialization.MapCodec.unit(NativeReaderResources::new);
 public static final net.minecraft.client.renderer.texture.atlas.SpriteSourceType TYPE=new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(CODEC);
 @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","reader_sprites"),TYPE);}
 @Override public net.minecraft.client.renderer.texture.atlas.SpriteSourceType type(){return TYPE;}
 private static boolean active(){return "true".equals(System.getenv("VANDOR_READER_RESOURCE_CHECK"))||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_door"))||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","access_card_slot"));}
 @Override public void run(net.minecraft.server.packs.resources.ResourceManager manager,Output output){
  if(!active())return;for(var descriptor:NativeReaderAssets.textures()){var id=ResourceLocation.parse(descriptor.id());var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");var resource=manager.getResource(path).orElseThrow(()->new IllegalStateException("Missing authored reader sprite "+path));output.add(id,loader->loader.loadSprite(id,resource,(name,size,image,metadata)->new net.minecraft.client.renderer.texture.SpriteContents(name,size,image,metadata)));}
 }

 @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){
  if(active())for(String model:NativeReaderAssets.models())event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.parse(model)));
 }
}

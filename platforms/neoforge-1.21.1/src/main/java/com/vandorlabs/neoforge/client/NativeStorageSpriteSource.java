package com.vandorlabs.neoforge.client;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.storage.*;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
/** Matching Source sets use standard pixels and authored padding; absent Storage adds no atlas images. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeStorageSpriteSource implements SpriteSource {
 public static final MapCodec<NativeStorageSpriteSource> CODEC=MapCodec.unit(NativeStorageSpriteSource::new);public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
 @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.parse("vandorlabs:storage_sprites"),TYPE);}
 @Override public SpriteSourceType type(){return TYPE;}
 private static boolean active(){for(var block:BuiltInRegistries.BLOCK)if(block instanceof NativeStorageBlock)return true;return false;}
 @Override public void run(ResourceManager resources,Output output){if(!active())return;for(var descriptor:NativeStorageAssets.textures()){var id=ResourceLocation.parse(descriptor.id());var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");output.add(id,resources.getResource(path).orElseThrow(()->new IllegalStateException("Missing Storage artwork "+path)));}}
}

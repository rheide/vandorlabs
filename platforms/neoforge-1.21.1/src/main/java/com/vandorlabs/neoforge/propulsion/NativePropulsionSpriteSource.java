package com.vandorlabs.neoforge.propulsion;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
/** Original Source names and standard pixels; square128px engine artwork needs no padding adapter. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePropulsionSpriteSource implements SpriteSource {
 public static final MapCodec<NativePropulsionSpriteSource> CODEC=MapCodec.unit(NativePropulsionSpriteSource::new);
 public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
 @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.parse("vandorlabs:propulsion_sprites"),TYPE);}
 @Override public SpriteSourceType type(){return TYPE;}
 @Override public void run(ResourceManager resources,Output output){if(!NativePropulsionResources.active())return;for(String name:NativePropulsionAssets.textures().keySet()){var id=ResourceLocation.parse(name);var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");output.add(id,resources.getResource(path).orElseThrow(()->new IllegalStateException("Missing engine artwork "+path)));}}
}

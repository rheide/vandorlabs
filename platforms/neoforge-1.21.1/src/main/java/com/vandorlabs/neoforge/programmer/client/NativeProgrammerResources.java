package com.vandorlabs.neoforge.programmer.client;
import com.vandorlabs.neoforge.programmer.NativeProgrammerAssets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
/** Requests authored resources only; no public blocks, renderer or registry activation. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeProgrammerResources implements net.minecraft.client.renderer.texture.atlas.SpriteSource {
 public static final com.mojang.serialization.MapCodec<NativeProgrammerResources> CODEC=com.mojang.serialization.MapCodec.unit(NativeProgrammerResources::new);
 public static final net.minecraft.client.renderer.texture.atlas.SpriteSourceType TYPE=new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(CODEC);
 public NativeProgrammerResources(){}
 @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmer_sprites"),TYPE);}
 @Override public net.minecraft.client.renderer.texture.atlas.SpriteSourceType type(){return TYPE;}
 @Override public void run(net.minecraft.server.packs.resources.ResourceManager manager,Output output){if(!active())return;for(String name:NativeProgrammerAssets.textures().keySet()){var id=ResourceLocation.parse(name);var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");var resource=manager.getResource(path).orElseThrow(()->new IllegalStateException("Missing authored Programmer sprite "+path));output.add(id,loader->loader.loadSprite(id,resource,(sprite,size,image,metadata)->new net.minecraft.client.renderer.texture.SpriteContents(sprite,size,image,metadata)));}}

 private static volatile NativeProgrammerBake bake;
 public static NativeProgrammerBake currentBake(){return bake;}
 @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){if(!active())return;var mc=net.minecraft.client.Minecraft.getInstance();var prepared=new NativeProgrammerBake(event.getModels(),material->mc.getTextureAtlas(material.atlasLocation()).apply(material.texture()),mc.getResourceManager());com.vandorlabs.neoforge.NativeProgrammerBlockRenderer.clearAll();bake=prepared;}
 public static boolean active(){return Boolean.getBoolean("vandorlabs.programmerWorldCheck")||Boolean.getBoolean("vandorlabs.programmerRenderCheck")||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","data_chip_programmer"));}
 @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){if(active())for(String model:NativeProgrammerAssets.models())event.register(ModelResourceLocation.standalone(ResourceLocation.parse(model)));}
}

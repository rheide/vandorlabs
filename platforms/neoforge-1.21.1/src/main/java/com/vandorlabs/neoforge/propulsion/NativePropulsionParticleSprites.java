package com.vandorlabs.neoforge.propulsion;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.security.*;
import java.util.HexFormat;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
/** Runtime vanilla artwork projection; no redistributed Minecraft PNGs and no changed pack overrides. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePropulsionParticleSprites implements SpriteSource {
 public static final MapCodec<NativePropulsionParticleSprites> CODEC=MapCodec.unit(NativePropulsionParticleSprites::new);public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
 private static final String CRITICAL="a7c036fd1b4c73856e3a7d638b56c1cbd047a2b18c504923ad7d5ae07046d908";
 private static final String[] NAMES={"flame","critical_hit","effect_0","generic_7"};
 private static final ResourceLocation[] IDS=java.util.Arrays.stream(NAMES).map(name->ResourceLocation.parse("vandorlabs:propulsion_particle/"+name)).toArray(ResourceLocation[]::new);
 public static ResourceLocation sprite(int style){return IDS[style==0?0:style==2?2:style==3?3:1];}
 @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent e){e.register(ResourceLocation.parse("vandorlabs:propulsion_particle_sprites"),TYPE);}
 @Override public SpriteSourceType type(){return TYPE;}
 @Override public void run(ResourceManager resources,Output output){if(!NativePropulsionResources.active())return;for(String name:NAMES){var source=ResourceLocation.parse("minecraft:textures/particle/"+name+".png");var found=resources.getResource(source);if(found.isEmpty())continue;var resource=found.get();boolean canonical=false;if(name.equals("critical_hit"))try(var in=resource.open()){canonical=CRITICAL.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}catch(IOException|NoSuchAlgorithmException failure){throw new IllegalStateException("Cannot inspect plume artwork "+source,failure);}final boolean restore=canonical;var target=ResourceLocation.parse("vandorlabs:propulsion_particle/"+name);output.add(target,loader->loader.loadSprite(target,resource,(id,size,image,metadata)->{if(restore&&image.getWidth()==8&&image.getHeight()==8)image.setPixelRGBA(6,5,0xffffffff);return new SpriteContents(id,size,image,metadata);}));}}
}

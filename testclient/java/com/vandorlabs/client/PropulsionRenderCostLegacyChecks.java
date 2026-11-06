package com.vandorlabs.client;
import com.vandorlabs.blocks.BlockPropulsionLight;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraftforge.common.property.IExtendedBlockState;
/** Measured released chunk-query path; same48-quad plasma WEST/Stream/material64/brightness7 as native GPU cache guard. */
public final class PropulsionRenderCostLegacyChecks {
 private static volatile List<BakedQuad> sink;
 public static void main(String[] args)throws Exception{PropulsionArtworkLegacyOracle.initialize();Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);for(Field f:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}TextureAtlasSprite sprite=new TextureAtlasSprite("source_material64"){};sprite.setIconWidth(128);sprite.setIconHeight(128);sprite.initSprite(1024,1024,768,768,false);TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String id){return sprite;}};for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(mc,atlas);}
  IBakedModel actual=PropulsionArtworkLegacyOracle.model("vandorlabs:plasma_vent_stream",0,270),wrapper=new PropulsionSideModel(actual);BlockPropulsionLight block=new BlockPropulsionLight("source_cost",false,1);IExtendedBlockState state=((IExtendedBlockState)block.getDefaultState()).withProperty(BlockPropulsionLight.SIDE_TEXTURE,64).withProperty(BlockPropulsionLight.BRIGHTNESS,7);for(int n=0;n<6000;n++)sink=wrapper.getQuads(state,null,0);com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId(),before=meter.getThreadAllocatedBytes(id);int vertices=0;for(int n=0;n<4096;n++){sink=wrapper.getQuads(state,null,0);vertices+=sink.size()*4;}long bytes=meter.getThreadAllocatedBytes(id)-before;if(vertices!=786432||bytes<=0)throw new AssertionError("Released material allocation control "+vertices+"/"+bytes);System.out.println("Source propulsion render COST getQuads4096 vertices="+vertices+" allocated="+bytes+" actualReleasedWrapper/plasmaWESTStream/material64/brightness7");
 }
}

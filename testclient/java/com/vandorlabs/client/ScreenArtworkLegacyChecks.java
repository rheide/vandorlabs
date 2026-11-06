package com.vandorlabs.client;
import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.nbt.*;
/** Actual older framed/bare artwork migration before shared extraction. */
public final class ScreenArtworkLegacyChecks {
 public static void main(String[] args)throws Exception{net.minecraft.init.Bootstrap.register();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");java.util.Set<String> ids=new java.util.TreeSet<>(ModBlocks.DISPLAY_SCREEN_IDS);ids.addAll(java.util.Arrays.asList(TileEntityAnimatedScreenSelector.INPUT_PANELS));ids.addAll(java.util.Arrays.asList("engineering_screen","foo_framed_bar","framed_unknown","future_saved"));int cases=0;
  for(String id:ids)for(String field:new String[]{"selectedScreen","inputPanel","secondaryInputPanel"})for(int front=0;front<3;front++){NBTTagCompound tag=new NBTTagCompound();tag.setString(field,id);tag.setString("Foreign","retained");if(front==1)tag.setInteger("FrameFront",-1);if(front==2)tag.setString("FrameFront","future");boolean needed=FrameAppearance.needsLegacyScreen(tag),changed=FrameAppearance.migrateLegacyScreen(tag);hash.update((needed+":"+changed+":"+canonical(tag)).getBytes("UTF-8"));cases++;}
  for(int choice=-4;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)for(String field:new String[]{"PrimarySurfaceTexture","SecondarySurfaceTexture"}){NBTTagCompound tag=new NBTTagCompound();tag.setInteger(field,choice);boolean needed=FrameAppearance.needsLegacyScreen(tag),changed=FrameAppearance.migrateLegacyScreen(tag);hash.update((needed+":"+changed+":"+canonical(tag)).getBytes("UTF-8"));cases++;}
  StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("94527eaa6d795c6fe784c5d5c639a8506aec23b336488332d1ecf2b966f09d49"))throw new AssertionError("Actual artwork migration changed "+out);System.out.println("Actual Screen artwork migration reference PASS cases="+cases+" sha256="+out);}
 private static String canonical(NBTTagCompound tag){StringBuilder out=new StringBuilder("{");for(String key:new java.util.TreeSet<String>(tag.getKeySet()))out.append(key).append(':').append(tag.getTag(key)).append(';');return out.append('}').toString();}
}

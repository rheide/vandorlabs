package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import net.minecraft.nbt.CompoundTag;
/** Native roles and fresh factory data match actual seven source block classes. */
public final class ScreenFamilyChecks {
 private static final String[] FACING={"down","up","north","south","west","east"};
 public static void main(String[] args)throws Exception{
  var catalog=new NativeScreenSettings.Catalog(){public String defaultInput(){return "console_controls_biology_controls";}public boolean validInput(String value){return defaultInput().equals(value);}public boolean validMaterial(int value){return ModernFinishCatalog.validChoice(value);}public int material(int value){return ModernFinishCatalog.storedChoice(value);}};
  int housing=choice("door_inner"),frame=choice("frame_frames_screen_full_frame_bolted_1254");java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(ScreenFamily family:ScreenFamily.values()){CompoundTag saved=NativeScreenFactory.choices(family,catalog,housing,frame);StringBuilder text=new StringBuilder(family.id);text.append(':').append(family.stateCount()).append(':').append(family.activationSlot());for(int slot=0;slot<2;slot++)text.append(':').append(family.supportsSlot(slot)).append(',').append(family.half(slot));text.append(':').append(family.displayArtwork);for(String key:new String[]{"housingTexture","displayMode","animationSpeedIndex","framed","FrameFront","DisplayArtworkVersion"})text.append(':').append(key).append('=').append(saved.contains(key)?saved.get(key):"absent");
   for(int meta=0;meta<16;meta++){var state=family.decode(meta);text.append(':').append(meta).append('=').append(family.encode(state));text.append(",facing=").append(FACING[state.facing]);if(family==ScreenFamily.DIAGONAL)text.append(",inverted=").append(state.inverted);if(family.keyboard)text.append(",keyboard=").append(state.keyboard);if(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)text.append(",upper=").append(state.upper);cases++;}hash.update(text.toString().getBytes("UTF-8"));
  }StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("c9f2a139f52e08833a1cadd3524ebe26e3d28245a5fcbef234055331d2f962f7"))throw new AssertionError("Native screen family differs from actual "+out);System.out.println("Native Screen family parity PASS cases="+cases+" sha256="+out);
 }
 private static int choice(String id){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals(id))return i;throw new AssertionError("Missing actual artwork "+id);}
}

package com.vandorlabs.neoforge.screen;
import com.google.gson.*;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.screen.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.TagParser;
/** Released independent artwork/frame/edit behavior, including all seven joining lattices. */
public final class ScreenRenderGroupChecks {
 public static void main(String[] args)throws Exception{
  JsonObject reference;try(var raw=Files.newInputStream(Path.of(args[0]));var in=new java.util.zip.GZIPInputStream(raw)){byte[] bytes=in.readNBytes(8*1024*1024+1);need(bytes.length<=8*1024*1024,"bounded released render-group reference");reference=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}int cases=0;
  for(var value:reference.getAsJsonArray("cases")){var row=value.getAsJsonObject();var family=ScreenFamily.fromId(row.get("id").getAsString());var a=visual(family,row.get("first").getAsString());var b=visual(family,row.get("second").getAsString());for(boolean frame:new boolean[]{false,true}){boolean actual=NativeScreenRenderMatching.matches(a,b,frame);need(actual==row.get(frame?"frameMatch":"artworkMatch").getAsBoolean(),row+" matching "+frame);boolean joined=ScreenJoinGeometry.supports(family,a.settings().scalar().smallInput)&&actual;var expected=row.getAsJsonArray(frame?"frame":"artwork");need(expected.get(0).getAsInt()==0&&expected.get(1).getAsInt()==0&&expected.get(2).getAsInt()==(joined?2:1)&&expected.get(3).getAsInt()==1,"actual Source two-member rectangle "+row.get("id")+" variant="+row.get("variant"));}
   var sa=NativeScreenJoining.signature(family,a.settings(),a.frames(),a.controls(),NativeScreenCatalog.INSTANCE);var sb=NativeScreenJoining.signature(family,b.settings(),b.frames(),b.controls(),NativeScreenCatalog.INSTANCE);boolean wide=(family==ScreenFamily.SCREEN||family==ScreenFamily.INPUT)&&sa.editEligible(family)&&sa.editMatches(sb);need(wide==row.get("wide").getAsBoolean(),"Source editor aspect independent from effective artwork");cases++;
  }need(cases==2520,"all source roles/states/36 mutations");System.out.println("Native Screen render groups CPU PASS Source cases="+cases+" independent frame/artwork predicates/rectangles and authored editor aspect");
 }
 private static NativeScreenEntity.Visual visual(ScreenFamily family,String saved)throws Exception{var catalog=NativeScreenCatalog.INSTANCE;var tag=TagParser.parseTag(saved);var settings=NativeScreenSettings.read(tag,catalog);var frames=FrameSettings.read(new ModernHousingTags(tag),catalog::validFrame,catalog::validFrameSurface,catalog::validMaterial);var controls=List.of(NativeScreenControls.read(tag.getCompound("RedstonePrimary"),family.standaloneControlRows(0)),NativeScreenControls.read(tag.getCompound("RedstoneSecondary"),family.standaloneControlRows(1)));var appearance=NativeScreenAppearance.of(family,settings,frames,controls,0,catalog);return new NativeScreenEntity.Visual(family,settings,frames,PanelPlayback.DEFAULT,controls,settings.channelLevel(),0,false,1,appearance);}
 private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}

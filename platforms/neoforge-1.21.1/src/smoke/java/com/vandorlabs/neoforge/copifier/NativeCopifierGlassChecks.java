package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import com.vandorlabs.neoforge.glass.NativeGlassSettings;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Actual Source ordinary-Glass copy projection and partial setter results, independent of world commit. */
public final class NativeCopifierGlassChecks {
 public static void main(String[] args)throws Exception{byte[] raw;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=stream.readAllBytes();}if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).equals("2863325c1e85694b9ace59f1845f9bdfca322654a9220375336aa7c214aca5db"))throw new AssertionError("Wrong Source Glass reference");int captures=0,cases=0;
  for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){var row=element.getAsJsonObject();int size=row.get("stateSize").getAsInt();if(row.has("input")){var saved=TagParser.parseTag(row.get("input").getAsString());var actual=NativeCopifierGlassEdit.capture(NativeGlassSettings.read(saved).resolveLegacySize(size));var expected=TagParser.parseTag(row.get("capture").getAsString());if(!expected.equals(actual))throw new AssertionError("Glass capture meta="+row.get("meta")+" expected="+expected+" actual="+actual);captures++;}
   var current=NativeGlassSettings.read(TagParser.parseTag(row.get("target").getAsString())).resolveLegacySize(size);var values=TagParser.parseTag(row.get("values").getAsString());var before=values.copy();boolean accepted=false;var after=current;try{var plan=NativeCopifierGlassEdit.prepare(current,values);accepted=plan.applicable();if(accepted)after=plan.settings();}catch(IllegalArgumentException malformed){/* The shared Source preflight refuses before any setter. */}
   var expected=TagParser.parseTag(row.get("after").getAsString());var actual=NativeCopifierGlassEdit.capture(after);if(accepted!=row.get("applied").getAsBoolean()||!expected.equals(actual))throw new AssertionError("Glass apply case="+cases+" values="+values+" accepted="+accepted+" expected="+expected+" actual="+actual);if(!before.equals(values))throw new AssertionError("Copied values mutated");cases++;
  }
  System.out.println("Native Copifier Glass PASS Source captures="+captures+" applyCases="+cases+" typed/ignored-invalid/source-size/playback/frame semantics");
 }
}

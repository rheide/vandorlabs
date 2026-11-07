package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
/** Actual Forge ordinary Door callbacks, independent of installed world authority. */
public final class NativeCopifierDoorEditChecks {
 public static void main(String[] args)throws Exception{byte[] raw;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=in.readAllBytes();}boolean large=args.length>1&&args[1].equals("large");if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).equals(large?"3d742e47189c1d3c79e5dc348664243163e692c0bcefaadfd6daad72af8541da":"fd5ddea41ed7d2dfed3bd042a3b1f6df917367447620c531fef38cfcf6e1ec07"))throw new AssertionError("Wrong Source Door reference");int captures=0,cases=0;
  for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){var row=element.getAsJsonObject();String id=row.get("id").getAsString();if(row.has("input")){var current=NativeCopifierDoorEdit.read(id,TagParser.parseTag(row.get("input").getAsString()));var expected=TagParser.parseTag(row.get("capture").getAsString());var actual=NativeCopifierDoorEdit.capture(current);if(!expected.equals(actual))throw new AssertionError("Door capture="+captures+" id="+id+" expected="+expected+" actual="+actual);captures++;}
   var state=NativeCopifierDoorEdit.read(id,TagParser.parseTag(row.get("target").getAsString()));var values=TagParser.parseTag(row.get("values").getAsString());var original=values.copy();boolean applied=false;try{var plan=NativeCopifierDoorEdit.prepare(state,large,values);applied=plan.applicable();if(applied)state=plan.state();}catch(IllegalArgumentException malformed){/* Source preflight rejects before all applicable setters. */}var expected=TagParser.parseTag(row.get("after").getAsString());var actual=NativeCopifierDoorEdit.capture(state);if(applied!=row.get("applied").getAsBoolean()||!expected.equals(actual))throw new AssertionError("Door application="+cases+" id="+id+" values="+values+" applied="+applied+" expected="+expected+" actual="+actual);if(!values.equals(original))throw new AssertionError("Clipboard mutated");cases++;
  }System.out.println("Native Door copied policy PASS captures="+captures+" applications="+cases);
 }
}

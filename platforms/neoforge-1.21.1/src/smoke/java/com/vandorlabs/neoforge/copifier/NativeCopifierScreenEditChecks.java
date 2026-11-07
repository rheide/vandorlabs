package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.screen.ScreenFamily;
import java.nio.file.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
/** Actual Source setter captures; no installed owner, physical chip or network grid enters the plan. */
public final class NativeCopifierScreenEditChecks {
 static NativeCopifierScreenEdit.State read(ScreenFamily family,CompoundTag root){return NativeCopifierScreenEdit.read(family,root);}

 public static void main(String[] args)throws Exception{int count=0;try(var lines=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=lines.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var family=ScreenFamily.fromId(row.get("block").getAsString());var current=TagParser.parseTag(row.get("target").getAsString());var state=read(family,current);var values=TagParser.parseTag(row.get("values").getAsString());var original=current.copy();var clipboard=values.copy();var before=NativeCopifierScreenEdit.capture(state);var expectedBefore=TagParser.parseTag(row.get("before").getAsString());if(!before.equals(expectedBefore))throw new AssertionError("Before row"+count+" family="+family+" expected="+expectedBefore+" actual="+before);boolean accepted=false;CompoundTag after=before;try{var plan=NativeCopifierScreenEdit.prepare(state,values);accepted=plan.applicable();after=plan.capture();}catch(IllegalArgumentException refused){}var expected=TagParser.parseTag(row.get("after").getAsString());if(accepted!=row.get("applied").getAsBoolean()||!expected.equals(after))throw new AssertionError("After row"+count+" family="+family+" values="+values+" expected="+expected+" actual="+after+" applicable="+accepted);if(!current.equals(original)||!values.equals(clipboard))throw new AssertionError("Mutated caller");if(after.contains("ScreenChipReader")||after.contains("ChipDisplayPage"))throw new AssertionError("Physical ownership in clipboard");count++;}}if(count!=5866)throw new AssertionError("Corpus count");System.out.println("Native Screen clipboard PASS Source cases="+count+" sevenRoles/orderedRawSetters/CHIP+rows/frame+transmission/privacy/immutability; Vehicle refused");}
}

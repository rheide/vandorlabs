package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import com.vandorlabs.neoforge.propulsion.*;
import com.vandorlabs.propulsion.PropulsionDefinition;
import java.nio.file.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
/** Actual Source placed-fixture callbacks, no Vehicle/provider mocks. */
public final class NativeCopifierPropulsionEditChecks {
 public static void main(String[] args)throws Exception{int count=0;try(var lines=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=lines.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var definition=PropulsionDefinition.of(row.get("block").getAsString());int meta=row.get("meta").getAsInt();var current=TagParser.parseTag(row.get("target").getAsString());var settings=NativeRedstoneLightSettings.read(current).capture((meta&8)!=0);var values=TagParser.parseTag(row.get("values").getAsString());var original=current.copy();var clipboard=values.copy();var before=NativeCopifierPropulsionEdit.capture(definition,settings);var expectedBefore=TagParser.parseTag(row.get("before").getAsString());if(!before.equals(expectedBefore))throw new AssertionError("Before row"+count+" expected="+expectedBefore+" actual="+before);boolean accepted=false;CompoundTag after=before;var afterDefinition=definition;try{var plan=NativeCopifierPropulsionEdit.prepare(definition,(meta&7)%6,settings,values);accepted=plan.applicable();after=plan.capture();afterDefinition=plan.definition();}catch(IllegalArgumentException refused){}var expected=TagParser.parseTag(row.get("after").getAsString());if(accepted!=row.get("applied").getAsBoolean()||!expected.equals(after)||!afterDefinition.id.equals(row.get("afterBlock").getAsString()))throw new AssertionError("After row"+count+" values="+values+" expected="+expected+" actual="+after+" applicable="+accepted+" shape="+afterDefinition.id+" expectedShape="+row.get("afterBlock"));if(!current.equals(original)||!values.equals(clipboard))throw new AssertionError("Mutated caller");count++;}}if(count!=8694)throw new AssertionError("Corpus count");System.out.println("Native placed propulsion clipboard PASS Source cases="+count+" all27/typed applicability/shapeThenSettings/recapture/immutability; Vehicle excluded");}
}

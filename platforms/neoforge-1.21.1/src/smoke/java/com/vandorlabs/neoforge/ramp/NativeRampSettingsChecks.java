package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
public final class NativeRampSettingsChecks {
 public static void main(String[] args)throws Exception{int count=0;try(var input=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var original=TagParser.parseTag(row.get("input").getAsString());var before=original.copy();var settings=row.get("fresh").getAsBoolean()?NativeRampSettings.defaults():NativeRampSettings.read(original);var snapshot=row.getAsJsonObject("snapshot");int facing=snapshot.get("travelFacing").getAsInt();var expected=TagParser.parseTag(snapshot.get("capture").getAsString());var actual=settings.capture(facing);if(!expected.equals(actual))throw new AssertionError("Ramp Source capture case="+count+" expected="+expected+" actual="+actual);var item=TagParser.parseTag(snapshot.get("pick").getAsString()).getCompound("tag").getCompound("RampSettings");if(!item.equals(settings.itemChoices(facing)))throw new AssertionError("Ramp Source configured item case="+count+" expected="+item+" actual="+settings.itemChoices(facing));if(!original.equals(before))throw new AssertionError("Mutated caller");count++;}}if(count!=256)throw new AssertionError("Coverage");System.out.println("Native Ramp settings PASS Source="+count+" factory/typedSavedMigration/configuredPick/sixScanFaces/slabHalf/noOwnership");}
}

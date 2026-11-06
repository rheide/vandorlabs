package com.vandorlabs.neoforge.segmented;
import com.google.gson.*;
import java.nio.file.*;
/** Actual released allowed/rejected choices, including modern grouped artwork. */
public final class SegmentedMaterialsChecks {
 public static void main(String[] args)throws Exception{int count=0,accepted=0;for(var value:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()){var row=value.getAsJsonObject();int choice=row.get("choice").getAsInt();boolean expected=row.get("valid").getAsBoolean(),actual=NativeSegmentedDoorEntity.validTexture(choice);if(expected!=actual)throw new AssertionError("Segmented material choice="+choice+" expected="+expected+" actual="+actual+" category="+(choice>=0?com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).category():"special"));if(actual)accepted++;count++;}System.out.println("Native Segmented material acceptance PASS actual-source choices="+count+" accepted="+accepted);}
}

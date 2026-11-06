package com.vandorlabs.neoforge.client;
import com.google.gson.*;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.Direction;
public final class SegmentedTerrainEligibilityChecks {
 public static void main(String[] args)throws Exception{int profiles=0;for(String line:Files.readAllLines(Path.of(args[0]))){var row=JsonParser.parseString(line).getAsJsonObject();int broken=row.get("brokenBottom").getAsInt();var cfg=new NativeSegmentedDoorSettings(row.get("direction").getAsInt(),row.get("straight").getAsBoolean(),1,0,false,1,false,NativeSegmentedDoorSettings.DEFAULT.glass,row.get("position").getAsInt());var rows=new ArrayList<>(Collections.nCopies(32,NativeSegmentedDoorSettings.DEFAULT));rows.set(0,cfg);if(broken!=0)rows.set(31,new NativeSegmentedDoorSettings(0,false,broken==2?0:1,0,broken==1,1,false,NativeSegmentedDoorSettings.DEFAULT.glass,0));var visual=new NativeSegmentedVisual(1,32,0,Direction.NORTH,row.get("texture").getAsInt(),cfg,rows,Collections.nCopies(32,SegmentedWindowLayout.SINGLE),true,false,false,false);boolean actual=NativeSegmentedTerrainSnapshot.eligible(visual);if(actual!=row.get("eligible").getAsBoolean())throw new AssertionError("Source eligibility profile="+profiles+" "+row);if(row.get("lowerCustom").getAsBoolean()&&!actual)throw new AssertionError("Source eligibility tests root texture, every row's panel style");profiles++;}if(profiles!=1115)throw new AssertionError("Source coverage "+profiles);System.out.println("[vandorlabs][segmented-terrain-eligibility] CPU PASS actual source1115profiles/allcatalog namespaces/pose/all32rows/lowercustom scope");}
}

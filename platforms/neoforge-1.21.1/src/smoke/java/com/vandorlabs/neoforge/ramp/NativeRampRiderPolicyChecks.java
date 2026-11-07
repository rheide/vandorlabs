package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import com.vandorlabs.motion.PlatformSupportPolicy;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.phys.*;

/** Independent Source support/event/axis results; actual entity and world adapters are separate checks. */
public final class NativeRampRiderPolicyChecks {
    private static AABB box(JsonArray a){return new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
    private static boolean available(JsonObject row){int f=row.get("flags").getAsInt();return PlatformSupportPolicy.available((f&1)!=0,(f&2)!=0,(f&4)!=0,row.get("entity").getAsInt()==0&&(f&8)!=0);}
    private static void same(AABB actual,JsonObject row,int index){if(!row.has("out")){if(actual!=null)throw new AssertionError("Expected removed support "+index);return;}if(actual==null||!actual.equals(box(row.getAsJsonArray("out"))))throw new AssertionError("Source support bounds "+index);}
    private static AABB clip(AABB b,double feet){double top=PlatformSupportPolicy.supportTop(b.minY,b.maxY,feet);return Double.isNaN(top)?null:top==b.maxY?b:new AABB(b.minX,b.minY,b.minZ,b.maxX,top,b.maxZ);}
    public static void main(String[] args)throws Exception {
        int count=0,support=0,recent=0,clear=0;
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("kind").getAsString();
            if(kind.equals("support")){boolean eligible=PlatformSupportPolicy.eligible(available(row),row.get("velocity").getAsDouble());if(eligible!=row.get("eligible").getAsBoolean()||(row.get("entity").getAsInt()>=3)!=row.get("vehicle").getAsBoolean())throw new AssertionError("Source eligibility "+count);var b=box(row.getAsJsonArray("box"));boolean clips=row.get("moving").getAsBoolean()&&row.get("entity").getAsInt()<2&&eligible;same(clips?clip(b,row.get("feet").getAsDouble()):b,row,count);support++;}
            else if(kind.equals("recent")){var b=box(row.getAsJsonArray("box"));boolean valid=available(row)&&PlatformSupportPolicy.recent(row.get("dimensionMatches").getAsBoolean(),row.get("age").getAsLong(),row.get("x").getAsDouble(),row.get("z").getAsDouble(),1.25,-3.5)&&PlatformSupportPolicy.provenFloor(b.minX,b.maxX,b.minZ,b.maxZ,b.maxY,1.25,-3.5,80);same(valid?clip(b,row.get("feet").getAsDouble()):b,row,count);recent++;}
            else if(kind.equals("clear")){var body=box(row.getAsJsonArray("body"));var d=row.getAsJsonArray("delta");var delta=new Vec3(d.get(0).getAsDouble(),d.get(1).getAsDouble(),d.get(2).getAsDouble());var boxes=new ArrayList<AABB>();for(var value:row.getAsJsonArray("obstacles"))boxes.add(box(value.getAsJsonArray()));var actual=NativeRampRiders.clipped(body,delta,boxes);var wanted=row.getAsJsonArray("clipped");if(actual.x!=wanted.get(0).getAsDouble()||actual.y!=wanted.get(1).getAsDouble()||actual.z!=wanted.get(2).getAsDouble())throw new AssertionError("Source axis offsets "+count);boolean valid=Math.abs(actual.x-delta.x)<1e-6&&Math.abs(actual.y-delta.y)<1e-6&&Math.abs(actual.z-delta.z)<1e-6;if(valid!=row.get("clear").getAsBoolean())throw new AssertionError("Source clearance "+count);clear++;}
            else throw new AssertionError("Unknown Source row "+kind);count++;
        }}
        if(count!=49200||support!=18400||recent!=28800||clear!=2000)throw new AssertionError("Source coverage");
        System.out.println("Native Ramp rider policy PASS Source="+count+" support18400/recent28800/clear2000 exact bounds and offsets; entity/world transport pending");
    }
}

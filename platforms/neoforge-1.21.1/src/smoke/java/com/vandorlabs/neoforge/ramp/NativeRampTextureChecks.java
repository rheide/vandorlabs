package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.AABB;

/** Actual released per-slice origin/UV callbacks, including the deliberately retained fallback cases. */
public final class NativeRampTextureChecks {
    public static void main(String[] args)throws Exception{
        int parts=0,boxes=0,different=0;double maximum=0;
        try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var tag=TagParser.parseTag(row.get("saved").getAsString());var cell=new BlockPos(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"));int material=row.get("material").getAsInt();var origins=new ArrayList<NativeRampGeometry.Origin>();var list=tag.getList("Origins",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++){var value=list.getCompound(i);List<AABB> shapes=material==3?List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,0,1,1,.5)):List.of(new AABB(0,tag.getDouble("Low"),0,1,tag.getDouble("High"),1));origins.add(new NativeRampGeometry.Origin(new BlockPos(value.getInt("X"),value.getInt("Y"),value.getInt("Z")),shapes));}
            var spec=new NativeRampGeometry.Spec(cell,Direction.from3DDataValue(row.get("facing").getAsInt()),tag.getInt("SourceY"),tag.getInt("Row"),tag.getInt("Length"),tag.getInt("TreadPixels"),tag.getDouble("Low"),tag.getDouble("High"),tag.getInt("StartOffsetHalfSteps")/2D,tag.getInt("EndOffsetHalfSteps")/2D,tag.getBoolean("Elevator"),tag.getInt("TravelAxis"),tag.getBoolean("ExtendSegments"),tag.getInt("SpeedMode")==0,tag.getInt("RampInterpolation"),tag.getBoolean("RampVerticalLayout"),tag.getBoolean("MoatMode"),material==3,origins);
            double pose=row.get("pose").getAsDouble();var snapshot=NativeRampGeometry.snapshot(spec,pose);var expected=row.getAsJsonArray("frames");if(snapshot.pieces().size()!=expected.size())throw new AssertionError("UV fixture geometry "+parts);
            for(int i=0;i<expected.size();i++){var frame=NativeRampTextureCoordinates.frame(spec,snapshot.pieces().get(i),pose);double[] values={frame.origin().getX(),frame.origin().getY(),frame.origin().getZ(),frame.uShift(),frame.vShift(),frame.bottom(),frame.top()};var old=expected.get(i).getAsJsonArray();for(int k=0;k<values.length;k++){double reference=old.get(k).getAsDouble(),delta=Math.abs(reference-values[k]);if(delta>1e-12)throw new AssertionError("Released Ramp texture part="+parts+" piece="+i+" field="+k+" actual="+values[k]+" expected="+reference+" saved="+tag);if(Double.doubleToLongBits(reference)!=Double.doubleToLongBits(values[k]))different++;maximum=Math.max(maximum,delta);}boxes++;}parts++;
        }}
        if(parts!=9322||boxes!=15596)throw new AssertionError("Ramp texture coverage="+parts+"/"+boxes);System.out.println("Native Ramp texture coordinates PASS actualParts="+parts+" pieces="+boxes+" differentComponents="+different+" maximumDelta="+maximum+" material-origin/sideways-fallback/sideUV; emitted/GPU separate");
    }
}

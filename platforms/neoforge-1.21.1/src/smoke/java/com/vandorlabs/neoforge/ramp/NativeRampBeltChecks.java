package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.AABB;

/** Actual deployed controller reservations, per-part lane selection, shapes and source texture frames. */
public final class NativeRampBeltChecks {
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static List<AABB> shapes(int material,double low,double high){return material==3?List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,0,1,1,.5)):List.of(new AABB(0,low,0,1,high,1));}
    public static void main(String[] args)throws Exception{
        int rows=0,cells=0,parts=0,boxes=0,different=0,legacy=0;double maximum=0;
        try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();int material=row.get("material").getAsInt(),length=row.get("length").getAsInt(),pixels=row.get("pixels").getAsInt(),interpolation=row.get("interpolation").getAsInt();boolean vertical=row.get("vertical").getAsBoolean();double end=row.get("rise").getAsDouble(),distance=row.get("distance").getAsDouble(),low=material==2?.5:0,high=material==1?.5:1;var face=Direction.from3DDataValue(row.get("facing").getAsInt());var origins=new ArrayList<NativeRampGeometry.Origin>();for(var p:row.getAsJsonArray("origins"))origins.add(new NativeRampGeometry.Origin(pos(p.getAsJsonArray()),shapes(material,low,high)));var footprint=new NativeRampFootprint.Spec(new BlockPos(4,80,4),face,80,vertical?80:8*face.getStepX()+8*face.getStepZ(),length,low,high,0,end,pixels,false,0,false,false,interpolation,vertical,false,origins);var expectedCells=new HashSet<BlockPos>();for(var p:row.getAsJsonArray("footprint"))expectedCells.add(pos(p.getAsJsonArray()));var actualCells=NativeRampBeltGeometry.cells(footprint);if(!actualCells.equals(expectedCells))throw new AssertionError("Deployed belt reservation row="+rows+" expected="+expectedCells+" actual="+actualCells);cells+=actualCells.size();
            for(var element:row.getAsJsonArray("parts")){var part=element.getAsJsonObject();var tag=TagParser.parseTag(part.get("saved").getAsString());var cell=pos(part.getAsJsonArray("cell"));var own=new ArrayList<NativeRampGeometry.Origin>();for(var value:tag.getList("Origins",Tag.TAG_COMPOUND)){var p=(CompoundTag)value;own.add(new NativeRampGeometry.Origin(new BlockPos(p.getInt("X"),p.getInt("Y"),p.getInt("Z")),shapes(material,low,high)));}
                var spec=new NativeRampGeometry.Spec(cell,face,tag.getInt("SourceY"),part.get("row").getAsInt(),tag.getInt("Length"),tag.getInt("TreadPixels"),tag.getDouble("Low"),tag.getDouble("High"),tag.getInt("StartOffsetHalfSteps")/2D,tag.getInt("EndOffsetHalfSteps")/2D,false,0,false,false,tag.getInt("RampInterpolation"),vertical,false,material==3,own);var actual=NativeRampBeltGeometry.snapshot(spec,distance);var expected=part.getAsJsonArray("boxes");if(actual.geometry().pieces().size()!=expected.size())throw new AssertionError("Belt part row="+rows+" cell="+cell+" expected="+expected.size()+" actual="+actual.geometry().pieces().size());
                for(int i=0;i<expected.size();i++){var b=actual.geometry().pieces().get(i).box();var f=actual.frames().get(i);double[] values={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,f.origin().getX(),f.origin().getY(),f.origin().getZ(),f.uShift(),f.vShift(),f.bottom(),f.top()};var reference=expected.get(i).getAsJsonArray();for(int k=0;k<values.length;k++){double old=reference.get(k).getAsDouble(),delta=Math.abs(old-values[k]);if(delta>1e-12)throw new AssertionError("Released belt row="+rows+" cell="+cell+" piece="+i+" field="+k+" expected="+old+" actual="+values[k]);if(Double.doubleToLongBits(old)!=Double.doubleToLongBits(values[k]))different++;maximum=Math.max(maximum,delta);}boxes++;}parts++;
            }if(pixels==3)legacy++;rows++;
        }}
        if(rows!=1120||cells!=15720||parts!=15720||boxes!=53962||legacy!=160)throw new AssertionError("Belt coverage="+rows+"/"+cells+"/"+parts+"/"+boxes+" legacy="+legacy);System.out.println("Native Ramp belts PASS Source="+rows+" reservationCells="+cells+" partSnapshots="+parts+" boxes="+boxes+" legacyPitch3="+legacy+" differentComponents="+different+" maximumDelta="+maximum+" currentNext/lastTread/lane/wrap/material/sideUV; runtime separate");
    }
}

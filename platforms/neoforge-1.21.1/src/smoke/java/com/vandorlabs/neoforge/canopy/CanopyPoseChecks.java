package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.redstone.ChannelList;
import java.nio.file.*;

/** Exact Source double words freeze rigid poses, zero handling and float-sqrt normal precision. */
public final class CanopyPoseChecks {
    private static long checks;
    private static void point(JsonArray expected,int at,CanopyPoint p) {
        double[] actual={p.x,p.y,p.z};for(int i=0;i<3;i++){checks++;long bits=Double.doubleToRawLongBits(actual[i]);if(expected.get(at+i).getAsLong()!=bits)throw new AssertionError("Source canopy pose word at="+(at+i)+" expected="+expected.get(at+i)+" actual="+bits);}
    }
    public static void main(String[] args)throws Exception {
        int poses=0,vectors=0;
        try(var reader=Files.newBufferedReader(Path.of(args[0]))) {
            for(String line;(line=reader.readLine())!=null;) {
                var row=JsonParser.parseString(line).getAsJsonObject();
                if(row.get("kind").getAsString().equals("vector")){var raw=row.getAsJsonArray("point");CanopyPoint p=new CanopyPoint(Double.longBitsToDouble(raw.get(0).getAsLong()),Double.longBitsToDouble(raw.get(1).getAsLong()),Double.longBitsToDouble(raw.get(2).getAsLong()));point(row.getAsJsonArray("normal"),0,p.normalize());vectors++;continue;}
                int width=row.get("width").getAsInt(),mount=row.get("mount").getAsInt();
                var settings=new CanopySettingsData(16,71,0,1,mount,row.get("motion").getAsInt(),row.get("hinge").getAsInt(),1,-1,row.get("openable").getAsBoolean(),false,row.get("slide").getAsBoolean(),false,true,0,ChannelList.EMPTY);
                var transform=new CanopyTransform(row.get("id").getAsString(),width,settings,row.get("progress").getAsDouble());
                CanopyPoint[] points={CanopyPoint.ZERO,new CanopyPoint(width,width,1),new CanopyPoint(.125,.25,.96875),new CanopyPoint(-1E-6,0,0)};
                JsonArray expected=row.getAsJsonArray("vertices");int at=0;
                for(int facing:new int[]{3,4,2,5})for(int index=0;index<points.length;index++){int i=mount==1?points.length-1-index:index;point(expected,at,CanopyTransform.rotate(transform.apply(points[i],row.get("group").getAsString()),facing));at+=3;}
                poses++;
            }
        }
        if(poses!=28800||vectors!=20)throw new AssertionError("Source canopy pose coverage "+poses+"/"+vectors);
        System.out.println("[vandorlabs][canopy-pose] PASS SourcePoses="+poses+" normalization="+vectors+" exactDoubleWords="+checks);
    }
}

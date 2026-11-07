package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import java.nio.file.*;

/** Native authored physical geometry replays actual Source windows, triangle boxes and face rays. */
public final class CanopyPhysicsChecks {
    private static long checks,boxes,rays;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static CanopyPoint point(JsonArray value){return new CanopyPoint(Double.longBitsToDouble(value.get(0).getAsLong()),Double.longBitsToDouble(value.get(1).getAsLong()),Double.longBitsToDouble(value.get(2).getAsLong()));}
    private static void box(JsonArray expected,CanopyPhysics.Box actual,String context){double[] values={actual.minX,actual.minY,actual.minZ,actual.maxX,actual.maxY,actual.maxZ};for(int i=0;i<6;i++)need(expected.get(i).getAsLong()==Double.doubleToRawLongBits(values[i]),"Source physical box "+context+" axis="+i);}
    public static void main(String[] args)throws Exception {
        int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString(),context=kind+"/"+row.get("facing")+"/"+row.get("profile");int facing=row.get("facing").getAsInt(),width=row.get("width").getAsInt();var settings=NativeCanopySettings.read(CanopySettingsChecks.tags(row.getAsJsonObject("settings"))).data();boolean moving=kind.equals("canopy")||kind.equals("long_canopy")||CanopySettingsData.supports(kind)&&settings.openable;double progress=moving?row.get("progress").getAsDouble():0;
            var base=NativeCanopyModels.builtin().geometry(kind,width,0,0,row.get("centered").getAsBoolean(),settings);var physical=CanopyTransform.faces(base,kind,width,settings,progress);var result=CanopyPhysics.boxes(physical,facing,8,80,8,CanopySettingsData.supports(kind));var expected=row.getAsJsonArray("boxes");need(expected.size()==result.size(),"Source physical count "+context+" expected="+expected.size()+" actual="+result.size());for(int i=0;i<result.size();i++){box(expected.get(i).getAsJsonArray(),result.get(i),context+"/"+i);boxes++;}box(row.getAsJsonArray("selection"),CanopyPhysics.bounds(result,8,80,8),context+"/selection");
            for(var raw:row.getAsJsonArray("rays")){var ray=raw.getAsJsonObject();var actual=CanopyPhysics.trace(physical,facing,8,80,8,point(ray.getAsJsonArray("start")),point(ray.getAsJsonArray("end")));var wanted=ray.get("hit");need(wanted==null||wanted.isJsonNull()?actual==null:actual!=null,"Source ray existence "+context+" ray="+rays);if(actual!=null){var hit=wanted.getAsJsonObject();var p=hit.getAsJsonArray("point");need(p.get(0).getAsLong()==Double.doubleToRawLongBits(actual.x)&&p.get(1).getAsLong()==Double.doubleToRawLongBits(actual.y)&&p.get(2).getAsLong()==Double.doubleToRawLongBits(actual.z),"Source exact ray point "+context);need(hit.get("face").getAsInt()==actual.face,"Source exact ray face "+context);}rays++;}profiles++;
        }}need(profiles==176&&boxes==1191320&&rays==5104,"Source physical corpus coverage");System.out.println("[vandorlabs][canopy-physics] PASS SourceProfiles="+profiles+" boxes="+boxes+" rays="+rays+" exactFields="+checks);
    }
}

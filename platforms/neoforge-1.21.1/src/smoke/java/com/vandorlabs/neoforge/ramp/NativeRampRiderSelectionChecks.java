package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Frozen results from actual released private carryRiders callbacks, including all-rider preflight. */
public final class NativeRampRiderSelectionChecks {
    private static double n(JsonArray a,int i){return a.get(i).getAsDouble();}
    private static AABB box(JsonArray a,int offset){return new AABB(n(a,offset),n(a,offset+1),n(a,offset+2),n(a,offset+3),n(a,offset+4),n(a,offset+5));}
    private static void same(double actual,double expected,int row,int actor,int field){if(Double.doubleToLongBits(actual)!=Double.doubleToLongBits(expected))throw new AssertionError("Source rider selection row="+row+" actor="+actor+" field="+field+" expected="+expected+" actual="+actual);}
    public static void main(String[] args)throws Exception{
        int rows=0,moved=0,blocked=0;
        try(var input=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){for(String line;(line=input.readLine())!=null;){
            var row=JsonParser.parseString(line).getAsJsonObject();boolean moat=row.has("moat")&&row.get("moat").getAsBoolean();boolean vertical=row.has("vertical")&&row.get("vertical").getAsBoolean();var facing=Direction.valueOf(row.get("facing").getAsString());int material=row.get("material").getAsInt(),mode=row.get("mode").getAsInt()%5;
            double low=material==2?.5:0,high=material==1?.5:1;var shapes=new ArrayList<AABB>();for(var shape:row.getAsJsonArray("shapes"))shapes.add(box(shape.getAsJsonArray(),0));
            var source=new BlockPos(0,80,0);var origins=List.of(new NativeRampGeometry.Origin(source,shapes),new NativeRampGeometry.Origin(vertical||moat?source.above():source.relative(facing),shapes));
            var spec=new NativeRampFootprint.Spec(new BlockPos(8,80,8),facing,80,vertical?80:0,2,low,high,0,-2,row.get("pixels").getAsInt(),mode==1,mode==2?1:mode==3?2:0,mode==4,false,row.get("interpolation").getAsInt(),vertical,moat,origins);
            var before=row.getAsJsonArray("before");var actors=new ArrayList<NativeRampRiderTargets.Candidate>();for(int i=0;i<before.size();i++){var a=before.get(i).getAsJsonArray();actors.add(new NativeRampRiderTargets.Candidate(i,box(a,3),i==0,!row.get("noClip").getAsBoolean(),n(a,10),i!=2));}
            var targets=NativeRampRiderTargets.select(spec,material==3,row.get("previous").getAsDouble(),row.get("current").getAsDouble(),row.get("fromOriginal").getAsBoolean(),row.get("toOriginal").getAsBoolean(),actors);
            var obstacles=new ArrayList<AABB>();for(var value:row.getAsJsonArray("obstacles"))obstacles.add(box(value.getAsJsonArray(),0));boolean accepted=targets!=null;if(accepted)for(var entry:targets.entrySet()){var clipped=NativeRampRiders.clipped(entry.getKey().body(),entry.getValue(),obstacles);var d=entry.getValue();if(Math.abs(clipped.x-d.x)>1e-6||Math.abs(clipped.y-d.y)>1e-6||Math.abs(clipped.z-d.z)>1e-6){accepted=false;break;}}
            if(accepted!=row.get("accepted").getAsBoolean())throw new AssertionError("Source all-rider obstruction row="+rows);if(!accepted)blocked++;
            var after=row.getAsJsonArray("after");boolean changed=false;for(var actor:actors){var old=before.get(actor.key()).getAsJsonArray();var wanted=after.get(actor.key()).getAsJsonArray();var delta=accepted?targets.get(actor):null;double dx=delta==null?0:delta.x,dy=delta==null?0:delta.y,dz=delta==null?0:delta.z;
                if(delta!=null&&!vertical&&!moat&&material!=3&&spec.travel()!=0){dx=(n(old,0)+dx)-n(old,0);dz=(n(old,2)+dz)-n(old,2);}
                for(int i=0;i<3;i++)same(n(old,i)+(i==0?dx:i==1?dy:dz),n(wanted,i),rows,actor.key(),i);
                // Position-setting rebuilds the entity's AABB using its authored dimensions; do not duplicate that implementation here.
                same(n(old,9),n(wanted,9),rows,actor.key(),9);same(delta==null?n(old,10):0,n(wanted,10),rows,actor.key(),10);same(n(old,11),n(wanted,11),rows,actor.key(),11);same(delta==null?n(old,12):1,n(wanted,12),rows,actor.key(),12);same(delta==null?n(old,13):0,n(wanted,13),rows,actor.key(),13);
                changed|=!old.equals(wanted);
            }if(changed)moved++;rows++;
        }}if((rows!=46080&&rows!=9216)||moved==0||blocked==0)throw new AssertionError("Rider coverage "+rows+"/"+moved+"/"+blocked);System.out.println("Native Ramp rider selection PASS actualSource="+rows+" moved="+moved+" blocked="+blocked+" exact player/item/pig position, momentum, ground/fall; ordinary/elevator/stairs/sideways/extend");
    }
}

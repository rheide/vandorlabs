package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Independent actual stepEscalator outputs, including deliberate fail-atomic refusal differences. */
public final class NativeRampBeltSelectionChecks {
    private static long checks;
    private static double n(JsonArray a,int i){return a.get(i).getAsDouble();}
    private static Vec3 point(JsonArray a){return new Vec3(n(a,0),n(a,1),n(a,2));}
    private static AABB box(JsonArray a,int o){return new AABB(n(a,o),n(a,o+1),n(a,o+2),n(a,o+3),n(a,o+4),n(a,o+5));}
    private static void same(double actual,double expected,int row,int actor,String field){checks++;if(Double.doubleToLongBits(actual)!=Double.doubleToLongBits(expected))throw new AssertionError("Belt row="+row+" actor="+actor+" field="+field+" expected="+expected+" actual="+actual);}
    public static void main(String[] args)throws Exception{
        int rows=0,refused=0,changed=0,sourcePartialActors=0,sourcePartialHistory=0;var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var input=new BufferedReader(new InputStreamReader(new java.security.DigestInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),digest),java.nio.charset.StandardCharsets.UTF_8))){for(String line;(line=input.readLine())!=null;){
            var row=JsonParser.parseString(line).getAsJsonObject();var facing=Direction.valueOf(row.get("facing").getAsString());int material=row.get("material").getAsInt(),scenario=row.get("scenario").getAsInt();var shapes=new ArrayList<AABB>();for(var value:row.getAsJsonArray("shapes"))shapes.add(box(value.getAsJsonArray(),0));var source=new BlockPos(0,80,0);
            var spec=new NativeRampFootprint.Spec(new BlockPos(8,80,8),facing,80,0,2,material==2?.5:0,material==1?.5:1,0,-2,row.get("pixels").getAsInt(),false,0,false,false,0,false,false,List.of(new NativeRampGeometry.Origin(source,shapes),new NativeRampGeometry.Origin(source.relative(facing),shapes)));
            var before=row.getAsJsonArray("before");var history=row.getAsJsonArray("history");var actors=new ArrayList<NativeRampBeltTargets.Candidate>();for(int i=0;i<before.size();i++){var a=before.get(i).getAsJsonArray();actors.add(new NativeRampBeltTargets.Candidate(i,point(a),box(a,3),i==0,i==2,false,scenario==2,false,false,false,new Vec3(n(a,9),n(a,10),n(a,11)),row.getAsJsonArray("stepHeights").get(i).getAsDouble(),history.get(i).isJsonNull()?null:point(history.get(i).getAsJsonArray())));}
            var obstacles=new ArrayList<AABB>();for(var value:row.getAsJsonArray("obstacles"))obstacles.add(box(value.getAsJsonArray(),0));
            var result=NativeRampBeltTargets.select(spec,material==3,row.get("distance").getAsDouble(),row.get("mode").getAsInt(),row.get("speed").getAsInt(),actors,(actor,query)->obstacles.stream().filter(b->b.intersects(query)).toList());boolean accepted=result!=null;checks++;if(accepted!=row.get("accepted").getAsBoolean())throw new AssertionError("Belt acceptance row="+rows+" expected="+row.get("accepted")+" actual="+accepted);
            if(!accepted){refused++;if(!before.equals(row.getAsJsonArray("after")))sourcePartialActors++;if(!history.equals(row.getAsJsonArray("savedHistory")))sourcePartialHistory++;rows++;continue;}
            same(result.nextDistance(),Double.parseDouble(row.get("nextDistance").getAsString()),rows,-1,"distance");var moves=new HashMap<Integer,NativeRampBeltTargets.Move>();for(var move:result.moves())moves.put(move.actor().key(),move);
            for(int i=0;i<before.size();i++){var a=before.get(i).getAsJsonArray();var expected=row.getAsJsonArray("after").get(i).getAsJsonArray();var move=moves.get(i);double[] actual=new double[14];for(int j=0;j<14;j++)actual[j]=n(a,j);if(move!=null){var target=move.target();actual[0]=target.x;actual[1]=target.y;actual[2]=target.z;double radius=(float)(n(a,6)-n(a,3))*.5,height=(float)(n(a,7)-n(a,4));actual[3]=target.x-radius;actual[4]=target.y;actual[5]=target.z-radius;actual[6]=target.x+radius;actual[7]=target.y+height;actual[8]=target.z+radius;actual[9]=i==2?0:actual[9];actual[10]=0;actual[11]=i==2?0:actual[11];actual[12]=1;actual[13]=0;}
                for(int j=0;j<14;j++)same(actual[j],n(expected,j),rows,i,"actor"+j);
                var saved=row.getAsJsonArray("savedHistory").get(i);checks++;if((move==null)!=saved.isJsonNull())throw new AssertionError("Belt history presence row="+rows+" actor="+i);if(move!=null){var target=move.target();var p=saved.getAsJsonArray();same(target.x,n(p,0),rows,i,"historyX");same(target.y,n(p,1),rows,i,"historyY");same(target.z,n(p,2),rows,i,"historyZ");}
                boolean clip=move!=null&&i==2?false:scenario==2;checks++;if(clip!=row.getAsJsonArray("noClipAfter").get(i).getAsBoolean())throw new AssertionError("Belt noClip row="+rows+" actor="+i);
            }
            if(!before.equals(row.getAsJsonArray("after")))changed++;rows++;
        }}
        if(!java.util.HexFormat.of().formatHex(digest.digest()).equals("4738d5725d1382be4cd6b90ce267b544af021feea2945c5d5b7c1dc104be2474"))throw new AssertionError("Frozen Source belt reference changed");
        if(rows!=13824||refused!=84||sourcePartialActors!=24||sourcePartialHistory!=12)throw new AssertionError("Belt reference coverage "+rows+"/"+refused+"/"+sourcePartialActors+"/"+sourcePartialHistory);
        System.out.println("Native belt target Source PASS rows="+rows+" changed="+changed+" refused="+refused+" checks="+checks+" Source-refusal-only actor mutations="+sourcePartialActors+" history mutations="+sourcePartialHistory+" intentionally not performed by native preflight");
    }
}

package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import com.vandorlabs.motion.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
/** Actual Source packet apply outputs, including sequence consumption on physical refusal. */
public final class NativePlatformMotionPolicyChecks {
    private static double value(JsonArray values,int index){return Double.parseDouble(values.get(index).getAsString());}
    public static void main(String[] args)throws Exception{
        int count=0,accepted=0;try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();var p=row.getAsJsonArray("position");var m=row.getAsJsonArray("packet");int flags=row.get("flags").getAsInt();long sequence=Long.parseLong(row.get("sequence").getAsString());String previous=row.get("previous").getAsString(),last=previous;double velocity=Double.parseDouble(row.get("velocity").getAsString());boolean header=PlatformMotionPolicy.header(row.get("dimension").getAsInt()==0,sequence,value(m,0),value(m,1),value(m,2),value(m,3));boolean ordered=header&&PlatformMotionPolicy.ordered(sequence,!previous.equals("none"),previous.equals("none")?0:Long.parseLong(previous));if(ordered&&sequence>0)last=Long.toString(sequence);boolean available=PlatformSupportPolicy.available((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);boolean applied=ordered&&PlatformMotionPolicy.physical(available,velocity,value(p,0),value(p,2),value(p,1),value(m,0),value(m,1),value(m,2),value(m,3),value(m,4),value(m,5));if(applied!=row.get("applied").getAsBoolean()||!last.equals(row.get("last").getAsString()))throw new AssertionError("Source motion admission/order case="+count);double[] after=applied?new double[]{value(p,0)+value(m,4),value(p,1)+value(m,3)-value(p,1),value(p,2)+value(m,5),.3,0,-.2,0}:new double[]{value(p,0),value(p,1),value(p,2),.3,velocity,-.2,7};var expected=row.getAsJsonArray("after");for(int i=0;i<after.length;i++)if(Double.doubleToLongBits(after[i])!=Double.doubleToLongBits(value(expected,i)))throw new AssertionError("Source motion output case="+count+" field="+i);if(row.get("grounded").getAsBoolean()!=applied)throw new AssertionError("Source grounded state");if(applied)accepted++;count++;}}
        if(count!=14483)throw new AssertionError("Source motion coverage "+count);System.out.println("Native platform correction policy PASS Source="+count+" accepted="+accepted+" exact positions/momentum/grounding/ordering; wire and actual client pending");
    }
}

package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyConnections;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

/** Released mask/roof callbacks replay against a world-free scalar policy. */
public final class CanopyContactReferenceChecks {
    public static void main(String[] args)throws Exception{
        int count=0,connected=0,cuts=0;
        try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonArray();boolean actual=false;
                if(!row.get(5).isJsonNull()){var p=row.get(5).getAsJsonArray();actual=CanopyConnections.connects(row.get(4).getAsInt(),row.get(0).getAsString(),row.get(1).getAsInt(),row.get(2).getAsInt(),row.get(3).getAsInt(),p.get(0).getAsString(),p.get(1).getAsInt(),p.get(2).getAsInt(),p.get(3).getAsInt(),p.get(4).getAsInt(),p.get(5).getAsInt(),p.get(6).getAsInt(),p.get(7).getAsBoolean());}
                boolean roof=actual&&!CanopyConnections.cutsRoof(row.get(6).isJsonNull()?null:row.get(6).getAsString());
                if(actual!=row.get(7).getAsBoolean()||roof!=row.get(8).getAsBoolean())throw new AssertionError("Source contact row="+count+" actual="+actual+" roof="+roof+" input="+row);
                count++;if(actual)connected++;if(actual&&!roof)cuts++;
            }
        }
        if(count<150000||connected==0||cuts==0)throw new AssertionError("Contact coverage "+count);
        System.out.println("Canopy contacts reference PASS Source="+count+" connected="+connected+" roofCuts="+cuts+" direct/helper/released/mount/facing/length/constituent");
    }
}

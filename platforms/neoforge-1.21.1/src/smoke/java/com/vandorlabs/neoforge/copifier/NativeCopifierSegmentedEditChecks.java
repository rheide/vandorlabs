package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

public final class NativeCopifierSegmentedEditChecks {
    private static int checks;
    private static void same(Object expected,Object actual,String why){checks++;if(!expected.equals(actual))throw new AssertionError(why+" expected="+expected+" actual="+actual);}
    private static void apply(java.util.List<NativeCopifierSegmentedEdit.State> states,int selected,CompoundTag values){
        var plan=NativeCopifierSegmentedEdit.prepare(states.get(selected),values);if(!plan.applicable())return;
        for(int i=0;i<states.size();i++)if(i==selected)states.set(i,plan.state());else if(plan.configuration())states.set(i,NativeCopifierSegmentedEdit.peer(states.get(i),plan));
    }
    public static void main(String[] args)throws Exception{
        byte[] raw;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=stream.readAllBytes();}
        same("edbf1837c357a857439d0745462fc018a7ec2223cfc5417012f12fe53672d560",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)),"Source reference");int count=0;
        for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();var states=new java.util.ArrayList<NativeCopifierSegmentedEdit.State>();
            for(var input:row.getAsJsonArray("input"))states.add(NativeCopifierSegmentedEdit.read(TagParser.parseTag(input.getAsString())));
            var before=row.getAsJsonArray("before");for(int i=0;i<states.size();i++)same(TagParser.parseTag(before.get(i).getAsString()),NativeCopifierSegmentedEdit.capture(states.get(i)),"initial case="+count+" section="+i);
            int selected=row.get("selected").getAsInt();var undo=NativeCopifierSegmentedEdit.capture(states.get(selected));var values=TagParser.parseTag(row.get("values").getAsString());var original=values.copy();boolean applicable=false;
            try{applicable=NativeCopifierSegmentedEdit.prepare(states.get(selected),values).applicable();apply(states,selected,values);}catch(IllegalArgumentException invalid){/* Source refuses global malformed fields before any setter. */}
            same(row.get("applied").getAsBoolean(),applicable,"applicability case="+count);same(original,values,"caller compound");
            var after=row.getAsJsonArray("after");for(int i=0;i<states.size();i++)same(TagParser.parseTag(after.get(i).getAsString()),NativeCopifierSegmentedEdit.capture(states.get(i)),"applied case="+count+" section="+i+" values="+values);
            if(applicable){apply(states,selected,undo);var expected=row.getAsJsonArray("undo");for(int i=0;i<states.size();i++)same(TagParser.parseTag(expected.get(i).getAsString()),NativeCopifierSegmentedEdit.capture(states.get(i)),"undo case="+count+" section="+i);}
            count++;
        }
        same(576,count,"whole Source corpus");System.out.println("Native Segmented Door copied policy PASS cases="+count+" checks="+checks);
    }
}

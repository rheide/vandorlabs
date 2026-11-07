package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
/** Independent actual Source captures/applications, including callback undo and intentional ignored fields. */
public final class NativeCopifierTrapdoorEditChecks {
    private static int checks;
    private static void same(Object expected,Object actual,String label){checks++;if(!expected.equals(actual))throw new AssertionError(label+" expected="+expected+" actual="+actual);}
    public static void main(String[] args)throws Exception{
        byte[] raw;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=in.readAllBytes();}same("a267729194c93911e166d3b7b1e4997be3c45fbc32070043308d199bf9d286d7",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)),"Source reference");int count=0;
        for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();var state=NativeCopifierTrapdoorEdit.read(row.get("diagonal").getAsBoolean(),row.get("face").getAsInt(),row.get("offset").getAsBoolean(),TagParser.parseTag(row.get("input").getAsString()));var undo=NativeCopifierTrapdoorEdit.capture(state);same(TagParser.parseTag(row.get("before").getAsString()),undo,"before="+count);var values=TagParser.parseTag(row.get("values").getAsString());var original=values.copy();boolean applied=false;
            try{var plan=NativeCopifierTrapdoorEdit.prepare(state,values,true);applied=plan.applicable();if(applied)state=plan.state();}catch(IllegalArgumentException malformed){/* Source global preflight refuses the entire edit. */}
            same(row.get("applied").getAsBoolean(),applied,"applicability="+count);same(TagParser.parseTag(row.get("after").getAsString()),NativeCopifierTrapdoorEdit.capture(state),"after="+count+" values="+values);same(original,values,"caller compound");
            if(applied){state=NativeCopifierTrapdoorEdit.prepare(state,undo,true).state();same(TagParser.parseTag(row.get("undo").getAsString()),NativeCopifierTrapdoorEdit.capture(state),"undo="+count);}
            count++;
        }
        same(768,count,"whole corpus");System.out.println("Native Trapdoor copied policy PASS cases="+count+" checks="+checks);
    }
}

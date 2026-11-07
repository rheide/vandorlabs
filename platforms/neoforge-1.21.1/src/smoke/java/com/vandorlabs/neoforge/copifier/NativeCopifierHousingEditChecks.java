package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Independent actual Forge post-apply captures; the adapter remains a pure uninstalled plan. */
public final class NativeCopifierHousingEditChecks {
    public static void main(String[] args)throws Exception{
        JsonArray rows;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){rows=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();}
        Map<String,CompoundTag> fresh=new HashMap<>();for(var e:rows){var r=e.getAsJsonObject();if(r.get("profile").getAsInt()==1)fresh.put(r.get("block").getAsString(),TagParser.parseTag(r.get("input").getAsString()));}
        int cases=0;for(var e:rows){var r=e.getAsJsonObject();String id=r.get("block").getAsString();var current=fresh.get(id);var original=current.copy();var values=TagParser.parseTag(r.get("capture").getAsString());var copied=values.copy();var plan=NativeCopifierHousingEdit.prepare(NativeCopifierHousingCapture.Family.of(id),current,values);var expected=TagParser.parseTag(r.get("recaptured").getAsString());if(plan.applicable()!=r.get("applied").getAsBoolean()||!plan.capture().equals(expected))throw new AssertionError("Copy plan "+id+" profile="+r.get("profile")+" expected="+expected+" actual="+plan.capture());if(!current.equals(original)||!values.equals(copied))throw new AssertionError("Plan mutated caller");cases++;}
        int preflight=0;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(args[1])))){
            for(var e:JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray()){
                var r=e.getAsJsonObject();var values=TagParser.parseTag(r.get("values").getAsString());var current=fresh.get("programmable_block");var original=current.copy();boolean accepted;
                try{var plan=NativeCopifierHousingEdit.prepare(NativeCopifierHousingCapture.Family.CUBE,current,values);accepted=plan.applicable();if(accepted&&plan.appearance().housing().mainTexture!=4)throw new AssertionError("Missing housing edit");}catch(IllegalArgumentException refused){accepted=false;}
                if(accepted!=r.get("accepted").getAsBoolean()||NativeCopiedSettingsValidation.valid(values)!=accepted||!current.equals(original))throw new AssertionError("Source preflight "+r);preflight++;
            }
        }
        System.out.println("Native Copifier preflight PASS Source typed/malformed cases="+preflight);
        System.out.println("Native Copifier housing edit PASS Source plans="+cases+" post-apply recapture/immutability; world commit remains pending");
    }
}

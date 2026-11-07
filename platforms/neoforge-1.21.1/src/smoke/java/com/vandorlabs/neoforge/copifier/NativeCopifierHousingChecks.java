package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Native saved-data capture against the actual Forge housing-family callbacks. */
public final class NativeCopifierHousingChecks {
    public static void main(String[] args)throws Exception{
        byte[] raw;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=stream.readAllBytes();}
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equals("38a0bb9557e05717852f1f26fe020f31fcc3535438ce9d36a533f84f0a7ff31f"))throw new AssertionError("Wrong Source housing reference");
        int cases=0;for(var element:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());var original=input.copy();
            var actual=NativeCopifierHousingCapture.capture(NativeCopifierHousingCapture.Family.of(row.get("block").getAsString()),input);
            var expected=TagParser.parseTag(row.get("capture").getAsString());
            if(!actual.equals(expected))throw new AssertionError("Housing capture "+row.get("block")+" profile="+row.get("profile")+" expected="+expected+" actual="+actual);
            if(!input.equals(original))throw new AssertionError("Capture mutated input");cases++;
        }
        System.out.println("Native Copifier housing capture PASS Source cases="+cases+" saved-data projection only; installed-host capture/apply pending");
    }
}

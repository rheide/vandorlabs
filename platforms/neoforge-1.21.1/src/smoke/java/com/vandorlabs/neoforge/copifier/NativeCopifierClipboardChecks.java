package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Actual native tags against the original tool's presence, title, clear and failed-capture behavior. */
public final class NativeCopifierClipboardChecks {
    public static void main(String[] args)throws Exception{
        byte[] raw;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=stream.readAllBytes();}
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        need(hash.toString().equals("1d29802db862f643723b7c9063baba03d7f9575b28be00a02d942398b324f25e"),"frozen Source clipboard hash");
        int cases=0;for(var element:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());var original=input.copy();
            need(NativeCopifierClipboard.hasCopy(input)==row.get("hasCopy").getAsBoolean(),"clipboard presence");
            need(NativeCopifierClipboard.suffix(input).equals(row.get("suffix").getAsString()),"typed Source suffix");
            var expected=row.get("cleared").isJsonNull()?null:TagParser.parseTag(row.get("cleared").getAsString());need(java.util.Objects.equals(NativeCopifierClipboard.clear(input),expected),"clear exact retained fields");
            need(input.equals(NativeCopifierClipboard.copied(input,null,"failed"))&&input.equals(NativeCopifierClipboard.copied(input,new CompoundTag(),"failed")),"failed capture retains clipboard");
            var saved=new CompoundTag();saved.putInt("wall_texture",7);var copied=NativeCopifierClipboard.copied(input,saved,"Wall");saved.putInt("wall_texture",9);var choices=NativeCopifierClipboard.settings(copied);need(choices.getInt("wall_texture")==7,"capture detached from caller");choices.putInt("wall_texture",11);need(NativeCopifierClipboard.settings(copied).getInt("wall_texture")==7,"read detached from clipboard");
            need(input.equals(original),"input unchanged");need(NativeCopifierClipboard.suffix(copied).equals(" - Wall")&&NativeCopifierClipboard.hasCopy(copied),"new capture replaces prior choice/name");cases++;
        }
        need(!NativeCopifierClipboard.hasCopy(null)&&NativeCopifierClipboard.settings(null)==null&&NativeCopifierClipboard.clear(null)==null&&NativeCopifierClipboard.suffix(null).isEmpty(),"missing root remains absent");
        System.out.println("Native Copifier clipboard PASS actual Source cases="+cases+" typed suffix/clear/failed capture and private copy isolation; no registered tool route");
    }
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

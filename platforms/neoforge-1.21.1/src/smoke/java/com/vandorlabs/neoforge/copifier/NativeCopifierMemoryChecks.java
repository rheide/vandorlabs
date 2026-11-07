package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Actual native NBT accounting against the independent released Forge private method. */
public final class NativeCopifierMemoryChecks {
 public static void main(String[] args)throws Exception{byte[] raw;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){raw=in.readAllBytes();}if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).equals("e7379a267e6013a04fa1ee9621993088ec833d9968eb444c93295a6c862675ae"))throw new AssertionError("Wrong Source memory reference");var bytes=NativeCopifierUndo.class.getDeclaredMethod("bytes",Tag.class);bytes.setAccessible(true);int cases=0;for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){var row=element.getAsJsonObject();var tag=TagParser.parseTag(row.get("tag").getAsString());long actual=((Number)bytes.invoke(null,tag)).longValue();if(actual!=row.get("bytes").getAsLong())throw new AssertionError("Retained-tree estimate case="+cases+" expected="+row.get("bytes")+" actual="+actual);cases++;}long unsupported=((Number)bytes.invoke(null,new LongArrayTag(new long[]{1}))).longValue();if(unsupported<=16L*1024*1024)throw new AssertionError("Unmeasured long array retained");System.out.println("Native Copifier memory PASS SourceCases="+cases+" unknown-long-array refusal");}
}

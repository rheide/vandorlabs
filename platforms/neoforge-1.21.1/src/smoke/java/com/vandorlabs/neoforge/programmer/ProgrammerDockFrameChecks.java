package com.vandorlabs.neoforge.programmer;
import com.google.gson.*;
import com.vandorlabs.neoforge.programmer.client.NativeProgrammerDockFrame;
import java.nio.file.*;
import java.io.*;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
/** Actual old production GL frames, not expectations reproduced from native pose constants. */
public final class ProgrammerDockFrameChecks {
 private static Matrix4f matrix(JsonArray values){float[] data=new float[16];if(values.size()!=16)throw new AssertionError("Matrix words");for(int i=0;i<16;i++)data[i]=Float.intBitsToFloat(values.get(i).getAsInt());return new Matrix4f().set(data);}
 public static void main(String[] args)throws Exception{JsonObject reference;try(var input=new java.util.zip.GZIPInputStream(Files.newInputStream(Path.of(args[0])));var reader=new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){reference=JsonParser.parseReader(reader).getAsJsonObject();}if(!reference.get("golden").getAsString().equals("1ad003dc329dd31e4d4ec75a1c59d3e43c7d911aab5553df78cf4b0801cf613c"))throw new AssertionError("Actual released dock reference");int profiles=0;double error=0;for(var value:reference.getAsJsonArray("cases")){var row=value.getAsJsonObject();var face=Direction.valueOf(row.get("facing").getAsString());int slot=row.get("slot").getAsInt();boolean chip=row.get("chip").getAsBoolean();var frame=NativeProgrammerDockFrame.matrix(face,slot,chip);if(frame!=NativeProgrammerDockFrame.matrix(face,slot,chip))throw new AssertionError("Cached pose identity");var root=row.getAsJsonArray("root");var actual=matrix(row.getAsJsonArray("base")).translate(root.get(0).getAsFloat(),root.get(1).getAsFloat(),root.get(2).getAsFloat()).mul(frame);var expected=matrix(row.getAsJsonArray("matrix"));for(int column=0;column<4;column++)for(int axis=0;axis<4;axis++){double delta=Math.abs(actual.get(column,axis)-expected.get(column,axis));error=Math.max(error,delta);if(delta>2e-6)throw new AssertionError("Source retained dock "+face+" slot="+slot+" chip="+chip+" component="+column+","+axis+" error="+delta);}profiles++;}if(profiles!=24)throw new AssertionError("Whole dock corpus");System.out.println("[vandorlabs][programmer-dock-frame] PASS actualSourceProfiles="+profiles+" maxMatrixError="+error);}
}

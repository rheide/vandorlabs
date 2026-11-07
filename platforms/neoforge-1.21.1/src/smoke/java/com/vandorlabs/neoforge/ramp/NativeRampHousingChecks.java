package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
/** Runs in the actual NeoForge server context, where FeatureFlags and registries are initialized. */
final class NativeRampHousingChecks {
 static void run(Path reference,NativeRampBlock full,NativeRampSlabBlock slab)throws Exception{int count=0;try(var lines=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(reference)),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=lines.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var expected=row.getAsJsonObject("snapshot");var block=row.get("block").getAsString().endsWith("_slab")?slab:full;var state=block.legacyState(row.get("inputMeta").getAsInt());if(block.legacyMeta(state)!=expected.get("meta").getAsInt()||NativeRampBlock.scanFacing(state).get3DDataValue()!=expected.get("scan").getAsInt()||state.getValue(NativeRampBlock.FACING).get3DDataValue()!=expected.get("travelFacing").getAsInt())throw new AssertionError("Ramp metadata/context "+count);var b=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds();double[] actual={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};var box=expected.getAsJsonArray("bounds");for(int i=0;i<6;i++)if(actual[i]!=box.get(i).getAsDouble())throw new AssertionError("Ramp housing bounds "+count+" axis="+i);count++;}}if(count!=256)throw new AssertionError("Housing coverage");System.out.println("Native Ramp actual housing PASS Source="+count+" metadata/sixScanFaces/independentTravel/horizontalSlabHalves");}
 private NativeRampHousingChecks(){}
}

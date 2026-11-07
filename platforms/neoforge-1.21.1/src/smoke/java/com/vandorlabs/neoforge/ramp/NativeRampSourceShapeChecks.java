package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.StairsShape;
public final class NativeRampSourceShapeChecks {
 public static void main(String[] args)throws Exception{int profiles=0,boxes=0;try(var input=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var actual=NativeRampSourceShapes.vanillaStairs(Direction.from3DDataValue(row.get("facing").getAsInt()),row.get("upper").getAsBoolean(),StairsShape.valueOf(row.get("shape").getAsString()));var expected=row.getAsJsonArray("boxes");if(actual.size()!=expected.size())throw new AssertionError("Source stair decomposition count "+row);for(int i=0;i<actual.size();i++){var b=actual.get(i);double[] a={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};for(int j=0;j<6;j++)if(a[j]!=expected.get(i).getAsJsonArray().get(j).getAsDouble())throw new AssertionError("Source stair decomposition "+row+" box="+i+" axis="+j);boxes++;}profiles++;}}if(profiles!=40||boxes!=96)throw new AssertionError("Stair source coverage");System.out.println("Native Ramp source shapes PASS actualSource="+profiles+" sourceOrderedBoxes="+boxes);}
}

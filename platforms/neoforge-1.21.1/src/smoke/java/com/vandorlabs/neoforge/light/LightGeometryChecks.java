package com.vandorlabs.neoforge.light;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.Direction;
/** Actual Source one-box geometry, without activating the family. */
public final class LightGeometryChecks {
 public static void main(String[] args)throws Exception{int rows=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();int family=row.get("family").getAsInt();Direction facing=Direction.from3DDataValue(row.get("facing").getAsInt());boolean upper=row.get("upper").getAsBoolean(),small=row.get("small").getAsBoolean();var box=NativeLightGeometry.bounds(family,facing,upper,small);double[] values={box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ};for(int n=0;n<6;n++)if(Double.doubleToRawLongBits(values[n])!=Double.doubleToRawLongBits(row.getAsJsonArray("bounds").get(n).getAsDouble()))throw new AssertionError("Light shape "+row+" actual="+box);if(!NativeLightGeometry.shape(family,facing,upper,small).bounds().equals(box)||row.get("waterloggable").getAsBoolean()||row.get("opacity").getAsInt()!=0||row.get("ao").getAsFloat()!=1||row.get("opaque").getAsBoolean()||row.get("full").getAsBoolean())throw new AssertionError("Light native source housing contract");rows++;}}if(rows!=72||NativeLightGeometry.retainedBytes()>2*1024*1024)throw new AssertionError("Light shape budget/coverage");System.out.println("Native Light geometry PASS profiles="+rows+" exact bounds/opaque/zero opacity/no waterlogging retainedBytes="+NativeLightGeometry.retainedBytes());}
}

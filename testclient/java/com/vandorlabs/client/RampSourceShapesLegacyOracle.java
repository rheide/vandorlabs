package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.compat.RampStairs;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
/** Actual released vanilla-stair source cuboids, independent of transformed ramp boxes. */
public final class RampSourceShapesLegacyOracle {
 public static void main(String[] args)throws Exception{RampSettingsLegacyOracle.initialize();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);List<String> rows=new ArrayList<>();int boxes=0;for(EnumFacing face:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values()){JsonObject row=new JsonObject();row.addProperty("facing",face.getIndex());row.addProperty("upper",half==BlockStairs.EnumHalf.TOP);row.addProperty("shape",shape.name());JsonArray list=new JsonArray();for(com.vandorlabs.ramp.RampGeometry.Box box:RampStairs.boxes(world,Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,face).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape))){JsonArray b=new JsonArray();for(double v:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})b.add(v);list.add(b);boxes++;}row.add("boxes",list);rows.add(row.toString());}RampSettingsLegacyOracle.retire(world);if(rows.size()!=40||boxes!=96)throw new AssertionError("Actual stair source coverage");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,rows,java.nio.charset.StandardCharsets.UTF_8);StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))sha.append(String.format(Locale.ROOT,"%02x",b&255));if(!sha.toString().equals("9987b2cb027ca878b77b64ead866a37ff22157f9431bd12d6522734c32e3fa86"))throw new AssertionError("Changed source shape golden "+sha);System.out.println("Ramp Source source-shapes PASS profiles="+rows.size()+" boxes="+boxes+" sha256="+sha);}
}

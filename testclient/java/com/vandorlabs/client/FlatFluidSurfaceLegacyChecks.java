package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.lang.reflect.*;
import java.util.*;
/** Freeze actual current Forge analytic panel surface geometry without starting a rendered client. */
public final class FlatFluidSurfaceLegacyChecks {
 private static final String GOLDEN="810a437312993934de0db60a98cdeea3cb6a749e69968808b95a348076186843";
 private static java.security.MessageDigest digest;
 private static void number(long value){for(int i=7;i>=0;i--)digest.update((byte)(value>>>(i*8)));}
 private static void point(Vec3d p){number(Double.doubleToRawLongBits(p.x));number(Double.doubleToRawLongBits(p.y));number(Double.doubleToRawLongBits(p.z));}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();digest=java.security.MessageDigest.getInstance("SHA-256");Class<?> source=Class.forName("com.vandorlabs.client.WaterloggedSurfaceMesh$Source");Constructor<?> constructor=source.getDeclaredConstructor(World.class,BlockPos.class);constructor.setAccessible(true);Method build=WaterloggedSurfaceMesh.class.getDeclaredMethod("build",source,World.class,BlockPos.class);build.setAccessible(true);Field surfaces=source.getDeclaredField("surfaces");surfaces.setAccessible(true);
  com.google.gson.JsonArray cases=new com.google.gson.JsonArray();int faceCount=0;
  for(boolean inset:new boolean[]{false,true}){BlockProgrammableWall wall=new BlockProgrammableWall(inset?"programmable_porthole_wall":"programmable_wall",inset?BlockProgrammableWall.Shape.PORTHOLE:BlockProgrammableWall.Shape.PLAIN);for(EnumFacing facing:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(int depth=0;depth<3;depth++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);IBlockState state=wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth);world.setBlockState(pos,state,2);Object own=constructor.newInstance(world,pos);build.invoke(null,own,world,pos);List<WaterloggedSurfaceMesh.Surface> faces=(List<WaterloggedSurfaceMesh.Surface>)surfaces.get(own);
   com.google.gson.JsonObject row=new com.google.gson.JsonObject();row.addProperty("facing",facing.getName());row.addProperty("depth",depth);row.addProperty("inset",inset);com.google.gson.JsonArray result=new com.google.gson.JsonArray();number(inset?1:0);number(facing.getIndex());number(depth);number(faces.size());
   for(WaterloggedSurfaceMesh.Surface face:faces){number(face.side);number(face.axis);number(face.boundary==null?-1:face.boundary.getIndex());number(face.vertices.length);point(face.normal);com.google.gson.JsonObject data=new com.google.gson.JsonObject();data.addProperty("side",face.side);data.addProperty("axis",face.axis);data.addProperty("boundary",face.boundary==null?-1:face.boundary.getIndex());data.add("normal",xyz(face.normal));com.google.gson.JsonArray points=new com.google.gson.JsonArray();for(Vec3d p:face.vertices){point(p);points.add(xyz(p));}data.add("vertices",points);result.add(data);faceCount++;}row.add("faces",result);cases.add(row);
  }}StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("Actual Forge flat-fluid surface geometry changed "+hash);
  String output=System.getProperty("vandorlabs.flatFluidExport");if(output!=null){com.google.gson.JsonObject root=new com.google.gson.JsonObject();root.addProperty("format",1);root.addProperty("golden",hash.toString());root.add("cases",cases);java.nio.file.Path file=java.nio.file.Paths.get(output);java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());try(java.io.Writer writer=java.nio.file.Files.newBufferedWriter(file,java.nio.charset.StandardCharsets.UTF_8)){new com.google.gson.GsonBuilder().create().toJson(root,writer);}}
  System.out.println("Actual Forge flat fluid surfaces PASS cases="+cases.size()+" faces="+faceCount+" raw-bit golden="+hash);
 }
 private static com.google.gson.JsonArray xyz(Vec3d p){com.google.gson.JsonArray array=new com.google.gson.JsonArray();array.add(p.x);array.add(p.y);array.add(p.z);return array;}
}

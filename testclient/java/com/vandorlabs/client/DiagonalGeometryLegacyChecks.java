package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Independent actual Forge collision and analytic fluid reference, with real neighbour discovery. */
public final class DiagonalGeometryLegacyChecks {
 private static final BlockPos POS=BlockPos.ORIGIN;
 private static java.security.MessageDigest digest;
 private static long boxes,faces;
 private static void number(long value){for(int i=7;i>=0;i--)digest.update((byte)(value>>>(i*8)));}
 private static JsonArray values(double... values){JsonArray a=new JsonArray();for(double v:values){a.add(v);number(Double.doubleToRawLongBits(v));}return a;}
 private static JsonArray box(AxisAlignedBB b){return values(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
 private static void nullable(JsonObject row,String key,Double value){if(value==null||Double.isNaN(value))row.add(key,JsonNull.INSTANCE);else row.addProperty(key,value);}
 private static void install(NonRenderingChecks.MemoryWorld world,BlockPos pos,BlockProgrammableWall wall,EnumFacing facing,boolean inverted,int mode,int fill,int join){
  IBlockState state=wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
  world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setDiagonalGeometry(mode,fill);tile.setDiagonalJoin(join);tile.setJoinPortholes(true);if(BlockProgrammableWall.geometry(world,pos)!=mode||BlockProgrammableWall.fill(world,pos)!=fill||tile.getDiagonalJoin()!=join)throw new AssertionError("fixture did not configure installed tile");
 }
 private static void profile(NonRenderingChecks.MemoryWorld world,BlockProgrammableWall diagonal,BlockProgrammableWall flat,EnumFacing face,boolean inverted,int mode,int profile){
  EnumFacing right=face.rotateY();
  if(profile==1||profile==3)for(int offset=-1;offset<=1;offset++)world.setBlockState(POS.down().offset(face.getOpposite(),offset),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,face).withProperty(BlockProgrammableWall.DEPTH,1),2);
  if(profile==2||profile==3)for(int offset=-1;offset<=1;offset++)world.setBlockState(POS.up().offset(face.getOpposite(),offset),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,face).withProperty(BlockProgrammableWall.DEPTH,2),2);
  if(profile==4)for(EnumFacing d:EnumFacing.values())world.setBlockState(POS.offset(d),Blocks.STONE.getDefaultState(),2);
  if(profile==5)for(EnumFacing d:EnumFacing.values())install(world,POS.offset(d),diagonal,face,!inverted,mode,0,1);
  if(profile==6){install(world,POS.offset(face),diagonal,right,inverted,mode,0,0);install(world,POS.offset(face.getOpposite()),diagonal,right.getOpposite(),inverted,mode,0,0);install(world,POS.offset(right),diagonal,face,inverted,mode,0,0);}
  if(profile==7){world.setBlockState(POS.offset(face),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,right).withProperty(BlockProgrammableWall.DEPTH,0),2);world.setBlockState(POS.offset(face.getOpposite()),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,right.getOpposite()).withProperty(BlockProgrammableWall.DEPTH,2),2);world.setBlockState(POS.offset(right),Blocks.STONE.getDefaultState(),2);}
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();digest=java.security.MessageDigest.getInstance("SHA-256");
  Class<?> source=Class.forName("com.vandorlabs.client.WaterloggedSurfaceMesh$Source");Constructor<?> constructor=source.getDeclaredConstructor(World.class,BlockPos.class);constructor.setAccessible(true);Method build=WaterloggedSurfaceMesh.class.getDeclaredMethod("build",source,World.class,BlockPos.class);build.setAccessible(true);Field surfaces=source.getDeclaredField("surfaces");surfaces.setAccessible(true);
  JsonArray cases=new JsonArray();BlockProgrammableWall flat=new BlockProgrammableWall("programmable_wall",BlockProgrammableWall.Shape.PLAIN);
  for(boolean inset:new boolean[]{false,true}){BlockProgrammableWall wall=new BlockProgrammableWall(inset?"diagonal_porthole_wall":"diagonal_wall",inset?BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE:BlockProgrammableWall.Shape.DIAGONAL);
   for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int profile=0;profile<8;profile++)for(int join=0;join<(profile==0?12:1);join++){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);install(world,POS,wall,face,inverted,mode,fill,join);profile(world,wall,flat,face,inverted,mode,profile);IBlockState state=world.getBlockState(POS);
    JsonObject row=new JsonObject();row.addProperty("facing",face.getName());row.addProperty("inverted",inverted);row.addProperty("mode",mode);row.addProperty("fill",fill);row.addProperty("profile",profile);row.addProperty("joinChoice",join);row.addProperty("inset",inset);number(face.getIndex());number(inverted?1:0);number(mode);number(fill);number(profile);number(join);number(inset?1:0);
    nullable(row,"lower",BlockProgrammableWall.flatEnd(state,world,POS,false));nullable(row,"upper",BlockProgrammableWall.flatEnd(state,world,POS,true));row.addProperty("hideLower",BlockProgrammableWall.coveredFlatEnd(state,world,POS,false));row.addProperty("hideUpper",BlockProgrammableWall.coveredFlatEnd(state,world,POS,true));row.add("clip",values(DiagonalNeighbourBounds.local(world,POS,state,mode,1)));
    BlockProgrammableWall.Corner c=wall.corner(state,world,POS);if(c==null)row.add("corner",JsonNull.INSTANCE);else{JsonObject corner=new JsonObject();if(c.frontRight!=null)corner.addProperty("frontRight",c.frontRight);if(c.backRight!=null)corner.addProperty("backRight",c.backRight);corner.addProperty("straightLeft",c.straightLeft);corner.addProperty("straightRight",c.straightRight);nullable(corner,"frontFlat",c.frontFlat);nullable(corner,"backFlat",c.backFlat);row.add("corner",corner);}
    List<AxisAlignedBB> collision=new ArrayList<>();wall.addCollisionBoxToList(state,world,POS,new AxisAlignedBB(-10,-10,-10,10,10,10),collision,null,true);JsonArray geometry=new JsonArray();number(collision.size());for(AxisAlignedBB b:collision){geometry.add(box(b));boxes++;}row.add("boxes",geometry);row.add("selection",box(wall.getBoundingBox(state,world,POS)));
    Object own=constructor.newInstance(world,POS);build.invoke(null,own,world,POS);List<WaterloggedSurfaceMesh.Surface> mesh=(List<WaterloggedSurfaceMesh.Surface>)surfaces.get(own);JsonArray result=new JsonArray();number(mesh.size());for(WaterloggedSurfaceMesh.Surface f:mesh){JsonObject data=new JsonObject();data.addProperty("side",f.side);data.addProperty("axis",f.axis);data.addProperty("boundary",f.boundary==null?-1:f.boundary.getIndex());number(f.side);number(f.axis);number(f.boundary==null?-1:f.boundary.getIndex());data.add("normal",values(f.normal.x,f.normal.y,f.normal.z));JsonArray vertices=new JsonArray();number(f.vertices.length);for(Vec3d p:f.vertices)vertices.add(values(p.x,p.y,p.z));data.add("vertices",vertices);result.add(data);faces++;}row.add("faces",result);cases.add(row);
   }
  }
  StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));String expected="e5e8e78899db52f45e7c3f74888b9b39eb8e6dc1f92722206c24beedee9e2062";if(!expected.equals(hash.toString()))throw new AssertionError("Forge diagonal baseline changed "+hash);
  String output=System.getProperty("vandorlabs.diagonalExport");if(output!=null){JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("golden",hash.toString());root.add("cases",cases);java.nio.file.Path path=java.nio.file.Paths.get(output);java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());try(java.io.Writer w=java.nio.file.Files.newBufferedWriter(path)){new Gson().toJson(root,w);}}
  System.out.println("Actual Forge diagonal geometry PASS cases="+cases.size()+" boxes="+boxes+" faces="+faces+" raw-bit golden="+hash);
 }
}

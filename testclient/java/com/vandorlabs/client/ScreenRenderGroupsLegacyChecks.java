package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.screen.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
/** Frozen discovery membership, with corrected render coordinates checked against surface vertices. */
public final class ScreenRenderGroupsLegacyChecks {
 private static int projectedLayouts;
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_render_groups_reference"));
  java.lang.reflect.Method base=ScreenJoinPredicatesLegacyChecks.class.getDeclaredMethod("base",BlockAnimatedScreenSelector.class),mutate=ScreenJoinPredicatesLegacyChecks.class.getDeclaredMethod("mutate",NBTTagCompound.class,int.class),matching=ScreenSurfaceGroups.class.getDeclaredMethod("matching",TileEntityAnimatedScreenSelector.class,TileEntityAnimatedScreenSelector.class,boolean.class);base.setAccessible(true);mutate.setAccessible(true);matching.setAccessible(true);JsonArray output=new JsonArray();
  for(String id:ScreenFamilyLegacyChecks.IDS){BlockAnimatedScreenSelector block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;for(IBlockState state:block.getBlockState().getValidStates())for(int variant=0;variant<36;variant++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);NBTTagCompound first=(NBTTagCompound)base.invoke(null,block),second=first.copy();if(variant<32)mutate.invoke(null,second,variant);else if(variant==32)second.setBoolean("FrameBare",true);else if(variant==33)second.setInteger("FrameFront",ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1"));else if(variant==34)second.setBoolean("FrameTile",true);else second.setBoolean("DisplayFit",false);
   TileEntityAnimatedScreenSelector a=tile(block,first,world,new BlockPos(0,80,0),state);BlockPos other=a.getPos().add(DisplayJoinGeometry.basis(a,state).steps[0]);TileEntityAnimatedScreenSelector b=tile(block,second,world,other,state);JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("meta",block.getMetaFromState(state));row.addProperty("variant",variant);row.addProperty("first",a.writeToNBT(new NBTTagCompound()).toString());row.addProperty("second",b.writeToNBT(new NBTTagCompound()).toString());row.addProperty("artworkMatch",(Boolean)matching.invoke(null,a,b,false));row.addProperty("frameMatch",(Boolean)matching.invoke(null,a,b,true));row.add("artwork",discoveryLayout(a,state,ScreenSurfaceGroups.layout(a)));row.add("frame",discoveryLayout(a,state,ScreenSurfaceGroups.frameLayout(a)));row.addProperty("wide",ScreenSurfaceGroups.wideDisplay(a));output.add(row);ScreenSurfaceGroups.unload(world);
  }}if(projectedLayouts==0)throw new AssertionError("Vertical rendered-axis correction was not exercised");String serialized=output.toString();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");StringBuilder digest=new StringBuilder();for(byte value:hash.digest(serialized.getBytes("UTF-8")))digest.append(String.format("%02x",value&255));if(!digest.toString().equals("0c627d7541a5c2991e0c0730b29f5ac01c35302e3252e954714f4c93f770d5dc"))throw new AssertionError("Released Screen discovery membership or corrected render orientation changed "+digest);JsonObject root=new JsonObject();root.addProperty("sha256",digest.toString());root.add("cases",output);java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/screen-render-groups-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,root.toString().getBytes("UTF-8"));System.out.println("Actual Screen render groups reference PASS cases="+output.size()+" projectedLayouts="+projectedLayouts+" sha256="+digest);
 }
 private static TileEntityAnimatedScreenSelector tile(BlockAnimatedScreenSelector block,NBTTagCompound tag,NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state){TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)block.createNewTileEntity(null,0);world.states.put(pos,state);world.tiles.put(pos,tile);tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);return tile;}
 private static JsonArray discoveryLayout(TileEntityAnimatedScreenSelector tile,IBlockState state,GlassSurfaceGroups.Layout layout){
  if(layout.columns==1&&layout.rows==1){JsonArray out=new JsonArray();out.add(layout.column);out.add(layout.row);out.add(1);out.add(1);return out;}
  // Keep the released corpus in its original discovery lattice. Determine the
  // inverse render projection from actual transformed vertices, not from the
  // new ScreenRenderLayout policy, so a missing/doubled mirror fails the hash.
  ScreenFamily family=ScreenFamily.fromId(state.getBlock().getRegistryName().getResourcePath());
  ScreenFamily.State saved=family.decode(state.getBlock().getMetaFromState(state));
  double[] pose=new double[16];ScreenRenderPose.write(family,saved,tile.isCeilingMounted(),tile.getCeilingPosition(-1),pose);
  ScreenSurface.Quad face=null;
  for(ScreenRenderGeometry.Surface surface:ScreenRenderGeometry.of(family,saved,tile.isSmallInput(),tile.getWallPosition(-1),tile.isCeilingMounted(),tile.getCeilingPosition(-1)).surfaces)if(surface.frame)face=surface.quad;
  if(face==null)throw new AssertionError("Missing rendered display surface");
  DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(tile,state);
  double right=axis(pose,face.topRight,face.topLeft,basis.steps[0]),up=axis(pose,face.topLeft,face.bottomLeft,basis.steps[2]);
  if(right==0||up==0)throw new AssertionError("Degenerate rendered display axes");
  int column=right<0?layout.columns-1-layout.column:layout.column,row=up<0?layout.rows-1-layout.row:layout.row;
  if(column!=layout.column||row!=layout.row)projectedLayouts++;
  JsonArray out=new JsonArray();out.add(column);out.add(row);out.add(layout.columns);out.add(layout.rows);return out;
 }
 private static double axis(double[] pose,ScreenSurface.Vertex a,ScreenSurface.Vertex b,BlockPos step){double x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;return (pose[0]*x+pose[4]*y+pose[8]*z)*step.getX()+(pose[1]*x+pose[5]*y+pose[9]*z)*step.getY()+(pose[2]*x+pose[6]*y+pose[10]*z)*step.getZ();}
}

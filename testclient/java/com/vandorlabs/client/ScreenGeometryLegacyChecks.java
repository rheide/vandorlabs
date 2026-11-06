package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.*;
import com.google.gson.*;
/** Actual canonical block bounds and collision ownership; excludes the separately deferred chip reader. */
public final class ScreenGeometryLegacyChecks {
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("minecraft:screen_geometry_reference"));
  JsonArray cases=new JsonArray();java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int boxes=0;
  for(String id:ScreenFamilyLegacyChecks.IDS){BlockAnimatedScreenSelector block=null;for(Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;if(block==null)throw new AssertionError(id);
   for(IBlockState state:block.getBlockState().getValidStates())for(int small=0;small<2;small++)for(int wall=-1;wall<3;wall++)for(int ceiling=-1;ceiling<3;ceiling++){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);world.states.put(pos,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)block.createNewTileEntity(null,0);
    tile.setSmallInput(small!=0);if(wall>=0)tile.setWallPosition(wall);tile.setCeilingMounted(ceiling>=0);if(ceiling>=0)tile.setCeilingPosition(ceiling);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);
    if(ScreenChipSupport.selected(tile))throw new AssertionError("Default geometry unexpectedly includes chip reader");
    JsonObject out=new JsonObject();out.addProperty("id",id);out.addProperty("meta",block.getMetaFromState(state));out.addProperty("small",small!=0);out.addProperty("wall",wall);out.addProperty("ceiling",ceiling);out.add("bounds",box(block.getBoundingBox(state,world,pos)));
    java.util.List<AxisAlignedBB> collision=new java.util.ArrayList<>();block.addCollisionBoxToList(state,world,pos,new AxisAlignedBB(-4,76,-4,5,85,5),collision,null,false);JsonArray list=new JsonArray();for(AxisAlignedBB value:collision){list.add(box(value.offset(0,-80,0)));boxes++;}out.add("collision",list);
    JsonArray rays=new JsonArray();for(int axis=0;axis<3;axis++)for(int a=0;a<3;a++)for(int b=0;b<3;b++){
     double[] start={a/2D,b/2D,.5},end=start.clone();int other=(axis+1)%3,last=(axis+2)%3;start[axis]=-2;end[axis]=3;start[other]=end[other]=a/2D;start[last]=end[last]=b/2D;RayTraceResult hit=block.collisionRayTrace(state,world,pos,new Vec3d(start[0],80+start[1],start[2]),new Vec3d(end[0],80+end[1],end[2]));if(hit==null)rays.add(JsonNull.INSTANCE);else{JsonArray ray=new JsonArray();ray.add(hit.hitVec.x);ray.add(hit.hitVec.y-80);ray.add(hit.hitVec.z);ray.add(hit.sideHit.getIndex());rays.add(ray);}
    }out.add("rays",rays);cases.add(out);digest.update(out.toString().getBytes("UTF-8"));
   }
  }
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!hash.toString().equals("83ede38318365cfad58149c9bb770d2ee211aa96d80dccc0f0aeec0768c779af"))throw new AssertionError("Actual Screen geometry changed: "+hash);JsonObject reference=new JsonObject();reference.addProperty("sha256",hash.toString());reference.add("cases",cases);java.nio.file.Path output=java.nio.file.Paths.get("build/test-artifacts/screen-geometry-reference.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.write(output,reference.toString().getBytes("UTF-8"));System.out.println("Actual Screen geometry reference PASS cases="+cases.size()+" collisionBoxes="+boxes+" rays="+(cases.size()*27)+" sha256="+hash);
 }
 private static JsonArray box(AxisAlignedBB box){JsonArray out=new JsonArray();for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})out.add(value);return out;}
}

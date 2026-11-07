package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual discovered contacts, entity collision, selection bounds, rays and contained-fluid cells. */
public final class FlatGlassContactLegacyOracle {
 private static final BlockPos POS=new BlockPos(8,80,8);
 private static JsonArray box(AxisAlignedBB b){JsonArray a=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})a.add(v);return a;}
 private static void install(NonRenderingChecks.MemoryWorld world,BlockProgrammableGlass glass,BlockPos pos,EnumFacing facing,int depth){IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);world.states.put(pos,state);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GlassPanelFacing",facing.getHorizontalIndex());tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);}
 public static void main(String[] args)throws Exception{
  PropulsionPlacementLegacyOracle.initialize();BlockProgrammableGlass glass=null;for(net.minecraft.block.Block b:ModBlocks.BLOCKS)if(b instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)b;if(glass==null)throw new AssertionError("glass");
  NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,bent=0,split=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(int facing=0;facing<4;facing++)for(int depth=0;depth<3;depth++)for(int front=0;front<7;front++)for(int back=0;back<7;back++)for(int straight=0;straight<4;straight++){
    world.clear();EnumFacing f=EnumFacing.getHorizontal(facing);install(world,glass,POS,f,depth);
    for(int end=0;end<2;end++){int p=end==0?front:back;if(p>0)install(world,glass,POS.offset(end==0?f:f.getOpposite()),p<=3?f.rotateY():f.rotateYCCW(),(p-1)%3);}
    for(int side=0;side<2;side++)if((straight&(1<<side))!=0)install(world,glass,POS.offset(side==0?f.rotateYCCW():f.rotateY()),f,depth);
    IBlockState state=world.getBlockState(POS);JsonObject row=new JsonObject();row.addProperty("facing",facing);row.addProperty("depth",depth);row.addProperty("front",front);row.addProperty("back",back);row.addProperty("straight",straight);row.addProperty("bar",PanelJoins.flatBar(state,world,POS)!=null);row.add("bounds",box(glass.getBoundingBox(state,world,POS)));
    List<AxisAlignedBB> collision=new ArrayList<>();glass.addCollisionBoxToList(state,world,POS,new AxisAlignedBB(POS),collision,null,false);JsonArray boxes=new JsonArray();double volume=0;for(AxisAlignedBB b:collision){AxisAlignedBB local=b.offset(-POS.getX(),-POS.getY(),-POS.getZ());boxes.add(box(local));volume+=(local.maxX-local.minX)*(local.maxY-local.minY)*(local.maxZ-local.minZ);}row.add("collision",boxes);
    com.vandorlabs.render.FlatGlassGeometry.Snapshot shared=com.vandorlabs.render.FlatGlassGeometry.build(PanelJoins.flatFacing(state,world,POS).getIndex(),PanelJoins.flatNear(state,world,POS),PanelJoins.flatCorner(state,world,POS));
    if(shared.boxes.size()!=collision.size())throw new AssertionError("Shared collision count "+profiles);for(int i=0;i<collision.size();i++)for(int axis=0;axis<6;axis++)if(Double.doubleToLongBits(shared.boxes.get(i).coordinate(axis))!=Double.doubleToLongBits(boxes.get(i).getAsJsonArray().get(axis).getAsDouble()))throw new AssertionError("Shared collision coordinate "+profiles+"/"+i+"/"+axis);
    for(int axis=0;axis<6;axis++)if(shared.bounds.coordinate(axis)!=row.getAsJsonArray("bounds").get(axis).getAsDouble())throw new AssertionError("Shared selection coordinate "+profiles+"/"+axis);
    if(PanelJoins.flatCorner(state,world,POS)!=null){bent++;AxisAlignedBB bounds=glass.getBoundingBox(state,world,POS);if(volume<(bounds.maxX-bounds.minX)*(bounds.maxY-bounds.minY)*(bounds.maxZ-bounds.minZ)-1E-9)split++;}
    JsonArray cells=new JsonArray();for(WaterloggedParts.Cell cell:WaterloggedParts.uncachedVolume(world,POS).cells){JsonObject c=new JsonObject();c.add("box",box(cell.box));c.addProperty("side",cell.side);if(shared.side((cell.box.minX+cell.box.maxX)/2,(cell.box.minY+cell.box.maxY)/2,(cell.box.minZ+cell.box.maxZ)/2)!=cell.side)throw new AssertionError("Shared fluid side "+profiles);cells.add(c);}row.add("cells",cells);
    JsonArray rays=new JsonArray();for(EnumFacing ray:EnumFacing.values()){Vec3d from=new Vec3d(POS.getX()+.5+ray.getFrontOffsetX()*2,POS.getY()+.5+ray.getFrontOffsetY()*2,POS.getZ()+.5+ray.getFrontOffsetZ()*2),to=new Vec3d(POS.getX()+.5-ray.getFrontOffsetX()*2,POS.getY()+.5-ray.getFrontOffsetY()*2,POS.getZ()+.5-ray.getFrontOffsetZ()*2);RayTraceResult hit=glass.collisionRayTrace(state,world,POS,from,to);com.vandorlabs.render.LegacyBoxTrace.Hit scalar=shared.trace(from.x-POS.getX(),from.y-POS.getY(),from.z-POS.getZ(),to.x-POS.getX(),to.y-POS.getY(),to.z-POS.getZ());if((hit==null)!=(scalar==null)||hit!=null&&(hit.sideHit.getIndex()!=scalar.face||hit.hitVec.x-POS.getX()!=scalar.x||hit.hitVec.y-POS.getY()!=scalar.y||hit.hitVec.z-POS.getZ()!=scalar.z))throw new AssertionError("Shared ray "+profiles+"/"+ray);if(hit==null)rays.add(JsonNull.INSTANCE);else{JsonArray h=new JsonArray();h.add(hit.sideHit.getIndex());h.add(hit.hitVec.x-POS.getX());h.add(hit.hitVec.y-POS.getY());h.add(hit.hitVec.z-POS.getZ());rays.add(h);}}row.add("rays",rays);out.println(gson.toJson(row));profiles++;
   }
  }
  SegmentedReferenceGolden.check(file,"afc379ea2397f937cebe950fbb706e95980bf1d3e19c59a0db2a3fbb58d9f86a");
  if(bent==0||split==0)throw new AssertionError("Missing actual bent split-collision negative");System.out.println("Actual flat glass contacts PASS profiles="+profiles+" bent="+bent+" split-vs-union="+split+" file="+file);
 }
}

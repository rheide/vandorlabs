package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual specialized joined placement and released surface-to-constituent ray selection. */
public final class CanopyJoinedRayLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static JsonArray point(Vec3d p){JsonArray a=new JsonArray();a.add(Long.toString(Double.doubleToRawLongBits(p.x)));a.add(Long.toString(Double.doubleToRawLongBits(p.y)));a.add(Long.toString(Double.doubleToRawLongBits(p.z)));return a;}
    private static void ray(JsonArray rows,TileEntityCanopy owner,Vec3d start,Vec3d end){JsonObject row=new JsonObject();row.add("start",point(start));row.add("end",point(end));RayTraceResult hit=owner.trace(start,end);if(hit!=null){JsonObject value=new JsonObject();value.add("point",point(hit.hitVec));value.add("pos",pos(hit.getBlockPos()));value.addProperty("face",hit.sideHit.getIndex());row.add("hit",value);}rows.add(row);}
    public static void main(String[] args)throws Exception{List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0,rays=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockCanopy block:blocks)if(block.opening())for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int width:new int[]{1,3,9})for(int motion:new int[]{0,1})for(float pose:new float[]{0,.5F,1}){
            CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("c39352c0-2866-44ed-ad20-82a9931293fc"),"CanopyRayOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=facing.getOpposite().getHorizontalAngle();
            for(int column=0;column<width;column++){BlockPos cell=ROOT.offset(facing.rotateY(),column);world.setBlockState(cell.down(),Blocks.STONE.getDefaultState(),2);ItemStack held=new ItemStack(Item.getItemFromBlock(block),1);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("CanopyMotion",motion);held.setTagCompound(settings);actor.setHeldItem(EnumHand.MAIN_HAND,held);if(held.getItem().onItemUse(actor,world,cell.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)!=EnumActionResult.SUCCESS)throw new AssertionError("Released joined placement refused");}
            TileEntityCanopy owner=TileEntityCanopy.at(world,ROOT).owner();owner.progress=owner.previous=pose;JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("width",width);row.addProperty("motion",motion);row.addProperty("pose",pose);JsonArray members=new JsonArray();List<BlockPos> positions=new ArrayList<>();for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> entry:world.states.entrySet())if(entry.getValue().getBlock()==block)positions.add(entry.getKey());positions.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));for(BlockPos p:positions){TileEntityCanopy member=TileEntityCanopy.at(world,p);JsonObject value=new JsonObject();value.add("pos",pos(p));value.add("saved",CanopySettingsLegacyOracle.tags(member.writeToNBT(new NBTTagCompound())));AxisAlignedBB selection=block.getSelectedBoundingBox(world.getBlockState(p),world,p);JsonArray bounds=new JsonArray();for(double v:new double[]{selection.minX,selection.minY,selection.minZ,selection.maxX,selection.maxY,selection.maxZ})bounds.add(Long.toString(Double.doubleToRawLongBits(v)));value.add("selection",bounds);members.add(value);}row.add("members",members);
            JsonArray queries=new JsonArray();boolean[] columns=new boolean[width];for(CanopyMesh.Face face:owner.faces()){if(!face.group.equals("shell")||face.vertices.length<3)continue;Vec3d a=face.vertices[0],b=face.vertices[1],c=face.vertices[2],local=a.add(b).add(c).scale(1D/3);int column=Math.max(0,Math.min(width-1,(int)Math.floor(local.x)));if(columns[column])continue;Vec3d normal=b.subtract(a).crossProduct(c.subtract(a)).normalize();if(normal.lengthSquared()<1e-8)continue;columns[column]=true;Vec3d p=CanopyMesh.rotate(local,facing).addVector(ROOT.getX(),ROOT.getY(),ROOT.getZ()),n=CanopyMesh.rotate(normal.addVector(.5,0,.5),facing).addVector(-.5,0,-.5);ray(queries,owner,p.add(n.scale(.25)),p.subtract(n.scale(.25)));ray(queries,owner,p.subtract(n.scale(.25)),p.add(n.scale(.25)));ray(queries,owner,p,p.add(n));}row.add("rays",queries);rays+=queries.size();out.println(gson.toJson(row));profiles++;
        }}if(profiles!=144||rays!=1872)throw new AssertionError("Released joined ray coverage");SegmentedReferenceGolden.check(file,"1050b3376e76c47c355047204c0c68bb1311545d3018ecddf5a5c155084dd3a0");System.out.println("Actual joined canopy surface rays PASS profiles="+profiles+" rays="+rays+" file="+file);
    }
}

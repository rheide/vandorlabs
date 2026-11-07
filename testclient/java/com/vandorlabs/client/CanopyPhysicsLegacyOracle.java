package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual tile physical windows/triangle boxes and exact face rays, independently of rendered clearances. */
public final class CanopyPhysicsLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static JsonArray vector(Vec3d v){JsonArray out=new JsonArray();out.add(Double.doubleToRawLongBits(v.x));out.add(Double.doubleToRawLongBits(v.y));out.add(Double.doubleToRawLongBits(v.z));return out;}
    private static JsonArray box(AxisAlignedBB b){JsonArray out=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})out.add(Double.doubleToRawLongBits(v));return out;}
    private static JsonElement hit(RayTraceResult result){if(result==null)return JsonNull.INSTANCE;JsonObject out=new JsonObject();out.add("point",vector(result.hitVec));out.addProperty("face",result.sideHit.getIndex());JsonArray pos=new JsonArray();pos.add(result.getBlockPos().getX());pos.add(result.getBlockPos().getY());pos.add(result.getBlockPos().getZ());out.add("pos",pos);return out;}
    private static void ray(JsonArray rays,TileEntityCanopy tile,Vec3d start,Vec3d end){JsonObject row=new JsonObject();row.add("start",vector(start));row.add("end",vector(end));row.add("hit",hit(tile.trace(start,end)));rays.add(row);}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,rayCount=0;long boxCount=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int profile=0;profile<4;profile++) {
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();world.setBlockState(ROOT,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(ROOT);tile.anchor=ROOT;tile.centeredPlacement=com.vandorlabs.canopy.CanopyVariants.centered(block.kind);tile.hollowPlacement=tile.resizable();NBTTagCompound settings=CanopyMeshLegacyOracle.input(profile);tile.large.read(settings);tile.progress=profile==0?0:profile==2?1:.5F;tile.previous=tile.progress;
                List<AxisAlignedBB> boxes=tile.boxes();AxisAlignedBB bounds=tile.selectionBounds();JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("width",tile.width());row.addProperty("profile",profile);row.addProperty("centered",tile.centeredPlacement);row.addProperty("progress",tile.progress);row.add("settings",CanopySettingsLegacyOracle.tags(settings));row.add("selection",box(bounds));JsonArray physical=new JsonArray();for(AxisAlignedBB b:boxes)physical.add(box(b));row.add("boxes",physical);boxCount+=boxes.size();
                Vec3d center=new Vec3d((bounds.minX+bounds.maxX)/2,(bounds.minY+bounds.maxY)/2,(bounds.minZ+bounds.maxZ)/2);double radius=2+Math.max(bounds.maxX-bounds.minX,Math.max(bounds.maxY-bounds.minY,bounds.maxZ-bounds.minZ));JsonArray rays=new JsonArray();
                for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){Vec3d d=new Vec3d(axis==0?radius*sign:0,axis==1?radius*sign:0,axis==2?radius*sign:0);ray(rays,tile,center.add(d),center.subtract(d));ray(rays,tile,center,center.add(d));}ray(rays,tile,center,center);
                int sampled=0;for(CanopyMesh.Face face:tile.faces()){if(face.vertices.length<3)continue;Vec3d a=CanopyMesh.rotate(face.vertices[0],facing).add(new Vec3d(ROOT)),b=CanopyMesh.rotate(face.vertices[1],facing).add(new Vec3d(ROOT)),c=CanopyMesh.rotate(face.vertices[2],facing).add(new Vec3d(ROOT));Vec3d n=b.subtract(a).crossProduct(c.subtract(a)).normalize();if(n.lengthSquared()==0)continue;Vec3d p=a.add(b).add(c).scale(1D/3);ray(rays,tile,p.add(n.scale(.000001)),p.subtract(n.scale(.000001)));ray(rays,tile,p,p.add(n));ray(rays,tile,a,b);ray(rays,tile,p.add(n.scale(.00000000001)),p.add(b.subtract(a)));if(++sampled==4)break;}
                row.add("rays",rays);rayCount+=rays.size();out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"65341c3cd4dd64665dabbf1aa4ed2a68366bf7981e8586df7d512594b9a14e09");
        System.out.println("Actual standalone canopy physics PASS profiles="+profiles+" boxes="+boxCount+" rays="+rayCount+" file="+file);
    }
}

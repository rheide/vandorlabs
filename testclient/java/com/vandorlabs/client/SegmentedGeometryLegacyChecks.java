package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.physics.RigidTransform;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.*;

/** Actual rigid transforms, two-pixel panel/header rays and world bounds for native targeting. */
public final class SegmentedGeometryLegacyChecks {
    private static double selectedPose;
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public double segmentedSelectionPose(SegmentedDoorTile tile){return selectedPose;}};
        SegmentedDoorBlock block=new SegmentedDoorBlock();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);Gson gson=new Gson();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int cases=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(int height:new int[]{1,3,32})for(int width:new int[]{1,5})for(int column:new java.util.TreeSet<Integer>(java.util.Arrays.asList(0,width-1)))for(int row:new java.util.TreeSet<Integer>(java.util.Arrays.asList(0,height-1)))for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int position=0;position<3;position++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(double progress:new double[]{0,.15,.5,.85,1}){
                int x=cases%3==0?29999872:cases%3==1?-29999872:0,z=cases%3==0?-29999872:cases%3==1?29999872:0;BlockPos origin=new BlockPos(x,100,z),pos=origin.down(row);
                world.setBlockState(origin,block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),2);if(row!=0)world.setBlockState(pos,block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),2);
                SegmentedDoorTile root=SegmentedDoorTile.at(world,origin),tile=SegmentedDoorTile.at(world,pos);SegmentedDoorSettings settings=new SegmentedDoorSettings(direction,straight,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,position);
                for(SegmentedDoorTile part:new SegmentedDoorTile[]{root,tile}){part.topOffset=part==root?0:row;part.height=height;part.column=column;part.columns=width;part.settings=settings;part.from=progress;part.targetOpen=progress>0;part.started=world.getTotalWorldTime();part.duration=960;}
                selectedPose=progress;RigidTransform transform=SegmentedDoorInteractions.transform(tile,progress);JsonObject record=new JsonObject();record.addProperty("height",height);record.addProperty("width",width);record.addProperty("column",column);record.addProperty("row",row);record.addProperty("direction",direction);record.addProperty("straight",straight);record.addProperty("position",position);record.addProperty("face",face.getIndex());record.addProperty("progress",progress);record.add("origin",vector(x,100,z));record.add("matrix",gson.toJsonTree(transform.columnMajor()));record.add("bounds",box(SegmentedDoorInteractions.bounds(tile)));AxisAlignedBB header=SegmentedDoorBlock.headerBounds(tile);if(header!=null)record.add("header",box(header));
                java.util.List<AxisAlignedBB> collision=new java.util.ArrayList<>();block.addCollisionBoxToList(world.getBlockState(pos),world,pos,new AxisAlignedBB(x-100,0,z-100,x+100,200,z+100),collision,null,false);JsonArray boxes=new JsonArray();for(AxisAlignedBB b:collision)boxes.add(box(b));record.add("collisions",boxes);
                double[][] paths={{-1,0,0,2,0,0},{2,0,0,-1,0,0},{.5,-2,0,.5,2,0},{.5,2,0,.5,-2,0},{.5,0,-2,.5,0,2},{.5,0,2,.5,0,-2},{.5,0,0,.5,0,2},{-1,2,-2,-1,2,2},{0,-.5,-2,0,-.5,2}};JsonArray rays=new JsonArray();
                for(double[] path:paths){double[] a=transform.point(path[0],path[1],path[2]),b=transform.point(path[3],path[4],path[5]);RayTraceResult hit=SegmentedDoorInteractions.hit(tile,new Vec3d(a[0],a[1],a[2]),new Vec3d(b[0],b[1],b[2]),progress);JsonObject ray=new JsonObject();ray.add("a",gson.toJsonTree(a));ray.add("b",gson.toJsonTree(b));if(hit!=null){ray.addProperty("face",hit.sideHit.getIndex());ray.add("hit",vector(hit.hitVec.x,hit.hitVec.y,hit.hitVec.z));}rays.add(ray);}
                record.add("rays",rays);out.println(gson.toJson(record));cases++;
            }
        }
        SegmentedReferenceGolden.check(file,"6ead83660b533edeca426e9768a64cfa82c1e5260814b8dd2f671bd7d0c8b771");
        System.out.println("Actual Segmented geometry PASS profiles="+cases+" rays="+(cases*9)+" complete transforms bounds header ordering and far-world arithmetic");
    }
    private static JsonArray vector(double... values){JsonArray result=new JsonArray();for(double v:values)result.add(v);return result;}
    private static JsonArray box(AxisAlignedBB b){return vector(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
}

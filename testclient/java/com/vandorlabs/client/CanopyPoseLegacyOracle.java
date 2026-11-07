package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import java.io.*;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Actual bulk pose (including inverted winding) and legacy vector normalization. */
public final class CanopyPoseLegacyOracle {
    private static Vec3d[] points(int width){return new Vec3d[]{Vec3d.ZERO,new Vec3d(width,width,1),new Vec3d(.125,.25,.96875),new Vec3d(-1E-6,0,0)};}
    private static void point(JsonArray out,Vec3d p){out.add(Double.doubleToRawLongBits(p.x));out.add(Double.doubleToRawLongBits(p.y));out.add(Double.doubleToRawLongBits(p.z));}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,vectors=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(int width:new int[]{1,2,3,5,9})for(Vec3d p:points(width)){JsonObject row=new JsonObject();row.addProperty("kind","vector");JsonArray input=new JsonArray(),normal=new JsonArray();point(input,p);point(normal,p.normalize());row.add("point",input);row.add("normal",normal);out.println(gson.toJson(row));vectors++;}
            for(BlockCanopy block:blocks)for(int width:block.opening()?new int[]{1,3,9}:new int[]{block.width})
                for(int mount=0;mount<4;mount++)for(int hinge=0;hinge<4;hinge++)for(int motion=0;motion<2;motion++)for(boolean slide:new boolean[]{false,true})for(boolean openable:new boolean[]{false,true})for(double progress:new double[]{0,.0625,.5,.9375,1})for(String group:new String[]{"shell","frame","top_border"}) {
                    LargeCanopySettings settings=new LargeCanopySettings();settings.mount=mount;settings.hinge=hinge;settings.motion=motion;settings.slideForward=slide;settings.openable=openable;
                    CanopyMesh.Face face=new CanopyMesh.Face();face.vertices=points(width);face.uv=new double[face.vertices.length][2];face.group=group;face.baseMaterial=face.material="glass";
                    List<CanopyMesh.Face> posed=CanopyPose.faces(Collections.singletonList(face),block.kind,width,settings,progress);
                    JsonObject row=new JsonObject();row.addProperty("kind","pose");row.addProperty("id",block.kind);row.addProperty("width",width);row.addProperty("mount",mount);row.addProperty("hinge",hinge);row.addProperty("motion",motion);row.addProperty("slide",slide);row.addProperty("openable",openable);row.addProperty("progress",progress);row.addProperty("group",group);
                    JsonArray positions=new JsonArray();for(EnumFacing facing:EnumFacing.HORIZONTALS)for(Vec3d vertex:posed.get(0).vertices)point(positions,CanopyMesh.rotate(vertex,facing));
                    row.add("vertices",positions);out.println(gson.toJson(row));profiles++;
                }
        }
        SegmentedReferenceGolden.check(file,"d79b59a1fe01cfe1ee360a51056bb722b4dba62f64e737056c7dae4cf1376583");
        System.out.println("Actual standalone canopy bulk pose PASS profiles="+profiles+" normalization="+vectors+" file="+file);
    }
}

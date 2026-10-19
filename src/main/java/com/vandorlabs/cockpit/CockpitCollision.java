package com.vandorlabs.cockpit;

import com.google.gson.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Narrow conservative boxes for the kit's slanted convex console solids. */
final class CockpitCollision {
    static List<AxisAlignedBB> slices(JsonObject solid){
        List<Vec3d> vertices=new ArrayList<>();
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
        for(JsonElement e:solid.getAsJsonArray("vertices_blocks")){
            JsonArray a=e.getAsJsonArray();Vec3d p=new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
            vertices.add(p);minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);
        }
        boolean x=maxX-minX>=maxZ-minZ;double min=x?minX:minZ,max=x?maxX:maxZ;
        int count=Math.max(1,(int)Math.ceil((max-min)*16));List<AxisAlignedBB> boxes=new ArrayList<>();
        for(int i=0;i<count;i++){
            double lo=min+(max-min)*i/count,hi=min+(max-min)*(i+1)/count;
            double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(JsonElement e:solid.getAsJsonArray("faces")){
                List<Vec3d> face=new ArrayList<>();for(JsonElement index:e.getAsJsonArray())face.add(vertices.get(index.getAsInt()));
                face=clip(clip(face,x,lo,true),x,hi,false);
                for(Vec3d p:face){bounds[0]=Math.min(bounds[0],p.x);bounds[1]=Math.min(bounds[1],p.y);bounds[2]=Math.min(bounds[2],p.z);bounds[3]=Math.max(bounds[3],p.x);bounds[4]=Math.max(bounds[4],p.y);bounds[5]=Math.max(bounds[5],p.z);}
            }
            if(bounds[3]>bounds[0] && bounds[4]>bounds[1] && bounds[5]>bounds[2])boxes.add(new AxisAlignedBB(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]));
        }
        if(boxes.isEmpty())throw new IllegalArgumentException("Empty cockpit convex solid "+solid.get("mesh"));
        return boxes;
    }
    private static List<Vec3d> clip(List<Vec3d> polygon,boolean x,double plane,boolean above){
        List<Vec3d> result=new ArrayList<>();if(polygon.isEmpty())return result;
        Vec3d previous=polygon.get(polygon.size()-1);double a=(x?previous.x:previous.z)-plane;
        for(Vec3d current:polygon){
            double b=(x?current.x:current.z)-plane;boolean inA=above?a>=0:a<=0,inB=above?b>=0:b<=0;
            if(inA!=inB)result.add(previous.add(current.subtract(previous).scale(a/(a-b))));
            if(inB)result.add(current);previous=current;a=b;
        }
        return result;
    }
}

package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.render.ControlMountTransform;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored binary-control components, parsed without client classes or world access. */
public final class NativeMountedControlGeometry {
    private static final Map<String,Pose> POSES=new HashMap<>();
    // Include coordinate grids, occupancy bits, potential six cached face shapes, and source boxes.
    private static final com.google.common.cache.Cache<String,Geometry> SHAPES=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumWeight(8*1024*1024).weigher((String key,Geometry value)->retainedCost(value)).build();
    static int retainedCost(BlockState state,ControlMountSettings mount,int rotation){return retainedCost(geometry(state,mount,rotation));}
    static long cacheCost(){SHAPES.cleanUp();return SHAPES.asMap().values().stream().mapToLong(NativeMountedControlGeometry::retainedCost).sum();}
    static long cachedShapes(){return SHAPES.size();}
    private static int retainedCost(Geometry value){
        long x=value.shape.getCoords(Direction.Axis.X).size(),y=value.shape.getCoords(Direction.Axis.Y).size(),z=value.shape.getCoords(Direction.Axis.Z).size();
        return (int)Math.min(Integer.MAX_VALUE,512+value.boxes.size()*112L+7*(128+16*(x+y+z)+(x*y*z+7)/8));
    }
    public record Pose(List<List<Vec3>> components,List<AABB> supports,AABB support){}
    private record Geometry(List<AABB> boxes,AABB bounds,VoxelShape shape){}
    public static int rotation(BlockState state,int rotation){return state.getValue(NativeMountedSwitchBlock.FACING).getAxis()==Direction.Axis.Y?rotation&3:0;}
    private static String key(BlockState state,int rotation){
        String family=((NativeMountedSwitchBlock)state.getBlock()).isMomentary()?"push_button":"rocker_switch";
        return family+"/facing="+state.getValue(NativeMountedSwitchBlock.FACING).getSerializedName()+",on="+state.getValue(NativeMountedSwitchBlock.ON)+",rotation="+rotation(state,rotation);
    }
    public static Pose pose(BlockState state,int rotation){
        String key=key(state,rotation);synchronized(POSES){return POSES.computeIfAbsent(key,k->load(k,state.getValue(NativeMountedSwitchBlock.FACING)));}
    }
    public static ControlMountTransform<Vec3,AABB> transform(BlockState state,ControlMountSettings mount,int rotation){
        AABB b=pose(state,rotation).support;
        return new ControlMountTransform<>(state.getValue(NativeMountedSwitchBlock.FACING).get3DDataValue(),rotation(state,rotation),mount.height,mount.tilt,mount.direction,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,NativeMountedControlGeometry::point,AABB::new);
    }
    public static List<AABB> boxes(BlockState state,ControlMountSettings mount,int rotation){return geometry(state,mount,rotation).boxes;}
    public static AABB bounds(BlockState state,ControlMountSettings mount,int rotation){return geometry(state,mount,rotation).bounds;}
    public static VoxelShape shape(BlockState state,ControlMountSettings mount,int rotation){return geometry(state,mount,rotation).shape;}
    private static Geometry geometry(BlockState state,ControlMountSettings mount,int rotation){
        String key=key(state,rotation)+"/"+(mount.height+4*(mount.tilt+4*mount.direction));
        Geometry cached=SHAPES.getIfPresent(key);if(cached!=null)return cached;
        {
            Pose pose=pose(state,rotation);ControlMountTransform<Vec3,AABB> transform=transform(state,mount,rotation);List<AABB> boxes=new ArrayList<>();
            for(List<Vec3> component:pose.components){List<Vec3> points=new ArrayList<>(8);for(Vec3 p:component)points.add(transform.transform(p.x,p.y,p.z));boxes.add(hull(points));}
            if(mount.height+4*mount.tilt>0)for(AABB b:pose.supports)boxes.addAll(transform.wedgeBoxes(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
            AABB bounds=boxes.getFirst();VoxelShape shape=Shapes.empty();for(AABB b:boxes){bounds=bounds.minmax(b);shape=Shapes.or(shape,Shapes.create(b));}
            Geometry result=new Geometry(List.copyOf(boxes),bounds,shape);SHAPES.put(key,result);return result;
        }
    }
    private static Pose load(String key,Direction face){
        String[] parts=key.split("/",2);JsonObject variant=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants").getAsJsonObject(parts[1]);
        if(variant==null)throw new IllegalStateException("Missing mounted pose "+key);
        List<List<Vec3>> components=new ArrayList<>();List<AABB> supports=new ArrayList<>();Vec3 normal=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ()),origin=new Vec3(.5,.5,.5).subtract(normal.scale(.5));
        for(JsonElement value:elements(variant.get("model").getAsString(),0)){
            JsonObject e=value.getAsJsonObject();Vec3 lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));List<Vec3> points=new ArrayList<>(8),bottom=new ArrayList<>();
            for(double x:new double[]{lo.x,hi.x})for(double y:new double[]{lo.y,hi.y})for(double z:new double[]{lo.z,hi.z}){
                Vec3 p=new Vec3(x,y,z);
                if(e.has("rotation")){JsonObject r=e.getAsJsonObject("rotation");p=rotate(p,vector(r.getAsJsonArray("origin")),"xyz".indexOf(r.get("axis").getAsString()),r.get("angle").getAsDouble());}
                // Presence matters: even a zero rotation repeats the legacy subtract/add arithmetic.
                if(variant.has("x"))p=rotate(p,new Vec3(.5,.5,.5),0,-variant.get("x").getAsDouble());
                if(variant.has("y"))p=rotate(p,new Vec3(.5,.5,.5),1,-variant.get("y").getAsDouble());
                points.add(p);if(Math.abs(p.subtract(origin).dot(normal))<1e-6)bottom.add(p);
            }
            components.add(List.copyOf(points));if(bottom.size()>=4)supports.add(hull(bottom));
        }
        if(supports.isEmpty())throw new IllegalStateException("Control has no support surface "+key);
        AABB support=supports.getFirst();for(AABB b:supports)support=support.minmax(b);
        return new Pose(List.copyOf(components),List.copyOf(supports),support);
    }
    private static AABB hull(List<Vec3> points){double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;for(Vec3 p:points){x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);}return new AABB(x,y,z,X,Y,Z);}
    private static Vec3 point(double x,double y,double z){return new Vec3(x==0?0:x,y==0?0:y,z==0?0:z);}
    private static Vec3 vector(JsonArray a){return point(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private static Vec3 rotate(Vec3 point,Vec3 origin,int axis,double degrees){double[] p={point.x-origin.x,point.y-origin.y,point.z-origin.z};int a=(axis+1)%3,b=(axis+2)%3;double t=Math.toRadians(degrees),u=p[a],v=p[b];p[a]=u*Math.cos(t)-v*Math.sin(t);p[b]=u*Math.sin(t)+v*Math.cos(t);return point(p[0]+origin.x,p[1]+origin.y,p[2]+origin.z);}
    private static JsonArray elements(String model,int depth){if(depth>16)throw new IllegalStateException("Cyclic control parent "+model);String[] name=model.split(":",2);JsonObject root=json("/assets/"+name[0]+"/models/"+(name[1].startsWith("block/")?"":"block/")+name[1]+".json");return root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString(),depth+1);}
    private static JsonObject json(String path){try(InputStream stream=NativeMountedControlGeometry.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing control resource "+path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(path,e);}}
    private NativeMountedControlGeometry(){}
}

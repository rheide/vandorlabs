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

/** Authored mounted-control components, parsed without client classes or world access. */
public final class NativeMountedControlGeometry {
    private static final Map<String,Pose> POSES=new HashMap<>();
    private static final int SHAPE_BUDGET=8*1024*1024;
    // SliceShape/SubShape reference the parent occupancy and coordinate lists. Reserve the full
    // bounded face/edge/corner view closure (6+24+48 wrappers), not seven copies of the3D grid.
    private static final int FACE_VIEWS=80*160;
    // Selection is the released single enclosing AABB, never the detailed collision voxel grid.
    private static final int OUTLINE_BUDGET=2*1024*1024;
    private static final com.google.common.cache.Cache<String,VoxelShape> OUTLINES=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumWeight(OUTLINE_BUDGET).weigher((String key,VoxelShape value)->1024+FACE_VIEWS+2*key.length()).build();
    public static VoxelShape outline(BlockState state,ControlMountSettings mount,int variant){
        boolean power=state.getBlock() instanceof NativePowerLeverBlock,signal=state.getBlock() instanceof NativeSignalControlBlock;
        String key=(power?powerKey(state,variant):signal?"signal:"+NativeSignalControlPoses.key(state,variant):key(state,variant))+"/"+(mount.height+4*(mount.tilt+4*mount.direction));
        VoxelShape cached=OUTLINES.getIfPresent(key);if(cached!=null)return cached;
        Geometry collision=SHAPES.getIfPresent(key);AABB bounds;
        if(collision!=null)bounds=collision.bounds;
        else {
            Pose pose=power?powerPose(state,variant):signal?signalPose(state,variant):pose(state,variant);
            var transform=power?powerTransform(state,mount,variant):signal?signalTransform(state,mount,variant):transform(state,mount,variant);
            bounds=enclosing(componentBoxes(pose,transform,mount));
        }
        VoxelShape result=Shapes.create(bounds);OUTLINES.put(key,result);return result;
    }

    // Coordinate storage, one occupancy bitset, component boxes, and cached view wrappers.
    private static final com.google.common.cache.Cache<String,Geometry> SHAPES=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumWeight(SHAPE_BUDGET).weigher((String key,Geometry value)->retainedCost(value)).build();
    static int retainedCost(BlockState state,ControlMountSettings mount,int rotation){return retainedCost(geometry(state,mount,rotation));}
    static long cacheCost(){SHAPES.cleanUp();return SHAPES.asMap().values().stream().mapToLong(NativeMountedControlGeometry::retainedCost).sum();}
    static long cachedShapes(){return SHAPES.size();}
    private static int retainedCost(Geometry value){
        return NativeVoxelShapes.retainedCost(value.shape,value.boxes.size());
    }
    public record Pose(List<List<Vec3>> components,List<AABB> supports,AABB support){}
    private record Geometry(List<AABB> boxes,AABB bounds,VoxelShape shape){}
    public static int rotation(BlockState state,int rotation){return state.getValue(NativeMountedSwitchBlock.FACING).getAxis()==Direction.Axis.Y?rotation&3:0;}
    /** Authored mounted families sharing the six-face switch state properties. */
    public interface Profile { String mountedFamily(); }
    private static String key(BlockState state,int rotation){
        String family=state.getBlock() instanceof Profile profile?profile.mountedFamily():((NativeMountedSwitchBlock)state.getBlock()).isMomentary()?"push_button":"rocker_switch";
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
    private static int checkedSize(int size){if(!com.vandorlabs.persistence.PowerLeverSize.valid(size))throw new IllegalArgumentException("Invalid lever size "+size);return size;}
    private static String powerKey(BlockState state,int size){
        if(!com.vandorlabs.persistence.PowerLeverSize.valid(size))throw new IllegalArgumentException("Invalid lever size "+size);
        NativePowerLeverBlock block=(NativePowerLeverBlock)state.getBlock();String family=block.isTwin()?(size==0?"small_power_lever":"large_power_lever"):(size==0?"compact_power_lever":"industrial_power_lever");
        return family+"/facing="+state.getValue(NativePowerLeverBlock.FACING).getSerializedName()+",floor="+state.getValue(NativePowerLeverBlock.FLOOR)+",powered="+state.getValue(NativePowerLeverBlock.POWERED);
    }
    public static Direction powerFace(BlockState state){return state.getValue(NativePowerLeverBlock.FLOOR)?Direction.UP:state.getValue(NativePowerLeverBlock.FACING);}
    public static int powerRotation(BlockState state){return state.getValue(NativePowerLeverBlock.FLOOR)?state.getValue(NativePowerLeverBlock.FACING).get2DDataValue():0;}
    public static Pose powerPose(BlockState state,int size){String key=powerKey(state,size);synchronized(POSES){return POSES.computeIfAbsent(key,k->load(k,powerFace(state)));}}
    public static ControlMountTransform<Vec3,AABB> powerTransform(BlockState state,ControlMountSettings mount,int size){
        AABB b=powerPose(state,size).support;return new ControlMountTransform<>(powerFace(state).get3DDataValue(),powerRotation(state),mount.height,mount.tilt,mount.direction,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,NativeMountedControlGeometry::point,AABB::new);
    }
    public static List<AABB> powerBoxes(BlockState state,ControlMountSettings mount,int size){return geometry(state,mount,0,checkedSize(size)).boxes;}
    public static AABB powerBounds(BlockState state,ControlMountSettings mount,int size){return geometry(state,mount,0,checkedSize(size)).bounds;}
    public static VoxelShape powerShape(BlockState state,ControlMountSettings mount,int size){return geometry(state,mount,0,checkedSize(size)).shape;}
    static int powerRetainedCost(BlockState state,ControlMountSettings mount,int size){return retainedCost(geometry(state,mount,0,checkedSize(size)));}
    private static Geometry geometry(BlockState state,ControlMountSettings mount,int rotation){return geometry(state,mount,rotation,-1);}
    private static Geometry geometry(BlockState state,ControlMountSettings mount,int rotation,int size){
        String key=(size<0?key(state,rotation):powerKey(state,size))+"/"+(mount.height+4*(mount.tilt+4*mount.direction));
        Geometry cached=SHAPES.getIfPresent(key);if(cached!=null)return cached;
        {
            Pose pose=size<0?pose(state,rotation):powerPose(state,size);ControlMountTransform<Vec3,AABB> transform=size<0?transform(state,mount,rotation):powerTransform(state,mount,size);
            return assemble(key,pose,transform,mount);
        }
    }
    private static List<AABB> componentBoxes(Pose pose,ControlMountTransform<Vec3,AABB> transform,ControlMountSettings mount){
        List<AABB> boxes=new ArrayList<>();
        for(List<Vec3> component:pose.components){List<Vec3> points=new ArrayList<>(8);for(Vec3 p:component)points.add(transform.transform(p.x,p.y,p.z));boxes.add(hull(points));}
        if(mount.height+4*mount.tilt>0)for(AABB b:pose.supports)boxes.addAll(transform.wedgeBoxes(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        return boxes;
    }
    private static AABB enclosing(List<AABB> boxes){AABB bounds=boxes.getFirst();for(AABB b:boxes)bounds=bounds.minmax(b);return bounds;}
    private static Geometry assemble(String key,Pose pose,ControlMountTransform<Vec3,AABB> transform,ControlMountSettings mount){
        List<AABB> boxes=componentBoxes(pose,transform,mount);AABB bounds=enclosing(boxes);VoxelShape shape=NativeVoxelShapes.union(boxes,SHAPE_BUDGET);Geometry result=new Geometry(List.copyOf(boxes),bounds,shape);SHAPES.put(key,result);return result;
    }
    public static Pose signalPose(BlockState state,int rotation){String key="signal:"+NativeSignalControlPoses.key(state,rotation);synchronized(POSES){return POSES.computeIfAbsent(key,k->{
        JsonObject root=json(NativeSignalControlPoses.modelPath(state,rotation));JsonArray values=root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString(),1);List<List<Vec3>> components=components(values,point->NativeSignalControlPoses.orientPoint(point,state,rotation));
        AABB support=NativeSignalControlPoses.support(state,rotation);return new Pose(List.copyOf(components),List.of(support),support);
    });}}
    public static ControlMountTransform<Vec3,AABB> signalTransform(BlockState state,ControlMountSettings mount,int rotation){AABB b=signalPose(state,rotation).support;return new ControlMountTransform<>(NativeSignalControlPoses.face(state).get3DDataValue(),NativeSignalControlPoses.rotation(state,rotation),mount.height,mount.tilt,mount.direction,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,NativeMountedControlGeometry::point,AABB::new);}
    private static Geometry signalGeometry(BlockState state,ControlMountSettings mount,int rotation){String key="signal:"+NativeSignalControlPoses.key(state,rotation)+"/"+(mount.height+4*(mount.tilt+4*mount.direction));Geometry cached=SHAPES.getIfPresent(key);if(cached!=null)return cached;return assemble(key,signalPose(state,rotation),signalTransform(state,mount,rotation),mount);}
    public static VoxelShape signalShape(BlockState state,ControlMountSettings mount,int rotation){return signalGeometry(state,mount,rotation).shape;}
    public static List<AABB> signalBoxes(BlockState state,ControlMountSettings mount,int rotation){return signalGeometry(state,mount,rotation).boxes;}
    public static AABB signalBounds(BlockState state,ControlMountSettings mount,int rotation){return signalGeometry(state,mount,rotation).bounds;}
    record Authored(JsonArray elements,int x,int y){}
    /** Bake-only owned parse result; never published to section workers or retained in common caches. */
    static Authored authored(BlockState state,int rotation,int size){String[] parts=(state.getBlock() instanceof NativePowerLeverBlock?powerKey(state,size):state.getBlock() instanceof NativeSignalControlPoses.Profile profile?profile.controlKind()+"/facing="+NativeSignalControlPoses.face(state).getSerializedName()+",level="+state.getValue(NativeSignalControlPoses.LEVEL)+",on="+state.getValue(NativeSignalControlBlock.ON)+",rotation="+NativeSignalControlPoses.rotation(state,rotation):key(state,rotation)).split("/",2);var variant=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants").getAsJsonObject(parts[1]);return new Authored(elements(variant.get("model").getAsString(),0),variant.has("x")?variant.get("x").getAsInt():0,variant.has("y")?variant.get("y").getAsInt():0);}
    static Authored authoredItem(String id){return new Authored(elements("vandorlabs:item/"+id,0),0,0);}
    /** One authored element parser; profiles only add their post-element orientation. */
    private static List<List<Vec3>> components(JsonArray values,java.util.function.UnaryOperator<Vec3> orientation){
        List<List<Vec3>> components=new ArrayList<>();for(JsonElement value:values){JsonObject e=value.getAsJsonObject();Vec3 lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));List<Vec3> points=new ArrayList<>(8);
            for(double x:new double[]{lo.x,hi.x})for(double y:new double[]{lo.y,hi.y})for(double z:new double[]{lo.z,hi.z}){Vec3 p=new Vec3(x,y,z);if(e.has("rotation")){JsonObject r=e.getAsJsonObject("rotation");p=rotate(p,vector(r.getAsJsonArray("origin")),"xyz".indexOf(r.get("axis").getAsString()),r.get("angle").getAsDouble());}points.add(orientation.apply(p));}components.add(List.copyOf(points));
        }return List.copyOf(components);
    }
    private static Pose load(String key,Direction face){
        String[] parts=key.split("/",2);JsonObject variant=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants").getAsJsonObject(parts[1]);if(variant==null)throw new IllegalStateException("Missing mounted pose "+key);
        List<List<Vec3>> components=components(elements(variant.get("model").getAsString(),0),p->{
            // Presence matters: even a zero rotation repeats the legacy subtract/add arithmetic.
            if(variant.has("x"))p=rotate(p,new Vec3(.5,.5,.5),0,-variant.get("x").getAsDouble());if(variant.has("y"))p=rotate(p,new Vec3(.5,.5,.5),1,-variant.get("y").getAsDouble());return p;
        });
        List<AABB> supports=new ArrayList<>();Vec3 normal=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ()),origin=new Vec3(.5,.5,.5).subtract(normal.scale(.5));for(List<Vec3> points:components){List<Vec3> bottom=new ArrayList<>();for(Vec3 p:points)if(Math.abs(p.subtract(origin).dot(normal))<1e-6)bottom.add(p);if(bottom.size()>=4)supports.add(hull(bottom));}
        if(supports.isEmpty())throw new IllegalStateException("Control has no support surface "+key);AABB support=supports.getFirst();for(AABB box:supports)support=support.minmax(box);return new Pose(components,List.copyOf(supports),support);
    }
    private static AABB hull(List<Vec3> points){double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;for(Vec3 p:points){x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);}return new AABB(x,y,z,X,Y,Z);}
    private static Vec3 point(double x,double y,double z){return new Vec3(x==0?0:x,y==0?0:y,z==0?0:z);}
    private static Vec3 vector(JsonArray a){return point(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private static Vec3 rotate(Vec3 point,Vec3 origin,int axis,double degrees){double[] p={point.x-origin.x,point.y-origin.y,point.z-origin.z};int a=(axis+1)%3,b=(axis+2)%3;double t=Math.toRadians(degrees),u=p[a],v=p[b];p[a]=u*Math.cos(t)-v*Math.sin(t);p[b]=u*Math.sin(t)+v*Math.cos(t);return point(p[0]+origin.x,p[1]+origin.y,p[2]+origin.z);}
    private static JsonArray elements(String model,int depth){if(depth>16)throw new IllegalStateException("Cyclic control parent "+model);String[] name=model.split(":",2);JsonObject root=json("/assets/"+name[0]+"/models/"+(name[1].startsWith("block/")||name[1].startsWith("item/")?"":"block/")+name[1]+".json");return root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString(),depth+1);}
    private static JsonObject json(String path){try(InputStream stream=NativeMountedControlGeometry.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing control resource "+path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(path,e);}}
    private NativeMountedControlGeometry(){}
}

package com.vandorlabs.neoforge.control;

import com.google.gson.*;
import com.vandorlabs.render.ControlMount;
import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.render.ControlMount.Box;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Canonical authored model poses and the shared mounting transform, with native voxel conversion. */
public final class ControlGeometry {
    public static final ModelProperty<Integer> MOUNT=new ModelProperty<>(),SIZE=new ModelProperty<>();
    private static final Map<String,Pose> POSES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,List<com.vandorlabs.render.ControlPose.Element>> ELEMENTS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,VoxelShape> SHAPES=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,VoxelShape> entry){return size()>128;}});
    private static long builds,buildNanos,maxBuildNanos,maxGridCells,maxInputBoxes,maxRetainedBoxes;
    public static synchronized String buildStats(){return "builds="+builds+" totalMs="+buildNanos/1_000_000+" maxMs="+maxBuildNanos/1_000_000+" maxGridCells="+maxGridCells+" maxInputBoxes="+maxInputBoxes+" maxRetainedBoxes="+maxRetainedBoxes+" cached="+SHAPES.size();}
    public record Pose(List<List<Point>> components,List<Box> supports,Box support){}
    public static int mountCode(MountedControlEntity tile){return tile.baseHeight()+4*(tile.baseTilt()+4*tile.tiltDirection());}
    public static net.minecraft.core.Direction face(BlockState state){return ((MountedControlBlock)state.getBlock()).outward(state);}
    public static int rotation(BlockState state){return ((MountedControlBlock)state.getBlock()).mountRotation(state);}
    public static String resourceKey(BlockState state,int size){
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if(state.getBlock() instanceof MountedPowerLeverBlock power){id=power.twin?(size==0?"small_power_lever":"large_power_lever"):(size==0?"compact_power_lever":"industrial_power_lever");return id+"/facing="+state.getValue(MountedPowerLeverBlock.HEADING).getName()+",floor="+state.getValue(MountedPowerLeverBlock.FLOOR)+",powered="+state.getValue(MountedPowerLeverBlock.POWERED);}
        return id+"/facing="+state.getValue(MountedControlBlock.FACING).getName()+(state.getBlock() instanceof MountedSignalControlBlock?",level="+state.getValue(MountedSignalControlBlock.LEVEL):"")+",on="+state.getValue(MountedControlBlock.ON)+",rotation="+state.getValue(MountedControlBlock.ROTATION);
    }
    private static String key(BlockState state){return resourceKey(state,state.getBlock() instanceof MountedPowerLeverBlock p?p.defaultSize:0);}
    public static Pose pose(BlockState state){return pose(state,state.getBlock() instanceof MountedPowerLeverBlock p?p.defaultSize:0);}
    public static Pose pose(BlockState state,int size){return POSES.computeIfAbsent(resourceKey(state,size),key->{
        String[] parts=key.split("/",2);JsonObject variant=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants").getAsJsonObject(parts[1]);if(variant==null)throw new IllegalStateException("Missing control pose "+key);
        List<com.vandorlabs.render.ControlPose.Element> definitions=new ArrayList<>();
        for(JsonElement value:elements(variant.get("model").getAsString())){JsonObject e=value.getAsJsonObject();Point lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));JsonObject r=e.has("rotation")?e.getAsJsonObject("rotation"):null;definitions.add(new com.vandorlabs.render.ControlPose.Element(new Box(lo.x,lo.y,lo.z,hi.x,hi.y,hi.z),r==null?null:vector(r.getAsJsonArray("origin")),r==null?0:"xyz".indexOf(r.get("axis").getAsString()),r==null?0:r.get("angle").getAsDouble()));}
        var pose=new com.vandorlabs.render.ControlPose(definitions,face(state).get3DDataValue(),variant.has("x")?variant.get("x").getAsDouble():0,variant.has("y")?variant.get("y").getAsDouble():0);return new Pose(pose.components,pose.supports,state.getBlock() instanceof MountedSignalControlBlock signal?signalSupport(state,signal):pose.support);
    });}
    public static List<com.vandorlabs.render.ControlPose.Element> signalElements(BlockState state){String path=com.vandorlabs.render.SignalControlGeometry.modelPath(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(),face(state).get3DDataValue(),rotation(state),state.getValue(MountedSignalControlBlock.LEVEL));return ELEMENTS.computeIfAbsent(path,key->definitions(json(path).getAsJsonArray("elements")));}
    private static List<com.vandorlabs.render.ControlPose.Element> definitions(JsonArray array){List<com.vandorlabs.render.ControlPose.Element> result=new ArrayList<>();for(JsonElement value:array){JsonObject e=value.getAsJsonObject();Point lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));JsonObject r=e.has("rotation")?e.getAsJsonObject("rotation"):null;result.add(new com.vandorlabs.render.ControlPose.Element(new Box(lo.x,lo.y,lo.z,hi.x,hi.y,hi.z),r==null?null:vector(r.getAsJsonArray("origin")),r==null?0:"xyz".indexOf(r.get("axis").getAsString()),r==null?0:r.get("angle").getAsDouble()));}return List.copyOf(result);}
    private static Box signalSupport(BlockState state,MountedSignalControlBlock signal){return com.vandorlabs.render.SignalControlGeometry.supportBounds(BuiltInRegistries.BLOCK.getKey(signal).getPath(),face(state).get3DDataValue(),rotation(state));}
    public static ControlMount transform(BlockState state,int mount){return transform(state,mount,state.getBlock() instanceof MountedPowerLeverBlock p?p.defaultSize:0);}
    public static ControlMount transform(BlockState state,int mount,int size){return new ControlMount(face(state).get3DDataValue(),rotation(state),mount%4,mount/4%4,mount/16,pose(state,size).support);}
    public static VoxelShape shape(BlockState state,BlockGetter level,BlockPos pos,boolean collision){MountedControlEntity tile=level.getBlockEntity(pos) instanceof MountedControlEntity value?value:null;return shape(state,tile==null?0:mountCode(tile),tile==null?state.getBlock() instanceof MountedPowerLeverBlock p?p.defaultSize:0:tile.powerLeverSize(),collision);}
    public static VoxelShape shape(BlockState state,int mount,int size,boolean collision){return SHAPES.computeIfAbsent(resourceKey(state,size)+"/"+mount+"/"+collision,key->{Pose pose=pose(state,size);ControlMount transform=transform(state,mount,size);List<Box> boxes=new ArrayList<>();if(state.getBlock() instanceof MountedSignalControlBlock){boxes.addAll(com.vandorlabs.render.SignalControlGeometry.collision(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(),face(state).get3DDataValue(),rotation(state),mount%4,mount/4%4,mount/16,signalElements(state)));}else{for(List<Point> component:pose.components){List<Point> points=new ArrayList<>();for(Point p:component)points.add(transform.transform(p));boxes.add(ControlMount.hull(points));}if(mount%16>0){for(Box support:pose.supports)boxes.addAll(transform.wedgeBoxes(support));}}if(!collision){List<Point> all=new ArrayList<>();for(Box b:boxes)all.addAll(ControlMount.corners(b));return shape(ControlMount.hull(all));}return union(boxes);});}
    /** Exact containment removes authored detail already inside another collision box. */
    private static VoxelShape union(List<Box> input){
        long started=System.nanoTime();List<Box> retained=new ArrayList<>();
        outer:for(int i=0;i<input.size();i++){Box box=input.get(i);if(box.minX>=box.maxX||box.minY>=box.maxY||box.minZ>=box.maxZ)continue;for(int j=0;j<input.size();j++){if(i==j)continue;Box other=input.get(j);if(other.minX<=box.minX&&other.minY<=box.minY&&other.minZ<=box.minZ&&other.maxX>=box.maxX&&other.maxY>=box.maxY&&other.maxZ>=box.maxZ){boolean equal=other.minX==box.minX&&other.minY==box.minY&&other.minZ==box.minZ&&other.maxX==box.maxX&&other.maxY==box.maxY&&other.maxZ==box.maxZ;if(!equal||j<i)continue outer;}}retained.add(box);}
        List<VoxelShape> shapes=new ArrayList<>();java.util.TreeSet<Double> xs=new java.util.TreeSet<>(),ys=new java.util.TreeSet<>(),zs=new java.util.TreeSet<>();for(Box b:retained){shapes.add(shape(b));xs.add(b.minX);xs.add(b.maxX);ys.add(b.minY);ys.add(b.maxY);zs.add(b.minZ);zs.add(b.maxZ);}
        // Balanced unoptimized unions avoid repeatedly decomposing and rebuilding the entire mesh.
        while(shapes.size()>1){List<VoxelShape> next=new ArrayList<>((shapes.size()+1)/2);for(int i=0;i<shapes.size();i+=2)next.add(i+1==shapes.size()?shapes.get(i):Shapes.joinUnoptimized(shapes.get(i),shapes.get(i+1),BooleanOp.OR));shapes=next;}
        VoxelShape result=shapes.isEmpty()?Shapes.empty():shapes.get(0).optimize();long elapsed=System.nanoTime()-started;synchronized(ControlGeometry.class){builds++;buildNanos+=elapsed;maxBuildNanos=Math.max(maxBuildNanos,elapsed);maxGridCells=Math.max(maxGridCells,(long)Math.max(0,xs.size()-1)*Math.max(0,ys.size()-1)*Math.max(0,zs.size()-1));maxInputBoxes=Math.max(maxInputBoxes,input.size());maxRetainedBoxes=Math.max(maxRetainedBoxes,retained.size());}return result;
    }
    private static VoxelShape shape(Box b){return Shapes.box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static JsonObject json(String path){try(InputStream stream=ControlGeometry.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing control resource "+path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException error){throw new IllegalStateException(path,error);}}
    private static JsonArray elements(String model){String[] id=model.split(":",2);JsonObject root=json("/assets/"+id[0]+"/models/"+(id[1].startsWith("block/")?"":"block/")+id[1]+".json");return root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString());}
    private static Point vector(JsonArray a){return new Point(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private ControlGeometry(){}
}

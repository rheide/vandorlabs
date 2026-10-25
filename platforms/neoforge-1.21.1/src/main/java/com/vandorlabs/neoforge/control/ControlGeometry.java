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
    public static final ModelProperty<Integer> MOUNT=new ModelProperty<>();
    private static final Map<String,Pose> POSES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    public record Pose(List<List<Point>> components,List<Box> supports,Box support){}
    public static int mountCode(MountedControlEntity tile){return tile.baseHeight()+4*(tile.baseTilt()+4*tile.tiltDirection());}
    private static String key(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()+"/facing="+state.getValue(MountedControlBlock.FACING).getName()+",on="+state.getValue(MountedControlBlock.ON)+",rotation="+state.getValue(MountedControlBlock.ROTATION);}
    public static Pose pose(BlockState state){return POSES.computeIfAbsent(key(state),key->{
        String[] parts=key.split("/",2);JsonObject variant=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants").getAsJsonObject(parts[1]);if(variant==null)throw new IllegalStateException("Missing control pose "+key);
        List<com.vandorlabs.render.ControlPose.Element> definitions=new ArrayList<>();
        for(JsonElement value:elements(variant.get("model").getAsString())){JsonObject e=value.getAsJsonObject();Point lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));JsonObject r=e.has("rotation")?e.getAsJsonObject("rotation"):null;definitions.add(new com.vandorlabs.render.ControlPose.Element(new Box(lo.x,lo.y,lo.z,hi.x,hi.y,hi.z),r==null?null:vector(r.getAsJsonArray("origin")),r==null?0:"xyz".indexOf(r.get("axis").getAsString()),r==null?0:r.get("angle").getAsDouble()));}
        var pose=new com.vandorlabs.render.ControlPose(definitions,state.getValue(MountedControlBlock.FACING).get3DDataValue(),variant.has("x")?variant.get("x").getAsDouble():0,variant.has("y")?variant.get("y").getAsDouble():0);return new Pose(pose.components,pose.supports,pose.support);
    });}
    public static ControlMount transform(BlockState state,int mount){return new ControlMount(state.getValue(MountedControlBlock.FACING).get3DDataValue(),state.getValue(MountedControlBlock.ROTATION),mount%4,mount/4%4,mount/16,pose(state).support);}
    public static VoxelShape shape(BlockState state,BlockGetter level,BlockPos pos,boolean collision){int mount=level.getBlockEntity(pos) instanceof MountedControlEntity tile?mountCode(tile):0;return SHAPES.computeIfAbsent(key(state)+"/"+mount+"/"+collision,key->{Pose pose=pose(state);ControlMount transform=transform(state,mount);List<Box> boxes=new ArrayList<>();for(List<Point> component:pose.components){List<Point> points=new ArrayList<>();for(Point p:component)points.add(transform.transform(p));boxes.add(ControlMount.hull(points));}if(mount%16>0)for(Box support:pose.supports)boxes.addAll(transform.wedgeBoxes(support));if(!collision){List<Point> all=new ArrayList<>();for(Box b:boxes)all.addAll(ControlMount.corners(b));return shape(ControlMount.hull(all));}VoxelShape result=Shapes.empty();for(Box b:boxes)result=Shapes.or(result,shape(b));return result;});}
    private static VoxelShape shape(Box b){return Shapes.box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static JsonObject json(String path){try(InputStream stream=ControlGeometry.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing control resource "+path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException error){throw new IllegalStateException(path,error);}}
    private static JsonArray elements(String model){String[] id=model.split(":",2);JsonObject root=json("/assets/"+id[0]+"/models/"+(id[1].startsWith("block/")?"":"block/")+id[1]+".json");return root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString());}
    private static Point vector(JsonArray a){return new Point(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private ControlGeometry(){}
}

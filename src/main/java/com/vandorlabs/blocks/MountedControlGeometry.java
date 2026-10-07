package com.vandorlabs.blocks;

import com.google.gson.*;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native binary-control components share the throttle's upright extension and tilt transform. */
public final class MountedControlGeometry {
    public static final IUnlistedProperty<Integer> MOUNT=ProgrammableHousingState.integer("binary_control_mount");
    private static final Map<String,Pose> POSES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,List<AxisAlignedBB>> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean supports(Block block){return block instanceof BlockSignalControl || block instanceof BlockIndustrialLever || block instanceof BlockMountedSwitch;}
    public static int mount(IBlockAccess world,BlockPos pos){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityRedstoneChannel))return 0;
        TileEntityRedstoneChannel t=(TileEntityRedstoneChannel)raw;return t.getBaseHeight()+4*(t.getBaseTilt()+4*t.getTiltDirection());
    }
    public static EnumFacing face(IBlockState state){return state.getBlock() instanceof BlockIndustrialLever?state.getValue(BlockIndustrialLever.FLOOR)?EnumFacing.UP:state.getValue(BlockIndustrialLever.FACING):state.getValue(BlockVandorSwitch.FACING);}
    public static int rotation(IBlockState state){return state.getBlock() instanceof BlockIndustrialLever?state.getValue(BlockIndustrialLever.FLOOR)?state.getValue(BlockIndustrialLever.FACING).getHorizontalIndex():0:state.getValue(BlockVandorSwitch.ROTATION);}
    public static int size(IBlockState state){if(!(state.getBlock() instanceof BlockIndustrialLever))return 0;Integer size=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(BlockIndustrialLever.SIZE):null;return size!=null?size:state.getBlock() instanceof BlockIndustrialLever?((BlockIndustrialLever)state.getBlock()).defaultSize():0;}
    private static String key(IBlockState state,int size){
        if(state.getBlock() instanceof BlockIndustrialLever){
            String id=state.getBlock() instanceof BlockTwinPowerLever?(size==0?"small_power_lever":"large_power_lever"):(size==0?"compact_power_lever":"industrial_power_lever");
            return id+"/facing="+state.getValue(BlockIndustrialLever.FACING).getName()+",floor="+state.getValue(BlockIndustrialLever.FLOOR)+",powered="+state.getValue(BlockIndustrialLever.POWERED);
        }
        return state.getBlock().getRegistryName().getResourcePath()+"/facing="+face(state).getName()+",on="+state.getValue(BlockVandorSwitch.ON)+",rotation="+state.getValue(BlockVandorSwitch.ROTATION);
    }
    public static Pose nativePose(IBlockState state,int size){return POSES.computeIfAbsent(key(state,size),key->{
        String[] parts=key.split("/",2);JsonObject variants=json("/assets/vandorlabs/blockstates/"+parts[0]+".json").getAsJsonObject("variants");JsonObject variant=variants.getAsJsonObject(parts[1]);
        if(variant==null)throw new IllegalStateException("Missing mounted pose "+key);
        String model=variant.get("model").getAsString();JsonArray elements=elements(model);List<List<Vec3d>> components=new ArrayList<>();List<AxisAlignedBB> supports=new ArrayList<>();
        EnumFacing face=face(state);Vec3d normal=new Vec3d(face.getDirectionVec()),origin=new Vec3d(.5,.5,.5).subtract(normal.scale(.5));
        for(JsonElement value:elements){JsonObject e=value.getAsJsonObject();Vec3d lo=vector(e.getAsJsonArray("from")),hi=vector(e.getAsJsonArray("to"));List<Vec3d> points=new ArrayList<>(),bottom=new ArrayList<>();
            for(double x:new double[]{lo.x,hi.x})for(double y:new double[]{lo.y,hi.y})for(double z:new double[]{lo.z,hi.z}){
                Vec3d p=new Vec3d(x,y,z);
                if(e.has("rotation")){JsonObject r=e.getAsJsonObject("rotation");p=rotate(p,vector(r.getAsJsonArray("origin")),"xyz".indexOf(r.get("axis").getAsString()),r.get("angle").getAsDouble());}
                if(variant.has("x"))p=rotate(p,new Vec3d(.5,.5,.5),0,-variant.get("x").getAsDouble());
                if(variant.has("y"))p=rotate(p,new Vec3d(.5,.5,.5),1,-variant.get("y").getAsDouble());
                points.add(p);if(Math.abs(p.subtract(origin).dotProduct(normal))<1e-6)bottom.add(p);
            }
            components.add(Collections.unmodifiableList(points));if(bottom.size()>=4)supports.add(SignalControlShape.hull(bottom));
        }
        if(supports.isEmpty())throw new IllegalStateException("Control has no support surface "+key);
        return new Pose(components,supports);
    });}
    public static SignalControlMount transform(IBlockState state,int size,int mount){return new SignalControlMount(face(state),rotation(state),mount%4,mount/4%4,mount/16,nativePose(state,size).support);}
    public static List<AxisAlignedBB> boxes(IBlockState state,int size,int mount){return SHAPES.computeIfAbsent(key(state,size)+"/"+mount,k->{
        Pose pose=nativePose(state,size);SignalControlMount transform=transform(state,size,mount);List<AxisAlignedBB> result=new ArrayList<>();
        for(List<Vec3d> component:pose.components){List<Vec3d> points=new ArrayList<>();for(Vec3d p:component)points.add(transform.transform(p));result.add(SignalControlShape.hull(points));}
        if(mount%16>0)for(AxisAlignedBB support:pose.supports)result.addAll(transform.wedgeBoxes(support));return Collections.unmodifiableList(result);
    });}
    public static List<AxisAlignedBB> boxes(IBlockState state,IBlockAccess world,BlockPos pos){
        if(world.getTileEntity(pos)!=null)state=state.getBlock().getActualState(state,world,pos);int size=state.getBlock() instanceof BlockIndustrialLever?((BlockIndustrialLever)state.getBlock()).size(world,pos):0;
        return boxes(state,size,mount(world,pos));
    }
    public static AxisAlignedBB bounds(IBlockState state,IBlockAccess world,BlockPos pos){AxisAlignedBB result=null;for(AxisAlignedBB box:boxes(state,world,pos))result=result==null?box:result.union(box);return result;}
    private static JsonObject json(String path){try(InputStream stream=MountedControlGeometry.class.getResourceAsStream(path)){if(stream==null)throw new IllegalStateException("Missing control resource "+path);return new JsonParser().parse(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(path,e);}}
    private static JsonArray elements(String model){String[] name=model.split(":",2);JsonObject root=json("/assets/"+name[0]+"/models/"+(name[1].startsWith("block/")?"":"block/")+name[1]+".json");return root.has("elements")?root.getAsJsonArray("elements"):elements(root.get("parent").getAsString());}
    private static Vec3d vector(JsonArray a){return new Vec3d(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private static Vec3d rotate(Vec3d point,Vec3d origin,int axis,double degrees){double[] p={point.x-origin.x,point.y-origin.y,point.z-origin.z};int a=(axis+1)%3,b=(axis+2)%3;double t=Math.toRadians(degrees),u=p[a],v=p[b];p[a]=u*Math.cos(t)-v*Math.sin(t);p[b]=u*Math.sin(t)+v*Math.cos(t);return new Vec3d(p[0]+origin.x,p[1]+origin.y,p[2]+origin.z);}
    public static final class Pose {
        public final List<List<Vec3d>> components;public final List<AxisAlignedBB> supports;public final AxisAlignedBB support;
        Pose(List<List<Vec3d>> components,List<AxisAlignedBB> supports){this.components=Collections.unmodifiableList(components);this.supports=Collections.unmodifiableList(supports);AxisAlignedBB all=supports.get(0);for(AxisAlignedBB b:supports)all=all.union(b);support=all;}
    }
    private MountedControlGeometry(){}
}

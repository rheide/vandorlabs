package com.vandorlabs.blocks;

import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-safe collision pieces derived from the supplied control elements, plus the solid mounting wedge. */
public final class SignalControlShape {
    private static final Map<String,JsonArray> MODELS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,List<AxisAlignedBB>> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    public static List<AxisAlignedBB> boxes(BlockSignalControl block,IBlockState state,int height,int tilt,int direction){
        EnumFacing face=state.getValue(BlockVandorSwitch.FACING);int rotation=state.getValue(BlockVandorSwitch.ROTATION);
        boolean slider=block.controlKind().equals("wall_slider");
        String mount=face.getAxis()==EnumFacing.Axis.Y?(face==EnumFacing.UP?"floor":"ceiling")+(slider || rotation%2==0?"_z":"_x"):face.getName();
        String path="/assets/vandorlabs/models/block/"+((block.controlKind().equals("thruster_lever") || block.controlKind().equals("wall_slider"))?"thruster_controls":"aircraft_throttles")+"/32px/"+block.controlKind()+"_"+new String[]{"off","low","medium","high"}[state.getValue(BlockSignalControl.LEVEL)]+"_"+mount+".json";
        return SHAPES.computeIfAbsent(path+"/"+rotation+"/"+height+"/"+tilt+"/"+direction,key->{
            SignalControlMount transform=new SignalControlMount(face,rotation,height,tilt,direction,block.supportBounds(state));
            List<AxisAlignedBB> boxes=new ArrayList<>();
            for(JsonElement value:MODELS.computeIfAbsent(path,SignalControlShape::load)){
                JsonObject element=value.getAsJsonObject();Vec3d low=vector(element.getAsJsonArray("from")),high=vector(element.getAsJsonArray("to"));
                List<Vec3d> points=new ArrayList<>();
                for(double x:new double[]{low.x,high.x})for(double y:new double[]{low.y,high.y})for(double z:new double[]{low.z,high.z}){
                    Vec3d point=new Vec3d(x,y,z);
                    if(element.has("rotation"))point=rotate(point,element.getAsJsonObject("rotation"));
                    if(slider && face.getAxis()==EnumFacing.Axis.Y)point=point.subtract(new Vec3d(.5,.5,.5)).rotateYaw((float)(-((rotation+(face==EnumFacing.UP?2:0))&3)*Math.PI/2)).addVector(.5,.5,.5);
                    else if(face.getAxis()==EnumFacing.Axis.Y && rotation>=2)point=new Vec3d(1-point.x,point.y,1-point.z);
                    points.add(transform.transform(point));
                }
                boxes.add(hull(points));
            }
            if(height>0 || tilt>0)boxes.addAll(transform.wedgeBoxes(block.supportBounds(state)));
            return Collections.unmodifiableList(boxes);
        });
    }
    private static JsonArray load(String path){
        try(InputStream stream=SignalControlShape.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing control collision model "+path);
            JsonObject model=new JsonParser().parse(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(model.has("elements"))return model.getAsJsonArray("elements");
            String[] parent=model.get("parent").getAsString().split(":",2);
            return load("/assets/"+(parent.length==2?parent[0]:"minecraft")+"/models/"+parent[parent.length-1]+".json");
        }catch(IOException e){throw new IllegalStateException(path,e);}
    }
    private static Vec3d vector(JsonArray a){return new Vec3d(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private static Vec3d rotate(Vec3d point,JsonObject rotation){
        Vec3d origin=vector(rotation.getAsJsonArray("origin"));double[] v={point.x-origin.x,point.y-origin.y,point.z-origin.z};
        int axis="xyz".indexOf(rotation.get("axis").getAsString()),a=(axis+1)%3,b=(axis+2)%3;
        double angle=Math.toRadians(rotation.get("angle").getAsDouble()),x=v[a],y=v[b];v[a]=x*Math.cos(angle)-y*Math.sin(angle);v[b]=x*Math.sin(angle)+y*Math.cos(angle);
        return new Vec3d(v[0]+origin.x,v[1]+origin.y,v[2]+origin.z);
    }
    static AxisAlignedBB hull(List<Vec3d> points){
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        for(Vec3d p:points){x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z);}
        return new AxisAlignedBB(x0,y0,z0,x1,y1,z1);
    }
    private SignalControlShape(){}
}

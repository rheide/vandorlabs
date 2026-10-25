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
        String path=com.vandorlabs.render.SignalControlGeometry.modelPath(block.controlKind(),face.getIndex(),rotation,state.getValue(BlockSignalControl.LEVEL));
        return SHAPES.computeIfAbsent(path+"/"+rotation+"/"+height+"/"+tilt+"/"+direction,key->{
            List<com.vandorlabs.render.ControlPose.Element> elements=new ArrayList<>();
            for(JsonElement value:MODELS.computeIfAbsent(path,SignalControlShape::load)){
                JsonObject element=value.getAsJsonObject();Vec3d low=vector(element.getAsJsonArray("from")),high=vector(element.getAsJsonArray("to"));
                com.vandorlabs.render.ControlMount.Point origin=null;int axis=0;double degrees=0;
                if(element.has("rotation")){JsonObject r=element.getAsJsonObject("rotation");Vec3d p=vector(r.getAsJsonArray("origin"));origin=new com.vandorlabs.render.ControlMount.Point(p.x,p.y,p.z);axis="xyz".indexOf(r.get("axis").getAsString());degrees=r.get("angle").getAsDouble();}
                elements.add(new com.vandorlabs.render.ControlPose.Element(new com.vandorlabs.render.ControlMount.Box(low.x,low.y,low.z,high.x,high.y,high.z),origin,axis,degrees));
            }
            List<AxisAlignedBB> result=new ArrayList<>();for(com.vandorlabs.render.ControlMount.Box box:com.vandorlabs.render.SignalControlGeometry.collision(block.controlKind(),face.getIndex(),rotation,height,tilt,direction,elements))result.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));return Collections.unmodifiableList(result);
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
    static AxisAlignedBB hull(List<Vec3d> points){
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        for(Vec3d p:points){x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z);}
        return new AxisAlignedBB(x0,y0,z0,x1,y1,z1);
    }
    private SignalControlShape(){}
}

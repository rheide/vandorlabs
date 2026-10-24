package com.vandorlabs.dynmap;

import com.google.gson.*;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/** Reads the existing authored door boxes; no second geometry catalog or client linkage. */
final class DoorMapMesh {
    private static final Map<String,JsonObject> MODELS=new ConcurrentHashMap<>();
    private static JsonObject model(String path){
        JsonObject cached=MODELS.get(path);if(cached!=null)return cached;
        JsonObject loaded=load(path);JsonObject previous=MODELS.putIfAbsent(path,loaded);return previous==null?loaded:previous;
    }
    private static JsonObject load(String key){
        try(InputStream input=DoorMapMesh.class.getResourceAsStream("/assets/vandorlabs/models/"+key+".json")){
            if(input==null)throw new IOException("Missing door map model "+key);
            JsonObject child=new JsonParser().parse(new InputStreamReader(input,"UTF-8")).getAsJsonObject();
            if(child.has("parent")&&child.get("parent").getAsString().startsWith("vandorlabs:")){
                JsonObject result=new JsonParser().parse(model(child.get("parent").getAsString().substring(11)).toString()).getAsJsonObject();
                for(Map.Entry<String,JsonElement> entry:child.entrySet()){
                    if(entry.getKey().equals("textures")&&result.has("textures"))for(Map.Entry<String,JsonElement> texture:entry.getValue().getAsJsonObject().entrySet())result.getAsJsonObject("textures").add(texture.getKey(),texture.getValue());
                    else result.add(entry.getKey(),entry.getValue());
                }
                return result;
            }
            return child;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    static void emit(MapDataContext context,List<RenderPatch> out,String path,UnaryOperator<Vec3d> transform,int leafTexture,int materialBase){
        JsonObject model=model("block/detailed_doors/"+path);
        if(!model.has("elements"))return;
        for(JsonElement element:model.getAsJsonArray("elements")){
            JsonObject box=element.getAsJsonObject();JsonArray a=box.getAsJsonArray("from"),b=box.getAsJsonArray("to");
            double x=a.get(0).getAsDouble()/16,y=a.get(1).getAsDouble()/16,z=a.get(2).getAsDouble()/16,X=b.get(0).getAsDouble()/16,Y=b.get(1).getAsDouble()/16,Z=b.get(2).getAsDouble()/16;
            for(Map.Entry<String,JsonElement> entry:box.getAsJsonObject("faces").entrySet()){
                JsonObject face=entry.getValue().getAsJsonObject();String material=face.get("texture").getAsString();
                int texture=material.equals("#leaf")||material.equals("#glass")||material.equals("#pane")?leafTexture:material.equals("#frame")?materialBase:material.equals("#hinge")||material.equals("#edge")?materialBase+1:materialBase+2;
                double[][] corners;
                switch(entry.getKey()){
                    case "north":corners=new double[][]{{X,Y,z},{X,y,z},{x,y,z},{x,Y,z}};break;
                    case "south":corners=new double[][]{{x,Y,Z},{x,y,Z},{X,y,Z},{X,Y,Z}};break;
                    case "west":corners=new double[][]{{x,Y,z},{x,y,z},{x,y,Z},{x,Y,Z}};break;
                    case "east":corners=new double[][]{{X,Y,Z},{X,y,Z},{X,y,z},{X,Y,z}};break;
                    case "up":corners=new double[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}};break;
                    default:corners=new double[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}};
                }
                JsonArray uv=face.getAsJsonArray("uv");double u0=uv==null?0:uv.get(0).getAsDouble()/16,v0=uv==null?0:uv.get(1).getAsDouble()/16,u1=uv==null?1:uv.get(2).getAsDouble()/16,v1=uv==null?1:uv.get(3).getAsDouble()/16;
                List<MapSurface.Vertex> polygon=new ArrayList<>();
                for(int i=0;i<4;i++)polygon.add(new MapSurface.Vertex(transform.apply(new Vec3d(corners[i][0],corners[i][1],corners[i][2])),i<2?u0:u1,i==0||i==3?v0:v1));
                MapSurface.emit(context,out,polygon,texture);
            }
        }
    }
}

package com.vandorlabs.dynmap;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;

/** Read the shipped native faces once; map UV seams and rotations match the block model. */
final class CopyControllerMapModel {
    private static final class Face {final int texture;final List<MapSurface.Vertex> vertices;Face(int texture,List<MapSurface.Vertex> vertices){this.texture=texture;this.vertices=vertices;}}
    private static final List<Face> FACES=load();
    private static double[] array(JsonArray values){double[] out=new double[values.size()];for(int i=0;i<out.length;i++)out[i]=values.get(i).getAsDouble();return out;}
    private static JsonObject model(String name,Set<String> parents,int depth)throws IOException{
        if(depth>=16||!parents.add(name))throw new IOException("Cyclic or oversized Copy Controller model parent chain: "+name);
        net.minecraft.util.ResourceLocation location=new net.minecraft.util.ResourceLocation(name);String path=location.getResourcePath();if(path.contains("..")||path.startsWith("/"))throw new IOException("Invalid Copy Controller parent path");
        try(InputStream in=CopyControllerMapModel.class.getResourceAsStream("/assets/"+location.getResourceDomain()+"/models/"+path+".json")){
            if(in==null)throw new IOException("Copy Controller model missing: "+name);JsonObject child=new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject(),merged=child.has("parent")?model(child.get("parent").getAsString(),parents,depth+1):new JsonObject();
            JsonObject textures=new JsonObject();if(merged.has("textures"))for(Map.Entry<String,JsonElement> entry:merged.getAsJsonObject("textures").entrySet())textures.add(entry.getKey(),entry.getValue());if(child.has("textures"))for(Map.Entry<String,JsonElement> entry:child.getAsJsonObject("textures").entrySet())textures.add(entry.getKey(),entry.getValue());
            for(Map.Entry<String,JsonElement> entry:child.entrySet())if(!entry.getKey().equals("parent")&&!entry.getKey().equals("textures"))merged.add(entry.getKey(),entry.getValue());merged.add("textures",textures);return merged;
        }
    }
    private static String texture(JsonObject model,String reference)throws IOException{
        Set<String> aliases=new HashSet<>();while(reference.startsWith("#")){if(aliases.size()>=32||!aliases.add(reference))throw new IOException("Cyclic Copy Controller texture alias");JsonElement value=model.getAsJsonObject("textures").get(reference.substring(1));if(value==null)throw new IOException("Missing Copy Controller texture "+reference);reference=value.getAsString();}return reference;
    }
    private static List<Face> load(){
        try{
            JsonObject model=model("vandorlabs:block/copy_controller",new HashSet<>(),0);if(!model.has("elements"))throw new IOException("Native Copy Controller model has no inherited geometry");List<Face> faces=new ArrayList<>();List<String> palette=Arrays.asList("bottom","top","left","right","front","back");
            for(JsonElement value:model.getAsJsonArray("elements")){
                JsonObject element=value.getAsJsonObject();double[] lo=array(element.getAsJsonArray("from")),hi=array(element.getAsJsonArray("to"));
                for(Map.Entry<String,JsonElement> entry:element.getAsJsonObject("faces").entrySet()){
                    EnumFacing side=EnumFacing.valueOf(entry.getKey().toUpperCase(Locale.ROOT));JsonObject face=entry.getValue().getAsJsonObject();String texture=texture(model,face.get("texture").getAsString());int index=palette.indexOf(texture.substring(texture.lastIndexOf('/')+1));if(index<0)throw new IOException("Unknown Copy Controller texture");
                    double[][] points;switch(side){
                        case DOWN:points=new double[][]{{lo[0],lo[1],hi[2]},{lo[0],lo[1],lo[2]},{hi[0],lo[1],lo[2]},{hi[0],lo[1],hi[2]}};break;
                        case UP:points=new double[][]{{lo[0],hi[1],lo[2]},{lo[0],hi[1],hi[2]},{hi[0],hi[1],hi[2]},{hi[0],hi[1],lo[2]}};break;
                        case NORTH:points=new double[][]{{hi[0],hi[1],lo[2]},{hi[0],lo[1],lo[2]},{lo[0],lo[1],lo[2]},{lo[0],hi[1],lo[2]}};break;
                        case SOUTH:points=new double[][]{{lo[0],hi[1],hi[2]},{lo[0],lo[1],hi[2]},{hi[0],lo[1],hi[2]},{hi[0],hi[1],hi[2]}};break;
                        case WEST:points=new double[][]{{lo[0],hi[1],lo[2]},{lo[0],lo[1],lo[2]},{lo[0],lo[1],hi[2]},{lo[0],hi[1],hi[2]}};break;
                        default:points=new double[][]{{hi[0],hi[1],hi[2]},{hi[0],lo[1],hi[2]},{hi[0],lo[1],lo[2]},{hi[0],hi[1],lo[2]}};
                    }
                    double[] uv=face.has("uv")?array(face.getAsJsonArray("uv")):side==EnumFacing.DOWN?new double[]{lo[0],16-hi[2],hi[0],16-lo[2]}:side==EnumFacing.UP?new double[]{lo[0],lo[2],hi[0],hi[2]}:side==EnumFacing.NORTH?new double[]{16-hi[0],16-hi[1],16-lo[0],16-lo[1]}:side==EnumFacing.SOUTH?new double[]{lo[0],16-hi[1],hi[0],16-lo[1]}:side==EnumFacing.WEST?new double[]{lo[2],16-hi[1],hi[2],16-lo[1]}:new double[]{16-hi[2],16-hi[1],16-lo[2],16-lo[1]};
                    double[][] corners={{uv[0],uv[1]},{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]}};List<MapSurface.Vertex> vertices=new ArrayList<>();int rotation=face.has("rotation")?face.get("rotation").getAsInt()/90:0;
                    for(int i=0;i<4;i++){double[] p=points[i],t=corners[Math.floorMod(i+rotation,4)];vertices.add(new MapSurface.Vertex(new Vec3d(p[0]/16,p[1]/16,p[2]/16),t[0]/16,t[1]/16));}faces.add(new Face(index,Collections.unmodifiableList(vertices)));
                }
            }
            return Collections.unmodifiableList(faces);
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot load native Copy Controller map geometry",failure);}
    }
    static RenderPatch[] render(MapDataContext context,int base,int turns){
        List<RenderPatch> out=new ArrayList<>();for(Face face:FACES){List<MapSurface.Vertex> rotated=new ArrayList<>();for(MapSurface.Vertex vertex:face.vertices){double x=vertex.p.x,z=vertex.p.z;for(int t=0;t<turns;t++){double old=x;x=1-z;z=old;}rotated.add(new MapSurface.Vertex(new Vec3d(x,vertex.p.y,z),vertex.u,vertex.v));}MapSurface.emit(context,out,rotated,base+face.texture);}return out.toArray(new RenderPatch[0]);
    }
    private CopyControllerMapModel(){}
}

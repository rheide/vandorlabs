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
    private static List<Face> load(){
        try(InputStream in=CopyControllerMapModel.class.getResourceAsStream("/assets/vandorlabs/models/block/copy_controller.json")){
            if(in==null)throw new IOException("Native Copy Controller model missing");JsonObject model=new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();List<Face> faces=new ArrayList<>();List<String> palette=Arrays.asList("bottom","top","left","right","front","back");
            for(JsonElement value:model.getAsJsonArray("elements")){
                JsonObject element=value.getAsJsonObject();double[] lo=array(element.getAsJsonArray("from")),hi=array(element.getAsJsonArray("to"));
                for(Map.Entry<String,JsonElement> entry:element.getAsJsonObject("faces").entrySet()){
                    EnumFacing side=EnumFacing.valueOf(entry.getKey().toUpperCase(Locale.ROOT));JsonObject face=entry.getValue().getAsJsonObject();String texture=model.getAsJsonObject("textures").get(face.get("texture").getAsString().substring(1)).getAsString();int index=palette.indexOf(texture.substring(texture.lastIndexOf('/')+1));if(index<0)throw new IOException("Unknown Copy Controller texture");
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

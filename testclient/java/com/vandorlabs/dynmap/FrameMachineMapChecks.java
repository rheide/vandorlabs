package com.vandorlabs.dynmap;

import com.google.gson.*;
import com.vandorlabs.vehicle.VehicleRotation;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.dynmap.renderer.*;

/** Per-cell authored geometry reconstruction across state, facing and detected Core corner. */
public final class FrameMachineMapChecks {
    private static final class Patch implements RenderPatch {
        final int texture;final double[] points,limits;
        Patch(int texture,double[] points,double[] limits){this.texture=texture;this.points=points;this.limits=limits;}
        public int getTextureIndex(){return texture;}
        double[] at(double u,double v){double[] result=new double[3];for(int i=0;i<3;i++)result[i]=points[i]+u*(points[i+3]-points[i])+v*(points[i+6]-points[i]);return result;}
        double[][] corners(){return new double[][]{at(limits[0],limits[2]),at(limits[1],limits[3]),at(limits[1],limits[5]),at(limits[0],limits[4])};}
    }
    private static final RenderPatchFactory FACTORY=(RenderPatchFactory)Proxy.newProxyInstance(FrameMachineMapChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
        if(!method.getName().equals("getPatch"))throw new AssertionError("Unexpected frame factory call "+method);
        double[] p=new double[9];for(int i=0;i<9;i++)p[i]=(Double)args[i];
        double[] limits=args.length==17?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[12],(Double)args[13],(Double)args[14]}:new double[]{0,1,0,0,1,1};
        return new Patch((Integer)args[args.length-1],p,limits);
    });
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError("Frame machine map: "+why);}
    private static double area(double[][] vertices){double result=0;for(int i=1;i+1<vertices.length;i++){
        double[] a=new double[3],b=new double[3];for(int axis=0;axis<3;axis++){a[axis]=vertices[i][axis]-vertices[0][axis];b[axis]=vertices[i+1][axis]-vertices[0][axis];}
        double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];result+=Math.sqrt(x*x+y*y+z*z)/2;
    }return result;}
    public static void run()throws Exception{
        JsonObject catalog;
        try(InputStream input=FrameMachineMapChecks.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_frame_models.json")){catalog=new JsonParser().parse(new InputStreamReader(input,"UTF-8")).getAsJsonObject();}
        int textureCount=catalog.getAsJsonArray("textures").size(),assemblies=0,patches=0;FrameMachineRenderer renderer=new FrameMachineRenderer();
        for(Map.Entry<String,JsonElement> entry:catalog.getAsJsonObject("designs").entrySet()){
            String block=entry.getKey();JsonObject design=entry.getValue().getAsJsonObject();int w=design.get("width").getAsInt(),h=design.get("height").getAsInt(),d=design.get("depth").getAsInt();JsonArray states=design.getAsJsonArray("states");
            for(int mode=0;mode<states.size();mode++){
                double expected=0;for(JsonElement raw:states.get(mode).getAsJsonArray()){
                    JsonArray vertices=raw.getAsJsonObject().getAsJsonArray("vertices");double[][] points=new double[vertices.size()][3];for(int v=0;v<points.length;v++)for(int axis=0;axis<3;axis++)points[v][axis]=vertices.get(v).getAsJsonArray().get(axis).getAsDouble();expected+=area(points);
                }
                for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int cornerX:new int[]{0,w-1})for(int cornerZ:new int[]{0,d-1}){
                    int turn=(facing.getHorizontalIndex()+2)&3;BlockPos pivot=VehicleRotation.cell(new BlockPos(cornerX,0,cornerZ),w,d,turn);double actual=0;
                    for(int x=0;x<w;x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++){
                        BlockPos cell=VehicleRotation.cell(new BlockPos(x,y,z),w,d,turn),owner=pivot.subtract(cell);boolean core=cell.equals(pivot);
                        final DynmapBlockState coreState=new DynmapBlockState(null,0,"vandorlabs:"+block,"facing="+facing.getName()+",formed=true","IRON");
                        final DynmapBlockState local=core?coreState:new DynmapBlockState(null,0,"vandorlabs:constructor_part","normal","IRON");
                        final int cx=cornerX,cz=cornerZ,activity=mode;final boolean active=states.size()==2?mode==1:true;
                        MapDataContext context=new MapDataContext(){
                            public RenderPatchFactory getPatchFactory(){return FACTORY;}public DynmapBlockState getBlockType(){return local;}
                            public Object getBlockTileEntityField(String key){return key.equals("ConstructorOwner")?owner.toLong():getBlockTileEntityFieldAt(key,0,0,0);}
                            public DynmapBlockState getBlockTypeAt(int dx,int dy,int dz){return dx==owner.getX()&&dy==owner.getY()&&dz==owner.getZ()?coreState:local;}
                            public Object getBlockTileEntityFieldAt(String key,int dx,int dy,int dz){switch(key){case "ConstructorCoreX":return cx;case "ConstructorCoreZ":return cz;case "NavigationScreenOn":return active;case "MachineActivity":return activity;default:return null;}}
                            public int getX(){return cell.getX();}public int getY(){return cell.getY();}public int getZ(){return cell.getZ();}
                        };
                        for(RenderPatch raw:renderer.getRenderPatchList(context)){
                            require(raw!=null&&raw.getTextureIndex()>=0&&raw.getTextureIndex()<textureCount,"declared native texture slot");Patch patch=(Patch)raw;double[][] points=patch.corners();for(double[] point:points)for(double value:point)require(Double.isFinite(value)&&value>=-1e-6&&value<=1.000001,"clipped reservation bounds "+block);
                            actual+=area(points);patches++;
                        }
                    }
                    require(Math.abs(expected-actual)<1e-5*Math.max(1,expected),"complete per-cell surface area/no duplicates "+block+" "+facing+" "+cornerX+","+cornerZ+" state="+mode+" expected="+expected+" actual="+actual);assemblies++;
                }
            }
        }
        System.out.println("Frame machine Dynmap checks PASS assemblies="+assemblies+" clipped-patches="+patches+" native UVs states all facings and Core corners full area preserved once");
    }
    private FrameMachineMapChecks(){}
}

package com.vandorlabs.dynmap;

import com.google.gson.*;
import com.vandorlabs.blocks.PanelPlane;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.util.EnumFacing;
import org.dynmap.renderer.*;

/** Actual neighbor-derived part selection, clipped native surface area and saved join/mode boundaries. */
public final class HexThrusterMapChecks {
    private static final class Patch implements RenderPatch {
        final int texture;final double[] coordinates,limits;
        Patch(int texture,double[] coordinates,double[] limits){this.texture=texture;this.coordinates=coordinates;this.limits=limits;}
        @Override public int getTextureIndex(){return texture;}
        double[][] corners(){double[][] result=new double[4][3];double[][] uv={{limits[0],limits[2]},{limits[0],limits[3]},{limits[1],limits[4]},{limits[1],limits[5]}};for(int n=0;n<4;n++)for(int axis=0;axis<3;axis++)result[n][axis]=coordinates[axis]+uv[n][0]*(coordinates[3+axis]-coordinates[axis])+uv[n][1]*(coordinates[6+axis]-coordinates[axis]);return result;}
    }
    private static final RenderPatchFactory FACTORY=(RenderPatchFactory)Proxy.newProxyInstance(HexThrusterMapChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
        if(!method.getName().equals("getPatch"))throw new AssertionError("Unexpected hexagonal patch factory method "+method);
        double[] points=new double[9];for(int i=0;i<9;i++)points[i]=(Double)args[i];
        double[] limits=args.length==17?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[13],(Double)args[12],(Double)args[14]}:new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[12],(Double)args[11],(Double)args[12]};
        return new Patch((Integer)args[args.length-1],points,limits);
    });
    private static int checks;
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError("Hexagonal Dynmap: "+message);}
    private static final class Context implements MapDataContext {
        final String id,face,mode;final int ox,oy,width,height;boolean missing,mismatch,disabled;final PanelPlane plane;
        Context(String id,String face,String mode,int ox,int oy,int width,int height){this.id=id;this.face=face;this.mode=mode;this.ox=ox;this.oy=oy;this.width=width;this.height=height;plane=PanelPlane.of(EnumFacing.byName(face));}
        private int[] local(int x,int y,int z){return new int[]{ox+x*plane.right.getFrontOffsetX()+y*plane.right.getFrontOffsetY()+z*plane.right.getFrontOffsetZ(),oy+x*plane.up.getFrontOffsetX()+y*plane.up.getFrontOffsetY()+z*plane.up.getFrontOffsetZ()};}
        private boolean present(int[] p){return p[0]>=0&&p[0]<width&&p[1]>=0&&p[1]<height&&!(missing&&p[0]==width-1&&p[1]==height-1);}
        private DynmapBlockState state(int[] p){return present(p)?new DynmapBlockState(null,0,"vandorlabs:"+id,"facing="+face+",part=single,particles=false,powered="+(!mode.equals("off")&&!(mismatch&&p[0]==width-1&&p[1]==height-1)),"IRON"):DynmapBlockState.AIR;}
        public RenderPatchFactory getPatchFactory(){return FACTORY;}public DynmapBlockState getBlockType(){return state(new int[]{ox,oy});}
        public DynmapBlockState getBlockTypeAt(int x,int y,int z){return state(local(x,y,z));}
        public Object getBlockTileEntityField(String name){return field(name,new int[]{ox,oy});}
        public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){return field(name,local(x,y,z));}
        private Object field(String name,int[] p){if(!present(p))return null;if(name.equals("PropulsionJoin"))return disabled&&p[0]==width-1&&p[1]==height-1?0:1;if(name.equals("ParticleLevel"))return mode.equals("stream")?1:0;return 0;}
        public int getX(){return 0;}public int getY(){return 80;}public int getZ(){return 0;}
    }
    private static double triangle(double[] a,double[] b,double[] c){double x=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]),y=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]),z=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);return Math.sqrt(x*x+y*y+z*z)/2;}
    private static double area(RenderPatch[] mesh){double result=0;for(RenderPatch raw:mesh){Patch patch=(Patch)raw;double[][] v=patch.corners();result+=triangle(v[0],v[1],v[2])+triangle(v[0],v[2],v[3]);}return result;}
    private static double nativeScaledArea(String id,String mode,int size)throws Exception{
        try(InputStream in=HexThrusterMapChecks.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_hex_thrusters.json")){JsonObject data=new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();JsonArray polygons=data.getAsJsonObject("states").getAsJsonArray(id+"/north/"+mode+"/single");double result=0;
            for(JsonElement element:polygons){JsonArray polygon=element.getAsJsonArray();double[][] vertices=new double[polygon.size()-1][3];for(int i=1;i<polygon.size();i++){JsonArray v=polygon.get(i).getAsJsonArray();vertices[i-1]=new double[]{size*v.get(0).getAsDouble(),size*v.get(1).getAsDouble(),v.get(2).getAsDouble()};}for(int i=1;i<vertices.length-1;i++)result+=triangle(vertices[0],vertices[i],vertices[i+1]);}return result;}
    }
    private static String signature(RenderPatch[] mesh){StringBuilder key=new StringBuilder();for(RenderPatch raw:mesh){Patch p=(Patch)raw;key.append(p.texture).append('/');for(double[] v:p.corners())for(double n:v)key.append(Math.round(n*10000000)).append(',');}return key.toString();}
    private static RenderPatch[] mesh(Context context){HexThrusterRenderer renderer=new HexThrusterRenderer();RenderPatch[] mesh=renderer.getRenderPatchList(context);require(mesh.length>20,"native fixture has its detailed housing");for(RenderPatch raw:mesh){Patch p=(Patch)raw;require(p.texture>=0&&p.texture<renderer.getMaximumTextureCount(FACTORY),"native palette is mapped");for(double[] v:p.corners())for(double n:v)require(Double.isFinite(n)&&n>=-1e-7&&n<=1+1e-7,"per-cell native geometry remains in its own voxel");for(double n:p.limits)require(Double.isFinite(n)&&n>=-1e-7&&n<=1+1e-7,"authored UV regions remain normalized");}return mesh;}
    public static void run()throws Exception{
        checks=0;int states=0;
        for(int size:new int[]{2,3})for(String family:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine"})for(String mode:new String[]{"off","on","stream"})for(String face:new String[]{"north","east","south","west","up","down"}){
            String id=family+"_hexagonal";double combined=0;Set<String> parts=new HashSet<>();
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){RenderPatch[] joined=mesh(new Context(id,face,mode,x,y,size,size));combined+=area(joined);parts.add(signature(joined));states++;}
            require(parts.size()==size*size,"all native quadrant meshes are distinct");require(Math.abs(combined-nativeScaledArea(id,mode,size))<1e-5,"joined emitted meshes preserve complete scaled native surface area "+id+"/"+mode+"/"+face+" actual="+combined+" expected="+nativeScaledArea(id,mode,size));
            Context solo=new Context(id,face,mode,0,0,1,1);String single=signature(mesh(solo));states++;
            Context hole=new Context(id,face,mode,0,0,size,size);hole.missing=true;require(single.equals(signature(mesh(hole))),"incomplete group keeps original solo model");
            Context noJoin=new Context(id,face,mode,0,0,size,size);noJoin.disabled=true;require(single.equals(signature(mesh(noJoin))),"per-tile Join OFF prevents entire group");
            if(!mode.equals("off")){Context different=new Context(id,face,mode,0,0,size,size);different.mismatch=true;require(single.equals(signature(mesh(different))),"different render modes never join");}
            for(int y=0;y<size;y++)for(int x=0;x<size*3;x++)require(signature(mesh(new Context(id,face,mode,x,y,size*3,size))).equals(signature(mesh(new Context(id,face,mode,x%size,y,size,size)))),"neighbor-derived repeated2x2/3x3 partition matches gameplay");
        }
        System.out.println("Hexagonal Dynmap PASS native-states="+states+" assertions="+checks+" six-facings solo/quadrant geometry UV area missing/mixed/JoinOFF repeated partitions");
    }
    private HexThrusterMapChecks(){}
}

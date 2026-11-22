package com.vandorlabs.dynmap;

import com.google.gson.*;
import com.vandorlabs.vehicle.VehicleRotation;
import com.vandorlabs.geometry.CoreMesh;
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
    private static double clippedArea(double[][] input,int width,int height,int depth){
        List<double[]> polygon=new ArrayList<>(Arrays.asList(input));double[] limits={width,height,depth};
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){List<double[]> next=new ArrayList<>();if(polygon.isEmpty())return 0;double boundary=side==0?0:limits[axis];double[] previous=polygon.get(polygon.size()-1);boolean was=side==0?previous[axis]>=boundary:previous[axis]<=boundary;
            for(double[] point:polygon){boolean inside=side==0?point[axis]>=boundary:point[axis]<=boundary;if(inside!=was){double t=(boundary-previous[axis])/(point[axis]-previous[axis]);double[] cross=new double[3];for(int k=0;k<3;k++)cross[k]=previous[k]+t*(point[k]-previous[k]);next.add(cross);}if(inside)next.add(point);previous=point;was=inside;}polygon=next;
        }return area(polygon.toArray(new double[0][]));
    }
    private static void maxwellFront(JsonArray faces, int width, int height) {
        List<double[]> rectangles = new ArrayList<>();
        double frontArea = 0;
        for (JsonElement face : faces) {
            JsonArray vertices = face.getAsJsonObject().getAsJsonArray("vertices");
            double left = width, right = 0, bottom = height, top = 0;
            boolean planar = true;
            for (JsonElement vertex : vertices) {
                JsonArray point = vertex.getAsJsonArray();
                double x = point.get(0).getAsDouble(), y = point.get(1).getAsDouble(), z = point.get(2).getAsDouble();
                planar &= Math.abs(z) < 1e-8;
                left = Math.min(left, x);
                right = Math.max(right, x);
                bottom = Math.min(bottom, y);
                top = Math.max(top, y);
            }
            if (!planar) continue;
            require(left >= 0 && bottom >= 0 && right <= width && top <= height, "Maxwell front envelope");
            for (double[] previous : rectangles) {
                require(Math.min(right, previous[1]) - Math.max(left, previous[0]) < 1e-8
                        || Math.min(top, previous[3]) - Math.max(bottom, previous[2]) < 1e-8,
                        "Maxwell front trim and casing have no coplanar overlap");
            }
            rectangles.add(new double[]{left, right, bottom, top});
            frontArea += (right - left) * (top - bottom);
        }
        require(Math.abs(frontArea - width * height) < 1e-8, "Maxwell front has complete casing and trim coverage");
    }

    public static void run()throws Exception{
        JsonObject catalog;
        try(InputStream input=FrameMachineMapChecks.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_frame_models.json")){catalog=new JsonParser().parse(new InputStreamReader(input,"UTF-8")).getAsJsonObject();}
        int textureCount=catalog.getAsJsonArray("textures").size()+catalog.getAsJsonArray("colors").size(),assemblies=0,patches=0,cores=0;FrameMachineRenderer renderer=new FrameMachineRenderer();
        for(Map.Entry<String,JsonElement> entry:catalog.getAsJsonObject("designs").entrySet()){
            String block=entry.getKey();JsonObject design=entry.getValue().getAsJsonObject();int w=design.get("width").getAsInt(),h=design.get("height").getAsInt(),d=design.get("depth").getAsInt();JsonArray states=design.getAsJsonArray("states");
            final DynmapBlockState seed=new DynmapBlockState(null,0,"vandorlabs:"+block,"facing=north,formed=false","IRON");
            MapDataContext seedContext=new MapDataContext(){
                public RenderPatchFactory getPatchFactory(){return FACTORY;}public DynmapBlockState getBlockType(){return seed;}
                public Object getBlockTileEntityField(String key){return null;}public DynmapBlockState getBlockTypeAt(int x,int y,int z){return seed;}
                public Object getBlockTileEntityFieldAt(String key,int x,int y,int z){return null;}public int getX(){return 0;}public int getY(){return 0;}public int getZ(){return 0;}
            };
            List<CoreMesh.Face> coreFaces=CoreMesh.faces(design.get("core").getAsString());RenderPatch[] corePatches=renderer.getRenderPatchList(seedContext);
            require(corePatches.length==coreFaces.size()&&corePatches.length>6,"real scaffold instead of a solid seed cube "+block+" patches="+corePatches.length+" faces="+coreFaces.size());double coreArea=0,expectedCoreArea=0;
            for(int i=0;i<corePatches.length;i++){
                Patch patch=(Patch)corePatches[i];int palette=patch.texture-catalog.getAsJsonArray("textures").size();
                if(coreFaces.get(i).textured)require(patch.texture==design.get("core_texture").getAsInt(),"legacy center atlas");else require(palette>=0&&palette<catalog.getAsJsonArray("colors").size()&&catalog.getAsJsonArray("colors").get(palette).getAsInt()==coreFaces.get(i).rgb(CoreMesh.palette(design.get("core").getAsString())),"shared per-face Core RGB");
                double[][] authored=new double[4][3];for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++)authored[v][axis]=coreFaces.get(i).coordinate(v,axis);expectedCoreArea+=area(authored);coreArea+=area(patch.corners());for(double[] point:patch.corners()){boolean match=false;for(double[] source:authored)if(Math.abs(point[0]-source[0])+Math.abs(point[1]-source[1])+Math.abs(point[2]-source[2])<1e-7)match=true;require(match,"exact authored scaffold vertex");}
            }
            require(coreArea>0&&Math.abs(coreArea-expectedCoreArea)<1e-7,"complete authored scaffold surface area");cores++;
            for(int mode=0;mode<states.size();mode++){
                boolean maxwell=block.equals("longburn_drive_core");
                if (maxwell) maxwellFront(states.get(mode).getAsJsonArray(), w, h);
                boolean observer=block.equals("standalone_dish_core")||block.equals("standalone_antenna_core");
                for(int factor:maxwell?new int[]{3,4,9}:observer?new int[]{1,2}:new int[]{1}){
                final int fw=maxwell?factor:w*factor,fh=maxwell?factor:h*factor;
                final int fd=maxwell?com.vandorlabs.drives.MaxwellEngine.depth(factor):d*factor;
                double[] scale={fw/(double)w,fh/(double)h,fd/(double)d};
                double expected=maxwell?4*.875*.875:0;
                for(JsonElement raw:states.get(mode).getAsJsonArray()){
                    JsonArray vertices=raw.getAsJsonObject().getAsJsonArray("vertices");
                    double[][] points=new double[vertices.size()][3];
                    for(int v=0;v<points.length;v++)for(int axis=0;axis<3;axis++)
                        points[v][axis]=vertices.get(v).getAsJsonArray().get(axis).getAsDouble()*scale[axis];
                    expected+=clippedArea(points,fw,fh,fd);
                }
                for(boolean inverted:block.equals("standalone_dish_core")||block.equals("standalone_antenna_core")?new boolean[]{false,true}:new boolean[]{false})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int cornerX:new int[]{0,fw-1})for(int cornerZ:new int[]{0,fd-1}){
                    int turn=(facing.getHorizontalIndex()+2)&3;BlockPos pivot=VehicleRotation.cell(new BlockPos(cornerX,0,cornerZ),fw,fd,turn);double actual=0;
                    for(int x=0;x<fw;x++)for(int y=0;y<fh;y++)for(int z=0;z<fd;z++){
                        BlockPos cell=VehicleRotation.cell(new BlockPos(x,inverted?-y:y,z),fw,fd,turn),owner=pivot.subtract(cell);boolean core=cell.equals(pivot);
                        final DynmapBlockState coreState=new DynmapBlockState(null,0,"vandorlabs:"+block,"facing="+facing.getName()+",formed=true","IRON");
                        final DynmapBlockState local=core?coreState:new DynmapBlockState(null,0,"vandorlabs:constructor_part","normal","IRON");
                        final int cx=cornerX,cz=cornerZ,activity=mode;final boolean active=states.size()==2?mode==1:true;
                        MapDataContext context=new MapDataContext(){
                            public RenderPatchFactory getPatchFactory(){return FACTORY;}public DynmapBlockState getBlockType(){return local;}
                            public Object getBlockTileEntityField(String key){return key.equals("ConstructorOwner")?owner.toLong():getBlockTileEntityFieldAt(key,0,0,0);}
                            public DynmapBlockState getBlockTypeAt(int dx,int dy,int dz){return dx==owner.getX()&&dy==owner.getY()&&dz==owner.getZ()?coreState:local;}
                            public Object getBlockTileEntityFieldAt(String key,int dx,int dy,int dz){switch(key){case "ObserverWidth":return observer&&factor!=1?fw:null;case "MaxwellWidth":return maxwell?fw:null;case "MaxwellActiveLevel":return active?15:0;case "ObserverInverted":return inverted;case "ConstructorCoreX":return cx;case "ConstructorCoreZ":return cz;case "NavigationScreenOn":return active;case "MachineActivity":return activity;default:return null;}}
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
        }
        System.out.println("Frame machine Dynmap checks PASS assemblies="+assemblies+" clipped-patches="+patches+" open-colored-cores="+cores+" native UVs states all facings and Core corners full area preserved once");
    }
    private FrameMachineMapChecks(){}
}

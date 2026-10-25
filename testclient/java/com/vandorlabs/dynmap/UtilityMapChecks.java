package com.vandorlabs.dynmap;

import java.lang.reflect.Proxy;
import java.util.*;
import org.dynmap.renderer.*;

/** Native 2.6 utility models retain actual surfaces, UV regions and voxel ownership. */
public final class UtilityMapChecks {
    private static final class Patch implements RenderPatch {
        final int texture;final double[] coordinates,limits;
        Patch(int texture,double[] coordinates,double[] limits){this.texture=texture;this.coordinates=coordinates;this.limits=limits;}
        @Override public int getTextureIndex(){return texture;}
        double[][] corners(){double[][] result=new double[4][3];double[][] uv={{limits[0],limits[2]},{limits[0],limits[3]},{limits[1],limits[4]},{limits[1],limits[5]}};for(int n=0;n<4;n++)for(int axis=0;axis<3;axis++)result[n][axis]=coordinates[axis]+uv[n][0]*(coordinates[3+axis]-coordinates[axis])+uv[n][1]*(coordinates[6+axis]-coordinates[axis]);return result;}
    }
    private static final RenderPatchFactory FACTORY=(RenderPatchFactory)Proxy.newProxyInstance(UtilityMapChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
        if(!method.getName().equals("getPatch"))throw new AssertionError("Unexpected utility patch factory method "+method);
        double[] points=new double[9];for(int i=0;i<9;i++)points[i]=(Double)args[i];
        double[] limits=args.length==17?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[13],(Double)args[12],(Double)args[14]}:new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[12],(Double)args[11],(Double)args[12]};
        return new Patch((Integer)args[args.length-1],points,limits);
    });
    private static final class Context implements MapDataContext {
        final DynmapBlockState state;
        Context(String block,String properties){state=new DynmapBlockState(null,0,"vandorlabs:"+block,properties,"IRON");}
        public RenderPatchFactory getPatchFactory(){return FACTORY;}
        public DynmapBlockState getBlockType(){return state;}
        public Object getBlockTileEntityField(String name){return null;}
        public DynmapBlockState getBlockTypeAt(int x,int y,int z){return state;}
        public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){return null;}
        public int getX(){return 0;}public int getY(){return 80;}public int getZ(){return 0;}
    }
    private static RenderPatch[] check(String id,String props){
        UtilityModelRenderer renderer=new UtilityModelRenderer();RenderPatch[] mesh=renderer.getRenderPatchList(new Context(id,props));
        if(mesh.length<4)throw new AssertionError("Utility overview empty or reduced to cube: "+id+" "+props);
        for(RenderPatch raw:mesh){Patch patch=(Patch)raw;if(patch.texture<0||patch.texture>=renderer.getMaximumTextureCount(FACTORY))throw new AssertionError("Utility native texture palette missing");
            for(double value:patch.limits)if(!Double.isFinite(value)||value < -1e-8||value>1+1e-8)throw new AssertionError("Utility UV regions leave native texture");
            for(double[] point:patch.corners())for(double coordinate:point)if(!Double.isFinite(coordinate)||coordinate < -1e-8||coordinate>1+1e-8)throw new AssertionError("Utility geometry crosses another occupied voxel");
        }
        return mesh;
    }
    public static void run(){
        int states=0;
        for(String facing:new String[]{"north","east","south","west"}){
            int lower=0,upper=0;for(int part=0;part<4;part++){RenderPatch[] mesh=check("data_chip_programmer","facing="+facing+",part="+part);if(part<2)lower+=mesh.length;else upper+=mesh.length;states++;}
            if(lower==0||upper==0)throw new AssertionError("Whole programmer casing must span both rows of occupied cells");
            for(String id:new String[]{"compact_track","armoured_track","bogie_track"})for(boolean assembled:new boolean[]{false,true}){check(id,"facing="+facing+",assembled="+assembled);states++;}
        }
        for(String facing:new String[]{"north","east","south","west","up","down"}){
            RenderPatch[] released=check("square_coupler","facing="+facing+",locked=false"),locked=check("square_coupler","facing="+facing+",locked=true");
            if(signature(released).equals(signature(locked)))throw new AssertionError("Coupler endpoint models are identical: "+facing);states+=2;
        }
        for(String facing:new String[]{"north","east","south","west","up","down"})for(String half:new String[]{"bottom","top"})for(boolean locked:new boolean[]{false,true}){check("vehicle_coupler_slab","facing="+facing+",half="+half+",locked="+locked);states++;}
        System.out.println("Utility Dynmap renderer PASS states="+states+" 16 programmer cells 12 coupler endpoints 24 track defaults native UVs clipped cells");
    }
    private static String signature(RenderPatch[] mesh){StringBuilder result=new StringBuilder();for(RenderPatch raw:mesh){Patch patch=(Patch)raw;result.append(patch.texture);for(double[] point:patch.corners())for(double value:point)result.append(Math.round(value*1000000));}return result.toString();}
    private UtilityMapChecks(){}
}

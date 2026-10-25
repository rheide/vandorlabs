package com.vandorlabs.dynmap;

import java.lang.reflect.Proxy;
import java.util.*;
import org.dynmap.renderer.*;

/** Native 2.6 utility models retain actual surfaces, UV regions and voxel ownership. */
public final class SurfaceMapChecks {
    private static final class Patch implements RenderPatch {
        final int texture;final double[] coordinates,limits;
        Patch(int texture,double[] coordinates,double[] limits){this.texture=texture;this.coordinates=coordinates;this.limits=limits;}
        @Override public int getTextureIndex(){return texture;}
        double[][] corners(){double[][] result=new double[4][3];double[][] uv={{limits[0],limits[2]},{limits[0],limits[3]},{limits[1],limits[4]},{limits[1],limits[5]}};for(int n=0;n<4;n++)for(int axis=0;axis<3;axis++)result[n][axis]=coordinates[axis]+uv[n][0]*(coordinates[3+axis]-coordinates[axis])+uv[n][1]*(coordinates[6+axis]-coordinates[axis]);return result;}
    }
    private static final RenderPatchFactory FACTORY=(RenderPatchFactory)Proxy.newProxyInstance(SurfaceMapChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
        if(!method.getName().equals("getPatch"))throw new AssertionError("Unexpected utility patch factory method "+method);
        double[] points=new double[9];for(int i=0;i<9;i++)points[i]=(Double)args[i];
        double[] limits=args.length==17?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[13],(Double)args[12],(Double)args[14]}:new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[12],(Double)args[11],(Double)args[12]};
        return new Patch((Integer)args[args.length-1],points,limits);
    });
    private static class Context implements MapDataContext {
        final DynmapBlockState state;final String selection;final int mode,size,col,row;
        Context(String facing,String selection,int mode,int size){this(facing,selection,mode,size,0,0);}
        Context(String facing,String selection,int mode,int size,int col,int row){state=new DynmapBlockState(null,0,"vandorlabs:turntable_surface","facing="+facing,"IRON");this.selection=selection;this.mode=mode;this.size=size;this.col=col;this.row=row;}
        public RenderPatchFactory getPatchFactory(){return FACTORY;}
        public DynmapBlockState getBlockType(){return state;}
        public Object getBlockTileEntityField(String name){if(name.equals("SurfaceGroupOffset")){com.vandorlabs.turntable.TurntableGeometry.Face basis=com.vandorlabs.turntable.TurntableGeometry.Face.valueOf(state.isStateMatch("facing","up")?"UP":state.isStateMatch("facing","down")?"DOWN":state.isStateMatch("facing","north")?"NORTH":state.isStateMatch("facing","south")?"SOUTH":state.isStateMatch("facing","east")?"EAST":"WEST");double[] at=basis.point(-col,-row,0);return new net.minecraft.util.math.BlockPos(at[0],at[1],at[2]).toLong();}if(name.equals("SurfaceOff"))return selection;if(name.equals("SurfaceLayout"))return mode;if(name.equals("SurfaceGroupSize"))return size;return null;}
        public DynmapBlockState getBlockTypeAt(int x,int y,int z){return state;}
        public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){return null;}
        public int getX(){return 0;}public int getY(){return 80;}public int getZ(){return 0;}
    }
    private static RenderPatch[] check(String facing,String selection,int mode,int size,int col,int row){
        ProgrammableSurfaceRenderer renderer=new ProgrammableSurfaceRenderer();RenderPatch[] mesh=renderer.getRenderPatchList(new Context(facing,selection,mode,size,col,row));
        if(mesh.length<(mode==2?6:7))throw new AssertionError("Missing surface body or selected face");
        boolean selected=false;for(RenderPatch raw:mesh){Patch patch=(Patch)raw;selected|=patch.texture==com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT+com.vandorlabs.turntable.TurntableTextures.materialChoice(selection);
            for(double value:patch.limits)if(!Double.isFinite(value)||value < -1e-8||value>1+1e-8)throw new AssertionError("Surface UV leaves texture");
            for(double[] point:patch.corners())for(double value:point)if(!Double.isFinite(value)||value < -1e-8||value>1+1e-8)throw new AssertionError("Surface leaves map voxel");
        }if(!selected&&mode!=2)throw new AssertionError("Selected artwork absent");return mesh;
    }
    public static void run(){for(final boolean copy:new boolean[]{false,true})for(final String facing:new String[]{"north","east","south","west"})for(final boolean active:new boolean[]{false,true})for(final boolean clockwise:new boolean[]{false,true}){SurfaceControllerRenderer renderer=new SurfaceControllerRenderer();RenderPatch[] patches=renderer.getRenderPatchList(new Context(facing,"original_off",1,1){@Override public DynmapBlockState getBlockType(){return new DynmapBlockState(null,0,"vandorlabs:"+(copy?"copy_controller":"turntable_controller"),"facing="+facing,"IRON");}@Override public Object getBlockTileEntityField(String name){return name.equals("Running")?active:name.equals("Clockwise")?clockwise:null;}});if(patches.length!=6)throw new AssertionError("Native controller cube loses a face");java.util.Set<Integer> faces=new java.util.HashSet<>();for(RenderPatch patch:patches){if(patch.getTextureIndex()<2*com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT+5||patch.getTextureIndex()>=renderer.getMaximumTextureCount())throw new AssertionError("Native controller face outside palette");faces.add(patch.getTextureIndex());}if(faces.size()!=6)throw new AssertionError("Native controller distinct face art collapsed");}java.util.Set<Integer> selected=new java.util.HashSet<>();for(String id:com.vandorlabs.turntable.TurntableTextures.IDS){int choice=com.vandorlabs.turntable.TurntableTextures.materialChoice(id);if(!"Surfaces".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice)))throw new AssertionError("Surface catalog lookup did not resolve native artwork "+id);selected.add(choice);}if(selected.size()!=14)throw new AssertionError("Native surface materials collapsed");int states=0;for(String facing:new String[]{"down","up","north","south","west","east"})for(String selection:com.vandorlabs.turntable.TurntableTextures.IDS)for(int mode=0;mode<3;mode++)for(int size:new int[]{1,4})for(int col=0;col<size;col++)for(int row=0;row<size;row++){check(facing,selection,mode,size,col,row);states++;}System.out.println("Surface Dynmap renderer PASS states="+states+" selected native materials, six faces, group UVs and atlas bounds");}
    private SurfaceMapChecks(){}
}

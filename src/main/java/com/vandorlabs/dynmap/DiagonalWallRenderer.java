package com.vandorlabs.dynmap;

import com.vandorlabs.render.DiagonalWallGeometry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;
import java.util.*;

/** Saved slope and fill settings, in the same north-facing frame as the game mesh. */
final class DiagonalWallRenderer extends HousingRenderer {
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){return patches(context);}
    static RenderPatch[] patches(MapDataContext context){
        DynmapBlockState state=context.getBlockType();
        boolean inverted=state.isStateMatch("inverted","true"),half=booleanValue(context.getBlockTileEntityField("DiagonalHalfHeight"));
        int mode=half?2:booleanValue(context.getBlockTileEntityField("DiagonalFullWidth"))?1:0;
        int fill=Math.max(0,Math.min(3,integer(context.getBlockTileEntityField("DiagonalFill"),0)));
        EnumFacing facing=state.isStateMatch("facing","east")?EnumFacing.EAST:state.isStateMatch("facing","south")?EnumFacing.SOUTH:state.isStateMatch("facing","west")?EnumFacing.WEST:EnumFacing.NORTH;
        int texture=texture(context.getBlockTileEntityField("housingTexture"));
        List<RenderPatch> result=new ArrayList<>();
        double span=DiagonalWallGeometry.span(mode);
        double fillLow=half&&inverted?-.5:0,fillHigh=half&&inverted?.5:1;
        SortedSet<Double> cuts=new TreeSet<>(Arrays.asList(0D,1D));
        for(double near:new double[]{fillLow,fillHigh-.25}){
            double t=(near+.125)/span;if(inverted)t=1-t;
            if(t>0&&t<1)cuts.add(t);
        }
        Double previous=null;
        for(double upper:cuts){
            if(previous==null){previous=upper;continue;}double lower=previous;previous=upper;
            double a=DiagonalWallGeometry.near(mode,inverted,lower),b=DiagonalWallGeometry.near(mode,inverted,upper);
            double lo0=(fill&1)!=0?Math.min(fillLow,a):a,lo1=(fill&1)!=0?Math.min(fillLow,b):b;
            double hi0=(fill&2)!=0?Math.max(fillHigh,a+.25):a+.25,hi1=(fill&2)!=0?Math.max(fillHigh,b+.25):b+.25;
            double[][] v={{0,lower,lo0},{1,lower,lo0},{1,upper,lo1},{0,upper,lo1},{0,lower,hi0},{1,lower,hi0},{1,upper,hi1},{0,upper,hi1}};
            int index=0;
            for(int[] face:new int[][]{{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2}}){
                List<MapSurface.Vertex> polygon=new ArrayList<>();
                for(int vertex:face){double[] p=v[vertex];polygon.add(new MapSurface.Vertex(point(p[0],p[1],p[2],half,inverted,facing),index<2?p[0]:p[2]-(fill==0?0:fillLow),1-p[1]));}
                MapSurface.emit(context,result,polygon,texture);index++;
            }
            if(lower==0)cap(context,result,v,new int[]{4,5,1,0},half,inverted,facing,texture,fill==0?0:fillLow);
            if(upper==1)cap(context,result,v,new int[]{3,2,6,7},half,inverted,facing,texture,fill==0?0:fillLow);
        }
        return result.toArray(new RenderPatch[0]);
    }
    private static Vec3d point(double x,double y,double z,boolean half,boolean inverted,EnumFacing facing){
        if(half){double old=y;y=z+(inverted?.5:0);z=old;}
        switch(facing){case EAST:return new Vec3d(1-z,y,x);case SOUTH:return new Vec3d(1-x,y,1-z);case WEST:return new Vec3d(z,y,1-x);default:return new Vec3d(x,y,z);}
    }
    private static void cap(MapDataContext context,List<RenderPatch> out,double[][] vertices,int[] indices,boolean half,boolean inverted,EnumFacing facing,int texture,double origin){
        List<MapSurface.Vertex> polygon=new ArrayList<>();
        for(int index:indices){double[] p=vertices[index];polygon.add(new MapSurface.Vertex(point(p[0],p[1],p[2],half,inverted,facing),p[0],p[2]-origin));}
        MapSurface.emit(context,out,polygon,texture);
    }
}

package com.vandorlabs.dynmap;

import com.google.gson.Gson;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.turntable.TurntableTextures;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import org.dynmap.renderer.*;

/** Surface-local saved artwork keeps square-wide UVs in every map voxel. */
public final class ProgrammableSurfaceRenderer extends HousingRenderer {
    private static final double[] ASPECTS=load();
    private static double[] load(){try(InputStream in=ProgrammableSurfaceRenderer.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_material_aspects.json")){if(in==null)throw new IllegalStateException("Missing map material aspects");return new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),double[].class);}catch(IOException error){throw new IllegalStateException(error);}}
    @Override protected int getMaximumTextureCount(){return 2*ScreenHousingTextures.BUILTIN_COUNT+5;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"SurfaceOn","SurfaceOff","SurfaceLayout","SurfaceGroupOffset","SurfaceGroupSize","SurfaceGroupNormal","Owner","Running","CopyBusy","CopyActiveTicks"};}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        EnumFacing normal=EnumFacing.UP;for(EnumFacing face:EnumFacing.values())if(context.getBlockType().isStateMatch("facing",face.getName()))normal=face;
        TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());
        boolean active=false;Object owner=context.getBlockTileEntityField("Owner");
        if(owner instanceof Number){BlockPos at=BlockPos.fromLong(((Number)owner).longValue());int x=at.getX()-context.getX(),y=at.getY()-context.getY(),z=at.getZ()-context.getZ();
            if(Math.abs(x)<=32&&Math.abs(y)<=32&&Math.abs(z)<=32){active=booleanValue(context.getBlockTileEntityFieldAt("Running",x,y,z));DynmapBlockState controller=context.getBlockTypeAt(x,y,z);
                if(controller!=null&&"vandorlabs:copy_controller".equals(controller.blockName)){EnumFacing front=EnumFacing.NORTH;for(EnumFacing face:EnumFacing.HORIZONTALS)if(controller.isStateMatch("facing",face.getName()))front=face;EnumFacing right=front.rotateY();active=(-x*right.getFrontOffsetX()-z*right.getFrontOffsetZ()>0)&&(booleanValue(context.getBlockTileEntityFieldAt("CopyBusy",x,y,z))||integer(context.getBlockTileEntityFieldAt("CopyActiveTicks",x,y,z),0)>0);}
            }
        }
        Object selected=context.getBlockTileEntityField(active?"SurfaceOn":"SurfaceOff");int material=texture(TurntableTextures.materialChoice(selected instanceof String?(String)selected:active?"original_on":"original_off"));
        int size=Math.max(1,Math.min(16,integer(context.getBlockTileEntityField("SurfaceGroupSize"),1))),mode=integer(context.getBlockTileEntityField("SurfaceLayout"),1);
        Object offset=context.getBlockTileEntityField("SurfaceGroupOffset");BlockPos relative=offset instanceof Number?BlockPos.fromLong(((Number)offset).longValue()):BlockPos.ORIGIN;
        int col=-basis.u(relative.getX(),relative.getY(),relative.getZ()),row=-basis.v(relative.getX(),relative.getY(),relative.getZ());
        if(col<0||col>=size||row<0||row>=size){col=0;row=0;size=1;}
        List<RenderPatch> out=new ArrayList<>();int[] sides=new int[6];Arrays.fill(sides,2*ScreenHousingTextures.BUILTIN_COUNT+3);int[] faceSlot={0,1,4,5,2,3};sides[faceSlot[normal.getIndex()]]=-1;addBox(context.getPatchFactory(),out,0,1,0,1,0,1,sides);
        emit(context,out,basis,0,0,1,1,0,0,1,1,.4999,2*ScreenHousingTextures.BUILTIN_COUNT+4);
        double aspect=material<ASPECTS.length?ASPECTS[material]:1,fu=1,fv=1;if(mode==2){fu=Math.min(1,aspect);fv=Math.min(1,1/aspect);}
        double u0=mode==1?col/Math.max(1,aspect):.5+((double)col/size-.5)/fu,u1=mode==1?(col+1)/Math.max(1,aspect):.5+((double)(col+1)/size-.5)/fu;
        double v0=mode==1?row/Math.max(1,1/aspect):.5+((double)row/size-.5)/fv,v1=mode==1?(row+1)/Math.max(1,1/aspect):.5+((double)(row+1)/size-.5)/fv;
        double x0=0,x1=1,y0=0,y1=1;if(mode==2){x0=Math.max(0,-u0/(u1-u0));x1=Math.min(1,(1-u0)/(u1-u0));y0=Math.max(0,-v0/(v1-v0));y1=Math.min(1,(1-v0)/(v1-v0));}
        if(x1>x0&&y1>y0)emit(context,out,basis,x0,y0,x1,y1,u0+x0*(u1-u0),v0+y0*(v1-v0),u0+x1*(u1-u0),v0+y1*(v1-v0),.5,ScreenHousingTextures.BUILTIN_COUNT+material);
        return out.toArray(new RenderPatch[0]);
    }
    private static void emit(MapDataContext context,List<RenderPatch> out,TurntableGeometry.Face basis,double x0,double y0,double x1,double y1,double u0,double v0,double u1,double v1,double depth,int texture){List<MapSurface.Vertex> face=new ArrayList<>();double[][] corners={{x0,y0,u0,v0},{x1,y0,u1,v0},{x1,y1,u1,v1},{x0,y1,u0,v1}};for(double[] corner:corners){double[] p=basis.point(corner[0]-.5,corner[1]-.5,depth);face.add(new MapSurface.Vertex(new Vec3d(.5+p[0],.5+p[1],.5+p[2]),corner[2],corner[3]));}MapSurface.emit(context,out,face,texture);}
}

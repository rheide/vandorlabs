package com.vandorlabs.dynmap;

import com.google.gson.Gson;
import com.vandorlabs.blocks.MachineMountSurface;
import com.vandorlabs.vehicle.VehicleRotation;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;

/** Authored static machinery is clipped into its reserved cells, never submitted twice. */
public final class FrameMachineRenderer extends CustomRenderer {
    private static final class Face {int texture;double[][] vertices;}
    private static final class Design {int width,height,depth,seed;Face[][] states;}
    private static final class Catalog {String[] textures;Map<String,Design> designs;}
    private static final Catalog CATALOG=load();
    private final com.google.common.cache.Cache<String,RenderPatch[]> meshes=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(2048).build();
    private static Catalog load(){
        try(InputStream input=FrameMachineRenderer.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_frame_models.json")){
            if(input==null)throw new IllegalStateException("Missing Core map geometry");
            return new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),Catalog.class);
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    @Override protected int getMaximumTextureCount(){return CATALOG.textures.length;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"ConstructorOwner","ConstructorCoreX","ConstructorCoreZ","NavigationScreenOn","MachineActivity"};}
    private static int integer(Object value,int fallback){return value instanceof Number?((Number)value).intValue():fallback;}
    private static boolean on(Object value){return Boolean.TRUE.equals(value)||value instanceof Number&&((Number)value).intValue()!=0;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        boolean part=context.getBlockType().blockName.equals("vandorlabs:constructor_part");
        BlockPos owner=BlockPos.ORIGIN;
        if(part){Object raw=context.getBlockTileEntityField("ConstructorOwner");if(!(raw instanceof Number))return new RenderPatch[0];owner=BlockPos.fromLong(((Number)raw).longValue());if(Math.abs(owner.getX())>16||Math.abs(owner.getY())>16||Math.abs(owner.getZ())>16)return new RenderPatch[0];}
        int ox=owner.getX(),oy=owner.getY(),oz=owner.getZ();DynmapBlockState state=part?context.getBlockTypeAt(ox,oy,oz):context.getBlockType();
        if(state==null||state.blockName==null)return new RenderPatch[0];
        Design design=CATALOG.designs.get(state.blockName.replace("vandorlabs:",""));if(design==null)return new RenderPatch[0];
        if(!state.isStateMatch("formed","true")){
            if(part)return new RenderPatch[0];List<RenderPatch> seed=new ArrayList<>();int t=design.seed;addBox(context.getPatchFactory(),seed,0,1,0,1,0,1,new int[]{t,t,t,t,t,t});return seed.toArray(new RenderPatch[0]);
        }
        EnumFacing facing=state.isStateMatch("facing","east")?EnumFacing.EAST:state.isStateMatch("facing","south")?EnumFacing.SOUTH:state.isStateMatch("facing","west")?EnumFacing.WEST:EnumFacing.NORTH;
        int x=integer(context.getBlockTileEntityFieldAt("ConstructorCoreX",ox,oy,oz),0),z=integer(context.getBlockTileEntityFieldAt("ConstructorCoreZ",ox,oy,oz),0);
        if((x!=0&&x!=design.width-1)||(z!=0&&z!=design.depth-1))return new RenderPatch[0];
        BlockPos pivot=VehicleRotation.cell(new BlockPos(x,0,z),design.width,design.depth,(facing.getHorizontalIndex()+2)&3);
        int dx=ox-pivot.getX(),dy=oy,dz=oz-pivot.getZ();
        boolean active=on(context.getBlockTileEntityFieldAt("NavigationScreenOn",ox,oy,oz));
        int activity=integer(context.getBlockTileEntityFieldAt("MachineActivity",ox,oy,oz),1);
        int mode=design.states.length==1?0:design.states.length==2?(active?1:0):active?Math.max(0,Math.min(2,activity)):0;
        String key=state.blockName+":"+facing+":"+mode+":"+dx+":"+dy+":"+dz;RenderPatch[] cached=meshes.getIfPresent(key);if(cached!=null)return cached;
        List<RenderPatch> result=new ArrayList<>();
        for(Face face:design.states[mode]){
            List<MapSurface.Vertex> polygon=new ArrayList<>(face.vertices.length);
            for(double[] point:face.vertices){Vec3d p=MachineMountSurface.rotate(new Vec3d(point[0],point[1],point[2]),facing,design.width,design.depth).addVector(dx,dy,dz);polygon.add(new MapSurface.Vertex(p,point[3],point[4]));}
            // A face exactly on an internal cell plane belongs to the upper cell only.
            // Keep the maximum outer envelope face in the final reserved cell.
            boolean owned=true;double[] extent={facing.getAxis()==EnumFacing.Axis.X?design.depth:design.width,design.height,facing.getAxis()==EnumFacing.Axis.X?design.width:design.depth};int[] origin={dx,dy,dz};
            for(int axis=0;axis<3;axis++){
                boolean upper=true;for(MapSurface.Vertex vertex:polygon)upper&=Math.abs(vertex.at(axis)-1)<1e-8;
                if(upper&&1-origin[axis]<extent[axis]-1e-8)owned=false;
            }
            if(owned)MapSurface.emit(context,result,polygon,face.texture);
        }
        RenderPatch[] built=result.toArray(new RenderPatch[0]);meshes.put(key,built);return built;
    }
}

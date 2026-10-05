package com.vandorlabs.dynmap;

import com.vandorlabs.render.LargeDoorGeometry;
import org.dynmap.renderer.*;
import java.util.*;

/** Resolve each cell's shared anchor and clip the real assembly to this map cell. */
public final class LargeProgrammableDoorRenderer extends CustomRenderer {
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"LargeDoorColumn","LargeDoorRow","SpaceFramed","SpaceDoorSliding","SpaceSlideDirection","SpaceDoorPlacementDepth","SpaceDoorHinges"};}
    private static int number(Object value,int fallback){return value instanceof Number?((Number)value).intValue():fallback;}
    private static boolean flag(Object value,boolean fallback){return value==null?fallback:Boolean.TRUE.equals(value)||value instanceof Number && ((Number)value).intValue()!=0;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        DynmapBlockState state=context.getBlockType();int facing=state.isStateMatch("facing","north")?2:state.isStateMatch("facing","east")?3:state.isStateMatch("facing","west")?1:0;
        int col=number(context.getBlockTileEntityField("LargeDoorColumn"),0),row=number(context.getBlockTileEntityField("LargeDoorRow"),0);
        int ax=facing==0?-col:facing==2?col:0,az=facing==3?col:facing==1?-col:0;
        boolean frame=flag(context.getBlockTileEntityFieldAt("SpaceFramed",ax,-row,az),true),sliding=flag(context.getBlockTileEntityFieldAt("SpaceDoorSliding",ax,-row,az),false);
        int direction=number(context.getBlockTileEntityFieldAt("SpaceSlideDirection",ax,-row,az),0),depth=number(context.getBlockTileEntityFieldAt("SpaceDoorPlacementDepth",ax,-row,az),1);
        boolean hinges=flag(context.getBlockTileEntityFieldAt("SpaceDoorHinges",ax,-row,az),true);
        double offset=com.vandorlabs.persistence.SpaceDoorData.positionOffset(sliding,frame,hinges,depth);
        List<RenderPatch> result=new ArrayList<>();
        for(LargeDoorGeometry.Box b:LargeDoorGeometry.boxes(frame,sliding,direction,state.isStateMatch("open","true"),offset,facing)){
            double x0=Math.max(0,b.x0+ax),y0=Math.max(0,b.y0-row),z0=Math.max(0,b.z0+az),x1=Math.min(1,b.x1+ax),y1=Math.min(1,b.y1-row),z1=Math.min(1,b.z1+az);
            if(x1>x0 && y1>y0 && z1>z0)addBox(context.getPatchFactory(),result,x0,x1,y0,y1,z0,z1,new int[]{0,0,0,0,0,0});
        }
        return result.toArray(new RenderPatch[0]);
    }
}

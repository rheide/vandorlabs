package com.vandorlabs.dynmap;

import com.vandorlabs.garage.GarageDoorGeometry;
import org.dynmap.renderer.*;
import java.util.*;

/** Map each native section to its closed or overhead endpoint without a live-world lookup. */
public final class GarageDoorMapRenderer extends CustomRenderer {
    @Override protected int getMaximumTextureCount(){return 2;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"GarageTopOffset","GarageHeight","GarageOpen"};}
    private static int value(Object v,int fallback){return v instanceof Number?((Number)v).intValue():fallback;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        int row=Math.max(0,Math.min(31,value(context.getBlockTileEntityField("GarageTopOffset"),0))),height=Math.max(1,Math.min(32,value(context.getBlockTileEntityField("GarageHeight"),1)));Object target=context.getBlockTileEntityField("GarageOpen");boolean open=Boolean.TRUE.equals(target)||value(target,0)!=0;
        double[] pose=new double[4];GarageDoorGeometry.section(row,height,open?1:0,pose);double h=GarageDoorGeometry.HALF_THICKNESS;
        List<RenderPatch> out=new ArrayList<>();RenderPatchFactory factory=context.getPatchFactory();
        if(open)addBox(factory,out,0,1,row+pose[0]-h,row+pose[0]+h,pose[1]-.5,pose[1]+.5,new int[]{0,0,1,1,1,1});
        else addBox(factory,out,0,1,row+pose[0]-.5,row+pose[0]+.5,pose[1]-h,pose[1]+h,new int[]{1,1,1,1,0,0});
        if(row==0)addBox(factory,out,0,1,.875,1,.375,.625,new int[]{1,1,1,1,1,1});
        int angle=context.getBlockType().isStateMatch("facing","south")?180:context.getBlockType().isStateMatch("facing","west")?90:context.getBlockType().isStateMatch("facing","east")?270:0;
        RenderPatch[] result=new RenderPatch[out.size()];for(int i=0;i<result.length;i++){RenderPatch patch=out.get(i);result[i]=angle==0?patch:factory.getRotatedPatch(patch,0,angle,0,patch.getTextureIndex());}return result;
    }
}

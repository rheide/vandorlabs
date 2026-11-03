package com.vandorlabs.dynmap;

import com.vandorlabs.garage.GarageDoorGeometry;
import org.dynmap.renderer.*;
import java.util.*;

/** Map each native section to its closed or overhead endpoint without a live-world lookup. */
public final class GarageDoorMapRenderer extends HousingRenderer {
    @Override protected int getMaximumTextureCount(){return 2*com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT+3;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"GarageTopOffset","GarageHeight","GarageOpen","GarageTexture"};}
    private static int defaultTexture(){for(int i=0;i<com.vandorlabs.tiles.ScreenHousingTextures.IDS.length;i++)if("sectional_door_panel".equals(com.vandorlabs.tiles.ScreenHousingTextures.IDS[i]))return i;return 0;}
    private static int value(Object v,int fallback){return v instanceof Number?((Number)v).intValue():fallback;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        int row=Math.max(0,Math.min(31,value(context.getBlockTileEntityField("GarageTopOffset"),0))),height=Math.max(1,Math.min(32,value(context.getBlockTileEntityField("GarageHeight"),1)));Object target=context.getBlockTileEntityField("GarageOpen");boolean open=Boolean.TRUE.equals(target)||value(target,0)!=0;
        double[] pose=new double[4];GarageDoorGeometry.section(row,height,open?1:0,pose);double h=GarageDoorGeometry.HALF_THICKNESS;
        List<RenderPatch> out=new ArrayList<>();RenderPatchFactory factory=context.getPatchFactory();
        int front=integer(context.getBlockTileEntityField("GarageTexture"),-1);if(front<0)front=defaultTexture();front=com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT+texture(front);int edge=2*com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT+2;
        if(open)addBox(factory,out,0,1,row+pose[0]-h,row+pose[0]+h,pose[1]-.5,pose[1]+.5,new int[]{front,front,edge,edge,edge,edge});
        else addBox(factory,out,0,1,row+pose[0]-.5,row+pose[0]+.5,pose[1]-h,pose[1]+h,new int[]{edge,edge,edge,edge,front,front});
        if(row==0)addBox(factory,out,0,1,.875,1,.375,.625,new int[]{edge,edge,edge,edge,edge,edge});
        int angle=context.getBlockType().isStateMatch("facing","south")?180:context.getBlockType().isStateMatch("facing","west")?90:context.getBlockType().isStateMatch("facing","east")?270:0;
        RenderPatch[] result=new RenderPatch[out.size()];for(int i=0;i<result.length;i++){RenderPatch patch=out.get(i);result[i]=angle==0?patch:factory.getRotatedPatch(patch,0,angle,0,patch.getTextureIndex());}return result;
    }
}

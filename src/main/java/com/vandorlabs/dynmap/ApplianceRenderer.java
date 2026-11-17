package com.vandorlabs.dynmap;

import java.util.*;
import org.dynmap.renderer.*;

/** Six authored faces with the same horizontal rotation as the static block model. */
public final class ApplianceRenderer extends CustomRenderer {
    private RenderPatchFactory factory;
    private final RenderPatch[][] cache=new RenderPatch[4][];
    @Override protected int getMaximumTextureCount(){return 6;}
    @Override public synchronized RenderPatch[] getRenderPatchList(MapDataContext context){
        if(factory!=context.getPatchFactory()){factory=context.getPatchFactory();Arrays.fill(cache,null);}
        int turn=context.getBlockType().isStateMatch("facing","east")?1:context.getBlockType().isStateMatch("facing","south")?2:context.getBlockType().isStateMatch("facing","west")?3:0;if(cache[turn]!=null)return cache[turn];
        List<RenderPatch> faces=new ArrayList<>();addBox(factory,faces,0,1,0,1,0,1,new int[]{0,1,4,5,2,3});RenderPatch[] result=faces.toArray(new RenderPatch[0]);if(turn!=0)for(int i=0;i<result.length;i++)result[i]=factory.getRotatedPatch(result[i],0,turn*90,0,result[i].getTextureIndex());return cache[turn]=result;
    }
}

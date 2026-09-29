package com.vandorlabs.dynmap;

import java.util.ArrayList;
import java.util.List;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/** Reads the lower half's facing and the saved leaf settings for both door cells. */
public final class ProgrammableDoorRenderer extends CustomRenderer {
    private static final int[] PANEL={0,0,0,0,0,0};
    @Override public String[] getTileEntityFieldsNeeded() {
        return new String[]{"SpaceDoorSliding","SpaceDoorPlacementDepth"};
    }
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) {
        DynmapBlockState state=context.getBlockType();
        boolean upper=state.isStateMatch("half","upper");
        DynmapBlockState lower=upper?context.getBlockTypeAt(0,-1,0):state;
        if (lower==null || !lower.matchingBaseState(state)) return new RenderPatch[0];
        DynmapBlockState hinge=upper?state:context.getBlockTypeAt(0,1,0);
        boolean right=hinge!=null && hinge.isStateMatch("hinge","right");
        boolean open=lower.isStateMatch("open","true");
        int facing=lower.isStateMatch("facing","east")?1
                :lower.isStateMatch("facing","south")?2
                :lower.isStateMatch("facing","west")?3:0;
        int y=upper?-1:0;
        Object slidingValue=context.getBlockTileEntityFieldAt("SpaceDoorSliding",0,y,0);
        boolean sliding=Boolean.TRUE.equals(slidingValue)
                || slidingValue instanceof Number && ((Number)slidingValue).intValue()!=0;
        Object depthValue=context.getBlockTileEntityFieldAt("SpaceDoorPlacementDepth",0,y,0);
        int depth=depthValue instanceof Number?((Number)depthValue).intValue():1;
        double near=depth==0?7.5:depth==2?14:1;
        List<RenderPatch> patches=new ArrayList<>();
        if (open && sliding) return new RenderPatch[0];
        if (open) {
            boolean high=right == (facing==0 || facing==1);
            double edge=high?15:0;
            if ((facing&1)==0) addBox(context.getPatchFactory(),patches,edge/16,(edge+1)/16,0,1,0,1,PANEL);
            else addBox(context.getPatchFactory(),patches,0,1,0,1,edge/16,(edge+1)/16,PANEL);
        } else if ((facing&1)==0) {
            double z=facing==0?near:15-near;
            addBox(context.getPatchFactory(),patches,0,1,0,1,z/16,(z+1)/16,PANEL);
        } else {
            double x=facing==1?15-near:near;
            addBox(context.getPatchFactory(),patches,x/16,(x+1)/16,0,1,0,1,PANEL);
        }
        return patches.toArray(new RenderPatch[0]);
    }
}

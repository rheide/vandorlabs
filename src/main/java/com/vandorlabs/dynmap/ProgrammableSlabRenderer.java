package com.vandorlabs.dynmap;

import java.util.ArrayList;
import java.util.List;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/** Dynmap's block scan cannot see the slab's client-side baked housing. */
public final class ProgrammableSlabRenderer extends HousingRenderer {
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) {
        boolean top=context.getBlockType().isStateMatch("half","top");
        List<RenderPatch> patches=new ArrayList<>();
        addBox(context.getPatchFactory(),patches,0,1,top?.5:0,top?1:.5,0,1,
                faces(context,true));
        return patches.toArray(new RenderPatch[0]);
    }
}

package com.vandorlabs.dynmap;

import java.util.*;
import org.dynmap.renderer.*;

/** Saved selected artwork on the native raised or pressed inset pressure-plate housing. */
public final class PressurePlateRenderer extends HousingRenderer {
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"PlateTexture"};}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){List<RenderPatch> patches=new ArrayList<>();int material=texture(context.getBlockTileEntityField("PlateTexture"));double height=context.getBlockType().isStateMatch("power","0")?1D/16:.5/16;addBox(context.getPatchFactory(),patches,1D/16,15D/16,0,height,1D/16,15D/16,new int[]{material,material,material,material,material,material});return patches.toArray(new RenderPatch[0]);}
}

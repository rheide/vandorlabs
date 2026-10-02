package com.vandorlabs.dynmap;

import java.util.ArrayList;
import java.util.List;
import com.vandorlabs.tiles.ScreenHousingTextures;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

/** Uses the moving cell's clipped boxes instead of a fixed triangular stand-in. */
public final class ControlledRampRenderer extends CustomRenderer {
    @Override public String[] getTileEntityFieldsNeeded() {
        return new String[]{"DynmapBoxes","DynmapTexture"};
    }
    @Override protected int getMaximumTextureCount() { return ScreenHousingTextures.LEGACY_COUNT + 1; }
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) {
        Object saved=context.getBlockTileEntityField("DynmapBoxes");
        if (!(saved instanceof String)) return new RenderPatch[0];
        Object savedTexture=context.getBlockTileEntityField("DynmapTexture");
        int fallback=ScreenHousingTextures.LEGACY_COUNT;
        int texture=savedTexture instanceof Number?((Number)savedTexture).intValue():fallback;
        if (texture<0 || texture>fallback) texture=fallback;
        int[] faces={texture,texture,texture,texture,texture,texture};
        List<RenderPatch> patches=new ArrayList<>();
        for (String box:((String)saved).split(";")) {
            String[] values=box.split(",");
            if (values.length!=6) continue;
            try {
                double[] v=new double[6];
                for (int i=0;i<6;i++) v[i]=Double.parseDouble(values[i]);
                if (v[0]>=0 && v[3]<=1 && v[0]<v[3]
                        && v[1]>=0 && v[4]<=1 && v[1]<v[4]
                        && v[2]>=0 && v[5]<=1 && v[2]<v[5])
                    addBox(context.getPatchFactory(),patches,v[0],v[3],v[1],v[4],v[2],v[5],faces);
            } catch (NumberFormatException ignored) { }
        }
        return patches.toArray(new RenderPatch[0]);
    }
}

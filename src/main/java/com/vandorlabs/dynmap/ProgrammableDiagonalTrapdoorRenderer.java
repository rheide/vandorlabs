package com.vandorlabs.dynmap;

import com.vandorlabs.render.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import org.dynmap.renderer.*;

/** Six true sloped faces, using the same rigid endpoints as the client. */
public final class ProgrammableDiagonalTrapdoorRenderer extends CustomRenderer {
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"housingTexture","TrapdoorPosition","TrapdoorSliding","DiagonalReverse"};}
    private static int number(Object v){return v instanceof Number?((Number)v).intValue():0;}
    private static boolean flag(Object v){return Boolean.TRUE.equals(v) || number(v)!=0;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) {
        int turns=context.getBlockType().isStateMatch("facing","east")?1:context.getBlockType().isStateMatch("facing","south")?2:context.getBlockType().isStateMatch("facing","west")?3:0;
        double[][] v=DiagonalTrapdoorGeometry.corners(Math.max(0,Math.min(2,number(context.getBlockTileEntityField("TrapdoorPosition")))),context.getBlockType().isStateMatch("half","top"),turns,flag(context.getBlockTileEntityField("TrapdoorSliding")),flag(context.getBlockTileEntityField("DiagonalReverse")),context.getBlockType().isStateMatch("open","true")?1:0);
        int texture=ScreenHousingTextures.clamp(number(context.getBlockTileEntityField("housingTexture")));
        RenderPatch[] result=new RenderPatch[6];
        for(int f=0;f<6;f++) {
            int[] face=TrapdoorGeometry.FACES[f];double[] a=v[face[0]],b=v[face[1]],c=v[face[3]];
            result[f]=context.getPatchFactory().getPatch(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],0,1,0,1,RenderPatchFactory.SideVisible.TOP,texture);
        }
        return result;
    }
}

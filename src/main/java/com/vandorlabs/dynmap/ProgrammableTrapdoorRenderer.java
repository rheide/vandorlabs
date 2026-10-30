package com.vandorlabs.dynmap;

import com.vandorlabs.render.TrapdoorGeometry;
import com.vandorlabs.tiles.ScreenHousingTextures;
import org.dynmap.renderer.*;
import java.util.*;

/** Current finish and open endpoint, including horizontal sliding. */
public final class ProgrammableTrapdoorRenderer extends HousingRenderer {
    @Override protected int getMaximumTextureCount(){return ScreenHousingTextures.BUILTIN_COUNT*2+3;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"housingTexture","TrapdoorPosition","TrapdoorSliding","TrapdoorCover","TrapdoorSlideOverSurface"};}
    private static int number(Object value,int fallback){return value instanceof Number?((Number)value).intValue():fallback;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) {
        int position=Math.max(0,Math.min(2,number(context.getBlockTileEntityField("TrapdoorPosition"),0)));
        Object mode=context.getBlockTileEntityField("TrapdoorSliding");
        boolean sliding=Boolean.TRUE.equals(mode) || number(mode,0)!=0;
        int turns=context.getBlockType().isStateMatch("facing","east")?1:context.getBlockType().isStateMatch("facing","south")?2:
                context.getBlockType().isStateMatch("facing","west")?3:0;
        double[] b=TrapdoorGeometry.bounds(position,sliding,turns,context.getBlockType().isStateMatch("open","true")?1:0);
        Object surface=context.getBlockTileEntityField("TrapdoorSlideOverSurface");
        if(sliding && (Boolean.TRUE.equals(surface)||number(surface,0)!=0))b=com.vandorlabs.render.DiagonalTrapdoorGeometry.bounds(
                TrapdoorGeometry.surfaceCorners(position,turns,context.getBlockType().isStateMatch("open","true")?1:0,TrapdoorGeometry.OPEN_HINGE,15/16D));
        Object cover=context.getBlockTileEntityField("TrapdoorCover");
        if(Boolean.TRUE.equals(cover)||number(cover,0)!=0)b=com.vandorlabs.render.DiagonalTrapdoorGeometry.bounds(TrapdoorGeometry.coverCorners(position,turns,context.getBlockType().isStateMatch("open","true")?1:0));
        int texture=texture(context.getBlockTileEntityField("housingTexture"));
        List<RenderPatch> patches=new ArrayList<>();
        if("Frame".equals(ScreenHousingTextures.category(texture))) {
            int metal=ScreenHousingTextures.BUILTIN_COUNT*2+2;
            double cx=(b[0]+b[3])/2,cy=(b[1]+b[4])/2,cz=(b[2]+b[5])/2;
            addBox(context.getPatchFactory(),patches,cx+(b[0]-cx)*.9998,cx+(b[3]-cx)*.9998,cy+(b[1]-cy)*.9998,cy+(b[4]-cy)*.9998,cz+(b[2]-cz)*.9998,cz+(b[5]-cz)*.9998,new int[]{metal,metal,metal,metal,metal,metal});
        }
        addBox(context.getPatchFactory(),patches,b[0],b[3],b[1],b[4],b[2],b[5],new int[]{texture,texture,texture,texture,texture,texture});
        return patches.toArray(new RenderPatch[0]);
    }
}

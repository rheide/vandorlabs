package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.opengl.GL11;

/** Native faint glass sits inside the leaf; opaque authored artwork masks it. */
final class CompanyDoorGlazing {
    static boolean enabled=true;
    private static final Map<SelectedDoorGeometry,StaticSurfaceMesh> FULL=new LinkedHashMap<>();
    private static final Map<SelectedDoorGeometry,StaticSurfaceMesh[][]> PANELS=new LinkedHashMap<>();
    static void clear(){FULL.clear();PANELS.clear();}
    static StaticSurfaceMesh mesh(SelectedDoorGeometry geometry){
        StaticSurfaceMesh mesh=FULL.get(geometry);if(mesh!=null)return mesh;
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/glass_tile");double[] b=geometry.bounds;double z=(b[2]+b[5])/2;
        StaticSurfaceMesh.Capture out=StaticSurfaceMesh.capture();double[][] points={{b[0],b[1]},{b[3],b[1]},{b[3],b[4]},{b[0],b[4]}};
        for(int n=0;n<4;n++)out.pos(points[n][0],points[n][1],z).tex(sprite.getInterpolatedU(n==1||n==2?16:0),sprite.getInterpolatedV(n<2?16:0)).normal(0,0,1).endVertex();
        mesh=out.finish();FULL.put(geometry,mesh);if(FULL.size()>256)FULL.remove(FULL.keySet().iterator().next());return mesh;
    }
    static StaticSurfaceMesh[] panels(SelectedDoorGeometry geometry,boolean right){StaticSurfaceMesh[][] pair=PANELS.get(geometry);if(pair==null){pair=new StaticSurfaceMesh[2][];PANELS.put(geometry,pair);if(PANELS.size()>256)PANELS.remove(PANELS.keySet().iterator().next());}int hand=right?1:0;if(pair[hand]==null)pair[hand]=mesh(geometry).xPanels(hand,false);return pair[hand];}
    static void draw(SelectedDoorGeometry geometry,int light){
        if(!enabled)return;GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(false);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh(geometry).draw(buffer,light>>>16,light&65535);Tessellator.getInstance().draw();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();
    }
    private CompanyDoorGlazing(){}
}
